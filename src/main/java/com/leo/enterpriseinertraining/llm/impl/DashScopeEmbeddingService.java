package com.leo.enterpriseinertraining.llm.impl;

import com.leo.enterpriseinertraining.llm.api.*;
import com.leo.enterpriseinertraining.rag.llm.DashScopeEmbeddingClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DashScopeEmbeddingService implements EmbeddingService {

    private final DashScopeEmbeddingClient client;

    public DashScopeEmbeddingService(DashScopeEmbeddingClient client) { this.client = client; }

    @Override public Provider provider() { return Provider.DASHSCOPE; }

    @Override
    public List<Embedding> embed(EmbedRequest req) {
        List<float[]> vectors = client.embed(req.texts());
        List<Embedding> out = new ArrayList<>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) out.add(new Embedding(i, vectors.get(i)));
        return out;
    }
}
