package com.yunhe.website.crm.dto;

import com.yunhe.website.crm.entity.DocumentStatus;
import com.yunhe.website.crm.entity.ProformaDetails;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 形式发票响应 DTO。
 */
public record ProformaInvoiceDto(
        Long id,
        String invoiceNumber,
        LocalDate invoiceDate,
        DocumentStatus status,
        ProformaDetails details,
        CustomerSummary customer,
        Long quotationId,
        Instant createdAt
) {

    /**
     * 买方（客户）摘要。
     */
    public record CustomerSummary(Long id, String name, String company) {
    }
}
