package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("rag_eval_query")
public class RagEvalQuery extends BaseEntity {

    private String queryText;

    @Column(typeHandler = JacksonTypeHandler.class)
    private List<Long> goldChunkIds;

    private String querySource;
}
