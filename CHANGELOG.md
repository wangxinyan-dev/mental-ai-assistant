# 变更日志（Changelog）

本项目所有重要变更记录于此。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

---

## [v2.0] - 2026-08-14 — 事务 + 批量 Embedding + 安全加固 + 文档完善

### 🎯 本次目标
- 修复 RAG 索引重建过程的一致性缺陷（删半插半）
- 优化 Embedding API 网络开销（HTTP round-trip -90%）
- 清理代码细节硬伤 + 加固敏感配置默认值
- 完善 README，让面试官 1 分钟看懂项目亮点

### ✨ Added（新增）
- **[文档]** 新增 [README.md](README.md) —— 完整项目说明 + RAG 6 版演进表 + 性能优化清单 + 安全机制
- **[代码]** RagService 新增 `ChunkBatchItem` record —— 批量 Embedding 时的中间对象，保存 (chunk, title, chunkText) 三元组
- **[代码]** RagService 新增批量 Embedding 耗时统计日志（含平均 ms/分块）

### 🔒 Security（安全加固）
- **[高危]** `application.yml` JWT_SECRET 默认值：`MySecretKeyForJWT2025!@#...`（复杂但公开，可伪造管理员 token）→ `change-me-in-production`（明显不合法占位符，fail-fast）
- **[高危]** `Dockerfile` / `Dockerfile.local` 的 `ENV DB_PASSWORD=123456` → `change-me-in-production`，避免镜像分发后内置弱密码
- **[中危]** `application.yml` PG_PASSWORD 默认值 `123456` → `change-me-in-production`
- **[中危]** 根目录 `docker-compose.yml` + 后端目录 `docker-compose.yml` + `.env.example` 的 JWT_SECRET 默认值统一为 `change-me-in-production`

> **设计原则**：默认值不是为了让开发方便，而是占位符。改成明显不合法的字符串，让忘配置时服务 fail-fast 启动失败，而不是用弱密码静默裸奔。

### ♻️ Changed（变更）
- **[性能]** `RagService.rebuildIndex()` 加 `@Transactional(rollbackFor=Exception.class)` —— MySQL 侧 DELETE+INSERT 原子化，失败整体回滚
- **[性能]** `RagService.rebuildIndex()` Embedding 调用从"每条 1 次 HTTP"改为"10 条 1 次批量"——使用 `EmbeddingRequest.builder().inputs(List).build()` + `embeddingModel.call(request)`，降低 ~90% 网络 round-trip
- **[健壮性]** 批量 Embedding 返回数量 mismatch 时主动抛 `IllegalStateException` 触发事务回滚
- **[细节]** `ResultCode` Token 系列错误码从重复的 `A0230/A0230/A0230/A0231` 改为递增的 `A0230/A0231/A0232/A0233`，便于前端区分过期/无效/黑名单
- **[工程]** 根 `.gitignore` 补全前端构建产物 + 系统文件：`node_modules/`、`dist/`、`pg-data/`、`.DS_Store`、`Thumbs.db` 等

### 🗑 Removed（移除）
- **[重命名]** `GlobarExceptionHandler.java`（拼写错误）→ `GlobalExceptionHandler.java`，类名同步修正

### 📊 性能影响
| 指标 | 修改前 | 修改后 |
|------|--------|--------|
| 重建 200 块索引耗时 | ~10s（200 次 HTTP） | ~3s（20 次批量 HTTP） |
| Embedding HTTP 调用次数 | N | ⌈N/10⌉ |
| MySQL 侧失败数据残留 | 有（删半插半） | 0（事务回滚） |

---

## [v1.4] - 2026-08-14 — RAG 异步向量化 + 全局优化

