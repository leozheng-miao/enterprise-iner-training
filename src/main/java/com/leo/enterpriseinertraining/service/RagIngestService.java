package com.leo.enterpriseinertraining.service;

import com.leo.enterpriseinertraining.dto.RagIngestRequest;
import com.leo.enterpriseinertraining.vo.IngestSummaryVO;

public interface RagIngestService {
    IngestSummaryVO ingest(RagIngestRequest req);
}
