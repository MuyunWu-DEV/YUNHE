package com.yunhe.website.crm.dto;

import com.yunhe.website.crm.entity.DocumentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 装箱单响应 DTO。
 * <p>整单合计（件数/毛重/净重/体积）由实体派生 getter 提供（lines 求和）；{@code lines} 为逐货物项装箱数据，
 * 货物字段（品名/HS Code/描述/数量）展示时由前端经 quoteLineKey JOIN 报价单项取得。</p>
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
        List<PackingLineDto> lines,
        String remark,
        CustomerSummary customer,
        Long proformaInvoiceId,
        Long rootQuotationId,
        Instant createdAt
) {

    public record CustomerSummary(Long id, String name, String company) {
    }
}
