package com.leo.enterpriseinertraining.rag.store;

import java.util.List;

public interface VectorStore {

    /** 写入一批 chunk，返回它们在向量库内的 UUID。顺序与入参一致。 */
    List<String> upsertBatch(List<VectorRow> rows);

    /** 查询：传入查询向量、topK，返回 hit 列表（按 cosine 距离升序，即相似度降序）。 */
    List<VectorHit> search(float[] queryEmbedding, int topK);

    record VectorRow(
            long chunkId,
            long docId,
            String sectionTitle,
            int pageStart,
            int pageEnd,
            String content,
            float[] embedding
    ) {}

    record VectorHit(
            String vectorId,
            long chunkId,
            long docId,
            String sectionTitle,
            int pageStart,
            int pageEnd,
            String content,
            double score   // 1 - cosine_distance ∈ [0,1]，越大越相似
    ) {}
}
