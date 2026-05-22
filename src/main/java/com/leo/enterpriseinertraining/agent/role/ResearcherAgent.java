package com.leo.enterpriseinertraining.agent.role;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.*;
import com.leo.enterpriseinertraining.agent.prompt.PromptLoader;
import com.leo.enterpriseinertraining.agent.tool.AgentTool;
import com.leo.enterpriseinertraining.agent.tool.ToolInvocationTracer;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.trace.WorkflowNodeRunRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Researcher Agent（阶段 3 版）：处理单个 subtopic（来自 fanout），调 hybrid_search 工具，
 * 输出 JSON {content, citations}。
 *
 * <p>与阶段 2 区别：阶段 2 输入 inv.topic() 写整段 markdown；阶段 3 输入 inv.fanoutPayload()
 * （单个 subtopic）输出结构化 JSON 给 Analyst 聚合用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResearcherAgent implements Agent {

    private final ChatClient.Builder chatClientBuilder;

    /** ChatClient.Builder 并发 build() 不安全；启动时 build 一次，之后多线程复用（ChatClient 本身线程安全）。 */
    private ChatClient chatClient;

    @PostConstruct
    void initChatClient() {
        this.chatClient = chatClientBuilder.build();
    }
    private final PromptLoader promptLoader;
    private final WorkflowNodeRunRecorder recorder;
    private final ToolInvocationTracer tracer;
    private final ObjectMapper om;

    @Value("${app.dashscope.chat-model:qwen-plus}")
    private String defaultModel;

    @Override public String role() { return "Researcher"; }

    @Override
    public AgentResult execute(AgentInvocation inv, SseSink sink) {
        long t0 = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.load(inv.promptRef());
            // 阶段 3：fanoutPayload 是 subtopic 文本；阶段 2 用 topic 字段（向后兼容）
            String userInput = inv.fanoutPayload() != null ? inv.fanoutPayload() : inv.topic();

            List<ToolCallback> callbacks = new ArrayList<>();
            for (AgentTool t : inv.tools()) {
                callbacks.add(toCallback(t, inv, sink));
            }

            ChatClient client = this.chatClient;
            long llmStart = System.currentTimeMillis();
            var response = client.prompt()
                    .system(systemPrompt)
                    .user("研究子主题：" + userInput)
                    .toolCallbacks(callbacks.toArray(ToolCallback[]::new))
                    .call()
                    .chatResponse();
            long llmLatency = System.currentTimeMillis() - llmStart;

            String raw = response.getResult().getOutput().getText();
            int tokensIn = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getPromptTokens().intValue();
            int tokensOut = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getCompletionTokens().intValue();

            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), defaultModel,
                    Map.of("subtopic", userInput, "fanoutIndex", inv.fanoutIndex()),
                    Map.of("raw", raw),
                    tokensIn, tokensOut, (int) llmLatency, "OK", null);

            // 解析输出 JSON：{content, citations}
            String json = extractJson(raw);
            JsonNode node = om.readTree(json);
            String content = node.has("content") ? node.get("content").asText() : raw;
            List<Citation> citations = parseCitations(node);

            log.info("[Researcher/{}/{}] done in {} ms (LLM {} ms)", inv.taskId(), inv.fanoutIndex(),
                    System.currentTimeMillis() - t0, llmLatency);
            // markdown 字段返回完整 json（含 content + citations），上游 Worker 落到 workflow_subtask.result_json
            return AgentResult.ok(json, citations);
        } catch (Exception e) {
            log.error("[Researcher/{}/{}] failed", inv.taskId(), inv.fanoutIndex(), e);
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), defaultModel,
                    Map.of("subtopic", inv.fanoutPayload(), "fanoutIndex", inv.fanoutIndex()), null,
                    0, 0, (int) (System.currentTimeMillis() - t0),
                    "ERROR", e.getMessage());
            return AgentResult.error(e.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ToolCallback toCallback(AgentTool t, AgentInvocation inv, SseSink sink) {
        Function<Object, Object> wrapped = (Object params) -> {
            long start = System.currentTimeMillis();
            try {
                if (sink != null) sink.tool(t.name(), safeJson(params), "invoking...");
                Object result = t.invoke(params);
                int latency = (int) (System.currentTimeMillis() - start);
                tracer.recordSuccess(inv.taskId(), inv.nodeId(), role(), t.name(), params, result, latency);
                return result;
            } catch (Exception e) {
                int latency = (int) (System.currentTimeMillis() - start);
                tracer.recordError(inv.taskId(), inv.nodeId(), role(), t.name(), params, e, latency);
                throw new RuntimeException("tool '" + t.name() + "' invoke failed", e);
            }
        };
        return FunctionToolCallback.builder(t.name(), wrapped)
                .description(t.description())
                .inputType((Class) t.paramsType())
                .build();
    }

    private String extractJson(String s) {
        int l = s.indexOf('{'), r = s.lastIndexOf('}');
        return (l >= 0 && r > l) ? s.substring(l, r + 1) : s;
    }

    private List<Citation> parseCitations(JsonNode root) {
        List<Citation> result = new ArrayList<>();
        JsonNode arr = root.get("citations");
        if (arr != null && arr.isArray()) {
            for (JsonNode c : arr) {
                result.add(new Citation(
                        c.has("docId") ? c.get("docId").asLong() : null,
                        c.has("docTitle") ? c.get("docTitle").asText() : null,
                        c.has("source") ? c.get("source").asText() : null,
                        c.has("sectionTitle") ? c.get("sectionTitle").asText() : null,
                        c.has("pageStart") ? c.get("pageStart").asInt() : null,
                        c.has("pageEnd") ? c.get("pageEnd").asInt() : null));
            }
        }
        return result;
    }

    private String safeJson(Object o) {
        try { return om.writeValueAsString(o); } catch (Exception e) { return ""; }
    }
}
