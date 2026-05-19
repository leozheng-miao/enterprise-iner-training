package com.leo.enterpriseinertraining.rag.search;

import java.util.*;

public final class RrfFusion {

    private RrfFusion() {}

    public record Fused(long chunkId, double score) {}

    public static List<Fused> fuse(List<List<Long>> rankedLists, int k, int topK) {
        Map<Long, Double> agg = new HashMap<>();
        for (List<Long> list : rankedLists) {
            for (int r = 0; r < list.size(); r++) {
                long id = list.get(r);
                agg.merge(id, 1.0 / (k + r + 1), Double::sum);
            }
        }
        return agg.entrySet().stream()
                .map(e -> new Fused(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingDouble(Fused::score).reversed())
                .limit(topK)
                .toList();
    }
}
