package com.leo.enterpriseinertraining.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class RagIngestRequest implements Serializable {

    /** 来源标识，例如 CAICT / MIIT / NDRC / COMPANY-NINGDE。 */
    @NotBlank
    private String source;

    /**
     * 相对 corpus 目录的路径或 glob。例如：
     *   "embodied-ai-2026.pdf" 单文件
     *   "*.pdf" 全部
     */
    @NotBlank
    private String pathOrGlob;
}
