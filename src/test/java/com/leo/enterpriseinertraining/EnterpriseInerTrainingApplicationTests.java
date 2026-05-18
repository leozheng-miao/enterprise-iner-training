package com.leo.enterpriseinertraining;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 上下文加载烟雾测试。
 *
 * <p>需要 docker compose 起的 MySQL/Redis 在线（dev profile 默认激活）。
 * 通过 @TestPropertySource 覆盖 app.jwt.secret 与 spring.ai.openai.api-key，
 * 避免本地未设置 JWT_SECRET / DASHSCOPE_API_KEY 环境变量时该测试失败。</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-for-hs256-must-be-at-least-32-bytes-please",
        "spring.ai.openai.api-key=test-key-not-used"
})
class EnterpriseInerTrainingApplicationTests {

    @Test
    void contextLoads() {
    }

}