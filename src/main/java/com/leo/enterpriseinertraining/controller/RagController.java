package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.dto.RagIngestRequest;
import com.leo.enterpriseinertraining.service.RagIngestService;
import com.leo.enterpriseinertraining.vo.IngestSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Tag(name = "RAG", description = "知识入库 / 检索 / 评估")
public class RagController {

    private final RagIngestService ingestService;

    @PostMapping("/ingest")
    @Operation(summary = "离线 ingest 一批 PDF（path 是 corpus/ 下的相对路径或 glob）")
    public BaseResponse<IngestSummaryVO> ingest(@RequestBody @Valid RagIngestRequest req) {
        return ResultUtils.success(ingestService.ingest(req));
    }
}
