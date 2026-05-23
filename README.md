# 行业研报多 Agent 协作平台 — IRP (Inflow Research Platform)

> 输入研究主题 → **5 个 Agent 协作**（Planner / Researcher / Analyst / Writer / Critic）→ **流式产出带引用的 Markdown 研报**。
> 不止 demo，还把 **Prompt 灰度 / Trace 观测 / Token 成本 / 多租户 / LoRA 微调 / LLM-as-Judge** 全部做成了平台能力。

`Java 21` · `Spring Boot 3.5` · `Spring AI 1.0` · `RocketMQ 5` · `Redis 7` · `MySQL 8` · `PGVector` · `Elasticsearch 8` · `Qwen2.5-1.5B + LoRA`

---

## 1. 项目目标

面向「**1-3 年 Java 转 AI Agent 后端工程师**」求职定位，端到端覆盖 2026 年 China AI Agent JD 的全部技术维度：

| JD 关键词 | 本项目对应能力 |
|---|---|
| RAG / Multi-Agent / Workflow | 5 Agent 协作 + Hybrid Retrieval + Rerank + 引用追踪 |
| Spring AI / LangChain4j | Spring AI 主框架，Tool Calling 动态注册 |
| 异步 / 流式 / 缓存 | RocketMQ 节点级流转 + SSE + Redis Pub/Sub 跨实例广播 |
| Prompt 管理 / Trace / 评估 | `prompt_template` 表 + 灰度切换 + Trace 落库 + RAGAS + LLM-as-Judge |
| 微调 LoRA / 模型路由 | Qwen2.5-1.5B + LoRA Query 改写，OpenAI 兼容协议热切 |

---

## 2. 架构总览

```
┌──────────────────────────────────────────────────────────────────┐
│  Vue 3 + ElementPlus + SSE 前端（frontend/）                     │
└──────────────────────────────────────────────────────────────────┘
                       │ HTTP / SSE (Spring Security + JWT)
┌──────────────────────────────────────────────────────────────────┐
│  Spring Boot 3.5 · Java 21 (Virtual Thread)                      │
│  ┌───────────────┬──────────────────┬───────────────────────┐    │
│  │ 5 Agent 编排  │  RAG 检索        │  平台中台              │    │
│  │ Planner       │  Hybrid (RRF)    │  Prompt 灰度（@active）│    │
│  │ Researcher×N  │  BGE / DashScope │  Token 成本看板        │    │
│  │ Analyst       │  Rerank          │  Trace 时间轴          │    │
│  │ Writer        │  Citation 追踪    │  多租户隔离            │    │
│  │ Critic        │  RAGAS 评估      │  LLM-as-Judge          │    │
│  │ (Tool SPI)    │                  │  Query 改写 LoRA       │    │
│  └───────────────┴──────────────────┴───────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
                       │
┌──────────────────────────────────────────────────────────────────┐
│  MySQL 8（元数据 / Trace / Prompt）                              │
│  Redis 7（SSE Pub/Sub / 限流 / 缓存）                            │
│  RocketMQ 5（5 个 topic：长任务异步 / 节点重试 / 死信）          │
│  PGVector（向量库，HNSW cosine）                                  │
│  Elasticsearch 8（BM25 + IK 中文分词）                            │
│  DashScope（qwen-plus / qwen-max / text-embedding-v3 / rerank）  │
│  vLLM（Qwen2.5-1.5B + LoRA，自托管 OpenAI 兼容）                 │
└──────────────────────────────────────────────────────────────────┘
```

## 3. 五 Agent 协作流程

```
[用户提交主题] → /api/report/start
        │
        ▼  POST irp-task-created
┌──────────────────┐    fanout N 条子主题
│ TaskOrchestrator │ ────────────────────────┐
│  Planner (LLM)   │                          │
└──────────────────┘                          ▼
                                  ┌─────────────────────┐
                                  │  Researcher Worker  │ × N 并行消费
                                  │  hybrid_search Tool │
                                  └─────────────────────┘
                                              │ N 条全部完成（join all）
                                              ▼
                                  ┌─────────────────────┐
                                  │  Analyst (LLM)      │  推理 + 章节大纲
                                  └─────────────────────┘
                                              │ 章节 fanout
                                              ▼
                                  ┌─────────────────────┐
                                  │  Writer Worker      │ × M 章节并行
                                  └─────────────────────┘
                                              │ join all
                                              ▼
                                  ┌─────────────────────┐
                                  │  Critic (≤1 loop)   │  事实/逻辑自检
                                  └─────────────────────┘
                                              │ 全程落 workflow_node_run（Trace）
                                              ▼
                                       SSE 流到前端
```

**为什么这样设计：**
1. **可观测** — 每个节点的输入输出 / token / latency 都落 `workflow_node_run`，天然形成 Trace。
2. **可重试** — 单节点失败只重试自己（RocketMQ 16 次 + 死信）。
3. **可水平扩展** — Researcher / Writer Worker 可多实例并行消费同一 topic。
4. **可暂停恢复** — 状态全在库里，任意时刻崩溃重启不丢任务。
5. **流式体验** — Redis Pub/Sub 跨 JVM 广播 SSE 事件，多实例部署也能拿到自己 task 的事件。

