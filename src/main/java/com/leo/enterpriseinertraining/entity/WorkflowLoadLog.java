package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * Workflow 加载与重载审计记录。
 * 与 BaseEntity 不同：这张表本身就是日志，不需要 create_time / update_time / is_deleted。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("workflow_load_log")
public class WorkflowLoadLog implements Serializable {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String eventType;
    private String message;
    private String level;
    private String workflowName;
    private String workflowVersion;
    private Long ts;
}
