package com.leo.enterpriseinertraining.llm.impl;

import com.leo.enterpriseinertraining.llm.api.ChatRequest;
import com.leo.enterpriseinertraining.llm.api.Provider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AbstractOpenAiChatServiceTest {

    /** 具体子类只为测基类逻辑。 */
    static class TestSvc extends AbstractOpenAiChatService {
        TestSvc(OpenAiChatModel m) { super(m); }
        @Override public Provider provider() { return Provider.OPENAI; }
    }

    private ChatResponse springResponse(String text, int in, int out) {
        Generation gen = new Generation(new AssistantMessage(text));
        ChatResponseMetadata meta = ChatResponseMetadata.builder()
            .usage(new DefaultUsage(in, out))
            .model("gpt-4o")
            .build();
        return new ChatResponse(List.of(gen), meta);
    }

    @Test
    void call_mapsSpringResponse_toGatewayResponse() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(springResponse("hello", 10, 5));

        var svc = new TestSvc(model);
        var resp = svc.call(ChatRequest.builder().systemText("sys").userText("hi").build(), "gpt-4o");

        assertThat(resp.content()).isEqualTo("hello");
        assertThat(resp.promptTokens()).isEqualTo(10);
        assertThat(resp.completionTokens()).isEqualTo(5);
        assertThat(resp.model()).isEqualTo("gpt-4o");
    }

    @Test
    void call_appliesModelOverride_intoPromptOptions() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(springResponse("x", 1, 1));

        var svc = new TestSvc(model);
        svc.call(ChatRequest.builder().userText("hi").build(), "gpt-4o-mini");

        ArgumentCaptor<Prompt> cap = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(cap.capture());
        var opts = (org.springframework.ai.openai.OpenAiChatOptions) cap.getValue().getOptions();
        assertThat(opts.getModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void supports_returnsTrue_inStageA() {
        var svc = new TestSvc(mock(OpenAiChatModel.class));
        assertThat(svc.supports("anything")).isTrue();
    }
}
