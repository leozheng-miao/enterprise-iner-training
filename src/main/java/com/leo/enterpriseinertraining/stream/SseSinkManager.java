package com.leo.enterpriseinertraining.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 taskId 管理本 JVM 上的 SseEmitter；阶段 4 起所有「发布事件」都走 {@link SseEventBus}
 * → Redis Pub/Sub → 各 JVM 的 {@code SseRedisListener} → 调用 {@link #deliverLocal} 写回
 * 本地 emitter，从而支持跨实例广播。
 *
 * <p>{@link #get(long)} 永远返回非 null —— 发布者不需要知道 emitter 是否落在本 JVM。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseSinkManager {

    private final SseEventBus bus;
    private final ObjectMapper om;

    /** taskId → 本 JVM 持有的 emitter；未在本 JVM 注册的 task 不在 map 里。 */
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** 客户端 GET 时调用，注册一个新的 SseEmitter。 */
    public SseEmitter register(long taskId) {
        SseEmitter emitter = new SseEmitter(0L);   // 永不超时；由后端 close 决定
        SseEmitter prev = emitters.put(taskId, emitter);
        if (prev != null) {
            log.info("[SinkManager/{}] replaced previous emitter", taskId);
            safeComplete(prev);
        }

        emitter.onCompletion(() -> {
            log.info("[SinkManager/{}] onCompletion", taskId);
            emitters.remove(taskId, emitter);
        });
        emitter.onTimeout(() -> {
            log.info("[SinkManager/{}] onTimeout", taskId);
            emitters.remove(taskId, emitter);
            safeComplete(emitter);
        });
        emitter.onError(t -> {
            log.warn("[SinkManager/{}] onError: {}", taskId, t.getMessage());
            emitters.remove(taskId, emitter);
        });

        return emitter;
    }

    /**
     * Agent / Consumer 端调用：永不返回 null —— 返回的 {@link SseSink} 通过 Redis Pub/Sub
     * 广播事件，emitter 落在哪个 JVM 都能收到。
     */
    public SseSink get(long taskId) {
        return new SseSink(taskId, bus);
    }

    /** 任务结束 / 失败时调用：广播 close，让所有 JVM 关掉本地 emitter。 */
    public void remove(long taskId) {
        bus.publish(taskId, "__close__", Map.of());
    }

    /**
     * Redis 订阅端回调（由 {@code SseRedisListener} 触发）：把广播事件写到本 JVM 的 emitter。
     * 本 JVM 没有该 taskId 的 emitter 时静默丢弃。
     *
     * <p>done / error / __close__ 三个事件在转发后会关闭本地 emitter。</p>
     */
    public void deliverLocal(long taskId, String event, Object data) {
        SseEmitter em = emitters.get(taskId);
        if (em == null) return;
        try {
            if ("__close__".equals(event)) {
                emitters.remove(taskId, em);
                safeComplete(em);
                return;
            }
            em.send(SseEmitter.event().name(event).data(om.writeValueAsString(data)));
            if ("done".equals(event) || "error".equals(event)) {
                emitters.remove(taskId, em);
                safeComplete(em);
            }
        } catch (IOException | IllegalStateException e) {
            log.warn("[SinkManager/{}] deliverLocal '{}' failed: {}", taskId, event, e.getMessage());
            emitters.remove(taskId, em);
            safeComplete(em);
        }
    }

    private static void safeComplete(SseEmitter em) {
        try { em.complete(); } catch (Exception ignored) {}
    }
}
