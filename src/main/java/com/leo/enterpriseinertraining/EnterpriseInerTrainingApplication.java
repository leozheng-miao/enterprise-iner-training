package com.leo.enterpriseinertraining;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.leo.enterpriseinertraining.**.mapper")
public class EnterpriseInerTrainingApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseInerTrainingApplication.class, args);
    }
}