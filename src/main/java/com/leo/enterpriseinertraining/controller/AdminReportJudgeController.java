package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.eval.ReportJudgeService;
import com.leo.enterpriseinertraining.vo.JudgeRunVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * LLM-as-Judge 评分管理 API：跑一次评分 / 查某 task 历史 / 查当前租户最近评分。
 * 全部走 ReportService.findById 的 tenant 校验，跨租户访问返回 NOT_FOUND。
 */
@RestController
@RequestMapping("/api/admin/eval/judge")
@RequiredArgsConstructor
@Tag(name = "管理-LLM-as-Judge", description = "研报质量评分（5 维 rubric）")
public class AdminReportJudgeController {

    private final ReportJudgeService judgeService;

    @PostMapping("/{taskId}")
    @Operation(summary = "对该 task 跑一次评分（qwen-max 评委，约 5-15s）")
    public BaseResponse<JudgeRunVO> judge(@PathVariable long taskId) {
        return ResultUtils.success(judgeService.judge(taskId));
    }

    @GetMapping("/{taskId}/history")
    @Operation(summary = "查该 task 的全部历史评分")
    public BaseResponse<List<JudgeRunVO>> history(@PathVariable long taskId) {
        return ResultUtils.success(judgeService.listByTask(taskId));
    }

    @GetMapping
    @Operation(summary = "当前租户最近评分列表")
    public BaseResponse<List<JudgeRunVO>> recent(@RequestParam(defaultValue = "20") int limit) {
        return ResultUtils.success(judgeService.listForCurrentTenant(limit));
    }
}
