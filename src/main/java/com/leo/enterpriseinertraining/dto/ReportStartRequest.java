package com.leo.enterpriseinertraining.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ReportStartRequest implements Serializable {

    @NotBlank
    @Size(min = 4, max = 500)
    private String topic;

    /**
     * workflow 定义名，对应 resources/workflow/{name}.yaml。
     * 默认走 5 Agent 多智能体流程；researcher_only_v1 是阶段 2 的单 Agent 流程，
     * 不含 plan 节点，Orchestrator 无法编排，已不作为默认值。
     */
    @NotBlank
    private String workflow = "multi_agent_v1";
}
