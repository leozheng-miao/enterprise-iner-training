package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MqProducerServiceTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void mqMessage_of_no_fanout() {
        MqMessage m = MqMessage.of(42L, "research");
        assertEquals(42L, m.taskId());
        assertEquals("research", m.nodeId());
        assertNull(m.fanoutIndex());
        assertNull(m.payload());
    }

    @Test
    void mqMessage_fanout() {
        MqMessage m = MqMessage.fanout(42L, "research", 3);
        assertEquals(3, m.fanoutIndex());
    }

    @Test
    void mqMessage_serialize_roundtrip() throws Exception {
        MqMessage m = MqMessage.fanout(7L, "write", 2);
        String json = om.writeValueAsString(m);
        MqMessage back = om.readValue(json, MqMessage.class);
        assertEquals(m, back);
    }
}
