package com.leo.enterpriseinertraining.workflow;

import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.core.AgentStatus;
import com.leo.enterpriseinertraining.agent.core.Citation;
import com.leo.enterpriseinertraining.agent.tool.AgentTool;
import com.leo.enterpriseinertraining.agent.tool.ToolRegistryService;
import com.leo.enterpriseinertraining.stream.SseSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workflow 顺序执行器（阶段 2 雏形）。
 *
 * <p>限制：</p>
 * <ul>
 *   <li>只支持顺序节点（不支持 fanout / join / 回环）</li>
 *   <li>同步执行，不引入 RocketMQ（阶段 3 升级）</li>
 *   <li>节点失败立即抛出，整条 workflow 标记 FAILED</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private final ToolRegistryService toolRegistry;
    private final List<Agent> agents;

    private Map<String, Agent> byRole;

    public WorkflowExecutionResult execute(long taskId, WorkflowDef def, String topic, SseSink sink) {
        // 懒构建 role → Agent 映射
        if (byRole == null) {
            byRole = new HashMap<>();
            for (Agent a : agents) byRole.put(a.role(), a);
            log.info("[WorkflowEngine] registered agents: {}", byRole.keySet());
        }

        String lastMarkdown = null;
        List<Citation> allCitations = new ArrayList<>();

        for (WorkflowNode node : def.getNodes()) {
            if (sink != null && !sink.isClosed()) sink.nodeStatus(node.getId(), "RUNNING");

            Agent agent = byRole.get(node.getAgent());
            if (agent == null) {
                String msg = "未知 agent: " + node.getAgent();
                if (sink != null) sink.nodeStatus(node.getId(), "FAILED");
                return WorkflowExecutionResult.failure(msg);
            }

            List<AgentTool> tools = toolRegistry.byNames(
                    node.getTools() == null ? List.of() : node.getTools());

            AgentInvocation invocation = new AgentInvocation(
                    taskId, node.getId(), topic, tools, node.getPrompt());

            AgentResult result = agent.execute(invocation, sink);

            if (result.status() == AgentStatus.ERROR) {
                if (sink != null) sink.nodeStatus(node.getId(), "FAILED");
                return WorkflowExecutionResult.failure(result.errorMessage());
            }

            lastMarkdown = result.markdown();
            if (result.citations() != null) allCitations.addAll(result.citations());

            if (sink != null) sink.nodeStatus(node.getId(), "DONE");
        }

        return WorkflowExecutionResult.success(lastMarkdown, allCitations);
    }

    public record WorkflowExecutionResult(
            boolean ok,
            String markdown,
            List<Citation> citations,
            String errorMessage
    ) {
        public static WorkflowExecutionResult success(String md, List<Citation> cits) {
            return new WorkflowExecutionResult(true, md, cits, null);
        }
        public static WorkflowExecutionResult failure(String msg) {
            return new WorkflowExecutionResult(false, null, List.of(), msg);
        }
    }
}
