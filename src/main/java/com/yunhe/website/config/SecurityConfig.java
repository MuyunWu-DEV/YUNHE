package com.yunhe.website.config;

import com.yunhe.website.security.auth.AccountStatusFilter;
import com.yunhe.website.security.auth.LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Spring Security 配置。
 * <p>采用「Session 表单登录 + 方法级授权」模式，与 Thymeleaf 服务端渲染配合，便于按钮级权限控制。</p>
 * <p>注意：将来对外开放企业官网（SEO 页面）时，将对应公开路径加入
 * {@code requestMatchers(...).permitAll()} 即可。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   LoginSuccessHandler loginSuccessHandler,
                                                   AccountStatusFilter accountStatusFilter) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 静态资源与登录页、错误页允许匿名访问
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico", "/webjars/**").permitAll()
                        .requestMatchers("/", "/site/**", "/products/**", "/about/**", "/references").permitAll()
                        .requestMatchers("/login", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(loginSuccessHandler)
                        .failureUrl("/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .exceptionHandling(ex -> ex.accessDeniedPage("/403"))
                // S1 会话授权实时失效：在授权决策前实时校验账号状态，禁用/锁定即踢出
                .addFilterBefore(accountStatusFilter, AuthorizationFilter.class);

        return http.build();
    }
}
