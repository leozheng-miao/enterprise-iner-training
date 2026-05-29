package com.leo.enterpriseinertraining.llm.impl;

import com.leo.enterpriseinertraining.llm.api.Provider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class DeepSeekChatServiceImpl extends AbstractOpenAiChatService {
    public DeepSeekChatServiceImpl(@Qualifier("deepSeekChatModel") OpenAiChatModel m) { super(m); }
    @Override public Provider provider() { return Provider.DEEPSEEK; }
}
