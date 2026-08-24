package com.yunhe.website.crm.entity;

import com.yunhe.website.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 商业发票版本（每次生成 PDF 留存一条历史版本）。
 * <p>时间戳由 {@link BaseEntity} 的 created_at 提供（Instant + datetime(6)，自动填充）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "crm_ci_version",
        uniqueConstraints = @UniqueConstraint(name = "uk_ci_version", columnNames = {"commercial_invoice_id", "version_no"}))
public class CommercialInvoiceVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_invoice_id", nullable = false)
    private CommercialInvoice commercialInvoice;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    /** PDF 二进制（LONGBLOB，生成功能暂未实现，留空） */
    @Lob
    @Column(name = "pdf", columnDefinition = "longblob")
    private byte[] pdf;

    /** 生成那一刻的完整数据快照（JSON，用于回看历史版本） */
    @Lob
    @Column(name = "data", columnDefinition = "longtext")
    private String data;

    /** 改动描述（如 "Manual generation" / "Automatically generated - 报价单发起变更(原因)" / 真实错误信息） */
    @Column(name = "log", columnDefinition = "text")
    private String log;
}
