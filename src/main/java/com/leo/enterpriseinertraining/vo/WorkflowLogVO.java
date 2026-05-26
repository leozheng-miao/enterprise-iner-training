package com.leo.enterpriseinertraining.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Workflow 加载/重载日志条目（前端时间轴使用）。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class WorkflowLogVO implements Serializable {

    @Schema(description = "事件类型：cache_clear / yaml_reload / topology_check / activate")
    private String eventType;

    @Schema(description = "可读消息")
    private String message;

    @Schema(description = "级别：info / success / warning / error")
    private String level;

    @Schema(description = "时间戳，epoch millis")
    private Long ts;
}
