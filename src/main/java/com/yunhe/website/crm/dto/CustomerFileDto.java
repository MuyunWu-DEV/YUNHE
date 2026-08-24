package com.yunhe.website.crm.dto;

import java.time.Instant;

/**
 * 客户附件元数据（不含文件内容）。
 */
public record CustomerFileDto(
        Long id,
        String originalName,
        String contentType,
        long size,
        Instant createdAt
) {
}
