package com.yunhe.website.security.dto;

import java.time.Instant;
import java.util.Set;

/**
 * 用户响应 DTO（对外展示，不含密码）。
 */
public record UserDto(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        boolean enabled,
        boolean accountLocked,
        boolean mustChangePassword,
        Instant lastLoginAt,
        String lastLoginIp,
        Instant createdAt,
        Set<RoleSummary> roles
) {

    /**
     * 用户关联角色的摘要信息。
     */
    public record RoleSummary(Long id, String code, String name, String nameZh) {
    }
}
