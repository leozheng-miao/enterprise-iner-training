package com.leo.enterpriseinertraining.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 8.x Java Client 配置。
 *
 * <p>把底层 {@link RestClient} 拆成独立 bean 并指定 {@code destroyMethod="close"}，
 * 确保 Spring 容器关闭时连接池被释放（{@link ElasticsearchClient} 自身不实现
 * {@code Closeable}，不会触发自动关闭）。</p>
 */
@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient(
            @Value("${rag.elasticsearch.host}") String host,
            @Value("${rag.elasticsearch.port}") int port,
            @Value("${rag.elasticsearch.scheme}") String scheme) {
        return RestClient.builder(new HttpHost(host, port, scheme)).build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient elasticsearchRestClient) {
        return new ElasticsearchClient(
                new RestClientTransport(elasticsearchRestClient, new JacksonJsonpMapper()));
    }
}
