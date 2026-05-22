# Phase F2 · 研报 Agent UI（SSE + Markdown + Trace）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现设计图 #3 —— 用户提交研究主题 → 立即跳详情页 → 左侧 SSE 实时流（节点状态 / 工具调用 / token 流式 token）+ 右侧 Markdown 流式渲染（含可点击 `[n]` 引用与参考资料卡片）+ 底部 Trace 时间轴表格（LLM_CALL / TOOL_CALL 每行 tokens / latency / 可展开 JSON）。这是**项目核心 demo 页**，直接关联简历亮点。

**Architecture:** 沿用 F0/F1 已搭好的骨架。F2 新增：① `@microsoft/fetch-event-source` 实现支持 Header 鉴权的 SSE 消费；② `markdown-it` + `highlight.js` 渲染流式 Markdown，自定义 `[n]` rule 让引用可点击；③ `composables/useSse.ts` 封装 fetch-event-source 生命周期；④ 路由新增 `/report/submit` + `/report/:id`，DefaultLayout 启用"研究报告"菜单；⑤ HomeView 的"发起报告任务"快捷卡接通。

**Tech Stack:** 在 F0/F1 基础上新增 `@microsoft/fetch-event-source` ^2、`markdown-it` ^14、`@types/markdown-it` ^14、`highlight.js` ^11。

---

## 重要约定（所有 Task 必读）

> **承接 F0/F1 红线**：
> - Agent **绝不**跑 `npm install` / `npm run dev` / `mvn` / `curl`
> - Agent 写完代码即 commit；用户在 IDE 安装新依赖、启动后端、浏览器验收
> - 不写单测（spec §7 锁定 MVP 无单测）
> - 用 `Write` 创建、`Edit` 修改 F0/F1 已存在文件
>
> **F2 特有约定**：
> - 4 个新运行时依赖（fetch-event-source / markdown-it / @types/markdown-it / highlight.js）—— 第一个 Task 改 `frontend/package.json`，用户在 IDE 跑一次 `npm install`
> - SSE 要直连**真后端**，需要 Spring Boot 应用启动 + RAG 库已入库 + Agent workflow 配置就绪。本期的 IDE 验收依赖**全链路**可用
> - **token 流式渲染节流到 60fps**（约 16ms），避免每个 token 都 `markdown-it.render()` 卡顿。具体实现见 Task 5。
> - **重新运行** 实际上是 `POST /api/report/start` 创建一个新任务并跳转到新 ID（后端不支持 in-place 重跑）

---

## File Structure

### 本 Phase 新增文件

```
frontend/src/
├── types/
│   └── report.ts                      # 任务 + Trace + SSE 事件类型
├── api/
│   └── report.ts                      # start / get / trace 三个方法
├── composables/
│   └── useSse.ts                      # @microsoft/fetch-event-source 封装
├── utils/
│   ├── format.ts                      # formatDuration / formatEpochMillis
│   └── citation.ts                    # markdown-it [n] 引用 rule
├── components/
│   └── report/
│       ├── ReportTaskHeader.vue       # 顶部 meta 卡 + 操作按钮（图3 顶部）
│       ├── SseEventList.vue           # 左栏 SSE 实时流（图3 左）
│       ├── MarkdownRenderer.vue       # 右栏 markdown 渲染（图3 右）
│       ├── CitationGrid.vue           # 参考资料卡片网格（图3 右下）
│       └── TraceTable.vue             # Trace 时间轴表格（图3 底部）
└── views/
    └── report/
        ├── ReportSubmitView.vue       # 提交主题 → start → 跳详情
        └── ReportDetailView.vue       # 集成 SSE + Markdown + Trace
```

### F0/F1 已存在、F2 修改的文件

- `frontend/package.json` —— 追加 4 个 dep
- `frontend/src/router/index.ts` —— 加 `/report/submit` 与 `/report/:id` 两条子路由
- `frontend/src/layouts/DefaultLayout.vue` —— 启用"研究报告"菜单 + 扩展 activeMenu 匹配 `/report*`
- `frontend/src/views/HomeView.vue` —— "发起报告任务"快捷卡的 `to` 接通到 `/report/submit`

---

## Task 1: 追加 SSE / markdown / 高亮 依赖

**Files:**
- Modify: `frontend/package.json`

- [ ] **Step 1.1: 修改 `frontend/package.json` `dependencies` 段**

把现在的 dependencies 段（来自 F1 Task 1 后）改成下面的样子（按字母序新增 4 行，**不动其他字段**）：

```json
  "dependencies": {
    "@element-plus/icons-vue": "^2.3.1",
    "@microsoft/fetch-event-source": "^2.0.1",
    "@vueuse/core": "^10.11.0",
    "axios": "^1.7.2",
    "echarts": "^5.5.0",
    "element-plus": "^2.7.6",
    "highlight.js": "^11.10.0",
    "markdown-it": "^14.1.0",
    "pinia": "^2.1.7",
    "vue": "^3.4.31",
    "vue-router": "^4.4.0"
  },
```

并把 devDependencies 段里增加 `"@types/markdown-it": "^14.1.1",`（按字母序插入到 `@types/node` 之前）。修改后 devDeps 头部应该是：

```json
  "devDependencies": {
    "@types/markdown-it": "^14.1.1",
    "@types/node": "^20.14.10",
    ...
```

- [ ] **Step 1.2: Commit**

```bash
git add frontend/package.json
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): add SSE / markdown / highlight deps for Phase F2

- @microsoft/fetch-event-source for SSE with Authorization header
- markdown-it + @types/markdown-it for streaming markdown rendering
- highlight.js for code blocks and Trace JSON syntax coloring

User runs npm install in IDE.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Report 类型定义

**Files:**
- Create: `frontend/src/types/report.ts`

- [ ] **Step 2.1: 创建 `frontend/src/types/report.ts`**

```ts
import type { CitationData } from './rag'

/** 任务生命周期状态（对齐后端 ReportTask.status）。 */
export type ReportTaskStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

/** POST /api/report/start 请求。 */
export interface ReportStartRequest {
  topic: string
  workflow?: string
}

/** POST /api/report/start 响应 data。 */
export interface ReportStartResponse {
  taskId: number
  status: ReportTaskStatus
  streamUrl: string
}

/** GET /api/report/{id} 响应 data。 */
export interface ReportDetail {
  taskId: number
  status: ReportTaskStatus
  topic: string
  finalMarkdown: string | null
  citations: CitationData[]
  errorMessage: string | null
  startedAtEpochMillis: number | null
  finishedAtEpochMillis: number | null
}

/** GET /api/report/{id}/trace 单行（对齐后端 WorkflowNodeRun）。 */
export interface TraceRow {
  id: number
  stepSeq: number
  nodeId: string
  agentRole: string
  stepType: 'LLM_CALL' | 'TOOL_CALL'
  promptVersion: string | null
  model: string | null
  toolName: string | null
  tokensIn: number | null
  tokensOut: number | null
  latencyMs: number | null
  status: 'OK' | 'ERROR'
  errorMessage: string | null
  inputJsonPreview: string | null
  outputJsonPreview: string | null
}

// ====== SSE 事件 ======

export type NodeRunStatus = 'RUNNING' | 'DONE' | 'FAILED'

export interface NodeStatusEvent {
  nodeId: string
  status: NodeRunStatus
}

export interface ToolEvent {
  toolName: string
  paramsJson?: string
  resultPreview?: string
}

export interface TokenEvent {
  delta: string
}

export interface DoneEvent {
  finalMarkdown: string
  citations: CitationData[]
}

export interface ErrorEvent {
  message: string
}

