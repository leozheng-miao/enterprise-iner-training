package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.dto.ReportStartRequest;
import com.leo.enterpriseinertraining.entity.ReportSection;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.entity.WorkflowNodeRun;
import com.leo.enterpriseinertraining.mapper.ReportSectionMapper;
import com.leo.enterpriseinertraining.security.SecurityUtils;
import com.leo.enterpriseinertraining.service.ReportService;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.trace.TraceQueryService;
import com.leo.enterpriseinertraining.vo.ReportResultVO;
import com.leo.enterpriseinertraining.vo.ReportStartVO;
import com.leo.enterpriseinertraining.vo.SectionVO;
import com.leo.enterpriseinertraining.vo.TraceStepVO;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.ZoneId;
import java.util.List;

import static com.leo.enterpriseinertraining.entity.table.ReportSectionTableDef.REPORT_SECTION;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
@Tag(name = "研究报告", description = "提交主题 → 5 Agent 协作 → SSE 流式返回")
public class ReportController {

    private final ReportService reportService;
    private final TraceQueryService traceQueryService;
    private final SseSinkManager sinkManager;
    private final ReportSectionMapper sectionMapper;

    @PostMapping("/start")
    @Operation(summary = "提交研究主题，返回 taskId + streamUrl")
    public BaseResponse<ReportStartVO> start(@RequestBody @Valid ReportStartRequest req) {
        long userId = SecurityUtils.currentUserId();
        return ResultUtils.success(reportService.start(userId, req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单任务最终结果（含 phase / progress）")
    public BaseResponse<ReportResultVO> get(@PathVariable long id) {
        ReportTask t = reportService.findById(id);
        ReportResultVO vo = new ReportResultVO(
                t.getId(), t.getStatus(), t.getPhase(), t.getProgress(),
                t.getTopic(),
                t.getFinalMarkdown(), t.getCitationsJson(), t.getErrorMessage(),
                t.getStartedAt() == null ? null : t.getStartedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                t.getFinishedAt() == null ? null : t.getFinishedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return ResultUtils.success(vo);
    }

    @GetMapping(value = "/{id}/stream", produces = "text/event-stream")
    @Operation(summary = "SSE 实时流（node_status / tool / token / phase_changed / section_done / done / error）")
    public SseEmitter stream(@PathVariable long id) {
        reportService.findById(id);
        return sinkManager.register(id);
    }

    @GetMapping("/{id}/trace")
    @Operation(summary = "查 Trace 时间轴")
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

    @GetMapping("/{id}/sections")
    @Operation(summary = "查所有章节（含修订状态）")
    public BaseResponse<List<SectionVO>> sections(@PathVariable long id) {
        reportService.findById(id);
        List<ReportSection> rows = sectionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(REPORT_SECTION.TASK_ID.eq(id))
                        .orderBy(REPORT_SECTION.SECTION_ORDER, true));
        List<SectionVO> vos = rows.stream().map(s -> new SectionVO(
                s.getSectionOrder(), s.getTitle(), s.getOutline(),
                s.getContentMd(), s.getCitationsJson(),
                s.getStatus(), s.getRevisionCount()
        )).toList();
        return ResultUtils.success(vos);
    }

    private String preview(String s) {
        if (s == null) return null;
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
