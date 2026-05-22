package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Prompt 模板版本。
 *
 * <p>按 {@code (name, version)} 唯一；同一 {@code name} 下至多一条 {@code isActive=1}，
 * 即灰度生效版本。引用方式见 {@code agent.prompt.PromptLoader}：
 * {@code researcher_prompt@v2}（精确版本）或 {@code researcher_prompt@active}（生效版本）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("prompt_template")
public class PromptTemplate extends BaseEntity {

    private String name;
    private String version;
    private String content;
    private String model;
    private Double temperature;
    /** 1=灰度生效版本；同名至多一条为 1。 */
    private Integer isActive;
    private String description;
}
