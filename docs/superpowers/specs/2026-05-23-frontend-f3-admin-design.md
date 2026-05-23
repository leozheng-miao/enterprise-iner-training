# F3 前端 Admin 中台 设计规范

> 项目：行业研报多 Agent 协作平台（IRP）
> 阶段：F3（Admin 后台 + HomeView 真实数据接入）
> 编写日期：2026-05-23
> 前端技术栈：Vue 3 + TypeScript + Vite 5 + Element Plus + Pinia + ECharts
> 后端基址：`http://localhost:8080`（开发代理）

---

## 1. 目标与范围

### 1.1 目标

在已完成 F0–F2（登录/注册/HomeView 框架/RAG 检索/研报提交+SSE 详情）基础上，实现 Admin 平台中台：

- **HomeView**：从 mock 数据切换到真实接口数据。
- **5 个 Admin 页面**：平台统计、任务管理、Prompt 版本管理、LLM-as-Judge 评分、Workflow 管理。
- 视觉上严格还原已上传的 5 张设计图。后端没有支持的 UI 元素用 mock 占位，统一打 TODO，并产出一份后端接口缺口文档。

### 1.2 不在范围

- 不引入测试框架（vitest / playwright），靠 `vue-tsc` 类型检查 + 手动浏览器验证。
- 不为 admin 路由增加 `isAdmin` 守卫，与 handoff 文档建议一致。
- 不重构现有 F0–F2 已交付的组件（除明确的 HomeView 数据源切换）。
- 不做 i18n。

### 1.3 交付节奏

按优先级分三批 commit，每批结束可独立运行：

| 批次 | 内容 |
|---|---|
| **P0** | 基建（`api/admin.ts`、`types/admin.ts`、`apiPut`、共享 utils）+ 路由/菜单 + `AdminStatsView` + `AdminTaskListView` + `HomeView` 真实数据接入 |
| **P1** | `AdminPromptView` + `AdminJudgeView` |
| **P2** | `AdminWorkflowView` + 后端接口缺口文档 |

---

## 2. 后端接口（已与 Controller 校准）

| Method | URL | 用途 | 关键字段 |
|---|---|---|---|
| GET | `/admin/stats/overview` | 平台总览 | `PlatformOverviewVO` |
| GET | `/admin/stats/cost/model` | 按模型成本 | `ModelCostVO[]` |
| GET | `/admin/stats/cost/agent` | 按 Agent 成本 | `AgentCostVO[]` |
| GET | `/admin/tasks?status=&page=1&size=20` | 任务列表分页 | `Page<TaskBriefVO>` |
| GET | `/admin/prompts` | Prompt 全部版本 | `PromptTemplateVO[]` |
| GET | `/admin/prompts/{id}` | 单版本详情 | `PromptTemplateVO` |
| POST | `/admin/prompts` | 新建版本 | body: `PromptCreateRequest` |
| PUT | `/admin/prompts/{id}` | 修改版本 | body: `PromptUpdateRequest` |
| POST | `/admin/prompts/{id}/activate` | 设为生效 | — |
| POST | `/admin/eval/judge/{taskId}` | 跑一次评分（5–15s） | `JudgeRunVO` |
| GET | `/admin/eval/judge/{taskId}/history` | 该 task 历史评分 | `JudgeRunVO[]` |
| GET | `/admin/eval/judge?limit=20` | 当前租户最近评分 | `JudgeRunVO[]` |
| POST | `/admin/workflow/reload` | 清空 YAML 缓存 | `'reloaded'` |
| POST | `/admin/query-rewrite` | Query 改写 | body: `{topic}` → `QueryRewriteVO` |

**JWT 自动注入**：所有 `/admin/*` 复用 `client.ts` 拦截器，无需额外处理。

---

## 3. 文件结构

仅列变更点：

