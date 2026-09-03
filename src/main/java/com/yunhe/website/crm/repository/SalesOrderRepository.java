package com.yunhe.website.crm.repository;

import com.yunhe.website.crm.entity.SalesOrder;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 销售订单仓储。
 */
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    /** 某 PI 是否已转订单（1:1，防重复） */
    boolean existsByProformaInvoiceId(Long proformaInvoiceId);

    /** 某客户是否有关联销售订单（删除客户守卫用） */
    boolean existsByCustomerId(Long customerId);

    /** 某根报价单是否已产生销售订单（删除报价单守卫用） */
    boolean existsByRootQuotationId(Long rootQuotationId);

    /** 按来源 PI 查询订单（1:1） */
    Optional<SalesOrder> findByProformaInvoiceId(Long proformaInvoiceId);

    /** 按根报价单查询订单（1:1，用于全链追溯） */
    Optional<SalesOrder> findByRootQuotationId(Long rootQuotationId);

    /** 分页查询（一次性加载客户，避免 N+1） */
    @Override
    @EntityGraph(attributePaths = "customer")
    Page<SalesOrder> findAll(Pageable pageable);
}
