package org.example.aispingboot.controller;

import org.example.aispingboot.AiService.rag.RagService;
import org.example.aispingboot.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * RAG 索引管理接口
 * 供管理员构建/重建知识库索引
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    @Autowired
    private RagService ragService;

    /**
     * 重建RAG索引
     * 扫描所有已发布文章 → TokenTextSplitter分块 → Embedding向量化 → 存入VectorStore
     */
    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> rebuildIndex() {
        int chunkCount = ragService.rebuildIndex();
        return Result.ok(Map.of(
                "message", "索引重建成功",
                "chunkCount", chunkCount
        ));
    }

    /**
     * 查看索引状态
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getStatus() {
        return Result.ok(Map.of(
                "status", ragService.getIndexStatus()
        ));
    }

    /**
     * 测试检索（调试用）
     * 输入查询文本，返回Top-3相关片段（含相似度分数）
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Object> search(@RequestParam String query) {
        return Result.ok(ragService.retrieve(query));
    }
}
