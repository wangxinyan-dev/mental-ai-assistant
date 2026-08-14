# JMeter 压测脚本使用说明

> 适用版本：JMeter 5.5+（推荐 5.6.3）
> 脚本路径：`tests/jmeter/mental-health-load-test.jmx`

---

## 🚀 快速开始（3 步跑起来）

### 1. 准备数据

启动后端 + MySQL 后，准备：
- 管理员账号：`admin / 123456`（可通过 `DataInitializer` 自动创建）
- 至少一篇文章，且 `id=1`（用于压测阅读量自增）

```sql
-- 验证账号和文章存在
SELECT id, username, user_type FROM user WHERE username = 'admin';
SELECT id, title, read_count FROM article WHERE id = 1;
```

如果没有文章，先创建一条：
```sql
INSERT INTO article (title, content, category_id, status, user_id)
VALUES ('测试文章-压测用', '这是一篇测试文章', 1, 1, 1);
```

### 2. 打开脚本

```bash
# 进入 JMeter bin 目录
cd %JMETER_HOME%\bin
jmeter -t e:\agent\心理健康助手\tests\jmeter\mental-health-load-test.jmx
```

或在 GUI 中：File → Open → 选择 `mental-health-load-test.jmx`

### 3. 按需修改参数（重要！）

在测试计划顶部"用户定义变量"中修改：

| 变量名 | 默认值 | 什么时候需要改 |
|--------|--------|----------------|
| `HOST` | localhost | 后端不在本机时改 |
| `PORT` | 1236 | 后端端口改了时改 |
| `ARTICLE_ID` | 1 | 压测前请确认这个 ID 的文章存在 |
| `ADMIN_USERNAME` | admin | 你改过用户名时改 |
| `ADMIN_PASSWORD` | 123456 | 你改过密码时改 |

### 4. 运行

#### 方式 A：GUI 模式（推荐首次跑，便于看结果）
- 点击顶部"启动"按钮（绿色三角）
- 在每个场景的"汇总报告"和"聚合报告"里看数据

#### 方式 B：CLI 模式（推荐正式压测，更准确）
```bash
# 命令行运行（不打开 GUI，结果更准）
jmeter -n -t mental-health-load-test.jmx ^
  -l test-results.jtl ^
  -e -o ./report-dashboard

# 跑完后打开 ./report-dashboard/index.html 看可视化报告
```

> ⚠️ **GUI 模式会影响测试精度**，正式压测必须用 CLI 模式跑数据。

---

## 📋 三个压测场景说明

### 场景 A：文章详情接口（验证原子 SQL 自增）

| 配置项 | 值 |
|--------|-----|
| 并发线程 | 200 |
| 循环次数 | 5 |
| 总请求数 | 1000 |
| Ramp-up | 10s |
| 接口 | `GET /api/knowledge/article/{id}` |

**执行流程**：
1. **A.1 setUp 线程组**：先用 admin 登录，提取 token 到 `ADMIN_TOKEN`
2. **A.2 主线程组**：200 个线程带 token 并发访问文章详情，每个线程循环 5 次

**验证点**：
- 压测后查 MySQL：`SELECT read_count FROM article WHERE id = 1;`
- 期望：read_count = 压测前值 + 1000（**无丢失**）
- 如果用 v1.0 之前的"先查再改"代码，会丢 30-50 个

### 场景 B：登录接口（验证 Caffeine 缓存）

| 配置项 | 值 |
|--------|-----|
| 并发线程 | 100 |
| 循环次数 | 10 |
| 总请求数 | 1000 |
| Ramp-up | 5s |
| 接口 | `POST /api/auth/login` |

**验证点**：
- 压测时看后端控制台 SQL 输出
- 启用 Caffeine 前：每次请求都查 `SELECT * FROM user WHERE id = ?`（1000 次）
- 启用后：2 分钟 TTL 内只查 1 次（**命中率 99.9%**）
- 可对比跑两次：先关闭缓存跑一次，再开启缓存跑一次

### 场景 C：RAG 重建（验证异步化）

| 配置项 | 值 |
|--------|-----|
| 并发线程 | 1 |
| 循环次数 | 1 |
| 总请求数 | 1 |
| 接口 | `POST /api/rag/rebuild` |

**验证点**：
- **响应时间断言 ≤ 500ms**（脚本里已配置 DurationAssertion）
- v1.4 之前：响应时间 10s+（同步等 Embedding）
- v2.0：响应时间 200ms 左右（异步触发即返回）
- 后端日志应输出：`RAG向量化：X 个分块…平均 Yms/分块`

---

## 📊 看结果的位置

JMeter GUI 中每个场景都有以下监听器：

| 监听器 | 看什么 |
|--------|--------|
| **汇总报告 (Summary Report)** | Average / Median / 90% Line / 99% Line / Min / Max / Error% / Throughput |
| **聚合报告 (Aggregate Report)** | 同上，按 Sampler 分组 |
| **查看结果树** (场景 C) | 单个请求的响应详情、请求头、响应体 |

### CLI 模式结果文件

```bash
# test-results.jtl - 原始结果数据
# ./report-dashboard/index.html - 可视化仪表盘
```

打开 `report-dashboard/index.html`，会看到：
- Statistics（统计表）
- Response Times Over Time（响应时间趋势图）
- Active Threads Over Time（活跃线程趋势图）
- Errors（错误分布）

---

## 🎯 面试数据采集模板

