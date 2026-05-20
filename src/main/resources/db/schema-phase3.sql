-- 阶段 3 DDL：3 张新表 + report_task 加 phase/progress

-- 1) report_task 加 phase / progress
ALTER TABLE `report_task`
  ADD COLUMN `phase` VARCHAR(32) NOT NULL DEFAULT 'PLANNING'
    COMMENT 'PLANNING/RESEARCHING/ANALYZING/WRITING/CRITICIZING/DONE' AFTER `status`,
  ADD COLUMN `progress` INT NOT NULL DEFAULT 0 COMMENT '0-100 前端进度条' AFTER `phase`;

-- 2) workflow_subtask: fanout 子任务状态表
CREATE TABLE IF NOT EXISTS `workflow_subtask` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`        BIGINT       NOT NULL,
    `sub_index`      INT          NOT NULL,
    `subtopic`       VARCHAR(512) NOT NULL,
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
    `result_json`    MEDIUMTEXT   DEFAULT NULL,
    `error_message`  VARCHAR(1024) DEFAULT NULL,
    `started_at`     DATETIME     DEFAULT NULL,
    `finished_at`    DATETIME     DEFAULT NULL,
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_subindex` (`task_id`, `sub_index`),
    KEY `idx_task_status` (`task_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Fanout 子任务状态';

-- 3) report_section: 研报章节存储
CREATE TABLE IF NOT EXISTS `report_section` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`         BIGINT       NOT NULL,
    `section_order`   INT          NOT NULL,
    `title`           VARCHAR(255) NOT NULL,
    `outline`         MEDIUMTEXT   DEFAULT NULL COMMENT 'Analyst 输出的章节大纲',
    `related_subtopics_json` JSON  DEFAULT NULL COMMENT '[0,2,5] 关联的 subtopic 索引',
    `content_md`      MEDIUMTEXT   DEFAULT NULL,
    `citations_json`  JSON         DEFAULT NULL,
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/FINAL/REVISING',
    `revision_count`  INT          NOT NULL DEFAULT 0,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_section_order` (`task_id`, `section_order`),
    KEY `idx_task_status` (`task_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='研报章节';

-- 4) workflow_loop_state: Critic 回环计数
CREATE TABLE IF NOT EXISTS `workflow_loop_state` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`      BIGINT       NOT NULL,
    `node_id`      VARCHAR(64)  NOT NULL,
    `loop_count`   INT          NOT NULL DEFAULT 0,
    `max_loops`    INT          NOT NULL DEFAULT 1,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_node` (`task_id`, `node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Workflow 回环计数';
