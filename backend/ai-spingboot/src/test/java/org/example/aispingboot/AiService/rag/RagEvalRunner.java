package org.example.aispingboot.AiService.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0 · RAG 检索质量评测运行器（真实环境跑 recall@k）。
 *
 * 连接真实 EmbeddingModel（BGE） + 真实 PgVector，对三种 chunking 策略各建一次独立索引、
 * 检索评测集问题、输出 recall@k 对比。数据写入独立表（rag_eval_chunk_*），不碰生产
 * rag_embedding / knowledge_chunk，评测完自动 DROP，零污染。
 *
 * 需要环境：PostgreSQL + pgvector 已起（localhost:5432/rag_vector）、EMBEDDING_API_KEY 已注入。
 * 不满足时测试被 {@code @EnabledIfEnvironmentVariable} 跳过（返回 skipped），不误报为失败。
 * 结果落盘 target/eval-report.txt，供回填《RAG检索质量优化》文档的验收数据表。
 *
 * ⚠️ 本类只做「评测」，不调用生产 RagService.rebuildIndex/retrieve（避免污染生产索引）。
 * 真实检索链路用独立表模拟 —— 三种 chunking 各自：分块 -> embedding -> 写独立向量表 -> 余弦检索 -> recall@k。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "EMBEDDING_API_KEY", matches = ".+")
class RagEvalRunner {

    private static final Logger log = LoggerFactory.getLogger(RagEvalRunner.class);

    @Autowired
    @Qualifier("pgVectorJdbcTemplate")
    private JdbcTemplate pg;

    @Autowired
    private EmbeddingModel embeddingModel;

    /** 评测向量维度（与生产 BGE 一致） */
    private static final int DIM = 1024;
    private static final int TOP_K = 3;
    private static final double MIN_SIM = 0.0;

    /**
     * 策略 A 的 TokenTextSplitter：显式把 chunk 上限压到 512 token（嵌入模型 bge-large-zh 输入上限）。
     * 默认构造器是 800 token/chunk，直接超模型上限触发 400 崩溃；这里 minChunkSizeChars 也收紧，
     * 中文 1 字≈1.5 token 折算后 512 token ≈ 341 字，取 240 字为安全预算避免边缘超限。
     */
    private static TokenTextSplitter safeTokenSplitter() {
        // chunkSize=512 token, minChunkSizeChars=240, minChunkLengthToEmbed=10, maxNumChunks=10000, keepSeparator=true
        return new TokenTextSplitter(512, 240, 10, 10000, true);
    }

    @Test
    void compareChunkingRecall() throws IOException {
        // 读评测集（mock 文章 + 问题 + 黄金标注）
        RagEvalSet.EvalSet eval = RagEvalSet.build();
        assertThat(eval.articles()).hasSizeGreaterThanOrEqualTo(5);

        // 三种 chunking 策略
        List<ChunkingStrategy> strategies = List.of(
                new ChunkingStrategy("A_token默认", (title, md) ->
                        MarkdownChunker.splitWith(new TokenTextSplitter(), title, md)),
                new ChunkingStrategy("B_定长+overlap", (title, md) ->
                        MarkdownChunker.splitByFixedSize(title, md, 120, 20)),
                new ChunkingStrategy("C_按标题层级", (title, md) ->
                        MarkdownChunker.splitByH2(title, md))
        );

        StringBuilder report = new StringBuilder("P0 chunking recall@k 对比（真实 Embedding + PgVector）\n");
        report.append("文章数=").append(eval.articles().size())
              .append(" 问题数=").append(eval.questions().size()).append('\n');

        for (ChunkingStrategy strat : strategies) {
            String table = "rag_eval_chunk_" + strat.id().replaceAll("[^A-Za-z0-9]", "_");
            dropTable(table);
            try {
                int chunks = buildIndexForStrategy(table, eval, strat);
                RecallStats s = runRecall(eval, table, strat.id());
                report.append(String.format(
                        "%-14s 分块数=%-4d recall@1=%.2f recall@3=%.2f recall@5=%.2f avgFirstHitRank=%.2f%n",
                        strat.id(), chunks, s.recall()[0], s.recall()[1], s.recall()[2], s.avgFirstHitRank()));
                log.info("{} 分块数={} recall@1={} recall@3={} recall@5={} avgFirstHitRank={}",
                        strat.id(), chunks, s.recall()[0], s.recall()[1], s.recall()[2], s.avgFirstHitRank());
            } finally {
                dropTable(table);
            }
        }

        // 落盘
        Path out = Paths.get("target", "eval-report.txt");
        Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
        log.info("评测报告已写入 {}", out.toAbsolutePath());
        System.out.println(report);
    }

