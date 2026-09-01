package com.yunhe.website.crm.dto.request;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 装箱单行表单（编辑装箱单时填写的逐货物项装箱信息）。
 * <p>与 {@link com.yunhe.website.crm.entity.PackingLine} 对应；{@code quoteLineKey} 随表单一并提交，
 * 用于回写时对齐报价单项（保持 JOIN 稳定）。</p>
 */
@Data
public class PackingLineForm {

    /** 关联报价单明细项稳定 key（只读回传，编辑时原样提交） */
    private String quoteLineKey;

    /** 件数 / 箱数 */
    private Integer packages;

    /** 净重 kg */
    private BigDecimal netWeight;

    /** 毛重 kg */
    private BigDecimal grossWeight;

    /** 体积 m³ */
    private BigDecimal measurement;
}
