package com.leo.enterpriseinertraining.rag.store;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PgVectorStoreImpl implements VectorStore {

    @Qualifier("pgVectorJdbcTemplate")
    private final JdbcTemplate jdbc;

    private static final String INSERT_SQL = """
        INSERT INTO knowledge_chunk_vec
            (id, chunk_id, doc_id, section_title, page_start, page_end, content, embedding)
        VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?::vector)
        """;

    private static final String SEARCH_SQL = """
        SELECT id::text AS id, chunk_id, doc_id, section_title, page_start, page_end,
               content, 1 - (embedding <=> ?::vector) AS score
        FROM knowledge_chunk_vec
        ORDER BY embedding <=> ?::vector
        LIMIT ?
        """;

    @Override
    public List<String> upsertBatch(List<VectorRow> rows) {
        List<String> ids = new ArrayList<>(rows.size());
        for (VectorRow r : rows) {
            String uuid = UUID.randomUUID().toString();
            jdbc.update(INSERT_SQL,
                    uuid, r.chunkId(), r.docId(), r.sectionTitle(),
                    r.pageStart(), r.pageEnd(), r.content(),
                    toPgVector(r.embedding()));
            ids.add(uuid);
        }
        return ids;
    }

    @Override
    public List<VectorHit> search(float[] q, int topK) {
        String qv = toPgVector(q);
        return jdbc.query(SEARCH_SQL,
                (rs, n) -> new VectorHit(
                        rs.getString("id"),
                        rs.getLong("chunk_id"),
                        rs.getLong("doc_id"),
                        rs.getString("section_title"),
                        rs.getInt("page_start"),
                        rs.getInt("page_end"),
                        rs.getString("content"),
                        rs.getDouble("score")),
                qv, qv, topK);
    }

    private static String toPgVector(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
