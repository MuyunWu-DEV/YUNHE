package com.yunhe.website.security.dto;

/**
 * 权限响应 DTO。
 */
public record PermissionDto(
        Long id,
        String code,
        String name,
        String nameZh,
        String module,
        String resource,
        String action,
        String description,
        Integer sortOrder
) {
}
