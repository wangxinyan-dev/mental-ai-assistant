package org.example.aispingboot.AiService.rag;

import org.example.aispingboot.config.EmbeddingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * RagService 两级检索第二级（secondStageRank）的降级/精排行为测试（纯单测，无 DB / 无 Spring 上下文）。
 *
 * 验证生产链路的两条硬约束：
 * 1. 未配置 rerank（ObjectProvider 返回 null）→ 降级为「候选原始顺序取前 Top-K」，不空、不抛；
 * 2. rerank 调用抛异常 → 同样降级，绝不因精排故障让检索空返回。
 *
 * 另覆盖：rerank 正常重排（按 originalIndex 回映到候选）、候选为空 / 候选不足 Top-K 的快路径。
 * 沿用 {@link RagServiceRetryTest} 的字段反射注入惯例（避开 @SpringBootTest 与真实 DB）。
 */
class RagServiceTwoStageTest {

    private RagService ragService = new RagService();
    private EmbeddingConfig embeddingConfig;

    private static final int TOP_K = 3;

    @BeforeEach
    void setUp() {
        embeddingConfig = new EmbeddingConfig();
        setField(embeddingConfig, "rerankTopK", TOP_K);
        setField(ragService, "embeddingConfig", embeddingConfig);
    }

    /** 候选按向量相似度降序排列（模拟 PgVector 返回），content 即 title 以便断言可读。 */
    private List<RagService.SearchResult> candidates(int n) {
        var list = new java.util.ArrayList<RagService.SearchResult>();
        for (int i = 0; i < n; i++) {
            list.add(new RagService.SearchResult("标题" + i, "片段" + i, 1.0 - i * 0.1));
        }
        return list;
    }

    private void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("反射设置字段失败: " + name, e);
        }
    }

    /** 构造返回固定实例（可为 null，模拟"未配置 rerank 的 bean"）的 ObjectProvider。 */
    private ObjectProvider<RerankClient> providerOf(RerankClient instance) {
        return new ObjectProvider<>() {
            @Override
            public RerankClient getObject() {
                return instance;
            }

            @Override
            public RerankClient getObject(Object... args) {
                return instance;
            }

            @Override
            public RerankClient getIfAvailable() {
                return instance;
            }

            @Override
            public RerankClient getIfUnique() {
                return instance;
            }
        };
    }

    @Test
    void 未配置rerank_降级为候选原始顺序取TopK() {
        setField(ragService, "rerankClientProvider", providerOf(null));

        List<RagService.SearchResult> out = ragService.secondStageRank("焦虑", candidates(10));

        // 降级=直接取前 TopK（候选本就按向量相似度降序），不空、不抛
        assertThat(out).hasSize(TOP_K);
        assertThat(out.get(0).content()).isEqualTo("片段0");
        assertThat(out.get(2).content()).isEqualTo("片段2");
    }

    @Test
    void rerank调用抛异常_降级为向量粗召回TopK_不抛出() {
        RerankClient explosive = (q, docs, k) -> {
            throw new IllegalStateException("rerank 服务不可用");
        };
        setField(ragService, "rerankClientProvider", providerOf(explosive));

        assertThatCode(() -> {
            List<RagService.SearchResult> out = ragService.secondStageRank("焦虑", candidates(10));
            assertThat(out).hasSize(TOP_K);
            assertThat(out.get(0).content()).isEqualTo("片段0"); // 降级=原始顺序前 TopK
        }).doesNotThrowAnyException();
    }

    @Test
    void rerank正常_按originalIndex回映到候选并按分数降序() {
        RerankClient client = (q, docs, k) -> List.of(
                new RerankClient.RerankResult(5, docs.get(5), 0.99),
                new RerankClient.RerankResult(1, docs.get(1), 0.50),
                new RerankClient.RerankResult(8, docs.get(8), 0.30)
        );
        setField(ragService, "rerankClientProvider", providerOf(client));

        List<RagService.SearchResult> out = ragService.secondStageRank("焦虑", candidates(10));

        assertThat(out).hasSize(TOP_K);
        assertThat(out.get(0).content()).isEqualTo("片段5");
        assertThat(out.get(1).content()).isEqualTo("片段1");
        assertThat(out.get(2).content()).isEqualTo("片段8");
        // score 被换成精排 relevance_score
        assertThat(out.get(0).score()).isEqualTo(0.99);
    }

    @Test
    void rerank返回空_降级为向量粗召回TopK() {
        RerankClient empty = (q, docs, k) -> List.of();
        setField(ragService, "rerankClientProvider", providerOf(empty));

        List<RagService.SearchResult> out = ragService.secondStageRank("焦虑", candidates(10));
        assertThat(out).hasSize(TOP_K);
    }

    @Test
    void 候选为空_直接返回空_不调rerank() {
        setField(ragService, "rerankClientProvider", providerOf((RerankClient) (q, d, k) -> {
            throw new AssertionError("不应调用 rerank");
        }));

        List<RagService.SearchResult> out = ragService.secondStageRank("焦虑", candidates(0));
        assertThat(out).isEmpty();
    }

    @Test
    void 候选不足TopK_全量返回_不调rerank() {
        setField(ragService, "rerankClientProvider", providerOf((RerankClient) (q, d, k) -> {
            throw new AssertionError("候选不足 Top-K 时无需精排");
        }));

        List<RagService.SearchResult> out = ragService.secondStageRank("焦虑", candidates(2));
        assertThat(out).hasSize(2);
    }
}
