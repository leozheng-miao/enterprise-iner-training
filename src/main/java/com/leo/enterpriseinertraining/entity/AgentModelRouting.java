package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** agent_model_routing：Agent→Model 路由。布尔列用 Integer(0/1) 避开 Lombok is- 前缀坑。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("agent_model_routing")
public class AgentModelRouting extends BaseEntity {
    private Long tenantId;
    private String agentRole;
    private String provider;
    private String primaryModel;
    private String sameProviderFallbacks;   // JSON 字符串，Router 解析为 List<String>
    private String crossProviderFallbacks;  // JSON 字符串，Router 解析为 List<ProviderModel>
    private Integer crossEnabled;           // 0/1
    private Integer active;                  // 0/1
    private String note;
}
