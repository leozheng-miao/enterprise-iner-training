package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 按模型聚合的 Token 用量与成本。
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ModelCostVO implements Serializable {

    private String model;
    private long calls;
    private long tokensIn;
    private long tokensOut;
    /** 该模型累计成本，单位：元。 */
    private double costCny;
    /** 平均单次调用耗时，无样本时为 null。 */
    private Long avgLatencyMs;
}
