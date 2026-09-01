package org.example.aispingboot.AiService.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SiliconFlow cross-encoder Rerank 客户端（真实实现，生产 + 评测共用）。
 *
 * 调用 SiliconFlow 的字段级 rerank 接口对候选做精排，替换 {@link HeuristicRerankClient} 占位，
 * 供 {@link RagService} 两级检索（粗召回 + 精排）与 {@link RagEvalRunner} 评测使用。
 *
 * == 契约（2026-09-01 已实测确认，勿凭记忆改） ==
 * <pre>
 *   POST {base-url}/v1/rerank
 *   Authorization: Bearer {api-key}
 *   body: { "model": "BAAI/bge-reranker-v2-m3",
 *           "query": "...", "documents": ["..."], "top_n": N, "return_documents": true }
 *   响应: { "results": [ {"index": i, "relevance_score": x, "document": {"text": "..."}}, ... ] }
 *   已按 relevance_score 降序。
 * </pre>
 * ⚠️ 实测要点：①必须显式传 {@code return_documents}（缺省不传会报 20015 parameter is invalid）；
 * ②{@code return_documents:true} 时 {@code document} 是 {@code {text}} 对象，不是字符串。
 *
 * implementation note：
 * - relevance_score 是「语义相关性」（越大越相关），与 recall 评测里「黄金子串命中」口径不同——
 *   cross-encoder 认为语义相关未必含黄金子串，评测解读时须区分。
 * - 健壮降级：HTTP 非 200 / 解析失败 / 字段缺失时，**保守按候选原始顺序取前 topK 兜底**（不抛异常），
 *   保证生产检索链路不因 rerank 故障而空返回（宁可精排失效也不牺牲可用性）。
 * - 线程安全、无状态，可安全单例复用。
 */
public class SiliconFlowRerankClient implements RerankClient {

    private static final Logger log = LoggerFactory.getLogger(SiliconFlowRerankClient.class);

    public static final String DEFAULT_BASE_URL = "https://api.siliconflow.cn";
    public static final String DEFAULT_MODEL = "BAAI/bge-reranker-v2-m3";
    private static final String RERANK_PATH = "/v1/rerank";

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 无参构造：从环境变量注入凭证（评测/本地直接用），缺省回退：
     * {@code SILICONFLOW_BASE_URL}/{@code SILICONFLOW_API_KEY|EMBEDDING_API_KEY}/{@code RERANK_MODEL}。
     */
    public SiliconFlowRerankClient() {
        this(envOr("SILICONFLOW_API_KEY", envOr("EMBEDDING_API_KEY", null)),
                envOr("SILICONFLOW_BASE_URL", DEFAULT_BASE_URL),
                envOr("RERANK_MODEL", DEFAULT_MODEL));
    }

    /**
     * 显式构造：生产（Spring Bean）用，凭证由配置注入。
     *
     * @param apiKey  SiliconFlow API key（一般与 embedding 同一个）
     * @param baseUrl rerank base-url，null/blank 取默认硅基流动
     * @param model   rerank 模型名，null/blank 取默认
     */
    public SiliconFlowRerankClient(String apiKey, String baseUrl, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("SiliconFlowRerankClient 需要 apiKey");
        }
        this.apiKey = apiKey;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topK) {
        if (query == null || query.isBlank() || documents == null || documents.isEmpty() || topK <= 0) {
            return List.of();
        }
        try {
            // 1. 构造请求体（return_documents 必须显式传 true，见类注释实测要点）
            String body = json.writeValueAsString(Map.of(
                    "model", model,
                    "query", query,
                    "documents", documents,
                    "top_n", Math.min(topK, documents.size()),
                    "return_documents", true));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + RERANK_PATH))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new RuntimeException("rerank HTTP " + resp.statusCode() + ": " + resp.body());
            }

            // 2. 解析 results[]（已降序），映射回 RerankResult
            JsonNode root = json.readTree(resp.body());
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                log.warn("[RAG-RERANK] 响应缺少 results，按候选原始顺序兜底前 {} 条", topK);
                return fallbackByOrder(documents, topK);
            }
            List<RerankResult> out = new ArrayList<>(topK);
            for (JsonNode r : results) {
                int idx = r.path("index").asInt(-1);
                double score = r.path("relevance_score").asDouble(0.0);
                // return_documents:true 时 document 为 {text}; 缺失/越界的 index 跳过兜底
                String text = r.path("document").path("text").asText(null);
                if (idx < 0 || idx >= documents.size()) {
                    continue;
                }
                if (text == null) {
                    text = documents.get(idx);
                }
                out.add(new RerankResult(idx, text, score));
                if (out.size() >= topK) break;
            }
            return out.isEmpty() ? fallbackByOrder(documents, topK) : out;
        } catch (Exception e) {
            log.warn("[RAG-RERANK] 调用/解析失败，按候选原始顺序兜底前 {} 条: {}", topK, e.getMessage());
            return fallbackByOrder(documents, topK);
        }
    }

    /** 解析失败 / 字段不符时的保守兜底：按候选原始顺序取前 topK（虽不精排也保证不空）。 */
    private List<RerankResult> fallbackByOrder(List<String> documents, int topK) {
        List<RerankResult> out = new ArrayList<>(Math.min(topK, documents.size()));
        for (int i = 0; i < Math.min(topK, documents.size()); i++) {
            out.add(new RerankResult(i, documents.get(i), 0.0));
        }
        return out;
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
