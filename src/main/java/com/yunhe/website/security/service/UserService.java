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
    public void create(UserForm form) {
        if (userRepository.existsByUsername(form.getUsername())) {
            throw BusinessException.of("用户名已存在");
        }
        if (!StringUtils.hasText(form.getPassword())) {
            throw BusinessException.of("创建用户时密码不能为空");
        }
        SysUser user = new SysUser();
        applyForm(user, form);
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setRoles(resolveRoles(form.getRoleIds()));
        userRepository.save(user);
    }

    /** 更新用户 */
    @Transactional
    public void update(Long id, UserForm form) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("用户", id));
        if (userRepository.existsByUsernameAndIdNot(form.getUsername(), id)) {
            throw BusinessException.of("用户名已存在");
        }
        applyForm(user, form);
        if (StringUtils.hasText(form.getPassword())) {
            user.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        user.setRoles(resolveRoles(form.getRoleIds()));
    }

    /** 删除用户（禁止删除自己或超级管理员） */
    @Transactional
    public void delete(Long id, String currentUsername) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("用户", id));
        if (currentUsername.equals(user.getUsername())) {
            throw BusinessException.of("不能删除当前登录用户");
        }
        boolean isSuperAdmin = user.getRoles().stream()
                .anyMatch(r -> SysRole.CODE_SUPER_ADMIN.equals(r.getCode()));
        if (isSuperAdmin) {
            throw BusinessException.of("超级管理员账号不可删除");
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
}
