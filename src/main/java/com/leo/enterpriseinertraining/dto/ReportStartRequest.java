package com.leo.enterpriseinertraining.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ReportStartRequest implements Serializable {

    @NotBlank
    @Size(min = 4, max = 500)
    private String topic;

    @NotBlank
    private String workflow = "researcher_only_v1";
}
