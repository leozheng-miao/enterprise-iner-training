# 阶段 2：单 Agent + Workflow 引擎 + SSE 流式 设计文档

> 项目：行业研报多 Agent 协作平台 — 阶段 2
> 上游依赖：阶段 1 RAG 核心（hybrid_search / rerank / 评估）已落地，commit `06bb05f`
> 设计日期：2026-05-19
> 工期估算：**5-7 天**

---

## Context（为什么做这个阶段）

阶段 1 完成了 RAG **被动检索**接口：你问它，它返回相关片段。
阶段 2 要把它升级为 **主动 Agent**：

- 用户提交"研究主题"（如"2026 中国动力电池行业趋势"）
- LLM 自主决定：用什么 query 调用 `hybrid_search`、调几次、看 X 篇后是否再调一次
- 最终输出一段**带引用**的研究小结（Markdown）
- 前端 **SSE 流式** 实时看到：节点进度 → LLM token 字符 → 最终引用清单
- 后台 `workflow_node_run` 表沉淀 Trace（每次 LLM 调用 / 工具调用的 token / 耗时 / 输入输出）

这是从"检索"到"Agent"的本质跳跃。简历亮点 ↗：

> 基于 Spring AI 1.0 Function Calling + 自研 Tool SPI 注册机制，构建 ResearcherAgent 单 Agent
> 闭环；YAML 声明式 Workflow 引擎承载 DAG 执行，SSE 实时流式推送节点状态 + LLM token；
> 所有 LLM/工具调用的 token、耗时、输入输出统一落 `workflow_node_run` 表，形成可
> 观测 Trace（类 LangSmith 简化版）。

---

## §1 决策汇总

