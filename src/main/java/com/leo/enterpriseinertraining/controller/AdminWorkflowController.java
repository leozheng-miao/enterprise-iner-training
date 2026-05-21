package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
