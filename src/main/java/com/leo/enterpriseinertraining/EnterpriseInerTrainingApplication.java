package com.leo.enterpriseinertraining;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;

@SpringBootApplication(exclude = { PgVectorStoreAutoConfiguration.class })
@MapperScan("com.leo.enterpriseinertraining.mapper")
public class EnterpriseInerTrainingApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseInerTrainingApplication.class, args);
    }
}