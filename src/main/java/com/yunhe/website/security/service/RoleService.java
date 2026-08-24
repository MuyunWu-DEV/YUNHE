package com.yunhe.website.security.service;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.security.entity.SysPermission;
import com.yunhe.website.security.entity.SysRole;
import com.yunhe.website.security.dto.RoleDto;
import com.yunhe.website.security.dto.request.RoleForm;
import com.yunhe.website.security.mapper.RoleMapper;
import com.yunhe.website.security.repository.SysPermissionRepository;
import com.yunhe.website.security.repository.SysRoleRepository;
import com.yunhe.website.security.repository.SysUserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色管理服务。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final SysUserRepository userRepository;
    private final RoleMapper roleMapper;

    /** 查询全部角色（含用户数，一次性 group by 统计，避免 N+1） */
    @Transactional(readOnly = true)
    public List<RoleDto> list() {
        Map<Long, Long> userCounts = userRepository.countUsersGroupByRole().stream()
                .collect(Collectors.toMap(r -> r.getId(), r -> r.getCount()));
        return roleRepository.findAll().stream()
                .map(role -> roleMapper.toDto(role, userCounts.getOrDefault(role.getId(), 0L)))
                .toList();
    }

    /** 查询单个角色 */
    @Transactional(readOnly = true)
    public RoleDto getById(Long id) {
        SysRole role = roleRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("角色", id));
        return roleMapper.toDto(role, userRepository.countByRolesId(id));
    }

    /** 创建角色 */
    @Transactional
    public void create(RoleForm form) {
        if (roleRepository.existsByCode(form.getCode())) {
            throw BusinessException.of("角色编码已存在");
        }
        SysRole role = new SysRole();
        applyForm(role, form);
        role.setPermissions(resolvePermissions(form.getPermissionIds()));
        roleRepository.save(role);
    }

    /** 更新角色 */
    @Transactional
    public void update(Long id, RoleForm form) {
        SysRole role = roleRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("角色", id));
        if (roleRepository.existsByCodeAndIdNot(form.getCode(), id)) {
            throw BusinessException.of("角色编码已存在");
        }
        if (role.isBuiltIn() && !role.getCode().equals(form.getCode())) {
            throw BusinessException.of("内置角色不允许修改编码");
        }
        applyForm(role, form);
        role.setPermissions(resolvePermissions(form.getPermissionIds()));
    }

    /** 删除角色（内置角色或仍被用户使用的角色不可删除） */
    @Transactional
    public void delete(Long id) {
        SysRole role = roleRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("角色", id));
        if (role.isBuiltIn()) {
            throw BusinessException.of("内置角色不可删除");
        }
        if (userRepository.countByRolesId(id) > 0) {
            throw BusinessException.of("该角色下仍存在用户，无法删除");
        }
        role.getPermissions().clear();
        roleRepository.delete(role);
    }

    private void applyForm(SysRole role, RoleForm form) {
        role.setCode(form.getCode());
        role.setName(form.getName());
        role.setNameZh(form.getNameZh());
        role.setDescription(form.getDescription());
        role.setEnabled(form.isEnabled());
        role.setSortOrder(form.getSortOrder());
    }

    private Set<SysPermission> resolvePermissions(Set<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(permissionRepository.findAllById(permissionIds));
    }
}
