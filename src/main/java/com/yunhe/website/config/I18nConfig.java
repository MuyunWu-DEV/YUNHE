package com.yunhe.website.config;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/**
 * 国际化配置：语言解析与切换。
 * <p>消息源由 Spring Boot 自动配置（basename 默认 {@code messages}、UTF-8），无需额外定义 MessageSource。</p>
 */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    /** 用 Cookie 记忆语言，默认中文 */
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
        return ms;
    }
}
