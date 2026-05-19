package com.leo.enterpriseinertraining.agent.tool;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个 {@link AgentTool} 实现，让 Spring 自动作为 Bean 创建。
 * 等同于 {@code @Component}，但语义更明确。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface AgentToolMarker {
}
