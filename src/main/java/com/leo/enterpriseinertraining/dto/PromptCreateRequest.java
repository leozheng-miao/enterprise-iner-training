package com.leo.enterpriseinertraining.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 新建一个 prompt 版本。{@code (name, version)} 不可与已有重复，新版本默认未生效。
 */
@Data
public class PromptCreateRequest implements Serializable {

    @NotBlank
    @Size(max = 64)
    private String name;

    @NotBlank
    @Size(max = 32)
    private String version;

    @NotBlank
    private String content;

    private String model;

    private Double temperature;

    @Size(max = 255)
    private String description;
}
