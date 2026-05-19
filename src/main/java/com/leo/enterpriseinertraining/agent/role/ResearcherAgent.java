package com.leo.enterpriseinertraining.agent.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.*;
import com.leo.enterpriseinertraining.agent.prompt.PromptLoader;
import com.leo.enterpriseinertraining.agent.tool.AgentTool;
import com.leo.enterpriseinertraining.agent.tool.ToolInvocationTracer;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.trace.WorkflowNodeRunRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Researcher Agent：Spring AI 1.0 Function Calling + 工具调用 Trace。
 *
 * <p>实现思路：</p>
 * <ol>
 *   <li>把 {@link AgentTool} 列表通过 {@link FunctionToolCallback} 桥接成 Spring AI 工具</li>
 *   <li>桥接 lambda 内部包了一层：执行前推 SSE event:tool、执行后写 workflow_node_run TOOL_CALL 行</li>
 *   <li>{@code ChatClient.prompt().toolCallbacks(...).call()} 同步等到最终回答</li>
 *   <li>结束后写 workflow_node_run LLM_CALL 行，提取 markdown + 引用</li>
 *   <li>把最终 markdown 切片当作 token 事件推给 SSE（简化版；阶段 3 改用 .stream() 真流式）</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResearcherAgent implements Agent {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptLoader promptLoader;
    private final WorkflowNodeRunRecorder recorder;
    private final ToolInvocationTracer tracer;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${app.dashscope.chat-model:qwen-plus}")
    private String defaultModel;

    @Override
    public String role() {
        return "Researcher";
    }

    @Override
    public AgentResult execute(AgentInvocation inv, SseSink sink) {
        long t0 = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.load(inv.promptRef());

            // 1) 桥接工具
            List<ToolCallback> callbacks = new ArrayList<>();
            for (AgentTool t : inv.tools()) {
                callbacks.add(toCallback(t, inv, sink));
            }

            // 2) 调 LLM 同步
            ChatClient client = chatClientBuilder.build();
            long llmStart = System.currentTimeMillis();
            var response = client.prompt()
                    .system(systemPrompt)
                    .user(inv.topic())
                    .toolCallbacks(callbacks.toArray(ToolCallback[]::new))
                    .call()
                    .chatResponse();
            long llmLatency = System.currentTimeMillis() - llmStart;

            String content = response.getResult().getOutput().getText();
            int tokensIn = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getPromptTokens().intValue();
            int tokensOut = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getCompletionTokens().intValue();

            // 3) 写 LLM_CALL trace
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), defaultModel,
                    Map.of("topic", inv.topic()),
                    Map.of("content", content),
                    tokensIn, tokensOut, (int) llmLatency,
                    "OK", null);

            // 4) 切片推 token 事件（简化流式）
            if (sink != null && !sink.isClosed()) {
                for (int i = 0; i < content.length(); i += 64) {
                    sink.token(content.substring(i, Math.min(i + 64, content.length())));
                }
            }

            // 5) 引用聚合（阶段 2 简化：让 LLM 在 markdown 末尾自己列；trace 表里的 hits 留给阶段 3 解析）
            List<Citation> citations = List.of();

            log.info("[Researcher/{}] done in {} ms (LLM {} ms), tokensIn={} tokensOut={}",
                    inv.taskId(), System.currentTimeMillis() - t0, llmLatency, tokensIn, tokensOut);
            return AgentResult.ok(content, citations);

        } catch (Exception e) {
            log.error("[Researcher/{}] failed", inv.taskId(), e);
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), defaultModel,
                    Map.of("topic", inv.topic()), null,
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
                if (sink != null) {
                    String paramsJson = safeJson(params);
                    sink.tool(t.name(), paramsJson, "invoking...");
                }
                Object result = t.invoke(params);
                int latency = (int) (System.currentTimeMillis() - start);
                tracer.recordSuccess(inv.taskId(), inv.nodeId(), role(), t.name(),
                        params, result, latency);
                return result;
            } catch (Exception e) {
                int latency = (int) (System.currentTimeMillis() - start);
                tracer.recordError(inv.taskId(), inv.nodeId(), role(), t.name(),
                        params, e, latency);
                throw new RuntimeException("tool '" + t.name() + "' invoke failed", e);
            }
        };

        return FunctionToolCallback.builder(t.name(), wrapped)
                .description(t.description())
                .inputType((Class) t.paramsType())
                .build();
    }

    private String safeJson(Object o) {
        try { return om.writeValueAsString(o); } catch (Exception e) { return ""; }
    }
}
