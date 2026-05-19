package com.leo.enterpriseinertraining.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PGVector 独立数据源。
 *
 * <p>项目同时使用两个数据源：</p>
 * <ul>
 *   <li>主 {@code spring.datasource} → MySQL（业务元数据、Trace、Prompt 等），
 *       由 Spring Boot 自动配置 + MyBatis-Flex 扫描接管。</li>
 *   <li>{@link #pgVectorDataSource} → PGVector / PostgreSQL（向量库），
 *       使用 {@code @Qualifier("pgVectorDataSource")} 或
 *       {@code @Qualifier("pgVectorJdbcTemplate")} 注入，
 *       不参与 MyBatis-Flex 扫描，只暴露 {@link JdbcTemplate} 给 RAG 模块手写 SQL 用。</li>
 * </ul>
 *
 * <p>HikariCP 关闭由 Spring 容器自动接管（{@link HikariDataSource} 实现 {@link AutoCloseable}）。</p>
 */
@Configuration
public class PgVectorDataSourceConfig {

    @Bean(name = "pgVectorDataSource")
    public DataSource pgVectorDataSource(
            @Value("${rag.pgvector.url}") String url,
            @Value("${rag.pgvector.username}") String username,
            @Value("${rag.pgvector.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(8);
        ds.setMinimumIdle(1);
        ds.setPoolName("pg-vector-pool");
        return ds;
    }

    @Bean(name = "pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(DataSource pgVectorDataSource) {
        return new JdbcTemplate(pgVectorDataSource);
    }
}
