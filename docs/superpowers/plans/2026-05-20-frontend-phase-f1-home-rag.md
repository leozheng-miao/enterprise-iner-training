# Phase F1 · 首页 Dashboard + RAG 检索 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现设计图 #1（首页 dashboard，含问候 / 快速开始 / 7 阶段进度 / 4 统计卡 / 核心能力 / 最近活动）和设计图 #2（RAG Hybrid Search 检索页，含查询表单 / 命中结果卡 / 引用面板 / 来源饼图 / Score 柱图 / 检索配置），并把"RAG 检索"侧边菜单激活上线。

**Architecture:** 沿用 F0 已搭好的 Vite + Vue3 + TS + Pinia + Vue Router + Element Plus 骨架。F1 新增：① `mock/` 目录存放 dashboard 假数据（4 统计卡/7 阶段/核心能力/最近活动—— 后端 F1 阶段尚无聚合接口）；② `components/stat /stage /home /rag /chart /` 五个新组件目录；③ ECharts 按需引入；④ `api/rag.ts` 直连后端 `/api/rag/search`；⑤ 路由新增 `/rag`，DefaultLayout 启用 `rag` 菜单。

**Tech Stack:** 在 F0 基础上新增 `echarts` ^5.5.0、`markdown-it` ^14 暂不用、`@vueuse/core`（F0 已装，复用 `useElementSize` 做 ECharts 响应式）。

---

## 重要约定（所有 Task 必读）

> **承接 F0 的所有红线**：
> - Agent **绝不**跑 `npm` / `npm install` / `npm run dev` / `npm run build` / `mvn` / `curl`
> - Agent 写完代码即 commit；用户在 IDE 安装新依赖（echarts）并验证
> - 不写单测（spec §7 锁定 MVP 无单测）
> - 用 `Write` 创建新文件、`Edit` 改 F0 已存在文件
> - 所有路径以仓库根为基准；新文件路径以 `frontend/` 开头
>
> **F1 特有约定**：
> - **echarts 是 Phase F1 新引入的运行时依赖** —— 第一个 Task 改 `frontend/package.json` 时把 echarts 加进去，用户在 IDE 重新 `npm install` 一次（一次性）
> - HomeView dashboard 大部分数据是 mock —— Phase 4 平台中台落地后再切真接口；mock 数据放在 `frontend/src/mock/` 目录便于未来替换
> - RAG 检索页**直连真后端** `/api/rag/search`，需要后端在 IDE 启动 + 至少跑过一次 Ingest 让 PGVector 里有数据（用户在 F0 验收时其实已经登录过了，这里前置假设已经入库）

---

## File Structure

### 本 Phase 新增文件

```
frontend/src/
├── mock/                          # ← 新增目录
│   └── dashboard.ts               # 首页假数据（stat / stage / capability / activity）
├── types/
│   └── rag.ts                     # ← 新增：HitData / SearchRequest / SearchResponse 等
├── api/
│   └── rag.ts                     # ← 新增：rag.search()
├── utils/                         # ← 新增目录
│   └── echarts.ts                 # ECharts 按需注册 + 主题色
├── components/
│   ├── stat/
│   │   └── StatCard.vue           # 4 统计卡（图1 中段）
│   ├── stage/
│   │   ├── StageStepBar.vue       # 顶部 7 段水平进度条 + 三态图例（图1 顶部）
│   │   └── StageOverview.vue      # 阶段进度总览（图1 左下，含 % 与日期 + 当前阶段卡）
│   ├── home/
│   │   ├── QuickStartCard.vue     # 快速开始单卡（开始检索 / 发起报告 / 查看 Trace）
│   │   ├── CoreCapabilityCard.vue # 核心能力单卡（图1 右上四宫格）
│   │   └── RecentActivityList.vue # 最近活动列表（图1 右下）
│   ├── rag/
│   │   ├── HitResultCard.vue      # 检索结果单卡（图2 中央列表）
│   │   └── CitationPanel.vue      # 引用信息 + 来源饼图 + Score 柱图 + 检索配置（图2 右栏）
│   └── chart/
│       ├── DonutChart.vue         # ECharts 饼图封装（响应式）
│       └── BarChart.vue           # ECharts 柱图封装（响应式）
└── views/
    └── rag/
        └── RagSearchView.vue      # ← 新增：图2 主视图
```

### F0 已存在、F1 修改的文件

- `frontend/package.json` —— 追加 `echarts: ^5.5.0` 到 dependencies
- `frontend/src/views/HomeView.vue` —— 整页替换（F0 是占位页）
- `frontend/src/layouts/DefaultLayout.vue` —— 启用 `rag` 菜单（去 disabled + 加 path）+ 扩展 activeMenu 匹配逻辑
- `frontend/src/router/index.ts` —— `/` 子路由下加 `/rag`

---

## Task 1: 追加 echarts 依赖 + mock 数据基础类型

**Files:**
- Modify: `frontend/package.json` (add echarts dependency)
- Create: `frontend/src/mock/dashboard.ts`

- [ ] **Step 1.1: 修改 `frontend/package.json` 添加 echarts**

在 `dependencies` 块（按字母序）的 `element-plus` 之前插入 `"echarts": "^5.5.0",` 行。**只修改这一行，不动其他字段、不动版本号**。

修改后 dependencies 段应当长这样：

```json
  "dependencies": {
    "@element-plus/icons-vue": "^2.3.1",
    "@vueuse/core": "^10.11.0",
    "axios": "^1.7.2",
    "echarts": "^5.5.0",
    "element-plus": "^2.7.6",
    "pinia": "^2.1.7",
    "vue": "^3.4.31",
    "vue-router": "^4.4.0"
  },
```

- [ ] **Step 1.2: 创建 `frontend/src/mock/dashboard.ts`**

