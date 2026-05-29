package com.leo.enterpriseinertraining.llm.api;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestTest {

    @Test
    void builder_defaultsVarsToEmptyMap_whenNull() {
        ChatRequest req = ChatRequest.builder().userText("hi").build();
        assertThat(req.vars()).isNotNull().isEmpty();
        assertThat(req.userText()).isEqualTo("hi");
        assertThat(req.systemText()).isNull();
        assertThat(req.modelOverride()).isNull();
    }

    @Test
    void builder_carriesAllFields() {
        ChatRequest req = ChatRequest.builder()
            .systemText("sys").userText("u")
            .vars(Map.of("topic", "AI")).modelOverride("gpt-4o-mini").build();
        assertThat(req.systemText()).isEqualTo("sys");
        assertThat(req.vars()).containsEntry("topic", "AI");
        assertThat(req.modelOverride()).isEqualTo("gpt-4o-mini");
    }
}
