package com.leo.enterpriseinertraining.rag.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 阿里云 DashScope text-embedding-v3（OpenAI 兼容协议）。
 * 1024 维。<b>单次请求最多 10 条</b>（DashScope OpenAI 兼容入口限制，实测报错：
 * "batch size is invalid, it should not be larger than 10"）；超出自动拆批。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeEmbeddingClient {

    /** DashScope OpenAI 兼容入口 batch 上限。 */
    private static final int BATCH_LIMIT = 10;

    @Value("${spring.ai.openai.api-key}") String apiKey;
    @Value("${app.dashscope.embedding-model}") String model;
    @Value("${app.dashscope.embedding-url}") String endpoint;

    /** 批量 embed；超过 {@link #BATCH_LIMIT} 条自动拆批。 */
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        if (texts.size() > BATCH_LIMIT) {
            List<float[]> all = new java.util.ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i += BATCH_LIMIT) {
                all.addAll(embed(texts.subList(i, Math.min(i + BATCH_LIMIT, texts.size()))));
            }
            return all;
        }
        RestClient rc = RestClient.create();
        EmbeddingResp resp = rc.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbeddingReq(model, texts, "float"))
                .retrieve()
                .body(EmbeddingResp.class);
        if (resp == null || resp.data == null) {
            throw new RuntimeException("DashScope embedding 返回空");
        }
        return resp.data.stream().map(d -> d.embedding).toList();
    }

    public float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }

    @Data
    static class EmbeddingReq {
        String model;
        List<String> input;
        @JsonProperty("encoding_format")
        String encodingFormat;
        EmbeddingReq(String m, List<String> in, String f) {
            this.model = m; this.input = in; this.encodingFormat = f;
        }
    }
    @Data
    static class EmbeddingResp {
        List<Item> data;
        String model;
        @Data static class Item { float[] embedding; int index; }
    }
}
