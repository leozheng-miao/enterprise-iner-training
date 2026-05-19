package com.leo.enterpriseinertraining.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.entity.ToolRegistry;
import com.leo.enterpriseinertraining.mapper.ToolRegistryMapper;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.leo.enterpriseinertraining.entity.table.ToolRegistryTableDef.TOOL_REGISTRY;

/**
 * 启动时扫描所有 {@link AgentTool} Bean，按 name 注册到 {@code tool_registry} 表。
 * 提供按名查找工具的能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolRegistryService {

    private final List<AgentTool> tools;           // Spring 自动注入所有 AgentTool bean
    private final ToolRegistryMapper mapper;
    private final ObjectMapper om = new ObjectMapper();

    private final Map<String, AgentTool> byName = new HashMap<>();

    @PostConstruct
    public void init() {
        for (AgentTool t : tools) {
            byName.put(t.name(), t);
            upsertDbRow(t);
        }
        log.info("[ToolRegistry] registered {} tools: {}", tools.size(), byName.keySet());
    }

    /** 按名拿一组工具（顺序保留），找不到的会被忽略并 warn。 */
    public List<AgentTool> byNames(List<String> names) {
        return names.stream()
                .map(n -> {
                    AgentTool t = byName.get(n);
                    if (t == null) log.warn("[ToolRegistry] tool '{}' not found", n);
                    return t;
                })
                .filter(x -> x != null)
                .toList();
    }

    public AgentTool byNameOrThrow(String name) {
        AgentTool t = byName.get(name);
        if (t == null) throw new IllegalArgumentException("Tool not found: " + name);
        return t;
    }

    private void upsertDbRow(AgentTool t) {
        try {
            String schema = toJsonSchema(t.paramsType());
            ToolRegistry existing = mapper.selectOneByQuery(
                    QueryWrapper.create().where(TOOL_REGISTRY.NAME.eq(t.name())));
            if (existing == null) {
                ToolRegistry row = new ToolRegistry();
                row.setName(t.name());
                row.setDescription(t.description());
                row.setParamsSchema(schema);
                row.setHandlerBean(t.getClass().getName());
                row.setEnabled(1);
                mapper.insert(row);
                log.info("[ToolRegistry] DB insert: {}", t.name());
            } else {
                existing.setDescription(t.description());
                existing.setParamsSchema(schema);
                existing.setHandlerBean(t.getClass().getName());
                existing.setEnabled(1);
                mapper.update(existing);
                log.debug("[ToolRegistry] DB update: {}", t.name());
            }
        } catch (Exception e) {
            log.error("[ToolRegistry] upsert failed for {}: {}", t.name(), e.getMessage(), e);
        }
    }

    private String toJsonSchema(Class<?> paramsType) throws Exception {
        // 简化版 schema：reflection 出字段名 + 基础类型
        Map<String, Object> properties = new HashMap<>();
        for (var f : paramsType.getDeclaredFields()) {
            Map<String, String> p = new HashMap<>();
            p.put("type", jsonType(f.getType()));
            properties.put(f.getName(), p);
        }
        Map<String, Object> root = new HashMap<>();
        root.put("type", "object");
        root.put("properties", properties);
        return om.writeValueAsString(root);
    }

    private String jsonType(Class<?> c) {
        if (c == String.class) return "string";
        if (c == Integer.class || c == int.class || c == Long.class || c == long.class) return "integer";
        if (c == Boolean.class || c == boolean.class) return "boolean";
        if (c == Double.class || c == double.class || c == Float.class || c == float.class) return "number";
        return "object";
    }
}
