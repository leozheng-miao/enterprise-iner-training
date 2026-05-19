package com.leo.enterpriseinertraining.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * SseEmitter 的薄包装。提供 5 种语义事件方法 + 内部线程安全。
 *
 * <p>事件协议见 spec §4.4。</p>
 */
@Slf4j
public class SseSink {

    private final long taskId;
    private final SseEmitter emitter;
    private final ObjectMapper om = new ObjectMapper();
    private volatile boolean closed = false;

    public SseSink(long taskId, SseEmitter emitter) {
        this.taskId = taskId;
        this.emitter = emitter;
    }

    public long taskId() { return taskId; }
    public SseEmitter emitter() { return emitter; }

    public synchronized void nodeStatus(String nodeId, String status) {
        send("node_status", Map.of("nodeId", nodeId, "status", status));
    }

    public synchronized void tool(String toolName, Object paramsJson, String resultPreview) {
        send("tool", Map.of(
                "toolName", toolName,
                "paramsJson", paramsJson == null ? "" : paramsJson,
                "resultPreview", resultPreview == null ? "" : resultPreview));
    }

    public synchronized void token(String delta) {
        send("token", Map.of("delta", delta == null ? "" : delta));
    }

    public synchronized void done(String finalMarkdown, Object citations) {
        send("done", Map.of(
                "finalMarkdown", finalMarkdown == null ? "" : finalMarkdown,
                "citations", citations == null ? java.util.List.of() : citations));
        complete();
    }

    public synchronized void error(String message) {
        send("error", Map.of("message", message == null ? "" : message));
        complete();
    }

    public synchronized void ping() {
        send("ping", Map.of("ts", System.currentTimeMillis()));
    }

    private void send(String event, Object data) {
        if (closed) return;
        try {
            emitter.send(SseEmitter.event().name(event).data(om.writeValueAsString(data)));
        } catch (IOException | IllegalStateException e) {
            log.warn("[SSE/{}] send '{}' failed: {}", taskId, event, e.getMessage());
            closed = true;
        }
    }

    public synchronized void complete() {
        if (closed) return;
        closed = true;
        try { emitter.complete(); } catch (Exception ignored) {}
    }

    public synchronized void completeWithError(Throwable t) {
        if (closed) return;
        closed = true;
        try { emitter.completeWithError(t); } catch (Exception ignored) {}
    }

    public boolean isClosed() { return closed; }
}
