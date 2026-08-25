package com.yunhe.website.crm.repository;

import com.yunhe.website.crm.entity.CrmTermsLib;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 条款库仓储（支持多公司，多条记录）。
 */
public interface CrmTermsLibRepository extends JpaRepository<CrmTermsLib, Long> {

    List<CrmTermsLib> findAllByOrderByIdAsc();

    /** 取第一条作为默认条款库（PI 一键导入用） */
    Optional<CrmTermsLib> findFirstByOrderByIdAsc();
}
