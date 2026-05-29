package com.leo.enterpriseinertraining.llm.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** 4 个 Provider 各一个 OpenAiChatModel Bean，按 @Qualifier 名注入。 */
@Configuration
@EnableConfigurationProperties(LLMProperties.class)
public class LLMProviderConfig {

    private final LLMProperties props;

    public LLMProviderConfig(LLMProperties props) { this.props = props; }

    private OpenAiChatModel build(String name) {
        LLMProperties.ProviderConfig c = props.get(name);
        return OpenAiChatModelFactory.create(c.getBaseUrl(), c.getApiKey(), c.getModel(), c.getTemperature());
    }

    /** @Primary：现有 Agent 注入的 ChatClient.Builder 按单候选装配，沿用 DashScope，不破坏 v1.0.0。 */
    @Bean
    @Primary
    OpenAiChatModel dashScopeChatModel() { return build("dashscope"); }

    @Bean
    OpenAiChatModel openAiOfficialChatModel() { return build("openai"); }

    @Bean
    OpenAiChatModel deepSeekChatModel() { return build("deepseek"); }

    @Bean
    OpenAiChatModel vllmChatModel() { return build("vllm"); }
}
