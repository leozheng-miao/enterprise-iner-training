package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.core.AgentStatus;
import com.leo.enterpriseinertraining.agent.tool.AgentTool;
import com.leo.enterpriseinertraining.agent.tool.ToolRegistryService;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.entity.WorkflowSubtask;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.mapper.WorkflowSubtaskMapper;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.workflow.JoinTracker;
import com.leo.enterpriseinertraining.workflow.WorkflowDef;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import com.leo.enterpriseinertraining.workflow.WorkflowNode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.leo.enterpriseinertraining.entity.table.WorkflowSubtaskTableDef.WORKFLOW_SUBTASK;

/**
 * 消费 {@link MqTopics#RESEARCH_TASK}：读 subtopic → 调 ResearcherAgent → Join 检查 →
 * 是最后一个完成的就发 analyze.task。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.RESEARCH_TASK,
        consumerGroup = MqTopics.GROUP_RESEARCHER,
        consumeThreadMax = 16,
        consumeThreadNumber = 4)
public class ResearcherWorker implements RocketMQListener<String> {

    private final ObjectMapper om;
    private final WorkflowSubtaskMapper subtaskMapper;
    private final ReportTaskMapper taskMapper;
    private final WorkflowLoader workflowLoader;
    private final ToolRegistryService toolRegistry;
    private final List<Agent> agents;
    private final JoinTracker joinTracker;
    private final SseSinkManager sinkManager;
    private final MqProducerService producer;

    private Map<String, Agent> agentByRole;

    @Override
    public void onMessage(String body) {
        try {
            MqMessage msg = om.readValue(body, MqMessage.class);
            handleResearchTask(msg);
        } catch (Exception e) {
            log.error("[ResearcherWorker] handle failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void handleResearchTask(MqMessage msg) throws Exception {
        long taskId = msg.taskId();
        int subIndex = msg.fanoutIndex();

        List<WorkflowSubtask> rows = subtaskMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_SUBTASK.TASK_ID.eq(taskId))
                        .and(WORKFLOW_SUBTASK.SUB_INDEX.eq(subIndex)));
        if (rows.isEmpty()) {
            log.warn("[ResearcherWorker] subtask 不存在 taskId={} subIndex={}", taskId, subIndex);
            return;
        }
        WorkflowSubtask st = rows.get(0);
        if ("DONE".equals(st.getStatus())) {
            log.info("[ResearcherWorker] subtask 已完成跳过 taskId={} subIndex={}", taskId, subIndex);
            return;
        }
        st.setStatus("RUNNING");
        st.setStartedAt(java.time.LocalDateTime.now());
        subtaskMapper.update(st);

        ReportTask task = taskMapper.selectOneById(taskId);
        WorkflowDef def = workflowLoader.load(task.getWorkflowName());
        WorkflowNode researchNode = def.getNodes().stream()
                .filter(n -> "research".equals(n.getId())).findFirst().orElseThrow();

        List<AgentTool> tools = toolRegistry.byNames(
                researchNode.getTools() == null ? List.of() : researchNode.getTools());
        Agent researcher = agentByRole().get(researchNode.getAgent());

        var sink = sinkManager.get(taskId);
        if (sink != null) sink.nodeStatus("research#" + subIndex, "RUNNING");

        AgentInvocation inv = new AgentInvocation(
                taskId, "research", task.getTopic(),
                tools, researchNode.getPrompt(), subIndex, st.getSubtopic());
        AgentResult result = researcher.execute(inv, sink);

        if (result.status() == AgentStatus.ERROR) {
            joinTracker.markFailed(taskId, subIndex, result.errorMessage());
            if (sink != null) sink.nodeStatus("research#" + subIndex, "FAILED");
            return;
        }

        boolean isLast = joinTracker.markCompletedAndCheckLast(taskId, subIndex, result.markdown());

        if (sink != null) sink.nodeStatus("research#" + subIndex, "DONE");

        if (isLast) {
            task.setPhase("ANALYZING");
            task.setProgress(55);
            taskMapper.update(task);
            if (sink != null) sink.phaseChanged("ANALYZING", 55);
            producer.send(MqTopics.ANALYZE_TASK, MqMessage.of(taskId, "analyze"));
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
