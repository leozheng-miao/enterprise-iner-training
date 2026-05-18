-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(64)  NOT NULL COMMENT '用户名',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码',
    `nickname`      VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `tenant_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '租户 ID',
    `role`          VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER / ADMIN',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
