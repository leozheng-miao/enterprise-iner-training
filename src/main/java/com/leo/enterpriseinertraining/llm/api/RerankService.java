package com.leo.enterpriseinertraining.llm.api;

import java.util.List;

public interface RerankService {
    Provider provider();
    List<RerankItem> rerank(RerankRequest req);
}
