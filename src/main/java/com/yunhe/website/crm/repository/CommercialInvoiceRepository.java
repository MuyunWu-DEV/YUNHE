package com.yunhe.website.crm.repository;

import com.yunhe.website.crm.entity.CommercialInvoice;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 商业发票仓储。
 */
public interface CommercialInvoiceRepository extends JpaRepository<CommercialInvoice, Long> {

    /** 某 PI 是否已生成商业发票（1:1，防重复） */
    boolean existsByProformaInvoiceId(Long proformaInvoiceId);

    /** 某客户是否有关联商业发票（删除客户守卫用） */
    boolean existsByCustomerId(Long customerId);

    /** 某根报价单是否已产生商业发票（删除报价单守卫用） */
    boolean existsByRootQuotationId(Long rootQuotationId);

    /** 按根报价单查询商业发票（1:1，用于全链追溯） */
    Optional<CommercialInvoice> findByRootQuotationId(Long rootQuotationId);

    /** 分页查询（一次性加载客户，避免 N+1） */
    @Override
    @EntityGraph(attributePaths = "customer")
    Page<CommercialInvoice> findAll(Pageable pageable);
}