跑完三个场景后，按下面表格填数据（截图保存作为简历附件）：

### 场景 A：原子 SQL 自增验证

| 指标 | v1.0 旧版（先查再改） | v2.0 新版（原子 SQL） |
|------|---------------------|---------------------|
| 并发数 | 200 | 200 |
| 总请求数 | 1000 | 1000 |
| 平均响应时间 | __ ms | __ ms |
| Throughput | __ /s | __ /s |
| Error% | __ % | __ % |
| 阅读量丢失数 | __ 个 | **0 个** |

### 场景 B：Caffeine 缓存验证

| 指标 | 无 Caffeine | 启用 Caffeine (2min TTL) |
|------|------------|------------------------|
| 并发数 | 100 | 100 |
| 总请求数 | 1000 | 1000 |
| 平均响应时间 | __ ms | __ ms |
| Throughput | __ /s | __ /s |
| 后端 SQL 次数 | __ 次 | __ 次 |
| 缓存命中率 | 0% | __ % |

### 场景 C：RAG 异步化验证

| 指标 | v1.4 之前（同步） | v2.0（异步） |
|------|-------------------|-------------|
| 接口响应时间 | __ ms | __ ms |
| 后端日志 Embedding 调用 | __ 次 | __ 次（批量） |
| 重建总耗时（看日志） | __ s | __ s |

---

## ⚠️ 常见问题

### Q1：登录拿不到 token，断言失败

检查：
1. 后端是否启动：`curl http://localhost:1236/actuator/health`
2. 账号密码是否正确：`curl -X POST http://localhost:1236/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"123456"}'`
3. DataInitializer 是否执行（看后端启动日志有没有 "默认管理员已创建"）

### Q2：场景 A 报 401

检查：
1. setUp 线程组是否成功提取 token（看 Debug Sampler 输出 ADMIN_TOKEN 值）
2. Token 是否过期（默认 30 分钟，如果你压测太久可能过期）
3. JWT_SECRET 是否配置（如果用默认 `change-me-in-production` 应该可以工作）

### Q3：场景 C 报 500

检查：
1. PgVector 容器是否启动：`docker ps | findstr pgvector`
2. Embedding API Key 是否配置
3. 后端日志是否有 Embedding 调用失败信息

### Q4：CLI 模式跑完不知道看哪个文件

```bash
# 关键文件
test-results.jtl              # 原始数据（可在 GUI 中打开看）
report-dashboard/index.html   # 可视化报告（浏览器打开）
```

### Q5：如何只跑某一个场景

在 GUI 中：
- 右键其他两个线程组 → Disable
- 只保留要跑的线程组为 Enable
- 点启动

---

## 💡 进阶用法

### 1. 持续集成压测（CI/CD）

```bash
# 在 Jenkins/GitHub Actions 中跑：
jmeter -n -t mental-health-load-test.jmx -l ci-results.jtl
# 通过 Shell 脚本解析 jtl，断言 Throughput > 100，Error% < 1
```

### 2. 不同版本对比测试

```bash
# checkout v1.0 跑一次
git checkout v1.0-base
mvn spring-boot:run
jmeter -n -t mental-health-load-test.jmx -l v1.0-results.jtl -e -o ./report-v1.0

# checkout v2.0 跑一次
git checkout v2.0
mvn spring-boot:run
jmeter -n -t mental-health-load-test.jmx -l v2.0-results.jtl -e -o ./report-v2.0

# 对比两个 report 文件夹的 Statistics
```

### 3. 分布式压测（多台机器同时压）

如果单机 CPU 打满，可以：
- 启动多个 JMeter Server：`jmeter-server`
- Master 端配置 remote_hosts
- 用 `jmeter -n -t xxx.jmx -r` 触发所有 Server 同时跑

秋招项目用不到，知道有这回事就行。

---

## 📞 面试 Q&A 参考

**Q：你这个项目怎么验证性能优化的效果？**

> "我用 JMeter 写了三个压测场景：
> 1. 文章详情接口，200 并发 1000 请求，旧版丢 30-50 个阅读量，原子 SQL 后 0 丢失
> 2. 登录接口，1000 次压测，启用 Caffeine 后 SQL 查询次数从 1000 降到 1，命中率 99.9%
> 3. RAG 重建接口，从 10s 同步阻塞降到 200ms 异步触发，脚本里直接加了 DurationAssertion 断言响应时间 ≤ 500ms
>
> 脚本和测试报告都放在仓库的 tests/jmeter 目录下。"

**Q：为什么不用 Postman 或 Apifox？**

> "Postman 适合接口调试，但没法精确控制并发数和循环次数。JMeter 可以：
> 1. 精确控制 200 个线程同时发起请求，模拟真实并发场景
> 2. setUp 线程组先登录拿 token，主线程组复用，更接近真实使用流程
> 3. 自带断言，可以直接断言响应时间、JSON 字段，不用人工判断
> 4. CLI 模式跑出来不占用 GUI 资源，数据更准"

**Q：你的测试覆盖率多少？**

> "我没有追求 80%+ 的代码覆盖率，而是聚焦关键链路：
> 1. 单元测试：JWT 工具类、用户鉴权、知识库 CRUD、RAG 重建，5 个核心类的核心方法
> 2. 集成测试：JMeter 覆盖 3 个性能关键路径
> 3. 手工测试：Knife4j 走完 15 个接口的核心 case
>
> 我倾向于测有业务价值的边界条件，而不是为了覆盖率去测 getter/setter。"