export interface PingEvent {
  ts: number
}

/** 前端聚合的"已发生事件"结构，用于 SseEventList 渲染。 */
export type SseEvent =
  | { type: 'node_status'; ts: number; data: NodeStatusEvent }
  | { type: 'tool';        ts: number; data: ToolEvent }
  | { type: 'token';       ts: number; data: TokenEvent }
  | { type: 'done';        ts: number; data: DoneEvent }
  | { type: 'error';       ts: number; data: ErrorEvent }
  | { type: 'ping';        ts: number; data: PingEvent }
```

- [ ] **Step 2.2: Commit**

```bash
git add frontend/src/types/report.ts
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): Report task / Trace / SSE event types

Aligned with backend ReportTask, WorkflowNodeRun and the 6 SSE event
types (node_status / tool / token / done / error / ping). SseEvent is
a discriminated union for the frontend event log.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Report API（start / get / trace）

**Files:**
- Create: `frontend/src/api/report.ts`

- [ ] **Step 3.1: 创建 `frontend/src/api/report.ts`**

```ts
import { apiGet, apiPost } from './client'
import type {
  ReportDetail,
  ReportStartRequest,
  ReportStartResponse,
  TraceRow
} from '@/types/report'

export const reportApi = {
  start(body: ReportStartRequest) {
    return apiPost<ReportStartResponse>('/report/start', body)
  },
  get(id: number) {
    return apiGet<ReportDetail>(`/report/${id}`)
  },
  trace(id: number) {
    return apiGet<TraceRow[]>(`/report/${id}/trace`)
  }
}

/** SSE 端点不走 axios（要保留长连接），直接拼绝对 URL；交给 useSse 自己 fetch。 */
export function buildStreamUrl(id: number): string {
  const base = import.meta.env.VITE_API_BASE || '/api'
  return `${base}/report/${id}/stream`
}
```

- [ ] **Step 3.2: Commit**

```bash
git add frontend/src/api/report.ts
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): report API methods (start/get/trace) + buildStreamUrl

SSE stream URL is intentionally built separately because useSse uses
fetch-event-source directly with its own AbortController, not the
shared axios instance.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: useSse composable

**Files:**
- Create: `frontend/src/composables/useSse.ts`

- [ ] **Step 4.1: 创建 `frontend/src/composables/useSse.ts`**

```ts
import { onBeforeUnmount } from 'vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { useAuthStore } from '@/stores/auth'
import type {
  DoneEvent,
  ErrorEvent,
  NodeStatusEvent,
  PingEvent,
  TokenEvent,
  ToolEvent
} from '@/types/report'

export interface SseHandlers {
  onNodeStatus?: (data: NodeStatusEvent) => void
  onTool?: (data: ToolEvent) => void
  onToken?: (data: TokenEvent) => void
  onDone?: (data: DoneEvent) => void
  onError?: (data: ErrorEvent) => void
  onPing?: (data: PingEvent) => void
  /** 网络层错误 / 连接关闭。 */
  onClose?: (reason: 'done' | 'error' | 'manual') => void
}

export interface SseHandle {
  close: () => void
}

/**
 * 用 @microsoft/fetch-event-source 消费支持 Authorization Header 的 SSE。
 * 浏览器原生 EventSource 不支持自定义 Header，因此用 fetch-event-source 替代。
 *
 * @param url     拼好的完整 SSE URL（例如 /api/report/123/stream）
 * @param handlers 5 种业务事件 + ping + close 的回调
 */
