package com.leo.enterpriseinertraining.user.dto;

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
}
