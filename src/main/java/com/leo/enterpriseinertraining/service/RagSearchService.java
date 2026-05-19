package com.leo.enterpriseinertraining.service;

import com.leo.enterpriseinertraining.dto.RagSearchRequest;
import com.leo.enterpriseinertraining.vo.RagHitVO;

import java.util.List;

public interface RagSearchService {
    record SearchResult(List<RagHitVO> hits, long tookMs) {}
    SearchResult search(RagSearchRequest req);
}