```ts
/**
 * Phase F1 首页 dashboard 假数据。
 *
 * 这些数据的真实接口将由 Phase 4 平台中台（Trace / Token 成本 / 任务统计聚合）提供。
 * 在那之前前端先用 mock 填表，保证视觉与设计图 #1 一致。
 *
 * 真接口落地后：保留这些 type 定义，改用 api/platform.ts 拉数据即可。
 */

export type StageStatus = 'done' | 'current' | 'pending'

/** 顶部"项目阶段概览"7 段水平条上的一站。 */
export interface StageNode {
  index: number          // 0-6
  title: string          // "阶段 0"
  subtitle: string       // "环境初始化"
  status: StageStatus
}

/** "阶段进度总览"下面的详情节点（含进度 + 日期 + 状态文案）。 */
export interface StageDetail {
  index: number          // 0-6
  title: string          // "环境初始化"
  percent: number        // 0-100
  date: string           // "2024-05-10" 或 ""
  statusLabel: string    // "100%" / "进行中" / "待开始"
  status: StageStatus
}

/** "当前阶段"卡上的描述。 */
export interface CurrentStageInfo {
  index: number
  title: string          // "阶段 3 - 报告生成"
  description: string
  bullets: string[]      // ["报告大纲生成", "内容生成", ...]
}

/** 4 个统计卡。 */
export interface StatItem {
  key: string
  label: string          // "已接入文档"
  value: string          // "12,842"
  unit?: string          // "s" 等可选
  iconName: string       // Element Plus 图标组件名
  iconBg: string         // CSS 颜色（卡片左上角图标圆角背景）
  delta: {
    raw: string          // "+532" / "-0.28s"
    percent: string      // "↑4.32%" / "↓13.33%"
    trend: 'up' | 'down'
    note: string         // "较昨日"
  }
}

/** "核心能力"4 卡。 */
export interface CoreCapability {
  key: string
  title: string          // "RAG 检索"
  description: string
  iconName: string
  iconBg: string
  link: string           // 路径，点击后路由跳转（未启用的 phase 用 ''）
}

/** "最近活动"列表项。 */
export interface ActivityItem {
  id: string
  title: string          // "用户登录"
  description: string
  time: string           // "09:24:18" / "昨天 17:42"
  iconName: string
  iconColor: string
}

// ====== 数据 ======

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

export const statItems: StatItem[] = [
  {
    key: 'docs',
    label: '已接入文档',
    value: '12,842',
    iconName: 'Document',
    iconBg: '#dbeafe',
    delta: { raw: '+532', percent: '↑4.32%', trend: 'up', note: '较昨日' }
  },
  {
    key: 'queries',
    label: 'RAG 查询次数',
    value: '45,723',
    iconName: 'Search',
    iconBg: '#dcfce7',
    delta: { raw: '+2,341', percent: '↑5.39%', trend: 'up', note: '较昨日' }
  },
  {
    key: 'tasks',
    label: '报告任务数',
    value: '156',
    iconName: 'Tickets',
    iconBg: '#ede9fe',
    delta: { raw: '+8', percent: '↑5.41%', trend: 'up', note: '较昨日' }
  },
  {
    key: 'latency',
    label: '平均响应耗时',
    value: '1.82',
    unit: 's',
    iconName: 'Timer',
    iconBg: '#ffedd5',
    delta: { raw: '-0.28s', percent: '↓13.33%', trend: 'down', note: '较昨日' }
  }
]

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

export const recentActivities: ActivityItem[] = [
  {
    id: 'a1',
    title: '用户登录',
    description: '张伟 登录系统',
    time: '09:24:18',
    iconName: 'User',
    iconColor: '#9ca3af'
  },
  {
    id: 'a2',
    title: '报告任务已启动',
    description: '任务《2024 年新能源汽车行业研究》已启动',
    time: '09:15:33',
    iconName: 'VideoPlay',
    iconColor: '#3b82f6'
  },
  {
    id: 'a3',
    title: '报告任务已完成',
    description: '任务《半导体行业分析报告》已完成',
    time: '昨天 17:42',
    iconName: 'CircleCheck',
    iconColor: '#10b981'
  },
  {
    id: 'a4',
    title: 'RAG 检索执行',
    description: '执行查询"新能源电池技术趋势"，返回 23 条结果',
    time: '昨天 16:08',
    iconName: 'Search',
    iconColor: '#8b5cf6'
  },
  {
    id: 'a5',
    title: '文档入库',
    description: '新增文档《全球光伏产业链报告.pdf》',
    time: '昨天 15:21',
    iconName: 'Document',
    iconColor: '#f59e0b'
  }
]
```

- [ ] **Step 1.3: Commit**

```bash
git add frontend/package.json frontend/src/mock/dashboard.ts
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): add echarts dep and dashboard mock data

- Bump package.json to include echarts ^5.5.0 (user runs npm install in IDE)
- Create mock/dashboard.ts with typed data for stage progress, stat cards,
  core capabilities and recent activity. To be replaced by real Phase 4
  platform aggregation endpoints later.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: StatCard 组件（4 个统计卡）

**Files:**
- Create: `frontend/src/components/stat/StatCard.vue`

- [ ] **Step 2.1: 创建 `frontend/src/components/stat/StatCard.vue`**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import * as ElIcons from '@element-plus/icons-vue'
import type { StatItem } from '@/mock/dashboard'

const props = defineProps<{
  item: StatItem
}>()

// 字符串映射到 Element Plus 图标组件
const iconComponent = computed(() => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const all = ElIcons as any as Record<string, unknown>
  return all[props.item.iconName] ?? ElIcons.Document
})
</script>

<template>
  <div class="stat-card">
    <div class="stat-icon" :style="{ background: item.iconBg }">
      <el-icon :size="22"><component :is="iconComponent" /></el-icon>
    </div>

    <div class="stat-body">
      <div class="stat-label">
        {{ item.label }}
        <el-icon class="stat-info" :size="12"><InfoFilled /></el-icon>
      </div>
      <div class="stat-value">
        {{ item.value }}<span v-if="item.unit" class="stat-unit">{{ item.unit }}</span>
      </div>
      <div class="stat-delta">
        {{ item.delta.note }} <span class="delta-raw">{{ item.delta.raw }}</span>
        <span class="delta-percent" :class="item.delta.trend">({{ item.delta.percent }})</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
// 单独再 import InfoFilled 避免 setup 块顶上的 import 列表过长
import { InfoFilled } from '@element-plus/icons-vue'
export default { components: { InfoFilled } }
</script>

<style scoped>
.stat-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: var(--color-primary);
  flex-shrink: 0;
}

.stat-body {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.stat-info {
  color: var(--text-tertiary);
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin-top: 4px;
  line-height: 1.2;
}

.stat-unit {
  font-size: 14px;
  color: var(--text-secondary);
  margin-left: 4px;
}

.stat-delta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.delta-raw {
  color: var(--text-secondary);
  font-weight: 500;
}

.delta-percent.up {
  color: var(--color-success);
}

.delta-percent.down {
  color: var(--color-danger);
}
</style>
```

> **注意**：上面文件里有两个 `<script>` 块。Vue 3 SFC 是允许的 —— 一个 `<script setup>` 用于 Composition API，另一个普通 `<script>` 用于声明 `components` 注册。如果觉得别扭，可把 `InfoFilled` 的 import 合并到 setup 块（直接写在模板里 `<InfoFilled />`），但 setup 中 import 后**也必须**在模板里能直接用作组件，Vue 编译器会自动注册。所以更简洁的写法是合并到 setup 块即可：

**Step 2.1 修订（推荐写法 —— 单 script 块）**：

```vue
<script setup lang="ts">
import { computed } from 'vue'
import * as ElIcons from '@element-plus/icons-vue'
import { InfoFilled } from '@element-plus/icons-vue'
import type { StatItem } from '@/mock/dashboard'

const props = defineProps<{
  item: StatItem
}>()

const iconComponent = computed(() => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const all = ElIcons as any as Record<string, unknown>
  return all[props.item.iconName] ?? ElIcons.Document
})

// 暴露 InfoFilled 给模板（setup 块里 import 的标识符 vue 会自动当作组件使用）
const _info = InfoFilled
void _info
</script>

<template>
  <div class="stat-card">
    <div class="stat-icon" :style="{ background: item.iconBg }">
      <el-icon :size="22"><component :is="iconComponent" /></el-icon>
    </div>

    <div class="stat-body">
      <div class="stat-label">
        {{ item.label }}
        <el-icon class="stat-info" :size="12"><InfoFilled /></el-icon>
      </div>
      <div class="stat-value">
        {{ item.value }}<span v-if="item.unit" class="stat-unit">{{ item.unit }}</span>
      </div>
      <div class="stat-delta">
        {{ item.delta.note }} <span class="delta-raw">{{ item.delta.raw }}</span>
        <span class="delta-percent" :class="item.delta.trend">({{ item.delta.percent }})</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 上面同 */
.stat-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: var(--color-primary);
  flex-shrink: 0;
}
.stat-body { flex: 1; min-width: 0; }
.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}
.stat-info { color: var(--text-tertiary); }
.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin-top: 4px;
  line-height: 1.2;
}
.stat-unit { font-size: 14px; color: var(--text-secondary); margin-left: 4px; }
.stat-delta { margin-top: 6px; font-size: 12px; color: var(--text-tertiary); }
.delta-raw { color: var(--text-secondary); font-weight: 500; }
.delta-percent.up { color: var(--color-success); }
.delta-percent.down { color: var(--color-danger); }
</style>
```

**实现者应该使用 Step 2.1 修订版（单 script setup 块）**。

- [ ] **Step 2.2: Commit**

