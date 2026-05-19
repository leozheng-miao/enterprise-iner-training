package com.leo.enterpriseinertraining.rag.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DashScopeRerankClient {

    @Value("${spring.ai.openai.api-key}") String apiKey;
    @Value("${app.dashscope.rerank-model}") String model;
    @Value("${app.dashscope.rerank-url}") String endpoint;

    public record Scored(int index, double relevance) {}

    public List<Scored> rerank(String query, List<String> documents, int topN) {
        RestClient rc = RestClient.create();
        Req req = new Req(model, new Input(query, documents),
                new Parameters(topN, true));
        Resp resp = rc.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(Resp.class);
        if (resp == null || resp.output == null || resp.output.results == null) {
            throw new RuntimeException("DashScope rerank 返回空");
        }
        return resp.output.results.stream()
                .map(r -> new Scored(r.index, r.relevanceScore))
                .toList();
    }

    @Data
    static class Req {
        String model;
        Input input;
        Parameters parameters;
        Req(String m, Input i, Parameters p) { this.model=m; this.input=i; this.parameters=p; }
    }
    @Data
    static class Input {
        String query;
        List<String> documents;
        Input(String q, List<String> d) { this.query=q; this.documents=d; }
    }
    @Data
    static class Parameters {
        @JsonProperty("top_n") Integer topN;
        @JsonProperty("return_documents") Boolean returnDocs;
        Parameters(Integer t, Boolean r) { this.topN=t; this.returnDocs=r; }
    }
    @Data static class Resp { Output output; }
    @Data static class Output { List<R> results; }
    @Data static class R {
        Integer index;
        @JsonProperty("relevance_score") Double relevanceScore;
    }
}
