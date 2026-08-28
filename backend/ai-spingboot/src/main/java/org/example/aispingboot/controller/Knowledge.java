package org.example.aispingboot.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.example.aispingboot.annotation.AuditLog;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.service.KnowledgeService;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class Knowledge {

    @Resource
    private KnowledgeService knowledgeService;

    @GetMapping("/category/tree")
    public Result<List<Map<String, Object>>> categoryTree() {
        return Result.ok(knowledgeService.getCategoryTree());
    }

    @GetMapping("/article/page")
    public Result<Map<String, Object>> articlePage(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "updatedAt") String sortField,
            @RequestParam(required = false, defaultValue = "desc") String sortDirection) {
        return Result.ok(knowledgeService.articlePage(currentPage, size, title, categoryId, status, sortField, sortDirection));
    }

    @GetMapping("/article/{id}")
    public Result<KnowledgeArticle> articleDetail(@PathVariable Long id) {
        return Result.ok(knowledgeService.getArticleById(id));
    }

    @PostMapping("/article")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(action = "create_article", module = "knowledge")
    public Result<String> createArticle(@RequestBody Map<String, Object> dto) {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        knowledgeService.saveArticle(dto, userId, null);
        return Result.ok("文章创建成功");
    }

    @PutMapping("/article/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(action = "update_article", module = "knowledge")
    public Result<String> updateArticle(@PathVariable Long id, @RequestBody Map<String, Object> dto) {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        knowledgeService.saveArticle(dto, userId, id);
        return Result.ok("文章更新成功");
    }

    @PutMapping("/article/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(action = "update_article_status", module = "knowledge")
    public Result<String> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        knowledgeService.updateStatus(id, toInt(body.get("status")));
        return Result.ok("状态更新成功");
    }

    @DeleteMapping("/article/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(action = "delete_article", module = "knowledge")
    public Result<String> deleteArticle(@PathVariable Long id) {
        knowledgeService.deleteArticle(id);
        return Result.ok("删除成功");
    }

    private Integer toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Integer) return (Integer) val;
        return Integer.valueOf(val.toString());
    }
}
