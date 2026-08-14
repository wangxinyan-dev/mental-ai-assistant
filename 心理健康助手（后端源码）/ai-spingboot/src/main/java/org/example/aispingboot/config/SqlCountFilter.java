package org.example.aispingboot.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;

/**
 * 请求结束时打印 SQL 执行统计
 * 仅在 dev 环境生效
 */
@Configuration
@Profile("dev")
public class SqlCountFilter {

    @Bean
    public FilterRegistrationBean<Filter> sqlCountFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                try {
                    chain.doFilter(request, response);
                } finally {
                    HttpServletRequest req = (HttpServletRequest) request;
                    SqlCountInterceptor.logAndReport(req.getMethod(), req.getRequestURI());
                }
            }
        });
        registration.addUrlPatterns("/*");
        registration.setName("sqlCountFilter");
        registration.setOrder(Integer.MAX_VALUE);
        return registration;
    }
}
