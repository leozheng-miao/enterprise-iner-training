package com.leo.enterpriseinertraining.llm.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.entity.AgentModelRouting;
import com.leo.enterpriseinertraining.entity.PromptTemplate;
import com.leo.enterpriseinertraining.llm.api.*;
import com.leo.enterpriseinertraining.mapper.AgentModelRoutingMapper;
import com.leo.enterpriseinertraining.mapper.PromptTemplateMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.leo.enterpriseinertraining.entity.table.AgentModelRoutingTableDef.AGENT_MODEL_ROUTING;
import static com.leo.enterpriseinertraining.entity.table.PromptTemplateTableDef.PROMPT_TEMPLATE;

/** 读 agent_model_routing 解析路由；租户覆盖全局；5s in-memory 缓存（spec §3.3）。 */
@Slf4j
@Component
public class DefaultModelRouter implements ModelRouter {

    private static final long TTL_MILLIS = 5_000L;

    private final AgentModelRoutingMapper routingMapper;
    private final PromptTemplateMapper promptMapper;
    private final ObjectMapper om;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(ResolvedRoute route, long expireAt) {}

    public DefaultModelRouter(AgentModelRoutingMapper routingMapper,
                              PromptTemplateMapper promptMapper, ObjectMapper om) {
        this.routingMapper = routingMapper;
        this.promptMapper = promptMapper;
        this.om = om;
    }

    @Override
    public ResolvedRoute resolve(AgentRole role, Long tenantId) {
        long t = tenantId == null ? 0L : tenantId;
        String key = role.code() + "::" + t;
        long now = System.currentTimeMillis();

        Cached c = cache.get(key);
        if (c != null && c.expireAt() > now) return c.route();

        ResolvedRoute route = doResolve(role, t);
        cache.put(key, new Cached(route, now + TTL_MILLIS));
        return route;
    }

    private ResolvedRoute doResolve(AgentRole role, long tenantId) {
        QueryWrapper qw = QueryWrapper.create()
            .where(AGENT_MODEL_ROUTING.AGENT_ROLE.eq(role.code()))
            .and(AGENT_MODEL_ROUTING.ACTIVE.eq(1))
            .and(AGENT_MODEL_ROUTING.TENANT_ID.in(0L, tenantId));
        List<AgentModelRouting> rows = routingMapper.selectListByQuery(qw);
        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException("无激活路由: agent=" + role.code() + " tenant=" + tenantId
                + "（先跑 AgentModelRoutingSeeder 或在 Admin 配置）");
        }
        // 租户行优先（tenant_id 降序，tenantId>0 排在 0 前）
        AgentModelRouting chosen = rows.stream()
            .max(Comparator.comparingLong(AgentModelRouting::getTenantId))
            .orElseThrow();

        Provider provider = Provider.fromCode(chosen.getProvider());
        List<String> sameFb = parseStringList(chosen.getSameProviderFallbacks());
        List<ProviderModel> crossFb = parseCrossFallbacks(chosen.getCrossProviderFallbacks());
        boolean crossEnabled = chosen.getCrossEnabled() != null && chosen.getCrossEnabled() == 1;
        String promptVersion = resolvePromptVersion(role);

        return new ResolvedRoute(provider, chosen.getPrimaryModel(),
            sameFb, crossFb, crossEnabled, promptVersion);
    }

    /** prompt_template 是全局表（无 tenant_id）：按 agent_role + is_active 取生效版本。 */
    private String resolvePromptVersion(AgentRole role) {
        try {
            QueryWrapper qw = QueryWrapper.create()
                .where(PROMPT_TEMPLATE.AGENT_ROLE.eq(role.code()))
                .and(PROMPT_TEMPLATE.IS_ACTIVE.eq(1));
            PromptTemplate t = promptMapper.selectOneByQuery(qw);
            return t == null ? null : t.getVersion();
        } catch (Exception e) {
            log.warn("[ModelRouter] 读 promptVersion 失败 role={}: {}", role.code(), e.getMessage());
            return null;
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return om.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("[ModelRouter] sameProviderFallbacks JSON 解析失败: {}", json);
            return List.of();
        }
    }

    private List<ProviderModel> parseCrossFallbacks(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, String>> raw =
                om.readValue(json, new TypeReference<List<Map<String, String>>>() {});
            List<ProviderModel> out = new ArrayList<>();
            for (Map<String, String> m : raw) {
                out.add(new ProviderModel(Provider.fromCode(m.get("provider")), m.get("model")));
            }
            return out;
        } catch (Exception e) {
            log.warn("[ModelRouter] crossProviderFallbacks JSON 解析失败: {}", json);
            return List.of();
        }
    }
}
