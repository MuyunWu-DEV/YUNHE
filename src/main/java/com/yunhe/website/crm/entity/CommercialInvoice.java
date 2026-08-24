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
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * 商业发票（Commercial Invoice）。
 * <p>由形式发票（PI）生成（1:1），货物明细通过根报价单引用，不在本表冗余。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "crm_commercial_invoice",
        uniqueConstraints = @UniqueConstraint(name = "uk_ci_year_seq", columnNames = {"invoice_year", "seq"}))
public class CommercialInvoice extends BaseEntity {

    /** 单据号前缀 */
    public static final String NO_PREFIX = "YHINV";

    /** 发票号，如 YHINV-2026-001 */
    @Column(name = "invoice_no", nullable = false, unique = true, length = 50)
    private String invoiceNo;

    /** 年份（用于按年重置序列号） */
    @Column(name = "invoice_year", nullable = false)
    private int invoiceYear;

    /** 年内序列号 */
    @Column(nullable = false)
    private int seq;

    /** 发票日期 */
    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    /** 生成状态 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentStatus status = DocumentStatus.PENDING_GENERATION;

    /** 备注 */
    @Column(length = 500)
    private String remark;

    /** 定金百分比（0-100） */
    @Column(name = "deposit_percentage", precision = 5, scale = 2)
    private BigDecimal depositPercentage;

    /** 定金付款方式 */
    @Column(name = "deposit_payment_method", length = 200)
    private String depositPaymentMethod;

    /** 尾款付款方式 */
    @Column(name = "balance_payment_method", length = 200)
    private String balancePaymentMethod;

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
