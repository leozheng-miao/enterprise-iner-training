package com.leo.enterpriseinertraining.llm.impl;

import com.leo.enterpriseinertraining.entity.AgentModelRouting;
import com.leo.enterpriseinertraining.llm.api.AgentRole;
import com.leo.enterpriseinertraining.mapper.AgentModelRoutingMapper;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 启动时若 agent_model_routing 为空，插入 7 个 agent 的默认路由（DASHSCOPE/qwen-max）。 */
@Slf4j
@Component
public class AgentModelRoutingSeeder {

    private final AgentModelRoutingMapper mapper;

    public AgentModelRoutingSeeder(AgentModelRoutingMapper mapper) { this.mapper = mapper; }

    @PostConstruct
    public void seed() {
        long count = mapper.selectCountByQuery(QueryWrapper.create());
        if (count > 0) {
            log.info("[RoutingSeeder] agent_model_routing 已有 {} 行，跳过", count);
            return;
        }
        for (AgentRole role : AgentRole.values()) {
            AgentModelRouting r = new AgentModelRouting();
            r.setTenantId(0L);
            r.setAgentRole(role.code());
            r.setProvider("DASHSCOPE");
            r.setPrimaryModel("qwen-max");
            r.setSameProviderFallbacks(null);
            r.setCrossProviderFallbacks(null);
            r.setCrossEnabled(0);
            r.setActive(1);
            r.setNote("seeded default");
            mapper.insert(r);
        }
        log.info("[RoutingSeeder] 插入 7 行默认路由（DASHSCOPE/qwen-max）");
    }
}
