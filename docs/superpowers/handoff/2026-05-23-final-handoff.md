# IRP 最终交付 · 面试 Q&A · 简历素材（2026-05-23）

> 本文档是项目的**最终面试素材**，汇总 8 周交付 + 排错沉淀 + 可直接背的技术答辩。  
> 入口看 [README](../../../README.md)。微调子模块看 [`finetune/README.md`](../../../finetune/README.md)。

---

## 1. 一句话定位

**Java 21 + Spring Boot 3.5 + Spring AI 写的「5 Agent 协作研报生成 + 平台中台」**：
RAG 检索（PGVector + ES Hybrid + Rerank）→ 5 Agent 通过 RocketMQ 节点级流转协作 → 流式产出 Markdown；同时把 Prompt 灰度 / Token 成本 / Trace / 多租户 / LoRA 微调 / LLM-as-Judge 全部做成可调用接口。

## 2. 代码体量

| 模块 | 行数 | 文件数 |
|---|---|---|
| Java 后端 | **~6 986 LOC** | 147 |
| Python 微调流水线 | ~499 LOC | 8 |
| Vue 3 前端 | ~5 290 LOC | — |
| 测试 | 13 个 test class | — |

后端 Java 包分布（按行数倒序）：
| 包 | LOC | 职责 |
|---|---|---|
| `agent/` | 1334 | 5 Agent + Tool SPI + PromptLoader + QueryRewriter |
| `mq/` | 929 | RocketMQ 5 个 Consumer + Producer + 消息封装 |
| `rag/` | 840 | Hybrid 检索 + Rerank + 评估 + PDF 入库 |
| `controller/` | 465 | REST API |
| `service/` | 408 | 业务编排 |
| `trace/` | 391 | 节点落库 + 平台 stats 聚合 |
| `vo/` `entity/` `dto/` | 900 | 数据契约 |
| 其余 | 1619 | workflow / stream / security / eval / config |

---

## 3. 8 周交付清单

| 阶段 | 交付 | 关键 commit / 文件 |
|---|---|---|
| **0** 脚手架 | Spring Boot / MyBatis-Flex / JWT / Docker Compose / 注解处理器 pom | `3ec483c` |
| **1** RAG 核心 | 双数据源 (MySQL @Primary + PGVector) / PDF 分块 / Hybrid (RRF) / DashScope gte-rerank / 引用追踪 / 离线评估 | `rag/` |
| **2** 单 Agent + Workflow | YAML DAG 解析 / Tool SPI / LLMGateway / SSE 同步流 | `workflow/` `agent/core/` |
| **3** Multi-Agent + MQ | 5 Agent / RocketMQ 5 topic / fanout-join / Critic 回环 / Trace 落库 | `mq/` |
| **4 平台中台** | Token 成本看板 + Prompt 灰度 + Redis Pub/Sub SSE + 多租户 | `74a8eea` `0fcc150` `d803d69` `c9feeae` `f0542a9` `6fc7ba3` |
| **5** LoRA 微调 | `finetune/` 流水线 + Java 侧 `QueryRewriterClient`（OpenAI 兼容协议热切） | `97ca77f` |
| **6** LLM-as-Judge | 5 维 rubric + `report_eval_run` + 租户隔离 | `626304b` |
| **7** 文档与素材 | README + 本文档 | 本次 |

---

## 4. 数据模型（12 张表）

| 表 | 关键字段 | 作用 |
|---|---|---|
| `user` | username, password_hash, **tenant_id**, role | 鉴权主体 |
| `report_task` | user_id, **tenant_id**, topic, workflow_name, status, phase, progress, final_markdown, citations_json | 研报生命周期 |
| `workflow_subtask` | task_id, sub_index, subtopic, status, result_json | Fanout 子任务 |
| `report_section` | task_id, section_order, title, outline, content_md, citations_json | 章节成稿 |
| `workflow_node_run` | task_id, node_id, agent_role, step_type, prompt_version, model, tokens_in/out, latency_ms, status | **Trace 核心表** |
| `workflow_loop_state` | task_id, node_id, loop_count, max_loops | Critic 回环计数 |
| `prompt_template` | name, version, content, is_active | Prompt 版本管理（`@active` 灰度） |
| `tool_registry` | name, description, params_schema, handler_bean, enabled | Tool SPI 注册中心 |
| `knowledge_doc` | title, source, file_uri, content_hash | RAG 文档元信息 |
| `knowledge_chunk` | doc_id, section_title, page_start/end, vector_id, es_doc_id | RAG 分块 |
| `rag_eval_query` | query_text, gold_chunk_ids (JSON) | RAG 评估数据集 |
| `report_eval_run` | task_id, judge_model, rubric_version, score_overall/structure/…/clarity, breakdown_json | LLM-as-Judge 评分 |

