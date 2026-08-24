package com.yunhe.website.security.controller;

import com.yunhe.website.security.auth.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 认证相关页面控制器。
 * <p>登录认证逻辑由 Spring Security 表单登录处理，本控制器仅负责渲染登录页；
 * 已登录用户访问登录页时直接跳转到仪表盘。</p>
 */
@Controller
public class AuthController {

    @GetMapping("/login")
    public String loginPage(@AuthenticationPrincipal CustomUserDetails user) {
        if (user != null) {
            return "redirect:/admin/dashboard";
        }
        return "login";
    }
}
