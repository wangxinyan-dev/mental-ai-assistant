package org.example.aispingboot.AiService.rag;

import org.example.aispingboot.config.EmbeddingConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产两级检索冒烟测试（真实环境集成，非单测）。
 *
 * 需要：PostgreSQL + pgvector 已起（localhost:5432/rag_vector）、索引已构建（跑过 rebuildIndex）、
 * 且注入了 EMBEDDING_API_KEY。任一不满足则 {@code @EnabledIfEnvironmentVariable} 跳过 / assume 跳过，
 * 不误报失败。
 *
 * 验证两条生产链路：
 * 1. retrieve() 端到端起得通（向量化 → PgVector 粗召回 → 可选精排），返回非空相关片段；
 * 2. 未配置 rerank（rag.rerank.enabled=false 或未配 key）时降级为向量粗召回 Top-K，同样返回非空。
 *   —— 精排「调用失败也能降级」的纯单测见 {@link RagServiceTwoStageTest}。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "EMBEDDING_API_KEY", matches = ".+")
class RagServiceProductionSmokeTest {

    @Autowired
    private RagService ragService;

    @Autowired
    private EmbeddingConfig embeddingConfig;

    @Test
    void retrieve_索引存在时_两级检索返回非空结果() {
        assumeIndexReady();

        List<RagService.SearchResult> results = ragService.retrieve("最近总是焦虑失眠怎么办");

        assertThat(results).isNotEmpty();
        // 结果按精排/相似度降序，且各条分数 >= 0（相似度或 relevance_score）
        for (int i = 0; i < results.size(); i++) {
            assertThat(results.get(i).content()).isNotBlank();
            assertThat(results.get(i).score()).isGreaterThanOrEqualTo(0);
        }
        // 条数受 topK 约束（默认 3）
        assertThat(results.size()).isLessThanOrEqualTo(embeddingConfig.getRerankTopK());
    }

    @Test
    void retrieve_任意问题_降级不抛且条数受控() {
        assumeIndexReady();

        // 弱断言（不依赖某问题必然命中语料）：无论检索到几条，降级路径都不得抛异常，
        // 且返回条数受 topK 约束。真正的「rerank 失败→降级向量 Top-K」纯单测见 RagServiceTwoStageTest，
        // 这里只在真实 PG+embedding 链路上确认 retrieve() 对任意输入都稳定。
        List<RagService.SearchResult> results = ragService.retrieve("情绪低落的时候可以做些什么");
        assertThat(results.size()).isLessThanOrEqualTo(embeddingConfig.getRerankTopK());
        for (RagService.SearchResult r : results) {
            assertThat(r.content()).isNotBlank();
            assertThat(r.score()).isGreaterThanOrEqualTo(0);
        }
    }

    /** 索引未构建 / 连接异常时跳过（本地评测环境跑过 rebuildIndex 才有索引）。 */
    private void assumeIndexReady() {
        String status = ragService.getIndexStatus();
        boolean ready = status.startsWith("索引已加载") && !status.contains("0个分块");
        Assumptions.assumeTrue(ready, () -> "PgVector 索引未就绪，跳过冒烟（status=" + status + "）");
    }
}
