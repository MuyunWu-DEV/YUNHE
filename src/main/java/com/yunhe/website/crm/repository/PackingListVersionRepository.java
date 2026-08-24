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

    List<PackingListVersion> findByPackingListIdOrderByVersionNoDesc(Long plId);
}
