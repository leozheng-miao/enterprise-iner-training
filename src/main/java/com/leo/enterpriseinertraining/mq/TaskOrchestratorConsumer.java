package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.core.AgentStatus;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.entity.WorkflowSubtask;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.mapper.WorkflowSubtaskMapper;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.workflow.WorkflowDef;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import com.leo.enterpriseinertraining.workflow.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费 {@link MqTopics#TASK_CREATED}：加载 workflow → 调 PlannerAgent → 写 subtopics 到
 * workflow_subtask 表 → 发 N 条 research.task 消息触发 fanout。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopics.TASK_CREATED, consumerGroup = MqTopics.GROUP_ORCHESTRATOR)
public class TaskOrchestratorConsumer implements RocketMQListener<String> {

    private final ObjectMapper om;
    private final WorkflowLoader workflowLoader;
    private final ReportTaskMapper taskMapper;
    private final WorkflowSubtaskMapper subtaskMapper;
    private final List<Agent> agents;
    private final SseSinkManager sinkManager;
    private final MqProducerService producer;

    private Map<String, Agent> agentByRole;

    @Override
    public void onMessage(String body) {
        try {
            MqMessage msg = om.readValue(body, MqMessage.class);
            log.info("[Orchestrator] received task.created taskId={}", msg.taskId());
            handleTaskCreated(msg.taskId());
        } catch (Exception e) {
            log.error("[Orchestrator] handle failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void handleTaskCreated(long taskId) throws Exception {
        ReportTask task = taskMapper.selectOneById(taskId);
        if (task == null) {
            log.warn("[Orchestrator] task {} 不存在，丢弃消息", taskId);
            return;
        }

        task.setStatus("RUNNING");
        task.setPhase("PLANNING");
        task.setProgress(5);
        task.setStartedAt(LocalDateTime.now());
        taskMapper.update(task);

        SseSink sink = sinkManager.get(taskId);
        if (sink != null) {
            sink.phaseChanged("PLANNING", 5);
            sink.nodeStatus("plan", "RUNNING");
        }

        WorkflowDef def = workflowLoader.load(task.getWorkflowName());
        WorkflowNode plan = def.getNodes().stream()
                .filter(n -> "plan".equals(n.getId())).findFirst()
                .orElseThrow(() -> new RuntimeException("workflow 缺少 plan 节点"));

        Agent planner = agentByRole().get(plan.getAgent());
        if (planner == null) throw new RuntimeException("未知 agent: " + plan.getAgent());

        AgentInvocation inv = AgentInvocation.of(
                taskId, plan.getId(), task.getTopic(), List.of(), plan.getPrompt());
        AgentResult result = planner.execute(inv, sink);

        if (result.status() == AgentStatus.ERROR) {
            failTask(task, "Planner 失败: " + result.errorMessage(), sink);
            return;
        }

        JsonNode node = om.readTree(result.markdown());
        JsonNode subtopicsArr = node.get("subtopics");
        if (subtopicsArr == null || !subtopicsArr.isArray() || subtopicsArr.isEmpty()) {
            failTask(task, "Planner 返回 subtopics 为空", sink);
            return;
        }

        int n = subtopicsArr.size();
        for (int i = 0; i < n; i++) {
            WorkflowSubtask st = new WorkflowSubtask();
            st.setTaskId(taskId);
            st.setSubIndex(i);
            st.setSubtopic(subtopicsArr.get(i).asText());
            st.setStatus("PENDING");
            subtaskMapper.insert(st);
        }
        log.info("[Orchestrator] task {} fanout to {} research subtasks", taskId, n);

        task.setPhase("RESEARCHING");
        task.setProgress(10);
        taskMapper.update(task);
        if (sink != null) {
            sink.nodeStatus("plan", "DONE");
            sink.phaseChanged("RESEARCHING", 10);
        }

        for (int i = 0; i < n; i++) {
            producer.send(MqTopics.RESEARCH_TASK, MqMessage.fanout(taskId, "research", i));
        }
    }

    private void failTask(ReportTask task, String errorMessage, SseSink sink) {
        task.setStatus("FAILED");
        task.setPhase("DONE");
        task.setErrorMessage(errorMessage);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.update(task);
        if (sink != null) sink.error(errorMessage);
        sinkManager.remove(task.getId());
        log.error("[Orchestrator] task {} FAILED: {}", task.getId(), errorMessage);
    }

    private Map<String, Agent> agentByRole() {
        if (agentByRole == null) {
            agentByRole = new HashMap<>();
            for (Agent a : agents) agentByRole.put(a.role(), a);
        }
        return agentByRole;
    }
}
