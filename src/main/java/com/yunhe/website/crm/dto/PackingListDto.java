package com.yunhe.website.crm.dto;

import com.yunhe.website.crm.entity.DocumentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 装箱单响应 DTO。
 * <p>货物明细不在此处冗余，展示时从根报价单引用。</p>
 */
public record PackingListDto(
        Long id,
        String packingNo,
        LocalDate packingDate,
        DocumentStatus status,
        String marks,
        Integer numberOfPackages,
        BigDecimal grossWeight,
        BigDecimal netWeight,
        BigDecimal volume,
        String remark,
        CustomerSummary customer,
        Long proformaInvoiceId,
        Long rootQuotationId,
        Instant createdAt
) {

    public record CustomerSummary(Long id, String name, String company) {
    }
}
