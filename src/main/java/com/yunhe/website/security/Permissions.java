package com.yunhe.website.security;

/**
 * 权限码常量集：RBAC 权限编码的唯一事实来源。
 *
 * <p>权限码同时用于两处：</p>
 * <ul>
 *   <li>{@code DataInitializer} 初始化权限表（以 {@code Permissions.XXX} 引用）；</li>
 *   <li>各 Controller 的 {@code @PreAuthorize} 方法级授权
 *       （通过 SpEL {@code T(com.yunhe.website.security.Permissions).XXX} 引用）。</li>
 * </ul>
 * <p>常量命名规则：将权限码中的 {@code :} 与 {@code -} 替换为 {@code _}，并全大写。</p>
 */
public final class Permissions {

    private Permissions() {
    }

    // ===== 用户 =====
    public static final String USER_LIST = "user:list";
    public static final String USER_CREATE = "user:create";
    public static final String USER_UPDATE = "user:update";
    public static final String USER_DELETE = "user:delete";

    // ===== 角色 =====
    public static final String ROLE_LIST = "role:list";
    public static final String ROLE_CREATE = "role:create";
    public static final String ROLE_UPDATE = "role:update";
    public static final String ROLE_DELETE = "role:delete";

    // ===== 权限 =====
    public static final String PERMISSION_LIST = "permission:list";
    public static final String PERMISSION_CREATE = "permission:create";
    public static final String PERMISSION_UPDATE = "permission:update";
    public static final String PERMISSION_DELETE = "permission:delete";

    // ===== 客户 =====
    public static final String CUSTOMER_LIST = "customer:list";
    public static final String CUSTOMER_CREATE = "customer:create";
    public static final String CUSTOMER_UPDATE = "customer:update";
    public static final String CUSTOMER_DELETE = "customer:delete";

    // ===== 报价单 =====
    public static final String QUOTATION_LIST = "quotation:list";
    public static final String QUOTATION_CREATE = "quotation:create";
    public static final String QUOTATION_UPDATE = "quotation:update";
    public static final String QUOTATION_DELETE = "quotation:delete";

    // ===== 形式发票 =====
    public static final String PROFORMA_INVOICE_LIST = "proforma-invoice:list";
    public static final String PROFORMA_INVOICE_CREATE = "proforma-invoice:create";
    public static final String PROFORMA_INVOICE_UPDATE = "proforma-invoice:update";
    public static final String PROFORMA_INVOICE_DELETE = "proforma-invoice:delete";

    // ===== 销售订单 =====
    public static final String SALES_ORDER_LIST = "sales-order:list";
    public static final String SALES_ORDER_CREATE = "sales-order:create";
    public static final String SALES_ORDER_UPDATE = "sales-order:update";
    public static final String SALES_ORDER_DELETE = "sales-order:delete";

    // ===== 商业发票 =====
    public static final String COMMERCIAL_INVOICE_LIST = "commercial-invoice:list";
    public static final String COMMERCIAL_INVOICE_CREATE = "commercial-invoice:create";
    public static final String COMMERCIAL_INVOICE_UPDATE = "commercial-invoice:update";
    public static final String COMMERCIAL_INVOICE_DELETE = "commercial-invoice:delete";

    // ===== 装箱单 =====
    public static final String PACKING_LIST_LIST = "packing-list:list";
    public static final String PACKING_LIST_CREATE = "packing-list:create";
    public static final String PACKING_LIST_UPDATE = "packing-list:update";
    public static final String PACKING_LIST_DELETE = "packing-list:delete";

    // ===== 系统设置 =====
    public static final String SYSTEM_SETTINGS_UPDATE = "system:settings:update";
}
