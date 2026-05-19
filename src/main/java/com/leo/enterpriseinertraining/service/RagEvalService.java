package com.leo.enterpriseinertraining.service;

import com.leo.enterpriseinertraining.vo.EvalSummaryVO;

public interface RagEvalService {
    int synthesizeAndStore(int sampleSize);
    EvalSummaryVO run();
    EvalSummaryVO latest();
}
