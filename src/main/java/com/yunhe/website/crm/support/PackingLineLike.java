package com.yunhe.website.crm.support;

import java.math.BigDecimal;

/**
 * 装箱行的只读视图契约：实体 {@code PackingLine} 与 DTO {@code PackingLineDto} 共用。
 * 使 {@link PlJoins} 不耦合于具体包装类（实体 / DTO），Web 与 PDF 两条路径可共用同一 JOIN/合计逻辑。
 */
public interface PackingLineLike {

    String quoteLineKey();

    Integer packages();

    BigDecimal netWeight();

    BigDecimal grossWeight();

    BigDecimal measurement();
}
