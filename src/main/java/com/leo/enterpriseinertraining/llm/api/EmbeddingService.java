package com.leo.enterpriseinertraining.llm.api;

import java.util.List;

public interface EmbeddingService {
    Provider provider();
    List<Embedding> embed(EmbedRequest req);
}
