package com.yunhe.website.security.mapper;

import com.yunhe.website.security.entity.SysRole;
import com.yunhe.website.security.entity.SysUser;
import com.yunhe.website.security.dto.UserDto;
import java.util.Collection;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 用户实体与 DTO 之间的映射器（MapStruct，Spring 组件模型）。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserDto toDto(SysUser user);

    /** 角色 → 用户 DTO 中的角色摘要 */
    UserDto.RoleSummary toRoleSummary(SysRole role);

    List<UserDto> toDtoList(Collection<SysUser> users);
}
