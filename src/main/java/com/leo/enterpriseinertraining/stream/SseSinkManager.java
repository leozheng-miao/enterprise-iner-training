package com.leo.enterpriseinertraining.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 按 taskId 管理 SseSink 单例。同一 taskId 多次 GET 会替换旧 emitter（断点重连场景）。
 *
 * <p>阶段 2 简化为单连接模型；阶段 3 引入 Redis Pub/Sub 实现跨实例广播时再扩。</p>
 */
@Slf4j
@Component
public class SseSinkManager {

    private final Map<Long, SseSink> sinks = new ConcurrentHashMap<>();

    /** 客户端 GET 时调用，注册一个新的 SseSink。返回的 SseEmitter 直接交给 Spring MVC。 */
    public SseEmitter register(long taskId) {
        SseEmitter emitter = new SseEmitter(0L);  // 永不超时；后端 close 决定
        SseSink sink = new SseSink(taskId, emitter);

        SseSink prev = sinks.put(taskId, sink);
        if (prev != null) {
            log.info("[SinkManager/{}] replaced previous sink", taskId);
            prev.complete();
        }

        emitter.onCompletion(() -> {
            log.info("[SinkManager/{}] onCompletion", taskId);
            sinks.remove(taskId, sink);
        });
        emitter.onTimeout(() -> {
            log.info("[SinkManager/{}] onTimeout", taskId);
            sinks.remove(taskId, sink);
            sink.complete();
        });
        emitter.onError(t -> {
            log.warn("[SinkManager/{}] onError: {}", taskId, t.getMessage());
            sinks.remove(taskId, sink);
        });

        return emitter;
    }

    /** Agent 端推事件时调用。若客户端还没连上，返回 null —— 调用方丢弃即可（事件丢失可接受）。 */
    public SseSink get(long taskId) {
        return sinks.get(taskId);
    }

    /** Agent 完成后或失败后主动清理。 */
    public void remove(long taskId) {
        SseSink s = sinks.remove(taskId);
        if (s != null) s.complete();
    }
}
