package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("tool_registry")
public class ToolRegistry extends BaseEntity {

    private String name;
    private String description;
    private String paramsSchema;
    private String handlerBean;
    private Integer enabled;
}
