package com.leo.enterpriseinertraining.llm.impl;

import com.leo.enterpriseinertraining.llm.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultLLMGatewayTest {

    private ChatService chatSvc(Provider p) {
        ChatService s = mock(ChatService.class);
        when(s.provider()).thenReturn(p);
        return s;
    }

    @Test
    void chat_routesToChatServiceMatchingResolvedProvider_withPrimaryModel() {
        ChatService openai = chatSvc(Provider.OPENAI);
        ChatService dashscope = chatSvc(Provider.DASHSCOPE);
        when(openai.call(any(), eq("gpt-4o")))
            .thenReturn(new ChatResponse("ok", "gpt-4o", 1, 1, "stop"));

        ModelRouter router = mock(ModelRouter.class);
        when(router.resolve(eq(AgentRole.PLANNER), eq(0L))).thenReturn(
            new ResolvedRoute(Provider.OPENAI, "gpt-4o", List.of(), List.of(), false, "v1"));

        var gw = new DefaultLLMGateway(List.of(openai, dashscope), router, null, null);
        ChatResponse resp = gw.chat(AgentRole.PLANNER,
            ChatRequest.builder().userText("hi").build(), 0L);

        assertThat(resp.content()).isEqualTo("ok");
        verify(openai).call(any(), eq("gpt-4o"));
        verify(dashscope, never()).call(any(), any());
    }

    @Test
    void chat_unknownProvider_throws() {
        ModelRouter router = mock(ModelRouter.class);
        when(router.resolve(any(), any())).thenReturn(
            new ResolvedRoute(Provider.VLLM, "x", List.of(), List.of(), false, null));

        var gw = new DefaultLLMGateway(List.of(chatSvc(Provider.OPENAI)), router, null, null);

        assertThatThrownBy(() -> gw.chat(AgentRole.PLANNER,
                ChatRequest.builder().userText("hi").build(), 0L))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void embed_delegatesToEmbeddingService() {
        EmbeddingService emb = mock(EmbeddingService.class);
        when(emb.embed(any())).thenReturn(List.of(new Embedding(0, new float[]{1f})));

        var gw = new DefaultLLMGateway(List.of(), mock(ModelRouter.class), emb, null);
        assertThat(gw.embed(new EmbedRequest(List.of("a")))).hasSize(1);
    }
}
