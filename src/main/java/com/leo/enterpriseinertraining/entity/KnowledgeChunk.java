package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("knowledge_chunk")
public class KnowledgeChunk extends BaseEntity {

    private Long docId;
    private String sectionTitle;
    private Integer pageStart;
    private Integer pageEnd;
    private Integer charLen;
    private String vectorId;
    private String esDocId;
}
