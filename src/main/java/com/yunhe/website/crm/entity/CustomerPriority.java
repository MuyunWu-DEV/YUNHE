package com.yunhe.website.crm.entity;

/**
 * 客户优先级。
 * <p>显示文案通过 i18n key 在 messages*.properties 中解析。</p>
 */
public enum CustomerPriority {

    /** 高 */
    HIGH("customer.priority.HIGH"),
    /** 中 */
    MEDIUM("customer.priority.MEDIUM"),
    /** 低 */
    LOW("customer.priority.LOW");

    private final String code;

    CustomerPriority(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
