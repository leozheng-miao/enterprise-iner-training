package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("knowledge_doc")
public class KnowledgeDoc extends BaseEntity {

    private String title;
    private String source;
    private String fileUri;
    private Integer totalPages;
    private String contentHash;
    private LocalDateTime ingestedAt;
}