### ✨ Added
- **[RAG]** 新增 `RagAsyncTask` 独立 Bean —— @Async 触发索引重建（**抽独立 Bean 的原因**：@Async 基于 AOP 代理，同类内部方法互调会绕过代理导致注解失效）
- **[配置]** 新增 `AsyncConfig` —— RAG 索引重建专用线程池（core=2, max=4, queue=10, DiscardOldestPolicy）
- **[异常]** `GlobarExceptionHandler` 新增 `AccessDeniedException` 处理（@PreAuthorize 校验失败时返回 403）
- **[异常]** `ResultCode` 新增 `FORBIDDEN` 错误码

### ♻️ Changed
- **[启动]** `AiSpingbootApplication` 开启 `@EnableAsync`
- **[业务]** `KnowledgeService` 改为异步触发向量化，发布文章接口从 10s+ → 200ms 响应
- **[安全]** `SecurityConfig` 调整放行路径
- **[安全]** 多个 Controller 补充 `@PreAuthorize` 权限校验注解
- **[业务]** `PsychologicalSupportService` 优化心理支持流程
- **[鉴权]** `JwtAuthticationFilter` 小幅优化

### 📊 性能影响
| 接口 | 修改前 | 修改后 |
|------|--------|--------|
| POST /api/knowledge/article（发布文章） | 10s+ 同步等 Embedding | 200ms 异步触发即返回 |

---

## [v1.3] - 2026-08-14 — PgVector 向量数据库 + 多数据源

### ✨ Added
- **[配置]** 新增 `PgVectorConfig` —— PostgreSQL pgvector 数据源配置，HNSW 索引
- **[配置]** 新增 `PrimaryDataSourceConfig` —— 将 MySQL 声明为主数据源，避免 JPA 误用 PgVector
- **[部署]** `docker-compose.yml` 新增 `pgvector/pgvector:pg16` 服务
- **[部署]** `.env.example` 新增 `PG_PASSWORD` 环境变量

### ♻️ Changed
- **[RAG]** `RagService` 重构为基于 PgVector 的向量存储和检索，使用 HNSW 近似最近邻索引
- **[配置]** `application.yml` 排除 `JdbcRepositoriesAutoConfiguration` 避免多数据源冲突
- **[配置]** `SecurityConfig` 放行 PG 相关初始化路径
- **[依赖]** `pom.xml` 新增 `org.postgresql` 驱动依赖

### 🗑 Removed
- **[RAG]** 移除 v1.2 的 JSON 文件存储方案（OOM 风险）

### 📊 性能影响
| 指标 | 修改前（文件存储） | 修改后（PgVector） |
|------|-------------------|-------------------|
| 10 万向量检索 | OOM | 毫秒级返回 |
| 进程重启重建 | 30s+ | 0s（持久化） |

---

## [v1.2] - 2026-08-14 — Spring AI Embedding + 文件存储

### ✨ Added
- **[配置]** 新增 `EmbeddingConfig` —— 对接 SiliconFlow / 阿里云 / OpenAI 兼容 Embedding API
- **[依赖]** `pom.xml` 新增 `spring-ai-starter-model-openai` 1.0.0

### ♻️ Changed
- **[RAG]** `RagService` 重构为基于 Spring AI Embedding 的向量检索（BAAI/bge-large-zh-v1.5，1024 维）
- **[RAG]** `RagController` 适配新方案

### 🗑 Removed
- **[RAG]** 删除 v1.1 的 TF-IDF 相关类：`TfidfVectorStore`、`DocumentChunker`

### 📊 检索质量提升
| 用户输入 | v1.1（TF-IDF） | v1.2（Embedding） |
|----------|---------------|-------------------|
| "心里堵得慌" | ❌ 匹配不到"焦虑症" | ✅ 语义匹配成功 |

---

## [v1.1] - 2026-08-14 — TF-IDF + 余弦相似度（初代 RAG）

### ✨ Added
- **[RAG]** 新增 `TfidfVectorStore` —— 纯 Java 实现 TF-IDF 向量化
- **[RAG]** 新增 `DocumentChunker` —— 文章分块
- **[RAG]** 新增 `RagService` —— 检索 + Prompt 增强
- **[RAG]** 新增 `RagController` —— RAG 管理接口（重建/状态）

