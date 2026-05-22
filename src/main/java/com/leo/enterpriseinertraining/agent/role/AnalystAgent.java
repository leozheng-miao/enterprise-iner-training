package com.leo.enterpriseinertraining.agent.role;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.prompt.PromptLoader;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.trace.WorkflowNodeRunRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Analyst Agent：跨子主题分析 + 设计章节大纲。
 * 输入（fanoutPayload）：聚合 JSON 数组；输出：{sections: [...]} JSON。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalystAgent implements Agent {

    private final ChatClient.Builder chatClientBuilder;

    /** ChatClient.Builder 并发 build() 不安全；启动时 build 一次，之后多线程复用（ChatClient 本身线程安全）。 */
    private ChatClient chatClient;

    @PostConstruct
    void initChatClient() {
        this.chatClient = chatClientBuilder.build();
    }
    private final PromptLoader promptLoader;
    private final WorkflowNodeRunRecorder recorder;
    private final ObjectMapper om;

    @Value("${app.dashscope.chat-model-strong:qwen-max}")
    private String strongModel;

    @Override public String role() { return "Analyst"; }

    @Override
    public AgentResult execute(AgentInvocation inv, SseSink sink) {
        long t0 = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.load(inv.promptRef());
            ChatClient client = this.chatClient;

            var response = client.prompt()
                    .system(systemPrompt)
                    .user("以下是 N 个子主题的研究材料（JSON 数组）：\n" + inv.fanoutPayload())
                    .call()
                    .chatResponse();

            String raw = response.getResult().getOutput().getText();
            int tokensIn = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getPromptTokens().intValue();
            int tokensOut = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getCompletionTokens().intValue();
            int latency = (int) (System.currentTimeMillis() - t0);

            int l = raw.indexOf('{'), r = raw.lastIndexOf('}');
            String json = (l >= 0 && r > l) ? raw.substring(l, r + 1) : raw;
            JsonNode node = om.readTree(json);
            JsonNode sections = node.get("sections");
            if (sections == null || !sections.isArray() || sections.isEmpty()) {
                throw new RuntimeException("Analyst 输出缺少 sections 数组");
            }

            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), strongModel,
                    Map.of("inputPreview", inv.fanoutPayload() == null ? "" :
                            inv.fanoutPayload().substring(0, Math.min(500, inv.fanoutPayload().length()))),
                    Map.of("sections", sections, "raw", raw),
                    tokensIn, tokensOut, latency, "OK", null);

            log.info("[Analyst/{}] {} sections planned in {} ms", inv.taskId(), sections.size(), latency);
            return AgentResult.ok(json, List.of());
        } catch (Exception e) {
            log.error("[Analyst/{}] failed", inv.taskId(), e);
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), strongModel,
                    Map.of("inputPreview", ""), null,
                    0, 0, (int) (System.currentTimeMillis() - t0), "ERROR", e.getMessage());
            return AgentResult.error(e.getMessage());
        }
    }
}
