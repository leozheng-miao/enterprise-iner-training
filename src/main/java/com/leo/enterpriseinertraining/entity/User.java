package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Table("user")
public class User implements Serializable {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private Long tenantId;
    private String role;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @Column(isLogicDelete = true)
    private Integer isDeleted;
}
