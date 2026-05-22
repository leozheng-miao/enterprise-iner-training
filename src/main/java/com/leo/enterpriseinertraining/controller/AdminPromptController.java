package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.agent.prompt.PromptAdminService;
import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.dto.PromptCreateRequest;
import com.leo.enterpriseinertraining.dto.PromptUpdateRequest;
import com.leo.enterpriseinertraining.vo.PromptTemplateVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Prompt 版本管理后台 REST API：版本列表 / 详情 / 新建 / 改内容 / 切灰度生效版本。
 *
 * <p>切换生效版本无需重启——PromptLoader 缓存会被清空，Agent 下次调用即取新版本。</p>
 */
@RestController
@RequestMapping("/api/admin/prompts")
@RequiredArgsConstructor
@Tag(name = "管理-Prompt 版本", description = "Prompt 版本管理与灰度切换")
public class AdminPromptController {

    private final PromptAdminService promptAdminService;

    @GetMapping
    @Operation(summary = "列出全部 prompt 版本（按 name、version 升序）")
    public BaseResponse<List<PromptTemplateVO>> list() {
        return ResultUtils.success(promptAdminService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查单个版本详情（含完整 content）")
    public BaseResponse<PromptTemplateVO> get(@PathVariable long id) {
        return ResultUtils.success(promptAdminService.get(id));
    }

    @PostMapping
    @Operation(summary = "新建一个 prompt 版本（默认未生效）")
    public BaseResponse<PromptTemplateVO> create(@RequestBody @Valid PromptCreateRequest req) {
        return ResultUtils.success(promptAdminService.create(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改版本内容 / 元信息（保存后清 PromptLoader 缓存）")
    public BaseResponse<PromptTemplateVO> update(@PathVariable long id,
                                                 @RequestBody @Valid PromptUpdateRequest req) {
        return ResultUtils.success(promptAdminService.update(id, req));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "把该版本设为灰度生效版本（同名其余版本自动下线）")
    public BaseResponse<PromptTemplateVO> activate(@PathVariable long id) {
        return ResultUtils.success(promptAdminService.activate(id));
    }
}
