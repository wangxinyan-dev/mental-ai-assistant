package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.entity.KnowledgeCategory;
import org.example.aispingboot.mapper.KnowledgeArticleMapper;
import org.example.aispingboot.mapper.KnowledgeCategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock
    private KnowledgeCategoryMapper categoryMapper;

    @Mock
    private KnowledgeArticleMapper articleMapper;

    @InjectMocks
    private KnowledgeService knowledgeService;

    @Test
    void getCategoryTree_shouldReturnCategories() {
        KnowledgeCategory cat = new KnowledgeCategory();
        cat.setId(1L);
        cat.setCategoryName("情绪管理");
        cat.setSortOrder(1);

        when(categoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cat));

        List<Map<String, Object>> result = knowledgeService.getCategoryTree();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("情绪管理", result.get(0).get("categoryName"));
    }

    @Test
    void articlePage_shouldReturnPaginatedResults() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(1L);
        article.setTitle("测试文章");

        Page<KnowledgeArticle> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(article));
        mockPage.setTotal(1);

        when(articleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Map<String, Object> result = knowledgeService.articlePage(1, 10, null, null, null, null, null);

        assertNotNull(result);
    }

    @Test
    void getArticleById_shouldIncrementReadCount() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(1L);
        article.setTitle("测试文章");
        article.setReadCount(100);

        when(articleMapper.selectById(1L)).thenReturn(article);
        when(articleMapper.updateById(any(KnowledgeArticle.class))).thenReturn(1);

        KnowledgeArticle result = knowledgeService.getArticleById(1L);

        assertNotNull(result);
        assertEquals(101, result.getReadCount());
    }

    @Test
    void deleteArticle_shouldCallMapperDelete() {
        when(articleMapper.deleteById(1L)).thenReturn(1);

        knowledgeService.deleteArticle(1L);

        verify(articleMapper, times(1)).deleteById(1L);
    }

    @Test
    void updateStatus_shouldChangeArticleStatus() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(1L);
        article.setStatus(0);

        when(articleMapper.selectById(1L)).thenReturn(article);
        when(articleMapper.updateById(any(KnowledgeArticle.class))).thenReturn(1);

        knowledgeService.updateStatus(1L, 1);

        assertEquals(1, article.getStatus());
    }
}
