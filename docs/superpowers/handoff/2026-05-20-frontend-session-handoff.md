# 行业研报多 Agent 协作平台 — 前端开发会话交接手册

> 用途：新 Claude Code session 接手**前端开发**时第一份要读的文档。
> 项目根目录：`/Users/zhengsmacbook/Desktop/miniProject/claude/enterprise-iner-training`
> 主分支：`main`
> 交接日期：2026-05-20

---

## §0 给新会话 Claude 的开场指令

如果你（新会话的 Claude）刚接手这个文件：

1. 先 `cat` 读完本文件全部内容
2. 然后扫一眼项目 git 历史确认现状：`git log --oneline | head -30`
3. 用 `superpowers:brainstorming` skill 与用户一起设计前端方案
4. 用 `superpowers:writing-plans` skill 写实施计划
5. 用 `superpowers:subagent-driven-development` skill 逐 Task 执行

**与本项目用户合作的几条硬性原则**（来自前一 session 的经验教训）：

- 用户对 token 消耗非常敏感 —— **不要让 subagent 跑 `mvn` / `npm` / `curl` / 启动应用**，全部由用户在 IDE 验证
- 不要在 shell 里反复试错环境变量；项目已用 `.env`（被 gitignore）固化 secret
- 同一个 bug 修两次都没成功 → 立即停下来重新读代码，不要陷入轮询
- review 阶段也不要跑命令，只看 `git diff` 静态比对 spec
- 每个 Task 完成后做合并 spec+quality review 即可，不必拆成两次（节省 token）
- 给用户 4 个 PDF 之类的具体输入时主动用 WebSearch + curl 直拉，**不要让用户自己手动下载**

---

## §1 项目背景

### 求职定位
- 用户：1-3 年 Java 社招，目标转 **AI Agent 后端工程师**
- 简历亮点公式：业务价值 + 技术深度 + 量化指标
- 已交付亮点：RAG（PGVector + ES + Rerank）/ Single-Agent (Spring AI Function Calling) / Workflow YAML / SSE 流式 / Trace 可观测

### 全局 spec
唯一权威总设计文档：`~/.claude/plans/now-in-china-sorted-treasure.md`
（user home 目录下；新 session 用 `Read` 工具读取，了解 7 个阶段的整体规划）

### 阶段进度

| 阶段 | 内容 | 状态 | 关键 commit |
|---|---|---|---|
| 0 | 脚手架（Security + JWT + MySQL/Redis/RocketMQ/PGVector/ES docker-compose）| ✅ 完成 | `06bb05f` 之前 |
| 1 | RAG 核心（PGVector 向量 + ES BM25 + RRF + DashScope rerank + 评估器）| ✅ 完成（语料 31 篇）| `06bb05f` |
| 2 | Single-Agent + Workflow YAML + SSE 流式 + Tool SPI + Trace 表 | ✅ 完成 | `ac7539c` |
| 3 | Multi-Agent (Planner/Researcher/Analyst/Writer/Critic) + RocketMQ + Redis Pub/Sub | ⏳ 待启动 | — |
| 4 | 平台中台（Prompt 版本/Tool 注册 UI/Trace 看板/Token 成本）| ⏳ 待启动 | — |
| 5 | LoRA 微调子模块（Query 改写）| ⏳ 待启动 | — |
| 6 | 评估 + 优化（RAGAS + LLM-as-Judge + A/B）| ⏳ 待启动 | — |

**前端目标**：覆盖**已完成的阶段 0/1/2** 所有接口，先做 MVP，后续阶段做新功能时同步扩前端。

---

## §2 后端 API 完整清单

### 服务地址

