package com.yunhe.website.crm.dto;

import com.yunhe.website.crm.entity.QuoteDetailGroup;
import com.yunhe.website.crm.entity.QuotationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 报价单响应 DTO。
 */
public record QuotationDto(
        Long id,
        LocalDate quoteDate,
        QuotationStatus status,
        String remark,
        List<QuoteDetailGroup> details,
        CustomerSummary customer,
        Long proformaInvoiceId,
        Instant createdAt
) {

    /**
     * 关联客户摘要。
     */
    public record CustomerSummary(Long id, String name, String company, String phone) {
    }

    /** 附带 PI id（用于列表页「查看 Proforma Invoice」按钮） */
    public QuotationDto withProformaInvoiceId(Long piId) {
        return new QuotationDto(id, quoteDate, status, remark, details, customer, piId, createdAt);
    }
}
