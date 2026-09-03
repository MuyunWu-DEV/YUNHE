package com.yunhe.website.security.auth;

import com.yunhe.website.security.entity.SysUser;
import com.yunhe.website.security.repository.SysUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 会话授权实时失效 + 首次登录强制改密（S1 / S3）。
 *
 * <p>Spring Security 默认把登录时构建的 {@link CustomUserDetails}（含 enabled / accountLocked /
 * mustChangePassword 的当时快照）缓存进会话，管理员在后台改了账号状态后旧会话不会自行更新。
 * 本过滤器在「每个已认证请求」实时校验 DB 中的最新状态：</p>
 * <ul>
 *   <li>已禁用 / 已锁定 / 账号已被删除 → 使会话失效并回登录页（S1 禁用/锁定立即踢下线）；</li>
 *   <li>账号正常但 {@code mustChangePassword=true} → 强制跳转改密页，改密前阻断其它受保护页（S3 机制落地，
 *       此前 mustChangePassword 置位后无人消费、机制空转）。</li>
 * </ul>
 * <p>范围：角色 / 权限收缩仍以重新登录重建 authorities 生效；本过滤器专注「禁用 / 锁定踢出」与
 * 「强制改密」两类实时性诉求。</p>
 */
@Component
@RequiredArgsConstructor
public class AccountStatusFilter extends OncePerRequestFilter {

    /** 改密相关路径：首次改密用户也必须能访问以完成改密，故放行 */
    private static final String PASSWORD_CHANGE_PATH = "/admin/profile/password";

    private final SysUserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof CustomUserDetails userDetails
                && !isSkippable(request)) {
            String path = pathOf(request);
            // 实时查库拿最新状态（roles 为 LAZY，本处只读标量列，不会触发 N+1）
            SysUser user = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
            boolean deleted = user == null;
            boolean disabled = !deleted && !user.isEnabled();
            boolean locked = !deleted && user.isAccountLocked();
            // S1：禁用 / 锁定 / 账号被删 → 使会话失效并回登录页
            if (deleted || disabled || locked) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                response.sendRedirect(request.getContextPath() + "/login?error");
                return;
            }
            // S3：须改密 → 除改密页本身外一律强制跳改密页，完成改密前阻断其它受保护页
            if (user.isMustChangePassword() && !PASSWORD_CHANGE_PATH.equals(path)) {
                response.sendRedirect(request.getContextPath() + PASSWORD_CHANGE_PATH);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** 认证流转 / 静态资源请求不校验，避免踢出死循环 */
    private boolean isSkippable(HttpServletRequest request) {
        String path = pathOf(request);
        if (path == null) {
            return false;
        }
        if ("/login".equals(path) || "/logout".equals(path) || "/error".equals(path)) {
            return true;
        }
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/webjars/") || "/favicon.ico".equals(path);
    }

    private String pathOf(HttpServletRequest request) {
        String ctx = request.getContextPath();
        String uri = request.getRequestURI();
        return (uri != null && ctx != null && uri.startsWith(ctx))
                ? uri.substring(ctx.length())
                : uri;
    }
}
