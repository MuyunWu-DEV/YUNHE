package com.yunhe.website.security.service;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.security.entity.SysRole;
import com.yunhe.website.security.entity.SysUser;
import com.yunhe.website.security.dto.UserDto;
import com.yunhe.website.security.dto.request.ChangePasswordForm;
import com.yunhe.website.security.dto.request.UserForm;
import com.yunhe.website.security.mapper.UserMapper;
import com.yunhe.website.security.repository.SysRoleRepository;
import com.yunhe.website.security.repository.SysUserRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户管理服务。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /** 分页查询用户 */
    @Transactional(readOnly = true)
    public Page<UserDto> list(String keyword, Pageable pageable) {
        Page<SysUser> page = StringUtils.hasText(keyword)
                ? userRepository.searchByKeyword(keyword, pageable)
                : userRepository.findAll(pageable);
        return page.map(userMapper::toDto);
    }

    /** 查询单个用户 */
    @Transactional(readOnly = true)
    public UserDto getById(Long id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("用户", id));
        return userMapper.toDto(user);
    }

    /** 创建用户 */
    @Transactional
    public void create(UserForm form, String currentUsername) {
        if (userRepository.existsByUsername(form.getUsername())) {
            throw BusinessException.of("用户名已存在");
        }
        if (!StringUtils.hasText(form.getPassword())) {
            throw BusinessException.of("创建用户时密码不能为空");
        }
        Set<SysRole> targetRoles = resolveRoles(form.getRoleIds());
        boolean grantSuper = targetRoles.stream()
                .anyMatch(r -> SysRole.CODE_SUPER_ADMIN.equals(r.getCode()));
        // S2 提权保护：仅超级管理员可创建持有 SUPER_ADMIN 角色的账号
        if (grantSuper && !currentUserIsSuperAdmin(currentUsername)) {
            throw BusinessException.of("仅超级管理员可分配超级管理员角色");
        }
        SysUser user = new SysUser();
        applyForm(user, form);
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        // S3 密码治理：新用户首次登录须强制改密（机制此前空转，建号即置位）
        user.setMustChangePassword(true);
        user.setRoles(targetRoles);
        userRepository.save(user);
    }

    /** 更新用户 */
    @Transactional
    public void update(Long id, UserForm form, String currentUsername) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("用户", id));
        if (userRepository.existsByUsernameAndIdNot(form.getUsername(), id)) {
            throw BusinessException.of("用户名已存在");
        }
        boolean currentIsSuper = currentUserIsSuperAdmin(currentUsername);
        boolean targetIsSuper = isSuperAdminUser(user);
        // S2 提权保护：非超管不可触碰超管账号
        if (targetIsSuper && !currentIsSuper) {
            throw BusinessException.of("仅超级管理员可修改超级管理员账号");
        }
        Set<SysRole> newRoles = resolveRoles(form.getRoleIds());
        boolean newTargetIsSuper = newRoles.stream()
                .anyMatch(r -> SysRole.CODE_SUPER_ADMIN.equals(r.getCode()));
        // S2 提权保护：非超管不能把普通账号提升为超管
        if (!targetIsSuper && newTargetIsSuper && !currentIsSuper) {
            throw BusinessException.of("仅超级管理员可分配超级管理员角色");
        }
        // S2 防锁死：目标为「启用中的超管」且本次操作会使其失去超管角色或被禁用时，
        // 必须保证系统中仍存在至少一个启用的超管，避免把系统锁死没有管理入口。
        boolean targetIsActiveSuper = targetIsSuper && user.isEnabled() && !user.isAccountLocked();
        if (targetIsActiveSuper && (!newTargetIsSuper || !form.isEnabled())) {
            if (enabledSuperAdminCount() <= 1) {
                throw BusinessException.of("系统必须保留至少一个启用的超级管理员");
            }
        }
        applyForm(user, form);
        if (StringUtils.hasText(form.getPassword())) {
            user.setPassword(passwordEncoder.encode(form.getPassword()));
            // 管理员重置他人密码后，要求对方下次登录强制改密
            user.setMustChangePassword(true);
        }
        user.setRoles(newRoles);
    }

    /** 删除用户（禁止删除自己或超级管理员，超管账号仅超管可删并受防锁死约束） */
    @Transactional
    public void delete(Long id, String currentUsername) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("用户", id));
        if (currentUsername.equals(user.getUsername())) {
            throw BusinessException.of("不能删除当前登录用户");
        }
        boolean isSuperAdmin = isSuperAdminUser(user);
        if (isSuperAdmin) {
            // S2 提权保护：超管账号仅超管可删除
            if (!currentUserIsSuperAdmin(currentUsername)) {
                throw BusinessException.of("仅超级管理员可删除超级管理员账号");
            }
            // S2 防锁死：不能删除最后一个启用的超管
            if (user.isEnabled() && !user.isAccountLocked() && enabledSuperAdminCount() <= 1) {
                throw BusinessException.of("系统必须保留至少一个启用的超级管理员");
            }
        }
        user.getRoles().clear();
        userRepository.delete(user);
    }

    /** 登录成功后记录最后登录时间与来源 IP（入库） */
    @Transactional
    public void recordLogin(String username, String ip) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            user.setLastLoginIp(ip);
        });
    }

    /** 修改当前用户密码 */
    @Transactional
    public void changePassword(String username, ChangePasswordForm form) {
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw BusinessException.of("两次输入的新密码不一致");
        }
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> BusinessException.of("用户不存在：" + username));
        if (!passwordEncoder.matches(form.getOldPassword(), user.getPassword())) {
            throw BusinessException.of("原密码不正确");
        }
        user.setPassword(passwordEncoder.encode(form.getNewPassword()));
        user.setMustChangePassword(false);
    }

    private void applyForm(SysUser user, UserForm form) {
        user.setUsername(form.getUsername());
        user.setFullName(form.getFullName());
        user.setEmail(form.getEmail());
        user.setPhone(form.getPhone());
        user.setEnabled(form.isEnabled());
        user.setAccountLocked(form.isAccountLocked());
    }

    private Set<SysRole> resolveRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(roleRepository.findAllById(roleIds));
    }

    /** 目标用户是否持有超级管理员角色 */
    private boolean isSuperAdminUser(SysUser user) {
        return user.getRoles().stream()
                .anyMatch(r -> SysRole.CODE_SUPER_ADMIN.equals(r.getCode()));
    }

    /** 当前操作者（按登录名）是否为超级管理员 */
    private boolean currentUserIsSuperAdmin(String currentUsername) {
        return userRepository.findByUsername(currentUsername)
                .map(this::isSuperAdminUser)
                .orElse(false);
    }

    /** 启用中的超级管理员数量（防锁死守卫用） */
    private long enabledSuperAdminCount() {
        return userRepository.countByRoles_CodeAndEnabledTrue(SysRole.CODE_SUPER_ADMIN);
    }
}
