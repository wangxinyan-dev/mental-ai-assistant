package org.example.aispingboot.AiService.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SiliconFlow rerank 客户端（P1 真实实现骨架）。
 *
 * 调用 SiliconFlow 的 rerank API（OpenAI 不兼容、独立端点），对候选做 cross-encoder 精排。
 * 接入点：把本类的实例注入 {@link TwoStageRetrievalService}（见其 javadoc），替换 {@link HeuristicRerankClient} 占位。
 *
 * == 契约集中声明（⚠️ 使用前必须按实际 API 核实一次） ==
 * 下述 URL / 请求字段 / 响应字段基于 SiliconFlow rerank 的公开 API 形态编写，但**本环境无法实调验证**：
 *   - 端点：POST {base-url}/v1/rerank（base-url 默认 https://api.siliconflow.cn）
 *   - 请求体：
 *     {
 *       "model": "BAAI/bge-reranker-v2-m3",
 *       "query": "<用户问题>",
 *       "documents": ["<候选1>", "<候选2>", ...],
 *       "top_n": 3
 *     }
 *   - 响应体：{ "results": [ {"index": 0, "relevance_score": 0.9, "document": "..."}, ... ] }
 *     其中 results 已按 relevance_score 降序。
 * 若你的 key 对应的 rerank 端点/字段名不同，只改下面的常量即可，方法与调用方无需变动。
 *
 * 鉴权：请求头 Authorization: Bearer <EMBEDDING_API_KEY>（与 embedding 同一家 / 同一 key 域）。
 * 可用性：SiliconFlow 提供 bge-reranker-v2-m3 的 rerank 接口；若你的套餐未开通，会返回错误，请更换可用模型名。
 *
 * 错误处理：HTTP 非 2xx 抛 IllegalStateException（由调用方决定降级）；解析失败同样抛异常。
 * 线程安全：无状态，可并发复用（RestClient 线程安全）。
 */
public class SiliconFlowRerankClient implements RerankClient {

    private static final Logger log = LoggerFactory.getLogger(SiliconFlowRerankClient.class);

    /** rerank 模型名 —— 核实：是否已在你 key 的套餐开通 */
    public static final String MODEL = "BAAI/bge-reranker-v2-m3";
    /** 默认 base-url 与 rerank 相对路径 —— 核实 */
    public static final String DEFAULT_BASE_URL = "https://api.siliconflow.cn";
    private static final String RERANK_PATH = "/v1/rerank";

    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    /**
     * @param apiKey  SiliconFlow API key（一般与 embedding 同一个 EMBEDDING_API_KEY）
     * @param baseUrl 默认取 {@link #DEFAULT_BASE_URL}
     */
    public SiliconFlowRerankClient(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("SiliconFlowRerankClient 需要 apiKey");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topK) {
        if (query == null || documents == null || documents.isEmpty() || topK <= 0) {
            return List.of();
        }

        // 1. 组请求体（字段名需核实，见类注释）
        Map<String, Object> body = Map.of(
                "model", MODEL,
                "query", query,
                "documents", documents,
                "top_n", topK
        );

        String responseJson;
        try {
            responseJson = org.springframework.web.client.RestClient.create(baseUrl)
                    .post()
                    .uri(RERANK_PATH)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("[RAG-RERANK] 调用失败（可能未开通 rerank 或端点/字段不符，见 SiliconFlowRerankClient 类注释）: {}",
                    e.getMessage());
            throw new IllegalStateException("rerank 调用失败: " + e.getMessage(), e);
        }

        // 2. 解析响应 —— results 已降序，index 对应 documents 下标
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                log.warn("[RAG-RERANK] 响应缺少 results 数组，原样保留候选顺序返回前 topK（降级）");
                return fallbackByOrder(documents, topK);
            }
            List<RerankResult> out = new ArrayList<>(topK);
            for (JsonNode node : results) {
                int idx = node.path("index").asInt(-1);
                double score = node.path("relevance_score").asDouble(0.0);
                if (idx < 0 || idx >= documents.size()) {
                    continue; // 越界的 index 忽略（契约不符时降级跳过单条）
                }
                out.add(new RerankResult(idx, documents.get(idx), score));
                if (out.size() >= topK) break;
            }
            return out.isEmpty() ? fallbackByOrder(documents, topK) : out;
        } catch (Exception e) {
            log.warn("[RAG-RERANK] 解析失败: {}", e.getMessage());
            return fallbackByOrder(documents, topK);
        }
    }

    /** 解析失败/字段不符时的保守降级：按候选原始顺序取前 topK（虽不精排但也保证不空）。 */
    private List<RerankResult> fallbackByOrder(List<String> documents, int topK) {
        List<RerankResult> out = new ArrayList<>(topK);
        for (int i = 0; i < Math.min(topK, documents.size()); i++) {
            out.add(new RerankResult(i, documents.get(i), 0.0));
        }
        return out;
    }
}
