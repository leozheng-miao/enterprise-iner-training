package com.leo.enterpriseinertraining.llm.api;

/** 网关层归一化的聊天响应。content() 是模型输出文本。 */
public record ChatResponse(String content, String model,
                           int promptTokens, int completionTokens,
                           String finishReason) {}
