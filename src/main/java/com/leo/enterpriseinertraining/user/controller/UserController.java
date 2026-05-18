package com.leo.enterpriseinertraining.user.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.security.LoginUser;
import com.leo.enterpriseinertraining.security.SecurityUtils;
import com.leo.enterpriseinertraining.user.dto.UserLoginRequest;
import com.leo.enterpriseinertraining.user.dto.UserRegisterRequest;
import com.leo.enterpriseinertraining.user.service.UserService;
import com.leo.enterpriseinertraining.user.vo.LoginVO;
import com.leo.enterpriseinertraining.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户", description = "注册 / 登录 / 当前用户")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "注册")
    public BaseResponse<UserVO> register(@RequestBody @Valid UserRegisterRequest req) {
        return ResultUtils.success(userService.register(req));
    }

    @PostMapping("/login")
    @Operation(summary = "登录")
    public BaseResponse<LoginVO> login(@RequestBody @Valid UserLoginRequest req) {
        return ResultUtils.success(userService.login(req));
    }

    @GetMapping("/me")
    @Operation(summary = "当前用户（需 JWT）")
    public BaseResponse<UserVO> me() {
        LoginUser lu = SecurityUtils.currentUserOrThrow();
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(lu.getUser(), vo);
        return ResultUtils.success(vo);
    }
}
