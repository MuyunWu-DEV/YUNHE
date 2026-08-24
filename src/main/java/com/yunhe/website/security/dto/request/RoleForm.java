package com.yunhe.website.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;

/**
 * 角色创建 / 编辑表单 DTO。
 */
@Data
public class RoleForm {

    /** 主键（编辑时非空，创建时为空） */
    private Long id;

    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$", message = "角色编码需为大写字母/数字/下划线，且以字母开头")
    @Size(max = 50, message = "角色编码不能超过 50 个字符")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称不能超过 50 个字符")
    private String name;

    @NotBlank(message = "中文名称不能为空")
    @Size(max = 50, message = "中文名称不能超过 50 个字符")
    private String nameZh;

    @Size(max = 255, message = "描述不能超过 255 个字符")
    private String description;

    private boolean enabled = true;

    private Integer sortOrder;

    /** 分配的权限 ID 集合 */
    private Set<Long> permissionIds = new HashSet<>();
}
