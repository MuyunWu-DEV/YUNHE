package com.yunhe.website.security.repository;

import com.yunhe.website.security.entity.SysRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 系统角色仓储。
 */
public interface SysRoleRepository extends JpaRepository<SysRole, Long> {

    Optional<SysRole> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    /** 按排序值升序返回全部角色，并预加载权限，避免 N+1 */
    @Override
    @EntityGraph(attributePaths = "permissions")
    List<SysRole> findAll();
}
