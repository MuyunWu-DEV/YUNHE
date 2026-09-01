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
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 装箱单（Packing List）。
 * <p>由形式发票（PI）生成（1:1），货物字段通过根报价单引用（渲染时按 {@link PackingLine#quoteLineKey()} JOIN），
 * 装箱信息（逐行净重/毛重/件数/体积）由用户填写，存于 {@link #lines} JSON 列。</p>
 *
 * <p>整单合计（件数/毛重/净重/体积）<b>不冗余存储</b>，统一由 {@code lines} 求和派生（见各 {@code getXxx()}），
 * 从而保证「单一数据源 = lines」，避免合计与逐行数据不一致。</p>
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

    /** 装箱逐行数据（JSON 存储）；货物字段经 quoteLineKey JOIN 回报价单项，不在此冗余 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "packing_lines", columnDefinition = "json")
    private List<PackingLine> lines = new ArrayList<>();

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

    // ===================== 派生合计（单一数据源 = lines，避免冗余不一致） =====================

    /** 总件数 / 箱数 = 各行 packages 求和 */
    public Integer getNumberOfPackages() {
        return sumInt(lines, PackingLine::packages);
    }

    /** 总毛重 kg = 各行 grossWeight 求和 */
    public BigDecimal getGrossWeight() {
        return sumDecimal(lines, PackingLine::grossWeight);
    }

    /** 总净重 kg = 各行 netWeight 求和 */
    public BigDecimal getNetWeight() {
        return sumDecimal(lines, PackingLine::netWeight);
    }

    /** 总体积 m³ = 各行 measurement 求和 */
    public BigDecimal getVolume() {
        return sumDecimal(lines, PackingLine::measurement);
    }

    private static Integer sumInt(List<PackingLine> lines, java.util.function.Function<PackingLine, Integer> getter) {
        if (lines == null || lines.isEmpty()) return null;
        int sum = 0;
        boolean any = false;
        for (PackingLine l : lines) {
            Integer v = getter.apply(l);
            if (v != null) {
                sum += v;
                any = true;
            }
        }
        return any ? sum : null;
    }

    private static BigDecimal sumDecimal(List<PackingLine> lines, java.util.function.Function<PackingLine, BigDecimal> getter) {
        if (lines == null || lines.isEmpty()) return null;
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (PackingLine l : lines) {
            BigDecimal v = getter.apply(l);
            if (v != null) {
                sum = sum.add(v);
                any = true;
            }
        }
        return any ? sum : null;
    }
}
