package org.example.aispingboot.AiService.rag;

import org.example.aispingboot.config.EmbeddingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RagService.callWithRetry 重试行为测试（纯单测，无 DB / 无 Spring 上下文）。
 *
 * 真实 API 签名（自 spring-ai-model-1.0.0.jar javap 反编译验证，非猜测）：
 * - EmbeddingResponse(List<Embedding>, ...)  /  getResults() → List<Embedding>
 * - Embedding(float[], Integer)              /  getOutput() → float[]
 *
 * ⚠️ 与注释声称「只重试瞬时抖动、确定性错误不重试」的出入：
 * 实现 catch(Exception) 对【所有】异常统一重试，未按异常类型区分。
 * 本测试按【真实实现行为】断言，把出入如实固定下来——
 * 若「确定性错误不重试」是预期行为，需在实现里按类型过滤，届时本测试同步收紧。
 * （当前保持测试反映代码真实行为，不为实现打补丁。）
 */
@ExtendWith(MockitoExtension.class)
class RagServiceRetryTest {

    @Mock
    private EmbeddingModel embeddingModel;

    /** 只测 callWithRetry，其余 @Autowired 字段保持 null（该方法不触碰它们） */
    @InjectMocks
    private RagService ragService;

    private EmbeddingConfig embeddingConfig;

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 1; // 1ms 加速测试，不测真实 500ms 退避时长

    private EmbeddingRequest request;

    @BeforeEach
    void setUp() {
        // EmbeddingConfig 是 @Bean（非字段注入），@InjectMocks 不处理它的 @Value——
        // 用反射把 retry 配置塞进 @Value 字段，模拟 application.yml 的 max-attempts=3 / 500ms。
        embeddingConfig = new EmbeddingConfig();
        setField(embeddingConfig, "embeddingRetryMaxAttempts", MAX_ATTEMPTS);
        setField(embeddingConfig, "embeddingRetryInitialBackoffMs", BACKOFF_MS);
        setField(ragService, "embeddingConfig", embeddingConfig);

        request = new EmbeddingRequest(List.of("文本1", "文本2"), null);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("反射设置字段失败: " + name, e);
        }
    }

    private static EmbeddingResponse okResponse() {
        return new EmbeddingResponse(
                List.of(
                        new Embedding(new float[]{0.1f, 0.2f}, 0),
                        new Embedding(new float[]{0.3f, 0.4f}, 1)
                )
        );
    }

    @Test
    void 首次调用直接成功_不触发任何重试() {
        when(embeddingModel.call(any())).thenReturn(okResponse());

        EmbeddingResponse r = ragService.callWithRetry(request);

        assertThat(r).isNotNull();
        // 成功即返回，仅调 1 次，无退避
        verify(embeddingModel, times(1)).call(any());
    }

    @Test
    void 失败一次后第二次成功_触发一次重试() {
        when(embeddingModel.call(any()))
                .thenThrow(new RuntimeException("模拟瞬时失败"))
                .thenReturn(okResponse());

        EmbeddingResponse r = ragService.callWithRetry(request);

        assertThat(r).isNotNull();
        // 失败 1 + 成功 1 = 共 2 次
        verify(embeddingModel, times(2)).call(any());
    }

    @Test
    void 连续失败到maxAttempts_返回null_即跳过整批信号() {
        when(embeddingModel.call(any())).thenThrow(new RuntimeException("模拟持续失败"));

        EmbeddingResponse r = ragService.callWithRetry(request);

        // 耗尽返回 null =「跳过整批」，调用方 continue 而非整体失败
        assertThat(r).isNull();
        // 恰好 maxAttempts 次（3 次失败都算调用）
        verify(embeddingModel, times(MAX_ATTEMPTS)).call(any());
    }

    @Test
    void 确定性逻辑异常_当前实现仍统一重试_如实反映行为() {
        // 注释声称「确定性错误不重试」，但实现 catch(Exception) 全部重试——
        // 用 IllegalArgumentException（确定性错误典型）验证真实行为：同样被重试到耗尽。
        when(embeddingModel.call(any())).thenThrow(new IllegalArgumentException("维度不匹配"));

        EmbeddingResponse r = ragService.callWithRetry(request);

        assertThat(r).isNull();
        verify(embeddingModel, times(MAX_ATTEMPTS)).call(any());
    }
}