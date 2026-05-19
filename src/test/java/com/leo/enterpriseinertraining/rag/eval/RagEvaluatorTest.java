package com.leo.enterpriseinertraining.rag.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RagEvaluatorTest {

    @Test
    void recall_at_k_basic() {
        var m = RagEvaluator.compute(
                List.of(1L, 2L, 3L), List.of(2L, 99L), 3);
        assertEquals(1.0, m.recall(), 1e-9);
    }

    @Test
    void recall_zero_when_no_hit() {
        var m = RagEvaluator.compute(
                List.of(1L, 2L, 3L), List.of(99L), 3);
        assertEquals(0.0, m.recall(), 1e-9);
    }

    @Test
    void mrr_first_hit_at_rank_2_is_half() {
        var m = RagEvaluator.compute(
                List.of(1L, 5L, 9L), List.of(5L), 10);
        assertEquals(0.5, m.mrr(), 1e-9);
    }

    @Test
    void ndcg_first_position_full_score() {
        var m = RagEvaluator.compute(
                List.of(5L, 1L, 2L), List.of(5L), 10);
        // DCG = 1/log2(2)=1, IDCG=1, NDCG=1
        assertEquals(1.0, m.ndcg(), 1e-9);
    }
}
