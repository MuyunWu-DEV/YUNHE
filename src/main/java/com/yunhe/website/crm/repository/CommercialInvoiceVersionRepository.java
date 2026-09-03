package com.yunhe.website.crm.repository;

import com.yunhe.website.crm.entity.CommercialInvoiceVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 商业发票版本仓储。
 */
public interface CommercialInvoiceVersionRepository extends JpaRepository<CommercialInvoiceVersion, Long> {

    @Query("select coalesce(max(v.versionNo), 0) from CommercialInvoiceVersion v where v.commercialInvoice.id = :ciId")
    int findMaxVersionNo(@Param("ciId") Long ciId);

    /** 版本历史列表（轻量投影，只取列表展示所需列，不加载 pdf/data 大字段） */
    List<DocVersionSummary> findByCommercialInvoiceIdOrderByVersionNoDesc(Long ciId);

    /** 版本是否存在且归属于指定商业发票（下载越权校验用） */
    boolean existsByIdAndCommercialInvoiceId(Long id, Long commercialInvoiceId);

    /** 删除某商业发票的全部版本（删除主单据前级联清理其从属版本） */
    void deleteByCommercialInvoiceId(Long commercialInvoiceId);
}
