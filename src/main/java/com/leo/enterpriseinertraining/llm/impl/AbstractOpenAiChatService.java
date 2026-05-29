package com.leo.enterpriseinertraining.llm.impl;

import com.leo.enterpriseinertraining.llm.api.ChatRequest;
import com.leo.enterpriseinertraining.llm.api.ChatResponse;
import com.leo.enterpriseinertraining.llm.api.ChatService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.ArrayList;
import java.util.List;

/** 4 Provider 共用：ChatRequest → Spring AI Prompt → 调用 → 网关 ChatResponse。 */
public abstract class AbstractOpenAiChatService implements ChatService {

    protected final OpenAiChatModel chatModel;

    protected AbstractOpenAiChatService(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public boolean supports(String model) {
        return true;   // Stage A 不按 model 细分；Stage B 可按前缀收紧
    }

    @Override
    public ChatResponse call(ChatRequest req, String model) {
        List<Message> messages = new ArrayList<>();
        if (req.systemText() != null && !req.systemText().isBlank()) {
            messages.add(new SystemMessage(req.systemText()));
        }
        messages.add(new UserMessage(req.userText() == null ? "" : req.userText()));

        Prompt prompt = (model == null || model.isBlank())
            ? new Prompt(messages)
            : new Prompt(messages, OpenAiChatOptions.builder().model(model).build());

        org.springframework.ai.chat.model.ChatResponse resp = chatModel.call(prompt);

        String content = resp.getResult() == null ? ""
            : resp.getResult().getOutput().getText();
        Usage usage = resp.getMetadata() == null ? null : resp.getMetadata().getUsage();
        int in = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int out = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        String actualModel = resp.getMetadata() == null ? model : resp.getMetadata().getModel();
        String finish = resp.getResult() == null || resp.getResult().getMetadata() == null
            ? null : resp.getResult().getMetadata().getFinishReason();

        return new ChatResponse(content, actualModel == null ? model : actualModel, in, out, finish);
    }
}
