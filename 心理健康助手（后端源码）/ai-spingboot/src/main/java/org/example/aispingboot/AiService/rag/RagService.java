package org.example.aispingboot.AiService.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.entity.KnowledgeChunk;
import org.example.aispingboot.mapper.KnowledgeArticleMapper;
import org.example.aispingboot.mapper.KnowledgeChunkMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 检索增强生成服务（Embedding + PgVector 方案）
 *
 * 职责：
 * 1. 索引管理：扫描知识库文章 → TokenTextSplitter分块 → EmbeddingModel向量化 → PgVector存储
 * 2. 检索：用户消息 → EmbeddingModel向量化 → PgVector余弦相似度 → Top-3 相关片段
 * 3. Prompt增强：将检索到的片段拼接为"参考资料"注入System Prompt
 *
 * 技术栈：
 * - Embedding: OpenAI兼容API（SiliconFlow BAAI/bge-large-zh-v1.5，1024维）
 * - 向量存储: PostgreSQL + pgvector扩展，HNSW索引，余弦距离检索
 * - TextSplitter: Spring AI TokenTextSplitter（基于tokenizer的分块，支持中文）
 *
 * 架构说明：
 * - MySQL（MyBatis-Plus）：存储业务数据 + 分块元数据（knowledge_chunk表）
 * - PostgreSQL（pgvector）：存储向量索引（rag_embedding表），独立数据源 + JdbcTemplate
 * - 两库通过 chunk_id 关联，rebuildIndex 时双写保持一致
 */
@Slf4j
@Service
public class RagService {

    @Autowired
    private KnowledgeArticleMapper articleMapper;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    @Qualifier("pgVectorJdbcTemplate")
    private JdbcTemplate pgJdbcTemplate;

    @Value("${rag.embedding.api-key:}")
    private String embeddingApiKey;

    @Value("${rag.vector-store.pg.dimension:1024}")
    private int embeddingDimension;

    private static final int TOP_K = 3;
    private static final double SIMILARITY_THRESHOLD = 0.5;
    private static final int MAX_CONTEXT_CHARS = 1500;

    private final TokenTextSplitter textSplitter = new TokenTextSplitter();

    /**
     * 检索结果
     */
    public record SearchResult(String title, String content, double score) {}

    /**
     * 应用启动时初始化PgVector schema并检查索引状态
     */
    @PostConstruct
    public void checkIndexOnStartup() {
        initPgSchema();
        try {
            Integer pgCount = pgJdbcTemplate.queryForObject("SELECT COUNT(*) FROM rag_embedding", Integer.class);
            long dbCount = chunkMapper.selectCount(null);
            log.info("RAG索引状态：MySQL {} 个分块记录，PgVector {} 个向量", dbCount, pgCount == null ? 0 : pgCount);
        } catch (Exception e) {
            log.warn("RAG索引状态检查失败: {}", e.getMessage());
        }
    }

