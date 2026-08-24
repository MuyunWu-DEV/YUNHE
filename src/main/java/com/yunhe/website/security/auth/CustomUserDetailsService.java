package com.yunhe.website.security.auth;

import com.yunhe.website.security.entity.SysPermission;
import com.yunhe.website.security.entity.SysRole;
import com.yunhe.website.security.entity.SysUser;
import com.yunhe.website.security.repository.SysUserRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 加载用户及其角色、权限，构建认证主体。
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final SysUserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userRepository.findWithAuthoritiesByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在：" + username));

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (SysRole role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
            for (SysPermission permission : role.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(permission.getCode()));
            }
        }
        return new CustomUserDetails(user, authorities);
    }
}
