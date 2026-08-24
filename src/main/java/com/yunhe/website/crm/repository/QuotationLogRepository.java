package com.yunhe.website.crm.repository;

import com.yunhe.website.crm.entity.QuotationLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 报价单变更日志仓储。
 */
public interface QuotationLogRepository extends JpaRepository<QuotationLog, Long> {

    /** 按报价单查询变更日志（按时间倒序） */
    List<QuotationLog> findByQuotationIdOrderByCreatedAtDesc(Long quotationId);
}
