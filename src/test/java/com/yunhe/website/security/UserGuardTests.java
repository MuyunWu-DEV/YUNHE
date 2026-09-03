package com.yunhe.website.security;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.security.dto.request.UserForm;
import com.yunhe.website.security.entity.SysRole;
import com.yunhe.website.security.entity.SysUser;
import com.yunhe.website.security.repository.SysRoleRepository;
import com.yunhe.website.security.repository.SysUserRepository;
import com.yunhe.website.security.service.UserService;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S2 超管提权保护与防锁死守卫集成测试。
 *
 * <p>测试上下文（H2 + @ActiveProfiles("test")）启动时 {@code DataInitializer} 会播种
 * SUPER_ADMIN / ADMIN / USER 三个角色与一个启用的超管账号 {@code admin}
 * （mustChangePassword=true），本测试直接复用作为"系统唯一启用超管"的基线。</p>
 *
 * <p>被验证路径：{@link UserService#update}/{@link UserService#create}/{@link UserService#delete}
 * 中 currentUserIsSuperAdmin（按登录名查库判超管）驱动的三条守卫：
 * ①非超管不可给普通账号分配 SUPER_ADMIN；②非超管不可修改超管账号；
 * ③防锁死——系统必须保留至少一个启用的超管。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserGuardTests {

    @Autowired
    private UserService userService;

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // =====================================================================
    //  主验证：ADMIN（非超管）给普通账号勾 SUPER_ADMIN → 必须被拒
    // =====================================================================

    @Test
    void adminCannotGrantSuperAdminRoleOnUpdate() {
        SysUser operator = saveUser("guard_op_admin", "操作员(ADMIN)", role("ADMIN"));
        SysUser target = saveUser("guard_tgt_user", "普通用户(USER)", role("USER"));

        UserForm form = updateForm(target, Set.of(role("SUPER_ADMIN").getId()), true);

        assertThatThrownBy(() -> userService.update(target.getId(), form, operator.getUsername()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅超级管理员可分配超级管理员角色");
    }

    @Test
    void adminCannotGrantSuperAdminRoleOnCreate() {
        SysUser operator = saveUser("guard_op_admin2", "操作员(ADMIN)", role("ADMIN"));

        UserForm form = new UserForm();
        form.setUsername("guard_new_super");
        form.setPassword("Xyz@12345");
        form.setFullName("被提权新号");
        form.setEnabled(true);
        form.setRoleIds(Set.of(role("SUPER_ADMIN").getId()));

        assertThatThrownBy(() -> userService.create(form, operator.getUsername()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅超级管理员可分配超级管理员角色");
    }

    @Test
    void adminCannotModifySuperAdminAccount() {
        SysUser operator = saveUser("guard_op_admin3", "操作员(ADMIN)", role("ADMIN"));
        SysUser superAdmin = seededAdmin();

        // 目标本身是超管：非超管操作者连"编辑"都会被拦（保留其原角色不变亦不行）
        UserForm form = updateForm(superAdmin, Set.of(role("SUPER_ADMIN").getId()), true);

        assertThatThrownBy(() -> userService.update(superAdmin.getId(), form, operator.getUsername()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅超级管理员可修改超级管理员账号");
    }

    // =====================================================================
    //  防误伤（正向）：超管本人可正常提升/管理，防锁死只在"最后一个启用超管"时触发
    // =====================================================================

    @Test
    void superAdminCanPromoteOrdinaryUserToSuperAdmin() {
        SysUser superOperator = seededAdmin();
        SysUser target = saveUser("guard_tgt_promote", "待提升用户", role("USER"));

        UserForm form = updateForm(target, Set.of(role("SUPER_ADMIN").getId()), true);

        assertThatCode(() -> userService.update(target.getId(), form, superOperator.getUsername()))
                .doesNotThrowAnyException();

        SysUser reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(isSuperAdmin(reloaded)).isTrue();
    }

    @Test
    void superAdminCannotDemoteLastEnabledSuperAdmin() {
        SysUser superOperator = seededAdmin();
        SysUser onlySuper = seededAdmin(); // DataInitializer 播种的唯一启用超管（admin）

        // 把唯一启用超管降级为 ADMIN → 防锁死拒绝
        UserForm demote = updateForm(onlySuper, Set.of(role("ADMIN").getId()), true);

        assertThatThrownBy(() -> userService.update(onlySuper.getId(), demote, superOperator.getUsername()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统必须保留至少一个启用的超级管理员");

        // 禁用唯一启用超管 → 同样拒绝
        UserForm disable = updateForm(onlySuper, Set.of(role("SUPER_ADMIN").getId()), false);

        assertThatThrownBy(() -> userService.update(onlySuper.getId(), disable, superOperator.getUsername()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("系统必须保留至少一个启用的超级管理员");
    }

    @Test
    void superAdminCanDisableWhenAnotherEnabledSuperExists() {
        SysUser superOperator = seededAdmin();
        SysUser extraSuper = saveUser("guard_extra_super", "备用超管", role("SUPER_ADMIN"));

        // 系统已有两个启用超管：禁用其中一个是允许的（防锁死不误伤）
        UserForm disable = updateForm(extraSuper, Set.of(role("SUPER_ADMIN").getId()), false);

        assertThatCode(() -> userService.update(extraSuper.getId(), disable, superOperator.getUsername()))
                .doesNotThrowAnyException();

        SysUser reloaded = userRepository.findById(extraSuper.getId()).orElseThrow();
        assertThat(reloaded.isEnabled()).isFalse();
    }

    // =====================================================================
    //  helpers
    // =====================================================================

    private SysRole role(String code) {
        return roleRepository.findByCode(code).orElseThrow(() -> new IllegalStateException("角色未初始化: " + code));
    }

    /** 直存用户（绕开 UserService.create 守卫），角色按 code 传入；enabled=true、mustChangePassword=false */
    private SysUser saveUser(String username, String fullName, SysRole... roles) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode("Test@12345"));
        u.setFullName(fullName);
        u.setEnabled(true);
        u.setMustChangePassword(false);
        u.setRoles(new LinkedHashSet<>(Set.of(roles)));
        return userRepository.saveAndFlush(u);
    }

    /** DataInitializer 播种的系统唯一启用超管（username=admin） */
    private SysUser seededAdmin() {
        return userRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("测试基线 admin 超管未播种"));
    }

    private UserForm updateForm(SysUser target, Set<Long> roleIds, boolean enabled) {
        UserForm form = new UserForm();
        form.setUsername(target.getUsername());
        form.setFullName(target.getFullName());
        form.setEnabled(enabled);
        form.setRoleIds(roleIds);
        return form;
    }

    private boolean isSuperAdmin(SysUser user) {
        return user.getRoles().stream()
                .anyMatch(r -> SysRole.CODE_SUPER_ADMIN.equals(r.getCode()));
    }
}
