# F3 Admin Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 F0–F2 已交付的前端项目基础上落地 F3 阶段：5 个 Admin 中台页面（Stats / Tasks / Prompt / Judge / Workflow）+ HomeView 真实数据接入。

**Architecture:** 增量改造已有 Vue 3 + Element Plus 项目，新增 `views/admin/`、`components/admin/`、`api/admin.ts`、`types/admin.ts`。后端 Controller 全部就绪，缺接口的设计图元素以 mock 占位并集中在 `mock/admin-placeholders.ts`，同时产出后端 TODO 文档。按 P0 → P1 → P2 三批提交，每批一个 commit。

**Tech Stack:** Vue 3.4 + TypeScript 5.5 + Vite 5 + Element Plus 2.7 + Pinia 2.1 + ECharts 5.5 + axios 1.7 + @microsoft/fetch-event-source（已存在）

**Spec:** [docs/superpowers/specs/2026-05-23-frontend-f3-admin-design.md](../specs/2026-05-23-frontend-f3-admin-design.md)

**No tests**：F3 不引入 vitest/playwright。每个文件改完都跑 `npx vue-tsc --noEmit` 验证类型；每批结束跑 `npm run dev` 浏览器手动验证。

**前置条件**：后端 `enterprise-iner-training` 已在 `localhost:8080` 启动（`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`），并以一个有数据的账号登录。

---

## Milestone P0 — 基建 + Stats + Tasks + HomeView 真实数据

完成所有基础设施（types/api/utils/mock）、路由与菜单、`AdminStatsView`、`AdminTaskListView`、HomeView 改造，最后一次性 commit。

---

### Task 1: 新建 `types/admin.ts`

**Files:**
- Create: `frontend/src/types/admin.ts`

- [ ] **Step 1: 写入文件**

```typescript
// frontend/src/types/admin.ts
//
// Admin 中台接口的类型定义，字段与后端 com.leo.enterpriseinertraining.vo.* 严格对齐。
// 后端 Jackson 默认驼峰；但 QueryRewriteVO.sub_queries 有 @JsonProperty 注解，是蛇形。

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
  agentRole: string            // PLANNER / RESEARCHER / ANALYST / WRITER / CRITIC
  calls: number
  tokensIn: number
  tokensOut: number
  costCny: number
}

export type TaskStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

export interface TaskBriefVO {
  id: number
  topic: string
  status: TaskStatus
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

// 后端 @JsonProperty("sub_queries") -> 蛇形 key
export interface QueryRewriteVO {
  intent: string | null
  industry: string | null
  year: number | null
  geo: string | null
  sub_queries: string[] | null
}
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无错误输出（仅打印进度，无 error TS 行）

---

### Task 2: 新建 `types/dashboard.ts`，迁出 StatItem / ActivityItem

**Files:**
- Create: `frontend/src/types/dashboard.ts`
- Modify: `frontend/src/mock/dashboard.ts`（删类型定义，保留数据常量并改 import）
- Modify: `frontend/src/components/stat/StatCard.vue`（改 import 路径）
- Modify: `frontend/src/components/home/RecentActivityList.vue`（如有 import 也改）

- [ ] **Step 1: 创建 `frontend/src/types/dashboard.ts`**

```typescript
// frontend/src/types/dashboard.ts
//
// HomeView 看板的展示类型。原先与 mock 数据混在 mock/dashboard.ts，
// F3 把类型迁到 types 目录，让真实接口数据也能复用。

export type StageStatus = 'done' | 'current' | 'pending'

export interface StageNode {
  index: number
  title: string
  subtitle: string
  status: StageStatus
}

export interface StageDetail {
  index: number
  title: string
  percent: number
  date: string
  statusLabel: string
  status: StageStatus
}

export interface CurrentStageInfo {
  index: number
  title: string
  description: string
  bullets: string[]
}

export interface StatItemDelta {
  raw: string
  percent: string
  trend: 'up' | 'down'
  note: string
}

export interface StatItem {
  key: string
  label: string
  value: string
  unit?: string
  iconName: string
  iconBg: string
  // F3 起改为可选：真实接口（PlatformOverviewVO）暂不提供同比，前端隐藏 delta 行。
  delta?: StatItemDelta
}

export interface CoreCapability {
  key: string
  title: string
  description: string
  iconName: string
  iconBg: string
  link: string
}

export interface ActivityItem {
  id: string
  title: string
  description: string
  time: string
  iconName: string
  iconColor: string
}
```

- [ ] **Step 2: 改 `frontend/src/mock/dashboard.ts`**

把开头的类型定义段（`export type StageStatus` 到 `export interface ActivityItem` 整段，第 10–73 行）整体删除，替换为一行 import；同时把 `statItems`、`recentActivities` 两个 const 整段删除（这两块由真实接口接管）。结果文件如下：

```typescript
// frontend/src/mock/dashboard.ts
//
// Phase F1 首页 dashboard 的项目阶段 / 核心能力 mock 数据。
// F3 起，平台运行时数据（statItems / recentActivities）改由 /api/admin/* 提供，
// 本文件只保留项目阶段叙事内容（项目展示文案，与运行时无关）。

import type {
  CoreCapability,
  CurrentStageInfo,
  StageDetail,
  StageNode
} from '@/types/dashboard'

export const stageNodes: StageNode[] = [
  { index: 0, title: '阶段 0', subtitle: '环境初始化', status: 'done' },
  { index: 1, title: '阶段 1', subtitle: '数据接入', status: 'done' },
  { index: 2, title: '阶段 2', subtitle: '知识检索', status: 'done' },
  { index: 3, title: '阶段 3', subtitle: '报告生成', status: 'current' },
  { index: 4, title: '阶段 4', subtitle: '评估优化', status: 'pending' },
  { index: 5, title: '阶段 5', subtitle: '监控追踪', status: 'pending' },
  { index: 6, title: '阶段 6', subtitle: '系统增强', status: 'pending' }
]

export const stageDetails: StageDetail[] = [
  { index: 0, title: '环境初始化', percent: 100, date: '2024-05-10', statusLabel: '100%', status: 'done' },
  { index: 1, title: '数据接入',   percent: 100, date: '2024-05-11', statusLabel: '100%', status: 'done' },
  { index: 2, title: '知识检索',   percent: 100, date: '2024-05-12', statusLabel: '100%', status: 'done' },
  { index: 3, title: '报告生成',   percent: 60,  date: '',           statusLabel: '进行中', status: 'current' },
  { index: 4, title: '评估优化',   percent: 0,   date: '',           statusLabel: '待开始', status: 'pending' },
  { index: 5, title: '监控追踪',   percent: 0,   date: '',           statusLabel: '待开始', status: 'pending' },
  { index: 6, title: '系统增强',   percent: 0,   date: '',           statusLabel: '待开始', status: 'pending' }
]

export const currentStage: CurrentStageInfo = {
  index: 3,
  title: '阶段 3 - 报告生成',
  description: '基于检索到的知识与上下文，生成高质量行业研究报告，支持多模态内容与结构化输出。',
  bullets: ['报告大纲生成', '内容生成', '格式化输出', '引用与来源标注']
}

export const coreCapabilities: CoreCapability[] = [
  {
    key: 'rag',
    title: 'RAG 检索',
    description: '基于向量检索与语义理解，快速定位相关知识与信息。',
    iconName: 'Search',
    iconBg: '#dbeafe',
    link: '/rag'
  },
  {
    key: 'single-agent',
    title: 'Single-Agent 工作流',
    description: '单智能体驱动研究流程，自动规划与任务执行。',
    iconName: 'Cpu',
    iconBg: '#dcfce7',
    link: ''
  },
  {
    key: 'sse',
    title: 'SSE 流式输出',
    description: '基于 Server-Sent Events，实现流式响应与实时展示。',
    iconName: 'Connection',
    iconBg: '#ede9fe',
    link: ''
  },
  {
    key: 'trace',
    title: 'Trace 可观测',
    description: '全链路追踪可视化，提升系统稳定性与可解释性。',
    iconName: 'DataAnalysis',
    iconBg: '#fef3c7',
    link: ''
  }
]
```

- [ ] **Step 3: 改 `frontend/src/components/stat/StatCard.vue` 的 import**

把第 5 行 `import type { StatItem } from '@/mock/dashboard'` 改为：

```typescript
import type { StatItem } from '@/types/dashboard'
```

- [ ] **Step 4: 检查 `RecentActivityList.vue` 的类型 import**

Run: `cd frontend && grep -nE "from '@/mock/dashboard'" src/components/home/RecentActivityList.vue`

如果输出含 `ActivityItem` 的 import 行，将其中 `from '@/mock/dashboard'` 改为 `from '@/types/dashboard'`。如果没输出（说明它就地定义了），跳过。

- [ ] **Step 5: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 3: 改造 `StatCard.vue` 让 `delta` 可选

**Files:**
- Modify: `frontend/src/components/stat/StatCard.vue`（模板 delta 行加 `v-if`）

- [ ] **Step 1: 替换模板里的 delta div**

把模板里这一段：

```html
      <div class="stat-delta">
        {{ item.delta.note }} <span class="delta-raw">{{ item.delta.raw }}</span>
        <span class="delta-percent" :class="item.delta.trend">({{ item.delta.percent }})</span>
      </div>
```

替换为：

```html
      <div v-if="item.delta" class="stat-delta">
        {{ item.delta.note }} <span class="delta-raw">{{ item.delta.raw }}</span>
        <span class="delta-percent" :class="item.delta.trend">({{ item.delta.percent }})</span>
      </div>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 4: 给 `api/client.ts` 追加 `apiPut`

**Files:**
- Modify: `frontend/src/api/client.ts:88`（在 `apiPost` 之后追加）

- [ ] **Step 1: 在文件末尾 `apiClient` 导出**之前**追加 `apiPut`**

在第 88 行 `export const apiPost = ...` 之后、第 90 行 `export const apiClient = instance` 之前插入：

```typescript
export const apiPut = <T>(url: string, data?: object) =>
  http<T>({ method: 'PUT', url, data })
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 5: 新建 `api/admin.ts`

**Files:**
- Create: `frontend/src/api/admin.ts`

- [ ] **Step 1: 写入文件**

```typescript
// frontend/src/api/admin.ts
//
// Admin 中台接口封装。所有 URL 在 client.ts BASE_URL ('/api') 之下，
// 真实路径形如 '/api/admin/stats/overview'。

import { apiGet, apiPost, apiPut } from './client'
import type {
  PlatformOverviewVO,
  ModelCostVO,
  AgentCostVO,
  TaskBriefVO,
  TaskStatus,
  PromptTemplateVO,
  PromptCreateRequest,
  PromptUpdateRequest,
  JudgeRunVO,
  QueryRewriteVO
} from '@/types/admin'

// MyBatis-Flex 分页结构
export interface Page<T> {
  records: T[]
  totalRow: number
  pageNumber: number
  pageSize: number
  totalPage: number
}