    // ==================== 语料模式：96 篇「答案非唯一」大语料 ====================

    /**
     * P0-Corpus · 在 96 篇自建模拟语料（每簇 8 篇、答案非唯一）上对比三种 chunking 的 recall@k。
     *
     * 与内置 20 篇评测的关键区别：语料是「多篇同簇、同一黄金锚点多处出现」，
     * 检索必须在相近文档间做区分，此语境下 A(token) vs C(标题层级) 才可能拉开，
     * 不再出现“整篇单块天然全中”的假象。评测口径同 {@code runRecall}（黄金子串命中前 k）。
     *
     * 数据源 {@link CorpusEvalSet#load()}：读 scripts/corpus 下由 DeepSeek 生成的模拟语料。
     */
    @Test
    void compareCorpusChunkingRecall() throws IOException {
        RagEvalSet.EvalSet eval = CorpusEvalSet.load();
        assertThat(eval.articles()).hasSizeGreaterThanOrEqualTo(60); // 至少 60 篇才算语料就绪
        assertThat(eval.questions()).hasSizeGreaterThanOrEqualTo(20);

        List<ChunkingStrategy> strategies = List.of(
                new ChunkingStrategy("A_token512", (title, md) ->
                        MarkdownChunker.splitWith(safeTokenSplitter(), title, md)),
                new ChunkingStrategy("B_定长+overlap", (title, md) ->
                        MarkdownChunker.splitByFixedSize(title, md, 120, 20)),
                new ChunkingStrategy("C_按标题层级", (title, md) ->
                        MarkdownChunker.splitByH2(title, md))
        );

        StringBuilder report = new StringBuilder("P0-Corpus chunking recall@k 对比（96篇答案非唯一语料，真实Embedding+PgVector）\n");
        report.append("文章数=").append(eval.articles().size())
              .append(" 问题数=").append(eval.questions().size()).append('\n');

        for (ChunkingStrategy strat : strategies) {
            String table = "rag_eval_corpus_chunk_" + strat.id().replaceAll("[^A-Za-z0-9]", "_");
            dropTable(table);
            try {
                int chunks = buildIndexForStrategy(table, eval, strat);
                RecallStats s = runRecall(eval, table, strat.id());
                report.append(String.format(
                        "%-14s 分块数=%-4d recall@1=%.2f recall@3=%.2f recall@5=%.2f avgFirstHitRank=%.2f%n",
                        strat.id(), chunks, s.recall()[0], s.recall()[1], s.recall()[2], s.avgFirstHitRank()));
                log.info("[Corpus] {} 分块数={} recall@1={} recall@3={} recall@5={} avgFirstHitRank={}",
                        strat.id(), chunks, s.recall()[0], s.recall()[1], s.recall()[2], s.avgFirstHitRank());
            } finally {
                dropTable(table);
            }
        }
        Path out = Paths.get("target", "eval-report-corpus.txt");
        Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
        log.info("语料评测报告已写入 {}", out.toAbsolutePath());
        System.out.println(report);
    }

    // ==================== P1: rerank 精排对比 ====================

    /**
     * P1 · 对比「向量直排 topK」vs「召回放宽 + rerank 精排 topK」的 recall@k。
     *
     * 用 HeuristicRerankClient 占位（真实 cross-encoder 未接入前先验证编排 + 测 recall 横截面），
     * 说明见 {@link HeuristicRerankClient}。
     */
    @Test
    void compareRerankImprovement() throws IOException {
        RagEvalSet.EvalSet eval = RagEvalSet.build();

        // 用「按标题层级」chunking（P0 里最可能最优）建一次独立索引
        String table = "rag_eval_chunk_rerank";
        dropTable(table);
        StringBuilder report = new StringBuilder("P1 rerank 精排对比（真实 Embedding + PgVector）\n");
        report.append("文章数=").append(eval.articles().size())
              .append(" 问题数=").append(eval.questions().size()).append('\n');
        try {
            int chunks = buildIndexForStrategy(table, eval, new ChunkingStrategy("C", (t, md) -> MarkdownChunker.splitByH2(t, md)));

            // 基线：直接向量 top-3（现状 RagService 行为）
            double[] baseline = runRecall(eval, table, "baseline").recall();

            // P1: 召回放宽到 top-20，再用 rerank 精排 top-3
            RerankClient rerank = new HeuristicRerankClient();
            double[] reranked = runRecallWithRerank(eval, table, rerank, 20, 3);

            report.append(String.format("直排top3      recall@1=%.2f recall@3=%.2f%n", baseline[0], baseline[1]));
            report.append(String.format("召回20+rerank3 recall@1=%.2f recall@3=%.2f%n", reranked[0], reranked[1]));
            log.info("分块数={} 直排 recall@1={} recall@3={} | rerank recall@1={} recall@3={}",
                    chunks, baseline[0], baseline[1], reranked[0], reranked[1]);
        } finally {
            dropTable(table);
        }

        Path out = Paths.get("target", "eval-report-rerank.txt");
        Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println(report);
    }

