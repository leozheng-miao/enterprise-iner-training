package com.leo.enterpriseinertraining.agent.tool;

/**
 * Agent 可调用的工具 SPI。
 *
 * <p>实现类需要：
 * <ol>
 *   <li>声明 {@link AgentToolMarker @AgentToolMarker} 注解（也是 {@code @Component}）</li>
 *   <li>实现本接口的 4 个方法</li>
 * </ol>
 * 启动时由 {@link ToolRegistryService} 自动扫描，写入 {@code tool_registry} 表。</p>
 */
public interface AgentTool {

    /** 工具名（喂给 LLM，全局唯一）。 */
    String name();

    /** 描述（喂给 LLM 决定是否调用）。 */
    String description();

    /** 参数 record / POJO 类型，用于反射出 JSON schema。 */
    Class<?> paramsType();

    /** 执行工具。params 是已经按 {@link #paramsType()} 反序列化好的对象。 */
    Object invoke(Object params);
}
