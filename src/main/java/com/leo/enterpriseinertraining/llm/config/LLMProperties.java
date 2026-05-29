package com.leo.enterpriseinertraining.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;

/** app.llm.providers.<name> = {base-url, api-key, model, temperature}。name ∈ openai/deepseek/dashscope/vllm。 */
@ConfigurationProperties(prefix = "app.llm")
public class LLMProperties {

    private Map<String, ProviderConfig> providers = Map.of();

    public Map<String, ProviderConfig> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderConfig> providers) { this.providers = providers; }

    public ProviderConfig get(String name) {
        ProviderConfig c = providers.get(name);
        if (c == null) throw new IllegalStateException("缺少 app.llm.providers." + name + " 配置");
        return c;
    }

    public static class ProviderConfig {
        private String baseUrl;
        private String apiKey;
        private String model;
        private double temperature = 0.7;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double v) { this.temperature = v; }
    }
}
