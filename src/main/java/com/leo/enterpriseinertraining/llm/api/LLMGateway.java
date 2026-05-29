package com.leo.enterpriseinertraining.llm.api;

import java.util.List;

public interface LLMGateway {
    /** 按 Agent 角色路由并调用主模型。Stage B 将插入 Advisor 链 + 把 tenantId 换成 WorkflowContext。 */
    ChatResponse chat(AgentRole role, ChatRequest req, Long tenantId);

    List<Embedding> embed(EmbedRequest req);

    List<RerankItem> rerank(RerankRequest req);
}
