package org.example.aispingboot.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.service.EmotionDiaryService;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/emotion-diary")
public class EmotionDiary {

    @Resource
    private EmotionDiaryService emotionDiaryService;

    @PostMapping
    public Result<String> add(@RequestBody Map<String, Object> data) {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        emotionDiaryService.saveDiary(userId, data);
        return Result.ok("保存成功");
    }

    @GetMapping("/my-list")
    public Result<Map<String, Object>> myList(@RequestParam(defaultValue = "1") Integer current,
                                              @RequestParam(defaultValue = "10") Integer size) {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        return Result.ok(emotionDiaryService.userPage(current, size, userId));
    }

    /**
     * 情绪趋势分析：连续多日评分下降时自动触发关怀提示
     */
    @GetMapping("/trend")
    public Result<Map<String, Object>> trend() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        return Result.ok(emotionDiaryService.analyzeTrend(userId));
    }

    @GetMapping("/admin/page")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> adminPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String moodScreRange) {
        return Result.ok(emotionDiaryService.adminPage(current, size, userId, moodScreRange));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> delete(@PathVariable Long id) {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        emotionDiaryService.deleteDiary(id, userId);
        return Result.ok("删除成功");
    }
}
