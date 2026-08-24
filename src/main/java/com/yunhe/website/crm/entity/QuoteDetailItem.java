package com.yunhe.website.crm.entity;

import java.math.BigDecimal;

/**
 * 报价明细项：某个品名下的一条明细（货物描述、单价、数量）。
 * <p>作为报价单 details 的值对象，随 JSON 一起存储。</p>
 */
public record QuoteDetailItem(
        String description,
        BigDecimal unitPrice,
        int quantity,
        String unit,
        String currency
) {

    /**
     * 小计 = 单价 × 数量（用于详情展示，避免在模板里做类型转换）。
     */
    public BigDecimal subtotal() {
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
