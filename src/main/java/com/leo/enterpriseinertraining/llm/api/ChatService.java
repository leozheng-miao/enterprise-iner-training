package com.leo.enterpriseinertraining.llm.api;

/** Provider 维度的聊天服务。4 实现（OpenAI/DeepSeek/DashScope/vLLM）各返回自己的 provider()。 */
public interface ChatService {
    Provider provider();

    /** 该实现是否支持此 model 名（Stage A 简单返回 true；Stage B 可按 model 前缀细化）。 */
    boolean supports(String model);

    /** 调用：model 为空时用该 Provider 的默认模型。 */
    ChatResponse call(ChatRequest req, String model);
}
