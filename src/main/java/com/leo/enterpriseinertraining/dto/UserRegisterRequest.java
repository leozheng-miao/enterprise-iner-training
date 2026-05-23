package com.leo.enterpriseinertraining.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class UserRegisterRequest implements Serializable {

    @NotBlank(message = "不能为空")
    @Size(min = 4, max = 32, message = "长度必须在 4-32")
    private String username;

    @NotBlank(message = "不能为空")
    @Size(min = 6, max = 64, message = "长度必须在 6-64")
    private String password;

    @Size(max = 64, message = "长度不能超过 64")
    private String nickname;

    /**
     * 加入的租户 ID；不传则后端为该用户开一个新租户（tenant_id = user.id），
     * 实现「一个用户一个租户」的默认隔离。多人共用同一租户时显式传入。
     */
    private Long tenantId;
}
