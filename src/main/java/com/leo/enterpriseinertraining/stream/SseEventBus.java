package com.leo.enterpriseinertraining.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * SSE 事件总线：把任意节点产生的 SSE 事件通过 Redis Pub/Sub 广播到全集群。
 *
 * <p>设计动机：研报任务的客户端 SSE 连接 GET /stream 可能落在 JVM-A，
 * 而消费 RocketMQ 消息执行 Agent 的 Consumer 可能落在 JVM-B。
 * 直接写本地 emitter 会丢事件。所有 SSE 事件先发布到 Redis 频道 {@link #CHANNEL}，
 * 每个 JVM 的 {@code SseRedisListener} 都订阅，命中本地 emitter 就 forward。</p>
 *
 * <p>事件丢失语义：{@link #publish} 失败仅记日志（典型场景：Redis 短暂不可达）；
 * SSE 本身就是「best-effort 实时」，客户端可以走 {@code GET /api/report/{id}} 兜底拿最终结果。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventBus {

    /** Redis Pub/Sub 频道；所有 JVM 都订阅它。 */
    public static final String CHANNEL = "irp:sse:events";

    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    public void publish(long taskId, String event, Object data) {
        try {
            String json = om.writeValueAsString(new SseEvent(taskId, event, data));
            redis.convertAndSend(CHANNEL, json);
        } catch (Exception e) {
            log.warn("[SseEventBus] publish failed taskId={} event={}: {}", taskId, event, e.getMessage());
        }
    }

    /** Pub/Sub 信封：{taskId, event, data}。 */
    public record SseEvent(long taskId, String event, Object data) {}
}
