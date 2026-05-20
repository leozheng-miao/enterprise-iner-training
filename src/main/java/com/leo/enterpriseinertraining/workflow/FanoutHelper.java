package com.leo.enterpriseinertraining.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 {@code ${nodeId.field}} 表达式，从前序节点输出 Map 取值。
 *
 * <p>支持两种返回形态：</p>
 * <ul>
 *   <li>{@link #resolveAsStringList} — 期望 {@code List<String>}（如 planner 输出的 subtopics）</li>
 *   <li>{@link #resolveAsJsonStringList} — 期望 {@code List<Object>}，每项 JSON 序列化（如 analyst 输出的 sections）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class FanoutHelper {

    private static final Pattern EXPR = Pattern.compile("^\\$\\{([a-zA-Z_][\\w]*)\\.([a-zA-Z_][\\w]*)}$");

    private final ObjectMapper om;

    public List<String> resolveAsStringList(String expr, Map<String, Object> previousOutputs) {
        Object value = resolveRaw(expr, previousOutputs);
        if (!(value instanceof List<?> list)) {
            throw new RuntimeException("fanout 表达式期望 List，但拿到 " + className(value) + ": " + expr);
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof String s) result.add(s);
            else throw new RuntimeException("fanout List 元素期望 String，但拿到 " + className(item));
        }
        return result;
    }

    public List<String> resolveAsJsonStringList(String expr, Map<String, Object> previousOutputs) {
        Object value = resolveRaw(expr, previousOutputs);
        if (!(value instanceof List<?> list)) {
            throw new RuntimeException("fanout 表达式期望 List: " + expr);
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            try {
                result.add(om.writeValueAsString(item));
            } catch (Exception e) {
                throw new RuntimeException("fanout 元素 JSON 序列化失败: " + e.getMessage(), e);
            }
        }
        return result;
    }

    private Object resolveRaw(String expr, Map<String, Object> previousOutputs) {
        Matcher m = EXPR.matcher(expr.trim());
        if (!m.matches()) throw new RuntimeException("非法 fanout 表达式: " + expr);
        String node = m.group(1);
        String field = m.group(2);
        Object nodeOutput = previousOutputs.get(node);
        if (nodeOutput == null) throw new RuntimeException("未找到前序节点输出: " + node);
        if (!(nodeOutput instanceof Map<?, ?> map)) {
            throw new RuntimeException("节点输出不是 Map: " + node);
        }
        Object value = map.get(field);
        if (value == null) throw new RuntimeException("节点 " + node + " 输出无字段 " + field);
        return value;
    }

    private static String className(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName();
    }
}