```
frontend/src/
├── api/
│   ├── client.ts                # ✏️ 追加 apiPut（apiDelete 备用，F3 不用）
│   └── admin.ts                 # 🆕 admin 接口封装
├── types/
│   └── admin.ts                 # 🆕 全部 admin VO/DTO 类型
├── router/
│   └── index.ts                 # ✏️ 追加 5 条 admin/* 路由
├── layouts/
│   └── DefaultLayout.vue        # ✏️ 启用 4 个 admin 菜单
├── views/
│   ├── HomeView.vue             # ✏️ 切换到真实接口数据
│   └── admin/                   # 🆕
│       ├── AdminStatsView.vue
│       ├── AdminTaskListView.vue
│       ├── AdminPromptView.vue
│       ├── AdminJudgeView.vue
│       └── AdminWorkflowView.vue
├── components/admin/            # 🆕
│   ├── AdminPageHeader.vue
│   ├── ModelCostBarChart.vue
│   ├── AgentCostDonutChart.vue
│   ├── RecentModelCallsTable.vue
│   ├── SystemHealthPanel.vue
│   ├── PromptGroupList.vue
│   ├── PromptDetailPanel.vue
│   ├── JudgeScoreRadar.vue
│   ├── JudgeDetailDrawer.vue
│   ├── WorkflowGraph.vue
│   └── QueryRewritePanel.vue
├── utils/
│   ├── format.ts                # ✏️ 追加 formatCny / formatNumber / formatDuration
│   └── taskStatus.ts            # 🆕 状态→tag type 映射
├── mock/
│   ├── dashboard.ts             # ❌ HomeView 接入真实数据后删除
│   └── admin-placeholders.ts    # 🆕 集中 mock 数据 + TODO 标注
└── (HomeView 接入真实接口后，statItems/recentActivities 来自后端)
```

**视图文件大小约束**：每个 admin 视图 ≤200 行，业务下沉到 `components/admin/`。

---

## 4. 路由 / 菜单 / 权限

### 4.1 路由表（追加到 `DefaultLayout` 的 `children`）

| Path | name | meta | 文件 |
|---|---|---|---|
| `/admin/stats` | `admin-stats` | `{requiresAuth:true, title:'平台统计'}` | `AdminStatsView.vue` |
| `/admin/tasks` | `admin-tasks` | `{requiresAuth:true, title:'任务管理'}` | `AdminTaskListView.vue` |
| `/admin/prompts` | `admin-prompts` | `{requiresAuth:true, title:'Prompt 管理'}` | `AdminPromptView.vue` |
| `/admin/judge` | `admin-judge` | `{requiresAuth:true, title:'Judge 评估'}` | `AdminJudgeView.vue` |
| `/admin/workflow` | `admin-workflow` | `{requiresAuth:true, title:'Workflow 管理'}` | `AdminWorkflowView.vue` |

### 4.2 菜单（`DefaultLayout.vue`）

- 现有 `disabled: true` 的 4 项（知识入库 / 评估看板 / Trace 追踪 / 用户中心）保留为 disabled。
- 新增「管理中心」分组，**默认展开**，包含 5 项菜单：
  - 平台统计 → `/admin/stats`
  - 任务管理 → `/admin/tasks`
  - Prompt 管理 → `/admin/prompts`
  - Judge 评估 → `/admin/judge`
  - Workflow 管理 → `/admin/workflow`
- **全员可见**，不按 `isAdmin` 隐藏（与 handoff 一致）。

### 4.3 权限

仅 `requiresAuth`（已有守卫），不引入 `isAdmin` 路由守卫。

---

## 5. API 封装与类型

### 5.1 `src/api/client.ts` 补丁

```typescript
export const apiPut = <T>(url: string, data?: object) =>
  http<T>({ method: 'PUT', url, data })
```

### 5.2 `src/types/admin.ts`