| 维度 | 选定方案 | 理由 |
|---|---|---|
| Tool 注册 | 自研 SPI（`@AgentTool` 注解 + `AgentTool` 接口）| 简历亮点；Spring AI 内置 Function Calling 与 SPI 互补 |
| LLM 工具调用模式 | Spring AI 1.0 **Function Calling**（非 ReAct）| 官方原生 / DashScope qwen-plus 支持 / 不必手写 ReAct 状态机 |
| Agent 数量 | **1 个 ResearcherAgent** | 阶段 3 再扩 Planner/Analyst/Writer/Critic |
| Workflow 引擎 | **YAML 声明 + 节点状态机**（顺序执行，无 fanout/join） | 雏形版，阶段 3 加 fanout/join + RocketMQ |
| Workflow 引擎实现 | 自写（SnakeYAML + 简单状态机），不引 jBPM/Camunda | 几百行代码够，重型流程引擎对面试反而是负担 |
| 流式输出 | **Spring MVC `SseEmitter`** + Spring AI `ChatClient.stream()` | 不引入 WebFlux，避免响应式技术栈污染主链路 |
| Prompt 管理 | resources/prompts/*.txt + 类路径读取 | 阶段 4 平台中台再升级到 `prompt_template` 表 + 灰度 |
| 异步策略 | Task 提交后立即返回 task_id + SSE URL；后台 Virtual Thread 跑 Workflow | 不引 RocketMQ（同 JVM 内够用） |
| 报告输出格式 | Markdown 字符串 + 末尾"参考资料"引用清单 | 阶段 3 多 Agent 协作时再考虑结构化 JSON |

### 务实边界（YAGNI）

| 砍掉 | 替代 | 理由 |
|---|---|---|
| ❌ Multi-Agent（Planner/Analyst/Writer/Critic）| ✅ 1 个 ResearcherAgent | 阶段 3 一次性引入 |
| ❌ RocketMQ 节点级流转 | ✅ 同 JVM Virtual Thread | 单节点 DAG 用不上 MQ |
| ❌ Redis Pub/Sub | ✅ `SseEmitter` 单实例直推 | 同上 |
| ❌ Workflow DAG 编辑器 UI | ✅ YAML 文件 + 后台只读展示（阶段 4） | 阶段 2 完全无前端 |
| ❌ Prompt 版本管理 / 灰度 | ✅ resources/prompts/*.txt 硬编码 | 阶段 4 升级 |
| ❌ Tool 市场 UI | ✅ Spring 启动时把 `@AgentTool` Bean 写入 `tool_registry` 表 + 后台列表（阶段 4）| 阶段 2 只做注册机制 |
| ❌ Critic 自我审查回环 | — | 单 Agent 没意义 |
| ❌ Rerank 在 Agent 链路里的可配置开关 | ✅ hybrid_search 工具默认 `useRerank=true` | 阶段 1 已经验证 rerank 提升明显 |

---

## §2 端到端流程

```
[用户提交主题 "2026 中国动力电池行业趋势"]
    │
    ▼
POST /api/report/start
    │
    ├─ 落 report_task (status=PENDING)
    ├─ 提交 Virtual Thread 异步任务
    └─ 立即响应 { taskId, streamUrl: "/api/report/{id}/stream" }

──── 异步执行（Virtual Thread）────
TaskOrchestrator
    │
    ├─ load workflow YAML: researcher_only_v1.yaml
    ├─ 解析 1 个节点：research (agent=Researcher)
    └─ 顺序执行节点：
         │
         ▼
    ┌────────────────────────────────────────┐
    │ ResearcherAgent.execute(topic)         │
    │                                        │
    │ 1) ChatClient.prompt(researcher_prompt)│
    │    .tools(hybrid_search)               │
    │    .user(topic)                        │
    │    .stream() → Flux<String>            │
    │                                        │
    │ 2) LLM 决定调用 hybrid_search          │
    │    Spring AI 自动 invoke → 工具结果回灌│
    │    LLM 看完结果可能再调 1-3 次         │
    │                                        │
    │ 3) 最终 token stream 推 SSE event:token│
    │    每次工具调用推 SSE event:tool       │
    │    workflow_node_run 落库:             │
    │       prompt_version / tokens_in/out / │
    │       latency_ms / input/output JSON   │
    └────────────────────────────────────────┘
         │
         ▼
节点 DONE → workflow 完成 → report_task.status=DONE
SSE 推 event:done → emitter.complete()

──── 前端 ────
GET /api/report/{id}/stream
    │
    ▼
EventSource 接收：
  event:node_status   { nodeId, status: RUNNING/DONE }
  event:tool          { toolName, paramsJson, resultPreview }
  event:token         { delta }   <- LLM 增量 token
  event:done          { finalMarkdown, citations }
```

---

## §3 数据模型（继续追加到 MySQL）

继承 `BaseEntity`，自动获得 id/create_time/update_time/is_deleted。

### `report_task`（研究任务生命周期）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT | 关联 user.id |
| topic | VARCHAR(512) | 用户提交的研究主题 |
| workflow_name | VARCHAR(64) | 如 `researcher_only_v1` |
| status | VARCHAR(16) | PENDING / RUNNING / DONE / FAILED |
| final_markdown | MEDIUMTEXT NULL | 最终成稿（DONE 时填）|
| citations_json | JSON NULL | 引用清单（doc_id/title/sectionTitle/pageRange 数组）|
| error_message | VARCHAR(1024) NULL | FAILED 时填 |
| started_at / finished_at | DATETIME NULL | |
| INDEX | user_id, status | |

### `workflow_node_run`（**Trace 核心表**）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| task_id | BIGINT | → report_task.id |
| node_id | VARCHAR(64) | 来自 YAML 的 `nodes[].id` |
| agent_role | VARCHAR(32) | Researcher / Planner / ... |
| step_type | VARCHAR(16) | LLM_CALL / TOOL_CALL |
| step_seq | INT | 节点内步骤序号（0,1,2,...）|
| prompt_version | VARCHAR(64) NULL | 如 `researcher_prompt@v1`，LLM_CALL 填 |
| model | VARCHAR(64) NULL | qwen-plus / qwen-max，LLM_CALL 填 |
| tool_name | VARCHAR(64) NULL | TOOL_CALL 填 |
| input_json | MEDIUMTEXT | LLM 消息 / 工具参数 |
| output_json | MEDIUMTEXT | LLM 输出 / 工具结果 |
| tokens_in / tokens_out | INT | LLM_CALL 填 |
| latency_ms | INT | |
| status | VARCHAR(16) | OK / ERROR |
| error_message | VARCHAR(1024) NULL | |
| INDEX | task_id, node_id, step_seq | 一个 task 完整 Trace 时间轴 |

---

## §4 关键模块设计

### 4.1 Tool SPI

```java
public interface AgentTool {
    String name();              // 唯一名，如 "hybrid_search"
    String description();       // 喂给 LLM 的描述（影响调用决策）
    Class<?> paramsType();      // 参数 record/POJO 类型，Spring AI 反射拿 JSON schema
    Object invoke(Object params);
}
```

注解 `@AgentTool` 标记 Spring Bean。启动时扫描所有 `AgentTool` bean → 写入 `tool_registry`（沿用阶段 0 plan §2.1 中的表，本阶段顺手建表）。

ResearcherAgent 通过 `ToolRegistry.byNames("hybrid_search")` 拿到一组 `AgentTool` → 转 Spring AI `FunctionCallback` → 传给 ChatClient。

### 4.2 WorkflowEngine

```
src/main/resources/workflow/researcher_only_v1.yaml

name: researcher_only_v1
version: 1
nodes:
  - id: research
    agent: Researcher
    prompt: researcher_prompt@v1
    tools: [hybrid_search]
```

引擎 `WorkflowEngine`：
- `load(name) → WorkflowDef`：SnakeYAML 解析
- `execute(WorkflowDef, ExecutionContext) → 顺序执行 nodes`，每个节点调对应 Agent
- 节点状态机：PENDING → RUNNING → DONE/FAILED
- 节点开始/结束 emit 事件到 `SseSink`

阶段 3 扩展：fanout（plan → research×N）、join、并行、Critic 回环。

### 4.3 Agent 抽象

```java
public interface Agent {
    String role();                  // "Researcher"
    AgentResult execute(AgentInvocation in, SseSink sink);
}

public record AgentInvocation(
    long taskId, String nodeId, String topic, List<AgentTool> tools, String promptRef
) {}

public record AgentResult(String markdown, List<Citation> citations, AgentStatus status) {}
```

`ResearcherAgent implements Agent`：
- 用 `PromptLoader` 读 prompts/researcher_prompt_v1.txt
- 构造 `ChatClient.Builder` → 注册 tools → `prompt(...).stream()`
- 流式 token 推 SseSink event:token
- 工具调用通过 Spring AI 自动 invoke + 回灌；ToolInvocation 拦截器把 input/output 写 workflow_node_run
- 最终 markdown 解析"## 参考资料"段落，拼装 Citation list

### 4.4 SSE 协议

Endpoint：`GET /api/report/{taskId}/stream` 返回 `text/event-stream`

事件类型：

| event | data 字段 | 说明 |
|---|---|---|
| `node_status` | `{"nodeId":"research","status":"RUNNING"}` | 节点开始/结束 |
| `tool` | `{"toolName":"hybrid_search","paramsJson":"...","resultPreview":"hit 5 chunks"}` | 工具调用 |
| `token` | `{"delta":"在2026年，"}` | LLM 增量 token |
| `done` | `{"finalMarkdown":"...","citations":[...]}` | 全流程结束 |
| `error` | `{"message":"..."}` | 异常 |

实现要点：
- `SseEmitter` 默认 30s 超时 → 设为 `0L`（永不超时由后端 close 决定）
- 后端 close 时调 `emitter.complete()` 或 `emitter.completeWithError(t)`
- 客户端断开通过 `emitter.onCompletion/onTimeout/onError` 清理 sink
- 同一 taskId 多个连接：用 `Map<Long, SseSink>` 管理，订阅者多于 1 时复制广播；本阶段简化为单连接

---

## §5 关键接口

### REST API

| Method | Path | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/api/report/start` | 提交主题，返回 taskId + streamUrl | JWT |
| GET | `/api/report/{id}/stream` | SSE 实时流 | JWT |
| GET | `/api/report/{id}` | 查询任务最终结果（DONE 后用）| JWT |
| GET | `/api/report/{id}/trace` | 查 workflow_node_run 列表（调试/简历亮点）| JWT |

### POST `/api/report/start` 请求/响应

请求：
```json
{
  "topic": "2026 中国动力电池行业趋势",
  "workflow": "researcher_only_v1"
}
```

响应：
```json
{
  "code": 0,
  "data": {
    "taskId": 42,
    "status": "PENDING",
    "streamUrl": "/api/report/42/stream"
  }
}
```

### GET `/api/report/{id}` 最终结果

```json
{
  "code": 0,
  "data": {
    "taskId": 42,
    "status": "DONE",
    "topic": "...",
    "finalMarkdown": "## 行业概述\n...\n## 参考资料\n[1] ...",
    "citations": [
      {"docId":3, "docTitle":"...", "source":"CAICT", "sectionTitle":"...", "pageStart":12, "pageEnd":15}
    ],
    "tookMs": 28000
  }
}
```

### GET `/api/report/{id}/trace` Trace 视图

```json
{
  "code": 0,
  "data": {
    "taskId": 42,
    "steps": [
      {"stepSeq":0, "nodeId":"research", "stepType":"LLM_CALL", "model":"qwen-plus",
       "tokensIn":520, "tokensOut":85, "latencyMs":2300},
      {"stepSeq":1, "nodeId":"research", "stepType":"TOOL_CALL", "toolName":"hybrid_search",
       "inputJson":"{\"query\":\"动力电池产业链上游\",\"topK\":10}", "latencyMs":1450},
      {"stepSeq":2, "nodeId":"research", "stepType":"LLM_CALL", ...}
    ]
  }
}
```

---

## §6 关键文件结构（新增）

```
src/main/resources/
├── workflow/
│   └── researcher_only_v1.yaml          ← 新增
└── prompts/
    └── researcher_prompt_v1.txt         ← 新增

src/main/java/com/leo/enterpriseinertraining/
├── agent/
│   ├── core/
│   │   ├── Agent.java                   ← 接口
│   │   ├── AgentInvocation.java         ← record
│   │   ├── AgentResult.java             ← record
│   │   ├── AgentStatus.java             ← enum
│   │   └── Citation.java                ← record
│   ├── role/
│   │   └── ResearcherAgent.java
│   ├── tool/
│   │   ├── AgentTool.java               ← 接口
│   │   ├── ToolRegistry.java            ← 注册中心
│   │   ├── ToolInvocationTracer.java    ← 拦截 invoke 写 trace
│   │   └── impl/HybridSearchTool.java   ← 第一个工具
│   └── prompt/
│       └── PromptLoader.java            ← 读 resources/prompts/*
├── workflow/
│   ├── WorkflowDef.java                 ← YAML 反序列化模型
│   ├── WorkflowNode.java
│   ├── WorkflowLoader.java              ← SnakeYAML 解析
│   └── WorkflowEngine.java              ← 顺序执行 + 节点状态机
├── trace/
│   ├── WorkflowNodeRunRecorder.java     ← 写 workflow_node_run
│   └── TraceQueryService.java           ← 按 task_id 查 trace
├── stream/
│   ├── SseSink.java                     ← SseEmitter 包装
│   └── SseSinkManager.java              ← Map<taskId, SseSink>
├── entity/
│   ├── ReportTask.java                  ← extends BaseEntity
│   ├── WorkflowNodeRun.java             ← extends BaseEntity
│   └── ToolRegistry.java                ← extends BaseEntity (顺手建)
├── mapper/
│   ├── ReportTaskMapper.java
│   ├── WorkflowNodeRunMapper.java
│   └── ToolRegistryMapper.java
├── service/
│   ├── ReportService.java + impl/
│   └── TraceService.java + impl/
├── controller/
│   └── ReportController.java
├── dto/
│   └── ReportStartRequest.java
└── vo/
    ├── ReportStartVO.java
    ├── ReportResultVO.java
    └── TraceStepVO.java
```

---

## §7 阶段化交付（5-7 天，约 12 个 Task）

| Task | 子模块 | 关键产物 |
|---|---|---|
| T1 | DDL + 3 entity + mapper + ToolRegistry 表 | 4 张表落库 |
| T2 | Tool SPI 接口 + ToolRegistry + 启动时注册到 DB | `tool_registry` 表有 hybrid_search 一行 |
| T3 | HybridSearchTool 实现（包装 RagSearchService）| 工具可被调用，返回 chunk + 引用 |
| T4 | PromptLoader + researcher_prompt_v1.txt | 读 prompt 文本 |
| T5 | WorkflowLoader + WorkflowDef（SnakeYAML）+ TDD | YAML → DAG 对象 |
| T6 | WorkflowNodeRunRecorder + 单测 | 落 trace 行 |
| T7 | SseSink + SseSinkManager | 单连接推 4 种事件 |
| T8 | Agent 接口 + ResearcherAgent（Spring AI Function Calling + stream）| 给定 topic → 输出 markdown + token 流到 SseSink |
| T9 | ToolInvocationTracer（Spring AI tool callback 拦截）| 每次 tool call 落 trace |
| T10 | WorkflowEngine（顺序执行 + 节点状态机）| 跑通 researcher_only_v1.yaml |
| T11 | ReportService + 异步 Virtual Thread orchestrator + POST /api/report/start | 接口落 task → 异步触发 |
| T12 | ReportController（/{id}, /{id}/stream, /{id}/trace）+ DTO/VO | Knife4j 可调通 |

---

## §8 验证方式

### 功能层
- [ ] Knife4j 调 `POST /api/report/start` → 立即返回 taskId + streamUrl + status=PENDING
- [ ] 浏览器开 `EventSource('/api/report/{id}/stream')` 或 curl `--no-buffer`：能看到 `node_status: RUNNING` → 多个 `tool` 事件 → 多个 `token` 事件 → `done`
- [ ] `GET /api/report/{id}` 返回 DONE + finalMarkdown 包含 1-3 段正文 + "## 参考资料" 含 2-5 条引用
- [ ] `GET /api/report/{id}/trace` 返回 ≥3 个 step（至少 1 个 LLM_CALL + 1 个 TOOL_CALL + 1 个 LLM_CALL）

### 性能层
- [ ] 单 task 总耗时 ≤ 40s（包括 1-2 次 hybrid_search + 多轮 LLM 调用）
- [ ] SSE 首个 token 出现 ≤ 5s（用户感知"应用在干活"）
- [ ] 同 1 个用户并发提交 3 个 task：全部完成 ≤ 80s

### 工程层
- [ ] `workflow_node_run` 一致性：每个 LLM_CALL 都有 tokens_in/out > 0；每个 TOOL_CALL 都有 input_json + output_json
- [ ] tool_registry 启动时自动写入 `hybrid_search` 一行
- [ ] task 失败时 status=FAILED + error_message 落库，SSE 推 `event:error` 后 close
- [ ] SSE 客户端断开：服务端 1-2s 内清理 SseSink，不泄漏 emitter

### 集成测试
- [ ] WorkflowLoaderTest：YAML → WorkflowDef 字段全对
- [ ] WorkflowNodeRunRecorderTest：写入读取对得上
- [ ] ResearcherAgentIT（@EnabledIfEnvironmentVariable DASHSCOPE_API_KEY）：真实跑一次，验证 Function Calling 链路通

---

## §9 风险与延后

| 风险 | 缓解 |
|---|---|
| Spring AI 1.0 Function Calling 与 DashScope qwen-plus 的兼容性细节（如 parallel_tool_calls）| T8 实现时先单工具单次调用，能跑通后再放开多调用 |
| SseEmitter 在 long-running task 中超时 | 设 timeout=0 + 后端心跳 event（每 15s 推 `ping` 事件）|
| Virtual Thread + JDBC：MySQL JDBC 驱动在 VT 上 pin native thread | 阶段 2 单 task 无大并发，可接受；阶段 3 跑多 task 再评估换 Loom-friendly 池 |
| Spring AI tool callback 拦截 input/output 的 API 稳定性 | 优先用 `ChatClient` 的 `advisors`；不行 fallback 在 AgentTool.invoke 内部手动记录 |

---

## §10 后续步骤

1. 写入 git
2. 你扫一眼，有调整告诉我；无调整 → invoke writing-plans
3. 阶段 2 完成 → 阶段 3 = Multi-Agent (Planner/Researcher×N/Analyst/Writer/Critic) + RocketMQ 节点流转 + Redis Pub/Sub 跨实例 SSE
