package com.yunhe.website.site;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 企业官网（前台）控制器。
 * <p>公开可访问，不依赖后台登录。默认展示英文，便于面向海外 Google 搜索获客；
 * 通过 {@code ?lang=zh} / {@code ?lang=en} 或 Cookie 切换语言。</p>
 */
@Controller
public class SiteController {

    /** 首页：横长 Hero 轮播 + 机型大卡（特斯拉式）企业官网 */
    @GetMapping("/")
    public Object home(HttpServletRequest request, Model model) {
        // 公开官网默认英文：既无 lang 参数也无 lang Cookie 时，重定向到 ?lang=en
        if (request.getParameter("lang") == null && !hasLangCookie(request)) {
            return new RedirectView("/?lang=en");
        }
        model.addAttribute("pageTitle", "QINGDAO YUNHE · Water Jet Loom Manufacturer");
        return "site/home";
    }

    private boolean hasLangCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if ("lang".equals(c.getName())) {
                return true;
            }
        }
        return false;
    }
}
