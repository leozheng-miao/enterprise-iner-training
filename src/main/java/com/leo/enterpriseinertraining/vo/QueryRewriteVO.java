package com.leo.enterpriseinertraining.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Query 改写结果：意图、行业实体、时间地域、检索子查询。
 *
 * <p>字段集合即微调模型的目标输出 schema，用于训练 / 推理 / 评估三处保持一致。</p>
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class QueryRewriteVO implements Serializable {

    /** industry_trend / company_compare / tech_progress / policy_impact / market_size 等。 */
    private String intent;
    private String industry;
    private Integer year;
    private String geo;
    /** 3-5 条便于 hybrid search 检索的子查询。 */
    @JsonProperty("sub_queries")
    private List<String> subQueries;
}
