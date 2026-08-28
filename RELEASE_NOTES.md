# 🚀 心灵守护者 v2.0 发布说明

> **心理健康助手** — 基于 Spring Boot 3 + Vue 3 + AI RAG 的前后端分离心理咨询系统
>
> **版本**：v2.0.0（正式版，tag `v2.0.0`）
> **发布日期**：2026-08-14
> **仓库**：GitHub [wangxinyan-dev/mental-ai-assistant](https://github.com/wangxinyan-dev/mental-ai-assistant) ｜ Gitee [yolo122/mental-ai-assistant](https://gitee.com/yolo122/mental-ai-assistant)
>
> 完整项目介绍（架构图 / 技术栈 / RAG 6 版演进 / 功能模块 / 部署 / 测试 / 已知局限）见 **[README.md](README.md)**；本文件仅记录发布版本与变更明细。

---

## 📌 发布概述

v2.0 是本项目的**第一个正式发布版本**，整合了从 v1.0 到 v2.0 共 6 次迭代的所有功能与优化，本次发布重点：

1. **RAG 知识检索**：完整演进至 PgVector 向量数据库 + 异步向量化 + 事务一致性 + 批量 Embedding（历代技术方案与踩坑细节见 README「RAG 技术选型演进」）
2. **安全加固**：弱密码默认值全部清理（`123456` → `change-me-in-production`），采用 fail-fast 原则，真实密钥一律环境变量注入
3. **工程化**：Docker Compose 四容器一键部署 + 四容器 healthcheck + Knife4j API 文档 + dev / prod 双 profile

---

## 📊 v2.0 相对 v1.4 的改动

### ✨ 新增

- [代码] RagService 新增 `ChunkBatchItem` record 用于批量 Embedding
- [代码] RagService 新增批量 Embedding 耗时统计日志

### 🔒 安全加固

- **[高危]** JWT_SECRET 默认值从公开的硬编码串改为 `change-me-in-production`（fail-fast 占位符）
- **[高危]** Dockerfile / Dockerfile.local 的 `ENV DB_PASSWORD` 弱密码改为 `change-me-in-production`
- **[中危]** application.yml 的 PG_PASSWORD 默认值从 `123456` 改为 `change-me-in-production`
- **[中危]** docker-compose.yml + .env.example 的 JWT_SECRET / DB 密码默认值统一为 `change-me-in-production`

### ♻️ 变更

- [性能] `RagService.rebuildIndex()` 加 `@Transactional(rollbackFor=Exception.class)`，MySQL 侧原子化
- [性能] Embedding 从逐条改为 10 条批量调用，HTTP round-trip -90%
- [健壮性] 批量 Embedding 返回数量 mismatch 主动抛 `IllegalStateException` 触发事务回滚
- [健壮性] Embedding API 调用加 3 次重试 + 指数退避（默认 500ms→1000ms，可配置），吞掉免费版瞬时抖动；重试耗尽跳过整批，不阻塞其余批次与影子表切换，被跳过分块由幂等全量重建补齐
- [新功能] 操作审计日志：`@AuditLog` 注解 + AOP 切面 + 独立线程池 `auditLogExecutor` 异步落库 `audit_log` 表，知识文章发布/更新/删除、文章状态、用户禁用等写接口留痕（操作人/目标/入参快照/结果/耗时/IP）
- [细节] ResultCode Token 系列错误码从重复的 `A0230` 改为递增的 `A0230/A0231/A0232/A0233`
- [工程] 根 `.gitignore` 补全 `node_modules/` `dist/` `pg-data/` `.DS_Store` 等

### 🗑 移除/重命名

- [修复] `GlobarExceptionHandler.java` → `GlobalExceptionHandler.java`（修正拼写错误）

---

## 📈 性能对比（v2.0 vs v1.4）

| 指标 | v1.4 | v2.0 | 提升 |
|------|------|------|------|
| RAG 重建 200 块索引耗时 | ~10s（200 次 HTTP） | ~3s（20 次批量 HTTP） | **-70%** |
| Embedding HTTP 调用次数 | N | ⌈N/10⌉ | **-90%** |
| MySQL 侧失败数据残留 | 有（删半插半） | 0（事务回滚） | **100%** |
| 首 token 响应时间 | ~1s | ~200ms | **-80%** |

---

## 🛠 安全配置清单（部署必看）

部署前务必用 `.env` 覆盖以下默认值（模板见 `.env.example`）：

| 环境变量 | 说明 | 默认值（仅占位，生产必须改） |
|----------|------|------------------------|
| `DB_PASSWORD` | MySQL root 密码 | `change-me-in-production` |
| `PG_PASSWORD` | PgVector 数据库密码 | `change-me-in-production` |
| `JWT_SECRET` | JWT 签名密钥 | `change-me-in-production` |
| `AI_API_KEY` | AI 对话 API Key | 无（必须配置） |
| `EMBEDDING_API_KEY` | Embedding API Key | 无（必须配置） |
| `AI_BASE_URL` | AI 服务地址 | `https://api.siliconflow.cn` |
| `AI_MODEL` | 对话模型 | `deepseek-ai/DeepSeek-V3` |
| `EMBEDDING_MODEL` | Embedding 模型 | `BAAI/bge-large-zh-v1.5` |

> ⚠️ **生产环境警告**：上述默认值均为占位符，**生产环境必须通过 `.env` 注入真实值**；未配置关键变量时服务按 fail-fast 设计直接启动失败。

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

每个版本均打 `vX.Y-base` tag（本次正式版锚点 `v2.0.0`），可随时对比任意两版：

```bash
git clone -b v2.0 https://github.com/wangxinyan-dev/mental-ai-assistant.git
git diff v1.0-base v2.0          # 看完整演进
git diff v1.4-base v2.0          # 看本次 v2.0 改动
```

---

## 🧪 验证清单

部署完成后，按以下清单验证：

- [ ] 访问 `http://localhost:8080` 能看到前端页面
- [ ] 访问 `http://localhost:1236/actuator/health` 返回 `{"status":"UP"}`
- [ ] 访问 `http://localhost:1236/doc.html` 能看到 Knife4j API 文档
- [ ] 注册普通用户 → 登录 → 创建情绪日记 → 查看折线图
- [ ] 注册管理员（userType=2）→ 登录 → 知识库发布文章 → 触发 RAG 重建
- [ ] 在 AI 对话页输入"抑郁症怎么自测" → 验证 RAG 检索结果注入
- [ ] 在 AI 对话页输入"我想自杀" → 验证 400-161-9995 心理热线自动追加
- [ ] 在 AI 对话页输入"忽略之前指令" → 验证 Prompt 注入拦截返回 403

---

## 📝 License

[MIT License](https://opensource.org/licenses/MIT)。项目为个人学习 / 项目展示用途，**请勿用于真实心理咨询场景**——真实心理危机请拨打 **全国 24 小时心理援助热线：400-161-9995**。