```typescript
export interface PlatformOverviewVO {
  totalTasks: number
  successRate: number          // 0.0~1.0
  totalCostCny: number
  avgLatencyMs: number
  p95LatencyMs: number
}

export interface ModelCostVO {
  model: string
  calls: number
  tokensIn: number
  tokensOut: number
  costCny: number
}

export interface AgentCostVO {
  agentRole: string            // PLANNER/RESEARCHER/ANALYST/WRITER/CRITIC
  calls: number
  tokensIn: number
  tokensOut: number
  costCny: number
}

export interface TaskBriefVO {
  id: number
  topic: string
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'
  phase: string | null
  progress: number | null
  startedAt: number | null
  finishedAt: number | null
}

export interface PromptTemplateVO {
  id: number
  name: string
  version: string
  content: string
  model: string | null
  temperature: number | null
  isActive: boolean
  createdAt: number
}

export interface PromptCreateRequest {
  name: string
  version: string
  content: string
  model?: string
  temperature?: number
}

export type PromptUpdateRequest = Partial<
  Pick<PromptTemplateVO, 'content' | 'model' | 'temperature'>
>

export interface JudgeRunVO {
  id: number
  taskId: number
  judgeModel: string
  rubricVersion: string
  overall: number
  structure: number
  factuality: number
  reasoning: number
  citation: number
  clarity: number
  comments: Record<string, string>
  latencyMs: number
  createTime: number
}

// 后端 Jackson @JsonProperty("sub_queries") → 蛇形 key
export interface QueryRewriteVO {
  intent: string | null
  industry: string | null
  year: number | null
  geo: string | null
  sub_queries: string[] | null
}
```

### 5.3 `src/api/admin.ts`

```typescript
import { apiGet, apiPost, apiPut } from './client'
import type {
  PlatformOverviewVO, ModelCostVO, AgentCostVO, TaskBriefVO,
  PromptTemplateVO, PromptCreateRequest, PromptUpdateRequest,
  JudgeRunVO, QueryRewriteVO
} from '@/types/admin'
import type { Page } from '@/types/api'

export const adminApi = {
  // 统计
  overview: () => apiGet<PlatformOverviewVO>('/admin/stats/overview'),
  costByModel: () => apiGet<ModelCostVO[]>('/admin/stats/cost/model'),
  costByAgent: () => apiGet<AgentCostVO[]>('/admin/stats/cost/agent'),
  tasks: (status?: string, page = 1, size = 20) =>
    apiGet<Page<TaskBriefVO>>('/admin/tasks', { status, page, size }),

  // Prompt
  listPrompts: () => apiGet<PromptTemplateVO[]>('/admin/prompts'),
  getPrompt: (id: number) => apiGet<PromptTemplateVO>(`/admin/prompts/${id}`),
  createPrompt: (body: PromptCreateRequest) =>
    apiPost<PromptTemplateVO>('/admin/prompts', body),
  updatePrompt: (id: number, body: PromptUpdateRequest) =>
    apiPut<PromptTemplateVO>(`/admin/prompts/${id}`, body),
  activatePrompt: (id: number) =>
    apiPost<PromptTemplateVO>(`/admin/prompts/${id}/activate`),

  // Judge
  judgeRun: (taskId: number) =>
    apiPost<JudgeRunVO>(`/admin/eval/judge/${taskId}`),
  judgeHistory: (taskId: number) =>
    apiGet<JudgeRunVO[]>(`/admin/eval/judge/${taskId}/history`),
  judgeRecent: (limit = 20) =>
    apiGet<JudgeRunVO[]>('/admin/eval/judge', { limit }),

  // Workflow
  reloadWorkflow: () => apiPost<string>('/admin/workflow/reload'),
  rewriteQuery: (topic: string) =>
    apiPost<QueryRewriteVO>('/admin/query-rewrite', { topic })
}
```

---

## 6. 5 个 Admin 视图详细规范

### 6.1 AdminStatsView（`/admin/stats`）

**布局**：

```
AdminPageHeader「平台统计」
StatCard × 4：总任务数 / 成功率 / 总 Token 成本 / P95 耗时
├ ModelCostBarChart        │ AgentCostDonutChart
├ RecentModelCallsTable    │ SystemHealthPanel
```

