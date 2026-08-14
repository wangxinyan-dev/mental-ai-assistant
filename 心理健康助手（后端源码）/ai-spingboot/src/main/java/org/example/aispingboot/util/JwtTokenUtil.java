package org.example.aispingboot.util;

import ch.qos.logback.core.util.StringUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.example.aispingboot.config.JwtConfig;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;

@Component
public class JwtTokenUtil implements ApplicationContextAware {
    private static final String ISSUER = "mental-health-assistant";

    private static ApplicationContext applicationContext;
    // 用于再静态工具类中获取Spring容器管理的Bean
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        JwtTokenUtil.applicationContext = applicationContext;
    }

    private static JwtConfig getJwtConfig() {
        return applicationContext.getBean(JwtConfig.class);
    };

    // 生成token的方法
    public static String generateToken(Long userId, String username, Integer roleType) {
        try {
            // 获取jwt的配置
            JwtConfig jwtConfig = getJwtConfig();
            // 生成签名的算法
            Algorithm algorithm = Algorithm.HMAC256(jwtConfig.getSecret());
            // 生成过期时间
            Date expiration = new Date(System.currentTimeMillis() + jwtConfig.getExpiration());

            String token = JWT.create()
                    .withClaim("userId", userId)
                    .withClaim("username", username)
                    .withClaim("roleType", roleType)
                    .withExpiresAt(expiration) // 设置过期时间
                    .withIssuedAt(new Date()) // 设置签发时间
                    .withIssuer(ISSUER) // 设置签发者
                    .sign(algorithm);
            return token;
        } catch (Exception e) {
            throw new RuntimeException("生成token 失败: " + e);
        }
    }

    // 提取token
    public static String extractTokenFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String tokenHeader = request.getHeader("token");
        if (StringUtils.hasText(tokenHeader)) {
            return tokenHeader;
        }
        return null;
    }

    // 获取当前token
    public static String getCurrentToken() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String token = (String) request.getAttribute("jwtToken");
            if (token != null) {
                return token;
            }

            // 备用方案： 从请求头直接获取
            String headerToken =  extractTokenFromRequest(request);
            return headerToken;
        }
        return null;
    }

    // 验证token
    public static TokenVerificationResult validateToken(String token) {
        DecodedJWT jwt = verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        String username = jwt.getClaim("username").asString();

        // 角色类型
        Integer roleType = null;
        try {
            roleType = jwt.getClaim("roleType").asInt();
        } catch (Exception e) {
           String roleTypeStr = jwt.getClaim("roleType").asString();
           if (StringUtils.hasText(roleTypeStr)) {
               roleType = Integer.valueOf(roleTypeStr);
           }
        }
        if (userId != null && StringUtils.hasText(username) && roleType != null) {
            return new TokenVerificationResult(userId, username, roleType, true);
        }
        return null;
    }

    // 验证token有效性
    public static DecodedJWT verifyToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new JWTVerificationException("Token不能为空");
        }
        // token解码
        JwtConfig jwtConfig = getJwtConfig();
        Algorithm algorithm = Algorithm.HMAC256(jwtConfig.getSecret());
        JWTVerifier verifier =  JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
        return  verifier.verify(token);
    }

    // Token验证结果封装类
    @Getter
    public static class TokenVerificationResult {
        private final Long userId;
        private final String username;
        private final Integer roleType;
        private final boolean valid;

        public TokenVerificationResult(Long userId, String username, Integer roleType, boolean valid) {
            this.userId = userId;
            this.username = username;
            this.roleType = roleType;
            this.valid = valid;
        }
    }
}
