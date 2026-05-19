package com.leo.enterpriseinertraining.service.impl;

import com.leo.enterpriseinertraining.dto.RagSearchRequest;
import com.leo.enterpriseinertraining.entity.KnowledgeDoc;
import com.leo.enterpriseinertraining.mapper.KnowledgeDocMapper;
import com.leo.enterpriseinertraining.rag.llm.DashScopeRerankClient;
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
    private final DashScopeRerankClient reranker;

    @Override
    public SearchResult search(RagSearchRequest req) {
        long t0 = System.currentTimeMillis();
        int retrieveK = Boolean.TRUE.equals(req.getUseRerank())
                ? Math.max(req.getTopK() * 3, 30)
                : req.getTopK();
        var candidates = retriever.retrieve(req.getQuery(), retrieveK);

        Set<Long> docIds = candidates.stream().map(HybridRetriever.Candidate::docId).collect(Collectors.toSet());
        Map<Long, KnowledgeDoc> docMap = new HashMap<>();
        if (!docIds.isEmpty()) {
            for (KnowledgeDoc d : docMapper.selectListByIds(docIds)) docMap.put(d.getId(), d);
        }

        List<HybridRetriever.Candidate> finalList;
        Map<Long, Double> rerankScoreByChunk = new HashMap<>();
        if (Boolean.TRUE.equals(req.getUseRerank()) && !candidates.isEmpty()) {
            List<String> texts = candidates.stream().map(HybridRetriever.Candidate::content).toList();
            var scored = reranker.rerank(req.getQuery(), texts, req.getTopK());
            List<HybridRetriever.Candidate> reordered = new java.util.ArrayList<>();
            for (var s : scored) {
                if (s.index() < 0 || s.index() >= candidates.size()) continue;
                var c = candidates.get(s.index());
                reordered.add(c);
                rerankScoreByChunk.put(c.chunkId(), s.relevance());
            }
            finalList = reordered;
        } else {
            finalList = candidates.size() > req.getTopK()
                    ? candidates.subList(0, req.getTopK()) : candidates;
        }

        List<RagHitVO> hits = finalList.stream().map(c -> {
            KnowledgeDoc d = docMap.get(c.docId());
            CitationVO cite = new CitationVO(
                    c.docId(),
                    d == null ? null : d.getTitle(),
                    d == null ? null : d.getSource(),
                    c.sectionTitle(),
                    c.pageStart(), c.pageEnd());
            return new RagHitVO(c.chunkId(), c.fusedScore(),
                    rerankScoreByChunk.get(c.chunkId()),
                    c.content(), cite);
        }).toList();
        return new SearchResult(hits, System.currentTimeMillis() - t0);
    }
}
