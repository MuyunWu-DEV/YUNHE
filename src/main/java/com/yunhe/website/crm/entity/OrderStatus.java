package com.yunhe.website.crm.entity;

/**
 * 订单状态。
 * <p>显示文案通过 i18n key 在 messages*.properties 中解析。</p>
 */
public enum OrderStatus {

    /** 草稿 */
    DRAFT("order.status.DRAFT"),
    /** 已确认 */
    CONFIRMED("order.status.CONFIRMED"),
    /** 已发货 */
    SHIPPED("order.status.SHIPPED");

    private final String code;

    OrderStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