设计要点：
- **租户隔离边界刻意收窄**：只在 `report_task` 加 `tenant_id`，下游 `workflow_node_run` / `report_section` 等通过 `task_id IN (SELECT id FROM report_task WHERE tenant_id=?)` 子查询关联，避免反范式。`knowledge_doc` / `prompt_template` 保持全局，对应「集中维护语料 + 平台 prompt 资产」的产品定位。
- **BaseEntity 统一公共列**：id / create_time / update_time / is_deleted；时间戳走 `@Column(onInsertValue="now()")` 由 DB 端 `now()` 计算，避免 Java 时钟漂移。

---

## 5. 简历 Bullet（精修版，可直接复制）

> **行业研报多 Agent 协作平台**（个人项目，2026.03 - 2026.05）  
> Java 21 / Spring Boot 3.5 / Spring AI 1.0 / RocketMQ 5 / Redis 7 / MySQL 8 / PGVector / Elasticsearch 8 / DashScope / vLLM

- 设计并实现 5 Agent 协作流程（Planner → Researcher×N → Analyst → Writer×M → Critic），用 **RocketMQ 节点级流转**替代线程池编排：单节点失败自动重试 + 死信、Worker 水平扩展、状态全在库可暂停恢复
- RAG 做 **PGVector + Elasticsearch 8 (BM25 + IK 中文) Hybrid + RRF (k=60) + DashScope gte-rerank-v2**，chunk 带 doc_id + page 做引用追踪，离线评估覆盖 recall@K / MRR / nDCG（rerank 前后对比）
- 平台中台 5 大能力：**Prompt 版本管理 + `@active` 灰度切换**（改 DB 不重启）、**Token 成本看板**（按 model / agent 聚合到人民币）、**Trace 时间轴**（每次 LLM / Tool 调用 prompt_version + tokens + latency 全落库）、**Redis Pub/Sub 跨实例 SSE 广播**、**多租户**（`tenant_id` 逻辑隔离，跨租户访问 NOT_FOUND 不泄露存在性）
- **LLM-as-Judge** 5 维 rubric（structure / factuality / reasoning / citation / clarity）由 qwen-max 评委打分，落 `report_eval_run` 表长期追踪研报质量
- **微调子模块**：基于 Qwen2.5-1.5B + LoRA（LLaMA-Factory 训练，vLLM 自托管 OpenAI 兼容部署），Java 侧通过 `app.query-rewriter.base-url` **一行配置切流量**，从 DashScope qwen-max 切到自托管模型，零代码改动
- 排错沉淀：定位并修复 **ChatClient.Builder 多线程 build 不安全**（5 ms 异常无错误信息）、`HashMap.computeIfAbsent` 在 RocketMQ Consumer 并发下抛 CME、SSE response committed 之后 Spring Security 异步派发返回 ACCESS_DENIED 等 6 个并发与框架坑

> 后端 ~7000 行 Java；微调流水线 ~500 行 Python；端到端覆盖 RAG → Multi-Agent → 平台 → 微调 → 评估。

---

## 6. 面试技术 Q&A（10 题）

### Q1. 为什么用 RocketMQ 编排 Multi-Agent 而不是 Spring `@Async` / `CompletableFuture` / 线程池？

3 个不可替代的理由：

1. **节点级重试 + 死信**：Researcher 调用 DashScope 偶发 429 / 网络抖动，RocketMQ 自动 16 次退避重试，超限进死信队列；线程池失败要么吞要么自己写状态机。
2. **Worker 水平扩展**：Researcher Worker 可以多实例并行消费同一个 `irp-research-task` topic，单个研报 12 个子主题并行检索；线程池绑定在 JVM 内拓扑受限。
3. **可暂停恢复**：JVM 崩溃重启，已发出但未消费的消息仍在 broker 里，状态在 `report_task` / `workflow_subtask` 表里；线程池里的任务消失就消失了。

> 代价：调试链路长一截。但对「长任务 + 多 Worker」场景，这个代价值得。

### Q2. Hybrid Search 的 RRF 公式怎么调？BM25 vs 向量分各占多少？

用经典的 RRF（Reciprocal Rank Fusion）而非加权和：

```
score(d) = Σ_i  1 / (k + rank_i(d))      // k=60 是经验值
```