export const adminApi = {
  // ── 平台统计 ──
  overview: () => apiGet<PlatformOverviewVO>('/admin/stats/overview'),
  costByModel: () => apiGet<ModelCostVO[]>('/admin/stats/cost/model'),
  costByAgent: () => apiGet<AgentCostVO[]>('/admin/stats/cost/agent'),
  tasks: (status?: TaskStatus | '', page = 1, size = 20) =>
    apiGet<Page<TaskBriefVO>>('/admin/tasks', {
      status: status || undefined,
      page,
      size
    }),

  // ── Prompt 版本 ──
  listPrompts: () => apiGet<PromptTemplateVO[]>('/admin/prompts'),
  getPrompt: (id: number) => apiGet<PromptTemplateVO>(`/admin/prompts/${id}`),
  createPrompt: (body: PromptCreateRequest) =>
    apiPost<PromptTemplateVO>('/admin/prompts', body),
  updatePrompt: (id: number, body: PromptUpdateRequest) =>
    apiPut<PromptTemplateVO>(`/admin/prompts/${id}`, body),
  activatePrompt: (id: number) =>
    apiPost<PromptTemplateVO>(`/admin/prompts/${id}/activate`),

  // ── LLM-as-Judge ──
  judgeRun: (taskId: number) =>
    apiPost<JudgeRunVO>(`/admin/eval/judge/${taskId}`),
  judgeHistory: (taskId: number) =>
    apiGet<JudgeRunVO[]>(`/admin/eval/judge/${taskId}/history`),
  judgeRecent: (limit = 20) =>
    apiGet<JudgeRunVO[]>('/admin/eval/judge', { limit }),

  // ── Workflow & Query 改写 ──
  reloadWorkflow: () => apiPost<string>('/admin/workflow/reload'),
  rewriteQuery: (topic: string) =>
    apiPost<QueryRewriteVO>('/admin/query-rewrite', { topic })
}
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 6: 给 `utils/format.ts` 追加格式化函数

**Files:**
- Modify: `frontend/src/utils/format.ts`（追加 3 个函数）

- [ ] **Step 1: 在文件末尾追加**

```typescript

/** 千分位格式化（中国本地化）。 */
export function formatNumber(n: number | null | undefined): string {
  if (n == null) return '—'
  return n.toLocaleString('zh-CN')
}

/** 金额格式化（人民币，2 位小数）。 */
export function formatCny(n: number | null | undefined): string {
  if (n == null) return '—'
  return `¥${n.toFixed(2)}`
}

/** 把 ms 时长格式化为 "mm:ss"。负数或 null 返回 '—'。 */
export function formatDurationMs(ms: number | null | undefined): string {
  if (ms == null || ms < 0) return '—'
  const sec = Math.round(ms / 1000)
  const m = String(Math.floor(sec / 60)).padStart(2, '0')
  const s = String(sec % 60).padStart(2, '0')
  return `${m}:${s}`
}

/** 把 0.0~1.0 的成功率格式化为 "xx.x%"。 */
export function formatPercent(ratio: number | null | undefined): string {
  if (ratio == null || Number.isNaN(ratio)) return '—'
  return `${(ratio * 100).toFixed(1)}%`
}
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 7: 新建 `utils/taskStatus.ts`

**Files:**
- Create: `frontend/src/utils/taskStatus.ts`

- [ ] **Step 1: 写入文件**

```typescript
// frontend/src/utils/taskStatus.ts
//
// TaskStatus -> Element Plus el-tag 颜色 / 中文标签 / progress 状态 的映射。

import type { TaskStatus } from '@/types/admin'

export type ElTagType = 'info' | 'success' | 'warning' | 'danger'

export const STATUS_TAG_TYPE: Record<TaskStatus, ElTagType> = {
  PENDING: 'info',
  RUNNING: 'warning',
  DONE: 'success',
  FAILED: 'danger'
}

export const STATUS_LABEL: Record<TaskStatus, string> = {
  PENDING: 'PENDING',
  RUNNING: 'RUNNING',
  DONE: 'DONE',
  FAILED: 'FAILED'
}

/** el-progress 的 status 映射（FAILED 红、DONE 绿、其他默认蓝）。 */
export function progressStatus(s: TaskStatus): 'success' | 'exception' | undefined {
  if (s === 'DONE') return 'success'
  if (s === 'FAILED') return 'exception'
  return undefined
}
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 8: 新建 `mock/admin-placeholders.ts`

**Files:**
- Create: `frontend/src/mock/admin-placeholders.ts`

- [ ] **Step 1: 写入文件**

```typescript
// frontend/src/mock/admin-placeholders.ts
//
// 设计稿里超出后端能力的 UI 元素的 mock 数据，集中在此处便于将来替换。
// 每个常量对应一处后端 TODO，见 docs/superpowers/handoff/2026-05-24-backend-api-gaps.md

export interface HealthRow {
  name: string
  subtitle: string
  status: 'UP' | 'DOWN'
  metric: string                // 例如 '响应时间 1.2 ms'，由前端拼好
  iconName: 'CoffeeCup' | 'Connection' | 'CloudFilled'  // Element Plus 图标名
}

// TODO(backend-api-gap #1): Stats 系统健康面板的子服务条目。
// 真实接口落地后改为 GET /api/health/components 拉取。
export const mockSystemHealthExtras: HealthRow[] = [
  {
    name: 'Redis 缓存',
    subtitle: '缓存与会话存储',
    status: 'UP',
    metric: '响应时间 1.2 ms',
    iconName: 'CoffeeCup'
  },
  {
    name: 'SSE 服务',
    subtitle: '流式推送服务',
    status: 'UP',
    metric: '连接数 12',
    iconName: 'Connection'
  }
]

// TODO(backend-api-gap #4): Workflow 当前活跃元信息。
// 真实接口落地后改为 GET /api/admin/workflow/active 拉取。
export interface ActiveWorkflowInfo {
  name: string
  version: string
  file: string
  nodes: string[]
  lastLoadedAt: string          // 已格式化的字符串
  cached: boolean
}

export const mockActiveWorkflow: ActiveWorkflowInfo = {
  name: 'multi_agent_v1',
  version: 'v2',
  file: 'classpath:workflow/multi_agent_v1.yaml',
  nodes: ['Planner', 'Researcher', 'Analyst', 'Writer', 'Critic'],
  lastLoadedAt: '2026-05-24 09:00:00',
  cached: true
}

// TODO(backend-api-gap #5): Workflow 加载日志时间轴。
// 真实接口落地后改为 GET /api/admin/workflow/history?limit=20 拉取。
export interface WorkflowLogEntry {
  time: string                  // 已格式化 HH:mm:ss
  event: string
  detail: string
  level: 'info' | 'success' | 'warning'
}

export const mockWorkflowLoadLog: WorkflowLogEntry[] = [
  { time: '09:00:00', event: '缓存清空', detail: '已清空 Workflow YAML 缓存，准备重新加载', level: 'info' },
  { time: '09:00:00', event: 'YAML 重新加载', detail: '成功加载 classpath:workflow/multi_agent_v1.yaml (v2)', level: 'success' },
  { time: '09:00:00', event: '节点校验通过', detail: '共 5 个节点，5 条边，依赖完整，拓扑有效', level: 'success' },
  { time: '09:00:00', event: '工作流生效', detail: 'Workflow multi_agent_v1(v2) 已成功生效，等待下次任务使用', level: 'info' }
]
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 9: 新建 `components/admin/AdminPageHeader.vue`

**Files:**
- Create: `frontend/src/components/admin/AdminPageHeader.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <header class="admin-page-header">
    <div class="left">
      <h1 class="title">{{ title }}</h1>
      <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
    </div>
    <div class="right">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.admin-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.left { min-width: 0; }
.title {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 4px;
  color: var(--text-primary);
}
.subtitle {
  font-size: 13px;
  margin: 0;
  color: var(--text-secondary);
}
.right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 10: 新建 `components/admin/ModelCostBarChart.vue`

**Files:**
- Create: `frontend/src/components/admin/ModelCostBarChart.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import BarChart from '@/components/chart/BarChart.vue'
import type { ModelCostVO } from '@/types/admin'

const props = defineProps<{
  data: ModelCostVO[]
  metric: 'cost' | 'tokens'   // 'cost' 显示成本（元）；'tokens' 显示 token 数
}>()

const barData = computed(() =>
  props.data.map((m) => ({
    label: m.model,
    value: props.metric === 'cost' ? m.costCny : m.tokensIn + m.tokensOut
  }))
)
</script>

<template>
  <div class="model-cost-bar">
    <BarChart :data="barData" />
  </div>
</template>

<style scoped>
.model-cost-bar {
  width: 100%;
}
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 11: 新建 `components/admin/AgentCostDonutChart.vue`

**Files:**
- Create: `frontend/src/components/admin/AgentCostDonutChart.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import DonutChart from '@/components/chart/DonutChart.vue'
import type { AgentCostVO } from '@/types/admin'
import { formatCny } from '@/utils/format'

const props = defineProps<{
  data: AgentCostVO[]
}>()

const donutData = computed(() =>
  props.data.map((a) => ({ name: a.agentRole, value: a.costCny }))
)

const totalLabel = computed(() => {
  const total = props.data.reduce((acc, a) => acc + a.costCny, 0)
  return `总成本 ${formatCny(total)}`
})
</script>

<template>
  <DonutChart :data="donutData" :center-label="totalLabel" />
</template>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 12: 新建 `components/admin/RecentModelCallsTable.vue`

**Files:**
- Create: `frontend/src/components/admin/RecentModelCallsTable.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import type { ModelCostVO } from '@/types/admin'
import { formatCny, formatNumber } from '@/utils/format'

defineProps<{
  data: ModelCostVO[]
}>()
</script>

<template>
  <div class="card">
    <header class="card-header">
      <h3>最近模型调用</h3>
      <a class="link" href="#">查看全部模型调用 ›</a>
    </header>
    <el-table :data="data" stripe size="small" style="width: 100%">
      <el-table-column prop="model" label="模型" min-width="140" />
      <el-table-column label="调用次数" width="100" align="right">
        <template #default="{ row }">{{ formatNumber(row.calls) }}</template>
      </el-table-column>
      <el-table-column label="输入 Token" width="120" align="right">
        <template #default="{ row }">{{ formatNumber(row.tokensIn) }}</template>
      </el-table-column>
      <el-table-column label="输出 Token" width="120" align="right">
        <template #default="{ row }">{{ formatNumber(row.tokensOut) }}</template>
      </el-table-column>
      <el-table-column label="总 Token" width="120" align="right">
        <template #default="{ row }">{{ formatNumber(row.tokensIn + row.tokensOut) }}</template>
      </el-table-column>
      <el-table-column label="成本（元）" width="110" align="right">
        <template #default="{ row }">{{ formatCny(row.costCny) }}</template>
      </el-table-column>
      <el-table-column label="平均耗时" width="100" align="right">
        <!-- TODO(backend-api-gap #3): ModelCostVO 暂无 avgLatencyMs -->
        <template #default>—</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.card-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.link {
  font-size: 12px;
  color: var(--color-primary);
}
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 13: 新建 `components/admin/SystemHealthPanel.vue`

**Files:**
- Create: `frontend/src/components/admin/SystemHealthPanel.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { CloudFilled, CoffeeCup, Connection } from '@element-plus/icons-vue'
import { apiGet } from '@/api/client'
import { mockSystemHealthExtras } from '@/mock/admin-placeholders'

interface HealthApiRow {
  status: string
  time: string
}

const apiLatencyMs = ref<number | null>(null)
const apiStatus = ref<'UP' | 'DOWN'>('UP')

const iconMap = {
  CoffeeCup,
  Connection,
  CloudFilled
} as const

async function pingApi() {
  const start = performance.now()
  try {
    await apiGet<HealthApiRow>('/health')
    apiLatencyMs.value = Math.round(performance.now() - start)
    apiStatus.value = 'UP'
  } catch {
    apiLatencyMs.value = null
    apiStatus.value = 'DOWN'
  }
}

let timer: number | null = null
onMounted(() => {
  pingApi()
  timer = window.setInterval(pingApi, 60_000)
})
onBeforeUnmount(() => {
  if (timer !== null) window.clearInterval(timer)
})
</script>

