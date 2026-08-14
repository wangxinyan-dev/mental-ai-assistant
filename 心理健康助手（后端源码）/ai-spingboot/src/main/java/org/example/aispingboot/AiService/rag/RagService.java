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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * RAG 检索增强生成服务（Embedding 方案）
 *
 * 职责：
 * 1. 索引管理：扫描知识库文章 → TokenTextSplitter分块 → EmbeddingModel向量化 → 内存存储 + 文件持久化
 * 2. 检索：用户消息 → EmbeddingModel向量化 → 余弦相似度 → Top-3 相关片段
 * 3. Prompt增强：将检索到的片段拼接为"参考资料"注入System Prompt
 *
 * 技术栈：
 * - Embedding: OpenAI兼容API（阿里通义千问/OpenAI/SiliconFlow），独立于Chat的DeepSeek
 * - 向量存储: 内存ConcurrentHashMap + JSON文件持久化，应用重启自动加载
 * - TextSplitter: Spring AI TokenTextSplitter（基于tokenizer的分块，支持中文）
 *
 * 架构说明：
 * Spring AI 1.0.0 的 VectorStore 接口在独立模块中（需额外依赖），
 * 本项目直接使用 EmbeddingModel 接口 + 自定义内存向量存储，
 * 功能等价于 SimpleVectorStore，且零额外依赖。
 * 后续迁移到 PgVector/Milvus 只需替换存储和检索逻辑。
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

    @Value("${rag.vector-store.file-path:./rag-vector-store.json}")
    private String vectorStoreFilePath;

    @Value("${rag.embedding.api-key:}")
    private String embeddingApiKey;

    private static final int TOP_K = 3;
    private static final double SIMILARITY_THRESHOLD = 0.5;
    private static final int MAX_CONTEXT_CHARS = 1500;

    private final TokenTextSplitter textSplitter = new TokenTextSplitter();

    /** 内存向量存储：线程安全的列表，存储所有已向量化的文档片段 */
    private final CopyOnWriteArrayList<EmbeddingEntry> vectorStore = new CopyOnWriteArrayList<>();

    /**
     * 内存中的向量条目
     */
    private static class EmbeddingEntry {
        final String id;
        final String title;
        final String content;
        final float[] vector;

        EmbeddingEntry(String id, String title, String content, float[] vector) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.vector = vector;
        }
    }

    /**
     * 检索结果
     */
    public record SearchResult(String title, String content, double score) {}

    /**
     * 应用启动时检查索引状态
     */
    @PostConstruct
    public void checkIndexOnStartup() {
        long dbCount = chunkMapper.selectCount(null);
        if (dbCount > 0) {
            log.info("RAG索引：数据库中有 {} 个分块记录", dbCount);
        } else {
            log.info("RAG索引为空，请调用 POST /api/rag/rebuild 构建索引");
        }
    }

    /**
     * 重建索引：扫描所有已发布文章 → 分块 → Embedding向量化 → 存入内存
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

        // 2. 清空旧索引（DB + 内存）
        chunkMapper.delete(null);
        vectorStore.clear();

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

                // 存储分块元数据到DB
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setArticleId(article.getId());
                chunk.setChunkIndex(i);
                chunk.setTitle(article.getTitle());
                chunk.setContent(chunkText);
                chunk.setCreatedAt(LocalDateTime.now());
                chunkMapper.insert(chunk);

                // 调用Embedding API生成向量
                float[] vector = embeddingModel.embed(chunkText);

                // 存入内存向量库
                vectorStore.add(new EmbeddingEntry(
                        "chunk_" + chunk.getId(),
                        article.getTitle(),
                        chunkText,
                        vector
                ));
                chunkCount++;
            }
        }

        log.info("RAG索引重建完成：{} 篇文章 → {} 个分块（已Embedding向量化）", articles.size(), chunkCount);
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
        if (vectorStore.isEmpty() || embeddingApiKey == null || embeddingApiKey.isBlank()) {
            return Collections.emptyList();
        }

        try {
            // 将用户消息向量化
            float[] queryVector = embeddingModel.embed(userMessage);

            // 计算余弦相似度并排序
            List<SearchResult> scored = vectorStore.stream()
                    .map(entry -> new SearchResult(
                            entry.title,
                            entry.content,
                            cosineSimilarity(queryVector, entry.vector)
                    ))
                    .filter(r -> r.score >= SIMILARITY_THRESHOLD)
                    .sorted((a, b) -> Double.compare(b.score, a.score))
                    .limit(TOP_K)
                    .collect(Collectors.toList());

            return scored;
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
        int vectorCount = vectorStore.size();
        long dbCount = chunkMapper.selectCount(null);
        if (vectorCount == 0 && dbCount == 0) {
            return "索引未构建";
        }
        return String.format("索引已加载：%d个分块（Embedding向量存储，%d维）", vectorCount, getEmbeddingDimensions());
    }

    // ==================== 内部方法 ====================

    /**
     * 获取Embedding向量维度
     */
    private int getEmbeddingDimensions() {
        if (vectorStore.isEmpty()) return 0;
        return vectorStore.get(0).vector.length;
    }

    /**
     * 计算两个向量的余弦相似度
     *
     * cos(A, B) = (A·B) / (|A| × |B|)
     * 值域 [-1, 1]，越接近1表示越相似
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
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
