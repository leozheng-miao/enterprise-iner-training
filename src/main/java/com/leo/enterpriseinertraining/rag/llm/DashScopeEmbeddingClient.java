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
 * 1024 维。单次请求最多 25 条；超出自动拆批。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeEmbeddingClient {

    @Value("${spring.ai.openai.api-key}") String apiKey;
    @Value("${app.dashscope.embedding-model}") String model;
    @Value("${app.dashscope.embedding-url}") String endpoint;

    /** 批量 embed；DashScope v3 单次最多 25 条。 */
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        if (texts.size() > 25) {
            List<float[]> all = new java.util.ArrayList<>();
            for (int i = 0; i < texts.size(); i += 25) {
                all.addAll(embed(texts.subList(i, Math.min(i + 25, texts.size()))));
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
