package com.yunhe.website;

import com.yunhe.website.security.repository.SysPermissionRepository;
import com.yunhe.website.security.repository.SysRoleRepository;
import com.yunhe.website.security.repository.SysUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 冒烟测试：验证应用上下文可正常启动（基于 H2 内存库），且种子数据初始化成功。
 */
@SpringBootTest
@ActiveProfiles("test")
class WebsiteApplicationTests {

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysPermissionRepository permissionRepository;

    @Test
    void contextLoads() {
        // 若 Spring 上下文能正常装配并执行 DataInitializer，则视为通过
    }

    @Test
    void seedDataIsInitialized() {
        assertThat(userRepository.findByUsername("admin")).isPresent();
        assertThat(roleRepository.findByCode("SUPER_ADMIN")).isPresent();
        assertThat(permissionRepository.findByCode("user:create")).isPresent();
    }
}
