package com.leo.enterpriseinertraining.llm.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRoleTest {

    @Test
    void fromCode_isCaseInsensitive_andMatchesDbString() {
        assertThat(AgentRole.fromCode("Planner")).isEqualTo(AgentRole.PLANNER);
        assertThat(AgentRole.fromCode("planner")).isEqualTo(AgentRole.PLANNER);
        assertThat(AgentRole.fromCode("QueryRewriter")).isEqualTo(AgentRole.QUERY_REWRITER);
    }

    @Test
    void code_roundTrips_toDbString() {
        assertThat(AgentRole.QUERY_REWRITER.code()).isEqualTo("QueryRewriter");
        assertThat(AgentRole.PLANNER.code()).isEqualTo("Planner");
    }

    @Test
    void fromCode_unknown_throws() {
        assertThatThrownBy(() -> AgentRole.fromCode("Nope"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
