package org.example.aispingboot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuditLogPartitionService 分区管理单测（无 DB）。
 *
 * 分三段验证：
 * 1. 纯函数——分区名 / 下月首日 / 保留截止（面试点：p+yyyyMM 命名保证字典序==时间序）；
 * 2. ADD——下月分区缺失才执行，SQL 边界正确（前闭后开到下下月首日），已存在不重复 ADD；
 * 3. DROP——保留期外的过期分区逐条删，无过期不删。
 */
@ExtendWith(MockitoExtension.class)
class AuditLogPartitionServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AuditLogPartitionService service;

    @Test
    void 分区名_按月命名_跨年正确() {
        assertThat(AuditLogPartitionService.partitionNameFor(LocalDate.of(2026, 8, 28))).isEqualTo("p202608");
        assertThat(AuditLogPartitionService.partitionNameFor(LocalDate.of(2027, 1, 1))).isEqualTo("p202701");
    }

    @Test
    void 下月首日_跨年正确() {
        assertThat(AuditLogPartitionService.nextMonthFirst(LocalDate.of(2026, 12, 15)))
                .isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void 保留截止_按保留月数回退() {
        // 保留 12 个月：2026-08 往前保留到 2025-09（含），即 oldest=2025-09-01
        assertThat(AuditLogPartitionService.oldestRetainedMonth(LocalDate.of(2026, 8, 28), 12))
                .isEqualTo(LocalDate.of(2025, 9, 1));
        // 保留 1 个月：只留当月
        assertThat(AuditLogPartitionService.oldestRetainedMonth(LocalDate.of(2026, 8, 28), 1))
                .isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void 下月分区已存在_不重复ADD() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);

        service.ensureNextPartition(LocalDate.of(2026, 8, 28));

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void 下月分区缺失_执行ADD_边界到下下月首日() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

        service.ensureNextPartition(LocalDate.of(2026, 8, 28));

        // p202609 容 2026-09，边界 2026-10-01（2026-09-01 的下月首日）
        verify(jdbcTemplate).execute(contains("ADD PARTITION (PARTITION p202609"));
        verify(jdbcTemplate).execute(contains("TO_DAYS('2026-10-01')"));
    }

    @Test
    void 过期分区存在_逐条DROP() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("p202508"));

        service.dropExpiredPartitions(LocalDate.of(2026, 8, 28), 12);

        verify(jdbcTemplate).execute(contains("DROP PARTITION p202508"));
    }

    @Test
    void 无过期分区_不执行DROP() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of());

        service.dropExpiredPartitions(LocalDate.of(2026, 8, 28), 12);

        verify(jdbcTemplate, never()).execute(anyString());
    }
}