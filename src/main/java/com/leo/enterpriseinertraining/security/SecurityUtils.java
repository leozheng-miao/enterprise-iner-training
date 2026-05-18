package com.leo.enterpriseinertraining.security;

import com.leo.enterpriseinertraining.exception.BusinessException;
import com.leo.enterpriseinertraining.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static LoginUser currentUserOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser lu)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return lu;
    }

    public static Long currentUserId() {
        return currentUserOrThrow().getId();
    }
}
