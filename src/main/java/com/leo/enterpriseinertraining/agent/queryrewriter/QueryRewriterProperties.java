package com.leo.enterpriseinertraining.agent.queryrewriter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Query 改写 / 意图识别模型配置。
 *
 * <p>默认走 DashScope qwen-max（OpenAI 兼容协议），训练好 LoRA 后改 base-url
 * 指向 vLLM 即可热切，Java 侧零改动：</p>
 *
 * <pre>
 * app:
 *   query-rewriter:
 *     base-url: http://localhost:8000/v1
 *     api-key: EMPTY
 *     model: qwen2.5-1.5b-querywriter-lora
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.query-rewriter")
public class QueryRewriterProperties {

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String apiKey;
    private String model = "qwen-max";
    private double temperature = 0.0;
    private int timeoutMillis = 8000;
}
