# 简历项目描述模板

> 适用场景：秋招简历"项目经历"模块
>
> **使用建议**：
> - 详细版：放在简历重点项目（前 1-2 个），占用 8-12 行
> - 简洁版：放在简历次要项目（后 1-2 个），占用 4-6 行
> - 投递岗位：Java 后端 / AI 应用开发 / 全栈
>
> **配套文件**：
> - 项目源码：[https://gitee.com/yolo122/mental-ai-assistant](https://gitee.com/yolo122/mental-ai-assistant) (v2.0 分支)
> - 演示视频：5 分钟视频，链接见 B 站 / 云盘
> - 压测报告：[tests/jmeter/README.md](../tests/jmeter/README.md)

---

## 📝 详细版（推荐用于重点项目）

### 项目名称

**心灵守护者 - AI 心理健康助手系统** | 个人项目 | 2026.07 - 2026.08

### 技术栈

Spring Boot 3 · MyBatis-Plus · Spring Security · JWT · Spring AI · PgVector · Caffeine · MySQL · Docker · Vue 3 · Vite · Element Plus

### 项目描述

基于前后端分离架构的 AI 心理咨询系统，核心功能包括 AI 流式对话（RAG 知识检索增强）、情绪日记分析、知识库管理、用户鉴权等。RAG 模块经历 6 版技术迭代，从 TF-IDF 关键词匹配演进至 PgVector 向量数据库 + 异步批量 Embedding，单次重建耗时从 10s+ 降至 200ms。

### 核心职责

- **【RAG 检索系统】** 设计并实现 6 版演进的知识检索模块：v1.1 TF-IDF + 余弦相似度 → v1.2 Spring AI Embedding → v1.3 PgVector + HNSW 索引 → v1.4 @Async 异步线程池 → v2.0 @Transactional + 批量 Embedding。Embedding HTTP 调用次数降低 90%，重建耗时 -70%，MySQL 侧事务保证无脏数据残留。
- **【性能优化】** 用原子 SQL `read_count = read_count + 1` 解决高并发阅读量自增丢失（JMeter 200 并发 1000 请求验证 0 丢失）；Caffeine 双 TTL 缓存管理器（30min 分类树 + 2min 用户状态），JWT 过滤器通过 Token claims 解析用户信息实现 0 DB 查询，登录接口缓存命中率 99.9%；MyBatis-Plus 显式选字段排除 LONGTEXT，列表响应体从 200KB 降至 20KB。
- **【安全合规】** Spring Security + JWT 无状态鉴权，Token 过滤器捕获 ExpiredJwtException 返回 401；自定义 `@PreAuthorize` 方法级权限校验；`PromptInjectionGuard` 拦截"忽略指令"等 Prompt 注入攻击（不调用 LLM 直接 403）；`CrisisSafetyFilter` 检测"自杀/自残"关键词，对话结束后自动追加 400-161-9995 心理援助热线。
- **【工程化】** Docker Compose 一键部署 MySQL + PgVector + Backend + Frontend 四容器，配置 healthcheck 健康检查和 depends_on 顺序启动；多环境配置（dev/prod profile）；密钥全环境变量注入 + `.gitignore` 排除，默认值统一改为 `change-me-in-production` 践行 fail-fast 原则。
- **【文档与测试】** 输出 README、CHANGELOG、RELEASE_NOTES 三份配套文档；JMeter 压测脚本覆盖 3 大场景（文章详情并发自增、登录缓存命中、RAG 异步响应）；5 个核心类单元测试覆盖 JWT、用户鉴权、知识库 CRUD、RAG 重建关键路径。

### 项目成果

- 单次 RAG 重建从 10s+ 同步阻塞 → 200ms 异步触发（@Async 独立 Bean + DiscardOldestPolicy 线程池）
- 200 并发场景阅读量自增 **0 丢失**（原子 SQL vs 旧版先查再改丢 30-50 个）
- 登录接口缓存命中率 99.9%，SQL 查询次数从 1000 降至 1
- 6 版 RAG 迭代均有 git tag 快照保留，支持版本对比学习

---

## 📝 简洁版（推荐用于次要项目）

### 项目名称

**心灵守护者 - AI 心理健康助手** | 个人项目 | 2026.07 - 2026.08

### 技术栈

Spring Boot 3 · Spring AI · PgVector · Caffeine · JWT · MyBatis-Plus · Docker · Vue 3

### 项目描述

前后端分离的 AI 心理咨询系统，核心是 RAG 知识检索增强的 AI 对话。RAG 模块经历 6 版迭代：TF-IDF → Spring AI Embedding → PgVector + HNSW 索引 → @Async 异步化 → @Transactional + 批量 Embedding，重建耗时 -70%。

### 核心亮点

- **RAG 6 版演进**：从关键词匹配到向量数据库，每次迭代都有明确业务驱动，Embedding HTTP 调用降低 90%
- **并发安全**：原子 SQL 自增阅读量，JMeter 200 并发 1000 请求验证 0 丢失；@Transactional 保证 MySQL/PgVector 双库一致性
- **缓存优化**：Caffeine 双 TTL 缓存（30min 分类树 + 2min 用户状态），JWT 过滤器 0 DB 查询，登录命中率 99.9%
- **安全合规**：Prompt 注入拦截（不调用 LLM 直接 403）+ 危机关键词检测自动追加 400-161-9995 心理热线
- **工程化**：Docker Compose 四容器一键部署 + healthcheck；JMeter 3 场景压测；6 版 git tag 保留对比

---

## 📝 一句话版（用于简历空间极紧张时）

**心灵守护者 AI 心理健康助手** (Spring Boot 3 + Spring AI + PgVector + Vue 3)：
基于 RAG 的 AI 对话系统，6 版技术迭代（TF-IDF → Embedding → PgVector + 异步批量），原子 SQL 解决高并发阅读量自增丢失（JMeter 200 并发 0 丢失），Caffeine 双 TTL 缓存命中率 99.9%，Docker Compose 一键部署。

---

## 🎯 面试高频追问预演（背熟这些就稳了）

### Q1：你这个项目最大的难点是什么？

> "RAG 知识检索的技术选型。一开始用 TF-IDF + 余弦相似度，纯 Java 实现，但用户输入'心里堵得慌'匹配不到'焦虑症'，关键词级检索满足不了语义需求。所以换成 Spring AI Embedding 用 BAAI/bge-large-zh-v1.5 1024 维向量，但文件存储方案在 10 万向量时 OOM。最后换成 PgVector + HNSW 索引，毫秒级返回。这过程中还要处理多数据源（MySQL 主库 + PgVector 副库）配置冲突，我写了 PrimaryDataSourceConfig 把 MySQL 声明为主数据源，排除 JdbcRepositoriesAutoConfiguration 避免 JPA 抢 PgVector。"

### Q2：为什么用 PgVector 不用 Milvus？

> "三方面考虑：
> 1. 部署复杂度：Milvus 要 3 个容器（Milvus + MinIO + Etcd），PgVector 只要一个 pgvector/pgvector 镜像
> 2. 数据一致性：MySQL 和 PostgreSQL 同 docker-compose 部署，通过 chunk_id 关联，全量重建时事务保证原子性
> 3. 规模匹配：心理文章规模不会上万篇，10 万向量以下 HNSW 已经毫秒级返回，没必要上 Milvus
>
> 如果规模到百万级，我会换 Milvus 或者 Qdrant。"

### Q3：@Async 为什么要抽独立 Bean？

> "@Async 基于 Spring AOP 代理实现。同类内部方法互调会绕过代理，注解直接失效。所以我把异步方法抽到 RagAsyncTask 这个独立 Bean 里，通过 @Autowired 注入到主 Service 调用。同时配了 AsyncConfig 线程池：core=2, max=4, queue=10, RejectedExecutionHandler=DiscardOldestPolicy，保证最新向量化任务必处理。"

### Q4：批量 Embedding 为什么选 10 条一批？

> "三个考虑：
> 1. 上游 API 限制：SiliconFlow Embedding 单请求 1536 token，10 条 chunk 大概 3000-5000 token，批量后不会超限
> 2. 失败重试代价：10 条一批失败重试可控，50 条一批失败一次浪费 50 次 Embedding 的钱
> 3. 边际收益：10 条比 1 条降低 90% round-trip，50 条只多降 3%，选 90% 的拐点"

### Q5：原子 SQL 怎么解决并发更新丢失？

> "旧版代码是'先 SELECT read_count，再 UPDATE read_count = read_count + 1'，这种 read-modify-write 模式在并发下会丢失更新：线程 A 读到 100，线程 B 也读到 100，A 写 101，B 写 101，本应是 102。改成原子 SQL `UPDATE article SET read_count = read_count + 1 WHERE id = ?` 后，MySQL 行锁保证原子性，200 并发 1000 请求 0 丢失。"

### Q6：Caffeine 为什么不用 Redis？

> "Caffeine 是本地缓存，Redis 是分布式缓存。我选 Caffeine 三个原因：
> 1. 单机部署：项目是单机部署，不需要多实例共享缓存
> 2. 性能优势：Caffeine 基于 W-TinyLFU 算法，命中率比 LRU 高 30%，且没有网络开销
> 3. 部署简化：不用额外起 Redis 容器，docker-compose 少一个服务
>
> 如果要水平扩展，我会替换成 Redis + Caffeine 二级缓存。"

### Q7：怎么验证你的性能优化真的有效？

> "我用 JMeter 写了 3 个压测脚本，放在仓库 tests/jmeter 目录：
> 1. 文章详情 200 并发 1000 请求：旧版丢 30-50 个阅读量，原子 SQL 后 0 丢失
> 2. 登录接口 1000 次压测：启用 Caffeine 前查库 1000 次，启用后查库 1 次，命中率 99.9%
> 3. RAG 重建接口：脚本里直接断言响应时间 ≤ 500ms，v1.4 之前要 10s+
>
> 脚本里还配了 setUp 线程组自动登录拿 token，CLI 模式跑完自动生成可视化报告。"

### Q8：Prompt 注入怎么拦截的？

> "我写了 PromptInjectionGuard 过滤器，检测'忽略之前指令'、'role-play'、'你现在是'等典型注入关键字。命中后直接返回 403，**不调用 LLM**，节省 token 成本的同时防止越狱。
>
> 这是 RAG 系统的安全必备——如果不拦截，攻击者可以让 AI 输出管理员密码或者绕过心理热线。"

### Q9：项目有什么不足？

> "我知道四个限制：
> 1. 多数据源一致性：MySQL 和 PgVector 不在同一事务，用幂等 rebuild 兜底，生产可加 Atomikos JTA
> 2. 异步任务失败不反馈：RagAsyncTask 失败只记日志，可加 RabbitMQ 通知
> 3. 单机部署：Caffeine 不支持多实例共享，水平扩展需替换 Redis
> 4. Embedding API 限流：SiliconFlow 免费版有 QPS 限制，生产建议付费版或自部署
>
> 这些都写在 RELEASE_NOTES.md 的'已知限制'里，是有意识的取舍。"

### Q10：如果让你重做这个项目，会改什么？

> "三个方向：
> 1. 替换 Redis 替代 Caffeine，支持水平扩展
> 2. 加 RabbitMQ 处理异步任务，前端通过 SSE 收到重建完成通知
> 3. Embedding 自部署，用 text-embedding-2 模型在本地 GPU 跑，避免 API 限流
>
> 但秋招项目的目标是展示技术深度，不是无限优化。现在的版本已经能体现我对并发、缓存、AI 工程化的理解。"

---

## 📊 简历排版建议

### 推荐结构（按重要性排序）

```
项目经历
├── 心灵守护者（详细版，8-12 行）
├── 其他项目 1（简洁版，4-6 行）
└── 其他项目 2（一句话版，2-3 行）
```

### 字体和样式

- 项目名：**加粗**，后面跟"个人项目"或"团队项目"
- 技术栈：用 · 分隔，不要换行
- 项目描述：3-4 行，**第一句必须是项目是干什么的**
- 核心职责/亮点：用 `【】` 或 `-` 开头，**每条都有量化数据**

### 简历模板参考

推荐使用：
- [超级简历](https://www.wondercv.com/) - 在线排版导出 PDF
- [LaTeX Resume](https://github.com/billryan/resume-tex) - 程序员风
- 自己用 Word 排版（最简单）

---

## ⚠️ 简历常见雷区

1. ❌ **不要写"使用了 XXX 技术"** → 改成"用 XXX 解决了 YYY 问题"
2. ❌ **不要写"提高了性能"** → 改成"QPS 从 100 提升到 500"
3. ❌ **不要写"实现登录功能"** → 改成"JWT 无状态鉴权 + Token 过期自动返回 401"
4. ❌ **不要罗列技术栈不写应用场景** → 每个技术都要带"用它解决了什么"
5. ❌ **不要写"了解 XXX"** → 面试官会问，写"熟悉"或"精通"就要能扛追问
6. ❌ **不要把测试工具写在技术栈** → JMeter、Postman 不算技术栈
7. ❌ **不要写项目代码行数** → 面试官不在乎几万行，在乎深度

---

## 📞 最后建议

1. **简历投递前最后检查**：项目链接能不能打开，分支是不是 v2.0
2. **面试前一晚**：把 Q1-Q10 的话术背一遍，对着镜子说
3. **面试现场**：带电脑，万一面试官要现场演示，5 分钟视频可以应急
4. **HR 面**：重点讲项目背景（心理健康赛道的意义）和团队协作（即使是个人项目也要讲需求分析、设计、测试、部署全流程）

---

*本模板配合 v2.0 项目使用，所有数据均来自 JMeter 压测和实际验证。*
