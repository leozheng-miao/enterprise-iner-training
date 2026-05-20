package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("report_section")
public class ReportSection extends BaseEntity {

    private Long taskId;
    private Integer sectionOrder;
    private String title;
    private String outline;

    @Column(typeHandler = JacksonTypeHandler.class)
    private List<Integer> relatedSubtopicsJson;

    private String contentMd;

    @Column(typeHandler = JacksonTypeHandler.class)
    private List<ReportTask.CitationData> citationsJson;

    private String status;
    private Integer revisionCount;
}
