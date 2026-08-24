package com.yunhe.website.security.dto;

import java.time.Instant;
import java.util.Set;

/**
 * 角色响应 DTO。
 */
public record RoleDto(
        Long id,
        String code,
        String name,
        String nameZh,
        String description,
        boolean builtIn,
        boolean enabled,
        Integer sortOrder,
        long userCount,
        Instant createdAt,
        Set<Long> permissionIds,
        Set<String> permissionCodes
) {
}
