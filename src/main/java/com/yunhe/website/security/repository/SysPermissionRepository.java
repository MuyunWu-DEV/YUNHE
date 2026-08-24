package com.yunhe.website.security.repository;

import com.yunhe.website.security.entity.SysPermission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 系统权限仓储。
 */
public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {

    Optional<SysPermission> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    /** 按模块、排序值升序返回全部权限 */
    List<SysPermission> findAllByOrderByModuleAscSortOrderAsc();
}
