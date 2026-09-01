package org.example.aispingboot.AiService.rag;

import java.util.List;

/**
 * 两级检索流水线（P1）—— 召回放宽 + Rerank 精排的编排服务。
 *
 * 背景：一级向量检索（双塔余弦，粗排）的分数是「query 与 chunk 各自编码再算距离」，判断
 * 「最终相关性」天生不准（高分误报）。标准解法是再加一次 cross-encoder 精排（粗排控召回、
 * 精排控准确）。本类把「放宽召回数量 → 精排取 topK」这层编排抽出来，与具体检索引擎解耦。
 *
 * 结构：
 * - 一级召回：由调用方提供（内部是 PgVector 余弦 top-N，本类不关心），关键在「放宽召回数量
 *   （recallN > topK）」以提高召回率、给精排更多候选；
 * - 二级精排：注入 {@link RerankClient}，对召回候选逐对精排取 topK。
 *
 * 为什么独立成类、不写进 RagService：
 * - RagService.retrieve 目前是「直排 topK」，改两级需在 retrieve 里加 rerank 调用，属生产行为变更；
 * - 本类把两级编排独立成可测试组件，评测(RagEvalRunner)可注入 mock 验证「召回放宽+rerank 是否提升 recall」，
 *   真实上线时再接入 RagService.retrieve(替换直排)，对现有链路无破坏。
 *
 * 线程安全：本类无状态（只协作 RerankClient），可安全单例复用。
 */
public class TwoStageRetrievalService {

    private final RerankClient rerankClient;

    /** 一级召回数量（放宽到比最终 topK 大，保证召回率的前提下给精排更多候选） */
    private final int recallN;

    public TwoStageRetrievalService(RerankClient rerankClient, int recallN) {
        this.rerankClient = rerankClient;
        this.recallN = recallN;
    }

    /**
     * 迭代精排：对候选拆成「全部一锅」精排取 topK。
     * 极简实现：直接把召回候选全部交给 RerankClient 精排一次取 topK（跨-编码逐对打分）。
     * 适合候选量小（≤TOP_K 2020 量级）的场景；候选极大时可分桶 + 归并（未来扩展，当前不做）。
     *
     * @param queryText  检索词（精排需要 query，与一级召回的 query 相同）
     * @param candidates 一级召回候选（原文列表，保序）
     * @param topK       精排后保留条数
     * @return 精排后 topK 的 {@link RerankResult}（含原始 index + 文本 + 分）
     */
    public java.util.List<RerankClient.RerankResult> rerankDocuments(String queryText,
                                                                      List<String> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        return rerankClient.rerank(queryText, candidates, topK);
    }

    public int getRecallN() {
        return recallN;
    }
}
