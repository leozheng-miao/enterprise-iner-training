package com.leo.enterpriseinertraining.workflow;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowDef {
    private String name;
    private Integer version;
    private List<WorkflowNode> nodes;
}