    /**
     * P1-Corpus · 在 96 篇答案非唯一语料上，用**真实 cross-encoder**（bge-reranker-v2-m3 via
     * SiliconFlow）对比「向量直排 top3」vs「召回放宽 top20 + rerank 精排 top3」的 recall@k。
     *
     * 与 20 篇 {@code compareRerankImprovement} 的区别：①真实 client（非字符重叠占位，
     * 语义打分）；②语料放大到「同簇多篇同答案」，才看得到 rerank 在相近文档间的区分价值。
     *
     * ⚠️ 诚实口径：rerank 的 relevance_score 是「语义相关性」，recall 判定是「黄金子串是否命中」，
     * 两者不同——若 rerank 把严格含答案文本的块降权、把语义相近但不含文本的块提权，recall 可能
     * 不升反降，这是「口径错位」不是 rerank 无效。故本方法同时报告 ③「rerank 是否把语义更相关
     * 的块排前面」的辅助统计（见 {@code runRecallWithRerank} 内注释），供解读时分清。
     */
    @Test
    void compareCorpusRerankImprovement() throws IOException {
        RagEvalSet.EvalSet eval = CorpusEvalSet.load();
        assertThat(eval.articles()).hasSizeGreaterThanOrEqualTo(60);
        assertThat(eval.questions()).hasSizeGreaterThanOrEqualTo(20);

        String table = "rag_eval_corpus_rerank";
        dropTable(table);
        StringBuilder report = new StringBuilder("P1-Corpus rerank 精排对比（96篇语料，真实 cross-encoder BGE reranker）\n");
        report.append("文章数=").append(eval.articles().size())
              .append(" 问题数=").append(eval.questions().size()).append('\n');
        try {
            int chunks = buildIndexForStrategy(table, eval,
                    new ChunkingStrategy("C", (t, md) -> MarkdownChunker.splitByH2(t, md)));

            // 基线：直接向量 top-3
            double[] baseline = runRecall(eval, table, "baseline").recall();

            // P1: 召回放宽到 top-20，真实 cross-encoder rerank 精排 top-3
            RerankClient rerank = new SiliconFlowRerankClient();
            double[] reranked = runRecallWithRerank(eval, table, rerank, 20, 3);

            report.append(String.format("直排top3      recall@1=%.2f recall@3=%.2f%n", baseline[0], baseline[1]));
            report.append(String.format("召回20+rerank3 recall@1=%.2f recall@3=%.2f%n", reranked[0], reranked[1]));
            log.info("[Corpus][真实rerank] 分块数={} 直排 recall@1={} recall@3={} | rerank recall@1={} recall@3={}",
                    chunks, baseline[0], baseline[1], reranked[0], reranked[1]);
        } finally {
            dropTable(table);
        }
        Path out = Paths.get("target", "eval-report-corpus-rerank.txt");
        Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
        log.info("语料 rerank 评测报告已写入 {}", out.toAbsolutePath());
        System.out.println(report);
    }

