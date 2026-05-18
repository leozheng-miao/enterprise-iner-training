package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体。
 *
 * <p>继承 {@link BaseEntity} 获得 id / create_time / update_time / is_deleted
 * 四个公共字段及其填充策略。这里只声明业务字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table("user")
public class User extends BaseEntity {

    private String username;
    private String passwordHash;
    private String nickname;
    private Long tenantId;
    private String role;
    private Integer status;
}
