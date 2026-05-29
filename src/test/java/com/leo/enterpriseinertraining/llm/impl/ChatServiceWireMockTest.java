package com.leo.enterpriseinertraining.llm.impl;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leo.enterpriseinertraining.llm.api.ChatRequest;
import com.leo.enterpriseinertraining.llm.api.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceWireMockTest {

    private WireMockServer wm;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
        wm.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"id":"x","object":"chat.completion","model":"qwen-max",
                     "choices":[{"index":0,"finish_reason":"stop",
                       "message":{"role":"assistant","content":"pong"}}],
                     "usage":{"prompt_tokens":7,"completion_tokens":2,"total_tokens":9}}
                    """)));
    }

    @AfterEach
    void tearDown() { wm.stop(); }

    @Test
    void chatService_hitsCorrectPath_withBearerAuth_andModelBody() {
        // Use SimpleClientHttpRequestFactory (HTTP/1.1) so WireMock (Jetty HTTP/1.1) doesn't get
        // an HTTP/2 upgrade attempt from the JDK HttpClient default.
        RestClient.Builder http11Builder = RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory());

        OpenAiApi api = OpenAiApi.builder()
            .baseUrl("http://localhost:" + wm.port())
            .apiKey("sk-fake-key")
            .completionsPath("/v1/chat/completions")
            .restClientBuilder(http11Builder)
            .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder()
                .model("qwen-max")
                .temperature(0.7)
                .build())
            .build();

        // 任一子类共享基类逻辑，这里用 DashScope 子类代表
        var svc = new DashScopeChatServiceImpl(chatModel);
        ChatResponse resp = svc.call(
            ChatRequest.builder().systemText("sys").userText("ping").build(), null);

        assertThat(resp.content()).isEqualTo("pong");
        assertThat(resp.promptTokens()).isEqualTo(7);

        wm.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
            .withHeader("Authorization", equalTo("Bearer sk-fake-key"))
            .withRequestBody(matchingJsonPath("$.model", equalTo("qwen-max")))
            .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("system")))
            .withRequestBody(matchingJsonPath("$.messages[1].content", equalTo("ping"))));
    }
}
