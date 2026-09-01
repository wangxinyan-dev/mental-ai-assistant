package org.example.aispingboot.AiService.rag;

import java.util.List;

/**
 * Rerank（重排序）客户端接口 —— P1 两级检索的「精排」接缝。
 *
 * 定位：一级召回（向量相似度粗排，控制「广」）之后，对候选做 cross-encoder 精排（控制「准」），
 * 返回与 query 最相关的 topK 个，覆盖「双塔粗排高分误报」的缺陷。
 *
 * 设计说明（为何是接口）：
 * - 真实实现是外部 HTTP 调用（如 SiliconFlow 的 BAAI/bge-reranker-v2-m3 /v1/rerank），
 *   契约当前无法在本环境实调核实，故不写死在本接口内；实现方自行确认端点与字段。
 * - 本接口只定义「输入 query + 候选 documents、输出分数 topK」的抽象，业务与实现解耦，
 *   评测（RagEvalRunner）可注入 mock/随机实现先验证流水线，再替换为真实 client。
 * - 保持与 {@code RagService.retrieve} 兼容：候选来自 PgVector 余弦检索结果（Top-20）。
 *
 * 方法语义：
 * @param query      用户检索词
 * @param documents  一级召回候选（原文，非向量），保序传入
 * @param topK       精排后保留的条数
 * @return 精排后的 topK 条 {@link RerankResult}，按相关性**降序**，且可回溯每个元素在原始
 *         候选里的 index（用于和原始 chunk 内容对应）；传入为空或 topK<=0 时返回空列表。
 *         实现必须保证：返回至多 topK 条、不重复；具体排序依据由实现决定。
 */
public interface RerankClient {

    List<RerankResult> rerank(String query, List<String> documents, int topK);

    /**
     * 单条精排结果：重排后的条目 + 其在原始候选中的位置，便于调用方回映到 chunk。
     *
     * @param originalIndex 该条在 {@code documents} 中的原始下标
     * @param text          该条原文（冗余携带，避免调用方再回查）
     * @param score         精排相关性分（越大越相关；不同实现的分数含义可不同）
     */
    record RerankResult(int originalIndex, String text, double score) {}
}