export function useSse(url: string, handlers: SseHandlers): SseHandle {
  const auth = useAuthStore()
  const ctrl = new AbortController()
  let closed = false

  function close(reason: 'done' | 'error' | 'manual' = 'manual') {
    if (closed) return
    closed = true
    ctrl.abort()
    handlers.onClose?.(reason)
  }

  fetchEventSource(url, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${auth.token ?? ''}`,
      Accept: 'text/event-stream'
    },
    signal: ctrl.signal,
    // 切走 tab 时不暂停（任务可能还在跑，回来想看到补帧）
    openWhenHidden: true,
    onopen: async (resp) => {
      if (!resp.ok) {
        // 401 / 403 由 onerror 路径走，这里抛出让 fetch-event-source 触发 onerror
        throw new Error(`SSE open failed: ${resp.status}`)
      }
    },
    onmessage: (ev) => {
      if (!ev.event) return
      let data: unknown = {}
      try {
        data = ev.data ? JSON.parse(ev.data) : {}
      } catch {
        return
      }
      switch (ev.event) {
        case 'node_status':
          handlers.onNodeStatus?.(data as NodeStatusEvent)
          break
        case 'tool':
          handlers.onTool?.(data as ToolEvent)
          break
        case 'token':
          handlers.onToken?.(data as TokenEvent)
          break
        case 'done':
          handlers.onDone?.(data as DoneEvent)
          close('done')
          break
        case 'error':
          handlers.onError?.(data as ErrorEvent)
          close('error')
          break
        case 'ping':
          handlers.onPing?.(data as PingEvent)
          break
      }
    },
    onerror: (err) => {
      handlers.onError?.({ message: err?.message || 'SSE 连接错误' })
      // 抛出后 fetch-event-source 停止自动重连
      close('error')
      throw err
    }
  }).catch(() => {
    /* swallow: onerror 已经处理过了 */
  })

  onBeforeUnmount(() => close('manual'))

  return { close: () => close('manual') }
}
```

- [ ] **Step 4.2: Commit**

```bash
git add frontend/src/composables/useSse.ts
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): useSse composable with Authorization-Header support

Wraps @microsoft/fetch-event-source because native EventSource cannot
send custom headers. Dispatches to typed handlers per event name.
AbortController + onBeforeUnmount guarantees clean teardown.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: 工具函数 format + citation（markdown-it rule）

**Files:**
- Create: `frontend/src/utils/format.ts`
- Create: `frontend/src/utils/citation.ts`

- [ ] **Step 5.1: 创建 `frontend/src/utils/format.ts`**

```ts
/**
 * 把 epoch milliseconds 格式化成 "YYYY-MM-DD HH:mm:ss"。
 * null/undefined → '—'
 */
export function formatEpochMillis(ms: number | null | undefined): string {
  if (ms == null) return '—'
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * 把两个 epoch ms 之间的差格式化成 "Nm Ms"（不到 1 分钟则 "Ns"）。
 * 任一为 null → '—'
 */
export function formatDurationBetween(
  startMs: number | null | undefined,
  endMs: number | null | undefined
): string {
  if (startMs == null || endMs == null) return '—'
  const totalSec = Math.max(0, Math.floor((endMs - startMs) / 1000))
  if (totalSec < 60) return `${totalSec}s`
  const m = Math.floor(totalSec / 60)
  const s = totalSec % 60
  return `${m}m ${s}s`
}

/** SSE 事件流里的时间戳：HH:mm:ss */
export function formatClock(ms: number): string {
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
```

- [ ] **Step 5.2: 创建 `frontend/src/utils/citation.ts`**

```ts
import type MarkdownIt from 'markdown-it'

/**
 * markdown-it 插件：把行内文本里的 `[1]` `[2]`… 替换成
 *   <sup class="cite-ref" data-cite="1">[1]</sup>
 * 这样模板可以用 click delegation 监听 `.cite-ref` 实现 citation 联动。
 *
 * 只匹配方括号中**纯数字**的引用，避免误伤 markdown 链接 [text](url)。
 */
export function citationPlugin(md: MarkdownIt): void {
  const CITE_RE = /\[(\d+)\]/g

  md.core.ruler.push('citation_inline', (state) => {
    for (const token of state.tokens) {
      if (token.type !== 'inline' || !token.children) continue

      const newChildren: typeof token.children = []

      for (const child of token.children) {
        if (child.type !== 'text' || !CITE_RE.test(child.content)) {
          newChildren.push(child)
          continue
        }
        CITE_RE.lastIndex = 0

        // 拆分 text 并交叉插入 html_inline token
        const text = child.content
        let lastIdx = 0
        let m: RegExpExecArray | null
        const re = new RegExp(CITE_RE.source, 'g')
        while ((m = re.exec(text)) != null) {
          if (m.index > lastIdx) {
            const t = new state.Token('text', '', 0)
            t.content = text.slice(lastIdx, m.index)
            newChildren.push(t)
          }
          const t = new state.Token('html_inline', '', 0)
          t.content = `<sup class="cite-ref" data-cite="${m[1]}">[${m[1]}]</sup>`
          newChildren.push(t)
          lastIdx = m.index + m[0].length
        }
        if (lastIdx < text.length) {
          const t = new state.Token('text', '', 0)
          t.content = text.slice(lastIdx)
          newChildren.push(t)
        }
      }

      token.children = newChildren
    }
  })
}
```

- [ ] **Step 5.3: Commit**

```bash
git add frontend/src/utils/format.ts frontend/src/utils/citation.ts
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): time/duration formatters and markdown-it citation plugin

- format.ts: formatEpochMillis / formatDurationBetween / formatClock
- citation.ts: rewrites `[N]` text into <sup class="cite-ref" data-cite="N">
  for click-to-scroll citation interactions, without affecting [text](url)
  markdown link syntax

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: MarkdownRenderer 组件（流式渲染 + 引用联动）

**Files:**
- Create: `frontend/src/components/report/MarkdownRenderer.vue`

- [ ] **Step 6.1: 创建 `frontend/src/components/report/MarkdownRenderer.vue`**

```vue
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-light.css'
import { citationPlugin } from '@/utils/citation'

const props = defineProps<{
  /** 当前要渲染的完整 markdown 源文。流式时父组件持续追加这个 source。 */
  source: string
  /** 流式中：true 时启用节流（最多 60fps），结束时设 false 立即 final render */
  streaming?: boolean
}>()

const emit = defineEmits<{
  (e: 'cite-click', citationIndex: number): void
}>()

// ===== markdown-it 实例（带 hljs + citation plugin） =====
const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: false,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${
          hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
        }</code></pre>`
      } catch {
        /* fall through */
      }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(code)}</code></pre>`
  }
})
md.use(citationPlugin)

// ===== 节流渲染 =====
const rendered = ref('')
let rafId: number | null = null
let lastRenderAt = 0

function scheduleRender() {
  if (rafId != null) return
  rafId = requestAnimationFrame(() => {
    rafId = null
    const now = performance.now()
    // 节流：流式状态下两次渲染至少间隔 16ms（约 60fps）
    if (props.streaming && now - lastRenderAt < 16) {
      scheduleRender()
      return
    }
    lastRenderAt = now
    rendered.value = md.render(props.source || '')
  })
}

watch(
  () => props.source,
  () => scheduleRender(),
  { immediate: true }
)

// 流式结束的瞬间强制 final render（不节流）
watch(
  () => props.streaming,
  (cur, prev) => {
    if (prev === true && cur === false) {
      if (rafId != null) {
        cancelAnimationFrame(rafId)
        rafId = null
      }
      rendered.value = md.render(props.source || '')
    }
  }
)

// ===== 引用点击事件代理 =====
const rootEl = ref<HTMLElement>()

function onClickDelegated(ev: MouseEvent) {
  const target = ev.target as HTMLElement
  const sup = target.closest('.cite-ref') as HTMLElement | null
  if (!sup) return
  const idx = Number(sup.dataset.cite)
  if (Number.isFinite(idx) && idx > 0) {
    emit('cite-click', idx)
  }
}

onMounted(() => {
  rootEl.value?.addEventListener('click', onClickDelegated)
})

onBeforeUnmount(() => {
  rootEl.value?.removeEventListener('click', onClickDelegated)
  if (rafId != null) cancelAnimationFrame(rafId)
})
</script>

<template>
  <div ref="rootEl" class="md-root" v-html="rendered" />
</template>

<style scoped>
.md-root {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
}

.md-root :deep(h1) {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 16px;
}

.md-root :deep(h2) {
  font-size: 18px;
  font-weight: 600;
  margin: 24px 0 12px;
}

.md-root :deep(h3) {
  font-size: 15px;
  font-weight: 600;
  margin: 20px 0 8px;
}

.md-root :deep(p) {
  margin: 0 0 12px;
}

.md-root :deep(ul),
.md-root :deep(ol) {
  margin: 0 0 12px;
  padding-left: 24px;
}

.md-root :deep(li) {
  margin: 4px 0;
}

.md-root :deep(strong) {
  color: var(--text-primary);
}

.md-root :deep(a) {
  color: var(--color-primary);
}

.md-root :deep(.cite-ref) {
  display: inline-block;
  font-size: 11px;
  color: var(--color-primary);
  background: rgba(47, 109, 245, 0.1);
  border-radius: 4px;
  padding: 0 4px;
  margin: 0 2px;
  cursor: pointer;
  vertical-align: super;
  line-height: 1.6;
}

.md-root :deep(.cite-ref:hover) {
  background: rgba(47, 109, 245, 0.2);
}

.md-root :deep(pre.hljs) {
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  overflow-x: auto;
  font-size: 13px;
}

.md-root :deep(code) {
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
}

.md-root :deep(blockquote) {
  margin: 0 0 12px;
  padding: 4px 12px;
  border-left: 3px solid var(--border-base);
  color: var(--text-secondary);
}
</style>
```

- [ ] **Step 6.2: Commit**

```bash
git add frontend/src/components/report/MarkdownRenderer.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): MarkdownRenderer with streaming throttle + citation hooks

- markdown-it + highlight.js (atom-one-light theme) + citationPlugin
- 60fps throttle while streaming=true; final un-throttled render when
  streaming flips to false
- Click delegation on .cite-ref emits 'cite-click' with citation index

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: SseEventList 组件（左栏实时流）

**Files:**
- Create: `frontend/src/components/report/SseEventList.vue`

- [ ] **Step 7.1: 创建 `frontend/src/components/report/SseEventList.vue`**

```vue
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { SseEvent } from '@/types/report'
import { formatClock } from '@/utils/format'

const props = defineProps<{
  events: SseEvent[]
  connected: boolean
  autoScroll?: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-pause'): void
  (e: 'clear'): void
}>()

const paused = ref(false)
function onTogglePause() {
  paused.value = !paused.value
  emit('toggle-pause')
}

function onClear() {
  emit('clear')
}

const listEl = ref<HTMLElement>()
const wantScroll = computed(() => props.autoScroll !== false)

watch(
  () => props.events.length,
  async () => {
    if (!wantScroll.value || paused.value) return
    await nextTick()
    if (listEl.value) {
      listEl.value.scrollTop = listEl.value.scrollHeight
    }
  }
)

function badgeLabel(e: SseEvent): string {
  switch (e.type) {
    case 'node_status':
      return 'node_status'
    case 'tool':
      return 'tool'
    case 'token':
      return 'token'
    case 'done':
      return 'done'
    case 'error':
      return 'error'
    case 'ping':
      return 'ping'
  }
}

function badgeClass(e: SseEvent): string {
  return `badge badge-${e.type}`
}

function eventSummary(e: SseEvent): string {
  switch (e.type) {
    case 'node_status':
      return `${e.data.nodeId} 节点状态: ${e.data.status}`
    case 'tool': {
      const params = e.data.paramsJson ? ` ${truncate(e.data.paramsJson, 60)}` : ''
      return `调用工具 ${e.data.toolName}${params}`
    }
    case 'token':
      return truncate(e.data.delta, 80)
    case 'done':
      return '报告生成完成'
    case 'error':
      return `错误：${e.data.message}`
    case 'ping':
      return `ping ts=${e.data.ts}`
  }
}

function truncate(s: string, n: number): string {
  if (!s) return ''
  return s.length > n ? `${s.slice(0, n)}…` : s
}
</script>

<template>
  <section class="sse-panel">
    <header class="sse-header">
      <h3 class="sse-title">SSE 实时流</h3>
      <span class="sse-conn" :class="{ on: connected }">
        <span class="dot" />
        {{ connected ? '连接中' : '已断开' }}
      </span>
      <div class="sse-actions">
        <el-button size="small" plain @click="onTogglePause">
          {{ paused ? '继续' : '暂停' }}
        </el-button>
        <el-button size="small" plain @click="onClear">清空</el-button>
      </div>
    </header>

    <ul ref="listEl" class="sse-list">
      <li v-for="(e, idx) in events" :key="idx" class="sse-item">
        <span class="sse-time">{{ formatClock(e.ts) }}</span>
        <span :class="badgeClass(e)">{{ badgeLabel(e) }}</span>
        <span class="sse-summary">{{ eventSummary(e) }}</span>
      </li>
      <li v-if="events.length === 0" class="sse-empty">暂无事件</li>
    </ul>

    <footer class="sse-footer">
      <span v-if="wantScroll && !paused" class="hint">已自动滚动到底部</span>
      <span v-else class="hint muted">滚动已暂停</span>
    </footer>
  </section>
</template>

<style scoped>
.sse-panel {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.sse-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-light);
}

