package com.yunhe.website.crm.entity;

/**
 * 客户标签。
 * <p>显示文案通过 i18n key 在 messages*.properties 中解析。</p>
 */
public enum CustomerTag {

    /** 重要客户 */
    VIP("customer.tag.VIP"),
    /** 重点客户 */
    KEY("customer.tag.KEY"),
    /** 新客户 */
    NEW("customer.tag.NEW"),
    /** 老客户 */
    OLD("customer.tag.OLD"),
    /** 潜在客户 */
    POTENTIAL("customer.tag.POTENTIAL"),
    /** 流失客户 */
    LOST("customer.tag.LOST");

    private final String code;

    CustomerTag(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
