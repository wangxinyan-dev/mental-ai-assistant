package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.aispingboot.AiService.rag.RagAsyncTask;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.entity.KnowledgeCategory;
import org.example.aispingboot.mapper.KnowledgeArticleMapper;
import org.example.aispingboot.mapper.KnowledgeCategoryMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    @Resource
    private KnowledgeCategoryMapper categoryMapper;

    @Resource
    private KnowledgeArticleMapper articleMapper;

    @Resource
    private RagAsyncTask ragAsyncTask;

    @Cacheable(value = "categoryTree", cacheManager = "cacheManager")
    public List<Map<String, Object>> getCategoryTree() {
        List<KnowledgeCategory> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeCategory>().orderByAsc(KnowledgeCategory::getSortOrder));
        return categories.stream().map(cat -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", cat.getId());
            map.put("categoryName", cat.getCategoryName());
            map.put("parentId", cat.getParentId());
            map.put("sortOrder", cat.getSortOrder());
            return map;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> articlePage(Integer current, Integer size, String title,
                                           Long categoryId, Integer status,
                                           String sortField, String sortDirection) {
        Page<KnowledgeArticle> page = new Page<>(current, size);
        LambdaQueryWrapper<KnowledgeArticle> qw = new LambdaQueryWrapper<>();

        qw.select(KnowledgeArticle::getId, KnowledgeArticle::getTitle, KnowledgeArticle::getSummary,
                KnowledgeArticle::getCategoryId, KnowledgeArticle::getCoverImage, KnowledgeArticle::getTags,
                KnowledgeArticle::getAuthorName, KnowledgeArticle::getReadCount, KnowledgeArticle::getStatus,
                KnowledgeArticle::getCreatedAt, KnowledgeArticle::getUpdatedAt);

        if (title != null && !title.isEmpty()) {
            qw.like(KnowledgeArticle::getTitle, title);
        }
        if (categoryId != null) {
            qw.eq(KnowledgeArticle::getCategoryId, categoryId);
        }
        if (status != null) {
            qw.eq(KnowledgeArticle::getStatus, status);
        }

        if ("readCount".equals(sortField)) {
            qw.orderBy(true, "asc".equals(sortDirection), KnowledgeArticle::getReadCount);
        } else {
            qw.orderByDesc(KnowledgeArticle::getUpdatedAt);
        }

        Page<KnowledgeArticle> result = articleMapper.selectPage(page, qw);
        return Map.of("records", result.getRecords(), "total", result.getTotal());
    }

    public KnowledgeArticle getArticleById(Long id) {
        KnowledgeArticle article = articleMapper.selectById(id);
        if (article != null) {
            articleMapper.update(null, new LambdaUpdateWrapper<KnowledgeArticle>()
                    .eq(KnowledgeArticle::getId, id)
                    .setSql("read_count = COALESCE(read_count, 0) + 1"));
            article.setReadCount((article.getReadCount() == null ? 0 : article.getReadCount()) + 1);
        }
        return article;
    }

    public void saveArticle(Map<String, Object> dto, Long userId, Long articleId) {
        KnowledgeArticle article;
        if (articleId != null) {
            article = articleMapper.selectById(articleId);
            if (article == null) {
                article = new KnowledgeArticle();
                article.setCreatedAt(LocalDateTime.now());
            }
        } else {
            article = new KnowledgeArticle();
            article.setCreatedAt(LocalDateTime.now());
            article.setAuthorId(userId);
            article.setReadCount(0);
            article.setStatus(0);
        }

        if (dto.containsKey("title")) article.setTitle((String) dto.get("title"));
        if (dto.containsKey("content")) article.setContent((String) dto.get("content"));
        if (dto.containsKey("summary")) article.setSummary((String) dto.get("summary"));
        if (dto.containsKey("categoryId")) article.setCategoryId(toLong(dto.get("categoryId")));
        if (dto.containsKey("coverImage")) article.setCoverImage((String) dto.get("coverImage"));
        if (dto.containsKey("tags")) article.setTags((String) dto.get("tags"));
        if (dto.containsKey("authorName")) article.setAuthorName((String) dto.get("authorName"));
        article.setUpdatedAt(LocalDateTime.now());

        if (article.getId() == null) {
            articleMapper.insert(article);
        } else {
            articleMapper.updateById(article);
        }

        // 文章内容变更后，增量重建该篇文章的索引（不阻塞当前请求；未发布时内部自动清理残留）
        ragAsyncTask.triggerRebuildArticle(article.getId(), articleId == null ? "新增文章" : "编辑文章 id=" + articleId);
    }

    public void updateStatus(Long id, Integer status) {
        KnowledgeArticle article = articleMapper.selectById(id);
        if (article != null) {
            article.setStatus(status);
            article.setUpdatedAt(LocalDateTime.now());
            articleMapper.updateById(article);//同步落库
            // 状态变更影响 RAG 索引：发布→增量重建这篇，下线→增量删除这篇
            if (status != null && status == 1) {
                ragAsyncTask.triggerRebuildArticle(id, "文章发布 id=" + id);
            } else {
                ragAsyncTask.triggerDeleteArticle(id, "文章下线 id=" + id);
            }
        }
    }

    public void deleteArticle(Long id) {
        articleMapper.deleteById(id);
        // 文章删除后，增量移除其分块与向量
        ragAsyncTask.triggerDeleteArticle(id, "删除文章 id=" + id);
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long) return (Long) val;
        if (val instanceof Integer) return ((Integer) val).longValue();
        return Long.valueOf(val.toString());
    }
}
