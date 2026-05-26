package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 一次 LLM-as-Judge 评分的视图。{@code comments} 是 breakdown JSON 解析后的 Map，
 * 方便前端按维度展示评语。
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class JudgeRunVO implements Serializable {

    private Long id;
    private Long taskId;
    private String judgeModel;
    private String rubricVersion;

    private Double overall;
    private Double structure;
    private Double factuality;
    private Double reasoning;
    private Double citation;
    private Double clarity;

    /** dim -> 评语 */
    private Map<String, String> comments;

    private Integer latencyMs;
    private Long createTime;

    /** 关联任务的研究主题，便于前端列表展示。null 表示反查任务失败。 */
    private String topic;
}
