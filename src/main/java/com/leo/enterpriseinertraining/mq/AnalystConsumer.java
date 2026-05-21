package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.workflow.WorkflowDef;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import com.leo.enterpriseinertraining.workflow.WorkflowNode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.leo.enterpriseinertraining.entity.table.WorkflowSubtaskTableDef.WORKFLOW_SUBTASK;

/**
 * 消费 {@link MqTopics#ANALYZE_TASK}：聚合所有 subtopic results → 调 AnalystAgent →
 * 把 sections 写到 report_section 表（status=DRAFT, content_md=null）→ 发 N 条 write.section。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopics.ANALYZE_TASK, consumerGroup = MqTopics.GROUP_ANALYST)
public class AnalystConsumer implements RocketMQListener<String> {

    private final ObjectMapper om;
    private final WorkflowSubtaskMapper subtaskMapper;
    private final ReportTaskMapper taskMapper;
    private final ReportSectionMapper sectionMapper;
    private final WorkflowLoader workflowLoader;
    private final List<Agent> agents;
    private final SseSinkManager sinkManager;
    private final MqProducerService producer;

    private Map<String, Agent> agentByRole;

    @Override
    public void onMessage(String body) {
        try {
            MqMessage msg = om.readValue(body, MqMessage.class);
            handleAnalyze(msg.taskId());
        } catch (Exception e) {
            log.error("[AnalystConsumer] failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void handleAnalyze(long taskId) throws Exception {
        ReportTask task = taskMapper.selectOneById(taskId);
        if (task == null) return;

        SseSink sink = sinkManager.get(taskId);
        if (sink != null) sink.nodeStatus("analyze", "RUNNING");

        // 1) 读所有 DONE 子任务 → 聚合 JSON 数组
        List<WorkflowSubtask> subs = subtaskMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_SUBTASK.TASK_ID.eq(taskId))
                        .and(WORKFLOW_SUBTASK.STATUS.eq("DONE"))
                        .orderBy(WORKFLOW_SUBTASK.SUB_INDEX, true));
        if (subs.isEmpty()) {
            failTask(task, "无 DONE 子任务", sink);
            return;
        }
        List<Map<String, Object>> aggregate = new ArrayList<>();
        for (WorkflowSubtask s : subs) {
            Map<String, Object> item = new HashMap<>();
            item.put("subIndex", s.getSubIndex());
            item.put("subtopic", s.getSubtopic());
            try {
                item.put("result", om.readTree(s.getResultJson() == null ? "{}" : s.getResultJson()));
            } catch (Exception ignored) {
                item.put("result", Map.of("content", s.getResultJson()));
            }
            aggregate.add(item);
        }
        String aggregateJson = om.writeValueAsString(aggregate);

        // 2) 调 Analyst
        WorkflowDef def = workflowLoader.load(task.getWorkflowName());
        WorkflowNode analyzeNode = def.getNodes().stream()
                .filter(n -> "analyze".equals(n.getId())).findFirst().orElseThrow();
        Agent analyst = agentByRole().get(analyzeNode.getAgent());
        AgentInvocation inv = new AgentInvocation(
                taskId, "analyze", task.getTopic(),
                List.of(), analyzeNode.getPrompt(), null, aggregateJson);
        AgentResult result = analyst.execute(inv, sink);

        if (result.status() == AgentStatus.ERROR) {
            failTask(task, "Analyst 失败: " + result.errorMessage(), sink);
            return;
        }

        // 3) 写 report_section 表
        JsonNode sectionsNode = om.readTree(result.markdown()).get("sections");
        int n = sectionsNode.size();
        for (int i = 0; i < n; i++) {
            JsonNode s = sectionsNode.get(i);
            ReportSection sec = new ReportSection();
            sec.setTaskId(taskId);
            sec.setSectionOrder(s.has("order") ? s.get("order").asInt() : i);
            sec.setTitle(s.has("title") ? s.get("title").asText() : "Section " + i);
            sec.setOutline(s.has("outline") ? s.get("outline").asText() : null);
            List<Integer> related = new ArrayList<>();
            if (s.has("relatedSubtopics") && s.get("relatedSubtopics").isArray()) {
                s.get("relatedSubtopics").forEach(r -> related.add(r.asInt()));
            }
            sec.setRelatedSubtopicsJson(related);
            sec.setStatus("DRAFT");
            sec.setRevisionCount(0);
            sectionMapper.insert(sec);
        }

        // 4) phase → WRITING
        task.setPhase("WRITING");
        task.setProgress(60);
        taskMapper.update(task);
        if (sink != null) {
            sink.nodeStatus("analyze", "DONE");
            sink.phaseChanged("WRITING", 60);
        }

        // 5) 发 N 条 write.section
        for (int i = 0; i < n; i++) {
            int order = sectionsNode.get(i).has("order") ? sectionsNode.get(i).get("order").asInt() : i;
            producer.send(MqTopics.WRITE_SECTION, MqMessage.fanout(taskId, "write", order));
        }
        log.info("[Analyst/{}] dispatched {} write.section messages", taskId, n);
    }

    private void failTask(ReportTask task, String errorMessage, SseSink sink) {
        task.setStatus("FAILED");
        task.setPhase("DONE");
        task.setErrorMessage(errorMessage);
        task.setFinishedAt(java.time.LocalDateTime.now());
        taskMapper.update(task);
        if (sink != null) sink.error(errorMessage);
        sinkManager.remove(task.getId());
    }

    @PostConstruct
    private void initAgentByRole() {
        agentByRole = new HashMap<>();
        for (Agent a : agents) agentByRole.put(a.role(), a);
        log.info("[{}] agentByRole initialized: {}", getClass().getSimpleName(), agentByRole.keySet());
    }

    private Map<String, Agent> agentByRole() {
        return agentByRole;
    }
}
