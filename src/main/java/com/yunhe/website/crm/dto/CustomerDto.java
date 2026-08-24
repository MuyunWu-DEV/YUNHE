package com.yunhe.website.crm.dto;

import com.yunhe.website.crm.entity.CustomerPriority;
import com.yunhe.website.crm.entity.CustomerTag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 客户响应 DTO。
 */
public record CustomerDto(
        Long id,
        String name,
        String phone,
        String company,
        String registrationNo,
        String address,
        Set<CustomerTag> tags,
        CustomerPriority priority,
        LocalDate nextFollowUpAt,
        List<CustomerFileDto> files,
        Instant createdAt
) {
}