.sse-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.sse-conn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.sse-conn .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-tertiary);
}

.sse-conn.on {
  color: var(--color-success);
}

.sse-conn.on .dot {
  background: var(--color-success);
}

.sse-actions {
  margin-left: auto;
  display: flex;
  gap: 6px;
}

.sse-list {
  list-style: none;
  margin: 0;
  padding: 8px 12px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.sse-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 0;
  font-size: 12px;
  border-bottom: 1px dashed var(--border-light);
}

.sse-item:last-child {
  border-bottom: none;
}

.sse-empty {
  list-style: none;
  text-align: center;
  color: var(--text-tertiary);
  padding: 24px 0;
  font-size: 12px;
}

.sse-time {
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.badge {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}

.badge-node_status {
  background: rgba(47, 109, 245, 0.12);
  color: var(--color-primary);
}

.badge-tool {
  background: rgba(245, 158, 11, 0.18);
  color: #b45309;
}

.badge-token {
  background: rgba(99, 102, 241, 0.15);
  color: #4338ca;
}

.badge-done {
  background: rgba(16, 185, 129, 0.18);
  color: #047857;
}

.badge-error {
  background: rgba(239, 68, 68, 0.18);
  color: #b91c1c;
}

.badge-ping {
  background: var(--bg-muted);
  color: var(--text-tertiary);
}

.sse-summary {
  color: var(--text-secondary);
  word-break: break-word;
  min-width: 0;
}

.sse-footer {
  padding: 10px 16px;
  border-top: 1px solid var(--border-light);
  text-align: center;
}

.hint {
  font-size: 12px;
  color: var(--color-primary);
}

.hint.muted {
  color: var(--text-tertiary);
}
</style>
```

- [ ] **Step 7.2: Commit**

```bash
git add frontend/src/components/report/SseEventList.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): SseEventList component for left-column event stream

Color-coded badges per event type (node_status / tool / token / done /
error / ping). Auto-scroll-to-bottom on new events unless paused.
Emits toggle-pause and clear so parent can drive event list state.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: ReportTaskHeader 组件（顶部 meta 卡）

**Files:**
- Create: `frontend/src/components/report/ReportTaskHeader.vue`

- [ ] **Step 8.1: 创建 `frontend/src/components/report/ReportTaskHeader.vue`**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { ArrowLeft, Refresh, Download } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import type { ReportDetail, ReportTaskStatus } from '@/types/report'
import {
  formatDurationBetween,
  formatEpochMillis
} from '@/utils/format'

const props = defineProps<{
  detail: ReportDetail
  rerunDisabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'rerun'): void
  (e: 'download'): void
}>()

const router = useRouter()

const statusClass = computed<Record<ReportTaskStatus, string>>(() => ({
  PENDING: 'st-pending',
  RUNNING: 'st-running',
  DONE: 'st-done',
  FAILED: 'st-failed'
}))

const durationText = computed(() =>
  formatDurationBetween(
    props.detail.startedAtEpochMillis,
    props.detail.finishedAtEpochMillis
  )
)

function goBack() {
  router.back()
}
</script>

