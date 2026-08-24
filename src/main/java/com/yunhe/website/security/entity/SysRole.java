package com.yunhe.website.security.entity;

import com.yunhe.website.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统角色：一组权限的集合，用于分配给用户。
 * <p>与用户、权限均为多对多关系。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role")
public class SysRole extends BaseEntity {

    /** 内置超级管理员角色编码 */
    public static final String CODE_SUPER_ADMIN = "SUPER_ADMIN";

    /** 角色编码，全局唯一，如 SUPER_ADMIN */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** 角色名称（英文） */
    @Column(nullable = false, length = 50)
    private String name;

    /** 角色名称（中文） */
    @Column(nullable = false, length = 50)
    private String nameZh;

    /** 描述 */
    @Column(length = 255)
    private String description;

    /** 是否为内置角色（内置角色不可删除） */
    @Column(name = "built_in", nullable = false)
    private boolean builtIn = false;

    /** 是否启用 */
    @Column(nullable = false)
    private boolean enabled = true;

    /** 排序值，越小越靠前 */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 角色拥有的权限 */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "sys_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<SysPermission> permissions = new LinkedHashSet<>();

    /** 拥有该角色的用户 */
    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    private Set<SysUser> users = new LinkedHashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SysRole that)) {
            return false;
        }
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
