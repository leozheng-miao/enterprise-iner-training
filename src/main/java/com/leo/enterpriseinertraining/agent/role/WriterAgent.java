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
 * Writer Agent：写单个章节 markdown。
 * 输入（fanoutPayload）：{title, outline, materials} JSON；输出：markdown 字符串。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WriterAgent implements Agent {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptLoader promptLoader;
    private final WorkflowNodeRunRecorder recorder;
    private final ObjectMapper om;

    @Value("${app.dashscope.chat-model:qwen-plus}")
    private String defaultModel;

    @Override public String role() { return "Writer"; }

    @Override
    public AgentResult execute(AgentInvocation inv, SseSink sink) {
        long t0 = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.load(inv.promptRef());
            ChatClient client = chatClientBuilder.build();

            var response = client.prompt()
                    .system(systemPrompt)
                    .user(inv.fanoutPayload())
                    .call()
                    .chatResponse();

            String md = response.getResult().getOutput().getText();
            int tokensIn = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getPromptTokens().intValue();
            int tokensOut = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getCompletionTokens().intValue();
            int latency = (int) (System.currentTimeMillis() - t0);

            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), defaultModel,
                    Map.of("sectionOrder", inv.fanoutIndex(),
                            "inputPreview", inv.fanoutPayload().substring(0, Math.min(300, inv.fanoutPayload().length()))),
                    Map.of("mdPreview", md.substring(0, Math.min(300, md.length())), "mdLen", md.length()),
                    tokensIn, tokensOut, latency, "OK", null);

            log.info("[Writer/{}/{}] section markdown {} chars in {} ms",
                    inv.taskId(), inv.fanoutIndex(), md.length(), latency);
            return AgentResult.ok(md, List.of());
        } catch (Exception e) {
            log.error("[Writer/{}/{}] failed", inv.taskId(), inv.fanoutIndex(), e);
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), defaultModel,
                    Map.of("sectionOrder", inv.fanoutIndex()), null,
                    0, 0, (int) (System.currentTimeMillis() - t0), "ERROR", e.getMessage());
            return AgentResult.error(e.getMessage());
        }
    }
}
