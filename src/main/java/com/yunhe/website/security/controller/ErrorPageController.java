package com.yunhe.website.security.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 错误页面控制器。
 * <p>403 页面需配合 Spring Security 的 accessDeniedPage 显式映射；
 * 404 由 Spring Boot 默认错误处理自动渲染 error/404.html，无需手动映射。</p>
 */
@Controller
public class ErrorPageController {

    @GetMapping("/403")
    public String forbidden() {
        return "error/403";
    }
}
