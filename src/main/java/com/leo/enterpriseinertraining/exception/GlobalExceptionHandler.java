package com.leo.enterpriseinertraining.exception;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
@Hidden
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("参数校验失败");
        return ResultUtils.error(ErrorCode.PARAMS_ERROR.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public BaseResponse<?> handleBind(BindException e) {
        return ResultUtils.error(ErrorCode.PARAMS_ERROR.getCode(), "参数绑定失败");
    }

    @ExceptionHandler(AuthenticationException.class)
    public BaseResponse<?> handleAuth(AuthenticationException e) {
        log.warn("AuthenticationException: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public BaseResponse<?> handleAccessDenied(AccessDeniedException e) {
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR);
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}