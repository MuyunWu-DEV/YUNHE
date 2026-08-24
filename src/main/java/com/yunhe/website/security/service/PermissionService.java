package com.yunhe.website.security.service;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.security.entity.SysPermission;
import com.yunhe.website.security.entity.SysRole;
import com.yunhe.website.security.dto.PermissionDto;
import com.yunhe.website.security.dto.request.PermissionForm;
import com.yunhe.website.security.mapper.PermissionMapper;
import com.yunhe.website.security.repository.SysPermissionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限管理服务。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SysPermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    /** 查询全部权限（按模块、排序值排序） */
    @Transactional(readOnly = true)
    public List<PermissionDto> list() {
        return permissionMapper.toDtoList(permissionRepository.findAllByOrderByModuleAscSortOrderAsc());
    }

    /** 按模块分组查询权限，便于展示 */
    @Transactional(readOnly = true)
    public Map<String, List<PermissionDto>> listGroupedByModule() {
        Map<String, List<PermissionDto>> grouped = new LinkedHashMap<>();
        for (PermissionDto dto : list()) {
            grouped.computeIfAbsent(dto.module(), k -> new ArrayList<>()).add(dto);
        }
        return grouped;
    }

    /** 查询单个权限 */
    @Transactional(readOnly = true)
    public PermissionDto getById(Long id) {
        SysPermission permission = permissionRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("权限", id));
        return permissionMapper.toDto(permission);
    }

    /** 创建权限 */
    @Transactional
    public void create(PermissionForm form) {
        if (permissionRepository.existsByCode(form.getCode())) {
            throw BusinessException.of("权限编码已存在");
        }
        SysPermission permission = new SysPermission();
        applyForm(permission, form);
        permissionRepository.save(permission);
    }

    /** 更新权限 */
    @Transactional
    public void update(Long id, PermissionForm form) {
        SysPermission permission = permissionRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("权限", id));
        if (permissionRepository.existsByCodeAndIdNot(form.getCode(), id)) {
            throw BusinessException.of("权限编码已存在");
        }
        applyForm(permission, form);
    }

    /** 删除权限（同时从关联角色中移除） */
    @Transactional
    public void delete(Long id) {
        SysPermission permission = permissionRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("权限", id));
        // 权限是 role_permission 的反向端，需先解除与角色的关联
        List<SysRole> relatedRoles = new ArrayList<>(permission.getRoles());
        for (SysRole role : relatedRoles) {
            role.getPermissions().remove(permission);
        }
        permissionRepository.delete(permission);
    }

    private void applyForm(SysPermission permission, PermissionForm form) {
        permission.setCode(form.getCode());
        permission.setName(form.getName());
        permission.setNameZh(form.getNameZh());
        permission.setModule(form.getModule());
        permission.setResource(form.getResource());
        permission.setAction(form.getAction());
        permission.setDescription(form.getDescription());
        permission.setSortOrder(form.getSortOrder());
    }
}