<template>
  <div class="card">
    <header class="card-header">
      <h3>系统健康</h3>
    </header>

    <div class="row">
      <div class="row-icon api">
        <el-icon :size="20"><CloudFilled /></el-icon>
      </div>
      <div class="row-body">
        <div class="row-name">API 服务</div>
        <div class="row-sub">后端接口服务</div>
      </div>
      <div class="row-status">
        <el-tag :type="apiStatus === 'UP' ? 'success' : 'danger'" size="small">
          {{ apiStatus === 'UP' ? '正常' : '异常' }}
        </el-tag>
        <div class="row-metric">
          响应时间 {{ apiLatencyMs == null ? '—' : `${apiLatencyMs} ms` }}
        </div>
      </div>
    </div>

    <!-- TODO(backend-api-gap #1): 以下两条为 mock 子服务，待 /api/health/components 上线后接入 -->
    <div v-for="row in mockSystemHealthExtras" :key="row.name" class="row">
      <div class="row-icon" :class="row.iconName">
        <el-icon :size="20"><component :is="iconMap[row.iconName]" /></el-icon>
      </div>
      <div class="row-body">
        <div class="row-name">{{ row.name }}</div>
        <div class="row-sub">{{ row.subtitle }}</div>
      </div>
      <div class="row-status">
        <el-tag :type="row.status === 'UP' ? 'success' : 'danger'" size="small">
          {{ row.status === 'UP' ? '正常' : '异常' }}
        </el-tag>
        <div class="row-metric">{{ row.metric }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}
.card-header {
  margin-bottom: 12px;
}
.card-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid var(--border-light, #eef2f7);
}
.row:first-of-type { border-top: none; }
.row-icon {
  width: 36px; height: 36px;
  border-radius: 8px;
  display: grid; place-items: center;
  background: #e0f2fe;
  color: #0284c7;
}
.row-icon.CoffeeCup { background: #fee2e2; color: #dc2626; }
.row-icon.Connection { background: #ede9fe; color: #7c3aed; }
.row-body { flex: 1; min-width: 0; }
.row-name { font-size: 13px; font-weight: 500; color: var(--text-primary); }
.row-sub { font-size: 12px; color: var(--text-tertiary); }
.row-status { text-align: right; }
.row-metric { font-size: 12px; color: var(--text-tertiary); margin-top: 4px; }
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 14: 新建 `views/admin/AdminStatsView.vue`

**Files:**
- Create: `frontend/src/views/admin/AdminStatsView.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Document, Histogram, Money, Timer } from '@element-plus/icons-vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import StatCard from '@/components/stat/StatCard.vue'
import ModelCostBarChart from '@/components/admin/ModelCostBarChart.vue'
import AgentCostDonutChart from '@/components/admin/AgentCostDonutChart.vue'
import RecentModelCallsTable from '@/components/admin/RecentModelCallsTable.vue'
import SystemHealthPanel from '@/components/admin/SystemHealthPanel.vue'
import { adminApi } from '@/api/admin'
import { formatCny, formatNumber, formatPercent } from '@/utils/format'
import type { StatItem } from '@/types/dashboard'
import type {
  AgentCostVO,
  ModelCostVO,
  PlatformOverviewVO
} from '@/types/admin'

const loading = ref(false)
const errorMsg = ref('')
const overview = ref<PlatformOverviewVO | null>(null)
const modelCost = ref<ModelCostVO[]>([])
const agentCost = ref<AgentCostVO[]>([])
const metric = ref<'cost' | 'tokens'>('cost')

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const [o, m, a] = await Promise.all([
      adminApi.overview(),
      adminApi.costByModel(),
      adminApi.costByAgent()
    ])
    overview.value = o
    modelCost.value = m
    agentCost.value = a
  } catch (e) {
    errorMsg.value = (e as Error)?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

const statItems = computed<StatItem[]>(() => {
  const o = overview.value
  if (!o) return []
  return [
    {
      key: 'total',
      label: '总任务数',
      value: formatNumber(o.totalTasks),
      iconName: 'Document',
      iconBg: '#dbeafe'
    },
    {
      key: 'success',
      label: '成功率',
      value: formatPercent(o.successRate),
      iconName: 'Histogram',
      iconBg: '#dcfce7'
    },
    {
      key: 'cost',
      label: '总 Token 成本',
      value: formatCny(o.totalCostCny),
      iconName: 'Money',
      iconBg: '#fef3c7'
    },
    {
      key: 'p95',
      label: 'P95 耗时',
      value: formatNumber(o.p95LatencyMs),
      unit: 'ms',
      iconName: 'Timer',
      iconBg: '#ede9fe'
    }
  ]
})

// 显式引用以让 vue-tsc 不把图标当未使用导入
void Document; void Histogram; void Money; void Timer
</script>

<template>
  <div v-loading="loading" class="admin-stats">
    <AdminPageHeader
      title="平台统计"
      subtitle="平台任务、Token 成本与端到端耗时总览。"
    />

    <el-alert
      v-if="errorMsg"
      :title="errorMsg"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    >
      <template #default>
        <span>{{ errorMsg }}</span>
        <el-button type="primary" link size="small" @click="load">重试</el-button>
      </template>
    </el-alert>

    <section class="stats-row">
      <StatCard v-for="s in statItems" :key="s.key" :item="s" />
    </section>

    <section class="charts-row">
      <div class="card">
        <header class="card-header">
          <h3>按模型 Token 成本</h3>
          <el-radio-group v-model="metric" size="small">
            <el-radio-button value="cost">成本（元）</el-radio-button>
            <el-radio-button value="tokens">Token 数</el-radio-button>
          </el-radio-group>
        </header>
        <ModelCostBarChart :data="modelCost" :metric="metric" />
      </div>

      <div class="card">
        <header class="card-header">
          <h3>Agent 成本占比</h3>
        </header>
        <AgentCostDonutChart :data="agentCost" />
      </div>
    </section>

    <section class="bottom-row">
      <RecentModelCallsTable :data="modelCost" />
      <SystemHealthPanel />
    </section>
  </div>
</template>

<style scoped>
.admin-stats { display: flex; flex-direction: column; gap: 16px; }
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.charts-row, .bottom-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.card-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
@media (max-width: 1280px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .charts-row, .bottom-row { grid-template-columns: 1fr; }
}
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 15: 新建 `views/admin/AdminTaskListView.vue`

**Files:**
- Create: `frontend/src/views/admin/AdminTaskListView.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh } from '@element-plus/icons-vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { adminApi } from '@/api/admin'
import type { Page } from '@/api/admin'
import type { TaskBriefVO, TaskStatus } from '@/types/admin'
import {
  STATUS_LABEL,
  STATUS_TAG_TYPE,
  progressStatus
} from '@/utils/taskStatus'
import { formatDurationMs, formatEpochMillis } from '@/utils/format'

const router = useRouter()

type FilterValue = '' | TaskStatus
const filter = ref<FilterValue>('')
const page = ref(1)
const size = 20
const loading = ref(false)
const result = ref<Page<TaskBriefVO> | null>(null)

async function load() {
  loading.value = true
  try {
    result.value = await adminApi.tasks(filter.value, page.value, size)
  } finally {
    loading.value = false
  }
}

watch(filter, () => {
  page.value = 1
  load()
})

function onPageChange(p: number) {
  page.value = p
  load()
}

function goSubmit() {
  router.push('/report/submit')
}

function goDetail(id: number) {
  router.push(`/report/${id}`)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="admin-tasks">
    <AdminPageHeader
      title="任务管理"
      subtitle="查看 Multi-Agent 研报任务状态、阶段进度与执行耗时。"
    />

    <div class="toolbar">
      <el-radio-group v-model="filter" size="default">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="PENDING">PENDING</el-radio-button>
        <el-radio-button value="RUNNING">RUNNING</el-radio-button>
        <el-radio-button value="DONE">DONE</el-radio-button>
        <el-radio-button value="FAILED">FAILED</el-radio-button>
      </el-radio-group>

      <div class="toolbar-right">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="goSubmit">新建报告任务</el-button>
      </div>
    </div>

    <el-table :data="result?.records ?? []" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="topic" label="研究主题" min-width="240" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="STATUS_TAG_TYPE[(row as TaskBriefVO).status]" size="small">
            {{ STATUS_LABEL[(row as TaskBriefVO).status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前阶段" width="140">
        <template #default="{ row }">
          {{ (row as TaskBriefVO).phase ?? '—' }}
        </template>
      </el-table-column>
      <el-table-column label="进度" width="180">
        <template #default="{ row }">
          <el-progress
            :percentage="(row as TaskBriefVO).progress ?? 0"
            :status="progressStatus((row as TaskBriefVO).status)"
            :stroke-width="10"
          />
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="170">
        <template #default="{ row }">
          {{ formatEpochMillis((row as TaskBriefVO).startedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="完成耗时" width="110">
        <template #default="{ row }">
          <template v-if="(row as TaskBriefVO).startedAt != null && (row as TaskBriefVO).finishedAt != null">
            {{ formatDurationMs(((row as TaskBriefVO).finishedAt as number) - ((row as TaskBriefVO).startedAt as number)) }}
          </template>
          <template v-else>—</template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="goDetail((row as TaskBriefVO).id)">查看详情</el-button>
          <el-button type="primary" link size="small" @click="goDetail((row as TaskBriefVO).id)">Trace</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-row">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :current-page="page"
        :page-size="size"
        :total="result?.totalRow ?? 0"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.admin-tasks {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.toolbar-right { display: flex; gap: 8px; }
.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 16: HomeView 切换到真实接口数据

**Files:**
- Modify: `frontend/src/views/HomeView.vue`（替换 `statItems` / `recentActivities` 的数据来源）

- [ ] **Step 1: 替换 `<script setup>` 段**

把现有 `<script setup lang="ts">…</script>` 整段替换为：

```typescript
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import StatCard from '@/components/stat/StatCard.vue'
import StageStepBar from '@/components/stage/StageStepBar.vue'
import StageOverview from '@/components/stage/StageOverview.vue'
import QuickStartCard from '@/components/home/QuickStartCard.vue'
import CoreCapabilityCard from '@/components/home/CoreCapabilityCard.vue'
import RecentActivityList from '@/components/home/RecentActivityList.vue'
import { adminApi } from '@/api/admin'
import {
  coreCapabilities,
  currentStage,
  stageDetails,
  stageNodes
} from '@/mock/dashboard'
import type { ActivityItem, StatItem } from '@/types/dashboard'
import type { PlatformOverviewVO, TaskBriefVO } from '@/types/admin'
import {
  formatCny,
  formatEpochMillis,
  formatNumber,
  formatPercent
} from '@/utils/format'
import { STATUS_LABEL } from '@/utils/taskStatus'

const auth = useAuthStore()

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const displayName = computed(
  () => auth.user?.nickname || auth.user?.username || '访客'
)

const overview = ref<PlatformOverviewVO | null>(null)
const recentTasks = ref<TaskBriefVO[]>([])

onMounted(async () => {
  try {
    const [o, page] = await Promise.all([
      adminApi.overview(),
      adminApi.tasks('', 1, 5)
    ])
    overview.value = o
    recentTasks.value = page.records
  } catch {
    /* 业务错误已由拦截器 toast，此处保留兜底空数据 */
  }
})

const statItems = computed<StatItem[]>(() => {
  const o = overview.value
  if (!o) return []
  return [
    { key: 'total',   label: '总任务数',      value: formatNumber(o.totalTasks),       iconName: 'Document',  iconBg: '#dbeafe' },
    { key: 'success', label: '成功率',        value: formatPercent(o.successRate),     iconName: 'Histogram', iconBg: '#dcfce7' },
    { key: 'cost',    label: '总 Token 成本', value: formatCny(o.totalCostCny),        iconName: 'Money',     iconBg: '#fef3c7' },
    { key: 'p95',     label: 'P95 耗时',      value: formatNumber(o.p95LatencyMs),     unit: 'ms', iconName: 'Timer', iconBg: '#ede9fe' }
  ]
})

const recentActivities = computed<ActivityItem[]>(() =>
  recentTasks.value.map((t) => ({
    id: String(t.id),
    title: t.topic,
    description: `#${t.id} · ${STATUS_LABEL[t.status]} · ${t.phase ?? '—'}`,
    time: formatEpochMillis(t.startedAt),
    iconName: 'VideoPlay',
    iconColor: '#3b82f6'
  }))
)
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

注意：`<template>` 段不需要改（`v-for="s in statItems"`、`:activities="recentActivities"` 命名都没变）。`StageStepBar` / `StageOverview` 仍用现有的 mock。

---

### Task 17: 在 `router/index.ts` 追加 5 条 admin 路由

**Files:**
- Modify: `frontend/src/router/index.ts:50`（在 `report-detail` 路由对象之后、`children` 数组结束 `]` 之前追加）

- [ ] **Step 1: 在 children 数组里追加**

把现有 children 数组的最后一项（`report/:id(\\d+)` 那个，第 45–50 行）后面、`]` 之前，追加：

```typescript
      ,
      {
        path: 'admin/stats',
        name: 'admin-stats',
        component: () => import('@/views/admin/AdminStatsView.vue'),
        meta: { requiresAuth: true, title: '平台统计' }
      },
      {
        path: 'admin/tasks',
        name: 'admin-tasks',
        component: () => import('@/views/admin/AdminTaskListView.vue'),
        meta: { requiresAuth: true, title: '任务管理' }
      },
      {
        path: 'admin/prompts',
        name: 'admin-prompts',
        component: () => import('@/views/admin/AdminPromptView.vue'),
        meta: { requiresAuth: true, title: 'Prompt 管理' }
      },
      {
        path: 'admin/judge',
        name: 'admin-judge',
        component: () => import('@/views/admin/AdminJudgeView.vue'),
        meta: { requiresAuth: true, title: 'Judge 评估' }
      },
      {
        path: 'admin/workflow',
        name: 'admin-workflow',
        component: () => import('@/views/admin/AdminWorkflowView.vue'),
        meta: { requiresAuth: true, title: 'Workflow 管理' }
      }
```

注意 `AdminPromptView` / `AdminJudgeView` / `AdminWorkflowView` 此时还没建——P0 只用到前两个。**先按规划把 5 条路由都加上**，文件不存在的页面在 P0 结束时不会被加载（只有点菜单才会跳转），不影响 vue-tsc 类型检查（动态 import 不在类型系统的覆盖范围）。如果担心，请在 P1/P2 创建对应视图前**不要点这三个菜单项**。

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 18: `DefaultLayout.vue` 启用「管理中心」分组菜单

**Files:**
- Modify: `frontend/src/layouts/DefaultLayout.vue`（扩 MenuItem 类型、扩 menus 数组、扩 activeMenu 计算、加分组渲染样式）

- [ ] **Step 1: 修改 `<script setup>` 段**

替换原 `import { ... } from '@element-plus/icons-vue'` 整行为：

```typescript
import {
  HomeFilled,
  Search,
  Document,
  TrendCharts,
  DataAnalysis,
  Connection,
  User,
  Bell,
  ArrowDown,
  PieChart,
  Tickets,
  EditPen,
  DataLine,
  Setting
} from '@element-plus/icons-vue'
```

把原 `interface MenuItem { ... }` 替换为：

```typescript
interface MenuItem {
  key: string
  label: string
  icon: Component
  path?: string
  disabled?: boolean
}

interface MenuGroup {
  key: string
  label: string
  items: MenuItem[]
}
```

把原 `const menus: MenuItem[] = [ ... ]` 改为：

```typescript
const menus: MenuItem[] = [
  { key: 'home', label: '首页', icon: HomeFilled, path: '/' },
  { key: 'rag', label: 'RAG 检索', icon: Search, path: '/rag' },
  { key: 'ingest', label: '知识入库', icon: DataAnalysis, disabled: true },
  { key: 'eval', label: '评估看板', icon: TrendCharts, disabled: true },
  { key: 'report', label: '研究报告', icon: Document, path: '/report/submit' },
  { key: 'trace', label: 'Trace 追踪', icon: Connection, disabled: true },
  { key: 'user', label: '用户中心', icon: User, disabled: true }
]

const adminGroup: MenuGroup = {
  key: 'admin',
  label: '管理中心',
  items: [
    { key: 'admin-stats',    label: '平台统计',    icon: PieChart,     path: '/admin/stats' },
    { key: 'admin-tasks',    label: '任务管理',    icon: Tickets,      path: '/admin/tasks' },
    { key: 'admin-prompts',  label: 'Prompt 管理', icon: EditPen,      path: '/admin/prompts' },
    { key: 'admin-judge',    label: 'Judge 评估',  icon: DataLine,     path: '/admin/judge' },
    { key: 'admin-workflow', label: 'Workflow 管理', icon: Setting,    path: '/admin/workflow' }
  ]
}
```

把原 `activeMenu` 计算属性替换为：

```typescript
const activeMenu = computed(() => {
  const p = route.path
  if (p === '/') return 'home'
  if (p.startsWith('/rag')) return 'rag'
  if (p.startsWith('/report')) return 'report'
  if (p.startsWith('/admin/stats')) return 'admin-stats'
  if (p.startsWith('/admin/tasks')) return 'admin-tasks'
  if (p.startsWith('/admin/prompts')) return 'admin-prompts'
  if (p.startsWith('/admin/judge')) return 'admin-judge'
  if (p.startsWith('/admin/workflow')) return 'admin-workflow'
  return ''
})
```

- [ ] **Step 2: 修改 `<template>` 内的菜单段**

把原来的 `<nav class="sidebar-menu">...</nav>` 整段替换为：

```html
      <nav class="sidebar-menu">
        <div
          v-for="m in menus"
          :key="m.key"
          class="menu-item"
          :class="{ active: activeMenu === m.key, disabled: m.disabled }"
          @click="onMenuClick(m)"
        >
          <el-icon :size="18"><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
        </div>

        <div class="menu-group-label">{{ adminGroup.label }}</div>
        <div
          v-for="m in adminGroup.items"
          :key="m.key"
          class="menu-item"
          :class="{ active: activeMenu === m.key, disabled: m.disabled }"
          @click="onMenuClick(m)"
        >
          <el-icon :size="18"><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
        </div>
      </nav>
```

- [ ] **Step 3: 在 `<style scoped>` 内追加分组标签样式**

在 `.menu-item.disabled { ... }` 这一规则之后追加：

```css
.menu-group-label {
  margin: 12px 12px 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-tertiary);
  letter-spacing: 0.4px;
}
```

- [ ] **Step 4: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 19: P0 浏览器验证 + Commit

**Files:**（无新增改动，仅验证 + 提交）

- [ ] **Step 1: 起后端**

确认后端已在 `localhost:8080` 启动（`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`）。

- [ ] **Step 2: 起前端**

Run: `cd frontend && npm run dev`
Expected: Vite 输出 `Local: http://localhost:5173/`

- [ ] **Step 3: 浏览器验证清单**

打开 `http://localhost:5173`，用一个已注册账号登录，逐项确认：

1. 进入 `/` 首页：4 个 StatCard 数字非 mock，DevTools Network 能看到 `/api/admin/stats/overview` 与 `/api/admin/tasks?page=1&size=5` 两个请求
2. 「最近活动」列表内容来自真实任务（5 条）
3. 侧栏「管理中心」分组出现，5 项菜单可见
4. 点「平台统计」→ 路由跳 `/admin/stats`，加载 4 卡 + 2 图 + 最近模型调用表 + 系统健康面板
5. 切换柱图右上「成本/Token 数」 toggle 正常重绘
6. 点「任务管理」→ 路由跳 `/admin/tasks`，表格加载、状态 Tab 切换、分页可用
7. 点表格「查看详情」跳到 `/report/{id}`
8. 控制台无 error；vue-tsc 整体也无 error（之前已逐 task 跑过）

如有失败：修复后重跑相关 task 的步骤再回到本步骤。

- [ ] **Step 4: 暂存改动并 commit**

Run:

```bash
cd /Users/zhengsmacbook/Desktop/miniProject/claude/enterprise-iner-training
git add frontend/src/types/admin.ts \
        frontend/src/types/dashboard.ts \
        frontend/src/mock/dashboard.ts \
        frontend/src/mock/admin-placeholders.ts \
        frontend/src/api/client.ts \
        frontend/src/api/admin.ts \
        frontend/src/utils/format.ts \
        frontend/src/utils/taskStatus.ts \
        frontend/src/components/stat/StatCard.vue \
        frontend/src/components/home/RecentActivityList.vue \
        frontend/src/components/admin/ \
        frontend/src/views/admin/AdminStatsView.vue \
        frontend/src/views/admin/AdminTaskListView.vue \
        frontend/src/views/HomeView.vue \
        frontend/src/router/index.ts \
        frontend/src/layouts/DefaultLayout.vue
git commit -m "$(cat <<'EOF'
feat(frontend/f3): P0 基建 + AdminStats + AdminTaskList + HomeView 接入真实数据

- 新增 api/admin.ts、types/admin.ts、types/dashboard.ts、utils/taskStatus.ts
- 在 client.ts 增加 apiPut；utils/format.ts 增加 formatCny / formatNumber / formatDurationMs / formatPercent
- 把 StatCard 的 delta 改为可选；HomeView 改为从 /admin/stats/overview + /admin/tasks 拉真实数据
- 新建 AdminStatsView / AdminTaskListView 与依赖组件（图表、最近模型调用表、系统健康面板）
- DefaultLayout 增加「管理中心」分组菜单（5 项），router 追加 5 条 admin/* 路由
- mock/admin-placeholders.ts 集中存放暂时无后端接口的 mock 数据（系统健康子服务、Workflow 元信息与日志），均带 TODO 注释

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

Expected: 一个新 commit `feat(frontend/f3): P0 ...`

---

## Milestone P1 — Prompt 管理 + Judge 评估

新建 `AdminPromptView` 与 `AdminJudgeView`，最后一次性 commit。

---

### Task 20: 新建 `components/admin/PromptGroupList.vue`

**Files:**
- Create: `frontend/src/components/admin/PromptGroupList.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { PromptTemplateVO } from '@/types/admin'

const props = defineProps<{
  items: PromptTemplateVO[]
  selectedId: number | null
}>()

const emit = defineEmits<{
  (e: 'select', id: number): void
}>()

const keyword = ref('')

interface Group {
  name: string
  versions: PromptTemplateVO[]
}

const groups = computed<Group[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  const map = new Map<string, PromptTemplateVO[]>()
  for (const p of props.items) {
    if (kw && !p.name.toLowerCase().includes(kw)) continue
    const arr = map.get(p.name) ?? []
    arr.push(p)
    map.set(p.name, arr)
  }
  return [...map.entries()].map(([name, versions]) => ({
    name,
    versions: [...versions].sort((a, b) => a.version.localeCompare(b.version))
  }))
})
</script>

<template>
  <div class="prompt-list">
    <el-input
      v-model="keyword"
      placeholder="搜索 Prompt 名称"
      :prefix-icon="Search"
      clearable
      size="default"
    />

    <el-collapse :default-active="groups.map((g) => g.name)" class="groups">
      <el-collapse-item
        v-for="g in groups"
        :key="g.name"
        :name="g.name"
        :title="g.name"
      >
        <div
          v-for="v in g.versions"
          :key="v.id"
          class="version-row"
          :class="{ active: v.id === selectedId }"
          @click="emit('select', v.id)"
        >
          <span class="version-label">{{ v.version }}</span>
          <el-tag
            v-if="v.isActive"
            type="success"
            size="small"
            effect="light"
          >生效</el-tag>
          <el-tag v-else type="info" size="small" effect="plain">历史版本</el-tag>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.prompt-list {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.groups {
  flex: 1;
  overflow-y: auto;
  border: none;
}
:deep(.el-collapse-item__header) {
  font-weight: 600;
  font-size: 13px;
  color: var(--text-primary);
}
.version-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
}
.version-row:hover { background: var(--bg-muted); }
.version-row.active {
  background: rgba(47, 109, 245, 0.08);
  color: var(--color-primary);
  font-weight: 500;
}
.version-label { font-family: 'JetBrains Mono', monospace; }
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 21: 新建 `components/admin/PromptDetailPanel.vue`

**Files:**
- Create: `frontend/src/components/admin/PromptDetailPanel.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import { ref, watch } from 'vue'
import { CircleCheck, EditPen, Plus, InfoFilled } from '@element-plus/icons-vue'
import type { PromptTemplateVO, PromptUpdateRequest } from '@/types/admin'
import { formatEpochMillis } from '@/utils/format'

const props = defineProps<{
  detail: PromptTemplateVO | null
  mode: 'view' | 'edit'
}>()

const emit = defineEmits<{
  (e: 'activate'): void
  (e: 'enter-edit'): void
  (e: 'create'): void
  (e: 'cancel-edit'): void
  (e: 'save', body: PromptUpdateRequest): void
}>()

const draftContent = ref('')
const draftModel = ref('')
const draftTemperature = ref<number | null>(null)

watch(
  () => [props.detail, props.mode],
  () => {
    if (props.detail && props.mode === 'edit') {
      draftContent.value = props.detail.content
      draftModel.value = props.detail.model ?? ''
      draftTemperature.value = props.detail.temperature
    }
  },
  { immediate: true }
)

function save() {
  if (!props.detail) return
  const body: PromptUpdateRequest = {}
  if (draftContent.value !== props.detail.content) body.content = draftContent.value
  if ((draftModel.value || null) !== props.detail.model)
    body.model = draftModel.value || undefined
  if (draftTemperature.value !== props.detail.temperature)
    body.temperature = draftTemperature.value ?? undefined
  emit('save', body)
}
</script>

<template>
  <div class="detail-panel">
    <header class="header">
      <h2>版本详情</h2>
      <div class="actions" v-if="detail && mode === 'view'">
        <el-button :icon="CircleCheck" :disabled="detail.isActive" @click="emit('activate')">
          设为生效版本
        </el-button>
        <el-button :icon="EditPen" @click="emit('enter-edit')">编辑内容</el-button>
        <el-button type="primary" :icon="Plus" @click="emit('create')">新建版本</el-button>
      </div>
    </header>

    <el-empty v-if="!detail" description="请选择左侧某个 Prompt 版本" />

    <template v-else>
      <div class="meta">
        <div class="meta-row"><span>名称</span><b>{{ detail.name }}</b></div>
        <div class="meta-row"><span>版本</span><b>{{ detail.version }}</b></div>
        <div class="meta-row">
          <span>模型</span>
          <template v-if="mode === 'edit'">
            <el-input v-model="draftModel" size="small" style="max-width: 200px" />
          </template>
          <b v-else>{{ detail.model ?? '—' }}</b>
        </div>
        <div class="meta-row">
          <span>温度 (temperature)</span>
          <template v-if="mode === 'edit'">
            <el-input-number
              v-model="draftTemperature"
              :step="0.1"
              :min="0"
              :max="2"
              size="small"
              style="width: 140px"
            />
          </template>
          <b v-else>{{ detail.temperature ?? '—' }}</b>
        </div>
        <div class="meta-row">
          <span>状态</span>
          <el-tag :type="detail.isActive ? 'success' : 'warning'" size="small">
            {{ detail.isActive ? '生效' : '未生效' }}
          </el-tag>
        </div>
        <div class="meta-row"><span>创建时间</span><b>{{ formatEpochMillis(detail.createdAt) }}</b></div>
      </div>

      <div class="content-block">
        <h3>Prompt 内容</h3>
        <el-input
          v-if="mode === 'edit'"
          v-model="draftContent"
          type="textarea"
          :rows="20"
          resize="vertical"
        />
        <pre v-else class="readonly">{{ detail.content }}</pre>
      </div>

      <div class="footer">
        <el-alert
          v-if="mode === 'view'"
          type="info"
          :closable="false"
          :icon="InfoFilled"
          show-icon
          title="切换生效后，下次 Agent 调用立即使用，无需重启。"
        />
        <div v-else class="edit-actions">
          <el-button @click="emit('cancel-edit')">取消</el-button>
          <el-button type="primary" @click="save">保存修改</el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-panel {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 600px;
}
.header { display: flex; align-items: center; justify-content: space-between; }
.header h2 { margin: 0; font-size: 16px; }
.actions { display: flex; gap: 8px; }
.meta {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px 24px;
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  padding: 16px;
}
.meta-row { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
.meta-row span { color: var(--text-tertiary); }
.meta-row b { color: var(--text-primary); font-weight: 500; }
.content-block h3 { margin: 0 0 8px; font-size: 14px; }
.readonly {
  background: #0b1020;
  color: #d1d5db;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  line-height: 1.6;
  padding: 16px;
  border-radius: var(--radius-md);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 540px;
  overflow-y: auto;
}
.footer { display: flex; justify-content: flex-end; }
.edit-actions { display: flex; gap: 8px; }
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 22: 新建 `views/admin/AdminPromptView.vue`

**Files:**
- Create: `frontend/src/views/admin/AdminPromptView.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import PromptGroupList from '@/components/admin/PromptGroupList.vue'
import PromptDetailPanel from '@/components/admin/PromptDetailPanel.vue'
import { adminApi } from '@/api/admin'
import type {
  PromptCreateRequest,
  PromptTemplateVO,
  PromptUpdateRequest
} from '@/types/admin'

const loading = ref(false)
const items = ref<PromptTemplateVO[]>([])
const selectedId = ref<number | null>(null)
const detail = ref<PromptTemplateVO | null>(null)
const mode = ref<'view' | 'edit'>('view')

const createDialog = ref(false)
const createForm = ref<PromptCreateRequest>({
  name: '',
  version: 'v1',
  content: '',
  model: '',
  temperature: undefined
})

async function loadList(preselect?: number) {
  loading.value = true
  try {
    items.value = await adminApi.listPrompts()
    const target =
      preselect ?? selectedId.value ?? items.value[0]?.id ?? null
    if (target != null) {
      selectedId.value = target
      detail.value = items.value.find((x) => x.id === target) ?? null
    }
  } finally {
    loading.value = false
  }
}

function onSelect(id: number) {
  if (mode.value === 'edit') {
    ElMessage.warning('请先取消或保存当前编辑')
    return
  }
  selectedId.value = id
  detail.value = items.value.find((x) => x.id === id) ?? null
}

async function activate() {
  if (!detail.value) return
  await ElMessageBox.confirm(
    '将该版本设为生效，下次 Agent 调用立即使用，是否继续？',
    '切换生效版本',
    { type: 'warning' }
  )
  await adminApi.activatePrompt(detail.value.id)
  ElMessage.success('已切换，下次 Agent 调用立即生效，无需重启')
  await loadList(detail.value.id)
}

async function save(body: PromptUpdateRequest) {
  if (!detail.value) return
  const updated = await adminApi.updatePrompt(detail.value.id, body)
  ElMessage.success('已保存（PromptLoader 缓存已清）')
  detail.value = updated
  // 更新列表里同 id 的项
  const idx = items.value.findIndex((x) => x.id === updated.id)
  if (idx >= 0) items.value[idx] = updated
  mode.value = 'view'
}

async function submitCreate() {
  if (!createForm.value.name || !createForm.value.version || !createForm.value.content) {
    ElMessage.warning('name / version / content 必填')
    return
  }
  const body: PromptCreateRequest = {
    name: createForm.value.name,
    version: createForm.value.version,
    content: createForm.value.content,
    model: createForm.value.model || undefined,
    temperature: createForm.value.temperature
  }
  const created = await adminApi.createPrompt(body)
  createDialog.value = false
  createForm.value = { name: '', version: 'v1', content: '', model: '', temperature: undefined }
  await loadList(created.id)
  ElMessage.success('已创建新版本（未生效）')
}

onMounted(() => loadList())
</script>

<template>
  <div v-loading="loading" class="admin-prompt">
    <AdminPageHeader
      title="Prompt 管理"
      subtitle="管理 Planner / Researcher / Analyst / Writer / Critic 的 Prompt 版本与灰度生效状态。"
    />

    <div class="layout">
      <PromptGroupList
        :items="items"
        :selected-id="selectedId"
        @select="onSelect"
      />
      <PromptDetailPanel
        :detail="detail"
        :mode="mode"
        @activate="activate"
        @enter-edit="mode = 'edit'"
        @create="createDialog = true"
        @cancel-edit="mode = 'view'"
        @save="save"
      />
    </div>

    <el-dialog v-model="createDialog" title="新建 Prompt 版本" width="560">
      <el-form label-width="100" label-position="right">
        <el-form-item label="name" required>
          <el-input v-model="createForm.name" placeholder="如 planner_prompt" />
        </el-form-item>
        <el-form-item label="version" required>
          <el-input v-model="createForm.version" placeholder="如 v3" />
        </el-form-item>
        <el-form-item label="model">
          <el-input v-model="createForm.model" placeholder="如 qwen-max（可空）" />
        </el-form-item>
        <el-form-item label="temperature">
          <el-input-number v-model="createForm.temperature" :step="0.1" :min="0" :max="2" />
        </el-form-item>
        <el-form-item label="content" required>
          <el-input v-model="createForm.content" type="textarea" :rows="10" resize="vertical" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-prompt { display: flex; flex-direction: column; gap: 16px; }
.layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  align-items: stretch;
}
@media (max-width: 1280px) {
  .layout { grid-template-columns: 1fr; }
}
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 23: 新建 `components/admin/JudgeScoreRadar.vue`

**Files:**
- Create: `frontend/src/components/admin/JudgeScoreRadar.vue`

- [ ] **Step 1: 在 `utils/echarts.ts` 注册 Radar 模块**

打开 `frontend/src/utils/echarts.ts`，把：

```typescript
import { BarChart, PieChart } from 'echarts/charts'
```

改为：

```typescript
import { BarChart, PieChart, RadarChart } from 'echarts/charts'
```

把：

```typescript
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components'
```

改为：

```typescript
import {
  GridComponent,
  LegendComponent,
  RadarComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components'
```

并把 `use([...])` 的数组追加 `RadarChart, RadarComponent`：

```typescript
use([
  CanvasRenderer,
  PieChart,
  BarChart,
  RadarChart,
  GridComponent,
  LegendComponent,
  RadarComponent,
  TitleComponent,
  TooltipComponent
])
```

- [ ] **Step 2: 写入 `frontend/src/components/admin/JudgeScoreRadar.vue`**

```vue
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, type ECharts } from 'echarts/core'
import { useResizeObserver } from '@vueuse/core'
import '@/utils/echarts'
import type { JudgeRunVO } from '@/types/admin'

const props = defineProps<{
  run: JudgeRunVO
}>()

const root = ref<HTMLElement>()
let chart: ECharts | null = null

function build(run: JudgeRunVO) {
  return {
    color: ['#2f6df5'],
    tooltip: {},
    radar: {
      indicator: [
        { name: '结构', max: 10 },
        { name: '事实', max: 10 },
        { name: '推理', max: 10 },
        { name: '引用', max: 10 },
        { name: '清晰度', max: 10 }
      ],
      splitArea: { areaStyle: { color: ['#f9fafc', '#fff'] } },
      axisName: { color: '#4b5563', fontSize: 12 }
    },
    series: [
      {
        type: 'radar',
        areaStyle: { opacity: 0.18 },
        lineStyle: { width: 2 },
        data: [
          {
            value: [run.structure, run.factuality, run.reasoning, run.citation, run.clarity],
            name: `#${run.taskId}`
          }
        ]
      }
    ]
  }
}

