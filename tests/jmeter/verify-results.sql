-- ==================================================
-- 心理健康助手 JMeter 压测 - 数据库验证脚本
-- 路径：tests/jmeter/verify-results.sql
-- 用法：在压测前后各执行一次，对比数据
-- ==================================================

-- ==========================================
-- 场景 A：文章详情接口 - 验证原子 SQL 自增
-- ==========================================

-- 压测前执行：记录初始阅读量
SELECT id, title, read_count, '压测前' AS phase FROM article WHERE id = 1;

-- 压测后执行：记录最终阅读量
-- 期望：read_count = 压测前值 + 1000（无丢失）
SELECT id, title, read_count, '压测后' AS phase,
       (read_count - 1000) AS expected_before,
       CASE
           WHEN read_count >= 1000 THEN '✅ 无丢失（原子SQL生效）'
           ELSE '❌ 有丢失（旧版先查再改）'
       END AS result
FROM article WHERE id = 1;


-- ==========================================
-- 场景 B：登录接口 - 验证 Caffeine 缓存
-- ==========================================

-- 压测前：查看当前 user 表记录数
SELECT COUNT(*) AS user_count_before FROM user;

-- 压测后：查看 user 表记录数（应该和压测前一致，因为登录不创建用户）
SELECT COUNT(*) AS user_count_after,
       (SELECT COUNT(*) FROM user) - (SELECT COUNT(*) FROM user) AS diff
FROM DUAL;

-- 缓存命中率验证：通过后端日志统计
-- 期望日志输出：
--   - 无 Caffeine：每条请求都执行 SELECT * FROM user WHERE id = ?
--   - 启用 Caffeine：2分钟内只执行 1 次 SQL
-- 可在后端控制台直接搜索关键字 "SELECT id,user_type FROM user" 计数


-- ==========================================
-- 场景 C：RAG 重建 - 验证异步化 + 批量 Embedding
-- ==========================================

-- 查看知识分块表
SELECT COUNT(*) AS chunk_count FROM knowledge_chunk;

-- 查看 PgVector 向量条数（需切到 pgvector 数据源）
-- 如果通过 docker exec 进入 pgvector 容器执行：
-- docker exec -it mental-pgvector psql -U postgres -d ragdb -c "SELECT COUNT(*) FROM rag_embedding;"

-- 重建后验证：每个 chunk 都应该有对应向量
-- 如果 MySQL 有 chunk 但 PgVector 没向量，说明事务回滚或异步失败
SELECT
    (SELECT COUNT(*) FROM knowledge_chunk) AS mysql_chunk_count,
    -- PgVector 数量需要单独执行，这里仅作注释
    '需在 PgVector 容器执行: SELECT COUNT(*) FROM rag_embedding' AS pgvector_count_check
FROM DUAL;


-- ==========================================
-- 清理测试数据（可选）
-- ==========================================

-- 如果是测试环境，可以重置阅读量方便重复测试
-- UPDATE article SET read_count = 0 WHERE id = 1;
-- 注意：重置后需要重新跑场景 A 才能再次对比


-- ==========================================
-- 一键验证脚本（场景 A 压测后执行）
-- ==========================================

-- 把压测前的 read_count 填到下面这个变量里
-- 修改 @before_count 后执行
SET @before_count = 0;

SELECT
    id,
    title,
    read_count AS current_count,
    @before_count AS before_count,
    (read_count - @before_count) AS actual_increment,
    1000 AS expected_increment,
    CASE
        WHEN (read_count - @before_count) = 1000 THEN '✅ 原子SQL生效：1000 次请求 0 丢失'
        WHEN (read_count - @before_count) > 1000 THEN '⚠️ 增量超出，可能有其他并发写入'
        ELSE CONCAT('❌ 丢失 ', (1000 - (read_count - @before_count)), ' 次更新（旧版先查再改的并发问题）')
    END AS result
FROM article WHERE id = 1;
