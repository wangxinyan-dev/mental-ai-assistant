package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.aispingboot.DTO.command.UserLoginCommandDTO;
import org.example.aispingboot.DTO.command.UserRegisterCommandDTO;
import org.example.aispingboot.DTO.response.UserLoginResponseDTO;
import org.example.aispingboot.config.JwtConfig;
import org.example.aispingboot.entity.User;
import org.example.aispingboot.enumClass.UserStatus;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.mapper.UserMapper;
import org.example.aispingboot.util.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private User mockUser;
    private final String rawPassword = "test123456";

    @BeforeEach
    void setUp() {
        // 使用真实 BCrypt 编码密码，因为 UserService 中 passwordEncoder 是 final 字段
        String encodedPw = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(rawPassword);
        mockUser = User.builder()
            .id(1L).username("testuser").email("test@example.com")
            .password(encodedPw).userType(1).status(1)
            .nickname("测试用户").build();

        // 设置 JwtTokenUtil ApplicationContext 以支持 token 生成
        JwtConfig config = new JwtConfig();
        config.setSecret("TestSecretKeyForUnitTest2025!@#$%");
        config.setExpiration(3600000L);
        config.setRefreshExpiration(604800000L);
        config.setHeader("Authorization");
        config.setTokenPrefix("Bearer ");
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(JwtConfig.class)).thenReturn(config);
        new JwtTokenUtil().setApplicationContext(ctx);
    }

    @Test
    void login_shouldReturnToken_whenCredentialsAreCorrect() {
        UserLoginCommandDTO dto = new UserLoginCommandDTO();
        dto.setUsername("testuser");
        dto.setPassword(rawPassword);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);

        UserLoginResponseDTO result = userService.login(dto);

        assertNotNull(result);
        assertNotNull(result.getToken());
        verify(userMapper, atLeastOnce()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void register_shouldSucceed_withValidData() {
        UserRegisterCommandDTO dto = new UserRegisterCommandDTO();
        dto.setUsername("newuser");
        dto.setEmail("new@example.com");
        dto.setPassword("password123");
        dto.setConfirmPassword("password123");
        dto.setUserType(1);

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        UserLoginResponseDTO.UserDetailResponseDTO result = userService.register(dto);

        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
    }

    @Test
    void getUserById_shouldReturnUser() {
        when(userMapper.selectById(1L)).thenReturn(mockUser);

        var result = userService.getUserById(1L);

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
    }

    // ============ updateUserStatus（禁用/启用用户，本次新增） ============

    @Test
    void updateUserStatus_shouldDisableTargetUser() {
        // Arrange：目标用户 2L，当前状态正常
        User target = User.builder().id(2L).username("target").status(UserStatus.NORMAL.getCode()).build();
        when(userMapper.selectById(2L)).thenReturn(target);

        // Act：管理员 1L 禁用用户 2L
        userService.updateUserStatus(1L, 2L, UserStatus.DISABLED.getCode());

        // Assert：状态被改成禁用，且 updateById 被调用了一次
        assertEquals(UserStatus.DISABLED.getCode(), target.getStatus());
        verify(userMapper, times(1)).updateById(target);
    }

    @Test
    void updateUserStatus_shouldThrow_whenInvalidStatus() {
        // Act & Assert：传非法状态值 99，预期抛业务异常
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateUserStatus(1L, 2L, 99));

        assertEquals("非法的用户状态", ex.getMessage());
    }

    @Test
    void updateUserStatus_shouldThrow_whenDisableSelf() {
        // Act & Assert：管理员 1L 试图禁用自己 1L
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateUserStatus(1L, 1L, UserStatus.DISABLED.getCode()));

        assertEquals("不能修改自己的账号状态", ex.getMessage());
    }

    @Test
    void updateUserStatus_shouldThrow_whenTargetNotFound() {
        // Arrange：selectById 返回 null，表示目标用户不存在
        when(userMapper.selectById(999L)).thenReturn(null);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateUserStatus(1L, 999L, UserStatus.DISABLED.getCode()));

        assertEquals("用户不存在", ex.getMessage());
    }
}
