package org.example.aispingboot.util;

import org.example.aispingboot.config.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtTokenUtilTest {

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        config.setSecret("TestSecretKeyForUnitTest2025!@#$%");
        config.setExpiration(3600000L);
        config.setRefreshExpiration(604800000L);
        config.setHeader("Authorization");
        config.setTokenPrefix("Bearer ");

        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(JwtConfig.class)).thenReturn(config);
        new JwtTokenUtil().setApplicationContext(context);
    }

    @Test
    void generateToken_shouldCreateValidToken() {
        String token = JwtTokenUtil.generateToken(1L, "testuser", 1);

        assertNotNull(token);
        assertFalse(token.isEmpty());

        JwtTokenUtil.TokenVerificationResult result = JwtTokenUtil.validateToken(token);
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(1L, result.getUserId());
        assertEquals("testuser", result.getUsername());
        assertEquals(1, result.getRoleType());
    }

    @Test
    void generateToken_shouldCreateTokenWithCorrectClaims() {
        String token = JwtTokenUtil.generateToken(2L, "admin", 2);

        var decoded = JwtTokenUtil.verifyToken(token);
        assertNotNull(decoded);
        assertEquals(2L, decoded.getClaim("userId").asLong());
        assertEquals("admin", decoded.getClaim("username").asString());
        assertEquals("mental-health-assistant", decoded.getIssuer());
    }

    @Test
    void extractTokenFromRequest_shouldReturnNull_whenNoAuthorizationHeader() {
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        String token = JwtTokenUtil.extractTokenFromRequest(request);

        assertNull(token);
    }

    @Test
    void extractTokenFromRequest_shouldStripBearerPrefix() {
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer test.jwt.token");

        String token = JwtTokenUtil.extractTokenFromRequest(request);

        assertEquals("test.jwt.token", token);
    }
}
