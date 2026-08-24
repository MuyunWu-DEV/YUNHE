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

    /** 按根报价单查询装箱单（1:1，用于全链追溯） */
    Optional<PackingList> findByRootQuotationId(Long rootQuotationId);

    /** 分页查询（一次性加载客户，避免 N+1） */
    @Override
    @EntityGraph(attributePaths = "customer")
    Page<PackingList> findAll(Pageable pageable);
}
