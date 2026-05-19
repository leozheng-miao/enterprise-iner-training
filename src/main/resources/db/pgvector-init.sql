CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS knowledge_chunk_vec (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    chunk_id      BIGINT NOT NULL,
    doc_id        BIGINT NOT NULL,
    section_title TEXT,
    page_start    INT,
    page_end      INT,
    content       TEXT NOT NULL,
    embedding     vector(1024) NOT NULL,
    created_at    TIMESTAMP DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_kcv_chunk_id ON knowledge_chunk_vec (chunk_id);
CREATE INDEX IF NOT EXISTS idx_kcv_doc_id   ON knowledge_chunk_vec (doc_id);

CREATE INDEX IF NOT EXISTS idx_kcv_embedding_hnsw
    ON knowledge_chunk_vec
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
