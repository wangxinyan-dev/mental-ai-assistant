package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.aispingboot.AiService.rag.RagAsyncTask;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.entity.KnowledgeCategory;
import org.example.aispingboot.mapper.KnowledgeArticleMapper;
import org.example.aispingboot.mapper.KnowledgeCategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock
    private KnowledgeCategoryMapper categoryMapper;

    @Mock
    private KnowledgeArticleMapper articleMapper;

    // 增量重建后 KnowledgeService 新增了 ragAsyncTask 依赖，测试必须 mock 它，否则触发 NullPointerException
    @Mock
    private RagAsyncTask ragAsyncTask;

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

    // articlePage 内部用了 qw.select(KnowledgeArticle::getId, ...)，会触发 MyBatis-Plus
    // 对实体 TableInfo/lambda 元数据的解析；纯 mock 单测环境没有 MyBatis-Plus 运行时，跑不了。
    // 这类「深度绑定 ORM」的方法，真实项目里该用集成测试（H2/Testcontainers 起真实库）来测。

    @Test
    void getArticleById_shouldIncrementReadCount() {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(1L);
        article.setTitle("测试文章");
        article.setReadCount(100);

        when(articleMapper.selectById(1L)).thenReturn(article);
        // 实现里用的是 articleMapper.update(null, wrapper)，不是 updateById，所以这里必须 stub 真实调用的方法
        when(articleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

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
