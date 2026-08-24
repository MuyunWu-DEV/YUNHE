package com.yunhe.website.security.auth;

import com.yunhe.website.security.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 登录成功处理器：记录登录审计（日志 + 数据库），然后跳转到仪表盘。
 */
@Slf4j
@Component
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;

    /** 是否信任 X-Forwarded-For 头（仅在可信反向代理后部署时才设为 true） */
    private final boolean trustForwardedFor;

    public LoginSuccessHandler(UserService userService,
                               @Value("${app.security.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.userService = userService;
        this.trustForwardedFor = trustForwardedFor;
        setDefaultTargetUrl("/admin/dashboard");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        String username = authentication.getName();
        String ip = resolveClientIp(request);
        log.info("登录成功：username={}, ip={}", username, ip);
        try {
            userService.recordLogin(username, ip);
        } catch (Exception e) {
            log.warn("记录登录审计信息失败: {}", e.getMessage());
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private String resolveClientIp(HttpServletRequest request) {
        // 仅在显式配置为可信反向代理时才解析 X-Forwarded-For，避免审计 IP 被伪造
        if (trustForwardedFor) {
            String xff = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(xff)) {
                return xff.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
