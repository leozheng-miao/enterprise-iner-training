package com.leo.enterpriseinertraining.rag.store;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchBM25Store implements BM25Store {

    private final ElasticsearchClient es;

    @Value("${rag.elasticsearch.index}")
    private String indexName;

    @Override
    public List<String> upsertBatch(List<BM25Row> rows) {
        if (rows.isEmpty()) return List.of();
        BulkRequest.Builder br = new BulkRequest.Builder();
        for (BM25Row r : rows) {
            Map<String, Object> doc = new HashMap<>();
            doc.put("chunk_id", r.chunkId());
            doc.put("doc_id", r.docId());
            doc.put("section_title", r.sectionTitle());
            doc.put("page_start", r.pageStart());
            doc.put("page_end", r.pageEnd());
            doc.put("content", r.content());
            br.operations(op -> op.index(i -> i.index(indexName).document(doc)));
        }
        try {
            BulkResponse resp = es.bulk(br.build());
            if (resp.errors()) {
                log.warn("[ES] bulk has errors");
            }
            List<String> ids = new ArrayList<>(rows.size());
            for (BulkResponseItem item : resp.items()) ids.add(item.id());
            return ids;
        } catch (Exception e) {
            throw new RuntimeException("ES bulk upsert 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<BM25Hit> search(String queryText, int topK) {
        try {
            SearchResponse<Map> r = es.search(s -> s
                    .index(indexName)
                    .size(topK)
                    .query(q -> q.multiMatch(m -> m
                            .query(queryText)
                            .fields("content^1.0", "section_title^1.5"))),
                    Map.class);
            List<BM25Hit> hits = new ArrayList<>();
            for (var hit : r.hits().hits()) {
                Map src = hit.source();
                if (src == null) continue;
                hits.add(new BM25Hit(
                        hit.id(),
                        ((Number) src.get("chunk_id")).longValue(),
                        ((Number) src.get("doc_id")).longValue(),
                        (String) src.get("section_title"),
                        ((Number) src.get("page_start")).intValue(),
                        ((Number) src.get("page_end")).intValue(),
                        (String) src.get("content"),
                        hit.score() == null ? 0.0 : hit.score()));
            }
            return hits;
        } catch (Exception e) {
            throw new RuntimeException("ES search 失败: " + e.getMessage(), e);
        }
    }
}