function render() {
  if (!chart || !props.run) return
  chart.setOption(build(props.run), true)
}

onMounted(() => {
  if (!root.value) return
  chart = init(root.value)
  render()
  useResizeObserver(root, () => chart?.resize())
})

watch(() => props.run, () => render(), { deep: true })

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="root" class="radar" />
</template>

<style scoped>
.radar { width: 100%; height: 260px; }
</style>
```

- [ ] **Step 3: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 24: 新建 `components/admin/JudgeDetailDrawer.vue`

**Files:**
- Create: `frontend/src/components/admin/JudgeDetailDrawer.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import JudgeScoreRadar from '@/components/admin/JudgeScoreRadar.vue'
import type { JudgeRunVO } from '@/types/admin'
import { formatEpochMillis } from '@/utils/format'

const props = defineProps<{
  modelValue: boolean
  run: JudgeRunVO | null
  reRunLoading: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 're-run'): void
}>()

const grade = computed(() => {
  if (!props.run) return ''
  const o = props.run.overall
  if (o >= 9) return '优秀'
  if (o >= 7) return '良好'
  if (o >= 5) return '待提升'
  return '不合格'
})

const gradeColor = computed(() => {
  if (!props.run) return ''
  const o = props.run.overall
  if (o >= 9) return 'success'
  if (o >= 7) return 'primary'
  if (o >= 5) return 'warning'
  return 'danger'
})

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
</script>

