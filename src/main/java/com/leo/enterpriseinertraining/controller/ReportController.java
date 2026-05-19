package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.dto.ReportStartRequest;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.entity.WorkflowNodeRun;
import com.leo.enterpriseinertraining.security.SecurityUtils;
import com.leo.enterpriseinertraining.service.ReportService;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.trace.TraceQueryService;
import com.leo.enterpriseinertraining.vo.ReportResultVO;
import com.leo.enterpriseinertraining.vo.ReportStartVO;
import com.leo.enterpriseinertraining.vo.TraceStepVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
@Tag(name = "研究报告", description = "提交主题 → Agent 执行 → SSE 流式返回")
public class ReportController {

    private final ReportService reportService;
    private final TraceQueryService traceQueryService;
    private final SseSinkManager sinkManager;

    @PostMapping("/start")
    @Operation(summary = "提交研究主题，立即返回 taskId + streamUrl")
    public BaseResponse<ReportStartVO> start(@RequestBody @Valid ReportStartRequest req) {
        long userId = SecurityUtils.currentUserId();
        return ResultUtils.success(reportService.start(userId, req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单任务最终结果")
    public BaseResponse<ReportResultVO> get(@PathVariable long id) {
        ReportTask t = reportService.findById(id);
        ReportResultVO vo = new ReportResultVO(
                t.getId(), t.getStatus(), t.getTopic(),
                t.getFinalMarkdown(), t.getCitationsJson(), t.getErrorMessage(),
                t.getStartedAt() == null ? null : t.getStartedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                t.getFinishedAt() == null ? null : t.getFinishedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return ResultUtils.success(vo);
    }

    @GetMapping(value = "/{id}/stream", produces = "text/event-stream")
    @Operation(summary = "SSE 实时流（node_status / tool / token / done / error）")
    public SseEmitter stream(@PathVariable long id) {
        reportService.findById(id);   // 校验 task 存在
        return sinkManager.register(id);
    }

    @GetMapping("/{id}/trace")
    @Operation(summary = "查 Trace 时间轴（workflow_node_run）")
    public BaseResponse<List<TraceStepVO>> trace(@PathVariable long id) {
        List<WorkflowNodeRun> rows = traceQueryService.findByTaskId(id);
        List<TraceStepVO> vos = rows.stream().map(r -> new TraceStepVO(
                r.getId(), r.getStepSeq(), r.getNodeId(), r.getAgentRole(), r.getStepType(),
                r.getPromptVersion(), r.getModel(), r.getToolName(),
                r.getTokensIn(), r.getTokensOut(), r.getLatencyMs(),
                r.getStatus(), r.getErrorMessage(),
                preview(r.getInputJson()), preview(r.getOutputJson())
        )).toList();
        return ResultUtils.success(vos);
    }

    private String preview(String s) {
        if (s == null) return null;
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
