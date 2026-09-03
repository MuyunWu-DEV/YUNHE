package com.yunhe.website.crm.dto;

import com.yunhe.website.crm.support.PackingLineLike;
import java.math.BigDecimal;

/**
 * 装箱单行响应 DTO（与 {@link com.yunhe.website.crm.entity.PackingLine} 对应）。
 * <p>仅承载装箱专属数据；货物字段（品名/HS Code/描述/数量）由前端经 {@code quoteLineKey} JOIN 报价单项取得。</p>
 */
public record PackingLineDto(
        String quoteLineKey,
        Integer packages,
        BigDecimal netWeight,
        BigDecimal grossWeight,
        BigDecimal measurement
) implements PackingLineLike {
}
