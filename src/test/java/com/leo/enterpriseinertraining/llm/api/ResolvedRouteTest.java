package com.leo.enterpriseinertraining.llm.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ResolvedRouteTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void toJson_serializesAllRoutingFields() throws Exception {
        ResolvedRoute r = new ResolvedRoute(
            Provider.OPENAI, "gpt-4o",
            List.of("gpt-4o-mini"),
            List.of(new ProviderModel(Provider.DASHSCOPE, "qwen-max")),
            false, "v3");

        JsonNode n = om.readTree(r.toJson());

        assertThat(n.get("provider").asText()).isEqualTo("OPENAI");
        assertThat(n.get("primaryModel").asText()).isEqualTo("gpt-4o");
        assertThat(n.get("sameProviderFallbacks").get(0).asText()).isEqualTo("gpt-4o-mini");
        assertThat(n.get("crossProviderFallbacks").get(0).get("provider").asText()).isEqualTo("DASHSCOPE");
        assertThat(n.get("crossProviderFallbacks").get(0).get("model").asText()).isEqualTo("qwen-max");
        assertThat(n.get("crossEnabled").asBoolean()).isFalse();
        assertThat(n.get("promptVersion").asText()).isEqualTo("v3");
    }

    @Test
    void toJson_handlesEmptyFallbackLists() throws Exception {
        ResolvedRoute r = new ResolvedRoute(
            Provider.DASHSCOPE, "qwen-max", List.of(), List.of(), false, null);
        JsonNode n = om.readTree(r.toJson());
        assertThat(n.get("sameProviderFallbacks")).isEmpty();
        assertThat(n.get("crossProviderFallbacks")).isEmpty();
    }
}