<template>
  <header class="rh">
    <div class="rh-top">
      <button class="rh-back" type="button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <h1 class="rh-title">研究报告任务详情</h1>
      <div class="rh-actions">
        <el-button :icon="Refresh" :disabled="rerunDisabled" @click="emit('rerun')">
          重新运行
        </el-button>
        <el-button type="primary" :icon="Download" :disabled="!detail.finalMarkdown" @click="emit('download')">
          下载报告
        </el-button>
      </div>
    </div>

    <div class="rh-meta">
      <div class="meta-cell">
        <div class="meta-key">任务 ID</div>
        <div class="meta-val link">task_{{ String(detail.taskId).padStart(8, '0') }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">研究主题</div>
        <div class="meta-val" :title="detail.topic">{{ detail.topic }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">状态</div>
        <div class="meta-val">
          <span class="status-pill" :class="statusClass[detail.status]">{{ detail.status }}</span>
        </div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">启动时间</div>
        <div class="meta-val">{{ formatEpochMillis(detail.startedAtEpochMillis) }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">完成时间</div>
        <div class="meta-val">{{ formatEpochMillis(detail.finishedAtEpochMillis) }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">耗时</div>
        <div class="meta-val">{{ durationText }}</div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.rh {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px 20px;
}

.rh-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.rh-back {
  appearance: none;
  background: transparent;
  border: none;
  font-size: 18px;
  color: var(--text-secondary);
  cursor: pointer;
  display: grid;
  place-items: center;
  padding: 4px;
  border-radius: var(--radius-sm);
}

.rh-back:hover {
  background: var(--bg-muted);
}

.rh-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.rh-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

/* Meta row */
.rh-meta {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  padding: 14px 16px;
  background: var(--bg-muted);
  border-radius: var(--radius-md);
}

.meta-cell {
  min-width: 0;
}

.meta-key {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-bottom: 2px;
}

.meta-val {
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.meta-val.link {
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.status-pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.4px;
}

.status-pill.st-pending {
  background: var(--bg-muted);
  color: var(--text-secondary);
}

.status-pill.st-running {
  background: rgba(47, 109, 245, 0.16);
  color: var(--color-primary);
}

.status-pill.st-done {
  background: rgba(16, 185, 129, 0.18);
  color: #047857;
}

.status-pill.st-failed {
  background: rgba(239, 68, 68, 0.18);
  color: #b91c1c;
}

@media (max-width: 1100px) {
  .rh-meta {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 720px) {
  .rh-meta {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
```

- [ ] **Step 8.2: Commit**

```bash
git add frontend/src/components/report/ReportTaskHeader.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): ReportTaskHeader with task meta, status pill, and actions

6-column meta grid (taskId / topic / status / started / finished /
duration). Status pill has 4 colors. 'Rerun' and 'Download' emits are
parent-driven; back button uses router.back().

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: CitationGrid 组件（参考资料卡片网格）

**Files:**
- Create: `frontend/src/components/report/CitationGrid.vue`

- [ ] **Step 9.1: 创建 `frontend/src/components/report/CitationGrid.vue`**

```vue
<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { CitationData } from '@/types/rag'

const props = defineProps<{
  citations: CitationData[]
  activeIndex: number | null    // 1-based; null when nothing highlighted
}>()

const cardRefs = ref<HTMLElement[]>([])
function setCardRef(el: unknown, idx: number) {
  if (el) cardRefs.value[idx] = el as HTMLElement
}

watch(
  () => props.activeIndex,
  async (cur) => {
    if (cur == null || cur < 1) return
    await nextTick()
    const el = cardRefs.value[cur - 1]
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    el.classList.add('flash')
    window.setTimeout(() => el.classList.remove('flash'), 1200)
  }
)
</script>

<template>
  <section v-if="citations.length > 0" class="cg-block">
    <h2 class="cg-title">参考资料</h2>
    <div class="cg-grid">
      <div
        v-for="(c, idx) in citations"
        :key="idx"
        :ref="(el) => setCardRef(el, idx)"
        class="cite-card"
      >
        <div class="cite-no">[{{ idx + 1 }}]</div>
        <div class="cite-title" :title="c.docTitle">{{ c.docTitle }}</div>
        <div class="cite-section">{{ c.sectionTitle }}</div>
        <div class="cite-meta">
          <span class="meta-pill">{{ c.source }}</span>
          <span class="meta-pages">p. {{ c.pageStart }}-{{ c.pageEnd }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.cg-block {
  margin-top: 24px;
}

.cg-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 12px;
  color: var(--text-primary);
}

.cg-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.cite-card {
  border: 1px solid var(--border-light);
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: box-shadow 0.3s, border-color 0.3s, background 0.3s;
}

.cite-card.flash {
  border-color: var(--color-primary);
  background: rgba(47, 109, 245, 0.05);
  box-shadow: 0 0 0 4px rgba(47, 109, 245, 0.12);
}

.cite-no {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-primary);
}

.cite-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cite-section {
  font-size: 12px;
  color: var(--text-secondary);
}

.cite-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.meta-pill {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--bg-muted);
  color: var(--text-secondary);
}

.meta-pages {
  font-size: 11px;
  color: var(--text-tertiary);
}

@media (max-width: 900px) {
  .cg-grid {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [ ] **Step 9.2: Commit**

```bash
git add frontend/src/components/report/CitationGrid.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): CitationGrid component renders the references list

Reactive to activeIndex prop: when set, scrolls the matching card into
view and flashes a primary-color highlight for ~1.2s.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: TraceTable 组件（底部 Trace 时间轴）

**Files:**
- Create: `frontend/src/components/report/TraceTable.vue`

- [ ] **Step 10.1: 创建 `frontend/src/components/report/TraceTable.vue`**

```vue
<script setup lang="ts">
import { computed, ref } from 'vue'
import { Download, Refresh } from '@element-plus/icons-vue'
import hljs from 'highlight.js'
import type { TraceRow } from '@/types/report'
import { formatEpochMillis } from '@/utils/format'

const props = defineProps<{
  rows: TraceRow[]
  /** 任务 ID 用于导出文件名 */
  taskId: number
  loading?: boolean
  autoRefresh: boolean
}>()

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'update:autoRefresh', v: boolean): void
}>()

// ===== 过滤 =====
const nodeFilter = ref<string>('')

const nodeOptions = computed(() => {
  const set = new Set<string>()
  for (const r of props.rows) set.add(r.nodeId)
  return Array.from(set)
})

const filteredRows = computed(() => {
  if (!nodeFilter.value) return props.rows
  return props.rows.filter((r) => r.nodeId === nodeFilter.value)
})

// ===== 导出 JSON =====
function exportJson() {
  const blob = new Blob([JSON.stringify(props.rows, null, 2)], {
    type: 'application/json'
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `trace-${props.taskId}.json`
  a.click()
  URL.revokeObjectURL(url)
}

function tryHighlightJson(text: string | null): string {
  if (!text) return '<span style="color:var(--text-tertiary);">（无）</span>'
  try {
    // 后端可能已经截断成 "..." 结尾，hljs 也能处理
    return hljs.highlight(text, { language: 'json', ignoreIllegals: true }).value
  } catch {
    return text
  }
}

function tagType(t: TraceRow['stepType']): 'primary' | 'success' {
  return t === 'LLM_CALL' ? 'primary' : 'success'
}

function statusTagType(s: TraceRow['status']): 'success' | 'danger' {
  return s === 'OK' ? 'success' : 'danger'
}
</script>

<template>
  <section class="trace-wrap">
    <header class="tw-header">
      <h3 class="tw-title">Trace 时间轴</h3>
      <div class="tw-actions">
        <el-select
          v-model="nodeFilter"
          placeholder="全部节点"
          clearable
          style="width: 160px;"
          size="small"
        >
          <el-option label="全部节点" value="" />
          <el-option v-for="n in nodeOptions" :key="n" :label="n" :value="n" />
        </el-select>
        <el-button size="small" :icon="Download" @click="exportJson">
          导出 Trace (JSON)
        </el-button>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="emit('refresh')">
          刷新
        </el-button>
        <span class="tw-toggle">
          自动刷新
          <el-switch
            :model-value="autoRefresh"
            size="small"
            @update:model-value="(v) => emit('update:autoRefresh', v as boolean)"
          />
        </span>
      </div>
    </header>

    <el-table
      :data="filteredRows"
      stripe
      size="small"
      style="width: 100%;"
      header-cell-class-name="tw-cell"
    >
      <el-table-column type="expand">
        <template #default="{ row }: { row: TraceRow }">
          <div class="tw-expand">
            <div class="tw-expand-block">
              <div class="tw-expand-title">Input preview</div>
              <pre class="hljs"><code v-html="tryHighlightJson(row.inputJsonPreview)" /></pre>
            </div>
            <div class="tw-expand-block">
              <div class="tw-expand-title">Output preview</div>
              <pre class="hljs"><code v-html="tryHighlightJson(row.outputJsonPreview)" /></pre>
            </div>
            <div v-if="row.errorMessage" class="tw-expand-block">
              <div class="tw-expand-title err">Error</div>
              <pre class="err-text">{{ row.errorMessage }}</pre>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="stepSeq" label="stepSeq" width="80" />
      <el-table-column label="时间" width="180">
        <template #default="{ row }: { row: TraceRow }">
          {{ formatEpochMillis(row.latencyMs ? Date.now() : null) /* trace 表没存时间戳，此处空显示 */ }}
        </template>
      </el-table-column>
      <el-table-column prop="nodeId" label="nodeId" width="120" />
      <el-table-column prop="agentRole" label="agentRole" width="140" />
      <el-table-column label="stepType" width="120">
        <template #default="{ row }: { row: TraceRow }">
          <el-tag size="small" :type="tagType(row.stepType)">{{ row.stepType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="model / toolName" min-width="160">
        <template #default="{ row }: { row: TraceRow }">
          {{ row.model || row.toolName || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="tokensIn" label="tokensIn" width="90" align="right" />
      <el-table-column prop="tokensOut" label="tokensOut" width="100" align="right" />
      <el-table-column prop="latencyMs" label="latencyMs" width="100" align="right" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }: { row: TraceRow }">
          <el-tag size="small" :type="statusTagType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>

      <template #empty>
        <div class="tw-empty">暂无 Trace 数据。任务完成后会自动加载。</div>
      </template>
    </el-table>
  </section>
</template>

<style scoped>
.trace-wrap {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px 20px;
}

.tw-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.tw-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.tw-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.tw-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.tw-expand {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 12px 12px 48px;
}

.tw-expand-block {
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}

.tw-expand-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.tw-expand-title.err {
  color: var(--color-danger);
}

.tw-expand pre {
  margin: 0;
  font-size: 12px;
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  white-space: pre-wrap;
  word-break: break-word;
}

.err-text {
  color: var(--color-danger);
}

.tw-empty {
  color: var(--text-tertiary);
  font-size: 12px;
  padding: 16px 0;
}
</style>
```

> **Note on Trace timestamp column:** 后端当前的 `WorkflowNodeRun` 实体没单独存 `startedAt` 字段，Trace API 也不返回行级时间戳；plan 里这一列暂时**显示空** —— 未来 Phase 4 后端加 `startedAt` 后再接通。本期不实现，但 UI 留着这一列结构。

- [ ] **Step 10.2: Commit**

```bash
git add frontend/src/components/report/TraceTable.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): TraceTable with expand-row JSON, filter, export, refresh

el-table with expandable rows for input/output JSON previews (hljs
syntax highlighting). Node filter dropdown, JSON export action,
manual refresh and auto-refresh toggle wired via parent emits.

Timestamp column is structurally present but currently empty — backend
WorkflowNodeRun does not yet expose row-level startedAt; will fill in
when Phase 4 platform aggregation extends the trace API.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: ReportSubmitView（主题提交页）

**Files:**
- Create: `frontend/src/views/report/ReportSubmitView.vue`

- [ ] **Step 11.1: 创建 `frontend/src/views/report/ReportSubmitView.vue`**

```vue
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { reportApi } from '@/api/report'

const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  topic: (route.query.topic as string) || '',
  workflow: 'researcher_only_v1'
})

const rules: FormRules = {
  topic: [
    { required: true, message: '请输入研究主题', trigger: 'blur' },
    { min: 4, max: 500, message: '长度 4-500', trigger: 'blur' }
  ]
}

const workflowOptions = [
  { label: 'researcher_only_v1（仅检索员工作流）', value: 'researcher_only_v1' }
]

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const resp = await reportApi.start({
      topic: form.topic.trim(),
      workflow: form.workflow
    })
    ElMessage.success(`任务已提交：task_${String(resp.taskId).padStart(8, '0')}`)
    router.replace(`/report/${resp.taskId}`)
  } catch {
    /* ElMessage handled by interceptor */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="rs-submit">
    <header class="rs-title-row">
      <h1 class="rs-title">发起研究报告任务</h1>
      <p class="rs-subtitle">
        输入主题，平台将通过 Multi-Agent 工作流自动检索、分析并撰写一份带引用的行业研究报告。
      </p>
    </header>

    <section class="rs-form-card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <el-form-item label="研究主题" prop="topic">
          <el-input
            v-model="form.topic"
            placeholder="例：2026 年新能源汽车行业趋势研究"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            @keyup.enter.ctrl="onSubmit"
          />
        </el-form-item>

        <el-form-item label="工作流">
          <el-select v-model="form.workflow" style="width: 100%;">
            <el-option
              v-for="o in workflowOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="rs-submit-btn"
          :loading="submitting"
          @click="onSubmit"
        >
          提交并开始
        </el-button>

        <div class="rs-hint">
          Ctrl + Enter 提交 · 任务平均耗时 30-180s · 提交后会跳到详情页查看 SSE 实时流
        </div>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.rs-submit {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 720px;
  margin: 0 auto;
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

.rs-form-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 24px;
}

.rs-submit-btn {
  width: 100%;
}

.rs-hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
}
</style>
```

- [ ] **Step 11.2: Commit**

```bash
git add frontend/src/views/report/ReportSubmitView.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): ReportSubmitView with topic + workflow inputs

Validates topic 4-500 chars matching backend constraint. On submit
calls /api/report/start then router.replace to detail page so back
button doesn't return to submit.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: ReportDetailView（详情页主体 —— 集成全部）

**Files:**
- Create: `frontend/src/views/report/ReportDetailView.vue`

- [ ] **Step 12.1: 创建 `frontend/src/views/report/ReportDetailView.vue`**

```vue
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ReportTaskHeader from '@/components/report/ReportTaskHeader.vue'
import SseEventList from '@/components/report/SseEventList.vue'
import MarkdownRenderer from '@/components/report/MarkdownRenderer.vue'
import CitationGrid from '@/components/report/CitationGrid.vue'
import TraceTable from '@/components/report/TraceTable.vue'
import { buildStreamUrl, reportApi } from '@/api/report'
import { useSse, type SseHandle } from '@/composables/useSse'
import type {
  ReportDetail,
  SseEvent,
  TraceRow
} from '@/types/report'

const route = useRoute()
const router = useRouter()

// ===== 路由参数 =====
const taskId = computed(() => Number(route.params.id))

// ===== 主状态 =====
const detail = ref<ReportDetail | null>(null)
const events = ref<SseEvent[]>([])
const streamingMarkdown = ref('')       // 流式累积；done 时被 final 覆盖
const finalMarkdown = ref('')           // done 后的最终稿
const isStreaming = ref(false)
const sseConnected = ref(false)
const activeCitation = ref<number | null>(null)
const traceRows = ref<TraceRow[]>([])
const traceLoading = ref(false)
const autoRefreshTrace = ref(true)
let sseHandle: SseHandle | null = null
let traceTimer: number | null = null

const displayMarkdown = computed(() =>
  finalMarkdown.value || streamingMarkdown.value
)

// ===== 入口：拉初态，决定要不要开 SSE =====
async function bootstrap() {
  if (!taskId.value || Number.isNaN(taskId.value)) {
    ElMessage.error('任务 ID 无效')
    router.replace('/report/submit')
    return
  }

  try {
    const d = await reportApi.get(taskId.value)
    detail.value = d
    finalMarkdown.value = d.finalMarkdown ?? ''
  } catch {
    return
  }

  if (detail.value?.status === 'DONE' || detail.value?.status === 'FAILED') {
    await loadTrace()
    return
  }

  openStream()
}

function openStream() {
  closeStream()
  events.value = []
  streamingMarkdown.value = ''
  isStreaming.value = true
  sseConnected.value = true

  sseHandle = useSse(buildStreamUrl(taskId.value), {
    onNodeStatus: (data) =>
      events.value.push({ type: 'node_status', ts: Date.now(), data }),
    onTool: (data) =>
      events.value.push({ type: 'tool', ts: Date.now(), data }),
    onToken: (data) => {
      events.value.push({ type: 'token', ts: Date.now(), data })
      streamingMarkdown.value += data.delta
      if (detail.value) detail.value.status = 'RUNNING'
    },
    onDone: (data) => {
      events.value.push({ type: 'done', ts: Date.now(), data })
      finalMarkdown.value = data.finalMarkdown
      if (detail.value) {
        detail.value.status = 'DONE'
        detail.value.citations = data.citations
        detail.value.finalMarkdown = data.finalMarkdown
        detail.value.finishedAtEpochMillis = Date.now()
      }
      isStreaming.value = false
      sseConnected.value = false
      void loadTrace()
    },
    onError: (data) => {
      events.value.push({ type: 'error', ts: Date.now(), data })
      if (detail.value) {
        detail.value.status = 'FAILED'
        detail.value.errorMessage = data.message
      }
      isStreaming.value = false
      sseConnected.value = false
      ElMessage.error(data.message)
    },
    onPing: (data) =>
      events.value.push({ type: 'ping', ts: Date.now(), data }),
    onClose: () => {
      sseConnected.value = false
    }
  })
}

function closeStream() {
  sseHandle?.close()
  sseHandle = null
}

async function loadTrace() {
  if (!taskId.value) return
  traceLoading.value = true
  try {
    traceRows.value = await reportApi.trace(taskId.value)
  } catch {
    /* interceptor handles */
  } finally {
    traceLoading.value = false
  }
}

function scheduleTracePoll() {
  clearTracePoll()
  if (!autoRefreshTrace.value) return
  if (detail.value?.status !== 'RUNNING' && detail.value?.status !== 'PENDING') return
  traceTimer = window.setInterval(() => {
    void loadTrace()
  }, 3000)
}

function clearTracePoll() {
  if (traceTimer != null) {
    clearInterval(traceTimer)
    traceTimer = null
  }
}

watch(autoRefreshTrace, scheduleTracePoll)
watch(() => detail.value?.status, (s) => {
  if (s === 'DONE' || s === 'FAILED') {
    clearTracePoll()
    closeStream()
  } else {
    scheduleTracePoll()
  }
})

// ===== 操作 =====
async function onRerun() {
  if (!detail.value) return
  try {
    const resp = await reportApi.start({ topic: detail.value.topic })
    ElMessage.success(`已重新发起任务 task_${String(resp.taskId).padStart(8, '0')}`)
    router.replace(`/report/${resp.taskId}`)
  } catch {
    /* handled */
  }
}

function onDownload() {
  const md = finalMarkdown.value || streamingMarkdown.value
  if (!md) return
  const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `report-${taskId.value}.md`
  a.click()
  URL.revokeObjectURL(url)
}

function onClearEvents() {
  events.value = []
}

function onCiteClick(idx: number) {
  activeCitation.value = idx
}

// ===== 生命周期 =====
watch(taskId, () => {
  closeStream()
  clearTracePoll()
  detail.value = null
  events.value = []
  streamingMarkdown.value = ''
  finalMarkdown.value = ''
  traceRows.value = []
  bootstrap()
}, { immediate: true })

onBeforeUnmount(() => {
  closeStream()
  clearTracePoll()
})
</script>

<template>
  <div class="rd">
    <!-- 顶部 -->
    <ReportTaskHeader
      v-if="detail"
      :detail="detail"
      :rerun-disabled="detail.status === 'PENDING' || detail.status === 'RUNNING'"
      @rerun="onRerun"
      @download="onDownload"
    />
    <div v-else class="rd-loading">加载任务详情…</div>

    <!-- 主区：左 SSE + 右 Markdown -->
    <section v-if="detail" class="rd-main">
      <div class="rd-left">
        <SseEventList
          :events="events"
          :connected="sseConnected"
          @clear="onClearEvents"
        />
      </div>

      <div class="rd-right">
        <header class="rd-md-header">
          <h2 class="rd-md-title">最终报告 Markdown 预览</h2>
          <el-button size="small" plain @click="onDownload" :disabled="!displayMarkdown">
            复制/下载 Markdown
          </el-button>
        </header>

        <div class="rd-md-body">
          <MarkdownRenderer
            v-if="displayMarkdown"
            :source="displayMarkdown"
            :streaming="isStreaming"
            @cite-click="onCiteClick"
          />
          <div v-else class="rd-md-empty">
            <template v-if="detail.status === 'PENDING'">任务排队中，等待 Worker 接管…</template>
            <template v-else-if="detail.status === 'RUNNING'">报告生成中，请稍候…</template>
            <template v-else-if="detail.status === 'FAILED'">
              任务失败：{{ detail.errorMessage || '未知错误' }}
            </template>
            <template v-else>暂无内容</template>
          </div>

          <CitationGrid
            v-if="detail.citations && detail.citations.length > 0"
            :citations="detail.citations"
            :active-index="activeCitation"
          />
        </div>
      </div>
    </section>

    <!-- 底部 Trace 表格 -->
    <TraceTable
      v-if="detail"
      :rows="traceRows"
      :task-id="taskId"
      :loading="traceLoading"
      :auto-refresh="autoRefreshTrace"
      @refresh="loadTrace"
      @update:auto-refresh="(v) => (autoRefreshTrace = v)"
    />
  </div>
</template>

<style scoped>
.rd {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rd-loading {
  text-align: center;
  padding: 80px 0;
  color: var(--text-tertiary);
}

.rd-main {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 16px;
  align-items: stretch;
  min-height: 480px;
}

.rd-left {
  min-height: 0;
}

.rd-right {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.rd-md-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-light);
}

.rd-md-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.rd-md-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.rd-md-empty {
  color: var(--text-tertiary);
  font-size: 13px;
  padding: 40px 0;
  text-align: center;
}

@media (max-width: 1100px) {
  .rd-main {
    grid-template-columns: 1fr;
  }
}
</style>
```

- [ ] **Step 12.2: Commit**

```bash
git add frontend/src/views/report/ReportDetailView.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): ReportDetailView integrating SSE + markdown + trace

- onMounted: GET /api/report/{id}; if DONE/FAILED load trace; else
  open SSE stream
- SSE 'token' events append to streamingMarkdown; 'done' replaces with
  finalMarkdown and triggers trace load
- Citation click in MarkdownRenderer drives activeCitation prop of
  CitationGrid (smooth scroll + 1.2s flash)
- Trace auto-refresh polls every 3s while task running; stops on done
- Rerun emits POST /report/start with same topic and router.replace
- Download builds Blob from current markdown

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: 路由 + 启用菜单 + HomeView 接通

**Files:**
- Modify: `frontend/src/router/index.ts` (add 2 child routes)
- Modify: `frontend/src/layouts/DefaultLayout.vue` (enable 报告 menu, extend activeMenu)
- Modify: `frontend/src/views/HomeView.vue` (give "发起报告任务" QuickStartCard a `to`)

- [ ] **Step 13.1: 修改 `frontend/src/router/index.ts`**

把 `/` 子路由的 `children` 数组从（F1 末态）：

```ts
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
```

改成（追加两条 report 路由）：

```ts
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
      },
      {
        path: 'report/submit',
        name: 'report-submit',
        component: () => import('@/views/report/ReportSubmitView.vue'),
        meta: { requiresAuth: true, title: '发起报告任务' }
      },
      {
        path: 'report/:id(\\d+)',
        name: 'report-detail',
        component: () => import('@/views/report/ReportDetailView.vue'),
        meta: { requiresAuth: true, title: '研究报告详情' }
      }
    ]
