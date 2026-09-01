package org.example.aispingboot.AiService.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1　两级检索（Rerank）编排的单元测试（纯 JUnit，无 DB / 无外部 API）。
 *
 * 验证：
 *  - HeuristicRerankClient 按共现重叠打分、取 topK、保序、保留原始 index。
 *
 * ⚠️ 本测试只验证「HeuristicRerankClient 打分逻辑」，不证明「rerank 提升 recall」——那是 RagEvalRunner 跑真实数据的事。
 * （两级检索的编排/降级逻辑由 RagServiceTwoStageTest 覆盖。）
 */
class RerankClientTest {

    @Test
    void rerank_按共现打分取topK并保留原始索引() {
        HeuristicRerankClient client = new HeuristicRerankClient();
        String query = "焦虑 缓解 方法";
        List<String> docs = List.of(
                "这是一段关于睡眠改善的放松技巧",   // 与 query 共现少
                "缓解焦虑的认知行为方法 CBT",       // 共现多（焦虑/缓解/方法）
                "正念呼吸放松训练"                  // 共现中等
        );

        List<RerankClient.RerankResult> top2 = client.rerank(query, docs, 2);

        // 取 top2，且降序（第一个分数 >= 第二个）
        assertThat(top2).hasSize(2);
        assertThat(top2.get(0).score()).isGreaterThanOrEqualTo(top2.get(1).score());
        // 最高分应该是「缓解焦虑的认知行为方法 CBT」（index=1）
        assertThat(top2.get(0).originalIndex()).isEqualTo(1);
        assertThat(top2.get(0).text()).isEqualTo(docs.get(1));
    }

    @Test
    void rerank_topK大于候选数_返回全部() {
        HeuristicRerankClient client = new HeuristicRerankClient();
        List<RerankClient.RerankResult> r = client.rerank("测试", List.of("a", "b"), 10);
        assertThat(r).hasSize(2);
    }

    @Test
    void rerank_空输入安全返回空() {
        HeuristicRerankClient client = new HeuristicRerankClient();
        assertThat(client.rerank("q", List.of(), 3)).isEmpty();
        assertThat(client.rerank("q", null, 3)).isEmpty();
        assertThat(client.rerank(null, List.of("x"), 3)).isEmpty();
        assertThat(client.rerank("q", List.of("x"), 0)).isEmpty();
    }
}
