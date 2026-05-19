package com.leo.enterpriseinertraining.rag.search;

import com.leo.enterpriseinertraining.rag.llm.DashScopeEmbeddingClient;
import com.leo.enterpriseinertraining.rag.store.BM25Store;
import com.leo.enterpriseinertraining.rag.store.VectorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
public class HybridRetriever {

    private static final int CANDIDATES_PER_SIDE = 50;
    private static final int RRF_K = 60;

    private final DashScopeEmbeddingClient embedding;
    private final VectorStore vectorStore;
    private final BM25Store bm25Store;

    public record Candidate(
            long chunkId, long docId, String sectionTitle,
            int pageStart, int pageEnd, String content, double fusedScore) {}

    public List<Candidate> retrieve(String query, int topK) {
        var exec = Executors.newVirtualThreadPerTaskExecutor();
        CompletableFuture<List<VectorStore.VectorHit>> fVec =
                CompletableFuture.supplyAsync(() ->
                        vectorStore.search(embedding.embedOne(query), CANDIDATES_PER_SIDE), exec);
        CompletableFuture<List<BM25Store.BM25Hit>> fBm25 =
                CompletableFuture.supplyAsync(() ->
                        bm25Store.search(query, CANDIDATES_PER_SIDE), exec);
        List<VectorStore.VectorHit> vecHits;
        List<BM25Store.BM25Hit> bm25Hits;
        try {
            vecHits = fVec.get();
            bm25Hits = fBm25.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Hybrid retrieve 失败", e);
        }

        List<Long> vecRanked  = vecHits.stream().map(VectorStore.VectorHit::chunkId).toList();
        List<Long> bm25Ranked = bm25Hits.stream().map(BM25Store.BM25Hit::chunkId).toList();
        var fused = RrfFusion.fuse(List.of(vecRanked, bm25Ranked), RRF_K, topK);

        Map<Long, VectorStore.VectorHit> byChunkVec = new HashMap<>();
        for (var h : vecHits) byChunkVec.put(h.chunkId(), h);
        Map<Long, BM25Store.BM25Hit> byChunkBm25 = new HashMap<>();
        for (var h : bm25Hits) byChunkBm25.put(h.chunkId(), h);

        return fused.stream().map(f -> {
            var v = byChunkVec.get(f.chunkId());
            if (v != null) {
                return new Candidate(v.chunkId(), v.docId(), v.sectionTitle(),
                        v.pageStart(), v.pageEnd(), v.content(), f.score());
            }
            var b = byChunkBm25.get(f.chunkId());
            return new Candidate(b.chunkId(), b.docId(), b.sectionTitle(),
                    b.pageStart(), b.pageEnd(), b.content(), f.score());
        }).toList();
    }
}
