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
 * 装箱单（Packing List）。
 * <p>由形式发票（PI）生成（1:1），货物明细通过根报价单引用，装箱信息由用户填写。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "crm_packing_list",
        uniqueConstraints = @UniqueConstraint(name = "uk_pl_year_seq", columnNames = {"packing_year", "seq"}))
public class PackingList extends BaseEntity {

    /** 单据号前缀 */
    public static final String NO_PREFIX = "YHPL";

    /** 装箱单号，如 YHPL-2026-001 */
    @Column(name = "packing_no", nullable = false, unique = true, length = 50)
    private String packingNo;

    /** 年份（用于按年重置序列号） */
    @Column(name = "packing_year", nullable = false)
    private int packingYear;

    /** 年内序列号 */
    @Column(nullable = false)
    private int seq;

    /** 装箱日期 */
    @Column(name = "packing_date")
    private LocalDate packingDate;

    /** 生成状态 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentStatus status = DocumentStatus.PENDING_GENERATION;

    /** 唛头（Marks） */
    @Column(length = 500)
    private String marks;

    /** 件数 / 箱数 */
    @Column(name = "number_of_packages")
    private Integer numberOfPackages;

    /** 毛重 */
    @Column(name = "gross_weight", precision = 14, scale = 3)
    private BigDecimal grossWeight;

    /** 净重 */
    @Column(name = "net_weight", precision = 14, scale = 3)
    private BigDecimal netWeight;

    /** 体积（立方米） */
    @Column(precision = 14, scale = 3)
    private BigDecimal volume;

    /** 备注 */
    @Column(length = 500)
    private String remark;

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
