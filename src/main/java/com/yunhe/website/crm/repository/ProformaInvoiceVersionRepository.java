package com.yunhe.website.crm.repository;

import com.yunhe.website.crm.entity.ProformaInvoiceVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 形式发票版本仓储。
 */
public interface ProformaInvoiceVersionRepository extends JpaRepository<ProformaInvoiceVersion, Long> {

    @Query("select coalesce(max(v.versionNo), 0) from ProformaInvoiceVersion v where v.proformaInvoice.id = :piId")
    int findMaxVersionNo(@Param("piId") Long piId);

    /** 版本历史列表（轻量投影，只取列表展示所需列，不加载 pdf/data 大字段） */
    List<DocVersionSummary> findByProformaInvoiceIdOrderByVersionNoDesc(Long piId);

    /** 版本是否存在且归属于指定形式发票（下载越权校验用） */
    boolean existsByIdAndProformaInvoiceId(Long id, Long proformaInvoiceId);

    /** 删除某形式发票的全部版本（删除主单据前级联清理其从属版本） */
    void deleteByProformaInvoiceId(Long proformaInvoiceId);
}
