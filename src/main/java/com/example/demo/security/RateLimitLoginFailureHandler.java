package com.example.demo.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 登录失败时累计失败次数，再按原行为跳回 /login?error。 */
@Component
public class RateLimitLoginFailureHandler implements AuthenticationFailureHandler {

    private final LoginRateLimiter limiter;

    public RateLimitLoginFailureHandler(LoginRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String code = request.getParameter("code");
        if (code != null) {
            limiter.onFailure(code);
        }
        response.sendRedirect("/login?error");
    }
}