**数据流**：
- `onMounted` 并行 `Promise.all([overview, costByModel, costByAgent])`。
- 「最近模型调用」：**复用 `costByModel` 数据**，列：模型 / 调用次数 / 输入 Token / 输出 Token / 总 Token / 成本（元）/ 平均耗时。**「平均耗时」列固定显示 `—`**（后端 `ModelCostVO` 无该字段，见后端 TODO #3）。
- 「系统健康」：
  - 1 条真实「API 服务」：`GET /api/health` 轮询 60s，正常显示「正常」+ 响应耗时。
  - 2 条 mock：「Redis 缓存」「SSE 服务」，常态显示「正常」。来自 `mock/admin-placeholders.ts`，打 TODO。
- StatCard **不显示「较昨日 ±x%」同比行**（后端无此字段，见后端 TODO #2）。

**格式**：
- 成功率 `(x*100).toFixed(1)%`
- 总成本 `¥{x.toFixed(2)}`
- 耗时 `{x} ms`
- 任务数 千分位

**ECharts**：BarChart x 轴模型名，柱色品牌主色；Donut 颜色 5 片对应 5 个 Agent 角色，复用现有 `components/chart/DonutChart.vue` 风格。

**状态**：`loading / data / error`；error 时顶部 `el-alert` + 重试按钮，不阻塞已加载部分。

---

### 6.2 AdminTaskListView（`/admin/tasks`）

**布局**：

```
AdminPageHeader「任务管理」
[状态 Tab：全部 PENDING RUNNING DONE FAILED]   [刷新] [+ 新建报告任务]
el-table：ID / 主题 / 状态 / 当前阶段 / 进度 / 开始时间 / 完成耗时 / 操作
el-pagination
```

**数据流**：
- query 参数：`status?`（空=全部）/ `page` / `size=20`。
- 切 Tab → `page` 重置 1 → 重新请求。
- 「+ 新建报告任务」→ `router.push('/report/submit')`。
- 操作列「查看详情」「Trace」均跳 `/report/:id`（详情页已有 Trace 表）。

**状态颜色映射**（`utils/taskStatus.ts`）：

```typescript
export const STATUS_TAG_TYPE: Record<string, string> = {
  PENDING: 'info', RUNNING: 'warning', DONE: 'success', FAILED: 'danger'
}
export const STATUS_LABEL: Record<string, string> = {
  PENDING: 'PENDING', RUNNING: 'RUNNING', DONE: 'DONE', FAILED: 'FAILED'
}
```

**进度条**：`el-progress :percentage="row.progress ?? 0"`，FAILED 时 `status="exception"`，DONE 时 `status="success"`。

**完成耗时**：`startedAt && finishedAt` 同时存在 → `formatDuration(finished - started)` → `mm:ss`；否则 `—`。

---

### 6.3 AdminPromptView（`/admin/prompts`）

**布局**：左右两栏。

```
左 320px：搜索框（本地过滤 name）+ 按 name 分组的 collapse 列表（v1/v2/...，生效项绿 tag，其余灰 tag）
右 flex:1：
  AdminPageHeader 行内 [设为生效] [编辑] [+ 新建版本]
  元信息 grid 2×3：name / version / model / temperature / 状态 / 创建时间
  Prompt 内容：textarea rows=20 resize=vertical
  底部：提示横幅 + [取消] [保存]
```

**状态机**：
- 模式：`'view' | 'edit' | 'create'`
- `view`：所有字段只读，顶部 3 个按钮可用
- `edit`：content/model/temperature 可编辑；底部「取消/保存」出现；不可切换选中的版本（防丢失草稿）
- `create`：弹 `el-dialog`，要求 `name`/`version`/`content`，可选 `model`/`temperature`；保存后调 `createPrompt` → 列表刷新 → 选中新建项

**关键交互**：
- 「设为生效版本」→ `ElMessageBox.confirm`「将该版本设为生效，下次 Agent 调用立即使用，是否继续？」→ `activatePrompt(id)` → `listPrompts()` → toast「已切换」。
- 「保存」→ `updatePrompt(id, dirty)` → 选中项原地替换 → 切回 `view` 模式 → toast「已保存（缓存已清）」。

