package com.leo.enterpriseinertraining.llm.api;

/** LLM 供应商。4 者均走 OpenAI 协议，仅 base-url/api-key/model 不同。 */
public enum Provider {
    OPENAI, DEEPSEEK, DASHSCOPE, VLLM;

    public static Provider fromCode(String s) {
        return Provider.valueOf(s.trim().toUpperCase());
    }
}