```bash
git add frontend/src/components/stat/StatCard.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): StatCard component with icon, value, delta indicator

Renders one of four dashboard stat tiles. Icon resolved by name from
@element-plus/icons-vue. Delta trend (up/down) drives green/red color.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: StageStepBar + StageOverview（两个 7 段进度条）

**Files:**
- Create: `frontend/src/components/stage/StageStepBar.vue`
- Create: `frontend/src/components/stage/StageOverview.vue`

- [ ] **Step 3.1: 创建 `frontend/src/components/stage/StageStepBar.vue`**

顶部"项目阶段概览"水平条：7 个节点 + 三态图例。

```vue
<script setup lang="ts">
import { Check } from '@element-plus/icons-vue'
import type { StageNode } from '@/mock/dashboard'

defineProps<{
  nodes: StageNode[]
}>()
</script>

<template>
  <section class="stage-step-bar">
    <header class="ssb-header">
      <h3 class="ssb-title">项目阶段概览</h3>
      <div class="ssb-legend">
        <span class="legend-item">
          <span class="legend-dot done"><el-icon :size="10"><Check /></el-icon></span>
          已完成
        </span>
        <span class="legend-item">
          <span class="legend-dot current" />
          进行中
        </span>
        <span class="legend-item">
          <span class="legend-dot pending" />
          待开始
        </span>
      </div>
    </header>

    <div class="ssb-track">
      <div
        v-for="(n, idx) in nodes"
        :key="n.index"
        class="ssb-node"
        :class="n.status"
      >
        <div class="ssb-pill">
          <span class="ssb-pill-title">{{ n.title }}</span>
          <span class="ssb-pill-sub">{{ n.subtitle }}</span>
          <span v-if="n.status === 'done'" class="ssb-pill-icon done">
            <el-icon :size="12"><Check /></el-icon>
          </span>
          <span v-else-if="n.status === 'current'" class="ssb-pill-icon current">
            <span class="dot" />
          </span>
        </div>
        <div v-if="idx < nodes.length - 1" class="ssb-connector" :class="n.status" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.stage-step-bar {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px 24px;
}

.ssb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.ssb-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.ssb-legend {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--text-secondary);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.legend-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
}

.legend-dot.done {
  background: var(--color-success);
}

.legend-dot.current {
  background: var(--color-primary);
}

.legend-dot.pending {
  background: var(--bg-muted);
  border: 1px solid var(--border-base);
}

/* Track ===================================================== */
.ssb-track {
  display: flex;
  align-items: center;
}

.ssb-node {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.ssb-pill {
  position: relative;
  flex: 1;
  background: var(--bg-muted);
  border-radius: 999px;
  padding: 10px 32px 10px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.ssb-node.done .ssb-pill,
.ssb-node.current .ssb-pill {
  background: rgba(47, 109, 245, 0.06);
}

.ssb-pill-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}

.ssb-pill-sub {
  font-size: 12px;
  color: var(--text-tertiary);
}

.ssb-node.done .ssb-pill-title,
.ssb-node.current .ssb-pill-title {
  color: var(--color-primary);
}

.ssb-pill-icon {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
}

.ssb-pill-icon.done {
  background: var(--color-success);
}

.ssb-pill-icon.current {
  background: var(--color-primary);
}

.ssb-pill-icon.current .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
}

.ssb-connector {
  width: 14px;
  height: 2px;
  background: var(--border-base);
  flex-shrink: 0;
}

.ssb-connector.done {
  background: var(--color-primary);
}
</style>
```

- [ ] **Step 3.2: 创建 `frontend/src/components/stage/StageOverview.vue`**

下半部分"阶段进度总览"：7 个圆形节点（已完成 ✓ / 当前进行中数字 / 待开始数字）+ 进度文案 + 日期；右侧"当前阶段"卡。

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { Check } from '@element-plus/icons-vue'
import type { CurrentStageInfo, StageDetail } from '@/mock/dashboard'

const props = defineProps<{
  details: StageDetail[]
  current: CurrentStageInfo
  showCurrentCard?: boolean
}>()

const _ = computed(() => props.details.length)
</script>

<template>
  <section class="stage-overview">
    <header class="so-header">
      <h3 class="so-title">阶段进度总览</h3>
      <a class="so-link">查看详情 ›</a>
    </header>

    <div class="so-track">
      <div v-for="(d, idx) in details" :key="d.index" class="so-step">
        <div class="so-node" :class="d.status">
          <el-icon v-if="d.status === 'done'" :size="14"><Check /></el-icon>
          <span v-else>{{ d.index + 1 }}</span>
        </div>
        <div class="so-meta">
          <div class="so-meta-title">{{ d.title }}</div>
          <div class="so-meta-percent">{{ d.statusLabel }}</div>
          <div class="so-meta-date">{{ d.date || '—' }}</div>
        </div>
        <div v-if="idx < details.length - 1" class="so-connector" :class="d.status" />
      </div>
    </div>

    <div v-if="showCurrentCard !== false" class="so-current">
      <div class="so-current-body">
        <div class="so-current-title">
          <span class="so-pill">当前阶段</span>
          <span class="so-current-name">：{{ current.title }}</span>
        </div>
        <p class="so-current-desc">{{ current.description }}</p>
        <div class="so-current-grid">
          <div v-for="b in current.bullets" :key="b" class="so-current-bullet">
            <span class="bullet-dot" /> {{ b }}
          </div>
        </div>
      </div>
      <div class="so-current-illu" aria-hidden="true">
        <!-- 简单占位插画：堆叠的几何图块 -->
        <div class="illu illu-a" />
        <div class="illu illu-b" />
        <div class="illu illu-c" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.stage-overview {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px 24px;
}

.so-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.so-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.so-link {
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
}

.so-track {
  display: flex;
  align-items: flex-start;
  margin-bottom: 24px;
}

.so-step {
  flex: 1;
  display: flex;
  align-items: flex-start;
  position: relative;
  min-width: 0;
}

.so-node {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
  z-index: 1;
}

.so-node.done {
  background: var(--color-primary);
  color: #fff;
}

.so-node.current {
  background: var(--color-primary);
  color: #fff;
  box-shadow: 0 0 0 4px rgba(47, 109, 245, 0.15);
}

.so-node.pending {
  background: var(--bg-muted);
  color: var(--text-tertiary);
  border: 1px solid var(--border-base);
}

.so-meta {
  margin-left: 10px;
  min-width: 0;
}

.so-meta-title {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.so-meta-percent {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.so-meta-date {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.so-connector {
  position: absolute;
  top: 17px;
  left: calc(34px + 8px);
  right: 8px;
  height: 2px;
  background: var(--border-base);
  z-index: 0;
}

.so-connector.done {
  background: var(--color-primary);
}

/* Current stage card =========================================== */
.so-current {
  display: flex;
  gap: 24px;
  align-items: stretch;
  border-radius: var(--radius-lg);
  border: 1px solid rgba(47, 109, 245, 0.2);
  background: rgba(47, 109, 245, 0.04);
  padding: 20px;
}

.so-current-body {
  flex: 1;
  min-width: 0;
}

.so-current-title {
  display: flex;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
}

.so-pill {
  font-size: 12px;
  background: rgba(47, 109, 245, 0.12);
  border-radius: 4px;
  padding: 2px 8px;
}

.so-current-name {
  margin-left: 4px;
}

.so-current-desc {
  margin: 12px 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.so-current-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 24px;
}

.so-current-bullet {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.bullet-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-primary);
  flex-shrink: 0;
}

/* Illustration placeholder ================================== */
.so-current-illu {
  width: 160px;
  height: 110px;
  position: relative;
  flex-shrink: 0;
}

.illu {
  position: absolute;
  border-radius: 6px;
}

.illu-a {
  width: 90px;
  height: 60px;
  background: rgba(47, 109, 245, 0.2);
  bottom: 0;
  left: 0;
}

.illu-b {
  width: 70px;
  height: 90px;
  background: rgba(16, 185, 129, 0.18);
  bottom: 0;
  left: 50px;
}

.illu-c {
  width: 50px;
  height: 50px;
  background: rgba(245, 158, 11, 0.2);
  bottom: 30px;
  right: 0;
}
</style>
```

