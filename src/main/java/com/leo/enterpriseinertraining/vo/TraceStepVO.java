package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data @NoArgsConstructor @AllArgsConstructor
public class TraceStepVO implements Serializable {
    private Long id;
    private Integer stepSeq;
    private String nodeId;
    private String agentRole;
    private String stepType;
    private String promptVersion;
    private String model;
    private String toolName;
    private Integer tokensIn;
    private Integer tokensOut;
    private Integer latencyMs;
    private String status;
    private String errorMessage;
    private String inputJsonPreview;
    private String outputJsonPreview;
}
