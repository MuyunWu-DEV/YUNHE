package com.yunhe.website.security.repository;

import com.yunhe.website.security.entity.SysUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 系统用户仓储。
 */
public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    /**
     * 登录认证用：一次性加载角色与权限，避免 N+1 查询。
     * <p>双重 fetch join（roles × permissions）会产生笛卡尔积重复行，必须 distinct
     * 去重，否则同一用户名可能返回多行主实体（集合 fetch 下 Optional 取多值将抛异常）。</p>
     */
    @Query("""
            select distinct u from SysUser u
            left join fetch u.roles r
            left join fetch r.permissions
            where u.username = :username
            """)
    Optional<SysUser> findWithAuthoritiesByUsername(@Param("username") String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    /** 统计拥有某角色的用户数量 */
    long countByRolesId(Long roleId);

    /** 统计启用中且拥有某角色的用户数量（防锁死守卫用，入参角色编码如 SUPER_ADMIN） */
    long countByRoles_CodeAndEnabledTrue(String roleCode);

    /**
     * 角色用户数统计投影。
     */
    interface RoleUserCount {

        Long getId();

        Long getCount();
    }

    /** 按角色分组统计用户数量 */
    @Query("select r.id as id, count(u.id) as count from SysUser u join u.roles r group by r.id")
    List<RoleUserCount> countUsersGroupByRole();

    /** 分页查询，并一次性加载角色，避免 N+1 查询 */
    @Override
    @EntityGraph(attributePaths = "roles")
    Page<SysUser> findAll(Pageable pageable);

    /** 按关键词（用户名 / 姓名 / 邮箱）模糊分页查询 */
    @EntityGraph(attributePaths = "roles")
    @Query("""
            select u from SysUser u
            where lower(u.username) like lower(concat('%', :keyword, '%'))
               or lower(u.fullName) like lower(concat('%', :keyword, '%'))
               or lower(u.email) like lower(concat('%', :keyword, '%'))
            """)
    Page<SysUser> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
