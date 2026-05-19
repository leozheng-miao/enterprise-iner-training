package com.leo.enterpriseinertraining.agent.core;

import com.leo.enterpriseinertraining.agent.tool.AgentTool;

import java.util.List;

public record AgentInvocation(
        long taskId,
        String nodeId,
        String topic,
        List<AgentTool> tools,
        String promptRef       // "researcher_prompt@v1"
) {}
