package com.yunhe.website.crm.repository;

import com.yunhe.website.crm.entity.PackingListVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 装箱单版本仓储。
 */
public interface PackingListVersionRepository extends JpaRepository<PackingListVersion, Long> {

    @Query("select coalesce(max(v.versionNo), 0) from PackingListVersion v where v.packingList.id = :plId")
    int findMaxVersionNo(@Param("plId") Long plId);

    /** 版本历史列表（轻量投影，只取列表展示所需列，不加载 pdf/data 大字段） */
    List<DocVersionSummary> findByPackingListIdOrderByVersionNoDesc(Long plId);

    /** 版本是否存在且归属于指定装箱单（下载越权校验用） */
    boolean existsByIdAndPackingListId(Long id, Long packingListId);

    /** 删除某装箱单的全部版本（删除主单据前级联清理其从属版本） */
    void deleteByPackingListId(Long packingListId);
}