<template>
  <el-drawer v-model="visible" :title="run ? `评分详情 #${run.taskId}` : ''" size="500">
    <template v-if="run">
      <div class="head">
        <div class="overall">
          <div class="overall-label">总分</div>
          <div class="overall-value">{{ run.overall.toFixed(1) }} <span>/ 10</span></div>
          <el-tag :type="gradeColor" size="small" effect="light">{{ grade }}</el-tag>
        </div>
        <JudgeScoreRadar :run="run" class="radar-box" />
      </div>

      <el-divider />

      <div class="meta">
        <div><span>评估规则</span><b>{{ run.rubricVersion }}</b></div>
        <div><span>裁判模型</span><b>{{ run.judgeModel }}</b></div>
        <div><span>耗时</span><b>{{ run.latencyMs }} ms</b></div>
        <div><span>评估时间</span><b>{{ formatEpochMillis(run.createTime) }}</b></div>
      </div>

      <el-divider />

      <div class="comments">
        <h3>评论区</h3>
        <div
          v-for="(text, dim) in run.comments"
          :key="dim"
          class="comment-card"
        >
          <div class="comment-dim">{{ dim }} 评语</div>
          <div class="comment-text">{{ text }}</div>
        </div>
        <el-empty v-if="!Object.keys(run.comments).length" description="暂无评语" />
      </div>

      <div class="footer">
        <el-button
          type="primary"
          size="large"
          :loading="reRunLoading"
          @click="emit('re-run')"
        >
          {{ reRunLoading ? '评分中…' : '对该任务再次评分（约 5-15s）' }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.head { display: flex; gap: 16px; align-items: center; }
.overall { flex-shrink: 0; min-width: 110px; }
.overall-label { font-size: 12px; color: var(--text-tertiary); }
.overall-value {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.1;
}
.overall-value span { font-size: 14px; color: var(--text-tertiary); font-weight: 400; }
.radar-box { flex: 1; min-width: 0; }
.meta { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.meta div { font-size: 13px; }
.meta span { color: var(--text-tertiary); margin-right: 8px; }
.meta b { color: var(--text-primary); font-weight: 500; }
.comments h3 { margin: 0 0 12px; font-size: 14px; }
.comment-card {
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  padding: 12px;
  margin-bottom: 8px;
}
.comment-dim { font-size: 12px; color: var(--text-secondary); margin-bottom: 4px; font-weight: 500; }
.comment-text { font-size: 13px; color: var(--text-primary); line-height: 1.6; }
.footer { margin-top: 24px; text-align: center; }
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 25: 新建 `views/admin/AdminJudgeView.vue`

**Files:**
- Create: `frontend/src/views/admin/AdminJudgeView.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import JudgeDetailDrawer from '@/components/admin/JudgeDetailDrawer.vue'
import { adminApi } from '@/api/admin'
import type { JudgeRunVO } from '@/types/admin'
import { formatEpochMillis } from '@/utils/format'

const loading = ref(false)
const runs = ref<JudgeRunVO[]>([])
const taskIdInput = ref<number | null>(null)
const judgingNew = ref(false)

const drawerOpen = ref(false)
const drawerRun = ref<JudgeRunVO | null>(null)
const reRunLoading = ref(false)

// 前端分页
const pageSize = 10
const currentPage = ref(1)
const pagedRuns = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return runs.value.slice(start, start + pageSize)
})

async function load() {
  loading.value = true
  try {
    runs.value = await adminApi.judgeRecent(50)
  } finally {
    loading.value = false
  }
}

async function judgeNew() {
  if (taskIdInput.value == null) {
    ElMessage.warning('请输入任务 ID')
    return
  }
  judgingNew.value = true
  try {
    const r = await adminApi.judgeRun(taskIdInput.value)
    ElMessage.success(`评分完成：总分 ${r.overall.toFixed(1)}`)
    await load()
    openDetail(r)
  } finally {
    judgingNew.value = false
  }
}

function openDetail(r: JudgeRunVO) {
  drawerRun.value = r
  drawerOpen.value = true
}

async function reRunCurrent() {
  if (!drawerRun.value) return
  reRunLoading.value = true
  try {
    const r = await adminApi.judgeRun(drawerRun.value.taskId)
    ElMessage.success(`评分完成：总分 ${r.overall.toFixed(1)}`)
    drawerRun.value = r
    await load()
  } finally {
    reRunLoading.value = false
  }
}

function scoreColor(v: number) {
  if (v >= 9) return '#10b981'
  if (v >= 7) return '#2f6df5'
  if (v >= 5) return '#f59e0b'
  return '#ef4444'
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="admin-judge">
    <AdminPageHeader
      title="LLM-as-Judge 评分"
      subtitle="对研报任务进行结构、事实、推理、引用、清晰度五维质量评估。"
    />

    <div class="trigger-card">
      <div class="trigger-fields">
        <div class="field">
          <label>任务 ID</label>
          <el-input-number
            v-model="taskIdInput"
            :min="1"
            placeholder="输入任务 ID"
            style="width: 200px"
          />
        </div>
        <div class="field">
          <label>裁判模型</label>
          <el-tag size="default" type="info" effect="plain">qwen-max</el-tag>
        </div>
        <el-button
          type="primary"
          size="large"
          :loading="judgingNew"
          @click="judgeNew"
        >
          {{ judgingNew ? '评分中…' : '开始评分' }}
        </el-button>
        <span class="hint">评分约 5-15s，请耐心等待</span>
      </div>
    </div>

    <div class="list-card">
      <header class="card-header">
        <h3>最近评分记录</h3>
        <el-button :icon="Refresh" link @click="load">刷新</el-button>
      </header>

      <el-table :data="pagedRuns" stripe style="width: 100%">
        <el-table-column label="任务 ID" width="100">
          <template #default="{ row }">
            <span>Task #{{ (row as JudgeRunVO).taskId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总分" width="80">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).overall), fontWeight: 600 }">
              {{ (row as JudgeRunVO).overall.toFixed(1) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="结构" width="70">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).structure) }">{{ (row as JudgeRunVO).structure.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="事实" width="70">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).factuality) }">{{ (row as JudgeRunVO).factuality.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="推理" width="70">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).reasoning) }">{{ (row as JudgeRunVO).reasoning.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="引用" width="70">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).citation) }">{{ (row as JudgeRunVO).citation.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="清晰度" width="80">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).clarity) }">{{ (row as JudgeRunVO).clarity.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ (row as JudgeRunVO).latencyMs }} ms</template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatEpochMillis((row as JudgeRunVO).createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="160">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row as JudgeRunVO)">查看详情</el-button>
            <el-button
              link
              type="primary"
              size="small"
              :loading="reRunLoading && drawerRun?.taskId === (row as JudgeRunVO).taskId"
              @click="() => { drawerRun = row as JudgeRunVO; reRunCurrent() }"
            >再次评分</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="currentPage"
          :page-size="pageSize"
          :total="runs.length"
          @current-change="(p) => (currentPage = p)"
        />
      </div>
    </div>

    <JudgeDetailDrawer
      v-model="drawerOpen"
      :run="drawerRun"
      :re-run-loading="reRunLoading"
      @re-run="reRunCurrent"
    />
  </div>
</template>

<style scoped>
.admin-judge { display: flex; flex-direction: column; gap: 16px; }
.trigger-card,
.list-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}
.trigger-fields {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.field { display: flex; flex-direction: column; gap: 4px; }
.field label { font-size: 12px; color: var(--text-tertiary); }
.hint { font-size: 12px; color: var(--text-tertiary); }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.card-header h3 { margin: 0; font-size: 14px; font-weight: 600; color: var(--text-primary); }
.pagination-row { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 26: P1 浏览器验证 + Commit

**Files:**（无新增改动，仅验证 + 提交）

- [ ] **Step 1: 起前端（若已起则刷新）**

Run: `cd frontend && npm run dev`
Expected: 进程仍在 5173

- [ ] **Step 2: 浏览器验证清单**

1. 点侧栏「Prompt 管理」→ 进入 `/admin/prompts`，左侧出现按 name 分组的版本列表
2. 在搜索框输入「planner」→ 仅 `planner_prompt` 组保留
3. 选择某个 v1 / v2 → 右侧版本详情正确填充
4. 点「编辑内容」→ 进入 edit 模式，textarea 可编辑；点「取消」→ 回 view
5. 在 edit 模式改几个字，点「保存修改」→ toast 成功，右侧详情更新
6. 选一个非生效版本，点「设为生效版本」→ 二次确认 → 成功后列表里生效 tag 移到该版本
7. 点「+ 新建版本」→ 弹 dialog，填 `name=test_prompt, version=v1, content=hello`，提交 → 列表新增组，自动选中新版本
8. 点侧栏「Judge 评估」→ 进入 `/admin/judge`
9. 输入一个已完成任务的 ID（去 `/admin/tasks` 找一个 DONE 的），点「开始评分」→ loading 5-15s →成功后列表多一行，drawer 自动打开，雷达图渲染五维分数
10. 列表 5 维分数按区间染色（>=9 绿、7~9 蓝、5~7 黄、<5 红）
11. 点行的「再次评分」→ loading → 完成后 drawer 数据更新
12. 控制台无 error

- [ ] **Step 3: 暂存并 commit**

Run:

```bash
cd /Users/zhengsmacbook/Desktop/miniProject/claude/enterprise-iner-training
git add frontend/src/utils/echarts.ts \
        frontend/src/components/admin/PromptGroupList.vue \
        frontend/src/components/admin/PromptDetailPanel.vue \
        frontend/src/components/admin/JudgeScoreRadar.vue \
        frontend/src/components/admin/JudgeDetailDrawer.vue \
        frontend/src/views/admin/AdminPromptView.vue \
        frontend/src/views/admin/AdminJudgeView.vue
git commit -m "$(cat <<'EOF'
feat(frontend/f3): P1 AdminPrompt 版本管理 + AdminJudge 评分

- AdminPromptView：左侧分组 + 搜索 + 灰度切换 + 在线编辑 + 新建版本
- AdminJudgeView：触发评分（loading 5-15s）+ 最近评分列表 + 5 维染色 + 详情抽屉（雷达图 + 评语）
- utils/echarts.ts 注册 RadarChart / RadarComponent 模块

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

Expected: 一个新 commit `feat(frontend/f3): P1 ...`

---

## Milestone P2 — Workflow 管理 + 后端 TODO 文档

新建 `AdminWorkflowView` 及子组件，产出后端接口缺口 handoff 文档。

---

### Task 27: 新建 `components/admin/WorkflowGraph.vue`

**Files:**
- Create: `frontend/src/components/admin/WorkflowGraph.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import { Document, Search, PieChart, EditPen, CircleCheck, Right } from '@element-plus/icons-vue'

defineProps<{
  nodes: string[]
}>()

const iconMap: Record<string, unknown> = {
  Planner: Document,
  Researcher: Search,
  Analyst: PieChart,
  Writer: EditPen,
  Critic: CircleCheck
}

const subtitle: Record<string, string> = {
  Planner: '规划',
  Researcher: '研究检索',
  Analyst: '分析整理',
  Writer: '撰写报告',
  Critic: '评审优化'
}
</script>

<template>
  <div class="graph">
    <template v-for="(n, i) in nodes" :key="n">
      <div class="node">
        <div class="node-icon">
          <el-icon :size="20"><component :is="iconMap[n] ?? Document" /></el-icon>
        </div>
        <div class="node-name">{{ n }}</div>
        <div class="node-sub">{{ subtitle[n] ?? '' }}</div>
      </div>
      <el-icon v-if="i < nodes.length - 1" class="arrow" :size="18"><Right /></el-icon>
    </template>
  </div>
</template>

<style scoped>
.graph {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.node {
  flex: 1;
  min-width: 90px;
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  padding: 14px 8px;
  text-align: center;
}
.node-icon {
  width: 32px;
  height: 32px;
  margin: 0 auto 6px;
  border-radius: 8px;
  background: rgba(47, 109, 245, 0.08);
  color: var(--color-primary);
  display: grid;
  place-items: center;
}
.node-name { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.node-sub { font-size: 11px; color: var(--text-tertiary); margin-top: 2px; }
.arrow { color: var(--color-primary); }
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 28: 新建 `components/admin/QueryRewritePanel.vue`

**Files:**
- Create: `frontend/src/components/admin/QueryRewritePanel.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { adminApi } from '@/api/admin'
import type { QueryRewriteVO } from '@/types/admin'

const topic = ref('')
const loading = ref(false)
const result = ref<QueryRewriteVO | null>(null)
const resultTime = ref<string>('')

async function rewrite() {
  const t = topic.value.trim()
  if (t.length < 2) {
    ElMessage.warning('主题至少 2 个字符')
    return
  }
  loading.value = true
  try {
    result.value = await adminApi.rewriteQuery(t)
    resultTime.value = new Date().toLocaleString('zh-CN')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="card">
    <header class="card-header">
      <h3>Query Rewrite 测试</h3>
    </header>

    <div class="field">
      <label>输入研究主题</label>
      <el-input
        v-model="topic"
        type="textarea"
        :rows="4"
        :maxlength="500"
        show-word-limit
        placeholder="输入研究主题"
        resize="vertical"
      />
    </div>

    <el-button
      type="primary"
      size="large"
      :icon="Promotion"
      :loading="loading"
      style="width: 100%; margin-top: 12px"
      @click="rewrite"
    >测试改写</el-button>

    <div v-if="result" class="result">
      <header class="result-header">
        <span>示例输出（本次改写）</span>
        <span class="time">{{ resultTime }}</span>
      </header>

      <div class="row">
        <span class="row-label">用户意图</span>
        <el-tag size="small" type="primary">{{ result.intent ?? '—' }}</el-tag>
      </div>
      <div class="row">
        <span class="row-label">行业 / 时间 / 地域</span>
        <el-tag size="small">{{ result.industry ?? '—' }}</el-tag>
        <el-tag size="small">{{ result.year ?? '—' }}</el-tag>
        <el-tag size="small">{{ result.geo ?? '—' }}</el-tag>
      </div>
      <div class="row">
        <span class="row-label">检索子查询</span>
        <ul class="sub-list">
          <li v-for="(q, i) in (result.sub_queries ?? [])" :key="i">{{ q }}</li>
          <li v-if="!(result.sub_queries?.length)" class="empty">（无子查询）</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}
.card-header { margin-bottom: 12px; }
.card-header h3 { margin: 0; font-size: 14px; font-weight: 600; color: var(--text-primary); }
.field label { font-size: 12px; color: var(--text-tertiary); margin-bottom: 4px; display: block; }
.result {
  margin-top: 16px;
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  padding: 12px;
}
.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 8px;
}
.result-header .time { font-family: 'JetBrains Mono', monospace; }
.row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.row-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-right: 6px;
  min-width: 84px;
}
.sub-list { margin: 0; padding-left: 16px; font-size: 13px; color: var(--text-primary); }
.sub-list li { line-height: 1.6; }
.sub-list li.empty { color: var(--text-tertiary); font-style: italic; }
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 29: 新建 `views/admin/AdminWorkflowView.vue`

**Files:**
- Create: `frontend/src/views/admin/AdminWorkflowView.vue`

- [ ] **Step 1: 写入文件**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Document, Refresh, WarningFilled } from '@element-plus/icons-vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import WorkflowGraph from '@/components/admin/WorkflowGraph.vue'
import QueryRewritePanel from '@/components/admin/QueryRewritePanel.vue'
import { adminApi } from '@/api/admin'
import {
  mockActiveWorkflow,
  mockWorkflowLoadLog
} from '@/mock/admin-placeholders'

