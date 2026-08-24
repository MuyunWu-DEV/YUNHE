package com.yunhe.website.security.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 权限创建 / 编辑表单 DTO。
 */
@Data
public class PermissionForm {

    /** 主键（编辑时非空，创建时为空） */
    private Long id;

    @NotBlank(message = "权限编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9:_-]{1,99}$", message = "权限编码需为小写字母/数字/冒号/中划线/下划线，且以字母开头")
    @Size(max = 100, message = "权限编码不能超过 100 个字符")
    private String code;

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 100, message = "权限名称不能超过 100 个字符")
    private String name;

    @NotBlank(message = "中文名称不能为空")
    @Size(max = 100, message = "中文名称不能超过 100 个字符")
    private String nameZh;

    @NotBlank(message = "所属模块不能为空")
    @Size(max = 50, message = "所属模块不能超过 50 个字符")
    private String module;

    @Size(max = 200, message = "资源标识不能超过 200 个字符")
    private String resource;

    @Size(max = 50, message = "动作标识不能超过 50 个字符")
    private String action;

    @Size(max = 255, message = "描述不能超过 255 个字符")
    private String description;

    private Integer sortOrder;
}
