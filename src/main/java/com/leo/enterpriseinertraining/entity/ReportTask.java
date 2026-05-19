package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("report_task")
public class ReportTask extends BaseEntity {

    private Long userId;
    private String topic;
    private String workflowName;
    private String status;
    private String finalMarkdown;

    @Column(typeHandler = JacksonTypeHandler.class)
    private List<CitationData> citationsJson;

    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    /** 嵌入式引用记录（与 agent.core.Citation 同结构；放 entity 内便于 Jackson 序列化）。 */
    @Data
    public static class CitationData {
        private Long docId;
        private String docTitle;
        private String source;
        private String sectionTitle;
        private Integer pageStart;
        private Integer pageEnd;
    }
}
