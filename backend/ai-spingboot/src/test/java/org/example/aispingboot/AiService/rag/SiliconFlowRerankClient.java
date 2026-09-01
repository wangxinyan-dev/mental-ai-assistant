package org.example.aispingboot.AiService.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 真实 cross-encoder Rerank 客户端（P1 落地，SiliconFlow 兼容 OpenAI 契约）。
 *
 * 端点契约（2026-09-01 实测，见 RagEvalRunner 的 P1 语料评测）：
 * <pre>
 *   POST {base-url}/v1/rerank
 *   Authorization: Bearer {api-key}
 *   body: { "model": "BAAI/bge-reranker-v2-m3",
 *           "query": "...", "documents": ["..."], "top_n": N, "return_documents": true }
 *   响应 results[]: { "index": i, "document": {"text": "..."}, "relevance_score": x }  // 已按相关度降序
 * </pre>
 *
 * 凭证与模型均可由环境变量注入（**不硬编码密钥**）：
 * - {@code SILICONFLOW_API_KEY}（缺省回退 {@code EMBEDDING_API_KEY}，评测测试已要求该变量）
 * - {@code SILICONFLOW_BASE_URL}（缺省 https://api.siliconflow.cn）
 * - {@code RERANK_MODEL}（缺省 BAAI/bge-reranker-v2-m3）
 *
 * implementation note：
 * - relevance_score 是「语义相关性」打分（越大越相关），与 recall 评测里「黄金子串是否命中」
 *   的判定口径**不同**——cross-encoder 认为语义相关但未必含黄金子串。评测解读时须区分（见评测代码注释）。
 * - 用 JDK HttpClinet(J11+) 发一次跨编码请求；候选量 = 一级召回放宽数（P1 用 20）。
 */
public class SiliconFlowRerankClient implements RerankClient {

    private static final String DEFAULT_BASE_URL = "https://api.siliconflow.cn";
    private static final String DEFAULT_MODEL = "BAAI/bge-reranker-v2-m3";

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public SiliconFlowRerankClient() {
        this.baseUrl = envOr("SILICONFLOW_BASE_URL", DEFAULT_BASE_URL);
        this.apiKey = envOr("SILICONFLOW_API_KEY", envOr("EMBEDDING_API_KEY", null));
        this.model = envOr("RERANK_MODEL", DEFAULT_MODEL);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "SILICONFLOW_API_KEY / EMBEDDING_API_KEY 未设置：真实 rerank 客户端需要 API key（评测测试要求 EMBEDDING_API_KEY 已注入）");
        }
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topK) {
        if (query == null || query.isBlank() || documents == null || documents.isEmpty() || topK <= 0) {
            return List.of();
        }
        try {
            // 1. 构造请求体
            String body = json.writeValueAsString(java.util.Map.of(
                    "model", model,
                    "query", query,
                    "documents", documents,
                    "top_n", Math.min(topK, documents.size()),
                    "return_documents", true));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/rerank"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new RuntimeException("rerank HTTP " + resp.statusCode() + ": " + resp.body());
            }

            // 2. 解析 results[]（已按相关度降序），映射回 RerankResult
            JsonNode root = json.readTree(resp.body());
            JsonNode results = root.path("results");
            List<RerankResult> out = new ArrayList<>();
            for (JsonNode r : results) {
                int idx = r.path("index").asInt(-1);
                double score = r.path("relevance_score").asDouble(0);
                String text = r.path("document").path("text").asText("");
                out.add(new RerankResult(idx, text, score));
            }
            return out;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("rerank 调用失败: " + e.getMessage(), e);
        }
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
