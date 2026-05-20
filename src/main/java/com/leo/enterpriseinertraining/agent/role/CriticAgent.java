package com.leo.enterpriseinertraining.agent.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.prompt.PromptLoader;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.trace.WorkflowNodeRunRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Critic Agent：审查整份研报，输出 {needsRevision, issues}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CriticAgent implements Agent {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptLoader promptLoader;
    private final WorkflowNodeRunRecorder recorder;
    private final ObjectMapper om;

    @Value("${app.dashscope.chat-model-strong:qwen-max}")
    private String strongModel;

    @Override public String role() { return "Critic"; }

    @Override
    public AgentResult execute(AgentInvocation inv, SseSink sink) {
        long t0 = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.load(inv.promptRef());
            ChatClient client = chatClientBuilder.build();

            var response = client.prompt()
                    .system(systemPrompt)
                    .user("以下是完整研报 markdown：\n\n" + inv.fanoutPayload())
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
            om.readTree(json);  // 验证可解析

            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), strongModel,
                    Map.of("markdownLen", inv.fanoutPayload().length()),
                    Map.of("raw", raw),
                    tokensIn, tokensOut, latency, "OK", null);

            log.info("[Critic/{}] reviewed in {} ms", inv.taskId(), latency);
            return AgentResult.ok(json, List.of());
        } catch (Exception e) {
            log.error("[Critic/{}] failed", inv.taskId(), e);
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), strongModel,
                    Map.of(), null,
                    0, 0, (int) (System.currentTimeMillis() - t0), "ERROR", e.getMessage());
            return AgentResult.error(e.getMessage());
        }
    }
}
