package com.yunhe.website.common.base;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 实体基类：统一主键与审计字段。
 * <p>所有实体继承本类即可自动获得自增主键、创建时间、更新时间。</p>
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 创建时间（由 JPA Auditing 自动填充） */
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** 最后更新时间（由 JPA Auditing 自动填充） */
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