```

用 `Edit` 工具，old_string = 上面的"改前"完整块，new_string = "改后"完整块。

- [ ] **Step 13.2: 修改 `frontend/src/layouts/DefaultLayout.vue` —— 启用"研究报告"菜单**

两处 Edit：

**改动 1：** menus 数组中的 report 项。

old_string:
```
  { key: 'report', label: '研究报告', icon: Document, disabled: true },
```

new_string:
```
  { key: 'report', label: '研究报告', icon: Document, path: '/report/submit' },
```

**改动 2：** activeMenu computed。

old_string:
```
const activeMenu = computed(() => {
  if (route.path === '/') return 'home'
  if (route.path.startsWith('/rag')) return 'rag'
  return ''
})
```

new_string:
```
const activeMenu = computed(() => {
  if (route.path === '/') return 'home'
  if (route.path.startsWith('/rag')) return 'rag'
  if (route.path.startsWith('/report')) return 'report'
  return ''
})
```

- [ ] **Step 13.3: 修改 `frontend/src/views/HomeView.vue` —— "发起报告任务" 快捷卡补 `to`**

找到当前的卡：

old_string:
```
          <QuickStartCard
            title="发起报告任务"
            description="新建任务"
            icon-name="EditPen"
            icon-bg="#dcfce7"
          />
