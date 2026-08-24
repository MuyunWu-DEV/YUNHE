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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 形式发票（Proforma Invoice）。
 * <p>details 以 JSON 存储，含 Seller / Buyer / Terms / Bank Account Information。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "crm_proforma_invoice",
        uniqueConstraints = @UniqueConstraint(name = "uk_invoice_year_seq", columnNames = {"invoice_year", "seq"}))
public class ProformaInvoice extends BaseEntity {

    /** 单据号前缀 */
    public static final String NO_PREFIX = "YHPI";

    /** 发票号，如 YHPI-2026-001 */
    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

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

    /** 发票详情，JSON 存储 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private ProformaDetails details;

    /** 买方（客户） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** 来源报价单（1:1，唯一约束防止重复生成 PI） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", unique = true)
    private Quotation quotation;
}
