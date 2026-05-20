package com.leo.enterpriseinertraining.agent.core;

import com.leo.enterpriseinertraining.agent.tool.AgentTool;

import java.util.List;

public record AgentInvocation(
        long taskId,
        String nodeId,
        String topic,
        List<AgentTool> tools,
        String promptRef,
        Integer fanoutIndex,         // 阶段 3：fanout 子任务索引，非 fanout 节点为 null
        String fanoutPayload         // 阶段 3：单个子任务负载 JSON 字符串
) {
    /** 阶段 2 兼容构造：不带 fanout 字段。 */
    public static AgentInvocation of(long taskId, String nodeId, String topic,
                                     List<AgentTool> tools, String promptRef) {
        return new AgentInvocation(taskId, nodeId, topic, tools, promptRef, null, null);
    }
}
