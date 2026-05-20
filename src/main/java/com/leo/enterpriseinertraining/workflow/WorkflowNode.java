package com.leo.enterpriseinertraining.workflow;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowNode {
    private String id;
    private String agent;
    private String prompt;
    private String model;                  // 阶段 3 新增：覆盖默认模型
    private List<String> tools;
    private FanoutSpec fanout;              // 阶段 3 新增
    private String join;                    // 阶段 3 新增："all" 等
    private List<String> next;              // 阶段 3 新增：YAML 是 list
    private Integer maxLoops;               // 阶段 3 新增（Critic 用）
    private String onNeedsRevision;         // 阶段 3 新增（指向回环目标 nodeId）
}
