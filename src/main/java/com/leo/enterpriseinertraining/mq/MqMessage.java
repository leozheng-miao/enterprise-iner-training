package com.leo.enterpriseinertraining.mq;

/**
 * 所有节点消息统一格式。
 *
 * <p>{@code fanoutIndex} 非 fanout 节点为 null；{@code payload} 节点专属 JSON（小负载），
 * 大块数据走 DB（{@code workflow_subtask} / {@code report_section}）。</p>
 */
public record MqMessage(
        long taskId,
        String nodeId,
        Integer fanoutIndex,
        String payload
) {
    public static MqMessage of(long taskId, String nodeId) {
        return new MqMessage(taskId, nodeId, null, null);
    }
    public static MqMessage fanout(long taskId, String nodeId, int index) {
        return new MqMessage(taskId, nodeId, index, null);
    }
}
