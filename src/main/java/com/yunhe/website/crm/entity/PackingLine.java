package com.yunhe.website.crm.entity;

import com.yunhe.website.crm.support.PackingLineLike;
import java.math.BigDecimal;

/**
 * 装箱单行：装箱单(Packing List)中「逐货物项」的装箱信息。
 * <p>作为 {@link PackingList#lines} 的值对象，随 JSON 一起存储。</p>
 *
 * <p>本类只保存「装箱专属」数据（与物理打包相关），<b>不冗余</b>品名/HS Code/描述/数量等货物字段——
 * 那些字段由 {@code quoteLineKey} 在渲染时 JOIN 回来源报价单项取得（单一数据源，保证一致性）。</p>
 *
 * <p>各项均为「整行合计」语义（非每箱）：例如某品名 24 SETS 装 2 箱，则 {@code packages=2}、
 * {@code netWeight}=整行净重、{@code grossWeight}=整行毛重、{@code measurement}=整行体积。</p>
 */
public record PackingLine(
        /** 关联报价单明细项的稳定 key（QuoteDetailItem.key），渲染时据此 JOIN 取货物字段 */
        String quoteLineKey,
        /** 件数 / 箱数（整行合计） */
        Integer packages,
        /** 净重 kg（整行合计） */
        BigDecimal netWeight,
        /** 毛重 kg（整行合计） */
        BigDecimal grossWeight,
        /** 体积 m³（整行合计） */
        BigDecimal measurement
) implements PackingLineLike {
}
