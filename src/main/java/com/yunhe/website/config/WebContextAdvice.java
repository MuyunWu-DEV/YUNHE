package com.yunhe.website.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Web 上下文增强：向所有模板注入当前请求 URI。
 * <p>Thymeleaf 3.1 已移除 {@code #request} 等默认 web 表达式对象，故通过
 * {@code @ModelAttribute} 手动注入，供侧边栏菜单按 URL 前缀高亮使用。</p>
 */
@ControllerAdvice
public class WebContextAdvice {

    /** 注入当前请求 URI（不含 context-path 的部分为 servletPath；当前项目无 context-path，二者一致） */
    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
