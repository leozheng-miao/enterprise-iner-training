package com.leo.enterpriseinertraining.agent.core;

import java.util.List;

public record AgentResult(
        AgentStatus status,
        String markdown,
        List<Citation> citations,
        String errorMessage
) {
    public static AgentResult ok(String md, List<Citation> citations) {
        return new AgentResult(AgentStatus.OK, md, citations, null);
    }
    public static AgentResult error(String msg) {
        return new AgentResult(AgentStatus.ERROR, null, List.of(), msg);
    }
}
