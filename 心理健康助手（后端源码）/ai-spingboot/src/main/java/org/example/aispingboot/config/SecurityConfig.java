package org.example.aispingboot.config;

import cn.hutool.core.text.AntPathMatcher;
import lombok.extern.slf4j.Slf4j;
import org.example.aispingboot.util.JwtAuthticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private static final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private static final String[] PUBLIC_PATHS = {
            "/",
            "/api/test",
            // 用户登录/注册
            "/api/user/login",
            "/api/user/add",
            // 知识库前台浏览（仅 GET 公开，写操作由 @PreAuthorize 控制）
            "/api/knowledge/category/tree",
            "/api/knowledge/article/page",
            "/api/knowledge/article/*",
            // API 文档
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            // 静态资源 / 监控
            "/actuator/**",
            "/uploads/**"
    };

    public static Boolean isPublicPATH(String requestUri) {
        for (String publicPath : PUBLIC_PATHS) {
            if (antPathMatcher.match(publicPath, requestUri)) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public JwtAuthticationFilter jwtAuthticationFilter() {
        return new JwtAuthticationFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
