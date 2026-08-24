package com.yunhe.website.crm.entity;

import com.yunhe.website.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 销售订单（Sales Order）。
 * <p>由形式发票（PI）转换而来（1:1），details 快照来源报价单的货物明细。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "crm_sales_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_year_seq", columnNames = {"order_year", "seq"}))
public class SalesOrder extends BaseEntity {

    /** 单据号前缀 */
    public static final String NO_PREFIX = "YHSO";

    /** 订单号，如 YHSO-2026-001 */
    @Column(name = "order_no", nullable = false, unique = true, length = 50)
    private String orderNo;

    /** 年份（用于按年重置序列号） */
    @Column(name = "order_year", nullable = false)
    private int orderYear;

    /** 年内序列号 */
    @Column(nullable = false)
    private int seq;

    /** 订单日期 */
    @Column(name = "order_date")
    private LocalDate orderDate;

    /** 状态 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.DRAFT;

    /** 备注 */
    @Column(length = 500)
    private String remark;

    /** 货物明细：品名分组列表，JSON 存储，转换时快照自报价单 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<QuoteDetailGroup> details = new ArrayList<>();

    /** 买方（客户） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** 来源形式发票（1:1，唯一） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proforma_invoice_id", unique = true)
    private ProformaInvoice proformaInvoice;

    /** 根报价单 ID（冗余指针，用于全链追溯） */
    @Column(name = "root_quotation_id")
    private Long rootQuotationId;
}
