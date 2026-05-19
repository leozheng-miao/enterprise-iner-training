package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("workflow_node_run")
public class WorkflowNodeRun extends BaseEntity {

    private Long taskId;
    private String nodeId;
    private String agentRole;
    private String stepType;       // LLM_CALL / TOOL_CALL
    private Integer stepSeq;
    private String promptVersion;
    private String model;
    private String toolName;
    private String inputJson;
    private String outputJson;
    private Integer tokensIn;
    private Integer tokensOut;
    private Integer latencyMs;
    private String status;         // OK / ERROR
    private String errorMessage;
}
