package org.example.aispingboot.AiService.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 启发式 Rerank 实现（P1 占位 / mock，测试用，不入生产）。
 *
 * 作用：在真实 cross-encoder rerank 未接入前，给两级检索流水线一个「能跑」的占位实现，
 * 让 RagEvalRunner 可以先验证「召回放宽 + 重排取 topK」的编排正确性（含 recall 测算）。
 *
 * 打分启发式：候选文本与 query 的「共现字符重叠」越多越相关 —— 粗糙但零依赖、可复现。
 * 计算简单共现 = 候选与 query 去重字符集合的交集大小；再按交集降序。
 *
 * ⚠️ 这是评测占位，不是生产重排质量；真实上线时应替换为 cross-encoder（如 bge-reranker-v2-m3），
 * 见 RerankClient 的接口注释。用它对 recall 的「横向相对变化」做逻辑验证可行，
 * 但不能作为「rerank 提升 recall」的真实证据（真实证据需 cross-encoder 实现 + 真实数据）。
 */
public class HeuristicRerankClient implements RerankClient {

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topK) {
        if (documents == null || documents.isEmpty() || topK <= 0 || query == null) {
            return List.of();
        }
        List<RerankResult> scored = new ArrayList<>(documents.size());
        var qChars = query.chars().mapToObj(c -> (char) c).collect(java.util.stream.Collectors.toSet());
        for (int i = 0; i < documents.size(); i++) {
            String d = documents.get(i);
            long overlap = d.chars().mapToObj(c -> (char) c).distinct()
                    .filter(qChars::contains).count();
            scored.add(new RerankResult(i, d, overlap));
        }
        scored.sort(Comparator.comparingDouble(RerankResult::score).reversed());
        int n = Math.min(topK, scored.size());
        return new ArrayList<>(scored.subList(0, n));
    }
}
