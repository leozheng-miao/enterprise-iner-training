package com.leo.enterpriseinertraining.agent.tool.impl;

import com.leo.enterpriseinertraining.agent.tool.AgentTool;
import com.leo.enterpriseinertraining.agent.tool.AgentToolMarker;
import com.leo.enterpriseinertraining.dto.RagSearchRequest;
import com.leo.enterpriseinertraining.service.RagSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hybrid 检索工具：把阶段 1 的 RagSearchService 暴露给 LLM Function Calling。
 *
 * <p>LLM 视角的"调用签名"：</p>
 * <pre>
 * hybrid_search(query: string, top_k?: int = 5) → { hits: [...], tookMs }
 * </pre>
 */
@Slf4j
@AgentToolMarker
@RequiredArgsConstructor
public class HybridSearchTool implements AgentTool {

    private final RagSearchService searchService;

    /** 参数 record（公开为 LLM 可见的 schema）。 */
    public record Params(String query, Integer topK) {}

    @Override
    public String name() {
        return "hybrid_search";
    }

    @Override
    public String description() {
        return "在企业内置的行业研报知识库中做 Hybrid 检索（向量 + BM25 + Rerank），" +
               "返回 Top-K 个相关文本片段及其引用（文档标题、来源机构、章节、页码）。" +
               "当你需要查阅行业数据、政策、公司动态或专业术语时使用本工具。";
    }

    @Override
    public Class<?> paramsType() {
        return Params.class;
    }

    @Override
    public Object invoke(Object params) {
        Params p = (Params) params;
        if (p == null || p.query() == null || p.query().isBlank()) {
            throw new IllegalArgumentException("hybrid_search: query 不能为空");
        }
        int topK = p.topK() == null || p.topK() <= 0 ? 5 : Math.min(p.topK(), 20);

        RagSearchRequest req = new RagSearchRequest();
        req.setQuery(p.query());
        req.setTopK(topK);
        req.setUseRerank(true);    // Agent 路径默认走 rerank（阶段 1 已验证 Lift ≥ 0.10）

        var result = searchService.search(req);
        log.info("[hybrid_search] query='{}' topK={} hits={} tookMs={}",
                p.query(), topK, result.hits().size(), result.tookMs());
        return result;             // 直接返回 SearchResult（hits + tookMs）
    }
}
