package com.leo.enterpriseinertraining.service.impl;

import com.leo.enterpriseinertraining.dto.ReportStartRequest;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.exception.BusinessException;
import com.leo.enterpriseinertraining.exception.ErrorCode;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.security.SecurityUtils;
import com.leo.enterpriseinertraining.service.ReportService;
import com.leo.enterpriseinertraining.vo.ReportStartVO;
import com.leo.enterpriseinertraining.workflow.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportTaskMapper taskMapper;
    private final WorkflowEngine engine;

    @Override
    public ReportStartVO start(long userId, ReportStartRequest req) {
        ReportTask task = new ReportTask();
        task.setUserId(userId);
        task.setTenantId(SecurityUtils.currentTenantId());
        task.setTopic(req.getTopic());
        task.setWorkflowName(req.getWorkflow());
        task.setStatus("PENDING");
        task.setPhase("PLANNING");
        task.setProgress(0);
        taskMapper.insert(task);

        Long taskId = task.getId();
        log.info("[Report] task {} submitted: topic='{}' workflow={} tenant={}",
                taskId, req.getTopic(), req.getWorkflow(), task.getTenantId());

        engine.start(taskId, req.getWorkflow());

        return new ReportStartVO(taskId, "PENDING", "/api/report/" + taskId + "/stream");
    }

    @Override
    public ReportTask findById(long taskId) {
        ReportTask t = taskMapper.selectOneById(taskId);
        if (t == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        // 跨租户访问按 NOT_FOUND 处理：不向调用方泄露 task 是否存在
        if (!Objects.equals(t.getTenantId(), SecurityUtils.currentTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return t;
    }
}
