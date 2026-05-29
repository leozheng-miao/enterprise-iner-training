package com.leo.enterpriseinertraining.llm.impl;

import com.leo.enterpriseinertraining.llm.api.*;
import com.leo.enterpriseinertraining.rag.llm.DashScopeEmbeddingClient;
import com.leo.enterpriseinertraining.rag.llm.DashScopeRerankClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DashScopeEmbeddingServiceTest {

    @Test
    void embed_delegatesToClient_andMapsToIndexedEmbeddings() {
        DashScopeEmbeddingClient client = mock(DashScopeEmbeddingClient.class);
        when(client.embed(any())).thenReturn(List.of(new float[]{1f, 2f}, new float[]{3f, 4f}));

        var svc = new DashScopeEmbeddingService(client);
        List<Embedding> out = svc.embed(new EmbedRequest(List.of("a", "b")));

        assertThat(svc.provider()).isEqualTo(Provider.DASHSCOPE);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).index()).isEqualTo(0);
        assertThat(out.get(1).vector()).containsExactly(3f, 4f);
    }

    @Test
    void rerank_delegatesToClient_andMapsScores() {
        DashScopeRerankClient client = mock(DashScopeRerankClient.class);
        when(client.rerank(eq("q"), any(), eq(2)))
            .thenReturn(List.of(new DashScopeRerankClient.Scored(1, 0.9),
                                 new DashScopeRerankClient.Scored(0, 0.4)));

        var svc = new DashScopeRerankService(client);
        List<RerankItem> out = svc.rerank(new RerankRequest("q", List.of("d0", "d1"), 2));

        assertThat(svc.provider()).isEqualTo(Provider.DASHSCOPE);
        assertThat(out.get(0).index()).isEqualTo(1);
        assertThat(out.get(0).relevanceScore()).isEqualTo(0.9);
    }
}
