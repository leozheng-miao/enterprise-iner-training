package com.leo.enterpriseinertraining.llm.impl;

import com.leo.enterpriseinertraining.entity.AgentModelRouting;
import com.leo.enterpriseinertraining.entity.PromptTemplate;
import com.leo.enterpriseinertraining.llm.api.AgentRole;
import com.leo.enterpriseinertraining.llm.api.Provider;
import com.leo.enterpriseinertraining.llm.api.ResolvedRoute;
import com.leo.enterpriseinertraining.mapper.AgentModelRoutingMapper;
import com.leo.enterpriseinertraining.mapper.PromptTemplateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultModelRouterTest {

    private AgentModelRoutingMapper routingMapper;
    private PromptTemplateMapper promptMapper;
    private DefaultModelRouter router;

    private AgentModelRouting route(long tenant, String provider, String model,
                                    String sameFb, String crossFb, int crossEnabled) {
        AgentModelRouting r = new AgentModelRouting();
        r.setTenantId(tenant);
        r.setAgentRole("Planner");
        r.setProvider(provider);
        r.setPrimaryModel(model);
        r.setSameProviderFallbacks(sameFb);
        r.setCrossProviderFallbacks(crossFb);
        r.setCrossEnabled(crossEnabled);
        r.setActive(1);
        return r;
    }

    @BeforeEach
    void setUp() {
        routingMapper = mock(AgentModelRoutingMapper.class);
        promptMapper = mock(PromptTemplateMapper.class);
        router = new DefaultModelRouter(routingMapper, promptMapper, new ObjectMapper());
    }

    @Test
    void resolve_parsesProviderModelAndFallbackJson() {
        when(routingMapper.selectListByQuery(any())).thenReturn(List.of(
            route(0L, "OPENAI", "gpt-4o",
                "[\"gpt-4o-mini\"]",
                "[{\"provider\":\"DASHSCOPE\",\"model\":\"qwen-max\"}]", 0)));
        when(promptMapper.selectListByQuery(any())).thenReturn(List.of(promptVer("v3")));

        ResolvedRoute r = router.resolve(AgentRole.PLANNER, 0L);

        assertThat(r.provider()).isEqualTo(Provider.OPENAI);
        assertThat(r.primaryModel()).isEqualTo("gpt-4o");
        assertThat(r.sameProviderFallbacks()).containsExactly("gpt-4o-mini");
        assertThat(r.crossProviderFallbacks()).hasSize(1);
        assertThat(r.crossProviderFallbacks().get(0).provider()).isEqualTo(Provider.DASHSCOPE);
        assertThat(r.crossEnabled()).isFalse();
        assertThat(r.promptVersion()).isEqualTo("v3");
    }

    @Test
    void resolve_prefersTenantRow_overGlobalDefault() {
        when(routingMapper.selectListByQuery(any())).thenReturn(List.of(
            route(0L, "DASHSCOPE", "qwen-max", null, null, 0),
            route(7L, "OPENAI", "gpt-4o", null, null, 0)));
        when(promptMapper.selectListByQuery(any())).thenReturn(List.of());

        ResolvedRoute r = router.resolve(AgentRole.PLANNER, 7L);

        assertThat(r.provider()).isEqualTo(Provider.OPENAI);   // 租户 7 覆盖全局
        assertThat(r.primaryModel()).isEqualTo("gpt-4o");
    }

    @Test
    void resolve_cachesWithin5s_secondCallHitsCache_noDbQuery() {
        when(routingMapper.selectListByQuery(any())).thenReturn(List.of(
            route(0L, "DASHSCOPE", "qwen-max", null, null, 0)));
        when(promptMapper.selectListByQuery(any())).thenReturn(List.of());

        router.resolve(AgentRole.PLANNER, 0L);
        router.resolve(AgentRole.PLANNER, 0L);

        verify(routingMapper, times(1)).selectListByQuery(any());  // 第二次走缓存
    }

    @Test
    void resolve_nullFallbackJson_yieldsEmptyLists() {
        when(routingMapper.selectListByQuery(any())).thenReturn(List.of(
            route(0L, "DASHSCOPE", "qwen-max", null, null, 0)));
        when(promptMapper.selectListByQuery(any())).thenReturn(List.of());

        ResolvedRoute r = router.resolve(AgentRole.PLANNER, 0L);

        assertThat(r.sameProviderFallbacks()).isEmpty();
        assertThat(r.crossProviderFallbacks()).isEmpty();
        assertThat(r.promptVersion()).isNull();
    }

    @Test
    void resolve_multipleActivePromptRows_picksFirst_doesNotThrow() {
        when(routingMapper.selectListByQuery(any())).thenReturn(List.of(
            route(0L, "DASHSCOPE", "qwen-max", null, null, 0)));
        // 模拟同 agent_role 多条 active（schema 未强约束唯一）：实现按 version desc limit 1，取首条；不应抛异常
        when(promptMapper.selectListByQuery(any()))
            .thenReturn(List.of(promptVer("v5"), promptVer("v4")));

        ResolvedRoute r = router.resolve(AgentRole.PLANNER, 0L);

        assertThat(r.promptVersion()).isEqualTo("v5");
    }

    private PromptTemplate promptVer(String v) {
        PromptTemplate t = new PromptTemplate();
        t.setVersion(v);
        return t;
    }
}
