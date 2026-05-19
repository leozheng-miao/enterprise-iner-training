package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data @NoArgsConstructor @AllArgsConstructor
public class CitationVO implements Serializable {
    private Long docId;
    private String docTitle;
    private String source;
    private String sectionTitle;
    private Integer pageStart;
    private Integer pageEnd;
}
