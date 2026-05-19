package com.leo.enterpriseinertraining.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.entity.WorkflowNodeRun;
import com.leo.enterpriseinertraining.mapper.WorkflowNodeRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 写入 {@code workflow_node_run} 的薄包装。
 *
 * <p>step_seq 在同一 (taskId, nodeId) 内自增。线程安全。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowNodeRunRecorder {

    private final WorkflowNodeRunMapper mapper;
    private final ObjectMapper om = new ObjectMapper();

    /** key = taskId + "::" + nodeId */
    private final Map<String, AtomicInteger> seqCounters = new ConcurrentHashMap<>();

    public WorkflowNodeRun recordLlmCall(long taskId, String nodeId, String agentRole,
                                          String promptVersion, String model,
                                          Object input, Object output,
                                          int tokensIn, int tokensOut, int latencyMs,
                                          String status, String errorMessage) {
        WorkflowNodeRun row = new WorkflowNodeRun();
        row.setTaskId(taskId);
        row.setNodeId(nodeId);
        row.setAgentRole(agentRole);
        row.setStepType("LLM_CALL");
        row.setStepSeq(nextSeq(taskId, nodeId));
        row.setPromptVersion(promptVersion);
        row.setModel(model);
        row.setInputJson(toJson(input));
        row.setOutputJson(toJson(output));
        row.setTokensIn(tokensIn);
        row.setTokensOut(tokensOut);
        row.setLatencyMs(latencyMs);
        row.setStatus(status);
        row.setErrorMessage(errorMessage);
        mapper.insert(row);
        return row;
    }

    public WorkflowNodeRun recordToolCall(long taskId, String nodeId, String agentRole,
                                           String toolName,
                                           Object input, Object output,
                                           int latencyMs,
                                           String status, String errorMessage) {
        WorkflowNodeRun row = new WorkflowNodeRun();
        row.setTaskId(taskId);
        row.setNodeId(nodeId);
        row.setAgentRole(agentRole);
        row.setStepType("TOOL_CALL");
        row.setStepSeq(nextSeq(taskId, nodeId));
        row.setToolName(toolName);
        row.setInputJson(toJson(input));
        row.setOutputJson(toJson(output));
        row.setTokensIn(0);
        row.setTokensOut(0);
        row.setLatencyMs(latencyMs);
        row.setStatus(status);
        row.setErrorMessage(errorMessage);
        mapper.insert(row);
        return row;
    }

    private int nextSeq(long taskId, String nodeId) {
        String k = taskId + "::" + nodeId;
        return seqCounters.computeIfAbsent(k, x -> new AtomicInteger(0)).getAndIncrement();
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"_serializeError\":\"" + e.getMessage() + "\"}";
        }
    }
}
