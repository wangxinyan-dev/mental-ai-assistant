package org.example.aispingboot.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.example.aispingboot.DTO.command.UserLoginCommandDTO;
import org.example.aispingboot.DTO.command.UserRegisterCommandDTO;
import org.example.aispingboot.DTO.response.UserLoginResponseDTO;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理", description = "用户登录、注册、个人信息接口")
@RestController
@RequestMapping("/api/user")
public class User {
    @Resource
    private UserService userService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDTO commandDTO) {
        // 调用服务层登录方法
        UserLoginResponseDTO result = userService.login(commandDTO);
        return Result.ok(result);
    }

    // 用户注册接口
    @PostMapping("/add")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> register(@Valid @RequestBody UserRegisterCommandDTO commandDTO) {
        UserLoginResponseDTO.UserDetailResponseDTO result = userService.register(commandDTO);
        return Result.ok(result);
    }

    // 获取当前用户
    @GetMapping("/current")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> getCurrentUser() {
        // 如何从token中解析出用户的id
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        // 调用service层获取用户详情
        UserLoginResponseDTO.UserDetailResponseDTO result = userService.getUserById(userId);
        return Result.ok(result);
    }

    // 退出登录
    @PostMapping("/logout")
    public Result<String> logout() {
        return Result.ok("退出登录成功");
    }

}
