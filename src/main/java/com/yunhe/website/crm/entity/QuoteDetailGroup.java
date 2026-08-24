package com.yunhe.website.crm.entity;

import java.util.List;

/**
 * 报价明细分组：一个品名（含 HS Code）对应一组明细项。
 * <p>替代原先 {@code Map<String, List<QuoteDetailItem>>} 的 Map entry，
 * 随 JSON 存储在报价单 / 销售订单的 details 字段中。</p>
 */
public record QuoteDetailGroup(
        String name,
        String hsCode,
        List<QuoteDetailItem> items
) {
}

