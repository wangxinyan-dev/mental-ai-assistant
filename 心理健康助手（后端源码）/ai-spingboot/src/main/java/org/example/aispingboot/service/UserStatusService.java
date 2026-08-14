package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.example.aispingboot.entity.User;
import org.example.aispingboot.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class UserStatusService {

    @Resource
    private UserMapper userMapper;

    @Resource
    @Qualifier("shortTtlCacheManager")
    private CacheManager cacheManager;

    @Cacheable(value = "userStatus", key = "#userId", cacheManager = "shortTtlCacheManager", unless = "#result == null")
    public Integer getUserStatus(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null ? user.getStatus() : null;
    }

    public void evictUserStatus(Long userId) {
        if (cacheManager.getCache("userStatus") != null) {
            cacheManager.getCache("userStatus").evict(userId);
        }
    }
}