package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.dto.RagIngestRequest;
import com.leo.enterpriseinertraining.dto.RagSearchRequest;
import com.leo.enterpriseinertraining.service.RagEvalService;
import com.leo.enterpriseinertraining.service.RagIngestService;
import com.leo.enterpriseinertraining.service.RagSearchService;
import com.leo.enterpriseinertraining.vo.IngestSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Tag(name = "RAG", description = "知识入库 / 检索 / 评估")
public class RagController {

    private final RagIngestService ingestService;
    private final RagSearchService searchService;
    private final RagEvalService evalService;

    @PostMapping("/ingest")
    @Operation(summary = "离线 ingest 一批 PDF（path 是 corpus/ 下的相对路径或 glob）")
    public BaseResponse<IngestSummaryVO> ingest(@RequestBody @Valid RagIngestRequest req) {
        return ResultUtils.success(ingestService.ingest(req));
    }

    @PostMapping("/search")
    @Operation(summary = "Hybrid 检索")
    public BaseResponse<Map<String, Object>> search(@RequestBody @Valid RagSearchRequest req) {
        var r = searchService.search(req);
        return ResultUtils.success(Map.of(
                "query", req.getQuery(),
                "tookMs", r.tookMs(),
                "hits", r.hits()));
    }

    @PostMapping("/eval/synthesize")
    @Operation(summary = "合成评估 query（每个 chunk 生 1 个，需 ADMIN 自行筛选）")
    public BaseResponse<Integer> synthesize(@RequestParam(defaultValue = "100") int sampleSize) {
        return ResultUtils.success(evalService.synthesizeAndStore(sampleSize));
    }
}
