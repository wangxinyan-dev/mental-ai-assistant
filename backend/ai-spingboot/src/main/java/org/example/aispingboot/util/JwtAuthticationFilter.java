package org.example.aispingboot.util;

import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.aispingboot.common.ResultCode;
import org.example.aispingboot.config.SecurityConfig;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import org.example.aispingboot.enumClass.UserStatus;
import org.example.aispingboot.service.UserStatusService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
public class JwtAuthticationFilter extends OncePerRequestFilter {

    @Resource
    private UserStatusService userStatusService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        log.debug("Request URI: {}", requestUri);

        if (SecurityConfig.isPublicPATH(requestUri)) {
            log.debug("Public path: {}", requestUri);
            AnonymousAuthenticationToken anonymousAuth = new AnonymousAuthenticationToken(
                    "anonymous", "anonymousUser",
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
            );
            SecurityContextHolder.getContext().setAuthentication(anonymousAuth);
            chain.doFilter(request, response);
            return;
        }

        String token = JwtTokenUtil.extractTokenFromRequest(request);
        if (StringUtils.hasText(token)) {
            try {
                JwtTokenUtil.TokenVerificationResult validationResult = JwtTokenUtil.validateToken(token);
                if (validationResult != null && validationResult.isValid()) {
                    Integer status = userStatusService.getUserStatus(validationResult.getUserId());
                    log.debug("Authenticated user: {}, status: {}", validationResult.getUsername(), status);
                    if (status != null && UserStatus.NORMAL.getCode().equals(status)) {
                        // roleType: 1=普通用户(USER), 2=管理员(ADMIN)
                        String role = validationResult.getRoleType() == 2 ? "ROLE_ADMIN" : "ROLE_USER";
                        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                                new SimpleGrantedAuthority(role)
                        );
                        UsernamePasswordAuthenticationToken authcation = new UsernamePasswordAuthenticationToken(
                                validationResult.getUsername(), null, authorities
                        );
                        SecurityContextHolder.getContext().setAuthentication(authcation);
                        request.setAttribute("jwtToken", token);
                        chain.doFilter(request, response);
                        return;
                    }
                }
            } catch (TokenExpiredException e) {
                log.warn("Token已过期: {}", e.getMessage());
                ResponseUtil.writeError(response, ResultCode.TOKEN_EXPIRED);
                return;
            } catch (JWTVerificationException e) {
                log.warn("Token无效: {}", e.getMessage());
                ResponseUtil.writeError(response, ResultCode.TOKEN_INVALID);
                return;
            }
        }

        ResponseUtil.writeError(response, ResultCode.ACCESS_UNAUTHORIZED);
    }
}