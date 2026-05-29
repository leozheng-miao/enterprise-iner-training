package com.leo.enterpriseinertraining.llm.impl;

import com.leo.enterpriseinertraining.entity.AgentModelRouting;
import com.leo.enterpriseinertraining.mapper.AgentModelRoutingMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentModelRoutingSeederTest {

    @Test
    void seed_insertsSevenDefaults_whenTableEmpty() {
        AgentModelRoutingMapper mapper = mock(AgentModelRoutingMapper.class);
        when(mapper.selectCountByQuery(any())).thenReturn(0L);

        new AgentModelRoutingSeeder(mapper).seed();

        ArgumentCaptor<AgentModelRouting> cap = ArgumentCaptor.forClass(AgentModelRouting.class);
        verify(mapper, times(7)).insert(cap.capture());
        assertThat(cap.getAllValues())
            .allMatch(r -> "DASHSCOPE".equals(r.getProvider()) && r.getActive() == 1
                        && r.getTenantId() == 0L && "qwen-max".equals(r.getPrimaryModel()));
        assertThat(cap.getAllValues()).extracting(AgentModelRouting::getAgentRole)
            .containsExactlyInAnyOrder("Planner", "Researcher", "Analyst", "Writer",
                "Critic", "QueryRewriter", "Judge");
    }

    @Test
    void seed_skips_whenTableNotEmpty() {
        AgentModelRoutingMapper mapper = mock(AgentModelRoutingMapper.class);
        when(mapper.selectCountByQuery(any())).thenReturn(3L);

        new AgentModelRoutingSeeder(mapper).seed();

        verify(mapper, never()).insert(any());
    }
}
