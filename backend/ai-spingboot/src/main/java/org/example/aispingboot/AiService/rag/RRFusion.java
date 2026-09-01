package org.example.aispingboot.AiService.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF（Reciprocal Rank Fusion）融合器 —— P2 混合检索的核心算法（纯 Java、可单测）。
 *
 * 背景：混合检索 = 向量召回（稠密，语义）+ 关键词召回（稀疏，精确术语）两路结果取并集，
 * 但两路分数尺度不可比（余弦相似度 vs trigram 相似度），不能直接相加。RRF 用「排名」替代
 * 「分数」做无参融合：一个文档在两路列表里的名次越靠前，融合分越高。
 *
 * 公式：RRF_score(d) = Σ_{list ∈ {向量, 关键词}} 1 / (k + rank_list(d))
 *   - k 为柔性常数（默认 60），k 越大越平均、越小越强调榜首；
 *   - 文档在某一路未命中则不贡献该路的倒数（即只按命中的列表累加）；
 *   - 无需调权重、可解释、对分数尺度不敏感，是混合检索融合的社区标准做法。
 *
 * 典型用途：把「PgVector 余弦 top-N」和「pg_trgm 关键词 top-N」两路 result 融合成最终 top-K。
 * 本类只做融合，不负责召回（召回由调用方完成）。
 *
 * 线程安全：无状态，可安全复用于并发（内部局部变量）。
 */
public class RRFusion {

    /** RRF 柔性常数默认值（社区常用） */
    public static final int DEFAULT_K = 60;

    /**
     * 融合多路倒序排名列表，返回 Top-K 文档（按融合降序）。
     *
     * @param rankedLists 各路的文档 ID 列表（**按相关度降序**，即 list.get(0) 是该路最相关）。
     *                    用文档 ID（Long）标识，如 chunk_id；文档须在各路内部互不重复。
     * @param topK        返回的文档数
     * @param k           RRF 常数（建议 60；传 0 或负用默认）
     * @return 融合后按降序的文档 ID 列表（长度 ≤ topK）
     */
    public static List<Long> fuse(List<List<Long>> rankedLists, int topK, int k) {
        if (rankedLists == null || rankedLists.isEmpty() || topK <= 0) {
            return List.of();
        }
        int kk = k <= 0 ? DEFAULT_K : k;

        // 1. 设 60 的 rank 起点：名次从 1 开始（榜首 rank=1）
        Map<Long, Double> scores = new HashMap<>();
        for (List<Long> list : rankedLists) {
            if (list == null) continue;
            for (int rank = 0; rank < list.size(); rank++) {
                Long docId = list.get(rank);
                if (docId == null) continue;
                // rank 从 0 起，但 RRF 用 1 起的名次：rank+1
                scores.merge(docId, 1.0 / (kk + rank + 1), Double::sum);
            }
        }

        // 2. 按融合分降序，取 topK
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** 便捷重载：用默认 K 融合。 */
    public static List<Long> fuse(List<List<Long>> rankedLists, int topK) {
        return fuse(rankedLists, topK, DEFAULT_K);
    }

    /** 从融合结果反查「某个文档 id 是否在 topK 内」（供 recall 命中判定）。 */
    public static boolean contains(List<Long> fused, Long docId) {
        return fused != null && fused.contains(docId);
    }
}
