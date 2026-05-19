package com.leo.enterpriseinertraining.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EsIndexInitializer {

    @Bean
    public ApplicationRunner esIndexRunner(ElasticsearchClient es,
                                           @Value("${rag.elasticsearch.index}") String indexName) {
        return args -> {
            boolean exists = es.indices().exists(ExistsRequest.of(b -> b.index(indexName))).value();
            if (exists) {
                log.info("[ES] index {} already exists, skip", indexName);
                return;
            }
            try (InputStream in = new ClassPathResource(
                    "es/knowledge_chunk_bm25.mapping.json").getInputStream()) {
                CreateIndexRequest req = CreateIndexRequest.of(b -> b
                        .index(indexName)
                        .withJson(in));
                es.indices().create(req);
                log.info("[ES] index {} created", indexName);
            } catch (Exception e) {
                log.error("[ES] index init failed", e);
                throw e;
            }
        };
    }
}