- 开发环境：`http://localhost:8080`
- Knife4j Swagger UI：`http://localhost:8080/doc.html`（Bearer auth 已配）
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`（前端可用来生成 TS 类型）

### 统一响应包装

所有接口（除 SSE 流）都返回 `BaseResponse<T>`：

```typescript
interface BaseResponse<T> {
  code: number;     // 0=成功，非 0=失败
  data: T | null;
  message: string;  // "ok" 或错误描述
}
```

### 错误码（`com.leo.enterpriseinertraining.exception.ErrorCode`）

| code | 含义 |
|---|---|
| 0 | 成功 |
| 40000 | 参数错误 |
| 40010 | 用户名已存在 |
| 40011 | 用户不存在 |
| 40012 | 用户名或密码错误 |
| 40100 | 未登录 |
| 40101 | 无权限 |
| 40110 | JWT 无效或已过期 |
| 40300 | 禁止访问 |
| 40400 | 请求数据不存在 |
| 42900 | 请求过于频繁 |
| 50000 | 系统内部异常 |

### 鉴权

**所有接口（除以下白名单）都需要 JWT**：
- `/api/user/register`
- `/api/user/login`
- `/api/health/**`
- `/v3/api-docs/**`、`/swagger-ui/**`、`/swagger-ui.html`、`/doc.html`、`/webjars/**`

JWT 通过 HTTP Header 传：`Authorization: Bearer <token>`
（注意 `Bearer` 后一个空格）

---

### 阶段 0 接口（用户）

#### POST `/api/user/register`
```ts
// Request body
{ username: string (4-32), password: string (6-64), nickname?: string }
// Response data
{ id: number, username: string, nickname: string, role: "USER" | "ADMIN" }
```

#### POST `/api/user/login`
```ts
// Request body
{ username: string, password: string }
// Response data
{
  token: string,    // JWT，24h 有效
  user: { id, username, nickname, role }
}
```

#### GET `/api/user/me`（需 JWT）
```ts
// Response data
{ id, username, nickname, role }
```

#### GET `/api/health`（无需 JWT）
```ts
// Response data
{ status: "UP", time: "2026-05-20T14:30:00" }
```

---

### 阶段 1 接口（RAG）

#### POST `/api/rag/ingest`（需 JWT，慢，几分钟）
```ts
// Request body
{ source: string, pathOrGlob: string }   // 如 {"source":"CAICT","pathOrGlob":"*.pdf"}
// Response data
{
  filesScanned: number,
  filesIngested: number,
  filesSkipped: number,
  chunksWritten: number,
  tookMs: number,
  ingestedTitles: string[]
}
```

#### POST `/api/rag/search`（需 JWT）
```ts
// Request body
{ query: string, topK?: number (1-50, default 10), useRerank?: boolean (default true) }
// Response data
{
  query: string,
  tookMs: number,
  hits: [{
    chunkId: number,
    score: number,
    rerankScore: number | null,
    content: string,
    citation: {
      docId, docTitle, source, sectionTitle, pageStart, pageEnd
    }
  }]
}
```

#### POST `/api/rag/eval/synthesize?sampleSize=N`（需 JWT，慢）
返回 `data: number`（合成成功的 query 条数）

#### POST `/api/rag/eval/run`（需 JWT，慢）
```ts
// Response data
{
  totalQueries, recallAt10NoRerank, recallAt10WithRerank,
  mrrNoRerank, mrrWithRerank, ndcg10NoRerank, ndcg10WithRerank,
  rerankLiftNdcg, tookMs, runAt
}
```

#### GET `/api/rag/eval/latest`（需 JWT）
返回最近一次 eval/run 的结果（同上）

---

### 阶段 2 接口（Report / Agent）

#### POST `/api/report/start`（需 JWT，立即返回）
```ts
// Request body
{
  topic: string (4-500),
  workflow?: string (default "researcher_only_v1")
}
// Response data
{
  taskId: number,
  status: "PENDING",
  streamUrl: "/api/report/<taskId>/stream"
}
```

#### GET `/api/report/{id}`（需 JWT，可轮询）
```ts
// Response data
{
  taskId, status: "PENDING"|"RUNNING"|"DONE"|"FAILED",
  topic, finalMarkdown: string | null,
  citations: CitationData[],
  errorMessage: string | null,
  startedAtEpochMillis: number | null,
  finishedAtEpochMillis: number | null
}
```

#### GET `/api/report/{id}/stream`（需 JWT，SSE 长连接）

**响应类型**：`text/event-stream`

**事件协议**：
```
event: node_status
data: {"nodeId":"research","status":"RUNNING"|"DONE"|"FAILED"}

event: tool
data: {"toolName":"hybrid_search","paramsJson":"...","resultPreview":"..."}

event: token
data: {"delta":"在2026年，"}

event: done
data: {"finalMarkdown":"...","citations":[...]}

event: error
data: {"message":"..."}

event: ping
data: {"ts":1779...}
```

**前端实现注意**：
- `EventSource` 不支持自定义 Header → 阶段 2 暂时**无法直接用** `new EventSource(url)`，因为浏览器没法带 JWT
- 临时方案 1：用 `fetch` + `ReadableStream` 自己解析 SSE（保留 Authorization Header）
- 临时方案 2：找前端 SSE 库支持 Header 传入（如 `@microsoft/fetch-event-source`）
- 后端长期方案：支持 `?token=xxx` query 参数鉴权（阶段 3 加）

#### GET `/api/report/{id}/trace`（需 JWT）
```ts
// Response data
[{
  id, stepSeq, nodeId, agentRole,
  stepType: "LLM_CALL" | "TOOL_CALL",
  promptVersion, model, toolName,
  tokensIn, tokensOut, latencyMs,
  status: "OK" | "ERROR",
  errorMessage,
  inputJsonPreview, outputJsonPreview     // > 300 字符会截断 + "..."
}]
```

---

## §3 前端推荐技术栈

**这是建议，不是强制**。新 session 和用户 brainstorming 时可以调整。

| 维度 | 推荐 | 理由 |
|---|---|---|
| 框架 | **Vue 3 + `<script setup>`** | 国内主流，配合后端 Java 全家桶的生态 |
| 构建 | **Vite 5+** | 启动快，DX 好 |
| 状态管理 | **Pinia** | Vue 3 官方推荐 |
| UI 库 | **Element Plus** 或 **Naive UI** | 中文项目首选，组件齐全 |
| HTTP | **Axios** | 拦截器写 JWT 注入 + 401 重定向 + BaseResponse 解包 |
| 路由 | **Vue Router 4** | 标配 |
| 语言 | **TypeScript** | 简历亮点；可用 `openapi-typescript` 从 `/v3/api-docs` 直接生成类型 |
| Markdown 渲染 | **markdown-it** + **highlight.js** | 渲染 LLM 输出的报告 |
| SSE | **@microsoft/fetch-event-source** | 支持自定义 Header（普通 EventSource 不支持）|
| 图标 | **@iconify/vue** 或 ElementPlus 内置 | — |

**目录建议**：

```
frontend/                              # 与现有 Spring Boot 项目同级 / 或单独仓库
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── router/
│   │   └── index.ts
│   ├── stores/
│   │   ├── auth.ts                    # JWT + currentUser
│   │   └── report.ts                  # 当前任务/SSE 状态
│   ├── api/
│   │   ├── client.ts                  # axios 实例 + JWT 拦截器 + BaseResponse 解包
│   │   ├── user.ts                    # login / register / me
│   │   ├── rag.ts                     # ingest / search / eval
│   │   ├── report.ts                  # start / get / stream / trace
│   │   └── types.ts                   # 从 OpenAPI 生成
│   ├── views/
│   │   ├── LoginView.vue
│   │   ├── RegisterView.vue
│   │   ├── HomeView.vue               # 主入口
│   │   ├── rag/
│   │   │   ├── RagSearchView.vue      # query 输入 + hits 列表
│   │   │   ├── RagIngestView.vue      # admin 触发入库
│   │   │   └── RagEvalView.vue        # 跑评估 + 看历史指标
│   │   └── report/
│   │       ├── ReportSubmitView.vue   # topic 输入 → 跳到详情
│   │       ├── ReportDetailView.vue   # SSE 实时流 + markdown 渲染
│   │       └── ReportTraceView.vue    # 时间轴展示 Trace（核心简历亮点 UI）
│   ├── components/
│   │   ├── SseEventStream.vue         # SSE 消费器（可复用）
│   │   ├── CitationCard.vue           # 引用片段展示
│   │   ├── TraceTimeline.vue          # Trace 步骤时间轴
│   │   └── MarkdownRenderer.vue
│   ├── composables/
│   │   ├── useSse.ts                  # fetch-event-source 包装
│   │   └── useAuth.ts
│   └── styles/
│       └── main.css
└── ...
```

---

## §4 期望的前端开发流程

**与后端会话完全一致**：

### Phase F0：基础设施（脚手架 + 鉴权）
- Vite + Vue3 + TS 项目初始化
- Axios + JWT 拦截器 + BaseResponse 解包
- Login / Register 页面 + Pinia auth store
- 路由守卫（未登录跳 login）
- 一个最简 dashboard 页面验证 `/api/user/me`

### Phase F1：RAG UI
- Hybrid Search 检索页（query 输入 + topK + useRerank 开关 + hits 卡片列表 + 引用展示）
- Ingest 触发页（admin only，进度提示）
- Eval 看板（跑评估 + 历史指标对比 + Rerank Lift 图表）

### Phase F2：Report Agent UI（**项目核心 demo 页**）
- 提交主题 → 立即跳详情页
- **左侧 SSE 实时流**：节点状态 + 工具调用 + token 字符逐个出现
- **右侧最终 markdown 渲染**（含 ## 参考资料 + 可点击引用卡片）
- **底部 Trace 时间轴**：每个 LLM_CALL / TOOL_CALL 一行，含 tokens / latency / 折叠的 input/output JSON

### Phase F3+：等阶段 3/4 后端落地后再扩

---

## §5 出口标准（前端 MVP）

- ✅ 注册 / 登录 / 退出 / 我的信息
- ✅ Hybrid Search 检索 + 引用展示
- ✅ 提交研究主题 → 看到 SSE 实时流 → 看到 markdown 成稿 → 看到 Trace 时间轴
- ✅ 401/403 拦截器自动跳登录
- ✅ 移动端基本可用（不强求精美适配，能正常显示即可）
- ✅ 视觉风格统一 + Loading 状态完整 + 错误提示友好
- ✅ 部署一个 `npm run dev` 能跑通的本地版本（不强求生产 build）

### 加分项（如果时间允许）
- Trace 时间轴用 **Mermaid** 或 **vis-timeline** 渲染甘特图
- markdown 引用 `[1] [2]` 点击高亮对应 citation card
- 暗色主题切换
- 多语言 i18n（中英）

---

## §6 第一次会话该做什么（推荐路径）

新 session 一开始，按这个顺序：

1. **`Read` 本文件** + `Read` `~/.claude/plans/now-in-china-sorted-treasure.md`
2. **告诉用户**："我已读完后端项目交接手册。建议先用 brainstorming 定下前端范围与技术栈，然后写 spec / plan，按 phase 推进。"
3. **invoke `superpowers:brainstorming`** —— 关键决策点：
   - 单独建 `frontend/` 子目录 vs 独立 git 仓库？
   - Vue 3 vs React vs Svelte？
   - UI 库 Element Plus vs Naive UI vs Ant Design Vue？
   - TypeScript vs JavaScript？
   - SSE 库选型？
   - Mock 接口 vs 直连本地后端开发？
4. **invoke `superpowers:writing-plans`** —— 写 Phase F0 的实施计划（bite-sized 步骤 + 完整代码）
5. **invoke `superpowers:subagent-driven-development`** —— 逐 Task 执行

---

## §7 关键技术细节给前端注意

### 1. JWT secret 长度
- application.yml 的 `app.jwt.secret` 是 256-bit base64 字符串
- 前端只用接收 / 转发 token，不需要解码 / 验签
- token 24h 过期 → axios 拦截器收到 `40110` 自动跳登录

### 2. CORS
- 阶段 0 SecurityConfig 里 **CORS 已 disable**（`.cors(AbstractHttpConfigurer::disable)`）
- 如果前端跑在不同端口（如 5173），会被浏览器 CORS 拦
- **后端临时方案**：在 Vite 配 proxy：
  ```ts
  // vite.config.ts
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/doc.html': 'http://localhost:8080',
      '/v3/api-docs': 'http://localhost:8080'
    }
  }
  ```
- **后端长期方案**：在 SecurityConfig 加 CorsConfigurationSource bean，前端阶段 0 时让用户改一下后端配置

### 3. SSE 鉴权坑
- 浏览器 `EventSource` 不支持自定义 Header
- 必须用 `@microsoft/fetch-event-source` 这种库
- 或后端 SSE 端点支持 `?token=xxx` query 参数（阶段 3 待加）

### 4. OpenAPI 自动生成类型
```bash
# 一行命令生成 TS 类型
npx openapi-typescript http://localhost:8080/v3/api-docs -o src/api/types.ts
```

### 5. 开发时 mock 数据
- 后端跑起来要先 docker compose up + IDE Run 应用，前端开发会等待
- 可以用 **MSW (Mock Service Worker)** 做 API mock，独立前端开发
- 但 SSE Mock 复杂，开发 SSE 相关功能时直连后端最直接

---

## §8 简历亮点（前端 MVP 完成后）

> 基于 Vue 3 + TypeScript + Element Plus 构建行业研报多 Agent 协作平台前端。集成 Axios JWT 鉴权拦截器、Pinia 状态管理、Vue Router 路由守卫，覆盖鉴权 / RAG 检索 / Agent 任务提交 / SSE 流式渲染 / Trace 时间轴等核心场景。重点实现**基于 `@microsoft/fetch-event-source` 的 SSE 消费层**，支持 Authorization Header 鉴权、5 种事件类型解析、自动重连，与后端 Spring AI Function Calling Agent 实时联动；Trace 时间轴用 vis-timeline 可视化每次 LLM/Tool 调用的 token 与耗时。

---

## §9 与后端的对接节奏

| 阶段 | 后端状态 | 前端动作 |
|---|---|---|
| 当前（2026-05-20）| 阶段 0/1/2 已完成 | **可以开始前端开发** |
| 阶段 3 完成后 | 加 Multi-Agent + RocketMQ | 前端给 Report 详情页加多节点 / 多 Agent 状态展示 |
| 阶段 4 完成后 | 加 Prompt 版本管理 / Token 成本看板后端接口 | 前端加平台中台页（admin only） |
| 阶段 6 完成后 | 加 RAGAS / LLM-as-Judge | 前端加评估对比图表 |

---

## §10 联系信息

- 前一会话使用的核心 spec 文件：
  - 总 spec：`~/.claude/plans/now-in-china-sorted-treasure.md`
  - 阶段 1 spec：`docs/superpowers/specs/2026-05-19-phase-1-rag-core-design.md`
  - 阶段 2 spec：`docs/superpowers/specs/2026-05-19-phase-2-single-agent-workflow-sse.md`
  - 阶段 1 plan：`docs/superpowers/plans/2026-05-19-phase-1-rag-core.md`
  - 阶段 2 plan：`docs/superpowers/plans/2026-05-19-phase-2-single-agent-workflow-sse.md`
- 后端 git baseline：commit `ac7539c`（main 分支）
- DashScope 模型：qwen-plus（chat）/ text-embedding-v3 / gte-rerank-v2
- 本地服务端口：`http://localhost:8080`

---

**祝前端开发顺利。** 任何超出"前端"范围的问题（比如发现后端 API 设计不合理需要改）—— 先在本会话里跟用户讨论，避免双 session 同时改后端导致冲突。
