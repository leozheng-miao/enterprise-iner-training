package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通用实体基类。
 *
 * <p>所有业务表都包含以下 4 个公共字段：主键、创建时间、更新时间、逻辑删除。
 * 这里集中声明，业务实体 {@code extends BaseEntity} 即可获得这些字段及其行为。</p>
 *
 * <p>时间戳填充策略：<br>
 * - {@code createTime}：INSERT 时由 MyBatis-Flex 在 SQL 写入 {@code now()}，DB 端计算。<br>
 * - {@code updateTime}：INSERT / UPDATE 时同样由 DB 端 {@code now()} 计算。<br>
 * 避免 Java 时钟与 DB 时钟漂移，且无需在 Service 手动 set。</p>
 *
 * <p>逻辑删除：<br>
 * - {@code isDeleted=1} 表示已删除；与 application.yml 中
 *   {@code mybatis-flex.global-config.logic-delete-column: is_deleted} 配合，
 *   {@code deleteById} 会自动改为 UPDATE。</p>
 */
@Data
public class BaseEntity implements Serializable {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(value = "create_time", onInsertValue = "now()")
    private LocalDateTime createTime;

    @Column(value = "update_time", onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;

    @Column(value = "is_deleted", isLogicDelete = true)
    private Integer isDeleted;
}
