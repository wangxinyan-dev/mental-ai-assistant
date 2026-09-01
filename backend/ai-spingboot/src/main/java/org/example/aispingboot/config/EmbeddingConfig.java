package org.example.aispingboot.config;

import org.example.aispingboot.AiService.rag.RerankClient;
import org.example.aispingboot.AiService.rag.SiliconFlowRerankClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Embedding 模型配置
 *
 * 设计说明：
 * DeepSeek API 不提供 Embedding 接口，因此 Chat 和 Embedding 使用不同的 API 端点。
 * 默认使用 SiliconFlow（OpenAI 兼容接口，免费），也可通过环境变量切换为其他提供商：
 *
 * 常见 Embedding 服务：
 * - 阿里云 DashScope: text-embedding-v3（1024维，中文效果好）
 * - OpenAI: text-embedding-3-small（1536维）
 * - SiliconFlow: BAAI/bge-large-zh-v1.5（1024维，免费）
 */
@Configuration
public class EmbeddingConfig {

    @Value("${rag.embedding.base-url:https://api.siliconflow.cn}")
    private String embeddingBaseUrl;

    @Value("${rag.embedding.api-key:}")
    private String embeddingApiKey;

    @Value("${rag.embedding.model:text-embedding-v3}")
    private String embeddingModelName;

    /** 批量 Embedding 调用失败重试最大次数（含首次），默认 3 次 */
    @Value("${rag.embedding.retry.max-attempts:3}")
    private int embeddingRetryMaxAttempts;

    /** 指数退避初始间隔（毫秒），默认 500ms，第 N 次失败后等待 500 * 2^(N-1) ms */
    @Value("${rag.embedding.retry.initial-backoff-ms:500}")
    private long embeddingRetryInitialBackoffMs;

    // ==================== Rerank（两级检索精排）配置 ====================
    // 未配置 api-key 时返回 null → RagService 优雅降级为「仅向量粗召回 Top-3」
    @Value("${rag.rerank.enabled:false}")
    private boolean rerankEnabled;

    @Value("${rag.rerank.api-key:}")
    private String rerankApiKey;

    @Value("${rag.rerank.base-url:https://api.siliconflow.cn}")
    private String rerankBaseUrl;

    @Value("${rag.rerank.model:BAAI/bge-reranker-v2-m3}")
    private String rerankModel;

    /** 粗召回条数（送进 rerank 的候选数） */
    @Value("${rag.rerank.recall-n:20}")
    private int rerankRecallN;

    /** 精排后返回条数（可覆盖 RagService 内默认 TOP_K） */
    @Value("${rag.rerank.top-k:3}")
    private int rerankTopK;

    public String getEmbeddingApiKey() {
        return embeddingApiKey;
    }

    public int getEmbeddingRetryMaxAttempts() {
        return embeddingRetryMaxAttempts;
    }

    public long getEmbeddingRetryInitialBackoffMs() {
        return embeddingRetryInitialBackoffMs;
    }

    public boolean isRerankEnabled() {
        return rerankEnabled;
    }

    public int getRerankRecallN() {
        return rerankRecallN;
    }

    public int getRerankTopK() {
        return rerankTopK;
    }

    /**
     * 生产 Rerank 客户端 Bean。
     * 未开启或未配置 api-key 时返回 null，RagService 侧以空判断优雅降级，
     * 不因缺 key 抛异常导致应用启动失败。
     */
    @Bean(destroyMethod = "")
    public RerankClient rerankClient() {
        boolean hasKey = rerankApiKey != null && !rerankApiKey.isBlank();
        if (!rerankEnabled || !hasKey) {
            return null;
        }
        return new SiliconFlowRerankClient(rerankApiKey, rerankBaseUrl, rerankModel);
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(embeddingApiKey)
                .baseUrl(embeddingBaseUrl)
                .build();

        return new OpenAiEmbeddingModel(openAiApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(embeddingModelName)
                        .build());
    }
}
