package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("workflow_loop_state")
public class WorkflowLoopState extends BaseEntity {

    private Long taskId;
    private String nodeId;
    private Integer loopCount;
    private Integer maxLoops;
}
