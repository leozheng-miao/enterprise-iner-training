package com.leo.enterpriseinertraining.stream;

import java.util.List;
import java.util.Map;

/**
 * 「客户端 SSE 事件」的发布者抽象。同一 API 在阶段 2-3 直接写本地 emitter；
 * 阶段 4 改为通过 {@link SseEventBus} 发布到 Redis Pub/Sub，由各 JVM 的
 * {@code SseRedisListener} 转发到本地 emitter，从而支持跨实例广播。
 *
 * <p>本类只负责「发布」，不持有 emitter 状态。{@code isClosed} 在发布者端没有意义
 * （远端是否还连着不可知），始终返回 false。</p>
 */
public class SseSink {

    private final long taskId;
    private final SseEventBus bus;

    public SseSink(long taskId, SseEventBus bus) {
        this.taskId = taskId;
        this.bus = bus;
    }

    public long taskId() { return taskId; }

    public void nodeStatus(String nodeId, String status) {
        bus.publish(taskId, "node_status", Map.of("nodeId", nodeId, "status", status));
    }

    public void tool(String toolName, Object paramsJson, String resultPreview) {
        bus.publish(taskId, "tool", Map.of(
                "toolName", toolName,
                "paramsJson", paramsJson == null ? "" : paramsJson,
                "resultPreview", resultPreview == null ? "" : resultPreview));
    }

    public void token(String delta) {
        bus.publish(taskId, "token", Map.of("delta", delta == null ? "" : delta));
    }

    public void done(String finalMarkdown, Object citations) {
        bus.publish(taskId, "done", Map.of(
                "finalMarkdown", finalMarkdown == null ? "" : finalMarkdown,
                "citations", citations == null ? List.of() : citations));
    }

    public void error(String message) {
        bus.publish(taskId, "error", Map.of("message", message == null ? "" : message));
    }

    public void phaseChanged(String phase, int progress) {
        bus.publish(taskId, "phase_changed", Map.of("phase", phase, "progress", progress));
    }

    public void sectionDone(int order, String title, String preview) {
        bus.publish(taskId, "section_done", Map.of(
                "order", order,
                "title", title == null ? "" : title,
                "preview", preview == null ? "" : preview));
    }

    public void ping() {
        bus.publish(taskId, "ping", Map.of("ts", System.currentTimeMillis()));
    }

    /** 主动通知所有 JVM 关闭该 taskId 的本地 emitter。 */
    public void complete() {
        bus.publish(taskId, "__close__", Map.of());
    }

    public void completeWithError(Throwable t) {
        error(t == null || t.getMessage() == null ? "unknown error" : t.getMessage());
    }

    /** 发布者端不持有 emitter 状态，始终返回 false（保留接口兼容旧调用方）。 */
    public boolean isClosed() { return false; }
}
