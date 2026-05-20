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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlannerAgent implements Agent {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptLoader promptLoader;
    private final WorkflowNodeRunRecorder recorder;
    private final ObjectMapper om;

    @Value("${app.dashscope.chat-model-strong:qwen-max}")
    private String strongModel;

    @Override public String role() { return "Planner"; }

    @Override
    public AgentResult execute(AgentInvocation inv, SseSink sink) {
        long t0 = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.load(inv.promptRef());
            ChatClient client = chatClientBuilder.build();

            var response = client.prompt()
                    .system(systemPrompt)
                    .user(inv.topic())
                    .call()
                    .chatResponse();

            String raw = response.getResult().getOutput().getText();
            int tokensIn = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getPromptTokens().intValue();
            int tokensOut = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getCompletionTokens().intValue();
            int latency = (int) (System.currentTimeMillis() - t0);

            List<String> subtopics = parseSubtopics(raw);

            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), strongModel,
                    Map.of("topic", inv.topic()),
                    Map.of("subtopics", subtopics, "raw", raw),
                    tokensIn, tokensOut, latency, "OK", null);

            log.info("[Planner/{}] {} subtopics generated in {} ms", inv.taskId(), subtopics.size(), latency);

            String resultJson = om.writeValueAsString(Map.of("subtopics", subtopics));
            return AgentResult.ok(resultJson, List.of());
        } catch (Exception e) {
            log.error("[Planner/{}] failed", inv.taskId(), e);
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), strongModel,
                    Map.of("topic", inv.topic()), null,
                    0, 0, (int) (System.currentTimeMillis() - t0),
                    "ERROR", e.getMessage());
            return AgentResult.error(e.getMessage());
        }
    }

    private List<String> parseSubtopics(String raw) throws Exception {
        int l = raw.indexOf('{'), r = raw.lastIndexOf('}');
        String json = (l >= 0 && r > l) ? raw.substring(l, r + 1) : raw;
        JsonNode node = om.readTree(json);
        JsonNode arr = node.get("subtopics");
        if (arr == null || !arr.isArray()) {
            throw new RuntimeException("Planner 输出缺少 subtopics 数组");
        }
        List<String> result = new ArrayList<>();
        arr.forEach(n -> result.add(n.asText()));
        if (result.size() < 3) throw new RuntimeException("subtopics 数量过少: " + result.size());
        if (result.size() > 12) {
            log.warn("[Planner] subtopics 数量 {} 过多，截断到 12", result.size());
            return result.subList(0, 12);
        }
        return result;
    }
}
