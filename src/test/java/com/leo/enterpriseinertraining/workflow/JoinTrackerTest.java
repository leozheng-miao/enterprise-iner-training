package com.leo.enterpriseinertraining.workflow;

import com.leo.enterpriseinertraining.entity.WorkflowSubtask;
import com.leo.enterpriseinertraining.mapper.WorkflowSubtaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JoinTrackerTest {

    @Test
    void markCompleted_returns_true_when_last() {
        WorkflowSubtaskMapper mapper = mock(WorkflowSubtaskMapper.class);
        WorkflowSubtask row = new WorkflowSubtask();
        row.setId(1L);
        row.setTaskId(42L);
        row.setSubIndex(2);
        row.setStatus("RUNNING");
        when(mapper.selectListByQuery(any())).thenReturn(List.of(row));
        when(mapper.update(any())).thenReturn(1);

        JoinTracker tracker = new JoinTracker(mapper);
        // 模拟：当前是最后一个完成的（query "status != DONE" 返回 0 行）
        when(mapper.selectCountByQuery(any())).thenReturn(0L);

        boolean isLast = tracker.markCompletedAndCheckLast(42L, 2, "{\"content\":\"ok\"}");
        assertTrue(isLast);

        ArgumentCaptor<WorkflowSubtask> cap = ArgumentCaptor.forClass(WorkflowSubtask.class);
        verify(mapper).update(cap.capture());
        assertEquals("DONE", cap.getValue().getStatus());
        assertEquals("{\"content\":\"ok\"}", cap.getValue().getResultJson());
    }

    @Test
    void markCompleted_returns_false_when_others_running() {
        WorkflowSubtaskMapper mapper = mock(WorkflowSubtaskMapper.class);
        WorkflowSubtask row = new WorkflowSubtask();
        row.setId(1L);
        row.setTaskId(42L);
        row.setSubIndex(0);
        row.setStatus("RUNNING");
        when(mapper.selectListByQuery(any())).thenReturn(List.of(row));
        when(mapper.update(any())).thenReturn(1);
        when(mapper.selectCountByQuery(any())).thenReturn(3L);   // 还有 3 个没完成

        JoinTracker tracker = new JoinTracker(mapper);
        boolean isLast = tracker.markCompletedAndCheckLast(42L, 0, "{}");
        assertFalse(isLast);
    }

    @Test
    void markCompleted_subtask_not_found_throws() {
        WorkflowSubtaskMapper mapper = mock(WorkflowSubtaskMapper.class);
        when(mapper.selectListByQuery(any())).thenReturn(List.of());

        JoinTracker tracker = new JoinTracker(mapper);
        assertThrows(RuntimeException.class,
                () -> tracker.markCompletedAndCheckLast(42L, 99, "{}"));
    }
}
