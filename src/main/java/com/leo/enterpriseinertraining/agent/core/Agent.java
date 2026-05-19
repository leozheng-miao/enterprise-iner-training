package com.leo.enterpriseinertraining.agent.core;

import com.leo.enterpriseinertraining.stream.SseSink;

public interface Agent {
    /** 角色名，与 WorkflowNode.agent 字段对齐（"Researcher"）。 */
    String role();

    AgentResult execute(AgentInvocation invocation, SseSink sink);
}
