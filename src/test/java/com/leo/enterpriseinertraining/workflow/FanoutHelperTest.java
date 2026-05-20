package com.leo.enterpriseinertraining.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FanoutHelperTest {

    private final FanoutHelper helper = new FanoutHelper(new ObjectMapper());

    @Test
    void resolve_simple_list_expression() {
        Map<String, Object> outputs = Map.of(
                "plan", Map.of("subtopics", List.of("a", "b", "c"))
        );
        List<String> result = helper.resolveAsStringList("${plan.subtopics}", outputs);
        assertEquals(3, result.size());
        assertEquals("a", result.get(0));
        assertEquals("c", result.get(2));
    }

    @Test
    void resolve_unknown_node_throws() {
        Map<String, Object> outputs = Map.of();
        assertThrows(RuntimeException.class,
                () -> helper.resolveAsStringList("${ghost.field}", outputs));
    }

    @Test
    void resolve_unknown_field_throws() {
        Map<String, Object> outputs = Map.of("plan", Map.of("other", List.of()));
        assertThrows(RuntimeException.class,
                () -> helper.resolveAsStringList("${plan.subtopics}", outputs));
    }

    @Test
    void resolve_non_list_value_throws() {
        Map<String, Object> outputs = Map.of("plan", Map.of("subtopics", "not-a-list"));
        assertThrows(RuntimeException.class,
                () -> helper.resolveAsStringList("${plan.subtopics}", outputs));
    }

    @Test
    void resolve_list_of_objects_to_json_strings() throws Exception {
        Map<String, Object> outputs = Map.of(
                "analyze", Map.of("sections", List.of(
                        Map.of("order", 0, "title", "概述"),
                        Map.of("order", 1, "title", "趋势")))
        );
        List<String> jsons = helper.resolveAsJsonStringList("${analyze.sections}", outputs);
        assertEquals(2, jsons.size());
        ObjectMapper om = new ObjectMapper();
        Map<?, ?> first = om.readValue(jsons.get(0), Map.class);
        assertEquals("概述", first.get("title"));
    }
}
