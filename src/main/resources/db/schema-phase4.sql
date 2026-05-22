-- 阶段 4 DDL：Prompt 版本管理表
--
-- 执行后重启后端：PromptSeeder 会在首次启动时把 classpath:prompts/*.txt
-- 自动导入本表（每个 name 的最大版本设为 is_active=1）。
-- 未执行本脚本也不影响运行——PromptLoader 会回退到 classpath 文件。

CREATE TABLE IF NOT EXISTS `prompt_template` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `name`         VARCHAR(64)  NOT NULL COMMENT 'prompt 名，如 researcher_prompt',
    `version`      VARCHAR(32)  NOT NULL COMMENT '版本号，如 v1 / v2',
    `content`      MEDIUMTEXT   NOT NULL COMMENT 'prompt 正文',
    `model`        VARCHAR(64)  DEFAULT NULL COMMENT '建议模型（可选，元信息）',
    `temperature`  DECIMAL(3,2) DEFAULT NULL COMMENT '建议温度（可选，元信息）',
    `is_active`    TINYINT      NOT NULL DEFAULT 0 COMMENT '1=灰度生效版本，同名至多一条',
    `description`  VARCHAR(255) DEFAULT NULL COMMENT '版本备注',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name_version` (`name`, `version`),
    KEY `idx_name_active` (`name`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt 模板版本管理';
