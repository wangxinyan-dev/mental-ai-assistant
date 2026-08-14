package org.example.aispingboot.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL 计数拦截器：统计每个 HTTP 请求执行的 SQL 次数
 * 请求结束时打印汇总，用于验证 N+1 优化效果
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, org.apache.ibatis.session.RowBounds.class, org.apache.ibatis.session.ResultHandler.class})
})
public class SqlCountInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger("SqlCountMonitor");

    // 每个请求线程的 SQL 计数器
    private static final String COUNTER_ATTR = "_sqlCounter";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 递增计数
        getCounter().increment();
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    /**
     * 获取当前请求的计数器
     */
    public static SqlCounter getCounter() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return new SqlCounter(); // 非 HTTP 请求上下文
        }
        HttpServletRequest request = attrs.getRequest();
        Object existing = request.getAttribute(COUNTER_ATTR);
        if (existing instanceof SqlCounter) {
            return (SqlCounter) existing;
        }
        SqlCounter counter = new SqlCounter();
        request.setAttribute(COUNTER_ATTR, counter);
        return counter;
    }

    /**
     * 请求结束时打印统计结果（由 Filter 调用）
     */
    public static void logAndReport(String method, String uri) {
        SqlCounter counter = getCounter();
        int count = counter.getCount();
        if (count > 0) {
            log.info("┌──────────────────────────────────────────");
            log.info("│ SQL 执行统计");
            log.info("│ 接口: {} {}", method, uri);
            log.info("│ SQL 次数: {}", count);
            if (count > 5) {
                log.warn("│ ⚠️  SQL 次数 > 5，可能存在 N+1 查询问题！");
            } else {
                log.info("│ ✅ SQL 次数正常");
            }
            log.info("└──────────────────────────────────────────");
        }
    }

    public static class SqlCounter {
        private int count = 0;

        void increment() {
            count++;
        }

        public int getCount() {
            return count;
        }
    }
}
