package com.leo.enterpriseinertraining.llm.api;

/** 跨 Provider fallback 候选：哪个 Provider 的哪个 model。 */
public record ProviderModel(Provider provider, String model) {}
