package com.leo.enterpriseinertraining.llm.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatModelFactoryTest {

    @Test
    void create_buildsModel_withGivenBaseUrlAndModel_noNetwork() {
        OpenAiChatModel model = OpenAiChatModelFactory.create(
            "https://api.openai.com", "sk-test", "gpt-4o", 0.7);
        assertThat(model).isNotNull();
        // 默认选项里的 model 被带上（toString 含 defaultOptions）
        assertThat(model.toString()).contains("gpt-4o");
    }

    @Test
    void create_blankApiKey_stillBuilds_forVllmLocalNoAuth() {
        OpenAiChatModel model = OpenAiChatModelFactory.create(
            "http://localhost:8000", "", "qwen2.5-1.5b", 0.0);
        assertThat(model).isNotNull();
    }
}
