package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;

/** routing_change_log：路由变更审计。ts=epoch millis。不继承 BaseEntity（无逻辑删除/更新时间需求）。 */
@Data
@Table("routing_change_log")
public class RoutingChangeLog {
    @com.mybatisflex.annotation.Id(keyType = com.mybatisflex.annotation.KeyType.Auto)
    private Long id;
    private Long tenantId;
    private String agentRole;
    private String changeType;       // CREATE / ACTIVATE / DEACTIVATE / UPDATE
    private String beforeSnapshot;   // JSON
    private String afterSnapshot;    // JSON
    private Long operatorUserId;
    private Long ts;
}
