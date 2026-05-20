package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.core.AgentStatus;
import com.leo.enterpriseinertraining.entity.ReportSection;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.entity.WorkflowLoopState;
import com.leo.enterpriseinertraining.mapper.ReportSectionMapper;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.mapper.WorkflowLoopStateMapper;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.workflow.WorkflowDef;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import com.leo.enterpriseinertraining.workflow.WorkflowNode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.leo.enterpriseinertraining.entity.table.ReportSectionTableDef.REPORT_SECTION;
import static com.leo.enterpriseinertraining.entity.table.WorkflowLoopStateTableDef.WORKFLOW_LOOP_STATE;

/**
 * 消费 {@link MqTopics#CRITIC_TASK}：读所有 section → 拼成 markdown → 调 Critic →
 * 如 needsRevision 且 loopCount < maxLoops，重发 write.section；否则拼接 final_markdown 并 DONE。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopics.CRITIC_TASK, consumerGroup = MqTopics.GROUP_CRITIC)
public class CriticConsumer implements RocketMQListener<String> {

    private final ObjectMapper om;
    private final ReportSectionMapper sectionMapper;
    private final ReportTaskMapper taskMapper;
    private final WorkflowLoopStateMapper loopMapper;
    private final WorkflowLoader workflowLoader;
    private final List<Agent> agents;
    private final SseSinkManager sinkManager;
    private final MqProducerService producer;

    private Map<String, Agent> agentByRole;

    @Override
    public void onMessage(String body) {
        try {
            MqMessage msg = om.readValue(body, MqMessage.class);
            handleCritic(msg.taskId());
        } catch (Exception e) {
            log.error("[CriticConsumer] failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void handleCritic(long taskId) throws Exception {
        ReportTask task = taskMapper.selectOneById(taskId);
        if (task == null) return;

        SseSink sink = sinkManager.get(taskId);
        if (sink != null) sink.nodeStatus("critic", "RUNNING");

        // 1) 拼接 markdown
        List<ReportSection> secs = sectionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(REPORT_SECTION.TASK_ID.eq(taskId))
                        .orderBy(REPORT_SECTION.SECTION_ORDER, true));
        StringBuilder md = new StringBuilder();
        for (ReportSection s : secs) {
            md.append(s.getContentMd() == null ? "" : s.getContentMd()).append("\n\n");
        }
        String fullMd = md.toString();

        // 2) 调 Critic
        WorkflowDef def = workflowLoader.load(task.getWorkflowName());
        WorkflowNode criticNode = def.getNodes().stream()
                .filter(n -> "critic".equals(n.getId())).findFirst().orElseThrow();
        Agent critic = agentByRole().get(criticNode.getAgent());
        AgentInvocation inv = new AgentInvocation(
                taskId, "critic", task.getTopic(),
                List.of(), criticNode.getPrompt(), null, fullMd);
        AgentResult result = critic.execute(inv, sink);

        if (result.status() == AgentStatus.ERROR) {
            // Critic 失败 → 跳过审查直接 DONE
            finalize(task, fullMd, secs, sink, "critic_failed_force_accept");
            return;
        }

        JsonNode node = om.readTree(result.markdown());
        boolean needsRevision = node.has("needsRevision") && node.get("needsRevision").asBoolean();
        int maxLoops = criticNode.getMaxLoops() == null ? 1 : criticNode.getMaxLoops();
        int loopCount = currentLoopCount(taskId, "critic");

        if (!needsRevision || loopCount >= maxLoops) {
            String reason = !needsRevision ? "no_revision_needed" : "max_loops_reached";
            finalize(task, fullMd, secs, sink, reason);
            return;
        }

        // 3) 触发修订：loopCount++ + 找出有问题的 sectionOrder，重发 write.section
        incrementLoopCount(taskId, "critic", maxLoops);

        Set<Integer> issueSectionOrders = new HashSet<>();
        JsonNode issues = node.get("issues");
        if (issues != null && issues.isArray()) {
            issues.forEach(it -> {
                if (it.has("sectionOrder")) issueSectionOrders.add(it.get("sectionOrder").asInt());
            });
        }
        if (issueSectionOrders.isEmpty()) {
            finalize(task, fullMd, secs, sink, "no_section_specified");
            return;
        }

        // 重发对应 section 给 Writer，并把 section 标记为 REVISING + revisionCount++
        for (Integer order : issueSectionOrders) {
            for (ReportSection s : secs) {
                if (s.getSectionOrder().equals(order)) {
                    s.setStatus("REVISING");
                    s.setRevisionCount(s.getRevisionCount() == null ? 1 : s.getRevisionCount() + 1);
                    sectionMapper.update(s);
                    break;
                }
            }
            producer.send(MqTopics.WRITE_SECTION, MqMessage.fanout(taskId, "write", order));
        }
        task.setPhase("WRITING");
        task.setProgress(75);
        taskMapper.update(task);
        if (sink != null) {
            sink.nodeStatus("critic", "DONE");
            sink.phaseChanged("WRITING", 75);
        }
        log.info("[Critic/{}] revision triggered for sections {}", taskId, issueSectionOrders);
    }

    private void finalize(ReportTask task, String fullMd, List<ReportSection> secs,
                          SseSink sink, String reason) {
        task.setFinalMarkdown(fullMd);
        task.setStatus("DONE");
        task.setPhase("DONE");
        task.setProgress(100);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.update(task);

        // 聚合所有 sections 的 citations
        List<com.leo.enterpriseinertraining.entity.ReportTask.CitationData> allCitations = new java.util.ArrayList<>();
        for (ReportSection s : secs) {
            if (s.getCitationsJson() != null) allCitations.addAll(s.getCitationsJson());
        }

        if (sink != null) {
            sink.nodeStatus("critic", "DONE");
            sink.phaseChanged("DONE", 100);
            sink.done(fullMd, allCitations);
        }
        sinkManager.remove(task.getId());
        log.info("[Critic/{}] task DONE (reason={})", task.getId(), reason);
    }

    private int currentLoopCount(long taskId, String nodeId) {
        List<WorkflowLoopState> rows = loopMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_LOOP_STATE.TASK_ID.eq(taskId))
                        .and(WORKFLOW_LOOP_STATE.NODE_ID.eq(nodeId)));
        return rows.isEmpty() ? 0 : rows.get(0).getLoopCount();
    }

    private void incrementLoopCount(long taskId, String nodeId, int maxLoops) {
        List<WorkflowLoopState> rows = loopMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_LOOP_STATE.TASK_ID.eq(taskId))
                        .and(WORKFLOW_LOOP_STATE.NODE_ID.eq(nodeId)));
        if (rows.isEmpty()) {
            WorkflowLoopState s = new WorkflowLoopState();
            s.setTaskId(taskId); s.setNodeId(nodeId); s.setLoopCount(1); s.setMaxLoops(maxLoops);
            loopMapper.insert(s);
        } else {
            WorkflowLoopState s = rows.get(0);
            s.setLoopCount(s.getLoopCount() + 1);
            loopMapper.update(s);
        }
    }

    private Map<String, Agent> agentByRole() {
        if (agentByRole == null) {
            agentByRole = new HashMap<>();
            for (Agent a : agents) agentByRole.put(a.role(), a);
        }
        return agentByRole;
    }
}
