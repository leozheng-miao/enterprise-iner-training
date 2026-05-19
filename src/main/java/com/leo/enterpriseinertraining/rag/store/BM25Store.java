package com.leo.enterpriseinertraining.rag.store;

import java.util.List;

public interface BM25Store {

    /** 写入一批 chunk，返回 ES _id 列表（顺序与入参一致）。 */
    List<String> upsertBatch(List<BM25Row> rows);

    List<BM25Hit> search(String queryText, int topK);

    record BM25Row(
            long chunkId,
            long docId,
            String sectionTitle,
            int pageStart,
            int pageEnd,
            String content
    ) {}

    record BM25Hit(
            String esDocId,
            long chunkId,
            long docId,
            String sectionTitle,
            int pageStart,
            int pageEnd,
            String content,
            double score
    ) {}
}
