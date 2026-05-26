package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.vo.ActiveWorkflowVO;
import com.leo.enterpriseinertraining.vo.WorkflowLogVO;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/workflow")
@RequiredArgsConstructor
@Tag(name = "管理-Workflow", description = "运维操作")
public class AdminWorkflowController {

    private final WorkflowLoader workflowLoader;

    @PostMapping("/reload")
    @Operation(summary = "清空 workflow YAML 缓存（修改 YAML 后调用即可热更，不必重启）")
    public BaseResponse<String> reload() {
        workflowLoader.reload();
        return ResultUtils.success("reloaded");
    }

    @GetMapping("/active")
    @Operation(summary = "当前活跃 Workflow 元信息（用于 Admin 看板）")
    public BaseResponse<ActiveWorkflowVO> active() {
        return ResultUtils.success(workflowLoader.activeInfo());
    }

    @GetMapping("/history")
    @Operation(summary = "Workflow 加载历史（用于 Admin 日志时间轴）")
    public BaseResponse<List<WorkflowLogVO>> history(
            @RequestParam(defaultValue = "20") int limit) {
        return ResultUtils.success(workflowLoader.history(limit));
    }
}
