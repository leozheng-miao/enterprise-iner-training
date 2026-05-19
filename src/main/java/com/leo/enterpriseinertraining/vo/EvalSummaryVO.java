package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data @NoArgsConstructor @AllArgsConstructor
public class EvalSummaryVO implements Serializable {
    private int totalQueries;
    private double recallAt10NoRerank;
    private double recallAt10WithRerank;
    private double mrrNoRerank;
    private double mrrWithRerank;
    private double ndcg10NoRerank;
    private double ndcg10WithRerank;
    private double rerankLiftNdcg;
    private long tookMs;
    private long runAt;
}
