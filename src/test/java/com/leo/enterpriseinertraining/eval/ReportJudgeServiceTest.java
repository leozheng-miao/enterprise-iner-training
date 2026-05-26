package com.leo.enterpriseinertraining.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.entity.ReportEvalRun;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.mapper.ReportEvalRunMapper;
import com.leo.enterpriseinertraining.service.ReportService;
import com.leo.enterpriseinertraining.vo.JudgeRunVO;
import com.mybatisflex.core.query.CPI;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportJudgeServiceTest {

    @Mock ReportService reportService;
    @Mock ReportEvalRunMapper evalRunMapper;
    @Mock com.leo.enterpriseinertraining.mapper.ReportTaskMapper reportTaskMapper;
    @InjectMocks ReportJudgeService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "om", new ObjectMapper());
        ReflectionTestUtils.setField(service, "judgeModel", "qwen-max");
    }

    @Test
    void judge_withCacheHit_returnsCachedWithoutLlmCall() {
        long taskId = 42L;
        ReportTask task = new ReportTask();
        task.setId(taskId);
        task.setTopic("锂电产业链");
        task.setFinalMarkdown("# Report\n\nbody");
        when(reportService.findById(taskId)).thenReturn(task);

        ReportEvalRun cached = new ReportEvalRun();
        cached.setId(7L);
        cached.setTaskId(taskId);
        cached.setJudgeModel("qwen-max");
        cached.setRubricVersion("v1");
        cached.setScoreOverall(8.5);
        cached.setCreateTime(LocalDateTime.now());
        when(evalRunMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(cached));

        JudgeRunVO vo = service.judge(taskId, null, false);

        assertThat(vo.getId()).isEqualTo(7L);
        assertThat(vo.getOverall()).isEqualTo(8.5);
        assertThat(vo.getTopic()).isEqualTo("锂电产业链");
        verify(evalRunMapper, never()).insert(any(ReportEvalRun.class));
    }

    @Test
    void judge_withForce_skipsCacheAndCallsLlm() {
        long taskId = 42L;
        ReportTask task = new ReportTask();
        task.setId(taskId);
        task.setTopic("锂电产业链");
        task.setFinalMarkdown("# Report\n\nbody");
        when(reportService.findById(taskId)).thenReturn(task);

        // force=true 不查 selectListByQuery，但会触发 LLM 调用 → 在没有 RestClient stub 的情况下应抛 NPE / RuntimeException
        assertThatThrownBy(() -> service.judge(taskId, null, true))
                .isInstanceOf(RuntimeException.class);

        verify(evalRunMapper, never()).selectListByQuery(any(QueryWrapper.class));
    }

    @Test
    void judge_withModelOverride_queriesCacheWithOverrideModel() {
        long taskId = 42L;
        ReportTask task = new ReportTask();
        task.setId(taskId);
        task.setTopic("半导体周期");
        task.setFinalMarkdown("# body");
        when(reportService.findById(taskId)).thenReturn(task);

        ReportEvalRun cached = new ReportEvalRun();
        cached.setJudgeModel("deepseek-chat");
        cached.setRubricVersion("v1");
        cached.setScoreOverall(7.2);
        when(evalRunMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(cached));

        JudgeRunVO vo = service.judge(taskId, "deepseek-chat", false);

        assertThat(vo.getOverall()).isEqualTo(7.2);
        assertThat(vo.getJudgeModel()).isEqualTo("deepseek-chat");  // cached row model bubbled through

        // Verify override actually flowed into the cache query, not silently dropped.
        // QueryWrapper.toString() does not embed parameter literals, so inspect bound
        // condition values via mybatis-flex's CPI helper.
        ArgumentCaptor<QueryWrapper> cap = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(evalRunMapper).selectListByQuery(cap.capture());
        Object[] boundParams = CPI.getValueArray(cap.getValue());
        assertThat(boundParams).contains("deepseek-chat");

        verify(evalRunMapper, never()).insert(any(ReportEvalRun.class));
    }
}
