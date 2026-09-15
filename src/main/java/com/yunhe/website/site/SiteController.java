package com.yunhe.website.site;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 企业官网（前台）控制器。
 * <p>公开可访问，不依赖后台登录。默认展示英文，便于面向海外 Google 搜索获客；
 * 通过 {@code ?lang=zh} / {@code ?lang=en} 或 Cookie 切换语言。</p>
 */
@Controller
@RequiredArgsConstructor
public class SiteController {

    private final MessageSource messageSource;

    /** JSON-LD 序列化（Product 结构化数据用，保证字段值正确转义） */
    private final ObjectMapper objectMapper;

    /** 支持的机型 ID（白名单；新增机型需同步扩 i18n 与模板） */
    private static final List<String> VALID_MODELS = List.of("yh608", "yh822", "yh9100");

    /** 详情页特性图标（6 项，与模板 th:each 顺序一一对应；与机型无关的固定列表） */
    private static final List<String> FEATURE_ICONS = List.of("⚡", "🔧", "📏", "💡", "🛡️", "🌍");

    /** 生产标准域名（canonical / hreflang / OG 的绝对前缀；不含结尾斜杠） */
    private static final String SITE_BASE = "https://www.cnyunhe.ltd";

    /** 全站默认 Open Graph 分享图（各页可在 handler 里以 seoOgImage 覆盖） */
    private static final String DEFAULT_OG_IMAGE = SITE_BASE + "/images/hero_01.jpg";

    /**
     * 为官网每个渲染请求注入 SEO 上下文（canonical / hreflang / OG）。
     * <p>语言 URL 策略：英文为权威（无参 URL），中文版为同一路径加 {@code ?lang=zh}。
     * canonical 自指当前语言版本；en 与 zh 经 hreflang 声明交替，x-default 指向英文。
     * 基于请求 {@code requestURI}（不含 query）推导，避免引入 ?lang 重复内容。</p>
     */
    @ModelAttribute
    public void addSiteSeo(Model model, HttpServletRequest request) {
        String path = request.getRequestURI(); // 如 "/"、"/products/yh608"（不含 query）
        Locale loc = LocaleContextHolder.getLocale();
        boolean zh = loc != null && "zh".equalsIgnoreCase(loc.getLanguage());
        String enUrl = SITE_BASE + path;
        String zhUrl = SITE_BASE + path + "?lang=zh";
        model.addAttribute("seoEnUrl", enUrl);
        model.addAttribute("seoZhUrl", zhUrl);
        model.addAttribute("seoCanonicalUrl", zh ? zhUrl : enUrl);
        model.addAttribute("seoOgImage", DEFAULT_OG_IMAGE);
        // 当前路径（不含 query）：语言切换链接据此保留所在页面，仅替换 lang 参数
        model.addAttribute("currentPath", request.getRequestURI());
    }

    /** 按当前 locale 解析站内 SEO description 消息 key */
    private String desc(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    /**
     * 生成产品页 Product JSON-LD（序列化后的 JSON 字符串，模板原样输出）。
     * <p>B2B 无公开价格，不声明 offers；brand/manufacturer 指向公司主体，
     * name/description 按当前语言取 i18n，便于 Google 以访客语言理解机型。</p>
     */
    private String buildProductJsonLd(String prefix, String imageUrl) {
        try {
            Map<String, Object> org = new LinkedHashMap<>();
            org.put("@type", "Organization");
            org.put("name", "Qingdao Yunhe Intelligent Manufacturing Co., Ltd.");
            org.put("url", SITE_BASE + "/");
            Map<String, Object> brand = new LinkedHashMap<>();
            brand.put("@type", "Brand");
            brand.put("name", "QINGDAO YUNHE");
            Map<String, Object> product = new LinkedHashMap<>();
            product.put("@context", "https://schema.org");
            product.put("@type", "Product");
            product.put("name", desc(prefix + "name"));
            product.put("description", desc(prefix + "tagline"));
            product.put("image", imageUrl);
            product.put("brand", brand);
            product.put("manufacturer", org);
            return objectMapper.writeValueAsString(product);
        } catch (Exception e) {
            // 结构化数据失败不应影响页面渲染
            return null;
        }
    }

    /** 首页：横长 Hero 轮播 + 机型大卡（特斯拉式）企业官网 */
    @GetMapping("/")
    public String home(Model model) {
        // 无语言偏好的访问已由 SiteDefaultLangInterceptor 重定向到 ?lang=en（保证 SEO 收英文权威页）
        model.addAttribute("pageTitle", "QINGDAO YUNHE · Water Jet Loom Manufacturer");
        model.addAttribute("pageDesc", desc("site.meta.desc.home"));
        // 首屏 Hero 第一屏背景图（CSS .hero-slide--a），preload 改善 LCP
        model.addAttribute("lcpImage", SITE_BASE + "/images/hero_01.jpg");
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
        model.addAttribute("pageDesc", desc("site.meta.desc.product." + modelId));
        // 产品页分享图用对应机型实拍（覆盖 @ModelAttribute 默认 hero 图）。
        // 物理文件名不含 yh 前缀：yh608→/images/608.jpg（与 static/images 实际文件对齐）
        String modelImage = SITE_BASE + "/images/" + modelId.replaceFirst("^yh", "") + ".jpg";
        String modelImagePath = "/images/" + modelId.replaceFirst("^yh", "") + ".jpg";
        model.addAttribute("seoOgImage", modelImage);
        // 首屏大图 preload（LCP），模板 img 同源同 URL
        model.addAttribute("lcpImage", modelImage);
        model.addAttribute("modelImage", modelImagePath);
        // Product 结构化数据（JSON-LD，按当前语言输出机型名与描述）
        model.addAttribute("productJsonLd", buildProductJsonLd(prefix, modelImage));
        return "site/product";
    }

    /** About · 集团概览：{@code /about}（About megamenu「Group Overview」落点） */
    @GetMapping("/about")
    public String aboutGroup(Model model) {
        model.addAttribute("pageTitle", "About · QINGDAO YUNHE");
        model.addAttribute("pageDesc", desc("site.meta.desc.about"));
        return "site/about";
    }

    /** About · 我们的价值观：{@code /about/values} */
    @GetMapping("/about/values")
    public String aboutValues(Model model) {
        model.addAttribute("pageTitle", "Our Values · QINGDAO YUNHE");
        model.addAttribute("pageDesc", desc("site.meta.desc.values"));
        return "site/about-values";
    }

    /** About · 联系我们：{@code /about/contact} */
    @GetMapping("/about/contact")
    public String aboutContact(Model model) {
        model.addAttribute("pageTitle", "Contact · QINGDAO YUNHE");
        model.addAttribute("pageDesc", desc("site.meta.desc.contact"));
        return "site/about-contact";
    }

    /** References · 客户案例：{@code /references} */
    @GetMapping("/references")
    public String references(Model model) {
        model.addAttribute("pageTitle", "References · QINGDAO YUNHE");
        model.addAttribute("pageDesc", desc("site.meta.desc.references"));
        return "site/references";
    }
}