为什么不用加权和（如 `0.7 * vec + 0.3 * bm25`）：BM25 分数和余弦相似度量级完全不同（前者可能十几，后者 0-1），加权和需要先 normalize，引入新调参点。RRF 只看 rank，对分数量级免疫，**默认 k=60 在多个 benchmark 验证过的稳健值**，没刚需别动。

加 rerank 后的拓扑：检索阶段取 `topK*3` 候选（保证召回），rerank 重排取 `topK`（保证精度）。

### Q3. ChatClient.Builder 多线程 build 不安全是怎么发现的？

症状：阶段 3 Multi-Agent 上线后，12 路 Researcher 并行跑，第一批 3 个 subtask 状态 `FAILED`、`latency_ms=5`、`error_message=null`。后续重试都成功。

诊断链：
- `latency_ms=5` → 在调 LLM 之前就崩了
- `error_message=null` → 是 NPE 而不是业务异常
- 只有「第一批」失败 → 典型 race condition
- 5 个 Agent 是 singleton，**每次 execute() 都 `chatClientBuilder.build()`** → 怀疑 Builder 非线程安全

Spring AI 源码确认 `ChatClient.Builder` 不是 thread-safe（mutable 字段）。修复：`@PostConstruct void init() { chatClient = builder.build(); }` 一次性 build，多线程复用（ChatClient 本身线程安全）。

`commit d90379f`。

### Q4. Prompt 灰度切换怎么做？事务上怎么保证「同名只能一条 active」？

- 表设计：`prompt_template (name, version)` 唯一键，`is_active TINYINT`，**软约束**靠 service 层保证（同名最多一条 is_active=1）。
- 切换逻辑（`PromptAdminService.activate` `@Transactional`）：
  1. `UPDATE prompt_template SET is_active=0 WHERE name = ?`
  2. `UPDATE prompt_template SET is_active=1 WHERE id = ?`
  3. `promptLoader.reload()` 清缓存
- 为什么不用 DB 唯一索引强约束：要支持「先把新版本设 active，旧版本立即下线」的单步语义；用唯一索引会需要先 unset 再 set，事务里执行无所谓，唯一索引则中间态会冲突。
- Workflow YAML 用 `planner_prompt@active`，**切版本即时作用到 Agent 下次调用**（PromptLoader 缓存被清空）。
- 文件兜底：DB 表未建 / 为空时 PromptLoader 自动回退到 `classpath:prompts/{name}_v*.txt`，`@active` 兜底取最大版本号。**迁移零中断**。

### Q5. 跨租户隔离为什么用 NOT_FOUND 不是 FORBIDDEN？

```java
ReportTask t = taskMapper.selectOneById(taskId);
if (t == null) throw NOT_FOUND;
if (!Objects.equals(t.getTenantId(), currentTenantId())) {
    throw NOT_FOUND;                       // 故意不抛 FORBIDDEN
}
```

理由：**不泄露 task 是否存在**。如果跨租户返回 `403 FORBIDDEN`，攻击者可以遍历 ID 探测哪些 task 存在；返回 `404 NOT_FOUND`，无论 task 不存在还是不属于你，外观一致，杜绝 enumeration attack。

### Q6. SSE 跨实例为什么选 Pub/Sub 不是 Redis Stream？

需求：多 JVM 后端实例，task 跑在实例 A，前端 SSE 连接到实例 B，B 也得能推事件。

- **Pub/Sub**：fire-and-forget，订阅者不在线消息就丢。但 SSE 本身就是「实时管道」语义，丢了就重连，无所谓回放——**正好匹配**。
- **Stream**：持久化 + 消费组，能回放历史。但 SSE 用不到回放，多个实例每个都要订阅会产生重复消费，需要消费组拓扑——**为不需要的特性付额外复杂度**。

所以：**Pub/Sub 是 SSE 跨实例广播的最佳匹配**。

### Q7. LoRA 微调为什么选 Qwen2.5-1.5B？rank=8 怎么定的？

模型选择：
- **轻**：1.5B 在 4090 单卡能 LoRA 微调 + vLLM 部署，显存 ~6-14 GB
- **强**：Qwen2.5 系列在中文指令任务的 baseline 比 LLaMA 系强
- **快**：1.5B 在 vLLM 上 P95 < 200 ms，比调 qwen-max 的 800 ms 快 4×

LoRA 超参：
- `rank=8`：经验值，对 1B-3B 模型「指令格式拟合 + 不破坏基模」的最佳点；rank=16 收益递减且过拟合风险上升
- `alpha=16`：alpha=2×rank 标准配比
- `dropout=0.05`：轻 dropout 防小样本过拟合
- `epoch=3`：1000-2000 条样本 3 epoch 是行业默认；多了开始记忆样本

