package com.yunhe.website.crm.entity;

import java.math.BigDecimal;

/**
 * 报价明细项：某个品名下的一条明细（货物描述、单价、数量）。
 * <p>作为报价单 details 的值对象，随 JSON 一起存储。</p>
 * <p>{@code key} 为稳定实体标识（生成即固定，不随明细重排而变），装箱单(PL)通过该 key
 * 与报价单项建立 JOIN，从而 PL 不冗余存储品名/HS Code/描述/数量，保证数据唯一性与一致性。</p>
 */
public record QuoteDetailItem(
        /** 稳定实体标识：PL 经此 key 关联报价单项；不随数组下标变化 */
        String key,
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
