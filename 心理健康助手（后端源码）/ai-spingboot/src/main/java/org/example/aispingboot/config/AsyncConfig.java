package org.example.aispingboot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 *
 * 为 RAG 索引重建等耗时任务提供独立线程池，避免：
 * 1. 使用默认 SimpleAsyncTaskExecutor 导致线程无限创建
 * 2. 与主业务线程池抢占资源
 */
@Slf4j
@Configuration
public class AsyncConfig {

    /**
     * RAG 索引重建专用线程池
     *
     * 参数选择理由：
     * - 核心线程 2：RAG 重建为后台任务，不需要高并发
     * - 最大线程 4：突发流量时有限扩容
     * - 队列容量 10：避免重建任务无限堆积
     * - 拒绝策略 DiscardOldestPolicy：重建是幂等的（每次全量重建），
     *   队列满时丢弃最老的重建任务，保证最新文章变更一定会被处理
     */
    @Bean("ragTaskExecutor")
    public ThreadPoolTaskExecutor ragTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("rag-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("RAG 异步线程池已初始化: core=2, max=4, queue=10, reject=DiscardOldestPolicy");
        return executor;
    }
}
