# 🚀 心灵守护者 v2.0 发布说明

> **心理健康助手** — 基于 Spring Boot 3 + Vue 3 + AI RAG 的前后端分离心理咨询系统
>
> **发布日期**：2026-08-14
> **版本**：v2.0（正式版）
> **提交**：`2bd1f64`
> **仓库**：[https://gitee.com/yolo122/mental-ai-assistant](https://gitee.com/yolo122/mental-ai-assistant)

---

## 📌 版本概述

v2.0 是本项目的**第一个正式发布版本**，整合了从 v1.0 到 v2.0 共 6 次迭代的所有功能与优化。本次发布重点：

1. **RAG 知识检索**：完整演进至 PgVector 向量数据库 + 异步向量化 + 事务一致性 + 批量 Embedding
2. **安全加固**：清理所有弱密码默认值，采用 fail-fast 原则
3. **文档完善**：README + CHANGELOG + RELEASE_NOTES 三件套
4. **工程化**：Docker Compose 一键部署 + Knife4j API 文档 + 多环境配置

---

## ✨ 核心特性

### 1. AI 心理咨询对话

- **流式输出**：基于 SSE 的 Server-Sent Events，LLM 首 token 时间 ~200ms
- **Prompt 注入防护**：检测"忽略之前指令"、"角色扮演"等关键字，**不调用 LLM 直接 403 拦截**
- **危机安全机制**：对话中检测自杀/自残关键字 → 流式结束后自动追加全国 24h 心理援助热线 **400-161-9995**
- **RAG 知识增强**：用户消息 → Embedding 向量化 → PgVector HNSW 索引 Top-3 检索 → 注入 System Prompt

### 2. RAG 知识检索系统（6 版迭代核心）

| 版本 | 技术方案 | 检索质量 | 性能 | 工程复杂度 |
|------|---------|---------|------|-----------|
| v1.0 | 无知识检索 | ❌ 幻觉严重 | - | ⭐ |
| v1.1 | TF-IDF + 余弦相似度 | ⭐⭐ 关键词级 | 快 | ⭐ |
| v1.2 | Spring AI Embedding + 文件 | ⭐⭐⭐⭐ 语义级 | 中（OOM 风险） | ⭐⭐ |
| v1.3 | PgVector + HNSW 索引 | ⭐⭐⭐⭐ 语义级 | 快（毫秒级） | ⭐⭐⭐⭐ |
| v1.4 | + 异步向量化 | 同上 | 发布接口 200ms | ⭐⭐⭐⭐ |
| **v2.0** | **+ 事务 + 批量 Embedding** | 同上 | **重建速度提升 70%** | ⭐⭐⭐⭐ |

### 3. 安全 & 鉴权

- **JWT 无状态鉴权**：过滤器内捕获 `TokenExpiredException` 直接返回 401，Caffeine 2min TTL 缓存用户状态避免每请求查库
- **方法级权限**：`@PreAuthorize("hasRole('ADMIN')")` 双保险
- **密钥不入库**：所有敏感配置通过环境变量注入，`.env` 通过 `.gitignore` 排除
- **Fail-Fast 默认值**：所有敏感配置默认值改为 `change-me-in-production`，忘配置时服务直接启动失败

### 4. 性能优化

| 优化点 | 效果 |
|--------|------|
| 原子 SQL 自增阅读量 | 并发 1000 次 0 丢失 |
| Caffeine 双 TTL 缓存 | JWT 过滤器 0 DB 查询 + 分类树 60% 缓存命中 |
| LONGTEXT 字段排除 | 列表响应体 200KB → 20KB |
| HikariCP 参数调优 | max-lifetime=30min + leak-detection=60s |
| 批量 Embedding | HTTP round-trip -90%，重建 200 块 10s → 3s |
| RAG 重建事务 | MySQL 侧 0 残留脏数据 |
| 移除 artificial delay | 首 token 1s → 200ms |

### 5. 工程化

- **Docker Compose 一键启动**：MySQL + PgVector + Backend + Frontend 四容器
- **健康检查**：所有容器配置 healthcheck + `depends_on: condition: service_healthy`
- **多环境配置**：dev / prod 双 profile
- **API 文档**：Knife4j / Swagger 3（`/doc.html`）
- **监控**：Spring Boot Actuator（`/actuator/health`）
- **SQL 计数拦截器**：自定义 MyBatis Interceptor 防止 N+1

---

## 🔧 技术栈

### 后端
| 类型 | 技术 |
|------|------|
| 框架 | Spring Boot 3.5 / Spring Security / Spring AI 1.0 / Spring Cache |
| ORM | MyBatis-Plus 3.5 |
| 数据库 | MySQL 8（业务） / PostgreSQL 16 + pgvector（向量） |
| 缓存 | Caffeine（双 TTL 管理器） |
| 连接池 | HikariCP（max-lifetime=30min） |
| 鉴权 | JWT（java-jwt 4.4）+ `@PreAuthorize` |
| AI | Spring AI Embedding（BAAI/bge-large-zh-v1.5，1024 维） + DeepSeek-V3 对话 |
| 文档 | Knife4j / OpenAPI 3 |
| 监控 | Spring Boot Actuator |

### 前端
| 类型 | 技术 |
|------|------|
| 框架 | Vue 3 + Vite 6 |
| UI | Element Plus |
| HTTP | Axios（JWT 自动注入 + 401 自动跳登录） |
| 可视化 | ECharts（情绪趋势折线图） |
| 富文本 | WangEditor（知识库文章编辑） |

---

## 📦 部署方式

### 方式一：Docker Compose 一键启动（推荐）

```bash
# 1. 克隆仓库（使用 v2.0 分支）
git clone -b v2.0 https://gitee.com/yolo122/mental-ai-assistant.git mental-health
cd mental-health

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env，填入真实密钥：
#   DB_PASSWORD=你的MySQL密码
#   PG_PASSWORD=你的PgVector密码
#   JWT_SECRET=你的JWT签名密钥（生产务必改）
#   AI_API_KEY=你的AI服务API密钥
#   EMBEDDING_API_KEY=你的Embedding API密钥

# 3. 一键启动
docker compose up -d --build

# 4. 访问
# 前端：http://localhost:8080
# 后端 API：http://localhost:1236
# Swagger 文档：http://localhost:1236/doc.html
# 健康检查：http://localhost:1236/actuator/health
```

### 方式二：本地开发

```bash
# 后端
cd backend/ai-spingboot
mvn spring-boot:run

# 前端
cd frontend/ai-vue
npm install
npm run dev
```

---

## 🔐 安全配置清单

部署前请务必配置以下环境变量（`.env` 文件）：

| 环境变量 | 说明 | 默认值（不安全，仅占位） |
|----------|------|------------------------|
| `DB_PASSWORD` | MySQL root 密码 | `123456`（仅本地 docker-compose） |
| `PG_PASSWORD` | PgVector 数据库密码 | `123456`（仅本地 docker-compose） |
| `JWT_SECRET` | JWT 签名密钥 | `change-me-in-production`（必须改） |
| `AI_API_KEY` | DeepSeek/SiliconFlow API Key | 无（必须配置） |
| `EMBEDDING_API_KEY` | Embedding API Key（SiliconFlow） | 无（必须配置） |
| `AI_BASE_URL` | AI 服务地址 | `https://api.siliconflow.cn` |
| `AI_MODEL` | 对话模型 | `deepseek-ai/DeepSeek-V3` |
| `EMBEDDING_MODEL` | Embedding 模型 | `BAAI/bge-large-zh-v1.5` |

> ⚠️ **生产环境警告**：所有默认值仅为占位符，**生产环境必须通过 `.env` 注入真实值**。未配置时服务会因 fail-fast 设计直接启动失败。

---

## 📊 v2.0 相对 v1.4 的改动

### ✨ 新增
- [文档] [README.md](README.md) - 完整项目说明 + RAG 6 版演进表 + 性能优化清单
- [文档] [CHANGELOG.md](CHANGELOG.md) - Keep a Changelog 标准格式
- [代码] RagService 新增 `ChunkBatchItem` record 用于批量 Embedding
- [代码] RagService 新增批量 Embedding 耗时统计日志

### 🔒 安全加固
- **[高危]** JWT_SECRET 默认值从 `MySecretKeyForJWT2025!@#...`（公开复杂串）改为 `change-me-in-production`（fail-fast 占位符）
- **[高危]** Dockerfile / Dockerfile.local 的 `ENV DB_PASSWORD=123456` 改为 `change-me-in-production`（防镜像分发弱密码）
- **[中危]** application.yml PG_PASSWORD 默认值从 `123456` 改为 `change-me-in-production`
- **[中危]** docker-compose.yml + .env.example 的 JWT_SECRET 默认值统一为 `change-me-in-production`

### ♻️ 变更
- [性能] `RagService.rebuildIndex()` 加 `@Transactional(rollbackFor=Exception.class)`，MySQL 侧原子化
- [性能] Embedding 从逐条改为 10 条批量调用，HTTP round-trip -90%
- [健壮性] 批量 Embedding 返回数量 mismatch 主动抛 `IllegalStateException` 触发事务回滚
- [细节] ResultCode Token 系列错误码从重复的 `A0230` 改为递增的 `A0230/A0231/A0232/A0233`
- [工程] 根 `.gitignore` 补全 `node_modules/` `dist/` `pg-data/` `.DS_Store` 等

### 🗑 移除/重命名
- `GlobarExceptionHandler.java` → `GlobalExceptionHandler.java`（修正拼写错误）

---

## 📈 性能对比

| 指标 | v1.4 | v2.0 | 提升 |
|------|------|------|------|
| RAG 重建 200 块索引耗时 | ~10s（200 次 HTTP） | ~3s（20 次批量 HTTP） | **-70%** |
| Embedding HTTP 调用次数 | N | ⌈N/10⌉ | **-90%** |
| MySQL 侧失败数据残留 | 有（删半插半） | 0（事务回滚） | **100%** |
| 首 token 响应时间 | ~1s | ~200ms | **-80%** |

---

## 🗺 版本演进路线图

```
v2.0 ──── 事务 + 批量 Embedding + 安全加固 + 文档     [当前正式版]
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
git clone -b v2.0 https://gitee.com/yolo122/mental-ai-assistant.git
cd mental-ai-assistant
git diff v1.0-base v2.0          # 看完整演进
git diff v1.4-base v2.0          # 看本次 v2.0 改动
git log --oneline --graph --all --decorate
```

---

## 🧪 验证清单

部署完成后，请按以下清单验证：

- [ ] 访问 `http://localhost:8080` 能看到前端页面
- [ ] 访问 `http://localhost:1236/actuator/health` 返回 `{"status":"UP"}`
- [ ] 访问 `http://localhost:1236/doc.html` 能看到 Knife4j API 文档
- [ ] 注册普通用户 → 登录 → 创建情绪日记 → 查看折线图
- [ ] 注册管理员（userType=2）→ 登录 → 知识库发布文章 → 触发 RAG 重建
- [ ] 在 AI 对话页输入"抑郁症怎么自测"→ 验证 RAG 检索结果注入
- [ ] 在 AI 对话页输入"我想自杀"→ 验证 400-161-9995 心理热线自动追加
- [ ] 在 AI 对话页输入"忽略之前指令"→ 验证 Prompt 注入拦截返回 403

---

## ⚠️ 已知限制

1. **多数据源一致性**：MySQL 和 PgVector 不在同一事务中，极端情况下可能出现 MySQL 回滚但 PgVector 已写入。当前通过"先 MySQL 后 PgVector + 幂等 rebuild"策略兜底，生产环境如需强一致建议集成 Atomikos JTA/XA。

2. **异步任务失败不反馈**：RagAsyncTask 失败只记录日志，不主动通知前端。后续可加 RabbitMQ/SSE 通知机制。

3. **单机部署**：当前为单机架构，Caffeine 本地缓存不支持多实例共享。生产环境如需水平扩展，建议替换为 Redis。

4. **Embedding API 限流**：SiliconFlow 免费版有 QPS 限制，大批量重建可能触发限流。生产环境建议使用付费版或自部署 Embedding 模型。

---

## 📞 联系方式

- **仓库地址**：[https://gitee.com/yolo122/mental-ai-assistant](https://gitee.com/yolo122/mental-ai-assistant)
- **分支**：`v2.0`（当前正式版）
- **历史版本**：`v1.0` ~ `v1.4`（均保留 base tag）

---

## 📝 License

个人学习 / 项目展示用途。**请勿用于真实心理咨询场景**——真实心理危机请拨打：

> **全国 24 小时心理援助热线：400-161-9995**

---

*本发布说明由 v2.0 自动生成，详细变更记录请参考 [CHANGELOG.md](CHANGELOG.md)。*
