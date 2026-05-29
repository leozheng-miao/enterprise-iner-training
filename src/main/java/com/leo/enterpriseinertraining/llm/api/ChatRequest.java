package com.leo.enterpriseinertraining.llm.api;

import java.util.Map;

public record ChatRequest(String systemText, String userText,
                          Map<String, Object> vars, String modelOverride) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String systemText;
        private String userText;
        private Map<String, Object> vars;
        private String modelOverride;

        public Builder systemText(String v) { this.systemText = v; return this; }
        public Builder userText(String v) { this.userText = v; return this; }
        public Builder vars(Map<String, Object> v) { this.vars = v; return this; }
        public Builder modelOverride(String v) { this.modelOverride = v; return this; }

        public ChatRequest build() {
            return new ChatRequest(systemText, userText,
                vars == null ? Map.of() : vars, modelOverride);
        }
    }
}
