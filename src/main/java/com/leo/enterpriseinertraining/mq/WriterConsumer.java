package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.core.AgentStatus;
import com.leo.enterpriseinertraining.entity.ReportSection;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.entity.WorkflowSubtask;
import com.leo.enterpriseinertraining.mapper.ReportSectionMapper;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.mapper.WorkflowSubtaskMapper;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.leo.enterpriseinertraining.entity.table.ReportSectionTableDef.REPORT_SECTION;
import static com.leo.enterpriseinertraining.entity.table.WorkflowSubtaskTableDef.WORKFLOW_SUBTASK;

/**
 * 消费 {@link MqTopics#WRITE_SECTION}：读 section + 关联 subtopic materials → 调 WriterAgent →
 * update report_section 的 content_md → 检查所有 section 完成则发 critic.task。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.WRITE_SECTION,
        consumerGroup = MqTopics.GROUP_WRITER,
        consumeThreadMax = 8,
        consumeThreadNumber = 2)
public class WriterConsumer implements RocketMQListener<String> {

    private final ObjectMapper om;
    private final ReportSectionMapper sectionMapper;
    private final ReportTaskMapper taskMapper;
    private final WorkflowSubtaskMapper subtaskMapper;
    private final WorkflowLoader workflowLoader;
    private final List<Agent> agents;
    private final SseSinkManager sinkManager;
    private final MqProducerService producer;

    private Map<String, Agent> agentByRole;

    @Override
    public void onMessage(String body) {
        try {
            MqMessage msg = om.readValue(body, MqMessage.class);
            handleWriteSection(msg);
        } catch (Exception e) {
            log.error("[WriterConsumer] failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void handleWriteSection(MqMessage msg) throws Exception {
        long taskId = msg.taskId();
        int sectionOrder = msg.fanoutIndex();

        // 1) 取 section
        List<ReportSection> rows = sectionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(REPORT_SECTION.TASK_ID.eq(taskId))
                        .and(REPORT_SECTION.SECTION_ORDER.eq(sectionOrder)));
        if (rows.isEmpty()) {
            log.warn("[WriterConsumer] section 不存在 taskId={} order={}", taskId, sectionOrder);
            return;
        }
        ReportSection sec = rows.get(0);

        // 2) 取关联 subtopic materials
        List<Integer> relatedIndices = sec.getRelatedSubtopicsJson() == null
                ? List.of() : sec.getRelatedSubtopicsJson();
        List<Map<String, Object>> materials = new ArrayList<>();
        if (!relatedIndices.isEmpty()) {
            List<WorkflowSubtask> subs = subtaskMapper.selectListByQuery(
                    QueryWrapper.create()
                            .where(WORKFLOW_SUBTASK.TASK_ID.eq(taskId))
                            .and(WORKFLOW_SUBTASK.SUB_INDEX.in(relatedIndices)));
            for (WorkflowSubtask s : subs) {
                Map<String, Object> item = new HashMap<>();
                item.put("subtopic", s.getSubtopic());
                try {
                    item.put("result", om.readTree(s.getResultJson() == null ? "{}" : s.getResultJson()));
                } catch (Exception ignored) {}
                materials.add(item);
            }
        }

        // 3) 构造 user input
        Map<String, Object> userInput = new HashMap<>();
        userInput.put("title", sec.getTitle());
        userInput.put("outline", sec.getOutline());
        userInput.put("materials", materials);
        String userJson = om.writeValueAsString(userInput);

        // 4) 调 Writer
        ReportTask task = taskMapper.selectOneById(taskId);
        WorkflowDef def = workflowLoader.load(task.getWorkflowName());
        WorkflowNode writeNode = def.getNodes().stream()
                .filter(n -> "write".equals(n.getId())).findFirst().orElseThrow();
        Agent writer = agentByRole().get(writeNode.getAgent());

        var sink = sinkManager.get(taskId);
        if (sink != null) sink.nodeStatus("write#" + sectionOrder, "RUNNING");

        AgentInvocation inv = new AgentInvocation(
                taskId, "write", task.getTopic(),
                List.of(), writeNode.getPrompt(), sectionOrder, userJson);
        AgentResult result = writer.execute(inv, sink);

        if (result.status() == AgentStatus.ERROR) {
            sec.setStatus("DRAFT");
            sectionMapper.update(sec);
            if (sink != null) sink.nodeStatus("write#" + sectionOrder, "FAILED");
            throw new RuntimeException("Writer failed: " + result.errorMessage());
        }

        sec.setContentMd(result.markdown());
        sec.setStatus("FINAL");
        sectionMapper.update(sec);

        if (sink != null) {
            sink.nodeStatus("write#" + sectionOrder, "DONE");
            sink.sectionDone(sectionOrder, sec.getTitle(),
                    result.markdown().substring(0, Math.min(200, result.markdown().length())));
        }

        // 5) 检查是否所有 section 都 FINAL
        long pending = sectionMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(REPORT_SECTION.TASK_ID.eq(taskId))
                        .and(REPORT_SECTION.STATUS.ne("FINAL")));
        log.info("[Writer/{}] section {} done; pending={}", taskId, sectionOrder, pending);

        if (pending == 0) {
            task.setPhase("CRITICIZING");
            task.setProgress(90);
            taskMapper.update(task);
            if (sink != null) sink.phaseChanged("CRITICIZING", 90);
            producer.send(MqTopics.CRITIC_TASK, MqMessage.of(taskId, "critic"));
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