**搜索**：本地 `filter` `name`，模糊匹配；匹配中的分组保持展开。

---

### 6.4 AdminJudgeView（`/admin/judge`）

**布局**：

```
AdminPageHeader「LLM-as-Judge 评分」
触发卡：任务 ID input + 「裁判模型: qwen-max」只读 Badge + [开始评分]   ⓘ 评分约 5-15s
最近评分记录 el-table：任务ID / 总分 / 结构 / 事实 / 推理 / 引用 / 清晰度 / 耗时 / 时间 / 操作[详情][再次评分]
el-pagination（前端切页，size=10）

点「详情」→ JudgeDetailDrawer 抽屉：
  评分 #{id}
  总分 + 等级徽章（≥9 优秀 / 7~9 良好 / 5~7 待提升 / <5 不合格）
  ECharts Radar 五维：结构/事实/推理/引用/清晰度（max=10）
  元信息：rubricVersion / judgeModel / latencyMs / createTime
  评论区：5 维评语卡片（来源 comments[dim]）
  [对该任务再次评分（5-15s）]
```

**「裁判模型」处理**：固定只读 Badge `qwen-max`（设计图原下拉去除，后端不支持参数，见后端 TODO #7）。

**「研究主题」列**：`JudgeRunVO` 无 `topic` 字段。**列表只显示 `Task #{taskId}`**，不显示主题文本（见后端 TODO #6）。

**「再次评分」**：按钮 loading 期间禁用，文案改「评分中…」，完成后刷新 `judgeRecent` 并更新 drawer 当前数据。

**列表每行 5 个维度**：纯数字 + 颜色（≥9 绿 / 7~9 蓝 / 5~7 黄 / <5 红），**不渲染迷你弧形**（避免一行 5 个 ECharts 实例）。

---

### 6.5 AdminWorkflowView（`/admin/workflow`）

**布局**：左 2 列 + 右 1 列。

```
左：
  当前活跃 Workflow（mock 数据 + TODO）：name=multi_agent_v1, version=v2,
    file=classpath:workflow/multi_agent_v1.yaml, 节点链, 最近加载时间, 缓存状态
  WorkflowGraph：Planner→Researcher→Analyst→Writer→Critic 横向 5 节点
  提示横幅：「修改 workflow/*.yaml 后点击下方按钮清空缓存，无需重启服务」
  [清空 Workflow 缓存并热更新]  [查看完整日志]（scrollIntoView 到下方时间轴）
右：
  热更新结果面板：上次操作 / 结果 / 耗时 / 完成时间（本会话状态，不持久化）
  QueryRewritePanel：textarea(maxlength=500, minlength=2) + [测试改写] + 结果区
底部：最近加载日志（mock 时间轴 4 条）
```

**「当前活跃 Workflow」**：完全 mock，来自 `mock/admin-placeholders.ts`，打 TODO（见后端 TODO #4）。

**「清空缓存热更新」**：调 `reloadWorkflow()`，前后用 `performance.now()` 测耗时；成功后「热更新结果」面板更新本地 ref；toast「Workflow 缓存已清空，下次任务将使用最新 YAML」。

**「最近加载日志」**：纯 mock，4 条静态条目；标 TODO（见后端 TODO #5）。

**QueryRewritePanel**：
- textarea `maxlength=500 minlength=2 placeholder="输入研究主题"`，禁止空白提交。
- 点「测试改写」→ `rewriteQuery(topic)` → 结果区渲染：
  - `intent`：主题色 `el-tag`
  - `industry / year / geo`：灰色 `el-tag`（null 显示「—」）
  - `sub_queries`：列表卡片，每条一行
- loading 状态：按钮 `:loading="true"`。
- 出错时（业务码失败已被拦截器 toast）按钮恢复，结果区不动。

---

