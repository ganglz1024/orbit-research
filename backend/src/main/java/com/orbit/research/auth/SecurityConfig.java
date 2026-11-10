package com.orbit.research.auth;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean HttpSessionEventPublisher sessionEvents() { return new HttpSessionEventPublisher(); }
    private static void error(HttpServletRequest request, HttpServletResponse response, int status, String code, String message) throws java.io.IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"requestId\":\"" + request.getAttribute("requestId") + "\"}");
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/v1/health", "/api/v1/setup", "/api/v1/auth/csrf", "/api/v1/auth/login").permitAll().anyRequest().authenticated())
            .csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .requestCache(cache -> cache.disable())
            .sessionManagement(session -> session.maximumSessions(1).expiredSessionStrategy(event -> error(event.getRequest(), event.getResponse(), 401, "SESSION_EXPIRED", "登录已过期，请重新登录。")))
            .formLogin(login -> login.loginProcessingUrl("/api/v1/auth/login")
                .successHandler((req, res, auth) -> { res.setContentType("application/json;charset=UTF-8"); res.getWriter().write("{\"authenticated\":true}"); })
                .failureHandler((req, res, ex) -> error(req, res, 401, "LOGIN_FAILED", "账号或密码不正确。")))
            .logout(logout -> logout.logoutUrl("/api/v1/auth/logout").deleteCookies("ORBIT_SESSION")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req, res, ex) -> error(req, res, 401, "UNAUTHENTICATED", "请先登录。"))
                .accessDeniedHandler((req, res, ex) -> error(req, res, 403, "FORBIDDEN", "请重新获取 CSRF 令牌或检查访问权限。")))
            .build();
    }
}
