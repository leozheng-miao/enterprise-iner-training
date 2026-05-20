package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * 统一发送 {@link MqMessage} 到指定 topic。
 *
 * <p>用 {@link RocketMQTemplate#syncSend(String, org.springframework.messaging.Message)}
 * 同步发送，失败抛 RuntimeException。Consumer 端如果业务失败可以靠 RocketMQ 自动重试 3 次。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqProducerService {

    private final RocketMQTemplate template;
    private final ObjectMapper om;

    public void send(String topic, MqMessage msg) {
        try {
            String body = om.writeValueAsString(msg);
            var spring = MessageBuilder.withPayload(body).build();
            var result = template.syncSend(topic, spring);
            log.info("[MQ] send to {} taskId={} nodeId={} fanoutIndex={} → {}",
                    topic, msg.taskId(), msg.nodeId(), msg.fanoutIndex(), result.getSendStatus());
        } catch (Exception e) {
            throw new RuntimeException("MQ 发送失败: topic=" + topic + " err=" + e.getMessage(), e);
        }
    }

    /** 批量发送同 topic（fanout 场景用，单次循环 syncSend；RocketMQ 内部已有连接池）。 */
    public void sendAll(String topic, java.util.List<MqMessage> messages) {
        for (MqMessage m : messages) send(topic, m);
    }
}
