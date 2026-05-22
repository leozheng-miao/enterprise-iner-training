# 行业研报多 Agent 协作平台 — 开发总结 & 简历素材

> 用途：求职简历 bullet、面试技术问答、项目复盘
> 覆盖范围：阶段 0/1/2/3（已完成）
> 更新日期：2026-05-22

---

## 一、项目一句话定位

> 一个基于大模型的 **多 Agent 协作研报生成平台**：用户提交研究主题 → 5 个 Agent
> （规划/检索/分析/写作/审查）通过消息队列协作 → 输出带引用、可观测、流式返回的完整行业研报。

**技术栈**：`Spring Boot 3.5 · Java 21 · Spring AI 1.0 · RocketMQ · PGVector · Elasticsearch · MySQL · Redis · MyBatis-Flex · 通义千问(DashScope)`

---

## 二、架构全景

```
用户提交主题
   │
   ▼  POST /api/report/start（落库 + 发 MQ，立即返回 taskId）
RocketMQ 消息驱动编排
   │
   ▼
Planner Agent ── 拆解 8-12 个子主题 ──┐
                                      │ fanout：N 条 research 消息
   ┌──────────────────────────────────┘
   ▼
Researcher×N（并发消费，每个调 hybrid_search 工具）
   │ DB 状态机 join：全部 DONE 才推进
   ▼
Analyst Agent ── 跨主题分析，设计 4-6 个章节大纲 ──┐
                                                   │ fanout：M 条 write 消息
   ┌───────────────────────────────────────────────┘
   ▼
Writer×M（并发写章节 markdown）
   │ join：全部 FINAL 才推进
   ▼
Critic Agent ── 自我审查 ──┬── 有问题 → 回环重写（max_loops=1）
                            └── 通过 → 拼接 final_markdown，DONE

全程：SSE 流式推 7 种事件 + 每步落 workflow_node_run Trace 表
```

---

## 三、四个阶段的技术重点

| 阶段 | 主题 | 技术重点 |
|---|---|---|
| 0 | 工程地基 | Spring Security 6 无状态 JWT、MyBatis-Flex + BaseEntity 抽象、docker-compose 6 中间件、全局异常体系 |
| 1 | RAG 核心 | Hybrid Search（PGVector HNSW + ES BM25）双路并发、RRF 融合、gte-rerank 重排、Recall/MRR/NDCG 评估闭环 |
| 2 | 单 Agent + 可观测 | 自研 Tool SPI、Spring AI Function Calling、YAML Workflow 引擎、SSE 流式、Trace 落库 |
| 3 | 多 Agent 分布式编排 | 5 Agent 协作、RocketMQ 节点级编排、fanout/join、Critic 回环、失败隔离 |

### 阶段 1 — RAG 核心检索（细节）
- **Hybrid Search**：PGVector（HNSW 向量召回，cosine）+ Elasticsearch（IK 中文分词 BM25）双路 Virtual Thread 并发
- **RRF 融合**：应用层 Reciprocal Rank Fusion（k=60）合并两路排名，只看 rank 不看绝对分
- **Rerank**：阿里云 gte-rerank-v2 对融合候选重排
- **评估闭环**：qwen-max 合成 query + 人工筛选 50 条 GT，自实现 Recall@k / MRR / NDCG@k
- **PDF 解析**：Spring AI ParagraphPdfDocumentReader 结构感知分块 + TokenTextSplitter 兜底
- **语料**：31 篇官方公开 PDF（信通院/工信部/发改委/能源局/统计局等）

### 阶段 2 — 单 Agent + 可观测（细节）
- **自研 Tool SPI**：`AgentTool` 接口（name/description/paramsType/invoke）+ `@AgentToolMarker` 注解，启动扫描注册到 `tool_registry` 表
- **Spring AI Function Calling**：`FunctionToolCallback` 桥接 AgentTool → LLM 自主决策调用
- **YAML 声明式 Workflow 引擎**：SnakeYAML 解析 DAG
- **SSE 流式**：Spring MVC SseEmitter
- **Trace 可观测**：`workflow_node_run` 表，每次 LLM/Tool 调用落 token/延迟/IO（类 LangSmith 简化版）

