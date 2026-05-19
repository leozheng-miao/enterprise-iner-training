package com.leo.enterpriseinertraining.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 双数据源配置。
 *
 * <p>项目同时使用两个数据源：</p>
 * <ul>
 *   <li>{@link #dataSource()} → MySQL（业务元数据、Trace、Prompt 等），
 *       标记 {@link Primary @Primary}，MyBatis-Flex 默认走这一个；
 *       配置来自 {@code spring.datasource.*}。</li>
 *   <li>{@link #pgVectorDataSource} → PGVector / PostgreSQL（向量库），
 *       使用 {@code @Qualifier("pgVectorDataSource")} 或
 *       {@code @Qualifier("pgVectorJdbcTemplate")} 注入，
 *       不参与 MyBatis-Flex 扫描，只暴露 {@link JdbcTemplate} 给 RAG 模块手写 SQL 用。</li>
 * </ul>
 *
 * <p><b>为什么显式声明主 DataSource：</b>项目里同时存在 2 个 {@link DataSource} bean 时，
 * Spring Boot 自动配置的 {@code dataSource} bean 默认不是 {@link Primary @Primary}，
 * MyBatis-Flex 按 bean 顺序拿到的可能是 PGVector，导致 MySQL 表查询被发给 Postgres。
 * 这里手动接管主 DataSource 并标 {@code @Primary}，明确告诉 Spring/MyBatis 哪个是默认。</p>
 *
 * <p>HikariCP 关闭由 Spring 容器自动接管（{@link HikariDataSource} 实现 {@link AutoCloseable}）。</p>
 */
@Configuration
public class PgVectorDataSourceConfig {

    /**
     * 主 MySQL 数据源。手动 setter 注入 {@code spring.datasource.*} 字段，
     * 显式声明 {@link Primary @Primary} 让 MyBatis-Flex / Spring 默认注入用它。
     *
     * <p>不使用 {@code DataSourceBuilder + @ConfigurationProperties}：那种方式
     * 配合 {@code HikariDataSource} 时期望字段名 {@code jdbcUrl}，
     * 而项目 yml 用 {@code url}，会导致 "Can not get jdbcUrl" 启动失败。</p>
     */
    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name}") String driverClass) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driverClass);
        ds.setPoolName("mysql-pool");
        return ds;
    }

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
    public JdbcTemplate pgVectorJdbcTemplate(
            @Qualifier("pgVectorDataSource") DataSource ds) {
        // 必须 @Qualifier：主 dataSource 已标 @Primary，不显式 qualifier 会拿到 MySQL，
        // 导致 PGVector init SQL 跑到 MySQL 报 "table not found"。
        return new JdbcTemplate(ds);
    }
}
