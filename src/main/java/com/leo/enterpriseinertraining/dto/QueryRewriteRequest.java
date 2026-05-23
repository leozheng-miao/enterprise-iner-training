package com.leo.enterpriseinertraining.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * Query 改写请求：原始研究主题。
 */
@Data
public class QueryRewriteRequest implements Serializable {

    @NotBlank
    @Size(min = 2, max = 500)
    private String topic;
}
