package com.leo.enterpriseinertraining.rag.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RrfFusionTest {

    @Test
    void same_doc_top_in_both_lists_gets_highest_score() {
        List<Long> vec  = List.of(101L, 102L, 103L);
        List<Long> bm25 = List.of(101L, 104L, 105L);
        var fused = RrfFusion.fuse(List.of(vec, bm25), 60, 10);
        assertEquals(101L, fused.get(0).chunkId());
        assertEquals(1.0 / (60 + 1) + 1.0 / (60 + 1), fused.get(0).score(), 1e-9);
    }

    @Test
    void doc_only_in_one_list_still_appears() {
        var fused = RrfFusion.fuse(
                List.of(List.of(101L), List.of(202L)), 60, 10);
        assertEquals(2, fused.size());
        assertEquals(fused.get(0).score(), fused.get(1).score(), 1e-9);
    }

    @Test
    void topK_truncates() {
        var fused = RrfFusion.fuse(
                List.of(List.of(1L, 2L, 3L, 4L, 5L)), 60, 3);
        assertEquals(3, fused.size());
    }
}
