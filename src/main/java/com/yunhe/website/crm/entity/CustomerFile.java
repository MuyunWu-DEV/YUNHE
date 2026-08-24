package com.yunhe.website.crm.entity;

import com.yunhe.website.common.base.BaseEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 客户附件：文件内容以 BLOB 存库，其余为元数据。
 */
@Getter
@Setter
@Entity
@Table(name = "crm_customer_file")
public class CustomerFile extends BaseEntity {

    /** 所属客户 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** 原始文件名 */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    /** MIME 类型 */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** 文件大小（字节） */
    @Column(nullable = false)
    private long size;

    /** 文件内容（BLOB，延迟加载） */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", columnDefinition = "longblob")
    private byte[] content;
}
