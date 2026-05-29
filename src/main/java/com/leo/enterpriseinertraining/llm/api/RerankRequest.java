package com.leo.enterpriseinertraining.llm.api;

import java.util.List;

public record RerankRequest(String query, List<String> documents, int topN) {}
