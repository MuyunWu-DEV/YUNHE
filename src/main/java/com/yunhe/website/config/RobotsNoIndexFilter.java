package com.yunhe.website.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 后台/内部页面的 SEO 隔离（纵深防御）。
 * <p>企业官网公开页面（首页、产品详情、About、References）允许搜索引擎抓取；
 * 其余页面——后台(/admin、/crm)、登录/登出、个人中心、错误页等——一律在响应头加
 * {@code X-Robots-Tag: noindex, nofollow}，明确告知爬虫不要收录。
 * 即便这些页面后续出现某段未鉴权即被渲染的情况，响应头仍能阻止其进入索引
 * （比逐页写 {@code <meta name="robots">} 覆盖更全、不易遗漏；官网侧另行全站开放）。</p>
 *
 * <p>判定采用「白名单反选」：凡不属于「可索引官网页」且不属于「静态资源」的请求
 * 都视为内部页并加 noindex。静态资源不处理（交给 robots.txt 管理），避免多余头。</p>
 *
 * <p>只对 GET/HEAD 处理；登录表单提交等非 GET 无索引意义，仍统一加头亦无害。</p>
 */
@Component
public class RobotsNoIndexFilter extends OncePerRequestFilter {

    /** 官网公开可索引路径前缀（与 SecurityConfig permitAll 的官网部分对齐；/site 模板目录非 URL，不列入） */
    private static final List<String> INDEXABLE_PREFIXES = List.of(
            "/products/",
            "/about",
            "/references"
    );

    /** 静态资源：不加 noindex（由 robots.txt 管理抓取） */
    private static final List<String> STATIC_PREFIXES = List.of(
            "/css/", "/js/", "/images/", "/webjars/", "/favicon.ico",
            "/robots.txt", "/sitemap.xml"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // 精确首页 "/" 是可索引官网页 → 本过滤器跳过（不加 noindex）
        if ("/".equals(uri)) {
            return true;
        }
        for (String p : INDEXABLE_PREFIXES) {
            if (uri.startsWith(p)) {
                return true; // 官网公开页 → 不加 noindex
            }
        }
        // 静态资源 → 跳过（不处理，交给 robots.txt）
        for (String p : STATIC_PREFIXES) {
            if (uri.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        filterChain.doFilter(request, response);
    }
}
