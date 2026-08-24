package com.yunhe.website.crm.entity;

import com.yunhe.website.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 报价单变更日志（仅记录「发起变更」）。
 * <p>时间戳由 {@link BaseEntity} 的 created_at 提供（Instant + datetime(6)，自动填充）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "crm_quotation_log",
        indexes = @Index(name = "idx_quotation_log_quotation", columnList = "quotation_id"))
public class QuotationLog extends BaseEntity {

    /** 关联报价单 ID（普通列，不设外键，避免报价单删除后日志丢失） */
    @Column(name = "quotation_id", nullable = false)
    private Long quotationId;

    /** 变更原因（发起变更必填） */
    @Column(name = "change_reason", nullable = false, length = 500)
    private String changeReason;

    /** 操作人用户名 */
    @Column(name = "changed_by", length = 50)
    private String changedBy;
}
