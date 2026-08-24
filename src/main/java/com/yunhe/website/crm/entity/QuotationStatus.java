package com.yunhe.website.crm.entity;

/**
 * 报价单状态（行为驱动，无手选）。
 * <p>显示文案通过 i18n key 在 messages*.properties 中解析。</p>
 */
public enum QuotationStatus {

    /** 草稿（新建/编辑保存后） */
    DRAFT("quotation.status.DRAFT"),
    /** 已发送（点击下载报价单后） */
    SENT("quotation.status.SENT"),
    /** 已确认（生成 Proforma Invoice 后；此状态可发起变更） */
    CONFIRMED("quotation.status.CONFIRMED");

    private final String code;

    QuotationStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