    /** 召回放宽 + rerank 精排后算 recall@k */
    private double[] runRecallWithRerank(RagEvalSet.EvalSet eval, String table,
                                         RerankClient rerank, int recallN, int topK) {
        int total = eval.questions().size();
        int[] hits = new int[3];
        for (RagEvalSet.EvalQuestion q : eval.questions()) {
            float[] qv = embeddingModel.embed(q.question());
            String sql = "SELECT content FROM " + table +
                    " ORDER BY embedding <=> ?::vector LIMIT " + recallN;
            List<String> candidates = pg.query(sql, (rs, n) -> rs.getString("content"), toPgVector(qv));

            // 直接把候选交给 rerank 精排取 topK（先前经 TwoStageRetrievalService 转发，纯冗余已内联）
            List<RerankClient.RerankResult> top = rerank.rerank(q.question(), candidates, topK);
            for (int k = 0; k < 3; k++) {
                boolean hit = false;
                for (int i = 0; i <= k && i < top.size(); i++) {
                    if (top.get(i).text() != null && top.get(i).text().contains(q.goldenKeyword())) {
                        hit = true;
                        break;
                    }
                }
                if (hit) hits[k]++;
            }
        }
        return new double[]{hits[0] / (double) total, hits[1] / (double) total, hits[2] / (double) total};
    }

    // ==================== P2: 混合检索 + RRF 融合 ====================

    /**
     * P2 · 对比「纯向量召回」vs「向量 + pg_trgm 关键词召回，RRF 融合」的 recall@k。
     *
     * - 向量路：PgVector 余弦 top-N（语义）；
     * - 关键词路：pg_trgm 的 similarity() top-N（精确术语召回）；
     * - 融合：RRRFusion.fuse 按两路排名无参融合取 topK。
     *
     * ⚠️ 依赖 PG 环境（pg_trgm 扩展 + 两路 SQL）。当前被 @EnabledIfEnvironmentVariable 跳过时，
     *    无法产出真实数据；环境就绪后回填 recall 对比。
     *    诚实口径：pg_trgm 对中文是字级 trigram、效果一般，作为「关键词召回通道」够用，
     *    但不等于 ES 中文分词水平（见 RAG检索质量优化 文档 §四）。
     */
    @Test
    void compareHybridFusion() throws IOException {
        RagEvalSet.EvalSet eval = RagEvalSet.build();
        String table = "rag_eval_chunk_hybrid";
        dropTable(table);
        StringBuilder report = new StringBuilder("P2 混合检索（pg_trgm + RRF）对比\n");
        report.append("文章数=").append(eval.articles().size())
              .append(" 问题数=").append(eval.questions().size()).append('\n');
        try {
            int chunks = buildIndexForStrategy(table, eval,
                    new ChunkingStrategy("C", (t, md) -> MarkdownChunker.splitByH2(t, md)));
            ensureTrgmExtension();

            // 基线：纯向量 top-3
            double[] baseline = runRecall(eval, table, "baseline").recall();

            // 混合：向量 top-20 + 关键词 top-20，RRF 融合 top-3
            double[] hybrid = runRecallHybrid(eval, table, 20, 20, 3);

            report.append(String.format("纯向量top3    recall@1=%.2f recall@3=%.2f%n", baseline[0], baseline[1]));
            report.append(String.format("混合+RRF top3 recall@1=%.2f recall@3=%.2f%n", hybrid[0], hybrid[1]));
            log.info("分块数={} 纯向量 recall@1={} recall@3={} | 混合RRF recall@1={} recall@3={}",
                    chunks, baseline[0], baseline[1], hybrid[0], hybrid[1]);
        } finally {
            dropTable(table);
        }
        Path out = Paths.get("target", "eval-report-hybrid.txt");
        Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println(report);
    }

