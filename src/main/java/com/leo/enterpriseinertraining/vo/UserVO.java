package com.leo.enterpriseinertraining.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserVO implements Serializable {
    private Long id;
    private String username;
    private String nickname;
    private String role;
    private Long tenantId;
}
