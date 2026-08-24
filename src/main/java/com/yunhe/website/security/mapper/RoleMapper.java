package com.yunhe.website.security.mapper;

import com.yunhe.website.security.entity.SysPermission;
import com.yunhe.website.security.entity.SysRole;
import com.yunhe.website.security.dto.RoleDto;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * 角色实体与 DTO 之间的映射器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoleMapper {

    @Mapping(target = "userCount", source = "userCount")
    @Mapping(target = "permissionIds", source = "role.permissions", qualifiedByName = "mapPermissionIds")
    @Mapping(target = "permissionCodes", source = "role.permissions", qualifiedByName = "mapPermissionCodes")
    RoleDto toDto(SysRole role, long userCount);

    @Named("mapPermissionIds")
    default Set<Long> mapPermissionIds(Set<SysPermission> permissions) {
        if (permissions == null) {
            return Set.of();
        }
        return permissions.stream()
                .map(SysPermission::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Named("mapPermissionCodes")
    default Set<String> mapPermissionCodes(Set<SysPermission> permissions) {
        if (permissions == null) {
            return Set.of();
        }
        return permissions.stream()
                .map(SysPermission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
