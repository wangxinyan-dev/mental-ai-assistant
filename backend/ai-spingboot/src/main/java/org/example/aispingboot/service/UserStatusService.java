package org.example.aispingboot.service;

import jakarta.annotation.Resource;
import org.example.aispingboot.entity.User;
import org.example.aispingboot.mapper.UserMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class UserStatusService {

    @Resource
    private UserMapper userMapper;

    @Cacheable(value = "userStatus", key = "#userId", cacheManager = "shortTtlCacheManager", unless = "#result == null")
    public Integer getUserStatus(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null ? user.getStatus() : null;
    }
}
