package com.leo.enterpriseinertraining.llm.impl;

import com.leo.enterpriseinertraining.llm.api.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** LLM 网关门面。Stage A：resolve→选 ChatService→call 主模型；无 Advisor/fallback（Stage B 增）。 */
@Slf4j
@Service
public class DefaultLLMGateway implements LLMGateway {

    private final Map<Provider, ChatService> chatServices = new EnumMap<>(Provider.class);
    private final ModelRouter router;
    private final EmbeddingService embeddingService;
    private final RerankService rerankService;

    public DefaultLLMGateway(List<ChatService> services, ModelRouter router,
                             EmbeddingService embeddingService, RerankService rerankService) {
        for (ChatService s : services) this.chatServices.put(s.provider(), s);
        this.router = router;
        this.embeddingService = embeddingService;
        this.rerankService = rerankService;
    }

    @Override
    public ChatResponse chat(AgentRole role, ChatRequest req, Long tenantId) {
        ResolvedRoute route = router.resolve(role, tenantId);
        ChatService svc = chatServices.get(route.provider());
        if (svc == null) {
            throw new IllegalStateException("无 ChatService 实现: provider=" + route.provider()
                + "（agent=" + role.code() + "）");
        }
        String model = req.modelOverride() != null ? req.modelOverride() : route.primaryModel();
        log.debug("[LLMGateway] role={} provider={} model={}", role.code(), route.provider(), model);
        return svc.call(req, model);
    }

    @Override
    public List<Embedding> embed(EmbedRequest req) {
        return embeddingService.embed(req);
    }

    @Override
    public List<RerankItem> rerank(RerankRequest req) {
        return rerankService.rerank(req);
    }
}
