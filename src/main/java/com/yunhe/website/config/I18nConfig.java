package com.yunhe.website.config;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import com.yunhe.website.site.SiteDefaultLangInterceptor;

/**
 * 国际化配置：语言解析与切换。
 * <p>消息源由 Spring Boot 自动配置（basename 默认 {@code messages}、UTF-8），无需额外定义 MessageSource。</p>
 */
@Configuration
@RequiredArgsConstructor
public class I18nConfig implements WebMvcConfigurer {

    private final SiteDefaultLangInterceptor siteDefaultLangInterceptor;

    /** 用 Cookie 记忆语言，默认中文（后台为中文界面；官网默认英文由 SiteDefaultLangInterceptor 单独收敛） */
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("lang");
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        return resolver;
    }

    /** 通过 {@code ?lang=} 参数切换语言 */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
        // 官网默认英文收敛（无偏好访问重定向到 ?lang=en，保证 SEO 收英文权威页）
        registry.addInterceptor(siteDefaultLangInterceptor)
                .addPathPatterns("/", "/products/**", "/about/**", "/references");
    }

    /**
     * 合并站点独立国际化文件（{@code messages_site} / {@code messages_site_en}），
     * 与后台 {@code messages} 共存，互不污染。
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasenames("classpath:messages", "classpath:messages_site");
        ms.setDefaultEncoding("UTF-8");
        ms.setCacheSeconds(3600);
        // 关键：禁止回退到"系统默认 locale"。默认 fallbackToSystemLocale=true，
        // 在英文系统(如服务器 en_US Linux)上，zh_CN 找不到专属 bundle 时会回退到 en，
        // 导致中文官网在线上渲染成英文。关闭后 zh_CN 正确回退到 messages_site_zh(中文)。
        ms.setFallbackToSystemLocale(false);
        return ms;
    }
}
