package com.yunhe.website.crm.repository;

import com.yunhe.website.crm.entity.PackingList;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 装箱单仓储。
 */
public interface PackingListRepository extends JpaRepository<PackingList, Long> {

    /** 某 PI 是否已生成装箱单（1:1，防重复） */
    boolean existsByProformaInvoiceId(Long proformaInvoiceId);

    /** 某客户是否有关联装箱单（删除客户守卫用） */
    boolean existsByCustomerId(Long customerId);

    /** 某根报价单是否已产生装箱单（删除报价单守卫用） */
    boolean existsByRootQuotationId(Long rootQuotationId);

    /** 按根报价单查询装箱单（1:1，用于全链追溯） */
    Optional<PackingList> findByRootQuotationId(Long rootQuotationId);

    /** 分页查询（一次性加载客户，避免 N+1） */
    @Override
    @EntityGraph(attributePaths = "customer")
    Page<PackingList> findAll(Pageable pageable);
}
