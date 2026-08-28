package org.example.aispingboot.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解——标记需要留痕的写接口。
 *
 * 用法：给 Controller 写方法加 @AuditLog(action="...", module="...")，
 * 由 AuditLogAspect 统一拦截、组装审计记录并异步落库（audit_log 表）。
 *
 * 设计：显式注解精确控制"记哪些"，不铺满所有接口——
 * 只有标注的接口才留痕，避免审计表被大量无意义读操作淹没。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 动作名，如 create_article / update_user_status */
    String action();

    /** 模块名，如 knowledge / user */
    String module();
}