package com.leo.enterpriseinertraining.rag.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-for-hs256-must-be-at-least-32-bytes-please"
})
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class DashScopeEmbeddingClientIT {

    @Autowired
    DashScopeEmbeddingClient client;

    @Test
    void embed_one_returns_1024d_vector() {
        float[] v = client.embedOne("具身智能产业链上中下游分析");
        assertEquals(1024, v.length);
        double norm = 0;
        for (float f : v) norm += f * f;
        assertTrue(norm > 0.0);
    }

    @Test
    void embed_batch_returns_same_count() {
        var v = client.embed(java.util.List.of("AI", "半导体", "新能源"));
        assertEquals(3, v.size());
        v.forEach(x -> assertEquals(1024, x.length));
    }
}