interface ReloadResult {
  status: 'success' | 'fail'
  latencyMs: number
  finishedAt: string
  message: string
}

const reloading = ref(false)
const lastResult = ref<ReloadResult | null>(null)
const logRef = ref<HTMLElement | null>(null)

async function reload() {
  reloading.value = true
  const start = performance.now()
  try {
    const msg = await adminApi.reloadWorkflow()
    lastResult.value = {
      status: 'success',
      latencyMs: Math.round(performance.now() - start),
      finishedAt: new Date().toLocaleString('zh-CN'),
      message: msg ?? '下次任务将使用最新 YAML'
    }
    ElMessage.success('Workflow 缓存已清空，下次任务将使用最新 YAML')
  } catch (e) {
    lastResult.value = {
      status: 'fail',
      latencyMs: Math.round(performance.now() - start),
      finishedAt: new Date().toLocaleString('zh-CN'),
      message: (e as Error)?.message || '热更新失败'
    }
  } finally {
    reloading.value = false
  }
}

function scrollToLog() {
  logRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <div class="admin-workflow">
    <AdminPageHeader
      title="Workflow 管理"
      subtitle="清空 Workflow YAML 缓存、测试 Query Rewrite，确保下次任务使用最新编排配置。"
    />

    <section class="top-row">
      <!-- 当前活跃 Workflow -->
      <div class="card">
        <header class="card-header">
          <h3>当前活跃 Workflow</h3>
        </header>
        <!-- TODO(backend-api-gap #4): 待 /admin/workflow/active 上线后改为接口 -->
        <div class="info-grid">
          <div><span>Workflow</span><b>{{ mockActiveWorkflow.name }}</b></div>
          <div><span>版本</span><b>{{ mockActiveWorkflow.version }}</b></div>
          <div><span>文件</span><b class="mono">{{ mockActiveWorkflow.file }}</b></div>
          <div><span>节点</span><b>{{ mockActiveWorkflow.nodes.join(' → ') }}</b></div>
          <div><span>最近加载</span><b>{{ mockActiveWorkflow.lastLoadedAt }}</b></div>
          <div>
            <span>缓存状态</span>
            <el-tag size="small" type="success">{{ mockActiveWorkflow.cached ? '已缓存' : '未缓存' }}</el-tag>
          </div>
        </div>

        <WorkflowGraph :nodes="mockActiveWorkflow.nodes" style="margin: 16px 0" />

        <el-alert
          type="warning"
          :icon="WarningFilled"
          :closable="false"
          show-icon
          title="修改 workflow/*.yaml 后点击下方按钮清空缓存，无需重启服务。"
        />

        <div class="action-row">
          <el-button
            type="primary"
            size="large"
            :icon="Refresh"
            :loading="reloading"
            style="flex: 1"
            @click="reload"
          >清空 Workflow 缓存并热更新</el-button>
          <el-button
            size="large"
            :icon="Document"
            style="flex: 1"
            @click="scrollToLog"
          >查看完整日志</el-button>
        </div>
      </div>

      <!-- 右侧：热更新结果 + Query Rewrite 测试 -->
      <div class="right-col">
        <div class="card">
          <header class="card-header">
            <h3>热更新结果</h3>
          </header>
          <div v-if="!lastResult" class="empty">本次会话尚未触发热更新</div>
          <div v-else class="info-grid">
            <div><span>上次操作</span><b>清缓存并刷新</b></div>
            <div>
              <span>结果</span>
              <el-tag :type="lastResult.status === 'success' ? 'success' : 'danger'" size="small">
                <el-icon style="margin-right: 2px"><CircleCheck /></el-icon>
                {{ lastResult.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </div>
            <div><span>耗时</span><b>{{ lastResult.latencyMs }} ms</b></div>
            <div><span>说明</span><b>{{ lastResult.message }}</b></div>
            <div><span>完成时间</span><b>{{ lastResult.finishedAt }}</b></div>
          </div>
        </div>

        <QueryRewritePanel />
      </div>
    </section>

    <!-- 最近加载日志（mock） -->
    <div ref="logRef" class="card">
      <header class="card-header">
        <h3>最近加载日志</h3>
        <a class="link">查看完整日志 ›</a>
      </header>
      <!-- TODO(backend-api-gap #5): 待 /admin/workflow/history 上线后改为接口 -->
      <el-timeline>
        <el-timeline-item
          v-for="(row, i) in mockWorkflowLoadLog"
          :key="i"
          :type="row.level === 'success' ? 'success' : row.level === 'warning' ? 'warning' : 'primary'"
          :timestamp="row.time"
        >
          <div class="log-event">{{ row.event }}</div>
          <div class="log-detail">{{ row.detail }}</div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </div>
</template>

<style scoped>
.admin-workflow { display: flex; flex-direction: column; gap: 16px; }
.top-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  align-items: stretch;
}
.right-col { display: flex; flex-direction: column; gap: 16px; }
.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.card-header h3 { margin: 0; font-size: 14px; font-weight: 600; color: var(--text-primary); }
.link { font-size: 12px; color: var(--color-primary); cursor: pointer; }
.info-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px 16px;
}
.info-grid > div { display: flex; gap: 12px; font-size: 13px; align-items: center; }
.info-grid > div > span { color: var(--text-tertiary); min-width: 88px; }
.info-grid > div > b { color: var(--text-primary); font-weight: 500; }
.mono { font-family: 'JetBrains Mono', monospace; font-size: 12px; }
.action-row { display: flex; gap: 12px; margin-top: 12px; }
.empty { font-size: 13px; color: var(--text-tertiary); text-align: center; padding: 24px 0; }
.log-event { font-size: 13px; font-weight: 500; color: var(--text-primary); }
.log-detail { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
@media (max-width: 1280px) {
  .top-row { grid-template-columns: 1fr; }
}
</style>
```

- [ ] **Step 2: 验证类型**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 无 error TS 行

---

### Task 30: 产出后端接口缺口文档

**Files:**
- Create: `docs/superpowers/handoff/2026-05-24-backend-api-gaps.md`

- [ ] **Step 1: 写入文件**

```markdown
# 后端接口缺口清单（F3 前端落地暴露的）

> 编写日期：2026-05-24
> 上下文：F3 前端 Admin 中台落地时，部分设计图元素后端尚无对应接口。前端先以 mock 占位（集中在 `frontend/src/mock/admin-placeholders.ts`），等后端补接口后替换。
> 关联：[F3 Spec](../specs/2026-05-23-frontend-f3-admin-design.md)、[F3 Plan](../plans/2026-05-24-frontend-f3-admin-implementation.md)

---

## 缺口总览

| # | 缺口 | 影响页面 | 建议接口 | 优先级 |
|---|---|---|---|---|
| 1 | 系统健康子服务粒度 | AdminStatsView 「系统健康」面板 | `GET /api/health/components` | P3 |
| 2 | StatCard 同比指标 | HomeView + AdminStatsView | `PlatformOverviewVO` 增加 `*Delta` 字段 | P3 |
| 3 | 最近模型调用平均耗时 | AdminStatsView「最近模型调用」表格 | `ModelCostVO` 增加 `avgLatencyMs` 字段 | P2 |
| 4 | Workflow 当前活跃元信息 | AdminWorkflowView「当前活跃 Workflow」面板 | `GET /admin/workflow/active` | P2 |
| 5 | Workflow 加载历史 | AdminWorkflowView「最近加载日志」时间轴 | `GET /admin/workflow/history?limit=20` | P3 |
| 6 | Judge 评分关联主题 | AdminJudgeView 列表 | `JudgeRunVO` 增加 `topic` 字段 | P2 |
| 7 | Judge 评分裁判模型可选 | AdminJudgeView 触发卡 | `POST /admin/eval/judge/{taskId}?judgeModel=...` | P3 |

---

## #1 系统健康子服务粒度

**前端当前实现**：`SystemHealthPanel.vue` 顶部 1 条调用 `GET /api/health` 显示「API 服务」状态与响应耗时（轮询 60s）。下面 2 条「Redis 缓存」「SSE 服务」从 `mock/admin-placeholders.ts: mockSystemHealthExtras` 读 mock 数据。

**建议后端接口**：

```http
GET /api/health/components
```

返回：

```typescript
[
  { name: 'API',   status: 'UP', latencyMs: 78, subtitle: '后端接口服务' },
  { name: 'Redis', status: 'UP', latencyMs: 1.2, subtitle: '缓存与会话存储' },
  { name: 'SSE',   status: 'UP', latencyMs: null, subtitle: '流式推送服务',
    extra: { connections: 12 } }
]
```

**关联文件**：`frontend/src/components/admin/SystemHealthPanel.vue`、`frontend/src/mock/admin-placeholders.ts`

---

## #2 StatCard 同比指标

**前端当前实现**：`HomeView` 与 `AdminStatsView` 4 个 StatCard 不显示「较昨日 ±x.x%」（StatCard 的 `delta` 字段已改为可选）。

**建议后端方案**（两种任选）：

- A. 在 `PlatformOverviewVO` 增加同比字段：`totalTasksDelta: number; successRateDelta: number; totalCostCnyDelta: number; p95LatencyMsDelta: number`（绝对差值）+ 相应百分比字段。
- B. 新增接口 `GET /admin/stats/overview/compare?range=1d` 返回当前与对比周期的两组 `PlatformOverviewVO`，前端自己算同比。

推荐 A，避免前端二次请求。

**关联文件**：`frontend/src/views/HomeView.vue`、`frontend/src/views/admin/AdminStatsView.vue`、`frontend/src/components/stat/StatCard.vue`

---

## #3 最近模型调用平均耗时

**前端当前实现**：`RecentModelCallsTable.vue` 表格最后一列「平均耗时」固定显示 `—`。

**建议后端方案**：在 `ModelCostVO` 中追加 `avgLatencyMs: number`（由 `workflow_node_run` 表对应模型的 `(finished_at - started_at)` 取平均得到）。

**关联文件**：`frontend/src/components/admin/RecentModelCallsTable.vue`

---

## #4 Workflow 当前活跃元信息

**前端当前实现**：`AdminWorkflowView.vue` 左上「当前活跃 Workflow」整张卡片来自 `mock/admin-placeholders.ts: mockActiveWorkflow`。

**建议后端接口**：

```http
GET /admin/workflow/active
```

返回：

```typescript
{
  name: 'multi_agent_v1',
  version: 'v2',
  file: 'classpath:workflow/multi_agent_v1.yaml',
  nodes: ['Planner', 'Researcher', 'Analyst', 'Writer', 'Critic'],
  lastLoadedAt: 1716534000000,  // epoch millis
  cached: true
}
```

**关联文件**：`frontend/src/views/admin/AdminWorkflowView.vue`、`frontend/src/mock/admin-placeholders.ts`

---

## #5 Workflow 加载历史

**前端当前实现**：`AdminWorkflowView.vue` 底部「最近加载日志」时间轴来自 `mockWorkflowLoadLog`（4 条静态条目）。

**建议后端接口**：

```http
GET /admin/workflow/history?limit=20
```

返回：

```typescript
[
  { eventType: 'cache_clear', message: '已清空 Workflow YAML 缓存…', ts: 1716534000000, level: 'info' },
  { eventType: 'yaml_reload', message: '成功加载 classpath:workflow/multi_agent_v1.yaml (v2)', ts: 1716534000050, level: 'success' },
  ...
]
```

建议后端在 `WorkflowLoader.reload()` 内写一条审计日志到 `workflow_load_log` 表。

**关联文件**：`frontend/src/views/admin/AdminWorkflowView.vue`

---

## #6 Judge 评分关联主题

**前端当前实现**：`AdminJudgeView.vue` 列表「任务 ID」列只显示 `Task #{taskId}`，不显示研究主题（因为 `JudgeRunVO` 无 `topic` 字段）。

**建议后端方案**（两种任选）：

- A. 在 `JudgeRunVO` 增加 `topic: string` 字段，由 `ReportJudgeService` 在写库或返回时 join 取 `report_task.topic`。
- B. 不改 VO，前端在拉到 `judgeRecent` 后批量调 `/admin/tasks` 反查主题。性能差，不推荐。

推荐 A。

**关联文件**：`frontend/src/views/admin/AdminJudgeView.vue`

---

## #7 Judge 评分裁判模型可选

**前端当前实现**：触发卡片「裁判模型」位置固定显示只读 Badge `qwen-max`。

**建议后端方案**：`POST /admin/eval/judge/{taskId}` 接收 query 或 body 参数 `judgeModel`，由 `ReportJudgeService.judge()` 根据该参数路由不同模型。同时建议增加 `GET /admin/eval/judge/models` 返回可选裁判模型列表。

**关联文件**：`frontend/src/views/admin/AdminJudgeView.vue`

---

## 接入说明

后端补好某接口后，前端只需要做两件事：

1. 在 `frontend/src/types/admin.ts` 增加对应类型，在 `frontend/src/api/admin.ts` 增加对应方法
2. 把目标文件里 `TODO(backend-api-gap #N)` 注释处的 mock 引用替换为接口调用

mock 数据可在所有 gap 补齐后整体删除 `frontend/src/mock/admin-placeholders.ts`。
```

- [ ] **Step 2: 验证文件存在**

Run: `ls -la docs/superpowers/handoff/2026-05-24-backend-api-gaps.md`
Expected: 文件大小 ~4 KB

---

### Task 31: P2 浏览器验证 + 最终 Commit

**Files:**（无新增改动，仅验证 + 提交）

- [ ] **Step 1: 起前端**

Run: `cd frontend && npm run dev`
Expected: Vite 仍在 5173

- [ ] **Step 2: 浏览器验证清单**

1. 点侧栏「Workflow 管理」→ 进入 `/admin/workflow`
2. 左上「当前活跃 Workflow」卡片显示 `multi_agent_v1` / `v2` / 5 节点链
3. 节点流程图（WorkflowGraph）正常渲染 5 个节点与 4 个箭头
4. 点「清空 Workflow 缓存并热更新」→ 按钮 loading → 成功 toast → 右上「热更新结果」面板出现：结果 ✓ 成功 / 耗时（ms）/ 完成时间
5. 点「查看完整日志」→ 页面滚动到底部「最近加载日志」时间轴（4 条 mock）
6. 在右下「Query Rewrite 测试」textarea 输入「2026 新能源汽车」→ 点「测试改写」→ 结果区显示 intent / industry / year / geo / sub_queries 标签
7. 输入小于 2 字符的内容点提交 → 提示「主题至少 2 个字符」
8. 控制台无 error；vue-tsc 整体无 error
9. `docs/superpowers/handoff/2026-05-24-backend-api-gaps.md` 存在且可读

- [ ] **Step 3: 整库类型最终检查**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 整体 0 error

Run: `cd frontend && npm run build`
Expected: 成功打包，无 error

- [ ] **Step 4: 暂存并 commit**

Run:

```bash
cd /Users/zhengsmacbook/Desktop/miniProject/claude/enterprise-iner-training
git add frontend/src/components/admin/WorkflowGraph.vue \
        frontend/src/components/admin/QueryRewritePanel.vue \
        frontend/src/views/admin/AdminWorkflowView.vue \
        docs/superpowers/handoff/2026-05-24-backend-api-gaps.md
git commit -m "$(cat <<'EOF'
feat(frontend/f3): P2 AdminWorkflow + 后端接口缺口清单

- AdminWorkflowView：当前活跃 Workflow + 节点流程图 + 一键热更新 + Query Rewrite 测试 + 加载日志时间轴
- 「当前活跃信息」与「加载日志」走 mock，标 TODO(backend-api-gap #4/#5)；其余面板接真实接口
- 输出 docs/superpowers/handoff/2026-05-24-backend-api-gaps.md，汇总 7 项后端待补接口

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

Expected: 一个新 commit `feat(frontend/f3): P2 ...`，git log 应看到 P0/P1/P2 三个连续 commit。

---

## 验收

完成全部 31 个任务后，`git log --oneline -4` 应类似：

```
xxxxxxx feat(frontend/f3): P2 AdminWorkflow + 后端接口缺口清单
xxxxxxx feat(frontend/f3): P1 AdminPrompt 版本管理 + AdminJudge 评分
xxxxxxx feat(frontend/f3): P0 基建 + AdminStats + AdminTaskList + HomeView 接入真实数据
46e03b8 docs(spec): F3 admin frontend design
```

`http://localhost:5173/` 登录后，可：
- 首页：StatCard 与最近活动来自真实接口
- 平台统计：4 卡 / 2 图 / 最近模型调用 / 系统健康
- 任务管理：状态过滤分页可用
- Prompt 管理：分组 + 搜索 + 灰度切换 + 编辑 + 新建版本
- Judge 评估：触发评分 + 列表 + 雷达图详情
- Workflow 管理：节点链 + 一键热更 + Query Rewrite 测试

后端 TODO 清单产出 7 项可追踪缺口。

---

## Self-Review 结果

**Spec coverage**（对照 spec 14 节）：

- §1 目标/范围/三批节奏 → Plan 三 milestones 对齐 ✓
- §2 后端接口 14 条 → 全部在 Task 5 adminApi 覆盖 ✓
- §3 文件结构 → 全部 Task 1–31 覆盖 ✓
- §4 路由/菜单/权限 → Task 17 / 18 覆盖 ✓
- §5 API+Types → Task 1 / 4 / 5 ✓
- §6 五视图 → Stats(14)、Tasks(15)、Prompt(20–22)、Judge(23–25)、Workflow(27–29) ✓
- §7 HomeView 改造 → Task 16；§5.1 spec 修订（dashboard.ts 只删两 const）→ Task 2 ✓
- §8 utils/样式 → Task 6 / 7 ✓
- §9 mock 集中 → Task 8 ✓
- §10 后端 TODO → Task 30 ✓
- §11 验证清单 → Task 19 / 26 / 31 ✓
- §12 YAGNI、§13 风险、§14 流程 → 隐含落地 ✓

**Placeholder scan**：未发现 TBD / TODO（spec 注释里的 `TODO(backend-api-gap)` 是规范要求的标注，不算 plan 占位）✓

**Type consistency**：`formatDurationMs` / `formatPercent` / `formatNumber` / `formatCny` 在 Task 6 定义后被 14 / 15 / 16 复用，名字一致；`TaskStatus`、`Page<T>` 等类型在 Task 1 / 5 定义后被 17 / 15 / 16 复用，签名一致 ✓