- [ ] **Step 3.3: Commit**

```bash
git add frontend/src/components/stage/
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): StageStepBar (top 7-step bar) and StageOverview

- StageStepBar: horizontal pill bar with done/current/pending state and
  legend (matches design #1 header)
- StageOverview: numbered circular nodes + percent + date, plus the
  highlighted 'current stage' card with bullet description and placeholder
  illustration (matches design #1 left-middle)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: 快速开始卡片（QuickStartCard）

**Files:**
- Create: `frontend/src/components/home/QuickStartCard.vue`

- [ ] **Step 4.1: 创建 `frontend/src/components/home/QuickStartCard.vue`**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import * as ElIcons from '@element-plus/icons-vue'

const props = defineProps<{
  title: string
  description: string
  iconName: string
  iconBg: string
  to?: string
}>()

const router = useRouter()

const iconComponent = computed(() => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const all = ElIcons as any as Record<string, unknown>
  return all[props.iconName] ?? ElIcons.Search
})

function onClick() {
  if (props.to) router.push(props.to)
}
</script>

<template>
  <button class="qs-card" type="button" :disabled="!to" @click="onClick">
    <div class="qs-icon" :style="{ background: iconBg }">
      <el-icon :size="22"><component :is="iconComponent" /></el-icon>
    </div>
    <div class="qs-body">
      <div class="qs-title">{{ title }}</div>
      <div class="qs-description">{{ description }}</div>
    </div>
  </button>
</template>

<style scoped>
.qs-card {
  appearance: none;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  text-align: center;
  transition: border-color 0.15s, box-shadow 0.15s;
  font: inherit;
}

.qs-card:hover:not(:disabled) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(47, 109, 245, 0.08);
}

.qs-card:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.qs-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  color: var(--color-primary);
}

.qs-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.qs-description {
  font-size: 11px;
  color: var(--text-tertiary);
}
</style>
```

- [ ] **Step 4.2: Commit**

```bash
git add frontend/src/components/home/QuickStartCard.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): QuickStartCard component for home hero CTA tiles

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: CoreCapabilityCard 组件（核心能力卡片）

**Files:**
- Create: `frontend/src/components/home/CoreCapabilityCard.vue`

- [ ] **Step 5.1: 创建 `frontend/src/components/home/CoreCapabilityCard.vue`**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import * as ElIcons from '@element-plus/icons-vue'
import type { CoreCapability } from '@/mock/dashboard'

const props = defineProps<{
  item: CoreCapability
}>()

const router = useRouter()

const iconComponent = computed(() => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const all = ElIcons as any as Record<string, unknown>
  return all[props.item.iconName] ?? ElIcons.Cpu
})

const disabled = computed(() => !props.item.link)

function go() {
  if (disabled.value) return
  router.push(props.item.link)
}
</script>

<template>
  <div class="cap-card" :class="{ disabled }">
    <div class="cap-icon" :style="{ background: item.iconBg }">
      <el-icon :size="22"><component :is="iconComponent" /></el-icon>
    </div>
    <div class="cap-title">{{ item.title }}</div>
    <p class="cap-desc">{{ item.description }}</p>
    <a class="cap-link" @click.prevent="go">
      {{ disabled ? '即将上线' : '立即使用 →' }}
    </a>
  </div>
</template>

<style scoped>
.cap-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cap-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: var(--color-primary);
}

.cap-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.cap-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  margin: 0;
  line-height: 1.5;
  flex: 1;
}

.cap-link {
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
  margin-top: 4px;
  display: inline-block;
}

.cap-card.disabled .cap-link {
  color: var(--text-tertiary);
  cursor: not-allowed;
}
</style>
```

- [ ] **Step 5.2: Commit**

```bash
git add frontend/src/components/home/CoreCapabilityCard.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): CoreCapabilityCard for the 2x2 capability grid on home

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: RecentActivityList 组件

**Files:**
- Create: `frontend/src/components/home/RecentActivityList.vue`

- [ ] **Step 6.1: 创建 `frontend/src/components/home/RecentActivityList.vue`**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import * as ElIcons from '@element-plus/icons-vue'
import type { ActivityItem } from '@/mock/dashboard'

const props = defineProps<{
  activities: ActivityItem[]
}>()

function iconOf(name: string) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const all = ElIcons as any as Record<string, unknown>
  return all[name] ?? ElIcons.Document
}

const _ = computed(() => props.activities.length)
</script>

<template>
  <section class="rec-act">
    <header class="ra-header">
      <h3 class="ra-title">最近活动</h3>
      <a class="ra-link">查看全部 ›</a>
    </header>

    <ul class="ra-list">
      <li v-for="a in activities" :key="a.id" class="ra-item">
        <span class="ra-bullet" :style="{ background: a.iconColor }">
          <el-icon :size="14" style="color: #fff;">
            <component :is="iconOf(a.iconName)" />
          </el-icon>
        </span>
        <div class="ra-body">
          <div class="ra-row">
            <span class="ra-text">{{ a.title }}</span>
            <span class="ra-time">{{ a.time }}</span>
          </div>
          <div class="ra-desc">{{ a.description }}</div>
        </div>
      </li>
    </ul>

    <a class="ra-more">查看更多活动 ›</a>
  </section>
</template>

<style scoped>
.rec-act {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}

.ra-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.ra-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.ra-link,
.ra-more {
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
}

.ra-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.ra-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--border-light);
}

.ra-item:last-child {
  border-bottom: none;
}

.ra-bullet {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.ra-body {
  flex: 1;
  min-width: 0;
}

.ra-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ra-text {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 500;
}

.ra-time {
  font-size: 11px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.ra-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ra-more {
  display: block;
  text-align: center;
  margin-top: 12px;
}
</style>
```

- [ ] **Step 6.2: Commit**

```bash
git add frontend/src/components/home/RecentActivityList.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): RecentActivityList component for home right column

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: HomeView 总装（替换 F0 占位页）

**Files:**
- Modify: `frontend/src/views/HomeView.vue` (full replace)

- [ ] **Step 7.1: 全量替换 `frontend/src/views/HomeView.vue`**

把 F0 创建的 HomeView（仅显示 nickname 的占位）整个换成下面这个完整 dashboard。

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import StatCard from '@/components/stat/StatCard.vue'
import StageStepBar from '@/components/stage/StageStepBar.vue'
import StageOverview from '@/components/stage/StageOverview.vue'
import QuickStartCard from '@/components/home/QuickStartCard.vue'
import CoreCapabilityCard from '@/components/home/CoreCapabilityCard.vue'
import RecentActivityList from '@/components/home/RecentActivityList.vue'
import {
  coreCapabilities,
  currentStage,
  recentActivities,
  stageDetails,
  stageNodes,
  statItems
} from '@/mock/dashboard'

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
</script>

<template>
  <div class="home">
    <!-- Hero row：左侧问候，右侧 QuickStart -->
    <section class="hero-row">
      <div class="hero-greeting">
        <h1 class="greeting">{{ greeting }}，{{ displayName }} 👋</h1>
        <p class="subtitle">
          欢迎使用 行业研报多 Agent 协作平台，助力高效研究与智能分析
        </p>
      </div>

      <div class="hero-quickstart">
        <div class="qs-header">快速开始</div>
        <div class="qs-grid">
          <QuickStartCard
            title="开始检索"
            description="RAG 检索"
            icon-name="Search"
            icon-bg="#dbeafe"
            to="/rag"
          />
          <QuickStartCard
            title="发起报告任务"
            description="新建任务"
            icon-name="EditPen"
            icon-bg="#dcfce7"
          />
          <QuickStartCard
            title="查看 Trace"
            description="追踪链路"
            icon-name="Share"
            icon-bg="#ede9fe"
          />
        </div>
      </div>
    </section>

    <!-- 项目阶段概览 -->
    <StageStepBar :nodes="stageNodes" />

    <!-- 4 统计卡 -->
    <section class="stats-row">
      <StatCard v-for="s in statItems" :key="s.key" :item="s" />
    </section>

    <!-- 两栏：左侧 = 阶段进度总览 + 当前阶段；右侧 = 核心能力 + 最近活动 -->
    <section class="two-col">
      <div class="col-left">
        <StageOverview :details="stageDetails" :current="currentStage" />
      </div>

      <div class="col-right">
        <div class="capability-block">
          <header class="block-header">
            <h3 class="block-title">核心能力</h3>
            <a class="block-link">查看全部 ›</a>
          </header>
          <div class="capability-grid">
            <CoreCapabilityCard
              v-for="c in coreCapabilities"
              :key="c.key"
              :item="c"
            />
          </div>
        </div>

        <RecentActivityList :activities="recentActivities" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Hero ============================================ */
.hero-row {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 20px;
  align-items: stretch;
}

.hero-greeting {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 28px 28px 24px;
}

.greeting {
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 8px;
  color: var(--text-primary);
}

.subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.hero-quickstart {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px;
}

.qs-header {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.qs-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

/* Stats row ======================================= */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

/* Two-col ========================================= */
.two-col {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 20px;
  align-items: start;
}

.col-left {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.col-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.capability-block {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}

.block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.block-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.block-link {
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
}

.capability-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

/* Responsive：1280 以下右栏收掉 */
@media (max-width: 1280px) {
  .hero-row,
  .two-col {
    grid-template-columns: 1fr;
  }
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
```

