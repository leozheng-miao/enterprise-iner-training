package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 任务列表行：管理后台分页展示用的精简视图（不含 finalMarkdown 大字段）。
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class TaskBriefVO implements Serializable {

    private Long id;
    private Long userId;
    private String topic;
    private String status;
    private String phase;
    private Integer progress;
    private String errorMessage;
    /** 端到端耗时（finishedAt - startedAt），未结束时为 null。 */
    private Long latencyMs;
    /** 创建时间，epoch 毫秒。 */
    private Long createdAt;
}
