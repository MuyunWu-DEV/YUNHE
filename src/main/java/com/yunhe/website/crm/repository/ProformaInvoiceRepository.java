package com.yunhe.website.crm.repository;

import com.yunhe.website.crm.entity.ProformaInvoice;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 形式发票仓储。
 */
public interface ProformaInvoiceRepository extends JpaRepository<ProformaInvoice, Long> {

    /** 分页查询（一次性加载客户，避免 N+1） */
    @Override
    @EntityGraph(attributePaths = "customer")
    Page<ProformaInvoice> findAll(Pageable pageable);

    /** 按来源报价单批量查询（1:1，用于报价单列表展示 PI 关联） */
    @Query("select p from ProformaInvoice p where p.quotation.id in :quotationIds")
    List<ProformaInvoice> findByQuotationIdIn(@Param("quotationIds") Collection<Long> quotationIds);

    /** 某报价单是否已生成 PI（1:1，防重复） */
    boolean existsByQuotationId(Long quotationId);

    /** 按来源报价单查询 PI（1:1，用于全链追溯） */
    Optional<ProformaInvoice> findByQuotationId(Long quotationId);
}
