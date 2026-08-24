package com.yunhe.website.security.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;

/**
 * 用户创建 / 编辑表单 DTO。
 * <p>创建时密码必填，编辑时密码留空表示不修改（由服务层校验）。</p>
 */
@Data
public class UserForm {

    /** 主键（编辑时非空，创建时为空） */
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3~50 之间")
    private String username;

    @Size(min = 6, max = 64, message = "密码长度需在 6~64 之间")
    private String password;

    @Size(max = 50, message = "姓名不能超过 50 个字符")
    private String fullName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过 100 个字符")
    private String email;

    @Size(max = 20, message = "手机号不能超过 20 个字符")
    private String phone;

    private boolean enabled = true;

    private boolean accountLocked = false;

    /** 分配的角色 ID 集合 */
    private Set<Long> roleIds = new HashSet<>();
}