- [ ] **Step 7.2: Commit**

```bash
git add frontend/src/views/HomeView.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): HomeView dashboard with hero/quickstart/stats/stages/activity

Replaces the F0 placeholder. Pulls all data from mock/dashboard.ts; will
be wired to real Phase 4 platform endpoints later.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: RAG 类型与 API

**Files:**
- Create: `frontend/src/types/rag.ts`
- Create: `frontend/src/api/rag.ts`

- [ ] **Step 8.1: 创建 `frontend/src/types/rag.ts`**

类型严格对齐后端交接手册 §2 阶段 1 接口。

```ts
/** 一个检索命中里的 citation 子结构（与后端 RagSearchResponse.HitData.CitationData 对齐）。 */
export interface CitationData {
  docId: number
  docTitle: string
  source: string
  sectionTitle: string
  pageStart: number
  pageEnd: number
}

/** 单条 hit。 */
export interface HitData {
  chunkId: number
  score: number
  rerankScore: number | null
  content: string
  citation: CitationData
}

/** POST /api/rag/search 的请求体。 */
export interface SearchRequest {
  query: string
  topK?: number       // 1-50，默认 10
  useRerank?: boolean // 默认 true
}

/** POST /api/rag/search 的响应 data 字段。 */
export interface SearchResponse {
  query: string
  tookMs: number
  hits: HitData[]
}

/** 检索配置（前端常量，等 Phase 4 加配置接口后从后端拉）。 */
export const RAG_CONFIG = {
  embeddingModel: 'text-embedding-v3',
  vectorSimilarity: 'cosine',
  bm25Weight: 0.3,
  vectorWeight: 0.7,
  rerankModel: 'gte-rerank-v2'
} as const
```

- [ ] **Step 8.2: 创建 `frontend/src/api/rag.ts`**

```ts
import { apiPost } from './client'
import type { SearchRequest, SearchResponse } from '@/types/rag'

export const ragApi = {
  search(body: SearchRequest) {
    return apiPost<SearchResponse>('/rag/search', body)
  }
}
```

- [ ] **Step 8.3: Commit**

```bash
git add frontend/src/types/rag.ts frontend/src/api/rag.ts
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): RAG types and api.rag.search wrapper

Types align with backend RagSearchResponse (HitData + CitationData).
RAG_CONFIG constants hardcoded for now; Phase 4 will expose a config
endpoint to replace them.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: ECharts 公共工具 + DonutChart 组件

**Files:**
- Create: `frontend/src/utils/echarts.ts`
- Create: `frontend/src/components/chart/DonutChart.vue`

- [ ] **Step 9.1: 创建 `frontend/src/utils/echarts.ts`**

```ts
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components'

/**
 * 一次性注册项目里用到的所有 ECharts 模块。
 * main.ts 不需要 import 这个；由具体使用的图表组件在 onMounted 之前 import 此文件即可。
 */
use([
  CanvasRenderer,
  PieChart,
  BarChart,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent
])

/** 项目调色板（与 variables.css 对齐）。 */
export const chartPalette = [
  '#2f6df5',
  '#10b981',
  '#8b5cf6',
  '#f59e0b',
  '#ef4444',
  '#06b6d4'
]
```

- [ ] **Step 9.2: 创建 `frontend/src/components/chart/DonutChart.vue`**

```vue
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, type ECharts } from 'echarts/core'
import { useResizeObserver } from '@vueuse/core'
import { chartPalette } from '@/utils/echarts'

interface DonutDatum {
  name: string
  value: number
}

const props = defineProps<{
  data: DonutDatum[]
  centerLabel?: string
}>()

const root = ref<HTMLElement>()
let chart: ECharts | null = null

function buildOption(data: DonutDatum[], centerLabel?: string) {
  const total = data.reduce((acc, d) => acc + d.value, 0)
  return {
    color: chartPalette,
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 0,
      top: 'center',
      itemWidth: 8,
      itemHeight: 8,
      icon: 'circle',
      textStyle: { fontSize: 12, color: '#4b5563' },
      formatter: (name: string) => {
        const d = data.find((x) => x.name === name)
        const pct = d && total ? ((d.value / total) * 100).toFixed(1) : '0.0'
        return `${name}  ${d?.value ?? 0} (${pct}%)`
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['55%', '75%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        label: {
          show: !!centerLabel,
          position: 'center',
          formatter: () => `{a|${total}}\n{b|${centerLabel ?? ''}}`,
          rich: {
            a: { fontSize: 22, fontWeight: 600, color: '#1f2937' },
            b: { fontSize: 12, color: '#9ca3af', padding: [4, 0, 0, 0] }
          }
        },
        labelLine: { show: false },
        data
      }
    ]
  }
}

function render() {
  if (!chart) return
  chart.setOption(buildOption(props.data, props.centerLabel), true)
}

onMounted(() => {
  if (!root.value) return
  chart = init(root.value)
  render()
  useResizeObserver(root, () => chart?.resize())
})

watch(
  () => [props.data, props.centerLabel],
  () => render(),
  { deep: true }
)

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="root" class="donut-chart" />
</template>

<style scoped>
.donut-chart {
  width: 100%;
  height: 220px;
}
</style>
```

- [ ] **Step 9.3: Commit**

```bash
git add frontend/src/utils/echarts.ts frontend/src/components/chart/DonutChart.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): ECharts shared setup and DonutChart component

- utils/echarts.ts: on-demand registers Canvas/Pie/Bar + tooltip/legend
- DonutChart.vue: receives {name, value}[], renders donut with vertical
  legend showing value + percent, optional center label

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: BarChart 组件

**Files:**
- Create: `frontend/src/components/chart/BarChart.vue`

- [ ] **Step 10.1: 创建 `frontend/src/components/chart/BarChart.vue`**

```vue
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, type ECharts } from 'echarts/core'
import { useResizeObserver } from '@vueuse/core'
import { chartPalette } from '@/utils/echarts'

interface BarDatum {
  label: string
  value: number
}

const props = defineProps<{
  data: BarDatum[]
  barColor?: string
}>()

const root = ref<HTMLElement>()
let chart: ECharts | null = null

