package com.leo.enterpriseinertraining.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Bean
    public ElasticsearchClient elasticsearchClient(
            @Value("${rag.elasticsearch.host}") String host,
            @Value("${rag.elasticsearch.port}") int port,
            @Value("${rag.elasticsearch.scheme}") String scheme) {
        RestClient rest = RestClient.builder(new HttpHost(host, port, scheme)).build();
        return new ElasticsearchClient(new RestClientTransport(rest, new JacksonJsonpMapper()));
    }
}
