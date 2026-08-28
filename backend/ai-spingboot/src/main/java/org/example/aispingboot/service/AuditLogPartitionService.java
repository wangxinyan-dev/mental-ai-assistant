package org.example.aispingboot.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * audit_log 按月 RANGE 分区维护。
 *
 * 为什么「按月分区 + DROP PARTITION」而不是「定时 DELETE 老数据」——核心面试点：
 * - DROP PARTITION 是 O(1) 元数据操作：秒级、不产生大事务/binlog、不留碎片；
 *   而 DELETE 百万行既慢又写大量 binlog，还会在高峰期锁行。
 * - 分区对查询透明：历史审计记录仍在原表，SELECT 直接查，不存在"查历史要去归档表"的降级。
 * - 保留策略配置化（retention-months，默认 12 个月），过期的按月整块 DROP，
 *   永远不会"删了还在被查询的热数据"。
 *
 * 工程约束（面试常追问）：MySQL 分区表要求【主键必须包含分区键列】，
 * 因此 audit_log 主键从 (id) 改为 (id, created_at)——对按月审计查询无影响，
 * 只影响"按 id 精确单查"的索引前缀，可接受。
 *
 * 分区命名 p+yyyyMM：月份零填充 ⇒ 分区名字典序 == 时间序，比较直接用字符串。
 * 新分区 ADD 由 AuditLogPartitionTask 每月自动补齐；建表 DDL 只需含当月分区，
 * 后续分区由定时任务从当月向前推进（每次确保下月分区存在）。
 */
@Slf4j
@Service
public class AuditLogPartitionService {

    private static final String TABLE_NAME = "audit_log";
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    @Resource
    @Qualifier("mysqlJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    /** p+yyyyMM；2026-08 → "p202608"。命名保证字典序==时间序 */
    static String partitionNameFor(LocalDate month) {
        return "p" + MONTH_FORMAT.format(month);
    }

    /** 下月 1 日；2026-12-15 → 2027-01-01（跨年正确） */
    static LocalDate nextMonthFirst(LocalDate month) {
        return month.plusMonths(1).withDayOfMonth(1);
    }

    /** 保留范围内最早月份（含）；2026-08-28 且保留 12 个月 → 2025-09-01 */
    static LocalDate oldestRetainedMonth(LocalDate today, int retentionMonths) {
        return today.withDayOfMonth(1).minusMonths(retentionMonths - 1L);
    }

    /**
     * 确保「下月分区」已存在，缺失则 ADD——每次执行后分区表始终覆盖到下月，
     * 杜绝"数据写入撞上无分区"报错。
     */
    public void ensureNextPartition(LocalDate today) {
        LocalDate nextMonth = nextMonthFirst(today);
        String partition = partitionNameFor(nextMonth);
        if (partitionExists(partition)) {
            return;
        }
        LocalDate boundary = nextMonthFirst(nextMonth); // 前闭后开：下下月首日
        String ddl = "ALTER TABLE " + TABLE_NAME + " ADD PARTITION (PARTITION " + partition
                + " VALUES LESS THAN (TO_DAYS('" + boundary + "')))";
        jdbcTemplate.execute(ddl);
        log.info("[AUDIT-PARTITION] 已新增分区 {}（覆盖到 {} 前）", partition, boundary);
    }

    /**
     * 删除保留期外的过期分区，逐条 DROP PARTITION（O(1) 元数据操作，无大事务）。
     * 保留期由 retention-months 决定（默认 12 个月）。
     */
    public void dropExpiredPartitions(LocalDate today, int retentionMonths) {
        String cutoff = partitionNameFor(oldestRetainedMonth(today, retentionMonths));
        List<String> expired = jdbcTemplate.queryForList(
                "SELECT PARTITION_NAME FROM information_schema.PARTITIONS "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = '" + TABLE_NAME + "'"
                        + " AND PARTITION_NAME < '" + cutoff + "'",
                String.class);
        for (String name : expired) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " DROP PARTITION " + name);
            log.info("[AUDIT-PARTITION] 已删除过期分区 {}（早于 {}）", name, cutoff);
        }
    }

    private boolean partitionExists(String partitionName) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.PARTITIONS "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME = '" + TABLE_NAME + "'"
                        + " AND PARTITION_NAME = '" + partitionName + "'",
                Integer.class);
        return cnt != null && cnt > 0;
    }
}