package com.leo.enterpriseinertraining.workflow;

import com.leo.enterpriseinertraining.entity.WorkflowLoadLog;
import com.leo.enterpriseinertraining.mapper.WorkflowLoadLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkflowLoaderAuditTest {

    @Mock WorkflowLoadLogMapper logMapper;

    @Test
    void reload_writesFourAuditLogsInOrder() {
        // 测试 classpath 包含 src/main/resources/workflow/multi_agent_v1.yaml（已存在），
        // 因此 reload 走完整路径写 4 条审计：cache_clear → yaml_reload → topology_check → activate。
        WorkflowLoader loader = new WorkflowLoader(logMapper);

        loader.reload();

        ArgumentCaptor<WorkflowLoadLog> cap = ArgumentCaptor.forClass(WorkflowLoadLog.class);
        verify(logMapper, org.mockito.Mockito.times(4)).insert(cap.capture());
        List<WorkflowLoadLog> rows = cap.getAllValues();
        assertThat(rows).extracting(WorkflowLoadLog::getEventType)
                .containsExactly("cache_clear", "yaml_reload", "topology_check", "activate");
        assertThat(rows.get(0).getLevel()).isEqualTo("info");
        assertThat(rows.get(3).getLevel()).isEqualTo("success");
        assertThat(rows).allMatch(r -> r.getTs() != null);
    }

    @Test
    void appendLog_failureDoesNotBubbleToCaller() {
        org.mockito.Mockito.doThrow(new RuntimeException("DB down"))
                .when(logMapper).insert(any(WorkflowLoadLog.class));

        WorkflowLoader loader = new WorkflowLoader(logMapper);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(loader::reload);
    }
}
