package com.leo.enterpriseinertraining.workflow;

import lombok.Data;

/**
 * fanout 配置：
 * <pre>
 * fanout:
 *   from: "${plan.subtopics}"
 * </pre>
 */
@Data
public class FanoutSpec {
    /** 引用前序节点输出的表达式，如 {@code ${plan.subtopics}}。 */
    private String from;
}
