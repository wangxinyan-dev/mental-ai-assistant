package org.example.aispingboot.service;

import java.time.LocalDateTime;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.example.aispingboot.DTO.command.UserLoginCommandDTO;
import org.example.aispingboot.DTO.command.UserRegisterCommandDTO;
import org.example.aispingboot.DTO.response.UserLoginResponseDTO;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.entity.User;
import org.example.aispingboot.enumClass.UserStatus;
import org.example.aispingboot.enumClass.UserType;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.mapper.UserMapper;
import org.example.aispingboot.service.convert.UserConvert;
import org.example.aispingboot.util.JwtTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserService {
    @Resource
    private UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserLoginResponseDTO login(UserLoginCommandDTO commandDTO) {
        // 构建查询条件
        LambdaQueryWrapper<User> queryWrapper =  new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, commandDTO.getUsername())
                .or()
                .eq(User::getEmail, commandDTO.getUsername());
        // 调用MP API查询
        User user = userMapper.selectOne(queryWrapper);
        log.debug("查询到用户: {}", user);

        // 判断用户是否存在
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 验证密码
        String inputPassword = commandDTO.getPassword().trim();
        if (!passwordEncoder.matches(inputPassword, user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        // 检查用户的状态
        if (!user.isActive()) {
            throw new BusinessException("用户已被禁用，请联系管理员");
        }

        // 生成JWT token
        String token = JwtTokenUtil.generateToken(user.getId(), user.getUsername(), user.getUserType());
        log.debug("用户 {} 登录成功，生成token", user.getUsername());
        UserLoginResponseDTO.UserDetailResponseDTO userInfo = UserConvert.entityToDetailResponse(user);
        return UserConvert.entityToLoginResponse(token, userInfo);
    }

    public UserLoginResponseDTO.UserDetailResponseDTO register(UserRegisterCommandDTO commandDTO) {
        log.debug("注册请求: {}", JSONUtil.parseObj(commandDTO));
        // 验证密码是否一致
        if (!commandDTO.getPassword().equals(commandDTO.getConfirmPassword())) {
            throw new BusinessException("两次输入密码不一致");
        }

        // 检查用户名是否存在
        LambdaQueryWrapper<User> userNameQuery =  new LambdaQueryWrapper<>();
        userNameQuery.eq(User::getUsername, commandDTO.getUsername());
        if (userMapper.selectCount(userNameQuery) > 0) {
            throw new BusinessException("用户名已存在");
        }

        // 检查邮箱是否存在
        LambdaQueryWrapper<User> emailQuery =  new LambdaQueryWrapper<>();
        emailQuery.eq(User::getEmail, commandDTO.getEmail());
        if (userMapper.selectCount(emailQuery) > 0) {
            throw new BusinessException("邮箱已存在");
        }

        // 安全规则：/api/user/add 在白名单中，任何人可调用。
        // 不信任前端传入的 userType，强制注册为普通用户，杜绝越权提权（传 userType=2 也会被覆盖）。
        commandDTO.setUserType(UserType.USER.getCode());

        // 创建用户
        String password = commandDTO.getPassword().trim();
        String encodedPassword = passwordEncoder.encode(password);
        User user = UserConvert.registerCommandToEntity(commandDTO, encodedPassword);

        // 插入数据库
        userMapper.insert(user);

        return UserConvert.entityToDetailResponse(user);
    }

    public UserLoginResponseDTO.UserDetailResponseDTO getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return UserConvert.entityToDetailResponse(user);
    }

    /**
     * 修改用户状态（禁用/启用），仅管理员调用。
     * 主动清掉 userStatus 缓存，让 JwtAuthticationFilter 下一次请求立即读到最新状态，而不是等缓存 2 分钟自然过期。
     */
    @CacheEvict(value = "userStatus", key = "#targetUserId", cacheManager = "shortTtlCacheManager")
    public void updateUserStatus(Long operatorId, Long targetUserId, Integer status) {
        if (!UserStatus.isValidCode(status)) {
            throw new BusinessException("非法的用户状态");
        }
        // 不能修改自己的状态，防止管理员把自己锁在系统外
        if (operatorId != null && operatorId.equals(targetUserId)) {
            throw new BusinessException("不能修改自己的账号状态");
        }
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        target.setStatus(status);
        target.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(target);
    }
}
