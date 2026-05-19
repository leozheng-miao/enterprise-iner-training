-- 阶段 1 RAG 元数据表

CREATE TABLE IF NOT EXISTS `knowledge_doc` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`        VARCHAR(255) NOT NULL COMMENT '文档标题',
    `source`       VARCHAR(64)  NOT NULL COMMENT '来源：CAICT/MIIT/NDRC/CCID/COMPANY-XXX',
    `file_uri`     VARCHAR(512) NOT NULL COMMENT 'PDF 相对路径',
    `total_pages`  INT          NOT NULL DEFAULT 0 COMMENT 'PDF 总页数',
    `content_hash` CHAR(64)     NOT NULL COMMENT 'PDF 内容 SHA-256，去重',
    `ingested_at`  DATETIME     DEFAULT NULL COMMENT '完成入库时间',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 文档元数据';

CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `doc_id`        BIGINT       NOT NULL COMMENT '关联 knowledge_doc.id',
    `section_title` VARCHAR(255) DEFAULT NULL COMMENT '章节标题（来自 PDF outline）',
    `page_start`    INT          NOT NULL,
    `page_end`      INT          NOT NULL,
    `char_len`      INT          NOT NULL,
    `vector_id`     CHAR(36)     NOT NULL COMMENT 'PGVector 行 UUID',
    `es_doc_id`     VARCHAR(64)  NOT NULL COMMENT 'ES _id',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_doc_id` (`doc_id`),
    KEY `idx_vector_id` (`vector_id`),
    KEY `idx_es_doc_id` (`es_doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG chunk 轻量元数据';

CREATE TABLE IF NOT EXISTS `rag_eval_query` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `query_text`     VARCHAR(512) NOT NULL,
    `gold_chunk_ids` JSON         NOT NULL COMMENT '标准答案 chunk_id 列表',
    `query_source`   VARCHAR(16)  NOT NULL DEFAULT 'synthesized' COMMENT 'synthesized / human',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 评估数据集';
