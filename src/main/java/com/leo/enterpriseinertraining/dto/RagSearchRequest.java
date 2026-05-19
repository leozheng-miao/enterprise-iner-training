package com.leo.enterpriseinertraining.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class RagSearchRequest implements Serializable {
    @NotBlank
    private String query;
    @Min(1) @Max(50)
    private Integer topK = 10;
    private Boolean useRerank = true;
}
