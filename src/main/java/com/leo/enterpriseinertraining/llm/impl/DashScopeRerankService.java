package com.leo.enterpriseinertraining.llm.impl;

import com.leo.enterpriseinertraining.llm.api.*;
import com.leo.enterpriseinertraining.rag.llm.DashScopeRerankClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashScopeRerankService implements RerankService {

    private final DashScopeRerankClient client;

    public DashScopeRerankService(DashScopeRerankClient client) { this.client = client; }

    @Override public Provider provider() { return Provider.DASHSCOPE; }

    @Override
    public List<RerankItem> rerank(RerankRequest req) {
        return client.rerank(req.query(), req.documents(), req.topN()).stream()
            .map(s -> new RerankItem(s.index(), s.relevance()))
            .toList();
    }
}
