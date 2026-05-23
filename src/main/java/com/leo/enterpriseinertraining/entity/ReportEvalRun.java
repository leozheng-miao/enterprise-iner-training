package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 一次 LLM-as-Judge 评分结果（针对某个已完成 report_task）。
 * 5 维 rubric：structure / factuality / reasoning / citation / clarity，
 * 每维 0-10，{@code scoreOverall} = 5 维均值。{@code breakdownJson} 存逐项评语。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("report_eval_run")
public class ReportEvalRun extends BaseEntity {

    private Long taskId;
    private String judgeModel;
    private String rubricVersion;

    private Double scoreOverall;
    private Double scoreStructure;
    private Double scoreFactuality;
    private Double scoreReasoning;
    private Double scoreCitation;
    private Double scoreClarity;

    /** 各维度评语 JSON：{"structure":"...","factuality":"...",...} */
    private String breakdownJson;
    private Integer latencyMs;
}
