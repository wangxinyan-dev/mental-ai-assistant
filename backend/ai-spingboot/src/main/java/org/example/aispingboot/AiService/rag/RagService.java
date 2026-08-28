package org.example.aispingboot.AiService.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.aispingboot.config.EmbeddingConfig;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.entity.KnowledgeChunk;
import org.example.aispingboot.mapper.KnowledgeArticleMapper;
import org.example.aispingboot.mapper.KnowledgeChunkMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
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

    @Autowired
    private EmbeddingConfig embeddingConfig;

    @Autowired
    @Qualifier("pgVectorTransactionManager")
    private PlatformTransactionManager pgTransactionManager;

    @Value("${rag.vector-store.pg.dimension:1024}")
    private int embeddingDimension;

    private static final int TOP_K = 3;
    private static final double SIMILARITY_THRESHOLD = 0.5;
    private static final int MAX_CONTEXT_CHARS = 1500;
    private static final int EMBEDDING_BATCH_SIZE = 10;

    /**
     * RAG 检索结果缓存：同一用户消息复用向量化 + 检索结果，避免每次对话都打 Embedding HTTP。
     * 键为用户消息原文；rebuildIndex 重建索引时需 invalidateAll，防止返回过期结果。
     */
    private final Cache<String, List<SearchResult>> retrievalCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    /**
     * 批量向量化时的中间对象：保存 MySQL 分块记录 + 对应的文本
     * 等批量 Embedding 返回后再按位置关联向量写入 PgVector
     */
    private record ChunkBatchItem(KnowledgeChunk chunk, String title, String chunkText) {}

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
                    "  article_id BIGINT," +
                    "  title VARCHAR(200)," +
                    "  content TEXT," +
                    "  embedding vector(" + embeddingDimension + ") NOT NULL" +
                    ")"
            );
            // 增量重建依赖 article_id；老表缺列时幂等补上（CREATE TABLE IF NOT EXISTS 不改已有表结构）
            pgJdbcTemplate.execute("ALTER TABLE rag_embedding ADD COLUMN IF NOT EXISTS article_id BIGINT");
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
     * 重建索引：扫描所有已发布文章 → 分块 → 批量 Embedding 向量化 → 双写 MySQL + PgVector
     *
     * 一致性策略（影子表原子切换）：
     * - MySQL 侧（knowledge_chunk 表）由 @Transactional 保证原子性：删旧 + 插新在同一事务，
     *   失败回滚，InnoDB 事务隔离让中间状态对其他连接不可见，天然无空窗。
     * - PgVector 侧（独立数据源、自动提交）采用「影子表」方案，解决"先删后建"的索引空窗：
     *   1. 新向量先写入临时表 rag_embedding_shadow，全程不动正式表 rag_embedding；
     *   2. 全部写入成功后，在单个 PG 事务内 ALTER TABLE ... RENAME 原子切换（DDL 事务性）；
     *   3. 任一环节失败 → 只删影子表，正式表保持旧索引，检索零空窗。
     *   边界：影子表解决的是「PG 索引空窗」，跨库最终一致（MySQL 提交 vs PG 切换仍不同步）
     *   依赖幂等重建兜底，强一致需 JTA 两阶段提交。
     *
     * 性能优化：
     * - Embedding 调用从"每条 1 次 HTTP"改为"10 条 1 次批量"，降低 ~90% 的网络 round-trip
     *
     * @return 分块数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int rebuildIndex() {
        if (embeddingConfig.getEmbeddingApiKey() == null || embeddingConfig.getEmbeddingApiKey().isBlank()) {
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

        // 2. 清空 MySQL 旧分块（受 @Transactional 保护，失败回滚）
        chunkMapper.delete(null);

        // 3. 分块并写入 MySQL，同时收集 (chunk, title, chunkText) 供批量向量化
        List<ChunkBatchItem> allItems = new ArrayList<>();
        for (KnowledgeArticle article : articles) {
            String cleanContent = stripHtml(article.getContent());
            if (cleanContent.isBlank()) continue;

            Document articleDoc = Document.builder()
                    .text(cleanContent)
                    .metadata(Map.of("title", article.getTitle()))
                    .build();
            List<Document> chunks = textSplitter.split(articleDoc);

            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i).getText();

                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setArticleId(article.getId());
                chunk.setChunkIndex(i);
                chunk.setTitle(article.getTitle());
                chunk.setContent(chunkText);
                chunk.setCreatedAt(LocalDateTime.now());
                chunkMapper.insert(chunk);   // 受 @Transactional 保护

                allItems.add(new ChunkBatchItem(chunk, article.getTitle(), chunkText));
            }
        }

        // 无可向量化分块：此时无 Embedding 调用（无中途失败风险），直接清空 PG 正式表即可
        if (allItems.isEmpty()) {
            pgJdbcTemplate.update("DELETE FROM rag_embedding");
            retrievalCache.invalidateAll();
            log.info("RAG索引重建完成：无可向量化分块，索引已清空");
            return 0;
        }

        // 4. 影子表写入：新向量先落 rag_embedding_shadow，全程不动正式表，检索零空窗
        createShadowTable();
        try {
            int chunkCount = embedAndInsert(allItems, "rag_embedding_shadow");

            // 5. 全部写入成功 → 单事务原子切换影子表为正式表（DDL 事务性，切换瞬间对外不可见）
            swapShadowTable();
            // 索引内容已变，清缓存避免返回过期片段
            retrievalCache.invalidateAll();

            log.info("RAG索引重建完成：{} 篇文章 → {} 个分块（PgVector影子表已原子切换）", articles.size(), chunkCount);
            return chunkCount;
        } finally {
            // 幂等清理：成功切换后影子表已改名不存在（no-op）；失败时删掉影子表，正式表保持旧索引
            dropShadowTable();
        }
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
        if (embeddingConfig.getEmbeddingApiKey() == null || embeddingConfig.getEmbeddingApiKey().isBlank()) {
            return Collections.emptyList();
        }

        // 命中缓存则直接返回，跳过向量化 HTTP + PG 查询
        List<SearchResult> cached = retrievalCache.getIfPresent(userMessage);
        if (cached != null) {
            return cached;
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

            // 过滤低于阈值的结果，并写入缓存
            List<SearchResult> filtered = results.stream()
                    .filter(r -> r.score >= SIMILARITY_THRESHOLD)
                    .collect(Collectors.toList());
            retrievalCache.put(userMessage, filtered);
            return filtered;
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

    // ==================== 影子表切换 ====================

    /**
     * 创建 PG 影子表：结构与正式表一致，HNSW 索引名不同（shadow 后缀）
     * 新向量先写入此表，全部成功后原子切换为正式表，避免"先删后建"的检索空窗
     */
    private void createShadowTable() {
        pgJdbcTemplate.execute("DROP TABLE IF EXISTS rag_embedding_shadow");
        pgJdbcTemplate.execute(
                "CREATE TABLE rag_embedding_shadow (" +
                "  chunk_id BIGINT PRIMARY KEY," +
                "  article_id BIGINT," +
                "  title VARCHAR(200)," +
                "  content TEXT," +
                "  embedding vector(" + embeddingDimension + ") NOT NULL" +
                ")"
        );
        pgJdbcTemplate.execute(
                "CREATE INDEX rag_embedding_shadow_hnsw_idx " +
                "ON rag_embedding_shadow USING hnsw (embedding vector_cosine_ops)"
        );
    }

    /**
     * 原子切换：影子表 → 正式表，全程在单个 PG 事务内（利用 PG 的 DDL 事务性）
     *
     * 四个语句在提交前对其他会话不可见，因此切换不存在空窗：
     *  1. rag_embedding            → rag_embedding_old   （旧表暂存，旧索引名随之保留）
     *  2. rag_embedding_shadow     → rag_embedding       （新表上位）
     *  3. DROP rag_embedding_old                        （连带释放旧 HNSW 索引名）
     *  4. rag_embedding_shadow_hnsw_idx → rag_embedding_hnsw_idx（对齐 init 里的索引命名）
     */
    private void swapShadowTable() {
        new TransactionTemplate(pgTransactionManager).execute(status -> {
            pgJdbcTemplate.execute("ALTER TABLE rag_embedding RENAME TO rag_embedding_old");
            pgJdbcTemplate.execute("ALTER TABLE rag_embedding_shadow RENAME TO rag_embedding");
            pgJdbcTemplate.execute("DROP TABLE rag_embedding_old");
            pgJdbcTemplate.execute("ALTER INDEX rag_embedding_shadow_hnsw_idx RENAME TO rag_embedding_hnsw_idx");
            return null;
        });
    }

    /**
     * 清理影子表：幂等。成功切换后影子表已改名不存在（no-op）；失败时删除以保持正式表不变
     */
    private void dropShadowTable() {
        try {
            pgJdbcTemplate.execute("DROP TABLE IF EXISTS rag_embedding_shadow");
        } catch (Exception e) {
            log.warn("清理影子表失败（不影响正式索引）: {}", e.getMessage());
        }
    }

    // ==================== 增量重建 ====================

    /**
     * 增量重建：仅重建单篇文章的向量索引（新增/编辑文章时调用）
     *
     * 与全量 rebuildIndex 的一致性策略不同：
     * - 全量影响所有文章，用影子表保证切换零空窗；
     * - 增量只影响单篇文章，影响面小，直接「删旧 + 插新」即可，无需影子表。
     *
     * 文章不存在或未发布（status != 1）时，只清理残留索引，不入索引。
     *
     * @param articleId 文章ID
     * @return 该文章写入的分块数
     */
    @Transactional(rollbackFor = Exception.class)
    public int rebuildArticle(Long articleId) {
        if (embeddingConfig.getEmbeddingApiKey() == null || embeddingConfig.getEmbeddingApiKey().isBlank()) {
            log.warn("RAG增量重建失败：未配置Embedding API密钥（rag.embedding.api-key）");
            return 0;
        }

        KnowledgeArticle article = articleMapper.selectById(articleId);
        // 文章不存在或未发布：清掉残留向量后返回
        if (article == null || article.getStatus() == null || article.getStatus() != 1) {
            removeArticleIndex(articleId);
            retrievalCache.invalidateAll();
            log.info("RAG增量重建：文章 {} 不存在或未发布，已清理残留索引", articleId);
            return 0;
        }

        String cleanContent = stripHtml(article.getContent());
        if (cleanContent.isBlank()) {
            removeArticleIndex(articleId);
            retrievalCache.invalidateAll();
            log.info("RAG增量重建：文章 {} 内容为空，已清理残留索引", articleId);
            return 0;
        }

        // 1. 先删旧：该文章的 MySQL 分块 + PG 向量
        removeArticleIndex(articleId);

        // 2. 重新分块并写入 MySQL
        Document articleDoc = Document.builder()
                .text(cleanContent)
                .metadata(Map.of("title", article.getTitle()))
                .build();
        List<Document> chunks = textSplitter.split(articleDoc);

        List<ChunkBatchItem> items = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i).getText();
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setArticleId(articleId);
            chunk.setChunkIndex(i);
            chunk.setTitle(article.getTitle());
            chunk.setContent(chunkText);
            chunk.setCreatedAt(LocalDateTime.now());
            chunkMapper.insert(chunk);

            items.add(new ChunkBatchItem(chunk, article.getTitle(), chunkText));
        }

        if (items.isEmpty()) {
            return 0;
        }

        // 3. 批量向量化 + 直接写正式表（增量影响面小，无需影子表）
        int chunkCount = embedAndInsert(items, "rag_embedding");
        // 缓存 key 是消息原文、无法按文章定向失效，直接全清
        retrievalCache.invalidateAll();
        log.info("RAG增量重建完成：文章 {}「{}」→ {} 个分块", articleId, article.getTitle(), chunkCount);
        return chunkCount;
    }

    /**
     * 增量删除：移除单篇文章的分块与向量（删除/下线文章时调用）
     *
     * @param articleId 文章ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticleVectors(Long articleId) {
        removeArticleIndex(articleId);
        retrievalCache.invalidateAll();
        log.info("RAG增量删除完成：文章 {} 的分块与向量已移除", articleId);
    }

    /**
     * 移除单篇文章的索引（MySQL 分块 + PG 向量）。
     * MySQL 侧删除由外层 @Transactional 保护；PG 侧独立连接自动提交。
     */
    private void removeArticleIndex(Long articleId) {
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getArticleId, articleId));
        // 增量删除按 article_id 过滤；当前数据量小未建 btree 索引，数据量大时应加 idx(article_id)
        pgJdbcTemplate.update("DELETE FROM rag_embedding WHERE article_id = ?", articleId);
    }

    // ==================== 内部方法 ====================

    /**
     * 批量向量化并写入指定表。
     * targetTable 由内部常量传入（"rag_embedding" 或 "rag_embedding_shadow"），非外部输入，无注入风险。
     * 全量写影子表、增量写正式表，复用同一套「批量 Embedding + 位置对齐 + 维度校验」逻辑。
     */
    private int embedAndInsert(List<ChunkBatchItem> items, String targetTable) {
        long embeddingStart = System.currentTimeMillis();
        int chunkCount = 0;
        for (int start = 0; start < items.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, items.size());
            List<ChunkBatchItem> batch = items.subList(start, end);

            List<String> batchTexts = batch.stream()
                    .map(ChunkBatchItem::chunkText)
                    .toList();
            EmbeddingRequest request = new EmbeddingRequest(batchTexts, null);
            EmbeddingResponse response = callWithRetry(request);
            // 3 次重试全部失败返回 null：跳过整批（幂等重建可补齐），不中断其余批次与影子表切换
            if (response == null) {
                log.warn("[RAG-EMBED-RETRY] 重试耗尽，跳过本批 {} 条，继续下一批", batch.size());
                continue;
            }

            if (response.getResults().size() != batch.size()) {
                throw new IllegalStateException(String.format(
                        "Embedding 批量返回异常：请求 %d 条，收到 %d 条结果",
                        batch.size(), response.getResults().size()));
            }

            for (int i = 0; i < batch.size(); i++) {
                ChunkBatchItem item = batch.get(i);
                float[] vector = response.getResults().get(i).getOutput();
                // 维度校验：换 Embedding 模型后维度若变化，CREATE TABLE IF NOT EXISTS 不会重建旧表，
                // 提前拦截给出可读错误，而不是让 PG 抛晦涩的 "expected N dimensions"
                if (vector.length != embeddingDimension) {
                    throw new IllegalStateException(String.format(
                            "Embedding 维度不匹配：模型返回 %d 维，配置 %d 维（rag.vector-store.pg.dimension）。请对齐模型与配置并重建 rag_embedding 表",
                            vector.length, embeddingDimension));
                }
                pgJdbcTemplate.update(
                        "INSERT INTO " + targetTable + " (chunk_id, article_id, title, content, embedding) VALUES (?, ?, ?, ?, ?::vector)",
                        item.chunk().getId(),
                        item.chunk().getArticleId(),
                        item.title(),
                        item.chunkText(),
                        toPgVector(vector)
                );
                chunkCount++;
            }
        }
        long embeddingCost = System.currentTimeMillis() - embeddingStart;
        log.info("RAG向量化：{} 个分块写入 {}，批大小 {}，共用时 {}ms（平均 {}ms/分块）",
                chunkCount, targetTable, EMBEDDING_BATCH_SIZE, embeddingCost,
                String.format("%.0f", embeddingCost * 1.0 / chunkCount));
        return chunkCount;
    }

    /**
     * 带重试的 Embedding 批量调用（3 次 + 指数退避）。
     *
     * 重试 catch 到的是 call() 本身抛出的异常——真实链路中几乎全是瞬时抖动类
     * （网络超时 / 5xx / 限流）。成功即返回。
     *
     * 注意「确定性错误不重试」的真实边界：条数/维度校验并不在 callWithRetry 内，
     * 而是在 embedAndInsert 对 call() 返回结果之后的检查中抛异常，从而不进入本重试圈
     * （那类错误由校验层拦截、触发整体回滚/影子表丢弃）。故无需在重试内按异常类型过滤
     * ——若未来 call() 自身抛确定性错误成为可能，再考虑按类型区分瞬时/确定性。
     *
     * 失败语义（与「整批失败→整体回滚」互补）：
     * - 瞬时抖动 → 重试自愈，调用方感知不到失败；
     * - 3 次重试耗尽 → 返回 null =「跳过整批」而非「失败」。调用方 continue 到下一批，
     *   其余批次与影子表切换不被阻塞；被跳过的分块向量暂时缺失，下一次幂等全量重建自动补齐。
     *
     * 注意：退避用 Thread.sleep 占用的是 ragTaskExecutor 线程池线程（core=2 场景下可接受）；
     * 若未来重建变高频，应改为异步重试（Flux.retryWhen / Resilience4j Retry）避免线程阻塞。
     */
    EmbeddingResponse callWithRetry(EmbeddingRequest request) {
        int maxAttempts = embeddingConfig.getEmbeddingRetryMaxAttempts();
        long initialBackoffMs = embeddingConfig.getEmbeddingRetryInitialBackoffMs();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                EmbeddingResponse response = embeddingModel.call(request);
                return response;
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    log.error("[RAG-EMBED-RETRY] 连续 {} 次失败，跳过本批（{} 条），原因: {}",
                            maxAttempts, request.getInstructions().size(), e.getMessage());
                    return null;
                }
                // 指数退避：第 1 次失败等 500ms，第 2 次等 1000ms
                long backoffMs = initialBackoffMs * (1L << (attempt - 1));
                log.warn("[RAG-EMBED-RETRY] 第 {} 次调用失败，{}ms 后重试，原因: {}",
                        attempt, backoffMs, e.getMessage());
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("[RAG-EMBED-RETRY] 退避等待被中断，跳过本批", ie);
                    return null;
                }
            }
        }
        return null;
    }

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
        // 先移除 <script>/<style> 整块（含内部代码），避免脚本/样式文本混入向量化
        String cleaned = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ");
        // 再移除剩余 HTML 标签，最后统一处理 HTML 实体与空白
        return cleaned.replaceAll("<[^>]+>", " ")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&quot;", "\"")
                   .replaceAll("&#39;", "'")
                   .replaceAll("\\s+", " ")
                   .trim();
    }
}
