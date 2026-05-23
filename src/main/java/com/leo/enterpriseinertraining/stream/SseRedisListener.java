package com.leo.enterpriseinertraining.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * 订阅 {@link SseEventBus#CHANNEL}，把广播事件路由到本 JVM 的 emitter。
 *
 * <p>使用单线程 executor 投递，保证同一 channel 上的事件按发布顺序到达 emitter
 * （SSE 协议下顺序敏感：phase_changed / node_status / token 必须严格有序）。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SseRedisListener {

    private final ObjectMapper om;

    @Bean
    public RedisMessageListenerContainer sseListenerContainer(RedisConnectionFactory cf,
                                                              @Autowired SseSinkManager sinkManager) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(cf);
        // 单线程投递：保证同一 task 的事件按发布顺序写到 emitter
        container.setTaskExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sse-redis-listener");
            t.setDaemon(true);
            return t;
        }));
        container.addMessageListener(
                (Message msg, byte[] pattern) -> onMessage(msg, sinkManager),
                new ChannelTopic(SseEventBus.CHANNEL));
        log.info("[SseRedisListener] subscribed to {}", SseEventBus.CHANNEL);
        return container;
    }

    private void onMessage(Message msg, SseSinkManager sinkManager) {
        try {
            String body = new String(msg.getBody(), StandardCharsets.UTF_8);
            JsonNode node = om.readTree(body);
            long taskId = node.get("taskId").asLong();
            String event = node.get("event").asText();
            JsonNode data = node.get("data");                // 直接转发 JsonNode，避免反复反序列化
            sinkManager.deliverLocal(taskId, event, data);
        } catch (Exception e) {
            log.warn("[SseRedisListener] onMessage failed: {}", e.getMessage());
        }
    }
}
