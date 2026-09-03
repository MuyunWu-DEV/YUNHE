package com.yunhe.website.crm.repository;

import com.yunhe.website.crm.entity.Quotation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 报价单仓储。
 */
public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    /** 某客户是否有关联报价单（删除客户守卫用） */
    boolean existsByCustomerId(Long customerId);

    /** 分页查询，并一次性加载客户（避免 N+1） */
    @Override
    @EntityGraph(attributePaths = "customer")
    Page<Quotation> findAll(Pageable pageable);
}
