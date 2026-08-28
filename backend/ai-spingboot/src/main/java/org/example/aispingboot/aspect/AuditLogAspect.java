package org.example.aispingboot.aspect;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.aispingboot.annotation.AuditLog;
import org.example.aispingboot.entity.AuditLogRecord;
import org.example.aispingboot.service.AuditLogService;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 操作审计切面——拦截所有带 @AuditLog 的方法，统一组装审计记录并异步落库。
 *
 * 设计：审计是"旁路"，不能拖慢、不能让主请求失败。
 * 1. @Around 记成功/失败 + 耗时，业务正常 proceed；审计失败绝不抛给业务。
 * 2. 组装好的记录交给 AuditLogService 异步写（独立线程池 auditLogExecutor），
 *    主线程不等 DB 写入返回。
 * 3. 落库失败由 AuditLogService 内部 try-catch 记日志——同 RAG 异步任务的心智：
 *    异步方法自己兜底，不传播异常。
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    /** 入参快照最大长度，超长截断避免单行撑爆表 */
    static final int MAX_DETAIL_LENGTH = 500;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private AuditLogService auditLogService;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            writeRecord(joinPoint, auditLog, System.currentTimeMillis() - start, 0, null, result);
            return result;
        } catch (Throwable t) {
            writeRecord(joinPoint, auditLog, System.currentTimeMillis() - start, 1, t.getMessage(), null);
            throw t; // 业务异常照常抛，审计只记录不吞异常
        }
    }

    private void writeRecord(JoinPoint joinPoint, AuditLog auditLog, long costMs, int result, String errorMsg, Object returnValue) {
        try {
            AuditLogRecord record = buildRecord(joinPoint, auditLog, costMs, result, errorMsg);
            auditLogService.asyncWrite(record);
        } catch (Exception e) {
            // 组装失败不阻塞业务——审计记录是旁路，宁可丢一条也不能让主请求挂
            log.error("[AUDIT-LOG] 组装审计记录失败，module={}, action={}, 原因: {}",
                    auditLog.module(), auditLog.action(), e.getMessage());
        }
    }

    /**
     * 组装审计记录（纯逻辑，包级访问便于单测）。
     *
     * – 目标 id：取入参里第一个 Long（文章id/用户id 这类主键）；
     * – 入参快照：参数数组 JSON 化，超长截断；
     * – 当前用户：走 JwtTokenUtil（RequestContextHolder）解析，解析不到为匿名/空；
     * – 全链路 try-catch，任何信息源失败都不让审计崩。
     */
    AuditLogRecord buildRecord(JoinPoint joinPoint, AuditLog auditLog, long costMs, int result, String errorMsg) {
        AuditLogRecord record = new AuditLogRecord();
        record.setModule(auditLog.module());
        record.setAction(auditLog.action());
        record.setResult(result);
        record.setErrorMsg(errorMsg);
        record.setCostMs(costMs);
        record.setTargetId(resolveTargetId(joinPoint.getArgs()));
        record.setDetail(resolveDetail(joinPoint.getArgs()));
        resolveCurrentUser(record);
        record.setIp(resolveIp());
        record.setCreatedAt(java.time.LocalDateTime.now());
        return record;
    }

    /** 第一个 Long 类型入参即操作目标 id（ID 型主键在项目里统一是 Long） */
    private String resolveTargetId(Object[] args) {
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof Long) {
                    return arg.toString();
                }
            }
        }
        return null;
    }

    /** 参数数组 JSON 化作为入参快照；序列化失败降级为最简描述（审计不能崩） */
    private String resolveDetail(Object[] args) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(args);
            return json.length() > MAX_DETAIL_LENGTH ? json.substring(0, MAX_DETAIL_LENGTH) : json;
        } catch (Exception e) {
            return "args-not-serializable, count=" + (args == null ? 0 : args.length);
        }
    }

    /** 当前登录用户：JWT claim userId/username；解析不到（匿名/无上下文/解析异常）置空 */
    private void resolveCurrentUser(AuditLogRecord record) {
        try {
            String token = JwtTokenUtil.getCurrentToken();
            if (token == null) {
                return;
            }
            DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
            record.setUserId(jwt.getClaim("userId").asLong());
            record.setUsername(jwt.getClaim("username").asString());
        } catch (Exception e) {
            log.warn("[AUDIT-LOG] 解析当前用户失败，按匿名处理，原因: {}", e.getMessage());
        }
    }

    /** 客户端 IP；取不到返回 null（审计字段可空） */
    private String resolveIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String forward = request.getHeader("X-Forwarded-For");
                return forward != null && !forward.isEmpty() ? forward.split(",")[0].trim() : request.getRemoteAddr();
            }
        } catch (Exception e) {
            // IP 取不到不影响审计
        }
        return null;
    }
}