```

new_string:
```
          <QuickStartCard
            title="发起报告任务"
            description="新建任务"
            icon-name="EditPen"
            icon-bg="#dcfce7"
            to="/report/submit"
          />
```

- [ ] **Step 13.4: Commit**

```bash
git add frontend/src/router/index.ts frontend/src/layouts/DefaultLayout.vue frontend/src/views/HomeView.vue
```

```bash
git commit -m "$(cat <<'EOF'
feat(frontend): wire /report/submit + /report/:id routes and nav

- Router: add report-submit and report-detail child routes; detail
  uses :id(\\d+) constraint so non-numeric paths fall through to 404
- DefaultLayout: enable '研究报告' menu (path /report/submit), extend
  activeMenu to highlight /report*
- HomeView: '发起报告任务' QuickStartCard now navigates to submit page

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase F2 出口验证（**由用户在 IDE 完成**）

> ⚠️ Agent 不要跑这些。

- [ ] **V1: 安装新依赖 + 启动**

```bash
cd frontend
npm install        # 拉 fetch-event-source / markdown-it / @types/markdown-it / highlight.js
npm run dev        # http://127.0.0.1:5173/
```

后端：用户在 IDE 启动 Spring Boot 应用；确认 PGVector 已经有数据；确认 DashScope（或对应 LLM Provider）API key 在 .env 中已配并能通。**不要让 agent 帮跑这些。**

