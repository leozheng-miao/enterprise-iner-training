package com.leo.enterpriseinertraining.vo;

import com.leo.enterpriseinertraining.entity.ReportTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class ReportResultVO implements Serializable {
    private Long taskId;
    private String status;
    private String topic;
    private String finalMarkdown;
    private List<ReportTask.CitationData> citations;
    private String errorMessage;
    private Long startedAtEpochMillis;
    private Long finishedAtEpochMillis;
}
