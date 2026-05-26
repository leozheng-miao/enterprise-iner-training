package com.leo.enterpriseinertraining.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 平台总览看板：任务量 / 成功率 / Token 成本 / 端到端耗时 / 同比 / P95。
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class PlatformOverviewVO implements Serializable {

    private long totalTasks;
    private long doneTasks;
    private long failedTasks;
    private long runningTasks;
    /** 已结束任务中 DONE 占比，0-1。 */
    private double taskSuccessRate;

    private long totalNodeRuns;
    private long errorNodeRuns;

    private long totalTokensIn;
    private long totalTokensOut;
    /** 全平台累计 Token 成本，单位：元。 */
    private double totalCostCny;

    /** DONE 任务平均端到端耗时（finishedAt - startedAt），无样本时为 null。 */
    private Long avgTaskLatencyMs;

    // ── F4 #8 ──────────────────────────────────────────────
    @Schema(description = "P95 端到端耗时 ms；窗口内 DONE 任务 < 20 时返回 null")
    private Long p95TaskLatencyMs;

    // ── F4 #2 同比指标 ─────────────────────────────────────
    @Schema(description = "较上一窗口的任务总数变化量，正为增长")
    private Long totalTasksDelta;

    @Schema(description = "成功率绝对差，如 0.016 表示 +1.6 个百分点")
    private Double taskSuccessRateDelta;

    @Schema(description = "Token 成本变化量，单位元")
    private Double totalCostCnyDelta;

    @Schema(description = "平均耗时变化量，单位 ms")
    private Long avgTaskLatencyMsDelta;

    @Schema(description = "对比窗口标签，如 \"较昨日\"")
    private String compareWindowLabel;
}
