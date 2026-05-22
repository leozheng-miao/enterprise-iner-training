package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 按 Agent 角色聚合的 Token 用量与成本。
 *
 * <p>同一角色可能跨多个模型调用，成本为各模型分段单价之和。</p>
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class AgentCostVO implements Serializable {

    private String agentRole;
    private long calls;
    private long tokensIn;
    private long tokensOut;
    /** 该角色累计成本，单位：元。 */
    private double costCny;
    /** 平均单次节点耗时，无样本时为 null。 */
    private Long avgLatencyMs;
}
