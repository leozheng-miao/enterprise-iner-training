-- 阶段 2 表：研究任务 + Trace + Tool 注册

CREATE TABLE IF NOT EXISTS `report_task` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT       NOT NULL,
    `topic`           VARCHAR(512) NOT NULL,
    `workflow_name`   VARCHAR(64)  NOT NULL,
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
    `final_markdown`  MEDIUMTEXT   DEFAULT NULL,
    `citations_json`  JSON         DEFAULT NULL,
    `error_message`   VARCHAR(1024) DEFAULT NULL,
    `started_at`      DATETIME     DEFAULT NULL,
    `finished_at`     DATETIME     DEFAULT NULL,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='研究任务';

CREATE TABLE IF NOT EXISTS `workflow_node_run` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`        BIGINT       NOT NULL,
    `node_id`        VARCHAR(64)  NOT NULL,
    `agent_role`     VARCHAR(32)  NOT NULL,
    `step_type`      VARCHAR(16)  NOT NULL COMMENT 'LLM_CALL / TOOL_CALL',
    `step_seq`       INT          NOT NULL,
    `prompt_version` VARCHAR(64)  DEFAULT NULL,
    `model`          VARCHAR(64)  DEFAULT NULL,
    `tool_name`      VARCHAR(64)  DEFAULT NULL,
    `input_json`     MEDIUMTEXT   DEFAULT NULL,
    `output_json`    MEDIUMTEXT   DEFAULT NULL,
    `tokens_in`      INT          NOT NULL DEFAULT 0,
    `tokens_out`     INT          NOT NULL DEFAULT 0,
    `latency_ms`     INT          NOT NULL DEFAULT 0,
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'OK' COMMENT 'OK / ERROR',
    `error_message`  VARCHAR(1024) DEFAULT NULL,
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_task_node_seq` (`task_id`, `node_id`, `step_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Workflow 节点 Trace';

CREATE TABLE IF NOT EXISTS `tool_registry` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(64)  NOT NULL COMMENT '工具名（LLM 看到的）',
    `description`    VARCHAR(512) NOT NULL COMMENT '喂给 LLM 的描述',
    `params_schema`  JSON         DEFAULT NULL COMMENT 'JSON Schema',
    `handler_bean`   VARCHAR(128) NOT NULL COMMENT 'Spring Bean 类名',
    `enabled`        TINYINT      NOT NULL DEFAULT 1,
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 工具注册表';