## 7. HomeView 改造

**当前**：`src/views/HomeView.vue` + `src/mock/dashboard.ts` 静态 mock。

**改造**：

```typescript
onMounted → Promise.all([
  adminApi.overview(),                // → 4 个 StatCard
  adminApi.tasks(undefined, 1, 5)     // → 最近 5 条活动
])
```

| HomeView StatCard | 字段 | 格式 |
|---|---|---|
| 总任务数 | `overview.totalTasks` | 千分位 |
| 成功率 | `overview.successRate` | `(x*100).toFixed(1)%` |
| 总 Token 成本 | `overview.totalCostCny` | `¥{x.toFixed(2)}` |
| P95 耗时 | `overview.p95LatencyMs` | `{x} ms` |

「最近活动」列表：由 `Page<TaskBriefVO>.records` 映射，行内容 = 主标题 `topic` / 副标题 `#{id} · {status tag} · {phase ?? '—'}` / 右侧 `formatTs(startedAt)`；点击行 → `/report/:id`。

**清理**：删除 `src/mock/dashboard.ts`。

---

## 8. 共享 utils 与样式

### 8.1 `utils/format.ts` 追加

```typescript
export const formatCny = (x: number) => `¥${x.toFixed(2)}`
export const formatNumber = (x: number) => x.toLocaleString('zh-CN')
export const formatDuration = (ms: number) => {
  const sec = Math.round(ms / 1000)
  const m = String(Math.floor(sec / 60)).padStart(2, '0')
  const s = String(sec % 60).padStart(2, '0')
  return `${m}:${s}`
}
```

### 8.2 样式

- 全部走 `styles/variables.css` 的 CSS token，不硬编码颜色。
- `AdminPageHeader.vue` props：`title: string`、`subtitle?: string`。统一字号 / 行高 / 留白。
- ECharts 实例通过 `utils/echarts.ts` 按需引入；图表组件统一 props 数据 + 监听 resize。

### 8.3 加载 / 错误 / 空状态

| 场景 | 策略 |
|---|---|
| 全页加载 | 外层 card `v-loading="loading"` |
| 局部按钮 | `:loading="x"` |
| 错误 | 顶部 `el-alert type="error"` + 重试按钮 |
| 空数据 | `el-empty`，文案「暂无数据」 |

---

## 9. Mock 数据集中管理

`src/mock/admin-placeholders.ts` 集中以下 3 处 mock 数据：

| 数据 | 用途 | 关联后端 TODO |
|---|---|---|
| `mockSystemHealthExtras` | Stats「系统健康」Redis/SSE 两行 | TODO #1 |
| `mockActiveWorkflow` | Workflow「当前活跃」文本块 | TODO #4 |
| `mockWorkflowLoadLog` | Workflow「加载日志」4 条时间轴 | TODO #5 |

每处引用打：

```typescript
// TODO(backend-api-gap): 见 docs/superpowers/handoff/2026-05-23-backend-api-gaps.md #N
```

---

## 10. 后端接口缺口清单

输出到 `docs/superpowers/handoff/2026-05-23-backend-api-gaps.md`。

| # | 缺口 | 影响页面 | 建议接口 | 优先级 |
|---|---|---|---|---|
| 1 | 系统健康子服务粒度 | AdminStatsView | `GET /api/health/components` → `[{name, status, latencyMs}]` | P3 |
| 2 | StatCard 同比指标 | HomeView + AdminStatsView | `PlatformOverviewVO` 增加 `*Delta` 字段，或 `/admin/stats/overview/compare?range=1d` | P3 |
| 3 | 最近模型调用平均耗时 | AdminStatsView | `ModelCostVO` 增加 `avgLatencyMs` | P2 |
| 4 | Workflow 当前活跃元信息 | AdminWorkflowView | `GET /admin/workflow/active` → `{name, version, file, nodes[], lastLoadedAt, cached}` | P2 |
| 5 | Workflow 加载历史 | AdminWorkflowView | `GET /admin/workflow/history?limit=20` → `[{eventType, message, ts}]` | P3 |
| 6 | Judge 评分关联主题 | AdminJudgeView | `JudgeRunVO` 增加 `topic`；或前端通过 `/admin/tasks` 反查 | P2 |
| 7 | Judge 评分裁判模型可选 | AdminJudgeView | `POST /admin/eval/judge/{taskId}?judgeModel=...` | P3 |