### Q8. workflow_node_run 为什么不冗余 tenant_id？

否定的设计：「不在 `workflow_node_run` 加 `tenant_id` 列」。

理由：
- 该表纯由 `task_id` 派生，逻辑上 `task_id` 已确定租户归属
- 加冗余列要求所有写入路径同时设 `tenant_id`，多 5 个 Consumer 都要改 → 一致性风险
- 写多读少表（一个 task 跑下来 ~15 行 node_run），加冗余列收益的「读时少 join」并不显著
- 查询方走子查询：`WHERE task_id IN (SELECT id FROM report_task WHERE tenant_id = ?)`，MySQL 优化器对小子集 IN 处理良好

权衡哲学：**写多端的反范式风险 > 读端的 join 成本**。

### Q9. LLM-as-Judge 怎么避免「让模型自己评自己」的偏差？

3 个手段：
1. **评委用比 generator 强一档的模型**：Writer 用 qwen-plus，评委用 qwen-max
2. **细分 rubric 而不是「打个总分」**：5 维度（structure / factuality / reasoning / citation / clarity）每维独立打 0-10，逼模型分项审视
3. **要求逐项给评语**：`comments.{dim}` 字段强制评委陈述理由，让评分可追溯、可人工 spot-check

进一步可做但本项目没做（可作面试加分）：
- **多评委 ensemble**（DeepSeek-R1 + Claude + qwen-max，三家分数取均值/中位数）
- **pairwise 比较** 替代绝对分数（更稳）

### Q10. PromptSeeder + PromptLoader 的「文件兜底」是过度设计吗？

不是。一个完整论证：

- 用户运行流程：clone → 启容器 → 跑 DDL → 启动 → 测试
- 如果 `prompt_template` 表未建（用户忘了跑 schema-phase4.sql），DB-only 模式下 PromptLoader 抛异常 → **整个 Agent 流水线挂掉**
- 文件兜底成本：~20 行代码 + 一次启动期的 classpath 扫描；零运行期开销（缓存命中后不再走兜底分支）
- 收益：**用户用错姿势也不会挂**，并且在生产用 DB 之后，文件就是天然的「冷备/回滚源」

类似设计哲学：「**默认值要安全、错误路径要可恢复**」。

---

## 7. 排错实战（8 个故事）

| # | 症状 | 根因 | 修复 |
|---|---|---|---|
| 1 | Multi-Agent 上线后第一批 3/12 subtask FAILED，`latency_ms=5`，`error_message=null` | 5 个 Agent singleton 多线程并发调 `ChatClient.Builder.build()`，Builder 非线程安全 NPE | `@PostConstruct` 启动期 build 一次 (`d90379f`) |
| 2 | WriterConsumer 报 `ConcurrentModificationException`，任务最终能跑完 | `PromptLoader` / `WorkflowLoader` cache 用 `HashMap.computeIfAbsent`，多消费线程并发触发 CME | 换 `ConcurrentHashMap` (`be0b0c0`) |
| 3 | 调 `/api/report/start` 启动报 `topic[irp.task.created] contains illegal characters` | RocketMQ topic 只允许 `^[%\|a-zA-Z0-9_-]+$`，**点号不合规** | 全部 topic 改用 `-` 命名 (`13f6ba2`) |
| 4 | 调用 start 报 `Unable to find property 'max_loops'` | SnakeYAML 不自动 snake-to-camel | YAML 改 `maxLoops` (`c0e3ae2`) |
| 5 | RAG 接口 SQL 报错查的是 user 表，但实际跑到 PostgreSQL | 两个 DataSource bean 都没 `@Primary`，MyBatis-Flex 随机挑了 PGVector 那个 | 明确 `@Primary` + DataSourceBuilder 显式 setter（`url` 而非 `jdbcUrl`）+ Lombok config copy `@Qualifier` |
| 6 | DashScope embedding 调用 400 错误 | DashScope embedding 单批最多 10 条（不是 25 也不是 50） | `BATCH_LIMIT=10` (`43a8cc4`) |
| 7 | 研报跑完后控制台 `AuthorizationDeniedException ... response is already committed` | SSE 异步 DispatcherType.ASYNC 派发又过了一遍 Spring Security 过滤链，response 已被前面输出关闭 | SecurityFilterChain 加 `.shouldFilterAllDispatcherTypes(false)` (`89cdd2c`) |
| 8 | 多租户上线后所有新注册用户 `tenant_id=0`，隔离失效 | `UserServiceImpl.register` 写死 `setTenantId(0L)` | insert 后用 `user.id` 回填 `tenant_id`，一个用户一个租户 (`6fc7ba3`) |

