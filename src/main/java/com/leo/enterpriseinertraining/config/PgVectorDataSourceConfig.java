package com.leo.enterpriseinertraining.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

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
