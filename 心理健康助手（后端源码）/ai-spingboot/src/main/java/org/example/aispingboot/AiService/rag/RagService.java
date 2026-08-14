package org.example.aispingboot.AiService.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.entity.KnowledgeChunk;
import org.example.aispingboot.mapper.KnowledgeArticleMapper;
import org.example.aispingboot.mapper.KnowledgeChunkMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 检索增强生成服务
 *
 * 职责：
 * 1. 索引管理：扫描知识库文章 → 分块 → TF-IDF向量化 → 存储到DB + 加载到内存
 * 2. 检索：用户消息 → TF-IDF向量化 → 余弦相似度 → Top-3 相关片段
 * 3. Prompt增强：将检索到的片段拼接为"参考资料"注入System Prompt
 *
 * 设计决策：
 * - 使用TF-IDF而非神经Embedding，因为DeepSeek API不提供Embedding接口
 * - 使用字符二元组（bigrams）分词，适配中文且无需分词库
 * - 索引加载到内存（Caffeine级别），检索零DB访问
 */
@Slf4j
@Service
public class RagService {

    @Autowired
    private KnowledgeArticleMapper articleMapper;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private DocumentChunker chunker;

    @Autowired
    private TfidfVectorStore vectorStore;

    private static final int TOP_K = 3;
    private static final int MAX_CONTEXT_CHARS = 1500;

    /**
     * 应用启动时自动加载索引
     * 如果DB中已有分块数据，直接加载；否则跳过，等待手动构建
     */
    @PostConstruct
    public void loadIndexOnStartup() {
        try {
            long count = chunkMapper.selectCount(null);
            if (count > 0) {
                log.info("RAG索引加载中，数据库中共有 {} 个分块", count);
                loadIndexFromDb();
            } else {
                log.info("RAG索引为空，请调用 POST /api/rag/rebuild 构建索引");
            }
        } catch (Exception e) {
            log.warn("RAG索引加载失败: {}", e.getMessage());
        }
    }

    /**
     * 重建索引：扫描所有已发布文章 → 分块 → 计算TF-IDF → 存储到DB + 加载到内存
     */
    public int rebuildIndex() {
        // 1. 查询所有已发布文章（status=1）
        List<KnowledgeArticle> articles = articleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeArticle>()
                        .eq(KnowledgeArticle::getStatus, 1)
                        .isNotNull(KnowledgeArticle::getContent)
        );
        log.info("RAG索引重建：扫描到 {} 篇已发布文章", articles.size());

        // 2. 清空旧分块
        chunkMapper.delete(null);

        // 3. 分块 + 存储
        List<TfidfVectorStore.ChunkEntry> entries = new ArrayList<>();
        for (KnowledgeArticle article : articles) {
            String cleanContent = stripHtml(article.getContent());
            List<String> chunks = chunker.chunk(cleanContent);

            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setArticleId(article.getId());
                chunk.setChunkIndex(i);
                chunk.setTitle(article.getTitle());
                chunk.setContent(chunks.get(i));
                chunk.setCreatedAt(LocalDateTime.now());
                chunkMapper.insert(chunk);

                entries.add(new TfidfVectorStore.ChunkEntry(
                        chunk.getId(), article.getId(), article.getTitle(), chunks.get(i)
                ));
            }
        }

        // 4. 构建TF-IDF索引
        vectorStore.buildIndex(entries);

        log.info("RAG索引重建完成：{} 篇文章 → {} 个分块", articles.size(), entries.size());
        return entries.size();
    }

    /**
     * 从DB加载已有索引到内存
     */
    private void loadIndexFromDb() {
        List<KnowledgeChunk> chunks = chunkMapper.selectList(null);
        List<TfidfVectorStore.ChunkEntry> entries = chunks.stream()
                .map(c -> new TfidfVectorStore.ChunkEntry(
                        c.getId(), c.getArticleId(), c.getTitle(), c.getContent()
                ))
                .collect(Collectors.toList());

        vectorStore.buildIndex(entries);
        log.info("RAG索引加载完成：{} 个分块已载入内存", entries.size());
    }

    /**
     * 检索与用户消息最相关的知识片段
     *
     * @param userMessage 用户消息
     * @return 检索结果列表（最多TOP_K条）
     */
    public List<TfidfVectorStore.SearchResult> retrieve(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return new ArrayList<>();
        }
        return vectorStore.search(userMessage, TOP_K);
    }

    /**
     * 构建RAG增强上下文文本
     * 将检索到的Top-K片段拼接为"参考资料"段落，供注入System Prompt
     *
     * @param userMessage 用户消息
     * @return 增强上下文文本（如果无检索结果返回空字符串）
     */
    public String buildAugmentedContext(String userMessage) {
        List<TfidfVectorStore.SearchResult> results = retrieve(userMessage);
        if (results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【知识库参考资料】\n");
        sb.append("以下是从知识库中检索到的专业心理知识片段，请结合这些内容回答用户问题。\n");
        sb.append("注意：参考资料仅供参考，回答时请保持你的专业判断和温暖风格。\n\n");

        int totalChars = 0;
        for (int i = 0; i < results.size(); i++) {
            TfidfVectorStore.SearchResult result = results.get(i);
            // 截断过长的片段，控制总上下文长度
            String snippet = result.content;
            if (totalChars + snippet.length() > MAX_CONTEXT_CHARS) {
                snippet = snippet.substring(0, MAX_CONTEXT_CHARS - totalChars) + "...";
            }
            sb.append(String.format("【参考%d】（来源：%s）\n%s\n\n",
                    i + 1, result.title, snippet));
            totalChars += snippet.length();

            if (totalChars >= MAX_CONTEXT_CHARS) {
                break;
            }
        }

        return sb.toString();
    }

    /**
     * 获取当前索引状态
     */
    public String getIndexStatus() {
        int chunkCount = vectorStore.getIndexedChunkCount();
        if (chunkCount == 0) {
            return "索引未构建";
        }
        return String.format("索引已加载：%d个分块", chunkCount);
    }

    /**
     * 去除HTML标签，提取纯文本
     */
    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("\\s+", " ")
                   .trim();
    }
}
