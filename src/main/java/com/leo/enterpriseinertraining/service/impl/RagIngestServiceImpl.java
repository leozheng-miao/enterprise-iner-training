package com.leo.enterpriseinertraining.service.impl;

import com.leo.enterpriseinertraining.dto.RagIngestRequest;
import com.leo.enterpriseinertraining.exception.BusinessException;
import com.leo.enterpriseinertraining.exception.ErrorCode;
import com.leo.enterpriseinertraining.rag.ingest.PdfIngestPipeline;
import com.leo.enterpriseinertraining.service.RagIngestService;
import com.leo.enterpriseinertraining.vo.IngestSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagIngestServiceImpl implements RagIngestService {

    private final PdfIngestPipeline pipeline;

    @Override
    public IngestSummaryVO ingest(RagIngestRequest req) {
        long t0 = System.currentTimeMillis();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        var pattern = "classpath:corpus/" + req.getPathOrGlob();
        org.springframework.core.io.Resource[] resources;
        try {
            resources = resolver.getResources(pattern);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法解析路径: " + pattern);
        }
        List<String> titles = new ArrayList<>();
        int ingested = 0, skipped = 0, totalChunks = 0;
        for (var r : resources) {
            try {
                File f = r.getFile();
                if (!f.getName().toLowerCase().endsWith(".pdf")) continue;
                var res = pipeline.ingestOne(f, req.getSource());
                if (res.ingested()) {
                    ingested++;
                    totalChunks += res.chunks();
                    titles.add(res.title());
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("[Ingest] failed for {}", r.getDescription(), e);
            }
        }
        return new IngestSummaryVO(
                resources.length, ingested, skipped, totalChunks,
                System.currentTimeMillis() - t0, titles);
    }
}
