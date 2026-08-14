package org.example.aispingboot.AiService.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * RAG 索引重建异步任务
 *
 * 独立 Bean 设计原因：
 * Spring @Async 基于 AOP 代理，同类内部方法互调会绕过代理导致注解失效。
 * 因此把异步逻辑抽到独立 Bean，由 KnowledgeService 跨 Bean 调用，保证 @Async 生效。
 *
 * 使用 ragTaskExecutor 线程池（见 AsyncConfig），与主业务线程隔离。
 */
@Slf4j
@Component
public class RagAsyncTask {

    private final RagService ragService;

    public RagAsyncTask(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 异步触发 RAG 索引重建
     *
     * 注意：异步方法的异常不会传播给调用方，必须在此处捕获并记录日志，
     * 否则重建失败会被静默吞掉，无法排查。
     */
    @Async("ragTaskExecutor")
    public void triggerRebuild(String triggerReason) {
        long start = System.currentTimeMillis();
        try {
            log.info("[RAG-ASYNC] 索引重建开始，触发原因: {}", triggerReason);
            int chunkCount = ragService.rebuildIndex();
            long cost = System.currentTimeMillis() - start;
            log.info("[RAG-ASYNC] 索引重建完成，分块数: {}, 耗时: {}ms", chunkCount, cost);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("[RAG-ASYNC] 索引重建失败，触发原因: {}, 耗时: {}ms, 异常: {}",
                    triggerReason, cost, e.getMessage(), e);
        }
    }
}
