package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 平台总览看板：任务量 / 成功率 / Token 成本 / 端到端耗时。
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
}