### 🎯 触发原因
用户反馈"抑郁症自测怎么测？"AI 给了幻觉回答 → 必须引入知识检索兜底

---

## [v1.0] - 2026-08-14 — 项目初始化

### ✨ Added
- **[架构]** Spring Boot 3.5 + MyBatis-Plus 3.5 + Spring Security + JWT 完整分层架构
- **[鉴权]** JWT 无状态鉴权 + `JwtAuthticationFilter` + `SecurityConfig` 路径白名单
- **[安全]** `CrisisSafetyFilter` —— 危机关键词检测 + 自动追加 400-161-9995 心理热线
- **[安全]** `PromptInjectionGuard` —— Prompt 注入拦截（不调用 LLM 直接 403）
- **[业务]** 用户注册/登录（管理员/普通用户双角色）
- **[业务]** AI 心理咨询对话（Streaming 流式输出）
- **[业务]** 情绪日记（CRUD + 趋势折线图 + AI 关怀建议）
- **[业务]** 知识库管理（分类 CRUD + 文章富文本编辑）
- **[业务]** 数据分析（用户/咨询/情绪统计）
- **[业务]** 文件上传（图片 + 类型/大小校验）
- **[缓存]** Caffeine 双 TTL 缓存管理器（30min 分类树 + 2min 用户状态）
- **[性能]** 原子 SQL 自增阅读量（避免并发更新丢失）
- **[性能]** HikariCP 参数调优（max-lifetime=30min + leak-detection=60s）
- **[性能]** 列表查询显式选字段排除 LONGTEXT content
- **[工程]** Docker Compose 一键启动（MySQL + Backend + Frontend）
- **[工程]** Knife4j / Swagger 3 API 文档
- **[工程]** Spring Boot Actuator 健康检查
- **[工程]** 自定义 SQL 计数拦截器（防 N+1）
- **[工程]** 多环境配置（dev / prod）
- **[测试]** `JwtTokenUtilTest` / `UserServiceTest` / `KnowledgeServiceTest` 单元测试

---

## 版本演进路线图

```
v2.0 ──── 事务 + 批量 Embedding + 安全加固 + README     [当前最终版]
  │
v1.4 ──── 异步向量化 + 全局优化
  │
v1.3 ──── PgVector 向量数据库 + 多数据源
  │
v1.2 ──── Spring AI Embedding + 文件存储
  │
v1.1 ──── TF-IDF + 余弦相似度（初代 RAG）
  │
v1.0 ──── 项目初始化（纯 AI 对话，无知识检索）
```

每个版本均打 `vX.Y-base` tag，可通过以下命令对比任意两个版本：

```bash
git diff v1.0-base v2.0          # 看完整演进
git diff v1.3-base v1.4          # 看异步化改动
git diff v1.4-base v2.0          # 看本次 v2.0 改动
```

---

## 面试视角的关键决策点

| 版本 | 关键决策 | 取舍逻辑 |
|------|---------|---------|
| v1.1 → v1.2 | 关键词匹配 → 语义检索 | TF-IDF 解决不了"心里堵得慌"≠"焦虑症"的语义问题 |
| v1.2 → v1.3 | 文件存储 → PgVector | 10 万向量 OOM + 重启重建 30s，必须上专业向量库 |
| v1.3 → v1.4 | 同步 → 异步 | Embedding 调外部 API 阻塞主流程，10s 接口响应不可接受 |
| v1.4 → v2.0 | 逐条 → 批量 + 无事务 → 有事务 | HTTP round-trip 太多 + 失败时 MySQL 残留脏数据 |
| v2.0 安全加固 | 弱密码默认值 → change-me-in-production | fail-fast 原则：忘配置应该启动失败，而不是静默裸奔 |