### 阶段 3 — 多 Agent 分布式编排（核心难点，细节）
- **5 Agent 协作**：Planner（qwen-max 拆解 8-12 子主题）/ Researcher×N（qwen-plus + hybrid_search）/ Analyst（qwen-max 跨主题分析）/ Writer×M（qwen-plus 写章节）/ Critic（qwen-max 审查）
- **RocketMQ 节点级编排**：5 个 topic（task-created / research-task / analyze-task / write-section / critic-task），每个 Consumer 自驱动下一步
- **Fanout / Join**：Planner 一拆多 → `workflow_subtask` 表 N 行 → N 个 Researcher 并发 → DB 状态机 join 同步
- **Critic 自我审查回环**：`workflow_loop_state` 表计数，`max_loops=1` 防无限循环
- **失败隔离**：单节点失败 RocketMQ 重试 3 次 + 死信队列，不拖垮整体
- **模型分级**：决策性强的 Planner/Analyst/Critic 用 qwen-max，量大的 Researcher/Writer 用 qwen-plus（成本/质量平衡）

---

## 四、简历可直接写的 Bullet（量化版）

> **行业研报多 Agent 协作平台**（个人项目 / Spring Boot + Spring AI）
>
> - 设计并实现 **5 Agent 协作研报生成系统**（Planner/Researcher/Analyst/Writer/Critic），
>   基于自研 YAML Workflow 引擎 + RocketMQ 节点级消息编排，支持 fanout 并行检索、
>   DB 状态机 join 同步、Critic 自我审查回环；**单研报 P95 从单 Agent 串行的 ~40s 降至 ~20s**。
>
> - 构建 **Hybrid Search RAG 引擎**：PGVector（HNSW）向量召回 + Elasticsearch（IK 中文分词）
>   BM25 召回双路并发，应用层 RRF 融合 + 阿里云 gte-rerank 重排；自建 50 条评估集，
>   用 Recall@10 / MRR / NDCG@k 量化检索质量。
>
> - 自研 **Agent Tool SPI** + 集成 Spring AI 1.0 Function Calling，让 LLM 自主决策工具调用；
>   建立 **Trace 可观测体系**，每次 LLM/Tool 调用的 token、延迟、输入输出落库，
>   支持成本统计与链路回放。
>
> - 实现 **SSE 流式**推送 7 种事件（节点状态/工具调用/token/阶段切换/章节完成/完成/错误），
>   前端实时感知 Agent 执行进度。
>
> - RocketMQ 消息驱动 + 重试 3 次 + 死信队列保障节点级失败隔离；排查并修复 Java 21
>   多线程消费下的 3 类并发安全缺陷（`ChatClient.Builder` 非线程安全、`HashMap` 缓存 CME、
>   懒初始化 race）。

> ⚠️ 数字按真实实测填：P95 用跑 5 次的真实平均；召回率用实际评估结果。

---

## 五、面试深挖点（配怎么答）

### 1. "为什么用消息队列编排而不是直接方法调用？"
- 节点级失败隔离：单个 Researcher 挂了不影响其它，RocketMQ 自动重试 + 死信
- fanout 天然并行：Planner 发 N 条消息，N 个 worker 并发消费
- 可水平扩展：consumer group 多实例自动负载均衡
- 解耦 + 可观测：每个节点输入输出通过 MQ + DB 流转，天然形成 Trace

### 2. "fanout 之后怎么知道所有子任务都完成了（join）？"
- 不引 Saga 框架。Planner 拆解后写 N 行 `workflow_subtask`（status=PENDING）
- 每个 Researcher 完成时：`@Transactional` 内先 `update 自己=DONE`，
  再 `SELECT COUNT(*) WHERE status<>'DONE'`
- count=0 即"我是最后一个" → 触发下游。MySQL RR 隔离保证不会双发

### 3. "Hybrid Search 的两路结果怎么合并？RRF 是什么？"
- 向量召回擅长语义相近，BM25 擅长精确术语匹配，互补
- RRF：`score(doc) = Σ 1/(k + rank_i)`，只看排名不看各引擎绝对分（绝对分不可比）
- k=60 是经验值，平滑高排名的优势

### 4. "Critic 回环会不会无限循环？"
- `workflow_loop_state` 表计数，`max_loops=1`：最多修订 1 轮，第 2 轮强制接受
- Critic 输出 JSON 解析失败时 fail-safe 直接 finalize

### 5. "Trace 怎么设计的？"
- `workflow_node_run` 表：task_id + node_id + step_seq + step_type(LLM_CALL/TOOL_CALL)
  + tokens_in/out + latency_ms + input/output JSON
