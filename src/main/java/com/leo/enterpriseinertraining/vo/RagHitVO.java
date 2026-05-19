package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data @NoArgsConstructor @AllArgsConstructor
public class RagHitVO implements Serializable {
    private Long chunkId;
    private Double score;
    private Double rerankScore;
    private String content;
    private CitationVO citation;
}
