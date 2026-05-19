package com.leo.enterpriseinertraining.rag.eval;

import java.util.List;
import java.util.Set;

public final class RagEvaluator {

    private RagEvaluator() {}

    public record Metrics(double recall, double mrr, double ndcg) {}

    public static Metrics compute(List<Long> retrievedRanked, List<Long> gold, int k) {
        Set<Long> goldSet = Set.copyOf(gold);
        int top = Math.min(k, retrievedRanked.size());

        boolean hit = false;
        for (int i = 0; i < top; i++) {
            if (goldSet.contains(retrievedRanked.get(i))) { hit = true; break; }
        }
        double recall = hit ? 1.0 : 0.0;

        double mrr = 0.0;
        for (int i = 0; i < top; i++) {
            if (goldSet.contains(retrievedRanked.get(i))) {
                mrr = 1.0 / (i + 1);
                break;
            }
        }

        double dcg = 0.0;
        for (int i = 0; i < top; i++) {
            if (goldSet.contains(retrievedRanked.get(i))) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
        }
        double idcg = 0.0;
        int idealHits = Math.min(goldSet.size(), top);
        for (int i = 0; i < idealHits; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }
        double ndcg = idcg == 0 ? 0 : dcg / idcg;

        return new Metrics(recall, mrr, ndcg);
    }
}
