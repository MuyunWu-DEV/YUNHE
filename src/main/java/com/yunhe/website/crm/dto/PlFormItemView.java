package com.yunhe.website.crm.dto;

import java.math.BigDecimal;

/**
 * 装箱单编辑页「逐货物项」只读视图：货物字段来自报价单（经 quoteLineKey 关联），
 * 用于在编辑表单中展示每项对应的品名/HS Code/描述/数量，便于用户填写装箱数据。
 */
public record PlFormItemView(
        String quoteLineKey,
        String groupName,
        String hsCode,
        String description,
        Integer quantity,
        String unit
) {
}
