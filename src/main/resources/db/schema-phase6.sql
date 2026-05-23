-- 阶段 6 DDL：LLM-as-Judge 研报质量评分表
--
-- RAG 检索层评估（recall / MRR / nDCG）已在阶段 1 实现（rag_eval_query + RagEvaluator）；
-- 本表存最终研报的「人感」评分，按 5 维 rubric 打分 + 评语，用更强模型（qwen-max）做评委。

CREATE TABLE IF NOT EXISTS `report_eval_run` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`          BIGINT       NOT NULL,
    `judge_model`      VARCHAR(64)  NOT NULL,
    `rubric_version`   VARCHAR(32)  NOT NULL DEFAULT 'v1',
    `score_overall`    DECIMAL(4,2) DEFAULT NULL COMMENT '5 维均值',
    `score_structure`  DECIMAL(4,2) DEFAULT NULL,
    `score_factuality` DECIMAL(4,2) DEFAULT NULL,
    `score_reasoning`  DECIMAL(4,2) DEFAULT NULL,
    `score_citation`   DECIMAL(4,2) DEFAULT NULL,
    `score_clarity`    DECIMAL(4,2) DEFAULT NULL,
    `breakdown_json`   MEDIUMTEXT   DEFAULT NULL COMMENT 'per-dim 评语 JSON',
    `latency_ms`       INT          NOT NULL DEFAULT 0,
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_task` (`task_id`),
    KEY `idx_task_time` (`task_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM-as-Judge 研报质量评分';
