package com.leo.enterpriseinertraining.agent.tool;

import com.leo.enterpriseinertraining.trace.WorkflowNodeRunRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tool 调用 Trace 写入器（薄包装）。
 *
 * <p>不做 AOP 拦截 —— ResearcherAgent 包 AgentTool 成 FunctionToolCallback 时显式调用，业务路径清晰。</p>
 */
@Component
@RequiredArgsConstructor
public class ToolInvocationTracer {

    private final WorkflowNodeRunRecorder recorder;

    public void recordSuccess(long taskId, String nodeId, String agentRole,
                              String toolName, Object input, Object output, int latencyMs) {
        recorder.recordToolCall(taskId, nodeId, agentRole, toolName,
                input, output, latencyMs, "OK", null);
    }

    public void recordError(long taskId, String nodeId, String agentRole,
                            String toolName, Object input, Throwable err, int latencyMs) {
        recorder.recordToolCall(taskId, nodeId, agentRole, toolName,
                input, null, latencyMs, "ERROR", err.getMessage());
    }
}
