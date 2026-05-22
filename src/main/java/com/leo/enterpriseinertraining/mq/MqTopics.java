package com.leo.enterpriseinertraining.mq;

/**
 * RocketMQ topic / consumer group 常量。
 *
 * <p>命名约定：{@code irp-<phase>-<event>}，全部小写，**用 - 连字符不用 .**
 * （RocketMQ topic 名只允许 {@code ^[%|a-zA-Z0-9_-]+$}，点号不合规）。</p>
 */
public final class MqTopics {

    private MqTopics() {}

    public static final String TASK_CREATED   = "irp-task-created";
    public static final String RESEARCH_TASK  = "irp-research-task";
    public static final String ANALYZE_TASK   = "irp-analyze-task";
    public static final String WRITE_SECTION  = "irp-write-section";
    public static final String CRITIC_TASK    = "irp-critic-task";

    // Consumer groups
    public static final String GROUP_ORCHESTRATOR = "irp-task-orchestrator";
    public static final String GROUP_RESEARCHER   = "irp-researcher";
    public static final String GROUP_ANALYST      = "irp-analyst";
    public static final String GROUP_WRITER       = "irp-writer";
    public static final String GROUP_CRITIC       = "irp-critic";
}
