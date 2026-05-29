package com.leo.enterpriseinertraining.llm.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/** 用给定 base-url/api-key/model 构造一个独立 OpenAiChatModel。4 Provider 共用此工厂。 */
public final class OpenAiChatModelFactory {

    private OpenAiChatModelFactory() {}

    public static OpenAiChatModel create(String baseUrl, String apiKey, String model, double temperature) {
        OpenAiApi api = OpenAiApi.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey == null ? "" : apiKey)
            .completionsPath("/v1/chat/completions")
            .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(model)
            .temperature(temperature)
            .build();
        return OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(options)
            .build();
    }
}
