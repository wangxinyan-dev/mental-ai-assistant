package org.example.aispingboot.aspect;

import org.aspectj.lang.JoinPoint;
import org.example.aispingboot.annotation.AuditLog;
import org.example.aispingboot.entity.AuditLogRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * AuditLogAspect.buildRecord 事件组装纯函数单测（无 Spring 上下文、无请求上下文）。
 *
 * 只测「切面把 JoinPoint + 注解 + 执行结果组装成 AuditLogRecord」这一段纯逻辑：
 * – 注解的 module/action 落库
 * – 目标 id 取第一个 Long 类型参数
 * – detail 是入参 JSON 快照、超长截断
 * – 无请求上下文（测试环境）时 userId 为 null、不崩——对应生产里登录取用户信息失败/匿名场景
 * – 失败时 result=1 + errorMsg 记录异常 message，成功时 result=0
 *
 * JWT 用户解析本身是薄封装（复用已有 JwtTokenUtil，已有 JwtTokenUtilTest 覆盖），
 * 不在此单测重复——本文件测的是组装逻辑，不是 JWT。
 */
@ExtendWith(MockitoExtension.class)
class AuditLogAspectTest {

    private final AuditLogAspect aspect = new AuditLogAspect();

    @Mock
    private JoinPoint joinPoint;

    @Test
    void 成功事件_模块动作正确_结果0_无请求上下文时用户为null() throws Exception {
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, articleMap()});

        AuditLogRecord record = aspect.buildRecord(joinPoint, sampleAnnotation(), 42L, 0, null);

        assertThat(record.getModule()).isEqualTo("knowledge");
        assertThat(record.getAction()).isEqualTo("publish");
        assertThat(record.getResult()).isZero();
        assertThat(record.getErrorMsg()).isNull();
        // 单测无 RequestContextHolder → JWT 解析拿不到 → user_id 为 null（同匿名/登录取不到用户）
        assertThat(record.getUserId()).isNull();
        // 第一个 Long 参数是目标 id
        assertThat(record.getTargetId()).isEqualTo("1");
    }

    @Test
    void 失败事件_result为1_errorMsg记异常message() throws Exception {
        when(joinPoint.getArgs()).thenReturn(new Object[]{5L});

        AuditLogRecord record = aspect.buildRecord(joinPoint, sampleAnnotation(), 120L, 1, "模拟失败原因");

        assertThat(record.getResult()).isEqualTo(1);
        assertThat(record.getErrorMsg()).isEqualTo("模拟失败原因");
        assertThat(record.getCostMs()).isEqualTo(120L);
    }

    @Test
    void 无Long参数_targetId为null() throws Exception {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"不是id", "也没有id"});

        AuditLogRecord record = aspect.buildRecord(joinPoint, sampleAnnotation(), 3L, 0, null);

        assertThat(record.getTargetId()).isNull();
    }

    @Test
    void detail是入参JSON快照() throws Exception {
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, articleMap()});

        AuditLogRecord record = aspect.buildRecord(joinPoint, sampleAnnotation(), 42L, 0, null);

        assertThat(record.getDetail()).isNotNull().contains("title").contains("测试文章");
    }

    @Test
    void detail超长被截断到上限() throws Exception {
        Map<String, Object> big = new HashMap<>();
        big.put("title", "x".repeat(2000));
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, big});

        AuditLogRecord record = aspect.buildRecord(joinPoint, sampleAnnotation(), 42L, 0, null);

        assertThat(record.getDetail()).hasSizeLessThan(600);
    }

    /** 取测试方法上真实注解实例（比 mock 注解更真实） */
    private static AuditLog sampleAnnotation() throws Exception {
        Method m = AuditLogAspectTest.class.getDeclaredMethod("sampleTarget");
        return m.getAnnotation(AuditLog.class);
    }

    @AuditLog(action = "publish", module = "knowledge")
    void sampleTarget() {
    }

    private static Map<String, Object> articleMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("title", "测试文章");
        map.put("categoryId", 1);
        return map;
    }
}