package com.leo.enterpriseinertraining.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/** 当前活跃 Workflow 元信息（前端 Admin 看板左卡使用）。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ActiveWorkflowVO implements Serializable {

    @Schema(description = "Workflow 名称，如 multi_agent_v1")
    private String name;

    @Schema(description = "Workflow 版本")
    private String version;

    @Schema(description = "YAML 资源路径，如 classpath:workflow/multi_agent_v1.yaml")
    private String file;

    @Schema(description = "节点列表，按拓扑序")
    private List<String> nodes;

    @Schema(description = "上次加载时间，epoch millis；null 表示尚未加载")
    private Long lastLoadedAt;

    @Schema(description = "当前是否已在内存缓存中（true=命中缓存，false=刚 reload 或首次访问）")
    private boolean cached;
}
