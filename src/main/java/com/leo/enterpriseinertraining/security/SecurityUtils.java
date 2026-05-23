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

    /**
     * 当前登录用户所属的租户 ID。所有业务查询写入与读取都应按此过滤，
     * 实现多租户逻辑隔离（user.tenant_id 在登录加载时已写入 LoginUser）。
     * 老用户没有 tenant_id 时默认 0。
     */
    public static Long currentTenantId() {
        Long t = currentUserOrThrow().getUser().getTenantId();
        return t == null ? 0L : t;
    }
}