function buildOption(data: BarDatum[], barColor?: string) {
  return {
    color: chartPalette,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 36, right: 12, top: 24, bottom: 28 },
    xAxis: {
      type: 'category',
      data: data.map((d) => d.label),
      axisLabel: { color: '#9ca3af', fontSize: 11 },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#9ca3af', fontSize: 11 },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f1f3f8' } }
    },
    series: [
      {
        type: 'bar',
        data: data.map((d) => d.value),
        itemStyle: {
          color: barColor ?? '#2f6df5',
          borderRadius: [6, 6, 0, 0]
        },
        barMaxWidth: 28,
        label: {
          show: true,
          position: 'top',
          color: '#4b5563',
          fontSize: 11
        }
      }
    ]
  }
}

function render() {
  if (!chart) return
  chart.setOption(buildOption(props.data, props.barColor), true)
}

onMounted(() => {
  if (!root.value) return
  chart = init(root.value)
  render()
  useResizeObserver(root, () => chart?.resize())
})

watch(
  () => [props.data, props.barColor],
  () => render(),
  { deep: true }
)

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="root" class="bar-chart" />
</template>

<style scoped>
.bar-chart {
  width: 100%;
  height: 180px;
}
</style>
```

- [ ] **Step 10.2: Commit**

```bash
git add frontend/src/components/chart/BarChart.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): BarChart component (categorical bar with value labels)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: HitResultCard 组件

**Files:**
- Create: `frontend/src/components/rag/HitResultCard.vue`

- [ ] **Step 11.1: 创建 `frontend/src/components/rag/HitResultCard.vue`**

```vue
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { HitData } from '@/types/rag'

const props = defineProps<{
  index: number          // 1-based ordinal (1, 2, 3 ...)
  hit: HitData
  active?: boolean
}>()

defineEmits<{
  (e: 'select', hit: HitData): void
}>()

const expanded = ref(false)

const scoreText = computed(() => props.hit.score.toFixed(4))
const rerankText = computed(() =>
  props.hit.rerankScore == null ? '—' : props.hit.rerankScore.toFixed(4)
)
</script>

<template>
  <article
    class="hit-card"
    :class="{ active }"
    @click="$emit('select', hit)"
  >
    <div class="hit-row-top">
      <span class="hit-index">{{ index }}</span>

      <div class="hit-scores">
        <div class="score-block">
          <div class="score-label">Chunk Score</div>
          <div class="score-value chunk">{{ scoreText }}</div>
        </div>
        <div class="score-block">
          <div class="score-label">Rerank Score</div>
          <div class="score-value rerank">{{ rerankText }}</div>
        </div>
      </div>

      <div class="hit-content" :class="{ expanded }">
        <p class="hit-text">{{ hit.content }}</p>
        <a class="hit-toggle" @click.stop="expanded = !expanded">
          {{ expanded ? '收起 ‹' : '展开 ›' }}
        </a>
      </div>
    </div>

    <div class="hit-meta-grid">
      <div class="meta-cell">
        <div class="meta-key">docTitle</div>
        <div class="meta-val" :title="hit.citation.docTitle">{{ hit.citation.docTitle }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">source</div>
        <div class="meta-val">{{ hit.citation.source }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">sectionTitle</div>
        <div class="meta-val" :title="hit.citation.sectionTitle">{{ hit.citation.sectionTitle }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">pageStart</div>
        <div class="meta-val">{{ hit.citation.pageStart }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">pageEnd</div>
        <div class="meta-val">{{ hit.citation.pageEnd }}</div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.hit-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
  padding: 16px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.hit-card:hover,
.hit-card.active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(47, 109, 245, 0.08);
}

.hit-row-top {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.hit-index {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.hit-scores {
  display: grid;
  grid-template-columns: repeat(2, max-content);
  gap: 0 16px;
  flex-shrink: 0;
}

.score-label {
  font-size: 11px;
  color: var(--text-tertiary);
}

.score-value {
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.score-value.chunk {
  color: var(--color-primary);
}

.score-value.rerank {
  color: var(--color-success);
}

.hit-content {
  flex: 1;
  min-width: 0;
}

.hit-text {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hit-content.expanded .hit-text {
  -webkit-line-clamp: unset;
  overflow: visible;
}

.hit-toggle {
  display: inline-block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
}

.hit-meta-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-light);
}

.meta-key {
  font-size: 11px;
  color: var(--text-tertiary);
}

.meta-val {
  font-size: 13px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
```

- [ ] **Step 11.2: Commit**

```bash
git add frontend/src/components/rag/HitResultCard.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): HitResultCard with scores, expandable content, meta grid

Emits 'select' so parent can drive citation panel state. Two-line clamp
with expand/collapse toggle for long chunk text.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: CitationPanel 组件

**Files:**
- Create: `frontend/src/components/rag/CitationPanel.vue`

- [ ] **Step 12.1: 创建 `frontend/src/components/rag/CitationPanel.vue`**

右栏：当前选中 hit 详情 + 来源饼图 + Score 分布柱图 + 检索配置。

```vue
<script setup lang="ts">
import { computed } from 'vue'
import DonutChart from '@/components/chart/DonutChart.vue'
import BarChart from '@/components/chart/BarChart.vue'
import { RAG_CONFIG, type HitData } from '@/types/rag'

const props = defineProps<{
  activeHit: HitData | null
  activeIndex: number              // 1-based; 0 when no active
  allHits: HitData[]
}>()

// 来源分布：按 citation.source 聚合所有 hits
const sourceData = computed(() => {
  const map = new Map<string, number>()
  for (const h of props.allHits) {
    const k = h.citation.source || '未知'
    map.set(k, (map.get(k) ?? 0) + 1)
  }
  return Array.from(map.entries()).map(([name, value]) => ({ name, value }))
})

// Score 分布：将 hits[].score 落到 5 个桶
const scoreBuckets = computed(() => {
  const bins = [
    { label: '0-0.2', value: 0 },
    { label: '0.2-0.4', value: 0 },
    { label: '0.4-0.6', value: 0 },
    { label: '0.6-0.8', value: 0 },
    { label: '0.8-1.0', value: 0 }
  ]
  for (const h of props.allHits) {
    const idx = Math.min(Math.floor(h.score * 5), 4)
    bins[idx].value++
  }
  return bins
})

const score = computed(() => {
  const h = props.activeHit
  if (!h) return null
  return h.rerankScore != null
    ? h.rerankScore.toFixed(4)
    : h.score.toFixed(4)
})
</script>

<template>
  <aside class="cit-panel">
    <!-- 引用信息 -->
    <section class="cp-section">
      <header class="cp-section-header">
        <h3 class="cp-section-title">引用信息</h3>
      </header>
      <div v-if="activeHit" class="cp-detail">
        <div class="cp-detail-head">
          <span class="cp-index">{{ activeIndex }}</span>
          <span class="cp-doc-title" :title="activeHit.citation.docTitle">
            {{ activeHit.citation.docTitle }}
          </span>
          <span class="cp-score">{{ score }}</span>
        </div>
        <div class="cp-detail-grid">
          <div class="cp-row"><span class="cp-k">sectionTitle</span><span class="cp-v">{{ activeHit.citation.sectionTitle }}</span></div>
          <div class="cp-row"><span class="cp-k">pageStart</span><span class="cp-v">{{ activeHit.citation.pageStart }}</span></div>
          <div class="cp-row"><span class="cp-k">pageEnd</span><span class="cp-v">{{ activeHit.citation.pageEnd }}</span></div>
          <div class="cp-row"><span class="cp-k">source</span><span class="cp-v">{{ activeHit.citation.source }}</span></div>
          <div class="cp-row"><span class="cp-k">chunkId</span><span class="cp-v">{{ activeHit.chunkId }}</span></div>
          <div class="cp-row"><span class="cp-k">docId</span><span class="cp-v">{{ activeHit.citation.docId }}</span></div>
        </div>
      </div>
      <div v-else class="cp-empty">点击左侧任一结果查看引用详情</div>
    </section>

    <!-- 来源分布 -->
    <section class="cp-section">
      <header class="cp-section-header">
        <h3 class="cp-section-title">来源分布</h3>
      </header>
      <DonutChart v-if="sourceData.length > 0" :data="sourceData" center-label="总命中" />
      <div v-else class="cp-empty">检索一次即可看到分布</div>
    </section>

    <!-- Score 分布 -->
    <section class="cp-section">
      <header class="cp-section-header">
        <h3 class="cp-section-title">Score 分布</h3>
      </header>
      <BarChart :data="scoreBuckets" />
    </section>

    <!-- 检索配置 -->
    <section class="cp-section">
      <header class="cp-section-header">
        <h3 class="cp-section-title">检索配置</h3>
      </header>
      <div class="cp-config">
        <div class="cp-cfg-row"><span class="cp-k">向量模型</span><span class="cp-v">{{ RAG_CONFIG.embeddingModel }}</span></div>
        <div class="cp-cfg-row"><span class="cp-k">向量相似度</span><span class="cp-v">{{ RAG_CONFIG.vectorSimilarity }}</span></div>
        <div class="cp-cfg-row"><span class="cp-k">BM25 权重</span><span class="cp-v">{{ RAG_CONFIG.bm25Weight }}</span></div>
        <div class="cp-cfg-row"><span class="cp-k">向量权重</span><span class="cp-v">{{ RAG_CONFIG.vectorWeight }}</span></div>
        <div class="cp-cfg-row"><span class="cp-k">Rerank 模型</span><span class="cp-v">{{ RAG_CONFIG.rerankModel }}</span></div>
      </div>
    </section>
  </aside>
