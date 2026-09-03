package com.yunhe.website.security.auth;

import com.yunhe.website.security.entity.SysUser;
import com.yunhe.website.security.repository.SysUserRepository;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AccountStatusFilter（A·S1 会话实时失效 / A·S3 强制改密）集成测试。
 *
 * <p>直接驱动 {@link AccountStatusFilter#doFilter}：前置 SecurityContextHolder 放入一个
 * {@link CustomUserDetails} 主体验证 filter 行为。核心是证明 filter「每请求查库拿最新状态」而非
 * 信任登录快照——故各用例让 DB 状态与 principal 快照刻意不一致，断言 filter 依据 DB 判决。</p>
 *
 * <p>沿用 test/H2 + DataInitializer 基线，被踢/改密的用户均用独立造出的普通账号，不动基线 admin。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountStatusFilterTest {

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountStatusFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // =====================================================================
    //  A·S1：禁用 / 锁定 / 账号删除 → 会话失效并回登录页
    // =====================================================================

    @Test
    void disabledUserIsKickedToLoginEvenWhenPrincipalSnapshotStillEnabled() {
        // DB 用户已禁用，但 principal 快照 enabled=true（登录时未禁用）→ filter 须查库踢出
        SysUser disabled = saveUser("kick_disabled", false, false, false);
        principalWithSnapshot(usernameOf(disabled), /*enabled snapshot*/ true, false);

        MockHttpServletResponse resp = run("/crm/quotations");

        assertThat(resp.getRedirectedUrl()).isEqualTo("/login?error");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void lockedUserIsKicked() {
        // DB 用户已锁定，principal 快照 accountLocked=false（登录时未锁）→ filter 查库踢出
        SysUser locked = saveUser("kick_locked", true, false, true /*locked*/);
        principalWithSnapshot(usernameOf(locked), true, false);

        MockHttpServletResponse resp = run("/crm/quotations");

        assertThat(resp.getRedirectedUrl()).isEqualTo("/login?error");
    }

    @Test
    void deletedUserIsKicked() {
        // 只造 principal 快照、DB 无此用户 → 相当于账号已被删除
        principalWithSnapshot("kick_deleted", true, false);

        MockHttpServletResponse resp = run("/crm/quotations");

        assertThat(resp.getRedirectedUrl()).isEqualTo("/login?error");
    }

    // =====================================================================
    //  A·S3：mustChangePassword → 强制跳改密页；改密页本身放行
    // =====================================================================

    @Test
    void mustChangePasswordUserIsForcedToPasswordPage() {
        SysUser user = saveUser("kick_change", true, true /*mustChange*/, false);
        principalWithSnapshot(usernameOf(user), true, false);

        MockHttpServletResponse resp = run("/crm/quotations");

        assertThat(resp.getRedirectedUrl()).isEqualTo("/admin/profile/password");
    }

    @Test
    void passwordChangePageItselfIsAllowedThrough() {
        SysUser user = saveUser("kick_change2", true, true /*mustChange*/, false);
        principalWithSnapshot(usernameOf(user), true, false);

        // 访问改密页本身 → filter 不应拦截，放行给 chain
        MockHttpServletResponse resp = run("/admin/profile/password");

        assertThat(resp.getRedirectedUrl()).isNull();
        assertThat(chainCalled()).isTrue();
    }

    // =====================================================================
    //  正常账号不受干扰
    // =====================================================================

    @Test
    void healthyUserPassesThrough() {
        SysUser user = saveUser("kick_healthy", true, false, false);
        principalWithSnapshot(usernameOf(user), true, false);

        MockHttpServletResponse resp = run("/crm/quotations");

        assertThat(resp.getRedirectedUrl()).isNull();
        assertThat(chainCalled()).isTrue();
    }

    // =====================================================================
    //  helpers
    // =====================================================================

    /** 落库用户：enabled + mustChangePassword + accountLocked 可配 */
    private SysUser saveUser(String username, boolean enabled, boolean mustChange, boolean locked) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode("Test@12345"));
        u.setFullName("测试用户");
        u.setEnabled(enabled);
        u.setMustChangePassword(mustChange);
        u.setAccountLocked(locked);
        return userRepository.saveAndFlush(u);
    }

    private String usernameOf(SysUser u) {
        return u.getUsername();
    }

    /** 在 SecurityContextHolder 放一个指定快照状态的 CustomUserDetails 主体验证 filter */
    private void principalWithSnapshot(String username, boolean enabledSnapshot, boolean lockedSnapshot) {
        SysUser snapshotUser = new SysUser();
        snapshotUser.setUsername(username);
        snapshotUser.setEnabled(enabledSnapshot);
        snapshotUser.setAccountLocked(lockedSnapshot);
        CustomUserDetails principal = new CustomUserDetails(snapshotUser, List.of());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
    }

    /** 以指定路径驱动 filter，返回响应（chain 为 no-op 并记录是否被调用） */
    private MockHttpServletResponse run(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setContextPath("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filter.doFilter(request, response, (req, resp) -> chainCalled.set(true));
        } catch (Exception e) {
            throw new RuntimeException("filter.doFilter failed", e);
        }
        return response;
    }

    private final AtomicBoolean chainCalled = new AtomicBoolean(false);

    private boolean chainCalled() {
        return chainCalled.get();
    }
}