文档结构：背景 + 表格 + 每项「前端当前实现」+「建议后端方案」+ 「关联文件」。

---

## 11. 验证策略

不引入测试框架。每批 commit 前手动跑：

```bash
cd frontend
npm run build       # 走 vue-tsc，类型必须过
npm run lint        # ESLint
npm run dev         # 浏览器验证
```

### 11.1 P0 验证清单

- [ ] 登录后访问 `/admin/stats`、`/admin/tasks` 不 404
- [ ] HomeView 4 个 StatCard 数据来自真实接口（Network 看 `/api/admin/stats/overview`）
- [ ] HomeView 最近活动来自 `/api/admin/tasks?page=1&size=5`
- [ ] `src/mock/dashboard.ts` 已删除
- [ ] AdminStats 2 个图表正确渲染，窗口 resize 跟随
- [ ] AdminTaskList 状态 Tab 切换 / 分页正常，点详情跳转 `/report/:id`
- [ ] 控制台无 error、无 TS 错误

### 11.2 P1 验证清单

- [ ] Prompt 页左右两栏正常加载，搜索本地过滤生效
- [ ] 切换生效版本：二次确认 → 成功提示 → 列表刷新 → 生效 tag 变化
- [ ] 新建 / 编辑 Prompt 能保存并立即反映
- [ ] Judge：输入有效 taskId 触发评分（带 loading）→ 列表多一行 → drawer 雷达图渲染
- [ ] Judge 列表 5 维分数颜色映射正确

### 11.3 P2 验证清单

- [ ] Workflow 页布局完整，节点图 5 个节点
- [ ] 「清空缓存热更新」按钮成功调用，结果面板更新耗时与时间
- [ ] Query Rewrite textarea 输入合法 topic → 提交后展示改写结果（含 `sub_queries`）
- [ ] `docs/superpowers/handoff/2026-05-23-backend-api-gaps.md` 已创建并提交

---

## 12. 不做（YAGNI）

- ❌ admin 路由 isAdmin 守卫（推迟到生产化阶段）
- ❌ 测试框架（vitest/playwright）
- ❌ i18n
- ❌ 通用表格抽象（admin 表格列差异大，每页独立写更清晰）
- ❌ 通用 Drawer 抽象（只有 Judge 用到）
- ❌ ECharts 全局主题定制（沿用现有 BarChart/DonutChart 风格即可）
- ❌ 前端轮询 task 状态（详情页 SSE 已覆盖）
- ❌ Prompt diff 视图（YAGNI，后端也无 diff 接口）

---

## 13. 风险与权衡

| 风险 | 缓解 |
|---|---|
| 设计图中 mock 占位的 UI 可能在真实数据下错位 | mock 数据形态严格贴合后端建议字段，便于接入时低改动 |
| Judge 评分接口耗时 5–15s | 按钮 loading + 文案提示，禁用重复点击 |
| Prompt content 可能很长 | textarea rows=20 + resize=vertical |
| `sub_queries` 返回可能为 null | 渲染前 `?? []` 兜底；空数组显示「无子查询」 |
| `successRate` 后端可能返回 NaN（无任务） | 前端兜底显示 `—%` |
| QueryRewrite 返回结构异常 | 异常字段缺失时显示「—」，不抛错 |

---

## 14. 流程

1. 本 spec 提交 commit：`docs(spec): F3 admin frontend design`
2. 用户审 spec
3. 通过后调 `superpowers:writing-plans` 生成 Task 级实施计划
4. plan 通过后逐 Task 执行，分 P0 / P1 / P2 三批 commit
