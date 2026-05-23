package com.leo.enterpriseinertraining.agent.queryrewriter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.vo.QueryRewriteVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Query 改写 / 意图识别客户端。
 *
 * <p>调用任意 OpenAI 兼容协议的 {@code /chat/completions} 端点，
 * 默认 DashScope qwen-max，{@link QueryRewriterProperties} 切 base-url 即可换成
 * 自托管 vLLM + LoRA 微调模型，<b>Java 侧零改动</b>—— 这是平台 LLMGateway 抽象的核心价值。</p>
 *
 * <p>System prompt 与 {@code finetune/scripts/synthesize.py} 保持一致：
 * 训练数据、线上推理、离线评估三处共用同一份指令，保证微调收益不被 prompt drift 吃掉。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRewriterClient {

    static final String SYSTEM_PROMPT = """
            你是研究查询改写专家。把用户输入的研究主题改写为下面结构的 JSON：
            {
              "intent": "industry_trend | company_compare | tech_progress | policy_impact | market_size",
              "industry": "<行业实体，如 动力电池 / 具身智能>",
              "year": <4 位整数；未指明默认当前年份>,
              "geo": "<地域，默认 中国>",
              "sub_queries": ["...", "...", "..."]
            }
            sub_queries 给 3-5 条便于 hybrid search 召回的子查询字符串。
            严格只输出 JSON 本身，不要 markdown 代码块，不要任何解释文字。
            """;

    private final QueryRewriterProperties props;
    private final ObjectMapper om;
    private RestClient http;

    @PostConstruct
    void init() {
        this.http = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + (props.getApiKey() == null ? "" : props.getApiKey()))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("[QueryRewriterClient] base-url={} model={}", props.getBaseUrl(), props.getModel());
    }

    /** 同步改写一次：拿到结构化 VO；失败抛 RuntimeException，调用方决定降级策略。 */
    public QueryRewriteVO rewrite(String topic) {
        long t0 = System.currentTimeMillis();
        Map<String, Object> body = Map.of(
                "model", props.getModel(),
                "temperature", props.getTemperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", topic)));
        try {
            Map<?, ?> resp = http.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            String content = extractContent(resp);
            QueryRewriteVO vo = om.readValue(stripFences(content), QueryRewriteVO.class);
            log.info("[QueryRewriterClient] rewritten in {}ms: intent={} industry={} subs={}",
                    System.currentTimeMillis() - t0, vo.getIntent(), vo.getIndustry(),
                    vo.getSubQueries() == null ? 0 : vo.getSubQueries().size());
            return vo;
        } catch (Exception e) {
            throw new RuntimeException("query rewrite failed for topic '" + topic + "': " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractContent(Map<?, ?> resp) {
        if (resp == null) throw new IllegalStateException("empty response");
        Object choices = resp.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalStateException("response has no choices: " + resp);
        }
        Map<String, Object> msg = (Map<String, Object>) ((Map<String, Object>) list.get(0)).get("message");
        return (String) msg.get("content");
    }

    /** 去掉模型可能多余的 ```json ... ``` 围栏。 */
    private static String stripFences(String s) {
        s = s.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
            s = s.trim();
        }
        return s;
    }
}
