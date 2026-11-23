package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 登录请求限流：锁定中的账号直接拒绝，不再进行密码校验。 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginRateLimiter limiter;

    public LoginRateLimitFilter(LoginRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getRequestURI())) {
            String code = request.getParameter("code");
            if (code != null && limiter.isLocked(code)) {
                response.sendRedirect("/login?locked");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
