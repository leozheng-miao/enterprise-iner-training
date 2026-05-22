package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Prompt 版本视图：管理后台版本列表 / Diff / 详情用。
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class PromptTemplateVO implements Serializable {

    private Long id;
    private String name;
    private String version;
    private String content;
    private String model;
    private Double temperature;
    /** 是否为当前灰度生效版本。 */
    private boolean active;
    private String description;
    private Long createTime;
    private Long updateTime;
}
