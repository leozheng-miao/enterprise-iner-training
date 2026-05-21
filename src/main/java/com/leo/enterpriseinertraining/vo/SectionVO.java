package com.leo.enterpriseinertraining.vo;

import com.leo.enterpriseinertraining.entity.ReportTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class SectionVO implements Serializable {
    private Integer order;
    private String title;
    private String outline;
    private String contentMd;
    private List<ReportTask.CitationData> citations;
    private String status;          // DRAFT/FINAL/REVISING
    private Integer revisionCount;
}
