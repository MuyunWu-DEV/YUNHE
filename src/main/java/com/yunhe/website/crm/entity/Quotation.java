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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 报价单。
 * <p>details 以 JSON 存储，结构为：品名分组列表，每组含 HS Code 和明细项。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "crm_quotation")
public class Quotation extends BaseEntity {

    /** 报价日期 */
    @Column(name = "quote_date", nullable = false)
    private LocalDate quoteDate;

    /** 状态 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuotationStatus status = QuotationStatus.DRAFT;

    /** 备注 */
    @Column(length = 500)
    private String remark;

    /** 明细：品名分组列表，JSON 存储 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<QuoteDetailGroup> details = new ArrayList<>();

    /** 关联客户 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;
}
