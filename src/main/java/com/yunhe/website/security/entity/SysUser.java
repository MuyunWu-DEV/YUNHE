package com.yunhe.website.security.entity;

import com.yunhe.website.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统用户：登录后台的账号。
 * <p>通过角色间接获得权限（RBAC）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user")
public class SysUser extends BaseEntity {

    /** 登录名，全局唯一 */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** 加密后的密码（BCrypt），绝不以明文存储 */
    @Column(nullable = false, length = 100)
    private String password;

    /** 姓名 */
    @Column(name = "full_name", length = 50)
    private String fullName;

    /** 邮箱 */
    @Column(length = 100)
    private String email;

    /** 手机号 */
    @Column(length = 20)
    private String phone;

    /** 是否启用（禁用后无法登录） */
    @Column(nullable = false)
    private boolean enabled = true;

    /** 是否锁定（如多次登录失败后锁定） */
    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    /** 是否强制修改密码 */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    /** 最后登录时间 */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** 最后登录 IP */
    @Column(name = "last_login_ip", length = 50)
    private String lastLoginIp;

    /** 用户拥有的角色 */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "sys_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<SysRole> roles = new LinkedHashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SysUser that)) {
            return false;
        }
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
