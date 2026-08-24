package com.yunhe.website.security.entity;

import com.yunhe.website.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统权限：代表一个可授权的操作粒度（模块 + 资源 + 动作）。
 * <p>与角色为多对多关系。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "sys_permission", indexes = {
        @Index(name = "idx_permission_module", columnList = "module")
})
public class SysPermission extends BaseEntity {

    /** 权限编码，全局唯一，如 user:create */
    @Column(nullable = false, unique = true, length = 100)
    private String code;

    /** 权限名称（英文） */
    @Column(nullable = false, length = 100)
    private String name;

    /** 权限名称（中文） */
    @Column(nullable = false, length = 100)
    private String nameZh;

    /** 所属模块，如 user / role / permission */
    @Column(length = 50)
    private String module;

    /** 资源标识，如 /admin/users */
    @Column(length = 200)
    private String resource;

    /** 动作标识，如 create / read / update / delete */
    @Column(length = 50)
    private String action;

    /** 描述 */
    @Column(length = 255)
    private String description;

    /** 排序值，越小越靠前 */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 拥有该权限的角色 */
    @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
    private Set<SysRole> roles = new LinkedHashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SysPermission that)) {
            return false;
        }
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