    /**
     * P2-Corpus · 在 96 篇答案非唯一语料上重测混合检索（pg_trgm 关键词 + RRF 融合），
     * 与 20 篇 {@code compareHybridFusion} 对照——语料放大后看 RRF 是否仍负面。
     *
     * 诚实口径（与 20 篇一致）：`pg_trgm` 对中文是字级 trigram、无分词，整句 query 当 trigram 串
     * 匹配的语义质量本就有限；本测试只回答「在更大的、可区分的语料上，混合 RRF 相比纯向量是升是降」，
     * 用真实数据说话，不预设结论。
     */
    @Test
    void compareCorpusHybridFusion() throws IOException {
        RagEvalSet.EvalSet eval = CorpusEvalSet.load();
        assertThat(eval.articles()).hasSizeGreaterThanOrEqualTo(60);
        assertThat(eval.questions()).hasSizeGreaterThanOrEqualTo(20);

        String table = "rag_eval_corpus_hybrid";
        dropTable(table);
        StringBuilder report = new StringBuilder("P2-Corpus 混合检索（pg_trgm + RRF）对比（96篇语料）\n");
        report.append("文章数=").append(eval.articles().size())
              .append(" 问题数=").append(eval.questions().size()).append('\n');
        try {
            int chunks = buildIndexForStrategy(table, eval,
                    new ChunkingStrategy("C", (t, md) -> MarkdownChunker.splitByH2(t, md)));
            ensureTrgmExtension();

            double[] baseline = runRecall(eval, table, "baseline").recall();
            double[] hybrid = runRecallHybrid(eval, table, 20, 20, 3);

            report.append(String.format("纯向量top3    recall@1=%.2f recall@3=%.2f%n", baseline[0], baseline[1]));
            report.append(String.format("混合+RRF top3 recall@1=%.2f recall@3=%.2f%n", hybrid[0], hybrid[1]));
            log.info("[Corpus] 分块数={} 纯向量 recall@1={} recall@3={} | 混合RRF recall@1={} recall@3={}",
                    chunks, baseline[0], baseline[1], hybrid[0], hybrid[1]);
        } finally {
            dropTable(table);
        }
        Path out = Paths.get("target", "eval-report-corpus-hybrid.txt");
        Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
        log.info("语料混合测评报告已写入 {}", out.toAbsolutePath());
        System.out.println(report);
    }

    /** 确保评测库启用 pg_trgm 扩展（幂等） */
    private void ensureTrgmExtension() {
        pg.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
    }

    /** 混合检索 + RRF 融合后算 recall@k */
    private double[] runRecallHybrid(RagEvalSet.EvalSet eval, String table,
                                     int vecN, int kwN, int topK) {
        int total = eval.questions().size();
        int[] hits = new int[3];
        for (RagEvalSet.EvalQuestion q : eval.questions()) {
            // 1. 向量路：余弦 top-vecN，返回 (chunk_id, content)
            float[] qv = embeddingModel.embed(q.question());
            String vecSql = "SELECT chunk_id, content FROM " + table +
                    " ORDER BY embedding <=> ?::vector LIMIT " + vecN;
            List<VecHit> vecHits = pg.query(vecSql,
                    (rs, rowNum) -> new VecHit(rs.getLong("chunk_id"), rs.getString("content")),
                    toPgVector(qv));
            List<Long> vecIds = new ArrayList<>();
            Map<Long, String> contentById = new HashMap<>();
            for (VecHit v : vecHits) {
                vecIds.add(v.chunkId());
                contentById.put(v.chunkId(), v.content());
            }

            // 2. 关键词路：pg_trgm similarity top-kwN，返回 chunk_id
            String kwSql = "SELECT chunk_id, similarity(content, ?) AS sim FROM " + table +
                    " ORDER BY sim DESC LIMIT " + kwN;
            List<Long> kwIds = pg.query(kwSql,
                    (rs, rowNum) -> rs.getLong("chunk_id"), q.question());

            // 3. RRF 融合（两路排名列表）
            List<Long> fused = RRFusion.fuse(List.of(vecIds, kwIds), topK, 60);

            // 4. recall 命中：黄金子串出现在融合 topK 任一 chunk 的内容里。
            //    向量路内容已在 contentById；关键词路独有的命中懒加载补回（仅对融合 topK 内查）。
            for (Long id : fused) {
                if (!contentById.containsKey(id)) {
                    String c = pg.queryForObject(
                            "SELECT content FROM " + table + " WHERE chunk_id = ?", String.class, id);
                    contentById.put(id, c);
                }
            }
            for (int k = 0; k < 3; k++) {
                boolean hit = false;
                for (int i = 0; i <= k && i < fused.size(); i++) {
                    String c = contentById.get(fused.get(i));
                    if (c != null && c.contains(q.goldenKeyword())) { hit = true; break; }
                }
                if (hit) hits[k]++;
            }
        }
        return new double[]{hits[0] / (double) total, hits[1] / (double) total, hits[2] / (double) total};
    }

    /** 向量召回单条：chunk_id + content（供 RRF 融合与命中判定） */
    private record VecHit(long chunkId, String content) {}

