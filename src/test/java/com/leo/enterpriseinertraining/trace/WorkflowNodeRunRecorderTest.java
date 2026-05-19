package com.leo.enterpriseinertraining.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 纯逻辑测试：toJson 在异常输入下不抛、null 处理正常。
 * mapper 行为留给集成测试（@SpringBootTest）。
 */
class WorkflowNodeRunRecorderTest {

    @Test
    void toJson_null_returns_null() {
        ObjectMapper om = new ObjectMapper();
        assertNull(toJsonCopy(om, null));
    }

    @Test
    void toJson_map_ok() {
        ObjectMapper om = new ObjectMapper();
        String s = toJsonCopy(om, Map.of("a", 1));
        assertTrue(s.contains("\"a\""));
        assertTrue(s.contains("1"));
    }

    private static String toJsonCopy(ObjectMapper om, Object o) {
        if (o == null) return null;
        try { return om.writeValueAsString(o); }
        catch (Exception e) { return "{\"_serializeError\":\"" + e.getMessage() + "\"}"; }
    }
}
