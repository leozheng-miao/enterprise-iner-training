package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.trace.AdminStatsService;
import com.leo.enterpriseinertraining.vo.AgentCostVO;
import com.leo.enterpriseinertraining.vo.ModelCostVO;
import com.leo.enterpriseinertraining.vo.PlatformOverviewVO;
import com.leo.enterpriseinertraining.vo.TaskBriefVO;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台可观测中台 REST API：总览看板 / Token 成本 / 任务列表。
 *
 * <p>数据源是 {@code workflow_node_run}（Trace 核心表）的聚合，只读查询。</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "管理-平台观测", description = "总览看板 / Token 成本 / 任务列表")
public class AdminStatsController {

    private final AdminStatsService statsService;

    @GetMapping("/stats/overview")
    @Operation(summary = "平台总览（任务量 / 成功率 / Token 成本 / 端到端耗时）")
    public BaseResponse<PlatformOverviewVO> overview() {
        return ResultUtils.success(statsService.overview());
    }

    @GetMapping("/stats/cost/model")
    @Operation(summary = "Token 成本看板：按模型聚合")
    public BaseResponse<List<ModelCostVO>> costByModel() {
        return ResultUtils.success(statsService.costByModel());
    }

    @GetMapping("/stats/cost/agent")
    @Operation(summary = "Token 成本看板：按 Agent 角色聚合")
    public BaseResponse<List<AgentCostVO>> costByAgent() {
        return ResultUtils.success(statsService.costByAgent());
    }

    @GetMapping("/tasks")
    @Operation(summary = "任务列表分页查询（可按 status 过滤）")
    public BaseResponse<Page<TaskBriefVO>> tasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResultUtils.success(statsService.tasks(status, page, size));
    }
}
