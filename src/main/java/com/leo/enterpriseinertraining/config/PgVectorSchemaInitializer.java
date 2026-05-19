package com.leo.enterpriseinertraining.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class PgVectorSchemaInitializer {

    @Bean
    public ApplicationRunner pgVectorSchemaRunner(
            @Qualifier("pgVectorDataSource") DataSource ds,
            @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbc) {
        return args -> {
            try (var conn = ds.getConnection()) {
                EncodedResource script = new EncodedResource(
                        new ClassPathResource("db/pgvector-init.sql"),
                        StandardCharsets.UTF_8);
                ScriptUtils.executeSqlScript(conn, script);
                Integer rows = jdbc.queryForObject(
                        "SELECT count(*) FROM knowledge_chunk_vec", Integer.class);
                log.info("[PGVector] schema ready, current rows={}", rows);
            } catch (Exception e) {
                log.error("[PGVector] schema init failed", e);
                throw e;
            }
        };
    }
}
