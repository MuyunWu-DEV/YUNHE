package com.yunhe.website.crm.entity;

/**
 * 单据生成状态（PI / CI / PL 通用）。
 * <p>显示文案通过 i18n key 在 messages*.properties 中解析。</p>
 */
public enum DocumentStatus {

    /** 待生成（单据记录新建，尚未生成 PDF） */
    PENDING_GENERATION("document.status.PENDING_GENERATION"),
    /** 待重新生成（来源报价单已变更，单据过期） */
    PENDING_REGENERATION("document.status.PENDING_REGENERATION"),
    /** 已生成（PDF 已产出并留存版本） */
    GENERATED("document.status.GENERATED");

    private final String code;

    DocumentStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
