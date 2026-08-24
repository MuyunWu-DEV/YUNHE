package com.yunhe.website.security.mapper;

import com.yunhe.website.security.entity.SysPermission;
import com.yunhe.website.security.dto.PermissionDto;
import java.util.Collection;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 权限实体与 DTO 之间的映射器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionMapper {

    PermissionDto toDto(SysPermission permission);

    List<PermissionDto> toDtoList(Collection<SysPermission> permissions);
}