---

## 4. 核心特性矩阵

| 模块 | 关键能力 | 关键文件 |
|---|---|---|
| **Agent 编排** | YAML 定义 workflow / Tool SPI 注册 / fanout-join / Critic 回环 | `workflow/`、`agent/` |
| **RAG 检索** | PGVector + ES 8 BM25 → RRF 融合 → DashScope gte-rerank | `rag/search/HybridRetriever.java` |
| **流式输出** | SseEmitter + Redis Pub/Sub 跨实例 + 7 种事件类型 | `stream/SseRedisListener.java` |
| **Prompt 管理** | `prompt_template` 表 + `@active` 灰度 + `PromptLoader` 文件兜底 | `agent/prompt/PromptLoader.java` |
| **Trace 观测** | 每个 LLM/Tool 调用落库（prompt_version, tokens, latency, status） | `trace/WorkflowNodeRunRecorder.java` |
| **Token 成本** | 按 model / agent 聚合，`ModelPricing` 折算人民币 | `trace/AdminStatsService.java` |
| **多租户** | `tenant_id` 隔离 + 跨租户 NOT_FOUND（不泄露存在性） | `security/SecurityUtils.java`、`schema-phase4-multitenant.sql` |
| **RAG 评估** | recall@K / MRR / nDCG，rerank 前后对比 | `rag/eval/RagEvaluator.java` |
| **LLM-as-Judge** | 5 维 rubric（structure/factuality/reasoning/citation/clarity）→ qwen-max 打分 | `eval/ReportJudgeService.java` |
| **LoRA 微调** | Qwen2.5-1.5B + LoRA Query 改写，LLaMA-Factory 训练，vLLM 部署 | `finetune/` |

---

## 5. 快速开始

### 5.1 启基础设施

```bash
docker compose up -d                            # MySQL / Redis / RocketMQ / PGVector / ES
```

| 服务 | 宿主机端口 |
|---|---|
| MySQL 8 | 3307 |
| Redis 7 | 6380 |
| RocketMQ NameSrv | 9877 |
| PGVector (PG 16) | 5434 |
| Elasticsearch 8 (含 IK) | 9201 |

### 5.2 执行 DDL（按顺序）

```bash
mysql -h127.0.0.1 -P3307 -uirp -pirppw irp < src/main/resources/db/schema.sql
mysql ... < src/main/resources/db/schema-rag.sql
mysql ... < src/main/resources/db/schema-phase2.sql
mysql ... < src/main/resources/db/schema-phase3.sql
mysql ... < src/main/resources/db/schema-phase4.sql              # Prompt 版本管理
mysql ... < src/main/resources/db/schema-phase4-multitenant.sql  # 多租户
mysql ... < src/main/resources/db/schema-phase6.sql              # LLM-as-Judge

psql -h127.0.0.1 -p5434 -Uirp -d irp_vec -f src/main/resources/db/pgvector-init.sql
```

### 5.3 配置敏感变量

`.env`（已 gitignore）：
```env
JWT_SECRET=<openssl rand -base64 48>
DASHSCOPE_API_KEY=sk-xxx
```

### 5.4 启后端

```bash
./mvnw spring-boot:run            # 8080，OpenAPI: http://localhost:8080/doc.html
```

### 5.5 启前端

```bash
cd frontend && npm install && npm run dev      # 5173
```

### 5.6 灌一批语料（可选）

```bash
# 把 PDF 扔到 src/main/resources/corpus/，然后调
curl -X POST http://localhost:8080/api/rag/ingest \
     -H "Authorization: Bearer <JWT>" \
     -d '{"pathOrGlob":"*.pdf","source":"public"}'
```

---

## 6. 关键 API（精选）

| 接口 | 说明 |
|---|---|
| `POST /api/user/register` | 注册（不传 `tenantId` 自动开新租户） |
| `POST /api/user/login` | 登录拿 JWT |
| `POST /api/report/start` | 提交研报主题 |
| `GET  /api/report/{id}/stream` | SSE 流式接收事件 |
| `GET  /api/report/{id}/trace` | Trace 时间轴 |
| `POST /api/rag/ingest` | PDF 入库（向量 + BM25 双写） |
| `POST /api/rag/search` | Hybrid + Rerank 检索 |
| `POST /api/admin/eval/rag/run` | RAG 离线评估（recall/MRR/nDCG） |
| `POST /api/admin/eval/judge/{taskId}` | LLM-as-Judge 评分（5 维 rubric） |
| `GET  /api/admin/stats/overview` | 平台总览（成本 / 成功率 / 端到端耗时） |
| `GET  /api/admin/stats/cost/model` | Token 成本按模型聚合 |
| `GET  /api/admin/prompts` | Prompt 版本列表 |
| `POST /api/admin/prompts/{id}/activate` | 切灰度生效版本（**无需重启**） |
| `POST /api/admin/query-rewrite` | Query 改写（DashScope / vLLM 切 base-url 即热切） |

