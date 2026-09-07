package com.yunhe.website.site;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;

/**
 * 企业官网（前台）控制器。
 * <p>公开可访问，不依赖后台登录。默认展示英文，便于面向海外 Google 搜索获客；
 * 通过 {@code ?lang=zh} / {@code ?lang=en} 或 Cookie 切换语言。</p>
 */
@Controller
public class SiteController {

    /** 支持的机型 ID（白名单；新增机型需同步扩 i18n 与模板） */
    private static final List<String> VALID_MODELS = List.of("yh608", "yh822", "yh9100");

    /** 详情页特性图标（6 项，与模板 th:each 顺序一一对应；与机型无关的固定列表） */
    private static final List<String> FEATURE_ICONS = List.of("⚡", "🔧", "📏", "💡", "🛡️", "🌍");

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

    /**
     * 产品详情页：{@code /products/{modelId}}。
     * <p>全部文案走 i18n（{@code site.product.{modelId}.*}），模板用 {@code #{${prefix + '...'}}}
     * 动态拼接查表；规格条/特性/参数表用 {@code th:each} 编号渲染，模型数据零硬编码在模板里。</p>
     */
    @GetMapping("/products/{modelId}")
    public String product(@PathVariable String modelId, Model model) {
        if (!VALID_MODELS.contains(modelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String prefix = "site.product." + modelId + ".";
        model.addAttribute("modelId", modelId);
        model.addAttribute("prefix", prefix);
        model.addAttribute("featureIcons", FEATURE_ICONS);
        model.addAttribute("pageTitle", "QINGDAO YUNHE · " + modelId.replace("-", "").toUpperCase());
        return "site/product";
    }

    /** About · 集团概览：{@code /about}（About megamenu「Group Overview」落点） */
    @GetMapping("/about")
    public String aboutGroup(Model model) {
        model.addAttribute("pageTitle", "About · QINGDAO YUNHE");
        return "site/about";
    }

    /** About · 我们的价值观：{@code /about/values} */
    @GetMapping("/about/values")
    public String aboutValues(Model model) {
        model.addAttribute("pageTitle", "Our Values · QINGDAO YUNHE");
        return "site/about-values";
    }

    /** About · 联系我们：{@code /about/contact} */
    @GetMapping("/about/contact")
    public String aboutContact(Model model) {
        model.addAttribute("pageTitle", "Contact · QINGDAO YUNHE");
        return "site/about-contact";
    }

    /** References · 客户案例：{@code /references} */
    @GetMapping("/references")
    public String references(Model model) {
        model.addAttribute("pageTitle", "References · QINGDAO YUNHE");
        return "site/references";
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