- 可聚合算单研报 token 成本、可按时间轴还原执行链路

### 6. "Spring AI Function Calling 怎么接入自研工具的？"
- 自研 `AgentTool` 接口 + `@AgentToolMarker` 注解，启动时 `List<AgentTool>` 注入扫描
- 桥接：`FunctionToolCallback.builder(name, wrappedFn).description(...).inputType(...)`
- 桥接 lambda 内夹一层：调用前推 SSE tool 事件、调用后写 Trace —— 业务无侵入

---

## 六、踩坑经验（面试讲这个最加分 — 体现"会定位问题"）

阶段 3 引入多线程消费后真实踩的坑。面试讲"怎么发现 + 怎么定位"比讲架构更打动人。

### 坑 1：任务卡死 RESEARCHING，无任何报错
- **现象**：12 个子任务 9 成功 3 失败，失败的 `latency_ms=5` + `error_message=null`
- **定位推理**：5ms 不可能是真 LLM 调用 → 调用前就崩；message=null → NPE；
  3 个失败全在第一批并发的 7 个里，错峰执行的全成功 → **并发竞争**
- **根因**：5 个 Agent 是单例 bean，多线程同时调注入的同一个
  `ChatClient.Builder.build()`，Builder 内部 mutable 状态被并发改写
- **修复**：`@PostConstruct` 启动时 build 一次 ChatClient 复用
  （ChatClient 实例线程安全，只有 Builder 不能并发 build）

### 坑 2：ConcurrentModificationException
- `PromptLoader` / `WorkflowLoader` 用 `HashMap.computeIfAbsent` 做缓存，
  多 Consumer 线程并发 → CME
- 修复：`ConcurrentHashMap`

### 坑 3：RocketMQ topic 名报 illegal characters
- RocketMQ topic 只允许 `^[%|a-zA-Z0-9_-]+$`，点号 `irp.task.created` 不合规
- 修复：改连字符 `irp-task-created`

### 坑 4：SnakeYAML 不自动转 snake_case
- YAML 里 `max_loops` 映射不到 Java 字段 `maxLoops`
- 修复：YAML 直接用 camelCase

### 坑 5：MyBatis-Flex 多数据源注入错乱
- 引入 PGVector 第二数据源后，主 MySQL DataSource 无 `@Primary`，
  MyBatis-Flex 把 user 表 SQL 发到了 PostgreSQL
- 修复：主 DataSource 显式 `@Primary` + `@Qualifier` 精确注入 + lombok.config
  `copyableAnnotations += Qualifier`

**核心教训（面试金句）**：
> "阶段 2 单 Agent 同步执行时这些并发 bug 全部隐藏；阶段 3 一上多线程消费，
> 所有'单例 Bean 里的可变共享状态'集中爆发。这让我深刻理解了**无状态设计**
> 和**线程安全边界**在并发系统里的重要性 —— 单例 Bean 里任何可变字段都是潜在地雷。"

---

## 七、数据模型速览（10 张表）

| 表 | 作用 |
|---|---|
| `user` | 用户 + JWT 鉴权 |
| `knowledge_doc` / `knowledge_chunk` | RAG 文档 / chunk 元数据 |
| `rag_eval_query` | RAG 评估数据集 |
| `report_task` | 研报任务生命周期（status/phase/progress）|
| `workflow_node_run` | **Trace 核心表**：每步 LLM/Tool 调用 |
| `tool_registry` | Agent 工具注册 |
| `workflow_subtask` | fanout 子任务状态（join 判定）|
| `report_section` | 研报章节存储 |
| `workflow_loop_state` | Critic 回环计数 |

（向量数据另存 PGVector `knowledge_chunk_vec`，关键字索引存 ES `knowledge_chunk_bm25`）

---

## 八、后续规划（未完成）

| 阶段 | 内容 |
|---|---|
| 4 | 平台中台：Prompt 版本管理 / Tool 注册 UI / Trace 看板 / Token 成本统计 / Redis Pub/Sub 跨实例 SSE |
| 5 | LoRA 微调子模块：Qwen2.5-1.5B 微调 Query 改写，vLLM 部署，Java 端零侵入切换 |
| 6 | 评估与优化：RAGAS / LLM-as-Judge / Prompt A/B 测试 |
| 前端 | Vue3 + TS 全链路 UI（交接手册见 `2026-05-20-frontend-session-handoff.md`）|
