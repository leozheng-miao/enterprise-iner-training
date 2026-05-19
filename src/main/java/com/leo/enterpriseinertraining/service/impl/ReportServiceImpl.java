package com.leo.enterpriseinertraining.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Citation;
import com.leo.enterpriseinertraining.dto.ReportStartRequest;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.exception.BusinessException;
import com.leo.enterpriseinertraining.exception.ErrorCode;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.service.ReportService;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.vo.ReportStartVO;
import com.leo.enterpriseinertraining.workflow.WorkflowEngine;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportTaskMapper taskMapper;
    private final WorkflowLoader workflowLoader;
    private final WorkflowEngine engine;
    private final SseSinkManager sinkManager;
    private final ObjectMapper om = new ObjectMapper();

    private ExecutorService executor;

    @PostConstruct
    public void initExecutor() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) executor.close();
    }

    @Override
    public ReportStartVO start(long userId, ReportStartRequest req) {
        ReportTask task = new ReportTask();
        task.setUserId(userId);
        task.setTopic(req.getTopic());
        task.setWorkflowName(req.getWorkflow());
        task.setStatus("PENDING");
        taskMapper.insert(task);

        Long taskId = task.getId();
        log.info("[Report] task {} submitted: topic='{}' workflow={}", taskId, req.getTopic(), req.getWorkflow());

        executor.submit(() -> runWorkflow(taskId, req.getTopic(), req.getWorkflow()));

        return new ReportStartVO(taskId, "PENDING", "/api/report/" + taskId + "/stream");
    }

    @Override
    public ReportTask findById(long taskId) {
        ReportTask t = taskMapper.selectOneById(taskId);
        if (t == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        return t;
    }

    private void runWorkflow(long taskId, String topic, String workflowName) {
        try {
            // PENDING → RUNNING
            ReportTask t = taskMapper.selectOneById(taskId);
            t.setStatus("RUNNING");
            t.setStartedAt(LocalDateTime.now());
            taskMapper.update(t);

            var def = workflowLoader.load(workflowName);
            var result = engine.execute(taskId, def, topic, sinkManager.get(taskId));

            t = taskMapper.selectOneById(taskId);
            if (result.ok()) {
                t.setStatus("DONE");
                t.setFinalMarkdown(result.markdown());
                t.setCitationsJson(toCitationData(result.citations()));
            } else {
                t.setStatus("FAILED");
                t.setErrorMessage(result.errorMessage());
            }
            t.setFinishedAt(LocalDateTime.now());
            taskMapper.update(t);

            // 推 SSE done/error，sink 自己 complete
            var liveSink = sinkManager.get(taskId);
            if (liveSink != null) {
                if (result.ok()) liveSink.done(result.markdown(), result.citations());
                else liveSink.error(result.errorMessage());
            }
            sinkManager.remove(taskId);

            log.info("[Report] task {} finished: status={}", taskId, t.getStatus());
        } catch (Exception e) {
            log.error("[Report] task {} crashed", taskId, e);
            try {
                ReportTask t = taskMapper.selectOneById(taskId);
                if (t != null) {
                    t.setStatus("FAILED");
                    t.setErrorMessage("内部错误: " + e.getMessage());
                    t.setFinishedAt(LocalDateTime.now());
                    taskMapper.update(t);
                }
            } catch (Exception ignored) {}

            var liveSink = sinkManager.get(taskId);
            if (liveSink != null) liveSink.error("内部错误: " + e.getMessage());
            sinkManager.remove(taskId);
        }
    }

    private List<ReportTask.CitationData> toCitationData(List<Citation> citations) {
        if (citations == null) return List.of();
        return citations.stream().map(c -> {
            ReportTask.CitationData d = new ReportTask.CitationData();
            d.setDocId(c.docId());
            d.setDocTitle(c.docTitle());
            d.setSource(c.source());
            d.setSectionTitle(c.sectionTitle());
            d.setPageStart(c.pageStart());
            d.setPageEnd(c.pageEnd());
            return d;
        }).toList();
    }
}
