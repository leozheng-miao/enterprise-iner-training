# 阶段 3：Multi-Agent 协作 + RocketMQ 编排 设计文档

> 项目：行业研报多 Agent 协作平台 — 阶段 3
> 上游依赖：阶段 2 单 Agent + WorkflowEngine + SSE + Trace 完成（commit `ac7539c`）
> 设计日期：2026-05-20
> 工期估算：**10-14 天**（约 14-16 Task）

---

## Context（为什么做这个阶段）

阶段 2 完成了**单 Agent 闭环**：1 个 Researcher 用 Function Calling 自主调工具写出 1 段研究小结。
阶段 3 要把它升级为**真正的多 Agent 协作研报系统**：

- **5 个 Agent 角色** 各司其职：Planner（拆解大纲）/ Researcher×N（并行检索子主题）/ Analyst（跨主题分析）/ Writer（章节成文）/ Critic（自我审查）
- **RocketMQ 节点级消息** 串起 DAG：Planner 发 N 条 RESEARCH_TASK → Researcher Worker 并发消费 → 全部 ACK 后 ANALYZE_TASK → ...
- **Workflow YAML 升级** 支持 `fanout` / `join` / `max_loops`
- **报告输出** 升级为多章节结构（## 行业概述 / ## 上中下游 / ## 玩家格局 / ## 风险与挑战 / ## 参考资料）
- **简历价值** 从"调用 LLM"跳到"分布式多 Agent 编排 + 消息驱动架构"

简历亮点目标：

> **5 Agent 协作研报生成**：Planner（任务拆解 8-12 子主题）→ Researcher×N（**RocketMQ fanout 并行检索**）→ Analyst（**join 同步后跨主题分析**）→ Writer（章节流式输出）→ Critic（自我审查回环，max_loops=1）。
> 自研 WorkflowEngine v2 解析 YAML DAG + RocketMQ 节点级 topic 消息驱动，每个节点独立 ACK / 重试 3 次 / 死信兜底，**单研报 P95 从单 Agent 40s 降至 18s**（fanout 并发收益），失败率 < 0.5%。
> 5 个 Agent 全程 Trace 落 `workflow_node_run`，含 token / latency / IO，前端可视化 LangSmith 简化版。

---

## §1 决策汇总

