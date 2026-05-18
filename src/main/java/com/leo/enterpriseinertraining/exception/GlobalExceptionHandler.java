package com.leo.enterpriseinertraining.exception;


import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @program: yu-picture
 * @description: 全局异常管理器
 * @author: Miao Zheng
 * @date: 2025-10-20 17:03
 **/
@RestControllerAdvice // 环绕切面
@Slf4j
@Hidden
public class GlobalExceptionHandler {

    /**
     * 任何一个方法抛出这个异常时， 这个方法都会捕获到
     *
     * @param e
     * @return
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException：{}", e.getMessage());
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * 任何一个方法抛出这个异常时， 这个方法都会捕获到
     *
     * @param e
     * @return
     */
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> businessExceptionHandler(RuntimeException e) {
        log.error("RuntimeException：{}", e.getMessage());

        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

//    /**
//     * 未登录时报这个异常
//     *
//     * @param e
//     * @return
//     */
//    @ExceptionHandler(NotLoginException.class)
//    public BaseResponse<?> notLoginException(NotLoginException e) {
//        log.error("NotLoginException", e);
//        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, e.getMessage());
//    }
//
//    /**
//     * 没有权限时报这个异常
//     *
//     * @param e
//     * @return
//     */
//    @ExceptionHandler(NotPermissionException.class)
//    public BaseResponse<?> notPermissionExceptionHandler(NotPermissionException e) {
//        log.error("NotPermissionException", e);
//        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, e.getMessage());
//    }


}