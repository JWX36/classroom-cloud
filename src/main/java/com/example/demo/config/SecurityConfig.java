package com.example.demo.config;

import com.example.demo.security.CustomUserDetailsService;
import com.example.demo.security.LoginRateLimitFilter;
import com.example.demo.security.LoginRateLimiter;
import com.example.demo.security.RateLimitLoginFailureHandler;
import com.example.demo.security.RoleLoginSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           CustomUserDetailsService userDetailsService,
                                           RoleLoginSuccessHandler successHandler,
                                           RateLimitLoginFailureHandler failureHandler,
                                           LoginRateLimiter loginRateLimiter) throws Exception {
        LoginRateLimitFilter rateLimitFilter = new LoginRateLimitFilter(loginRateLimiter);
        http
                .userDetailsService(userDetailsService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/register/**",
                                "/css/**", "/js/**", "/img/**", "/error", "/api/health").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/teacher/**").hasRole("TEACHER")
                        .requestMatchers("/student/**").hasRole("STUDENT")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("code")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .csrf(csrf -> {
                    // 关闭"延迟令牌"：每个请求立即加载/保存 CSRF 令牌。
                    // 否则渲染页面时生成的令牌不会落进会话，导致间歇性 Invalid CSRF token（403）。
                    XorCsrfTokenRequestAttributeHandler requestHandler = new XorCsrfTokenRequestAttributeHandler();
                    requestHandler.setCsrfRequestAttributeName(null);
                    csrf.csrfTokenRequestHandler(requestHandler);
                })
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