    /**
     * 初始化PgVector：扩展、表、HNSW索引
     * 幂等操作，重复执行无副作用
     */
    private void initPgSchema() {
        try {
            pgJdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            pgJdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS rag_embedding (" +
                    "  chunk_id BIGINT PRIMARY KEY," +
                    "  title VARCHAR(200)," +
                    "  content TEXT," +
                    "  embedding vector(" + embeddingDimension + ") NOT NULL" +
                    ")"
            );
            pgJdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS rag_embedding_hnsw_idx " +
                    "ON rag_embedding USING hnsw (embedding vector_cosine_ops)"
            );
            log.info("PgVector schema 已就绪（dimension={}）", embeddingDimension);
        } catch (Exception e) {
            log.error("PgVector schema 初始化失败，RAG检索将不可用: {}", e.getMessage());
        }
    }

    /**
     * 重建索引：扫描所有已发布文章 → 分块 → Embedding向量化 → 存入PgVector
     *
     * @return 分块数量
     */
    public int rebuildIndex() {
        if (embeddingApiKey == null || embeddingApiKey.isBlank()) {
            log.warn("RAG索引重建失败：未配置Embedding API密钥（rag.embedding.api-key）");
            return 0;
        }

        // 1. 查询所有已发布文章（status=1）
        List<KnowledgeArticle> articles = articleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeArticle>()
                        .eq(KnowledgeArticle::getStatus, 1)
                        .isNotNull(KnowledgeArticle::getContent)
        );
        log.info("RAG索引重建：扫描到 {} 篇已发布文章", articles.size());

        // 2. 清空旧索引（MySQL + PgVector）
        chunkMapper.delete(null);
        pgJdbcTemplate.update("DELETE FROM rag_embedding");

        // 3. 分块 + Embedding + 存储
        int chunkCount = 0;
        for (KnowledgeArticle article : articles) {
            String cleanContent = stripHtml(article.getContent());
            if (cleanContent.isBlank()) continue;

            // 用TokenTextSplitter分块
            Document articleDoc = Document.builder()
                    .text(cleanContent)
                    .metadata(Map.of("title", article.getTitle()))
                    .build();
            List<Document> chunks = textSplitter.split(articleDoc);

            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i).getText();

                // 存储分块元数据到MySQL
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setArticleId(article.getId());
                chunk.setChunkIndex(i);
                chunk.setTitle(article.getTitle());
                chunk.setContent(chunkText);
                chunk.setCreatedAt(LocalDateTime.now());
                chunkMapper.insert(chunk);

                // 调用Embedding API生成向量
                float[] vector = embeddingModel.embed(chunkText);

                // 存入PgVector向量库
                pgJdbcTemplate.update(
                        "INSERT INTO rag_embedding (chunk_id, title, content, embedding) VALUES (?, ?, ?, ?::vector)",
                        chunk.getId(),
                        article.getTitle(),
                        chunkText,
                        toPgVector(vector)
                );
                chunkCount++;
            }
        }

        log.info("RAG索引重建完成：{} 篇文章 → {} 个分块（PgVector存储）", articles.size(), chunkCount);
        return chunkCount;
    }

    /**
     * 检索与用户消息最相关的知识片段
     *
     * @param userMessage 用户消息
     * @return 检索结果列表（最多TOP_K条，按相似度降序）
     */
    public List<SearchResult> retrieve(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Collections.emptyList();
        }
        if (embeddingApiKey == null || embeddingApiKey.isBlank()) {
            return Collections.emptyList();
        }

        try {
            // 将用户消息向量化
            float[] queryVector = embeddingModel.embed(userMessage);
            String vecStr = toPgVector(queryVector);

            // PgVector余弦距离 <=> 范围 [0,2]，相似度 = 1 - 距离
            // ORDER BY embedding <=> ? 可命中HNSW索引，避免全表扫描
            String sql = "SELECT title, content, 1 - (embedding <=> ?::vector) AS similarity " +
                    "FROM rag_embedding " +
                    "ORDER BY embedding <=> ?::vector " +
                    "LIMIT " + TOP_K;

            List<SearchResult> results = pgJdbcTemplate.query(sql,
                    (rs, rowNum) -> new SearchResult(
                            rs.getString("title"),
                            rs.getString("content"),
                            rs.getDouble("similarity")
                    ),
                    vecStr, vecStr
            );

            // 过滤低于阈值的结果
            return results.stream()
                    .filter(r -> r.score >= SIMILARITY_THRESHOLD)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("RAG检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 构建RAG增强上下文文本
     * 将检索到的Top-K片段拼接为"参考资料"段落，供注入System Prompt
     */
    public String buildAugmentedContext(String userMessage) {
        List<SearchResult> results = retrieve(userMessage);
        if (results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【知识库参考资料】\n");
        sb.append("以下是从知识库中检索到的专业心理知识片段，请结合这些内容回答用户问题。\n");
        sb.append("注意：参考资料仅供参考，回答时请保持你的专业判断和温暖风格。\n\n");

        int totalChars = 0;
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            String snippet = result.content;

            // 截断过长的片段，控制总上下文长度
            if (totalChars + snippet.length() > MAX_CONTEXT_CHARS) {
                snippet = snippet.substring(0, MAX_CONTEXT_CHARS - totalChars) + "...";
            }
            sb.append(String.format("【参考%d】（来源：%s）\n%s\n\n", i + 1, result.title, snippet));
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
        try {
            Integer vectorCount = pgJdbcTemplate.queryForObject("SELECT COUNT(*) FROM rag_embedding", Integer.class);
            long dbCount = chunkMapper.selectCount(null);
            if (vectorCount == null || vectorCount == 0) {
                if (dbCount == 0) return "索引未构建";
                return String.format("索引异常：MySQL有%d个分块，PgVector有0个向量", dbCount);
            }
            return String.format("索引已加载：%d个分块（PgVector向量存储，%d维）", vectorCount, embeddingDimension);
        } catch (Exception e) {
            return "PgVector连接失败：" + e.getMessage();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 将float[]向量转换为PgVector字符串格式：[0.1,0.2,...]
     */
    private String toPgVector(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
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
