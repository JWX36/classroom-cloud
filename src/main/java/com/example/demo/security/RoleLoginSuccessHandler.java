package com.example.demo.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;

@Component
public class RoleLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final LoginRateLimiter loginRateLimiter;

    public RoleLoginSuccessHandler(LoginRateLimiter loginRateLimiter) {
        this.loginRateLimiter = loginRateLimiter;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        // 登录成功后清空该账号的失败计数
        loginRateLimiter.onSuccess(authentication.getName());
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String role = authorities.isEmpty() ? "" : authorities.iterator().next().getAuthority();
        String target = switch (role) {
            case "ROLE_ADMIN" -> "/admin";
            case "ROLE_TEACHER" -> "/teacher/courses";
            default -> "/student/courses";
        };
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
