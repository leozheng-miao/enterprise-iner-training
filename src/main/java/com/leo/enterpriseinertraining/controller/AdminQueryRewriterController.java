package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.agent.queryrewriter.QueryRewriterClient;
import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.dto.QueryRewriteRequest;
import com.leo.enterpriseinertraining.vo.QueryRewriteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Query 改写独立测试入口：用来对比 DashScope qwen-max 与自托管 vLLM + LoRA 的效果。
 * 切 {@code app.query-rewriter.base-url} 即可换底，端到端无侵入。
 */
@RestController
@RequestMapping("/api/admin/query-rewrite")
@RequiredArgsConstructor
@Tag(name = "管理-Query 改写", description = "对接 vLLM / DashScope 的 OpenAI 兼容端点")
public class AdminQueryRewriterController {

    private final QueryRewriterClient client;

    @PostMapping
    @Operation(summary = "把原始主题改写为结构化 JSON（intent / industry / year / geo / sub_queries）")
    public BaseResponse<QueryRewriteVO> rewrite(@RequestBody @Valid QueryRewriteRequest req) {
        return ResultUtils.success(client.rewrite(req.getTopic()));
    }
}
