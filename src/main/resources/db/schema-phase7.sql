-- 阶段 7（F4）：Workflow 加载审计表 + report_task.create_time 索引（同比/P95 SQL 使用）

CREATE TABLE IF NOT EXISTS `workflow_load_log` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `event_type`       VARCHAR(32)  NOT NULL COMMENT 'cache_clear / yaml_reload / topology_check / activate',
    `message`          VARCHAR(512) NOT NULL,
    `level`            VARCHAR(16)  NOT NULL DEFAULT 'info' COMMENT 'info / success / warning / error',
    `workflow_name`    VARCHAR(128),
    `workflow_version` VARCHAR(32),
    `ts`               BIGINT       NOT NULL COMMENT 'epoch millis',
    PRIMARY KEY (`id`),
    KEY `idx_ts` (`ts` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Workflow 加载与重载操作审计';

-- report_task 现有索引只覆盖 user_id / status / tenant_id；为 F4 同比聚合和 P95 SQL 加 create_time 索引。
-- MySQL 8.0.29+ 支持 ALTER TABLE ... ADD KEY IF NOT EXISTS；旧版本如报错请手工跳过本行。
ALTER TABLE `report_task` ADD KEY IF NOT EXISTS `idx_create_time` (`create_time`);