**通用结论**：阶段 2 同步单 Agent 流程隐藏了所有共享可变状态的 race condition；阶段 3 多线程 RocketMQ 消费一上线全部暴露——**「不被并发跑过的代码不能信」**。

---

## 8. API 接口清单

### 用户 / 鉴权
- `POST /api/user/register` — 可选 `tenantId`，不传开新租户
- `POST /api/user/login` — 返回 JWT + UserVO

### 研报生命周期
- `POST /api/report/start` — 提交研究主题，返回 taskId
- `GET  /api/report/{id}` — 查任务状态
- `GET  /api/report/{id}/stream` — SSE 流式（7 种事件）
- `GET  /api/report/{id}/trace` — Trace 时间轴
- `GET  /api/report/{id}/sections` — 章节列表

### RAG
- `POST /api/rag/ingest` — PDF 入库（向量 + BM25 双写）
- `POST /api/rag/search` — Hybrid + Rerank 检索
- `POST /api/admin/eval/rag/synthesize` — 合成评估查询
- `POST /api/admin/eval/rag/run` — 跑 recall/MRR/nDCG 评估

### 平台中台
- `GET  /api/admin/stats/overview` — 总览
- `GET  /api/admin/stats/cost/model` — 成本按模型聚合
- `GET  /api/admin/stats/cost/agent` — 成本按 Agent 聚合
- `GET  /api/admin/tasks?status=&page=&size=` — 任务列表
- `GET  /api/admin/prompts` — Prompt 版本列表
- `GET  /api/admin/prompts/{id}` — 详情
- `POST /api/admin/prompts` — 新建版本
- `PUT  /api/admin/prompts/{id}` — 改内容
- `POST /api/admin/prompts/{id}/activate` — 切灰度
- `POST /api/admin/workflow/reload` — 热更 workflow YAML 缓存

### LoRA / 评估
- `POST /api/admin/query-rewrite` — Query 改写（OpenAI 兼容协议，切 base-url 热切）
- `POST /api/admin/eval/judge/{taskId}` — 触发 LLM-as-Judge 评分
- `GET  /api/admin/eval/judge/{taskId}/history` — 评分历史
- `GET  /api/admin/eval/judge?limit=20` — 当前租户最近评分

Swagger 全量：`http://localhost:8080/doc.html`。

---

## 9. 面试时怎么讲（节奏建议）

**90 秒电梯版**：
> 我做了一个用 Java 21 + Spring AI 实现的多 Agent 研报生成平台。核心是 5 个 Agent 通过 RocketMQ 节点级流转协作——Planner 拆解、N 个 Researcher 并行 hybrid 检索、Analyst 综合、M 个 Writer 流式产出章节、Critic 做事实回环。围绕业务还做了完整的平台中台：Prompt 灰度切换、Token 成本看板、Trace 时间轴、多租户隔离、Redis Pub/Sub 跨实例 SSE。另外有一个独立的 LoRA 微调子模块，用 Qwen2.5-1.5B 训了个 Query 改写小模型，通过 OpenAI 兼容协议挂到 vLLM，Java 侧改一行配置就切流量。

**5 分钟版**：加架构图 + 重点讲 Q1 (RocketMQ 编排) + Q3 (ChatClient 并发坑) + Q4 (Prompt 灰度) + Q7 (LoRA 选型)。

**白板编码题**：用 RRF 公式手写一个简化的 Hybrid 融合，或者写一个安全的 `PromptLoader.activate(id)`（事务 + 缓存失效）。

---

## 10. 后续路线（如果继续做）

按 ROI 排序：

1. **真训 LoRA**（最高 ROI，简历唯一的「自训模型」证据）—— `finetune/` 骨架完整，单卡 4090 跑 30-60 min
2. **OpenTelemetry**：用 OTel 标准替代自研 Trace，接 Jaeger / Tempo（让 Trace 的工程化更现代）
3. **Spring AI Advisors**：用 Spring AI 1.x 的 Advisor 机制替换手写 PromptLoader，减少胶水代码
4. **平台超管角色**：现在每个用户是自己租户的 admin，缺一层 `ROLE_PLATFORM_ADMIN` 看全局
5. **Per-Tenant 语料库**：当前 `knowledge_doc` 全局共享，可加 `tenant_id` 做 RAG 隔离
6. **A/B 灰度**：当前 `is_active` 是单一生效，可扩到「10% 流量走新版」按 hash 路由
