package com.leo.enterpriseinertraining.llm.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * ModelRouter 解析结果：某 Agent 当前生效的 provider/主模型/fallback 列表/prompt 版本。
 * toJson() 用于 Stage B 写 workflow_node_run.routing_snapshot 做事后归因。
 */
public record ResolvedRoute(
        Provider provider,
        String primaryModel,
        List<String> sameProviderFallbacks,
        List<ProviderModel> crossProviderFallbacks,
        boolean crossEnabled,
        String promptVersion) {

    private static final ObjectMapper OM = new ObjectMapper();

    public String toJson() {
        try {
            return OM.writeValueAsString(this);
        } catch (Exception e) {
            return "{\"_serializeError\":\"" + e.getMessage() + "\"}";
        }
    }
}