</template>

<style scoped>
.cit-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cp-section {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px;
}

.cp-section-header {
  margin-bottom: 12px;
}

.cp-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.cp-detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.cp-index {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  background: var(--color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.cp-doc-title {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cp-score {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-success);
  font-variant-numeric: tabular-nums;
}

.cp-detail-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cp-row,
.cp-cfg-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  border-bottom: 1px dashed var(--border-light);
  padding-bottom: 6px;
}

.cp-row:last-child,
.cp-cfg-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.cp-k {
  color: var(--text-tertiary);
}

.cp-v {
  color: var(--text-secondary);
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
}

.cp-empty {
  color: var(--text-tertiary);
  font-size: 12px;
  text-align: center;
  padding: 16px 0;
}

.cp-config {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
```

- [ ] **Step 12.2: Commit**

```bash
git add frontend/src/components/rag/CitationPanel.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): CitationPanel showing active hit details, source donut,
score histogram and retrieval config

Source/score charts aggregated per-search from hits returned by current
query. Config block reads from hardcoded RAG_CONFIG; will be wired to
a future config endpoint in Phase 4.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: RagSearchView + 路由 + 启用菜单

**Files:**
- Create: `frontend/src/views/rag/RagSearchView.vue`
- Modify: `frontend/src/router/index.ts` (add `/rag` child route)
- Modify: `frontend/src/layouts/DefaultLayout.vue` (enable `rag` menu + activeMenu match)

- [ ] **Step 13.1: 创建 `frontend/src/views/rag/RagSearchView.vue`**

```vue
<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import HitResultCard from '@/components/rag/HitResultCard.vue'
import CitationPanel from '@/components/rag/CitationPanel.vue'
import { ragApi } from '@/api/rag'
import type { HitData, SearchResponse } from '@/types/rag'

const query = ref('')
const topK = ref(10)
const useRerank = ref(true)

const loading = ref(false)
const lastResp = ref<SearchResponse | null>(null)
const activeHit = ref<HitData | null>(null)

const hits = computed(() => lastResp.value?.hits ?? [])
const tookMs = computed(() => lastResp.value?.tookMs ?? 0)

const activeIndex = computed(() => {
  if (!activeHit.value) return 0
  const i = hits.value.findIndex((h) => h.chunkId === activeHit.value!.chunkId)
  return i >= 0 ? i + 1 : 0
})

async function onSearch() {
  if (!query.value.trim()) {
    ElMessage.warning('请输入查询内容')
    return
  }
  loading.value = true
  try {
    const resp = await ragApi.search({
      query: query.value.trim(),
      topK: topK.value,
      useRerank: useRerank.value
    })
    lastResp.value = resp
    activeHit.value = resp.hits[0] ?? null
  } catch {
    /* ElMessage already shown by interceptor */
  } finally {
    loading.value = false
  }
}

function onSelect(h: HitData) {
  activeHit.value = h
}
</script>

<template>
  <div class="rag-search">
    <header class="rs-title-row">
      <h1 class="rs-title">Hybrid Search 检索</h1>
      <p class="rs-subtitle">
        基于向量检索 + BM25 关键词检索 + Rerank 的混合检索，快速定位知识库中的高相关内容。
      </p>
    </header>

    <!-- 顶部搜索表单 -->
    <section class="rs-form">
      <el-input
        v-model="query"
        placeholder="输入研究主题，如：新能源电池技术趋势与产业链变化"
        class="rs-query"
        clearable
        @keyup.enter="onSearch"
      />
      <div class="rs-form-field">
        <span class="rs-label">TopK</span>
        <el-select v-model="topK" style="width: 96px;">
          <el-option v-for="n in [5, 10, 20, 30, 50]" :key="n" :label="String(n)" :value="n" />
        </el-select>
      </div>
      <div class="rs-form-field">
        <span class="rs-label">启用 Rerank</span>
        <el-switch v-model="useRerank" />
      </div>
      <el-button type="primary" :loading="loading" @click="onSearch">开始检索</el-button>
    </section>

    <!-- 三统计卡 -->
    <section v-if="lastResp" class="rs-stats">
      <div class="rs-stat-card">
        <div class="rs-stat-label">耗时 tookMs</div>
        <div class="rs-stat-value">{{ tookMs }} <span class="rs-stat-unit">ms</span></div>
      </div>
      <div class="rs-stat-card">
        <div class="rs-stat-label">命中条数</div>
        <div class="rs-stat-value">{{ hits.length }}</div>
      </div>
      <div class="rs-stat-card">
        <div class="rs-stat-label">Rerank 状态</div>
        <div class="rs-stat-value">{{ useRerank ? '已开启' : '未开启' }}</div>
      </div>
    </section>

    <!-- 主区：左侧结果列表 + 右侧引用面板 -->
    <section class="rs-main">
      <div class="rs-results">
        <header v-if="lastResp" class="rs-results-header">
          <h2 class="rs-results-title">检索结果</h2>
          <span class="rs-results-meta">共 {{ hits.length }} 条结果（TopK={{ topK }}）</span>
        </header>

        <div v-if="loading" class="rs-empty">检索中…</div>
        <div v-else-if="!lastResp" class="rs-empty">输入查询并点击"开始检索"</div>
        <div v-else-if="hits.length === 0" class="rs-empty">未命中任何结果</div>

        <HitResultCard
          v-for="(h, idx) in hits"
          :key="h.chunkId"
          :index="idx + 1"
          :hit="h"
          :active="activeHit?.chunkId === h.chunkId"
          @select="onSelect"
        />
      </div>

      <div class="rs-side">
        <CitationPanel :active-hit="activeHit" :active-index="activeIndex" :all-hits="hits" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.rag-search {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rs-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.rs-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

/* Form ============================================ */
.rs-form {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.rs-query {
  flex: 1;
  min-width: 240px;
}

.rs-form-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rs-label {
  font-size: 12px;
  color: var(--text-secondary);
}

/* Stats =========================================== */
.rs-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.rs-stat-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px 20px;
}

.rs-stat-label {
  font-size: 12px;
  color: var(--text-tertiary);
}

.rs-stat-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.rs-stat-unit {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: 4px;
}

/* Main ============================================ */
.rs-main {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 16px;
  align-items: start;
}

.rs-results {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px;
  min-width: 0;
}

.rs-results-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.rs-results-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.rs-results-meta {
  font-size: 12px;
  color: var(--text-tertiary);
}

.rs-empty {
  padding: 32px 16px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

@media (max-width: 1280px) {
  .rs-main {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [ ] **Step 13.2: 修改 `frontend/src/router/index.ts`**

在 `/` 子路由的 `children` 数组中，紧跟 home 路由**之后**新增 `/rag` 子路由。完整修改后的文件内容如下（粘贴整文件覆盖）：

```ts
import {
  createRouter,
  createWebHistory,
  type RouteLocationNormalized,
  type RouteRecordRaw
} from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { requiresAuth: false, title: '注册' }
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { requiresAuth: true, title: '首页' }
      },
      {
        path: 'rag',
        name: 'rag-search',
        component: () => import('@/views/rag/RagSearchView.vue'),
        meta: { requiresAuth: true, title: 'RAG 检索' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to: RouteLocationNormalized) => {
  const auth = useAuthStore()
  const requiresAuth = to.matched.some((r) => r.meta.requiresAuth)

  if (requiresAuth && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if ((to.name === 'login' || to.name === 'register') && auth.isLoggedIn) {
    return { path: '/' }
  }

  const title = to.meta.title as string | undefined
  if (title) {
    document.title = `${title} · 行业研报多 Agent 协作平台`
  }
  return true
})

export default router
```

- [ ] **Step 13.3: 修改 `frontend/src/layouts/DefaultLayout.vue`**

只改两处：
1. `menus` 数组里 `rag` 那一项：把 `disabled: true` 换成 `path: '/rag'`（去掉 disabled）
2. `activeMenu` computed 增加 `/rag` 的映射

定位精确：用 `Edit` 工具，old_string 和 new_string 严格对齐下面两段。

**改动 1：** menus 数组中 rag 那一项

old_string:
```
  { key: 'rag', label: 'RAG 检索', icon: Search, disabled: true },
```

new_string:
```
  { key: 'rag', label: 'RAG 检索', icon: Search, path: '/rag' },
```

**改动 2：** activeMenu computed

old_string:
```
const activeMenu = computed(() => {
  if (route.path === '/') return 'home'
  return ''
})
```

new_string:
```
const activeMenu = computed(() => {
  if (route.path === '/') return 'home'
  if (route.path.startsWith('/rag')) return 'rag'
  return ''
})
```

- [ ] **Step 13.4: Commit**

```bash
git add frontend/src/views/rag/RagSearchView.vue frontend/src/router/index.ts frontend/src/layouts/DefaultLayout.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): RagSearchView with full hybrid-search flow + enable nav

