package com.leo.enterpriseinertraining.workflow;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowNode {
    private String id;
    private String agent;
    private String prompt;
    private List<String> tools;
}
