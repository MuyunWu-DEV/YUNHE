package com.yunhe.website.site;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 官网默认语言收敛到英文（SEO 关键）。
 * <p>站点级 LocaleResolver 默认中文（为后台服务）；但企业官网面向海外 Google 获客，需要
 * 「无语言偏好（既无 {@code ?lang} 参数也无 {@code lang} Cookie）的首次访问 / 爬虫拿到英文权威页」。
 * 本拦截器仅作用于官网公开路径：当 GET 请求无任何语言偏好时，302 到同一路径加 {@code ?lang=en}，
 * 使搜索引擎收录英文（无参 canonical 权威版本），而非落回默认中文版。
 * 有偏好（带 lang 参数或已有 Cookie）的访问不干预，语言切换不受影响。</p>
 */
@Component
public class SiteDefaultLangInterceptor implements HandlerInterceptor {

    private static final String LANG_PARAM = "lang";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 已有语言偏好 → 放行，不干预
        if (request.getParameter(LANG_PARAM) != null || hasLangCookie(request)) {
            return true;
        }
        String uri = request.getRequestURI();
        if ("/".equals(uri)) {
            response.sendRedirect("/?lang=en");
        } else {
            response.sendRedirect(uri + "?lang=en");
        }
        return false;
    }

    private boolean hasLangCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if (LANG_PARAM.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }
}