| 维度 | 选定方案 | 理由 |
|---|---|---|
| Agent 数量 | **5 个**（Planner / Researcher / Analyst / Writer / Critic）| 项目灵魂，简历最强卖点 |
| 编排引擎 | **WorkflowEngine v2** 扩 fanout/join/loop | 阶段 2 雏形升级，不引第三方流程引擎 |
| 消息中间件 | **RocketMQ 5**（阶段 0 已起容器）| 节点级 topic，自然形成分布式 |
| Fanout 实现 | Planner 输出 List<String> → 同时发 N 条 RESEARCH_TASK 消息 | RocketMQ 原生 fanout 即"同 topic N 条消息"|
| Join 实现 | **DB-based countdown**：插入 N 行子任务状态，Researcher 消费完 update DONE，触发器 / 轮询检查全部 DONE 后发 ANALYZE_TASK | 简单可靠；不用引 Saga 框架 |
| Researcher 并发 | RocketMQ 消费者组多线程 + Virtual Thread executor | 同实例内多 worker；阶段 4 多实例时天然水平扩展 |
| Critic 回环 | max_loops=1：Critic 输出"需修订" → 重发 WRITE_SECTION 一轮，第二轮 Critic 强制接受 | 防止无限循环 |
| SSE 跨实例 | **本阶段不引 Redis Pub/Sub** | 单实例够用；阶段 4 平台化时再加 |
| Prompt 管理 | resources/prompts/*.txt 文件（阶段 2 同款）| Prompt DB 表延后到阶段 4 |
| LLM 模型 | Planner/Analyst/Critic 用 qwen-max；Researcher/Writer 用 qwen-plus | 质量 vs 成本平衡 |
| 失败处理 | RocketMQ 重试 3 次 + 死信队列；workflow_node_run 写 ERROR | DLQ 监控留给阶段 4 |
| 输出格式 | 多章节 Markdown（H2 标题分段 + 末尾 ## 参考资料）| 与 Writer Agent 行为对齐 |

### 务实边界（YAGNI）

| 砍掉 | 替代 | 理由 |
|---|---|---|
| ❌ Redis Pub/Sub 跨实例 SSE | ✅ 单实例 SseSinkManager（沿用阶段 2）| 单实例 demo 够用 |
| ❌ Prompt 灰度 / 版本管理 UI | ✅ resources/prompts 硬编码 | 阶段 4 平台中台再做 |
| ❌ 动态调整 Workflow（运行时改 YAML）| ✅ 重启生效 + WorkflowLoader 缓存清理接口 | 不必动态 |
| ❌ Agent 间共享上下文（global memory）| ✅ 通过 ReportTask 表 / WorkflowContext 在 Java 内传递 | 不引向量记忆 |
| ❌ 多用户租户隔离 | ✅ user_id 逻辑隔离（沿用阶段 0）| 不做物理隔离 |
| ❌ Critic 多轮回环 | ✅ max_loops=1（最多 1 次修订） | 简化 |
| ❌ Agent 工具市场 / Agent 间 RPC | ✅ Tool SPI 静态注册（沿用阶段 2）| 不引微服务 |

---

## §2 端到端流程

```
[用户 POST /api/report/start { topic: "..." workflow: "multi_agent_v1" }]
    │
    ▼
ReportTaskService
    ├─ 落 report_task (status=PENDING, phase=PLANNING)
    ├─ 发 MQ topic=task.created msg={taskId, topic}
    └─ 立即返回 { taskId, status:PENDING, streamUrl }

──── 异步处理（RocketMQ Consumer 链路）────

[MQ task.created] → TaskOrchestrator.onTaskCreated
    │ 加载 workflow YAML "multi_agent_v1"
    │ 标记 phase=PLANNING + SseSink.nodeStatus("plan", RUNNING)
    │
    ▼
PlannerAgent.execute()
    │ qwen-max 拆解 topic → List<String> subtopics (8-12 个)
    │ 落 workflow_node_run LLM_CALL
    │ 落 workflow_subtask 表 (N 行 status=PENDING)
    │
    ▼ 发 N 条 MQ topic=research.task
[MQ research.task] × N → ResearcherAgentWorker.onResearchTask (consumer group, 并行)
    │ 每条消息独立 worker：
    │   - qwen-plus + hybrid_search 工具
    │   - 输出 SubtopicResult { content, citations }
    │   - 落 workflow_subtask 表 update status=DONE + result_json
    │   - 落 workflow_node_run LLM_CALL + TOOL_CALL
    │   - 检查"是否最后一个完成的子任务"
    │       ├─ 若是 → 发 MQ topic=analyze.task msg={taskId}
    │       └─ 否则 → ACK 退出
    │
    ▼
[MQ analyze.task] → AnalystAgent.onAnalyzeTask
    │ 读取 workflow_subtask 所有 N 行 result_json
    │ qwen-max 跨子主题分析 → AnalysisOutline { sections: List<SectionPlan> }
    │ 落 workflow_node_run LLM_CALL
    │ phase=WRITING
    │
    ▼ 发 N 条 MQ topic=write.section（每个 section 一条）
[MQ write.section] × N → WriterAgent.onWriteSection
    │ 每条消息：sectionPlan + 相关 subtopic results
    │ qwen-plus 写 1 段章节 markdown
    │ 落 report_section 表（task_id, order, title, content_md, citations_json）
    │ 落 workflow_node_run LLM_CALL
    │ 检查"是否最后一个章节"
    │   ├─ 是 → 发 MQ topic=critic.task
    │   └─ 否 → ACK
    │
    ▼
[MQ critic.task] → CriticAgent.onCriticTask
    │ 读取 report_section 所有章节 → 拼成 full markdown
    │ qwen-max 自我审查 → CriticResult { needsRevision: boolean, issues: [...] }
    │ 落 workflow_node_run LLM_CALL
    │ phase=CRITICIZING
    │
    ├─ needsRevision == false 或 loopCount >= 1（max_loops=1）
    │       │ 拼接最终 markdown 写 report_task.final_markdown
    │       │ phase=DONE, status=DONE
    │       │ SseSink.done(...)
    │       └─ 完成
    │
    └─ needsRevision == true 且 loopCount < 1
            │ loopCount++（写 workflow_loop_state 表）
            │ 发 MQ topic=write.section × M（M = 需要修订的章节数）
            └─ 回到 WriterAgent

──── 全程 SSE 推送 ────
每个 Agent 节点开始/结束 → SseSink.nodeStatus(nodeId, RUNNING/DONE/FAILED)
每个 Tool 调用 → SseSink.tool(name, params, preview)
每个 章节 完成 → SseSink.sectionDone(order, title, contentMdPreview)
最终 DONE → SseSink.done(finalMarkdown, citations)
异常 → SseSink.error(message)
```

---

## §3 数据模型变化

继承 `BaseEntity`。

### 新表 `workflow_subtask`（fanout 子任务状态表）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| task_id | BIGINT | → report_task.id |
| sub_index | INT | 0..N-1 |
| subtopic | VARCHAR(512) | Planner 输出的子主题文本 |
| status | VARCHAR(16) | PENDING / RUNNING / DONE / FAILED |
| result_json | MEDIUMTEXT | Researcher 输出（{content, citations}） |
| error_message | VARCHAR(1024) | FAILED 时填 |
| started_at / finished_at | DATETIME | |
| create_time / update_time / is_deleted | | BaseEntity |
| INDEX | task_id | |

**用途**：fanout 后追踪每个子任务状态；Researcher 完成时 update 自己那一行 + 查询是否全部 DONE（join 判定）。

### 新表 `report_section`（Writer 输出的章节存储）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| task_id | BIGINT | |
| section_order | INT | 0..N-1，章节顺序 |
| title | VARCHAR(255) | 如"行业概述" |
| content_md | MEDIUMTEXT | 章节 markdown |
| citations_json | JSON | 引用 |
| status | VARCHAR(16) | DRAFT / FINAL / REVISING（Critic 标 REVISING） |
| revision_count | INT | 修订次数 |
| create_time / update_time / is_deleted | | BaseEntity |
| INDEX | task_id, section_order |

**用途**：Writer 完成每章后插入；Critic 触发修订时 update + revision_count++；最终拼接 `final_markdown` 时按 section_order 取。

### 新表 `workflow_loop_state`（Critic 回环计数）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| task_id | BIGINT | |
| node_id | VARCHAR(64) | 如 "critic" |
| loop_count | INT | 当前已循环次数 |
| max_loops | INT | 阈值（max_loops=1） |
| create_time / update_time / is_deleted | | BaseEntity |
| UNIQUE | (task_id, node_id) | |

### 修改 `report_task` 加 2 字段

```sql
ALTER TABLE report_task
  ADD COLUMN phase VARCHAR(32) NOT NULL DEFAULT 'PLANNING'
    COMMENT 'PLANNING/RESEARCHING/ANALYZING/WRITING/CRITICIZING/DONE' AFTER status,
  ADD COLUMN progress INT NOT NULL DEFAULT 0 COMMENT '0-100，前端进度条' AFTER phase;
```

---

## §4 WorkflowEngine v2 升级

### YAML 语法扩展（`researcher_only_v1.yaml` 保留 + 新增 `multi_agent_v1.yaml`）

```yaml
name: multi_agent_v1
version: 1
nodes:
  - id: plan
    agent: Planner
    prompt: planner_prompt@v1
    model: qwen-max
    next: [research]

  - id: research
    agent: Researcher
    prompt: researcher_prompt@v2     # 阶段 2 用的 prompt 升级
    model: qwen-plus
    tools: [hybrid_search]
    fanout:
      from: ${plan.subtopics}        # 引用 plan 节点输出
    next: [analyze]
    join: all                        # 等所有 fanout 子任务完成

  - id: analyze
    agent: Analyst
    prompt: analyst_prompt@v1
    model: qwen-max
    next: [write]

  - id: write
    agent: Writer
    prompt: writer_prompt@v1
    model: qwen-plus
    fanout:
      from: ${analyze.sections}
    next: [critic]
    join: all

  - id: critic
    agent: Critic
    prompt: critic_prompt@v1
    model: qwen-max
    max_loops: 1
    on_needs_revision: write          # 触发回环时回到哪个节点
```

### 核心抽象

```java
public interface WorkflowEngine {
    // 启动一个 Workflow（同步只返回 taskId，异步执行在 MQ 链路）
    void start(long taskId, String workflowName, String topic, ExecutionContext ctx);
}

public interface NodeExecutor {
    NodeExecutionResult execute(long taskId, WorkflowNode node, NodeInput input);
}

// 节点输入：对于 fanout，子任务是单条；对于 join，input 已聚合
public record NodeInput(
    Map<String, Object> previousOutputs,    // 前序节点输出（含 fanout 聚合后的数据）
    Integer fanoutIndex,                     // 当前子任务索引（非 fanout 节点为 null）
    String fanoutPayload                     // 当前子任务负载（subtopic / sectionPlan 序列化）
) {}
```

### MQ 编排适配器

`MqWorkflowOrchestrator`：把 Engine 的"下一步要执行 X 节点"转译为"发 RocketMQ topic=X.task 消息"，消费端反向触发对应 Agent。

---

## §5 RocketMQ topic / consumer group 设计

| Topic | Consumer Group | 触发者 | 处理者 |
|---|---|---|---|
| `irp.task.created` | `irp-task-orchestrator` | ReportService.start | TaskOrchestrator.onTaskCreated |
| `irp.research.task` | `irp-researcher` | Planner 完成后批量发 | ResearcherAgentWorker (Tag=task_{id}) |
| `irp.analyze.task` | `irp-analyst` | Researcher Join 触发 | AnalystAgent |
| `irp.write.section` | `irp-writer` | Analyst 完成后批量发 | WriterAgent |
| `irp.critic.task` | `irp-critic` | Writer Join 触发 | CriticAgent |
| `irp.task.revision` | `irp-writer` | Critic 触发修订 | WriterAgent（同上 consumer）|

**消息体格式**（统一）：

```json
{
  "taskId": 42,
  "nodeId": "research",
  "fanoutIndex": 3,
  "payload": "<节点专属 JSON>"
}
```

**配置：**
- `consumeThreadMin: 4, consumeThreadMax: 16`（Researcher worker 并发）
- `maxReconsumeTimes: 3`（重试 3 次后进死信）
- `consumeMode: CLUSTERING`（消费者组负载均衡）
- DLQ topic 自动创建：`%DLQ%irp-researcher` 等

---

## §6 5 个 Agent 详细职责

### Planner

- **输入**：topic
- **LLM 模型**：qwen-max
- **工具**：无
- **prompt** (`planner_prompt_v1.txt`)：
  > 你是一位行业研究项目经理。根据用户提供的研究主题，将其拆解为 8-12 个**互不重叠**的子主题，每个子主题描述一个具体的研究维度（如规模/产业链/玩家/政策/技术/出口/挑战/趋势/案例/对比）。输出 JSON：`{"subtopics": ["...", "..."]}`
- **输出落库**：`workflow_node_run` LLM_CALL + N 行 `workflow_subtask` PENDING

### Researcher (×N)

- **输入**：单个 subtopic（由 fanout 拆出）
- **LLM 模型**：qwen-plus
- **工具**：`hybrid_search`（阶段 2 已实现）
- **prompt** (`researcher_prompt_v2.txt` 升级阶段 2 的 v1)：
  > 你是一位行业研究员。针对给定的子主题，使用 hybrid_search 工具检索（1-3 次），输出该子主题的研究材料。输出 JSON：`{"content": "...", "citations": [{docId, ...}]}`，content 200-400 字。
- **输出落库**：`workflow_node_run` LLM_CALL + N 行 TOOL_CALL + update `workflow_subtask` status=DONE

### Analyst

- **输入**：N 个 subtopic results（从 `workflow_subtask` 读取聚合）
- **LLM 模型**：qwen-max
- **工具**：无
- **prompt** (`analyst_prompt_v1.txt`)：
  > 你是一位资深分析师。基于多个子主题的研究材料，进行跨主题分析（矛盾发现、趋势归纳、关键玩家对比），并设计研报章节大纲（4-6 个 section）。输出 JSON：`{"sections": [{"order": 0, "title": "...", "outline": "...", "relatedSubtopics": [0, 2]}]}`
- **输出落库**：`workflow_node_run` LLM_CALL

### Writer (×M)

- **输入**：单个 sectionPlan + 关联的 subtopic results
- **LLM 模型**：qwen-plus
- **工具**：无（数据已经在 input 里）
- **prompt** (`writer_prompt_v1.txt`)：
  > 你是一位研报作者。基于章节大纲和检索材料，写一段 300-500 字的章节正文（Markdown，二级标题为 title），段落自然衔接，所有数字必须来自检索片段，末尾标注 `[1] [2]` 引用编号。
- **输出落库**：`workflow_node_run` LLM_CALL + insert `report_section`

### Critic

- **输入**：拼接的 full markdown（所有 sections）
- **LLM 模型**：qwen-max
- **工具**：无（不再检索）
- **prompt** (`critic_prompt_v1.txt`)：
  > 你是一位严苛的编辑。审查整份研报，找出（1）事实错误（2）引用与正文不符（3）逻辑断层（4）数字一致性。如全部通过，输出 `{"needsRevision": false, "issues": []}`；如有问题，输出 `{"needsRevision": true, "issues": [{"sectionOrder": 2, "problem": "...", "suggestion": "..."}]}`，最多 5 个 issue。
- **输出落库**：`workflow_node_run` LLM_CALL + 触发回环时 update `workflow_loop_state.loop_count`

---

## §7 SSE 协议扩展

阶段 2 的 5 个事件保留，加 2 个新事件：

| event | data | 说明 |
|---|---|---|
| `phase_changed` | `{"phase":"PLANNING","progress":10}` | 阶段切换 |
| `section_done` | `{"order":2,"title":"...","preview":"..."}` | Writer 完成单章节 |

`progress` 计算规则（粗略，前端显示）：
- PLANNING: 0-10%
- RESEARCHING: 10-50%（fanout 完成进度比例）
- ANALYZING: 50-60%
- WRITING: 60-90%
- CRITICIZING: 90-95%
- DONE: 100%

---

## §8 关键接口

阶段 2 的 4 个接口**全部保留**：

- `POST /api/report/start`（无变化）
- `GET /api/report/{id}`（响应加 phase / progress）
- `GET /api/report/{id}/stream`（新增 2 个事件）
- `GET /api/report/{id}/trace`（无变化）

新增接口：

- `GET /api/report/{id}/sections`：列出所有章节（`report_section` 表行 + 顺序）
- `POST /api/admin/workflow/reload`（admin）：清 WorkflowLoader 缓存，重新读 YAML（不重启可热更）

---

## §9 关键文件结构（新增）

```
src/main/resources/
├── workflow/
│   ├── researcher_only_v1.yaml         ← 阶段 2 保留
│   └── multi_agent_v1.yaml             ← 新增
└── prompts/
    ├── researcher_prompt_v1.txt        ← 阶段 2 保留
    ├── researcher_prompt_v2.txt        ← 升级
    ├── planner_prompt_v1.txt           ← 新增
    ├── analyst_prompt_v1.txt           ← 新增
    ├── writer_prompt_v1.txt            ← 新增
    └── critic_prompt_v1.txt            ← 新增

src/main/java/com/leo/enterpriseinertraining/
├── entity/
│   ├── WorkflowSubtask.java            ← 新增 extends BaseEntity
│   ├── ReportSection.java              ← 新增 extends BaseEntity
│   └── WorkflowLoopState.java          ← 新增 extends BaseEntity
├── mapper/
│   ├── WorkflowSubtaskMapper.java
│   ├── ReportSectionMapper.java
│   └── WorkflowLoopStateMapper.java
├── agent/role/
│   ├── PlannerAgent.java               ← 新增
│   ├── ResearcherAgent.java            ← 改造（阶段 2 单 Agent → fanout 子任务版）
│   ├── AnalystAgent.java               ← 新增
│   ├── WriterAgent.java                ← 新增
│   └── CriticAgent.java                ← 新增
├── workflow/
│   ├── WorkflowDef.java                ← 扩 fanout / join / max_loops 字段
│   ├── WorkflowNode.java               ← 同上
│   ├── WorkflowLoader.java             ← 加 reload() 方法
│   ├── WorkflowEngine.java             ← 改为 v2，async + MQ 适配
│   ├── NodeInput.java                  ← 新增 record
│   ├── NodeExecutionResult.java        ← 新增 record
│   ├── FanoutHelper.java               ← 处理 ${x.y} 表达式 + 子任务管理
│   └── JoinTracker.java                ← workflow_subtask 维度跟踪 join 完成
├── mq/
│   ├── MqTopics.java                   ← 常量类，列所有 topic 名
│   ├── MqMessage.java                  ← record 消息体格式
│   ├── TaskOrchestratorConsumer.java   ← topic=task.created 消费者
│   ├── ResearcherWorker.java           ← topic=research.task 消费者
│   ├── AnalystConsumer.java
│   ├── WriterConsumer.java
│   ├── CriticConsumer.java
│   └── MqProducerService.java          ← 统一发送
├── service/
│   ├── ReportSectionService.java + impl/
│   └── ReportService.java              ← 改：findById 返回加 phase/progress；加 listSections
├── controller/
│   ├── ReportController.java           ← 加 GET /{id}/sections
│   └── AdminWorkflowController.java    ← 新增（reload）
└── stream/
    └── SseSink.java                    ← 加 phaseChanged / sectionDone 两个方法

src/test/java/...
└── workflow/
    ├── FanoutHelperTest.java           ← TDD
    └── JoinTrackerTest.java            ← TDD（不依赖 DB，纯逻辑）
```

---

## §10 阶段化交付（14-16 Task，1.5-2 周）

| Task | 子模块 | 关键产物 |
|---|---|---|
| T1 | DDL（3 张新表 + report_task 加 2 字段）+ 3 entity + 3 mapper | DB schema 就位 |
| T2 | RocketMQ Producer/Consumer 配置 + MqTopics 常量 + MqMessage record | 启动可见 consumer 注册日志 |
| T3 | MqProducerService（统一发送 + 错误处理）+ 单测 | producer bean 可注入 |
| T4 | 5 个新 prompt 文件 + researcher v2 升级 | resources/prompts/*.txt 齐备 |
| T5 | multi_agent_v1.yaml + WorkflowDef/WorkflowNode 字段扩展 + WorkflowLoaderTest | YAML 解析含 fanout/join/max_loops |
| T6 | NodeInput/NodeExecutionResult records + FanoutHelper（${x.y} 表达式 + 拆 List）TDD | 单测覆盖 ${plan.subtopics} 解析 |
| T7 | JoinTracker（基于 workflow_subtask 检查全部 DONE 的逻辑）TDD | 单测覆盖 last-completed-detection |
| T8 | PlannerAgent + TaskOrchestratorConsumer | 提交主题 → MQ → Planner 跑 → 落 N 行 subtask + 发 N 条 research.task |
| T9 | ResearcherAgent 改造（适配 fanout 子任务版）+ ResearcherWorker consumer | 并发消费 research.task → 单 subtopic 处理 + Join 触发 analyze.task |
| T10 | AnalystAgent + AnalystConsumer | 跨子主题分析 → 输出 sections → 发 N 条 write.section |
| T11 | WriterAgent + WriterConsumer + ReportSection 落库 | 章节并发写入 + Join 触发 critic.task |
| T12 | CriticAgent + CriticConsumer + WorkflowLoopState 回环逻辑 | needsRevision 触发回 write.section / 否则拼接 final_markdown + DONE |
| T13 | SseSink 加 phaseChanged + sectionDone + 各 Agent 在节点切换时调 | 前端能看到阶段切换 + 章节增量 |
| T14 | WorkflowEngine v2 重构：start() 触发 MQ + 改 NodeExecutor 接入 | 统一入口 |
| T15 | ReportController 加 /{id}/sections + /admin/workflow/reload + ReportResultVO 加 phase/progress | API 升级 |
| T16 | 端到端冒烟（用户在 IDE 跑）+ 出口指标核验 | DoD 通过 |

---

## §11 验证方式（DoD）

### 功能层（用户在 IDE 验证）
- [ ] schema 升级 + 3 新表存在 + report_task 加了 phase/progress
- [ ] 启动日志含 5 个 consumer group 注册：`irp-task-orchestrator / irp-researcher / irp-analyst / irp-writer / irp-critic`
- [ ] `POST /api/report/start { workflow: "multi_agent_v1", topic: "..." }` 立即返回 taskId
- [ ] 通过 SSE 看到事件序列：phase_changed(PLANNING) → node_status(plan RUNNING/DONE) → phase_changed(RESEARCHING) → 多个 node_status(research) + tool 事件 → phase_changed(ANALYZING) → node_status(analyze) → phase_changed(WRITING) → 多个 section_done → phase_changed(CRITICIZING) → node_status(critic) → done
- [ ] `GET /{id}` 在不同时刻返回不同 phase + progress 0-100
- [ ] `GET /{id}/sections` 返回 4-6 个 section（按 order 排序）
- [ ] `GET /{id}/trace` 返回 ≥ 12 个 step（Planner LLM + Researcher×N (LLM+TOOL) + Analyst LLM + Writer×M LLM + Critic LLM）
- [ ] `final_markdown` 含 4-6 个二级标题 + ## 参考资料

### 性能层
- [ ] 单研报 P95 ≤ 25s（Researcher fanout 并发，目标 18s）
- [ ] Researcher 同时跑 8 个子任务的实际并发度 ≥ 4（看 MQ consumer 日志）

### 工程层
- [ ] 单个 Researcher 子任务失败 3 次后进 DLQ，整体任务标记 FAILED（节点级失败不导致全局僵死）
- [ ] Critic 触发修订时（手工注入 needsRevision=true 测试）：loopCount=1，重发 write.section，再次到 Critic 时强制接受
- [ ] 重复提交同一 topic：每次都创建新 task（不去重）
- [ ] WorkflowLoader.reload() 后修改 YAML 生效

### 测试层
- [ ] `FanoutHelperTest` 4+ 测试通过
- [ ] `JoinTrackerTest` 3+ 测试通过

---

## §12 风险与延后

| 风险 | 缓解 |
|---|---|
| RocketMQ broker IP 配置（阶段 0 brokerIP1=127.0.0.1）在跨容器场景失败 | 单 JVM 客户端跑宿主 OK；若失败改 `host.docker.internal` |
| Fanout 子任务数被 LLM 决定（8-12），如果异常返回 0 或 100 → 防御 | Planner 输出 schema 校验 + size 边界 (3-15) clamp |
| Critic 输出"需修订" 但 Writer 改完后 Critic 仍说需修订 | max_loops=1 强制接受兜底 |
| Researcher×N 并发耗光 DashScope embedding/rerank QPS | 单实例 4-16 worker 远低于 60 QPS 限额；阶段 4 加限流再扩 |
| Virtual Thread + JDBC pin native thread | 阶段 3 单 task 并发 ≤ 16，单实例并发 task ≤ 5，HikariCP 池 8 够 |
| MQ 消息丢失（broker 重启）| RocketMQ 默认同步刷盘 + 重试 3 次；DLQ 兜底 |
| 跨 Agent 数据传递（plan output → research input）通过 DB 不通过 MQ payload | workflow_subtask 表充当队列 + 状态机；MQ payload 只携带 task_id + fanout_index |

---

## §13 后续步骤

1. 写入 git
2. 用户扫一眼 spec，有调整告诉我；无调整 → invoke writing-plans
3. 阶段 3 完成 → 阶段 4 = 平台中台（Prompt 版本/Tool UI/Trace 看板/Token 成本/Redis Pub/Sub）