- New view: query/topK/rerank form, three stat cards, results list with
  active-hit selection, citation panel on the right
- Router: add /rag child route under DefaultLayout
- DefaultLayout: enable 'RAG 检索' menu (drop disabled, set path),
  extend activeMenu to highlight /rag*

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase F1 出口验证（**由用户在 IDE 完成**）

> ⚠️ Agent 不要跑这些。

- [ ] **V1: 安装新依赖 + 启动**

```bash
cd frontend
npm install        # 装 echarts，约几十秒
npm run dev        # http://127.0.0.1:5173/
```

后端：用户在 IDE 启动 Spring Boot 应用，并确认 PGVector 已经有数据（即至少跑过一次 `/api/rag/ingest`）。若 RAG 库空，检索会返回 `hits: []`，前端会显示"未命中任何结果"。

- [ ] **V2: 首页 dashboard 视觉对照**

登录 → 看 `/`。对照设计图 #1，确认：
- 顶部问候 + 右侧 3 个快速开始卡（"开始检索"高亮可点）
- 7 段水平阶段条（前 3 段绿勾，第 4 段蓝点进行中，后 3 段灰色）
- 4 统计卡（带 ↑↓ 趋势颜色）
- 左下"阶段进度总览" + "当前阶段：阶段 3 - 报告生成" + 占位插画
- 右上"核心能力"2x2 网格（RAG 检索可点，其余三张"即将上线"）
- 右下"最近活动" 5 条

- [ ] **V3: 路由 + 导航**

点首页"开始检索"卡 → 跳 `/rag`；侧栏"RAG 检索"高亮。点侧栏"首页"回到 `/`。F5 在 `/rag` 刷新仍保留页面。

- [ ] **V4: RAG 检索流**

在 `/rag` 输入查询（如"新能源电池技术趋势"），TopK 选 10，启用 Rerank，点"开始检索"：
- 顶部三统计卡出现真实 tookMs / 命中条数 / Rerank 状态
- 中央"检索结果"列出 hits（Chunk Score + Rerank Score + 2 行折叠 content + 5 字段 meta）
- 点任意 hit 卡 → 右栏"引用信息"刷新到该 hit；卡片高亮蓝边
- 右栏"来源分布"饼图按 hits[].citation.source 聚合
- 右栏"Score 分布"柱图按分数桶
- "检索配置"显示硬编码 5 行

- [ ] **V5: Empty state**

清空查询输入（或第一次进 `/rag` 还没搜过）：列表显示"输入查询并点击'开始检索'"；右栏"来源分布"显示"检索一次即可看到分布"。

- [ ] **V6: 错误态**

故意把 token 改坏 → 触发拦截器 → 自动跳登录；登录回来 `/rag` 历史状态丢失（这是预期，本期不做检索状态持久化）。

通过即为 Phase F1 验收完成。

---

## Self-Review（已执行）

**1. Spec coverage vs `docs/superpowers/specs/2026-05-20-frontend-mvp-design.md` §6 Phase F1**

| Spec 子项 | 覆盖 Task |
|---|---|
| T1.1 HomeView 顶部问候 + 快速入口 + 6 阶段进度 | Task 7（HomeView 总装）+ Task 3（StageStepBar）+ Task 4（QuickStartCard）|
| T1.2 StatCard × 4 | Task 2 |
| T1.3 阶段进度总览 + 当前阶段 + 核心能力 + 最近活动 | Task 3（StageOverview）+ Task 5（CoreCapabilityCard）+ Task 6（RecentActivityList）+ Task 7（总装）|
| T1.4 RagSearchView 上半 + 三统计卡 | Task 13 |
| T1.5 HitResultCard | Task 11 |
| T1.6 CitationPanel + 饼图 + 柱图 + 检索配置 | Task 12 + Task 9 + Task 10 |
| T1.7 视觉打磨 + Loading/Empty/Error 三态 | Task 7 + Task 13（empty / loading / error 已在 view 内处理）|

全部覆盖。额外补了 Task 1（mock 数据基础）和 Task 8（RAG 类型 + api），这两个是 spec 隐含但未单列的支撑层。

**2. Placeholder scan：** 无 TBD / TODO / "implement later"。所有代码块都是完整可运行的代码。两处 `// eslint-disable` 是合理的针对动态图标查找的局部豁免，不是逃避实现。

**3. Type consistency：**
- `StatItem` / `StageNode` / `StageDetail` / `CurrentStageInfo` / `CoreCapability` / `ActivityItem` 全部在 Task 1 (`mock/dashboard.ts`) 定义，Task 2/3/5/6/7 引用一致
- `HitData` / `CitationData` / `SearchRequest` / `SearchResponse` / `RAG_CONFIG` 全部在 Task 8 (`types/rag.ts`) 定义，Task 11/12/13 引用一致
- `ragApi.search(SearchRequest): Promise<SearchResponse>` 在 Task 8 定义，Task 13 调用签名对齐
- DonutChart 期望 `{ name, value }[]`（Task 9）；CitationPanel 在 Task 12 构造 source 聚合时 `{ name, value }` 形状一致
- BarChart 期望 `{ label, value }[]`（Task 10）；CitationPanel 构造 scoreBuckets 同样是 `{ label, value }`
- HitResultCard 在 Task 11 emit `select: HitData`；RagSearchView 在 Task 13 监听 `@select="onSelect"` 签名一致

无类型漂移。