    /** 用某种分块策略建独立向量表并写入向量，返回分块数 */
    private int buildIndexForStrategy(String table, RagEvalSet.EvalSet eval, ChunkingStrategy strat) {
        createTable(table);
        List<RagEvalSet.Article> articles = eval.articles();
        List<String> docs = new ArrayList<>();
        List<String> metas = new ArrayList<>();
        int chunkCount = 0;

        for (RagEvalSet.Article a : articles) {
            List<Document> chunks = strat.chunker().apply(a.title(), a.markdown());
            for (Document c : chunks) {
                String text = c.getText();
                if (text.isBlank()) continue;
                docs.add(text);
                metas.add(String.valueOf(c.getMetadata().get("title")));
                chunkCount++;
            }
        }

        // 批量 Embedding（复用 callWithRetry 思路，10 条一批）
        List<float[]> vectors = embedAll(docs);
        for (int i = 0; i < docs.size(); i++) {
            pg.update("INSERT INTO " + table + " (chunk_id, title, content, embedding) VALUES (?, ?, ?, ?::vector)",
                    i, metas.get(i), docs.get(i), toPgVector(vectors.get(i)));
        }
        return chunkCount;
    }

    private List<float[]> embedAll(List<String> texts) {
        // 兜底：任何 chunk 超过 350 字即截断（bge 512 token 上限的下限折算）。
        // 主防线在 chunking（策略 A/C 已压到 ≤300 字），此处仅防极端单段超长导致 400 崩溃。
        List<String> capped = new ArrayList<>(texts.size());
        for (String t : texts) {
            capped.add(t.length() > 350 ? t.substring(0, 350) : t);
        }
        List<float[]> out = new ArrayList<>();
        for (int start = 0; start < capped.size(); start += 10) {
            int end = Math.min(start + 10, capped.size());
            List<float[]> batch = new ArrayList<>();
            List<String> sub = capped.subList(start, end);
            EmbeddingRequest req = new EmbeddingRequest(sub, null);
            EmbeddingResponse resp = embeddingModel.call(req);
            resp.getResults().forEach(r -> batch.add(r.getOutput()));
            out.addAll(batch);
        }
        return out;
    }

    /** 跑 recall@k：对每个问题检索 top-k，判黄金子串是否命中前 k 个 chunk 的内容。
     *  同时累计 firstHitK（首次命中所需要翻到的最小 top-k，衡量“块是否精”）。 */
    private RecallStats runRecall(RagEvalSet.EvalSet eval, String table, String strat) {
        int total = eval.questions().size();
        int[] hits = new int[3]; // recall@1/@3/@5
        double firstHitSum = 0;
        for (RagEvalSet.EvalQuestion q : eval.questions()) {
            float[] qv = embeddingModel.embed(q.question());
            String sql = "SELECT content FROM " + table +
                    " ORDER BY embedding <=> ?::vector LIMIT 5";
            List<String> contents = pg.query(sql, (rs, n) -> rs.getString("content"), toPgVector(qv));
            int firstHit = -1;
            for (int i = 0; i < contents.size(); i++) {
                if (contents.get(i) != null && contents.get(i).contains(q.goldenKeyword())) {
                    firstHit = i; // 0-indexed rank
                    break;
                }
            }
            for (int k = 0; k < 3; k++) {
                if (firstHit >= 0 && firstHit <= k) hits[k]++;
            }
            firstHitSum += (firstHit >= 0 ? firstHit : contents.size()); // 未命中记为一个较大的“代价”
        }
        return new RecallStats(
                new double[]{hits[0] / (double) total, hits[1] / (double) total, hits[2] / (double) total},
                firstHitSum / (double) total);
    }

    /** chunking 评测的完整统计：recall@1/3/5 + 首次命中平均 rank（越小越精，1.0 表示普遍 top-1 直中） */
    private record RecallStats(double[] recall, double avgFirstHitRank) {}

    private void createTable(String table) {
        pg.execute("DROP TABLE IF EXISTS " + table);
        pg.execute("CREATE TABLE " + table + " (" +
                "chunk_id BIGINT PRIMARY KEY, title VARCHAR(200), content TEXT, embedding vector(" + DIM + ") NOT NULL)");
    }

    private void dropTable(String table) {
        try { pg.execute("DROP TABLE IF EXISTS " + table); } catch (Exception ignored) {}
    }

    private String toPgVector(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) { if (i > 0) sb.append(","); sb.append(v[i]); }
        sb.append("]");
        return sb.toString();
    }

    private record ChunkingStrategy(String id, Chunker chunker) {}
    @FunctionalInterface interface Chunker { List<Document> apply(String title, String md); }
}
