package com.yunhe.website.crm.dto;

import com.yunhe.website.crm.entity.DocumentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 商业发票响应 DTO。
 * <p>货物明细不在此处冗余，展示时从根报价单引用。</p>
 */
public record CommercialInvoiceDto(
        Long id,
        String invoiceNo,
        LocalDate invoiceDate,
        DocumentStatus status,
        String remark,
        BigDecimal depositPercentage,
        String depositPaymentMethod,
        String balancePaymentMethod,
        CustomerSummary customer,
        Long proformaInvoiceId,
        Long rootQuotationId,
        Instant createdAt
) {

    /** 尾款付款比例 = 100% - 定金百分比（派生值，不落库） */
    public BigDecimal balancePercentage() {
        return depositPercentage == null ? null : BigDecimal.valueOf(100).subtract(depositPercentage);
    }

    public record CustomerSummary(Long id, String name, String company) {
    }
}
