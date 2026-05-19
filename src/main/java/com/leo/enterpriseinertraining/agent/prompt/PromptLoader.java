package com.leo.enterpriseinertraining.agent.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 从 classpath:prompts/*.txt 读取 prompt 文本，按 "name@version" 索引并缓存。
 *
 * <p>调用约定：{@code load("researcher_prompt@v1")} → 读 {@code prompts/researcher_prompt_v1.txt}。</p>
 * <p>阶段 4 平台中台时会切换到 prompt_template 表实现，本类的接口保持不变。</p>
 */
@Slf4j
@Component
public class PromptLoader {

    private final Map<String, String> cache = new HashMap<>();

    public String load(String ref) {
        return cache.computeIfAbsent(ref, this::readFile);
    }

    private String readFile(String ref) {
        int at = ref.indexOf('@');
        if (at < 0) throw new IllegalArgumentException("prompt ref 必须形如 name@version: " + ref);
        String name = ref.substring(0, at);
        String version = ref.substring(at + 1);
        String path = "prompts/" + name + "_" + version + ".txt";
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            log.info("[PromptLoader] loaded {} ({} chars)", ref, s.length());
            return s;
        } catch (Exception e) {
            throw new RuntimeException("无法加载 prompt: " + path, e);
        }
    }
}
