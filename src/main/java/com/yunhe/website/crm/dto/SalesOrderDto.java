package com.yunhe.website.crm.dto;

import com.yunhe.website.crm.entity.OrderStatus;
import com.yunhe.website.crm.entity.QuoteDetailGroup;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售订单响应 DTO。
 */
public record SalesOrderDto(
        Long id,
        String orderNo,
        LocalDate orderDate,
        OrderStatus status,
        String remark,
        List<QuoteDetailGroup> details,
        CustomerSummary customer,
        Long proformaInvoiceId,
        Long rootQuotationId,
        Instant createdAt
) {

    /**
     * 关联客户摘要。
     */
    public record CustomerSummary(Long id, String name, String company) {
    }
}
