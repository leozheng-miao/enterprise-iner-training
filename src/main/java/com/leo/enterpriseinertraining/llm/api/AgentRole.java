package com.leo.enterpriseinertraining.llm.api;

import java.util.Arrays;

/** 7 个 Agent 角色。code() = DB agent_role 列存的字符串（如 "QueryRewriter"）。 */
public enum AgentRole {
    PLANNER("Planner"),
    RESEARCHER("Researcher"),
    ANALYST("Analyst"),
    WRITER("Writer"),
    CRITIC("Critic"),
    QUERY_REWRITER("QueryRewriter"),
    JUDGE("Judge");

    private final String code;

    AgentRole(String code) { this.code = code; }

    public String code() { return code; }

    public static AgentRole fromCode(String s) {
        String t = s == null ? "" : s.trim();
        return Arrays.stream(values())
            .filter(r -> r.code.equalsIgnoreCase(t) || r.name().equalsIgnoreCase(t))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("unknown AgentRole: " + s));
    }
}
