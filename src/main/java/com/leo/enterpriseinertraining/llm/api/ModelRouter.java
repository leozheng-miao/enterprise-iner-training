package com.leo.enterpriseinertraining.llm.api;

public interface ModelRouter {
    /** 解析某 Agent 在某租户下当前生效的路由（含主模型/fallback/promptVersion）。 */
    ResolvedRoute resolve(AgentRole role, Long tenantId);
}
