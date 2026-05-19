package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestSummaryVO implements Serializable {
    private int filesScanned;
    private int filesIngested;     // 实际新入库
    private int filesSkipped;      // content_hash 已存在
    private int chunksWritten;
    private long tookMs;
    private List<String> ingestedTitles;
}
