package com.leo.enterpriseinertraining.trace;

import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.mapper.WorkflowNodeRunMapper;
import com.leo.enterpriseinertraining.security.SecurityUtils;
import com.leo.enterpriseinertraining.vo.PlatformOverviewVO;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock WorkflowNodeRunMapper nodeRunMapper;
    @Mock ReportTaskMapper taskMapper;
    @InjectMocks AdminStatsService service;

    MockedStatic<SecurityUtils> sec;

    @BeforeEach
    void setUp() {
        sec = Mockito.mockStatic(SecurityUtils.class);
        sec.when(SecurityUtils::currentTenantId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() { sec.close(); }

    @Test
    void overview_attachesCompareWindowLabel() {
        when(taskMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(nodeRunMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(nodeRunMapper.selectListByQueryAs(any(QueryWrapper.class), any())).thenReturn(Collections.emptyList());
        when(taskMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        PlatformOverviewVO vo = service.overview();

        assertThat(vo.getCompareWindowLabel()).isEqualTo("较昨日");
        assertThat(vo.getTotalTasksDelta()).isNotNull();
    }

    @Test
    void overview_p95IsNullWhenSampleSizeBelowThreshold() {
        when(taskMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(5L);
        when(nodeRunMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(nodeRunMapper.selectListByQueryAs(any(QueryWrapper.class), any())).thenReturn(Collections.emptyList());
        when(taskMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(taskMapper.countDoneTasksInWindow(any(), any(), any())).thenReturn(5L);

        PlatformOverviewVO vo = service.overview();

        assertThat(vo.getP95TaskLatencyMs()).isNull();
    }
}
