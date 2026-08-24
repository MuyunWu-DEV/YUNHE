package com.yunhe.website.security.auth;

import com.yunhe.website.security.entity.SysUser;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 自定义用户详情：包装 {@link SysUser}，供 Spring Security 使用。
 * <p>同时对外暴露原始用户信息，便于视图层展示姓名等字段。</p>
 */
public class CustomUserDetails implements UserDetails {

    @Getter
    private final SysUser user;

    private final Set<GrantedAuthority> authorities;

    public CustomUserDetails(SysUser user, Collection<? extends GrantedAuthority> authorities) {
        this.user = user;
        this.authorities = new LinkedHashSet<>(authorities);
    }

    /** 用户 ID */
    public Long getUserId() {
        return user.getId();
    }

    /** 展示名：优先姓名，其次登录名 */
    public String getDisplayName() {
        return (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : user.getUsername();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !user.isAccountLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }
}