- [ ] **V2: 提交任务**

1. 登录 → 进首页
2. 点首页"发起报告任务"快捷卡 → 跳 `/report/submit`
3. 顶部环境标 / 侧栏"研究报告"高亮
4. 输入主题"2026 年新能源汽车行业趋势研究"，工作流默认 `researcher_only_v1`
5. 点"提交并开始"
6. 弹 toast "任务已提交：task_00000001"
7. 自动 router.replace 到 `/report/1`（数字按你 DB 而定）

- [ ] **V3: SSE 实时流**

详情页打开后预期：
- 顶部 meta 卡：任务 ID / 主题 / 状态 RUNNING / 启动时间 / 完成时间—（运行中暂无）/ 耗时—
- 左栏"SSE 实时流"显示"连接中"绿点；陆续滚出 `node_status` / `tool` / `token` 多种事件
- 右栏 Markdown 预览开始逐字浮现（流式 token 拼接）
- 底部"Trace 时间轴"表格在任务跑期间每 3s 自动刷新一次

- [ ] **V4: 完成态**

任务跑完（约 30-180s）：
- 左栏最后一条事件是 `done`
- 左栏顶部连接状态变成灰"已断开"
- 右栏 Markdown 替换成 finalMarkdown（一次性 final render，引用 `[1]` `[2]` 可见）
- 右栏"参考资料"卡片网格出现，点击 markdown 里的 `[1]` 时，对应卡片高亮 1.2s + 滚到视线内
- 顶部状态 pill 变 DONE 绿色，启动/完成时间填上，耗时填上
- 底部 Trace 表加载完整行，点击某行左侧"展开"按钮看到 input / output JSON 高亮

- [ ] **V5: 重新运行 / 下载**

- 点顶部"重新运行" → 起一个新 task → 自动跳到新详情页
- 点顶部"下载报告" → 浏览器下载 `report-<id>.md`，内容是 finalMarkdown
- 底部"导出 Trace (JSON)" → 下载 `trace-<id>.json`

- [ ] **V6: 错误态**

故意提交一个会让 LLM 失败的主题（比如把 .env 里的 LLM API key 改坏 → 重启后端再提交）：
- SSE 收到 `error` 事件
- 顶部状态 pill 变 FAILED 红色 + 错误消息文案出现
- 右栏空态文案变成 "任务失败：<message>"
- 左栏 SSE 连接断开

- [ ] **V7: 直接打开已完成任务**

复制一个已 DONE 的 task URL（比如 V4 那个）→ 用新标签页打开 → 应当：
- 直接渲染 finalMarkdown（不开 SSE，左栏空着也无所谓）
- Trace 表格直接 load 出来
- 顶部状态 DONE

如果以上 7 步都过 → Phase F2 验收通过，**整个 MVP 完成**。

---

## Self-Review（已执行）

**1. Spec coverage vs `docs/superpowers/specs/2026-05-20-frontend-mvp-design.md` §6 Phase F2**

| Spec 子项 | 覆盖 Task |
|---|---|
| T2.1 ReportSubmitView（topic 输入 + workflow + 提交 + 跳详情）| Task 11 |
| T2.2 useSse composable + 类型定义（5 种事件 + ping）| Task 2 + Task 4 |
| T2.3 ReportDetailView 框架（顶部 meta + 三栏布局）| Task 8 + Task 12 |
| T2.4 SseEventList（左栏实时流）| Task 7 |
| T2.5 MarkdownRenderer（流式 + 引用 [n] 联动）| Task 5 (citation.ts) + Task 6 (MarkdownRenderer) + Task 9 (CitationGrid) + Task 12 (联动 wiring) |
| T2.6 TraceTable（el-table + expandable JSON + hl.js）| Task 10 |
| T2.7 全链路联调 | Task 12 + Task 13 |

全部覆盖。补充任务：Task 1（依赖）/ Task 3（report api）/ Task 13（路由 + 菜单 + 首页接通）—— spec 隐含但未单列。

**2. Placeholder scan：** 无 TBD / TODO / "implement later"。`TraceTable.vue` 中"时间"列暂时用空显示 + 在 Step 10.2 commit 后的 Note 块里说明了原因（后端 Trace 不返回行级时间戳）—— 这是有意识保留的 UI 结构，不是占位符。

**3. Type consistency：**
- `ReportDetail` / `ReportStartResponse` / `TraceRow` / `SseEvent`（discriminated union）/ 6 个事件 payload 类型在 Task 2 定义；Task 3 / 4 / 7 / 8 / 10 / 11 / 12 引用一致。
- `CitationData` 复用 F1 `@/types/rag` 的定义，跨阶段共享；`CitationGrid` 与 `ReportDetail.citations` 字段对齐。
- `useSse(url, handlers): SseHandle` 在 Task 4 定义；Task 12 调用签名一致，AbortController 被封装在 `close()` 后面。
- `MarkdownRenderer` props `{ source: string; streaming?: boolean }` 与 emit `cite-click: (idx: number) => void` 在 Task 6 定义；Task 12 绑定 `:source="displayMarkdown" :streaming="isStreaming" @cite-click="onCiteClick"` 类型对齐。
- `SseEventList` props `{ events: SseEvent[]; connected: boolean; autoScroll?: boolean }` + emits `toggle-pause` / `clear`；Task 12 仅订阅 `@clear="onClearEvents"`（toggle-pause 留作未来加 pause 时再用，不订阅不会报错）。
- `TraceTable` props `{ rows: TraceRow[]; taskId: number; loading?: boolean; autoRefresh: boolean }` + emits `refresh` + `update:autoRefresh`；Task 12 用 v-model 风格 `@update:auto-refresh="(v) => (autoRefreshTrace = v)"` 接通。

无类型漂移。
