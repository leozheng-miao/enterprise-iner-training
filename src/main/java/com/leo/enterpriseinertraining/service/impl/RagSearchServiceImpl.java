package com.leo.enterpriseinertraining.service.impl;

import com.leo.enterpriseinertraining.dto.RagSearchRequest;
import com.leo.enterpriseinertraining.entity.KnowledgeDoc;
import com.leo.enterpriseinertraining.mapper.KnowledgeDocMapper;
import com.leo.enterpriseinertraining.rag.search.HybridRetriever;
import com.leo.enterpriseinertraining.service.RagSearchService;
import com.leo.enterpriseinertraining.vo.CitationVO;
import com.leo.enterpriseinertraining.vo.RagHitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagSearchServiceImpl implements RagSearchService {

    private final HybridRetriever retriever;
    private final KnowledgeDocMapper docMapper;

    @Override
    public SearchResult search(RagSearchRequest req) {
        long t0 = System.currentTimeMillis();
        var candidates = retriever.retrieve(req.getQuery(), req.getTopK());

        Set<Long> docIds = candidates.stream().map(HybridRetriever.Candidate::docId).collect(Collectors.toSet());
        Map<Long, KnowledgeDoc> docMap = new HashMap<>();
        if (!docIds.isEmpty()) {
            for (KnowledgeDoc d : docMapper.selectListByIds(docIds)) {
                docMap.put(d.getId(), d);
            }
        }

        List<RagHitVO> hits = candidates.stream().map(c -> {
            KnowledgeDoc d = docMap.get(c.docId());
            CitationVO cite = new CitationVO(
                    c.docId(),
                    d == null ? null : d.getTitle(),
                    d == null ? null : d.getSource(),
                    c.sectionTitle(),
                    c.pageStart(), c.pageEnd());
            return new RagHitVO(c.chunkId(), c.fusedScore(), null, c.content(), cite);
        }).toList();

        return new SearchResult(hits, System.currentTimeMillis() - t0);
    }
}
