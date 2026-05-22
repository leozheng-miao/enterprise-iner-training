package com.leo.enterpriseinertraining.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改某个 prompt 版本的内容 / 元信息（name、version 不可改）。
 * 仅非空字段生效；保存后 PromptLoader 缓存会被清空。
 */
@Data
public class PromptUpdateRequest implements Serializable {

    @NotBlank
    private String content;

    private String model;

    private Double temperature;

    @Size(max = 255)
    private String description;
}
