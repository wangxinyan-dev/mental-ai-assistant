package org.example.aispingboot.task;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.aispingboot.service.AuditLogPartitionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * audit_log 分区维护定时任务。
 *
 * 启动后 30s 首跑一次（新环境建表后尽快补齐下月分区），之后每 24h 跑一遍：
 * ① ADD 下月分区 ② DROP 保留期外过期分区。
 *
 * 定时任务异常会被 Spring 调度吞掉（不重抛就静默），必须自己 try-catch 兜底记日志——
 * 与 @Async、@Scheduled 共享同一个心智：外部线程/调度里的异常不自动传播。
 */
@Slf4j
@Component
public class AuditLogPartitionTask {

    @Resource
    private AuditLogPartitionService auditLogPartitionService;

    @Value("${audit-log.partition.retention-months:12}")
    private int retentionMonths;

    @Scheduled(initialDelay = 30000, fixedDelay = 86400000)
    public void partitionMaintenance() {
        try {
            LocalDate today = LocalDate.now();
            auditLogPartitionService.ensureNextPartition(today);
            auditLogPartitionService.dropExpiredPartitions(today, retentionMonths);
            log.info("[AUDIT-PARTITION] 维护完成：已确认下月分区，清理保留期外过期分区");
        } catch (Exception e) {
            // 定时任务异常不向上抛（否则调度失效），自己兜底
            log.error("[AUDIT-PARTITION] 维护失败，原因: {}", e.getMessage(), e);
        }
    }
}