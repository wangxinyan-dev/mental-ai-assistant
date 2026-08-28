package org.example.aispingboot.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.aispingboot.entity.AuditLogRecord;
import org.example.aispingboot.mapper.AuditLogMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 审计日志落库服务——@Async 异步写 audit_log。
 *
 * 审计是"可丢、可延迟"的旁路操作：主请求一结束立即返回，落库在线程池里做。
 * 异步方法异常必须自己兜底（@Async 不把异常传给调用方），这里 try-catch 记日志——
 * 审计失败绝不反噬业务，这是把审计做成旁路的前提。
 */
@Slf4j
@Service
public class AuditLogService {

    @Resource
    private AuditLogMapper auditLogMapper;

    @Async("auditLogExecutor")
    public void asyncWrite(AuditLogRecord record) {
        try {
            auditLogMapper.insert(record);
        } catch (Exception e) {
            log.error("[AUDIT-LOG] 异步落库失败，module={}, action={}, 原因: {}",
                    record.getModule(), record.getAction(), e.getMessage());
        }
    }
}