Swagger 全量：`http://localhost:8080/doc.html`（knife4j）。

---

## 7. 阶段交付清单

| 阶段 | 范围 | 状态 |
|---|---|---|
| **0 脚手架** | Spring Boot / MyBatis-Flex / Security JWT / Docker Compose | ✅ |
| **1 RAG 核心** | 双数据源 + Hybrid (RRF) + DashScope Rerank + 引用 + 离线评估 | ✅ |
| **2 单 Agent + Workflow** | YAML DAG + Tool SPI + LLMGateway + SSE 同步流 | ✅ |
| **3 Multi-Agent + MQ** | 5 Agent + RocketMQ 节点级流转 + fanout/join + Critic 回环 | ✅ |
| **4 平台中台** | Token 成本 / Prompt 灰度 / Trace / 多租户 / Redis Pub/Sub SSE | ✅ |
| **5 LoRA 微调** | Qwen2.5-1.5B + LoRA Query 改写，vLLM 部署，Java 侧热切 | ✅ 骨架完成（训练待跑） |
| **6 LLM-as-Judge** | 5 维 rubric 评分，落 `report_eval_run`，租户隔离 | ✅ |
| **前端 MVP** | Vue 3 + SSE + Markdown 实时渲染 | ✅ |
| **7 文档与素材** | README / 架构图 / 简历 bullet / 面试 Q&A | ✅（本文档 + `docs/superpowers/handoff/`） |

---

## 8. 简历亮点（可直接复用）

> **行业研报多 Agent 协作平台**（个人项目，Java 21 / Spring Boot 3.5 / Spring AI / RocketMQ）
>
> - 设计并实现 5 Agent 协作流程（Planner → Researcher × N → Analyst → Writer × M → Critic），用 **RocketMQ 节点级流转**替代线程池：单节点失败自动重试 + 死信、Worker 水平扩展、状态全在库可暂停恢复
> - RAG 检索做 **PGVector + Elasticsearch 8 (BM25) Hybrid + RRF 融合 + DashScope gte-rerank**，每个 chunk 带 doc_id + page 做引用追踪，离线评估覆盖 recall@K / MRR / nDCG
> - 平台中台：**Prompt 版本管理 + `@active` 灰度切换**（改 DB 不重启）、**Token 成本看板**（按 model / agent 聚合到人民币）、**Trace 时间轴**（每个 LLM / Tool 调用 prompt_version + tokens + latency 全落库）、**多租户**（`tenant_id` 逻辑隔离，跨租户访问返回 NOT_FOUND 不泄露存在性）
> - SSE 流式 + **Redis Pub/Sub 跨实例广播**，多 JVM 部署下任务无论落在哪个节点，前端订阅的 SSE 都能拿到事件
> - LLM-as-Judge 5 维 rubric（structure / factuality / reasoning / citation / clarity）由 qwen-max 评委打分，长期追踪研报质量
> - Query 改写微调：基于 **Qwen2.5-1.5B + LoRA**（LLaMA-Factory 训练，vLLM 自托管 OpenAI 兼容部署），通过 `app.query-rewriter.base-url` 一行配置切流量，Java 侧零改动
> - 排错沉淀：定位并修复 ChatClient.Builder 多线程 build 不安全 / `HashMap.computeIfAbsent` 在 Consumer 并发下抛 CME / RocketMQ topic 名非法字符 / SnakeYAML 不自动 snake-to-camel 等 5 个并发与序列化坑

---

## 9. 文档索引

| 文档 | 用途 |
|---|---|
| [`docs/superpowers/handoff/2026-05-23-final-handoff.md`](docs/superpowers/handoff/) | **面试详细 Q&A + 排错实战 + 完整数据模型** |
| [`docs/superpowers/handoff/2026-05-22-resume-and-tech-summary.md`](docs/superpowers/handoff/) | 阶段 0-3 中期复盘（保留作为里程碑） |
| [`finetune/README.md`](finetune/README.md) | LoRA 微调子模块端到端流程 |
| [`docs/superpowers/specs/`](docs/superpowers/specs/) | 各阶段需求规约（spec） |
| [`docs/superpowers/plans/`](docs/superpowers/plans/) | 各阶段实现计划（plan） |

---

## 10. 后续可扩展方向

- **Spring AI Advisors** 替换手写 PromptLoader（Spring AI 1.x 已成熟）
- **OpenTelemetry** 取代当前自研 Trace（接 Jaeger / Tempo）
- **Per-Tenant 语料库**：当前 `knowledge_doc` 全局共享，可加 `tenant_id` 做 RAG 隔离（hybrid retriever 加 filter）
- **平台超管角色**：现在每个用户即自己租户的 admin，缺一层 `ROLE_PLATFORM_ADMIN` 看全局
- **真训 LoRA**：`finetune/` 骨架完整，需要单卡 4090 / AutoDL 跑 30-60 分钟
