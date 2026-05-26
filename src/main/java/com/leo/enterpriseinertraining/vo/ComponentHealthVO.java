package com.leo.enterpriseinertraining.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/** 单个子服务的健康状态（Admin 系统健康面板使用）。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ComponentHealthVO implements Serializable {

    @Schema(description = "子服务名：API / Redis / SSE")
    private String name;

    @Schema(description = "状态：UP / DOWN / DEGRADED")
    private String status;

    @Schema(description = "用户可读副标题，如 \"后端接口服务\"")
    private String subtitle;

    @Schema(description = "探测耗时，单位 ms；null 表示不适用（如 SSE）")
    private Double latencyMs;

    @Schema(description = "扩展信息，如 SSE 的活跃连接数")
    private Map<String, Object> extra;
}
