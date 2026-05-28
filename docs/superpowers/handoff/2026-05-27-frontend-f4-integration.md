# 前端 F4 接入清单（后端 8 个缺口已补齐）

> 编写日期：2026-05-27
> 阅读对象：前端
> 关联文档：[后端 F4 Spec](../specs/2026-05-26-backend-f4-api-gaps-design.md) · [F4 实施计划](../plans/2026-05-26-backend-f4-api-gaps-implementation.md) · [原 gaps 清单（已落地）](2026-05-24-backend-api-gaps.md)
> 后端涉及 commit：`c18922e`（批次 A，Judge）+ `0c8f63a`（批次 B，Workflow+Stats+Health）

---

## 0. TL;DR

后端 8 个 gap **已全部上线**。前端只需要做 3 件事：

1. **拉新接口类型** — 给 `frontend/src/types/admin.ts` 加 6 个 VO 类型（见 §2）
2. **加 7 个 API 方法** — `frontend/src/api/admin.ts` 与 `frontend/src/api/health.ts`（见 §3）
3. **替换 5 处 mock** — 把 `// TODO(backend-api-gap #N)` 注释处的 mock 引用替换为接口调用（见 §4）

完成后整体删除 `frontend/src/mock/admin-placeholders.ts`，跑 `npx vue-tsc --noEmit && npm run build`，0 error 即可发版。

预计工作量 **~3h**。

---

## 1. 部署前置（运维侧已知）

后端 commit `0c8f63a` 引入了 DDL 文件 `src/main/resources/db/schema-phase7.sql`。**生产/预发环境部署时必须在 MySQL 上执行一次**（开发环境如果是脚本初始化的，重启会自动应用）：

```sql
-- 新增 workflow_load_log 表（Admin Workflow 页时间轴用）
-- report_task 加 idx_create_time（Stats 同比/P95 SQL 性能必需）
```

DDL 写了 `IF NOT EXISTS` / `ADD KEY IF NOT EXISTS`，重复执行幂等；MySQL < 8.0.29 不支持 `ADD KEY IF NOT EXISTS` 的话需要手工跳过那一行。

---

## 2. TypeScript 类型新增（`frontend/src/types/admin.ts`）

### 2.1 `JudgeRunVO` — 追加 1 个字段

```typescript
export interface JudgeRunVO {
  id: number
  taskId: number
  judgeModel: string
  rubricVersion: string
  overall: number | null
  structure: number | null
  factuality: number | null
  reasoning: number | null
  citation: number | null
  clarity: number | null
  comments: Record<string, string>
  latencyMs: number | null
  createTime: number | null
  topic: string | null   // ⚡ F4 新增：关联任务的研究主题
}
```

### 2.2 `PlatformOverviewVO` — 追加 6 个字段

```typescript
export interface PlatformOverviewVO {
  // ── 原有字段（保持不变）─────────────────
  totalTasks: number
  doneTasks: number
  failedTasks: number
  runningTasks: number
  taskSuccessRate: number      // 0~1
  totalNodeRuns: number
  errorNodeRuns: number
  totalTokensIn: number
  totalTokensOut: number
  totalCostCny: number
  avgTaskLatencyMs: number | null

  // ── F4 新增 ────────────────────────────
  /** P95 端到端耗时 ms；窗口内 DONE 任务 < 20 时为 null，前端 fallback 显示 avg。 */
  p95TaskLatencyMs: number | null
  /** 较上一窗口的任务总数变化量，正为增长。 */
  totalTasksDelta: number | null
  /** 成功率绝对差，如 0.016 表示 +1.6 pp。 */
  taskSuccessRateDelta: number | null
  /** Token 成本变化量，单位元。 */
  totalCostCnyDelta: number | null
  /** 平均耗时变化量 ms。 */
  avgTaskLatencyMsDelta: number | null
  /** 对比窗口标签，目前固定 "较昨日"。 */
  compareWindowLabel: string | null
}
```

### 2.3 `ActiveWorkflowVO`（新建）

```typescript
export interface ActiveWorkflowVO {
  name: string
  version: string         // 字符串化的版本号，如 "2"
  file: string            // "classpath:workflow/multi_agent_v1.yaml"
  nodes: string[]         // 按拓扑序的节点 id 列表
  lastLoadedAt: number | null   // epoch millis，null=尚未加载
  cached: boolean         // ⚠️ 注意：后端字段名就是 `cached` 不是 `isCached`
}
```

> ⚠️ **boolean 字段命名规约**：后端 `private boolean cached` 经 Lombok + Jackson 序列化为 JSON key `cached`（不带 `is` 前缀）。前端类型也用 `cached`，不要写 `isCached` 否则永远 `undefined`。这是 F3 落地时踩过 3 次的坑（见 [handoff 第 45-61 行](2026-05-24-backend-api-gaps.md#vo-字段命名规约避免重蹈覆辙)）。

### 2.4 `WorkflowLogVO`（新建）

```typescript
export interface WorkflowLogVO {
  eventType: 'cache_clear' | 'yaml_reload' | 'topology_check' | 'activate'
  message: string
  level: 'info' | 'success' | 'warning' | 'error'
  ts: number              // epoch millis
}
```

### 2.5 `ComponentHealthVO`（新建）

```typescript
export interface ComponentHealthVO {
  name: string            // "API" / "Redis" / "SSE"
  status: 'UP' | 'DOWN' | 'DEGRADED'
  subtitle: string        // "后端接口服务" / "缓存与会话存储" / "流式推送服务"
  latencyMs: number | null
  extra: Record<string, unknown> | null  // SSE 行可能含 { connections: number }
}
```

---

## 3. API 方法新增

### 3.1 在 `frontend/src/api/admin.ts` 追加

```typescript
import type {
  JudgeRunVO,
  PlatformOverviewVO,    // 已有，无需重导
  ActiveWorkflowVO,
  WorkflowLogVO,
} from '@/types/admin'

export const adminApi = {
  // ── 原有方法保持不变 ─────────────────────

  // ── F4 #6：Judge 候选模型列表 ──────────
  /** 获取可选的裁判模型列表（来自后端 application.yml）。 */
  judgeModels(): Promise<string[]> {
    return apiGet('/admin/eval/judge/models')
  },

  // ── F4 #6 + #7：触发评分支持 model / force ──────
  /**
   * 触发一次 Judge 评分。
   * @param taskId  目标任务 ID
   * @param opts.judgeModel  可选，传 `undefined` 用后端默认 qwen-max
   * @param opts.force       默认 false 命中缓存（同 task+model+rubric 直接返回历史），true 强制重新评分
   */
  judgeRun(taskId: number, opts: { judgeModel?: string; force?: boolean } = {}): Promise<JudgeRunVO> {
    const params = new URLSearchParams()
    if (opts.judgeModel) params.append('judgeModel', opts.judgeModel)
    if (opts.force) params.append('force', 'true')
    const qs = params.toString() ? `?${params}` : ''
    return apiPost(`/admin/eval/judge/${taskId}${qs}`)
  },

  // ── F4 #3：当前活跃 Workflow ──────────
  activeWorkflow(): Promise<ActiveWorkflowVO> {
    return apiGet('/admin/workflow/active')
  },

  // ── F4 #4：Workflow 加载历史 ──────────
  workflowHistory(limit = 20): Promise<WorkflowLogVO[]> {
    return apiGet(`/admin/workflow/history?limit=${limit}`)
  },
}
```

> `judgeRun` 的旧签名 `judgeRun(taskId)` 仍然兼容：不传 opts 即 `force=false`、不传 `judgeModel`，等价于 F3 前的行为。

### 3.2 新建 `frontend/src/api/health.ts`

```typescript
import { apiGet } from './http'
import type { ComponentHealthVO } from '@/types/admin'

export const healthApi = {
  /** 系统健康面板用：返回 API / Redis / SSE 三行子服务状态。 */
  components(): Promise<ComponentHealthVO[]> {
    return apiGet('/health/components')
  },
}
```

> 注意路径：`/api/health/components`，不在 `/admin` 下，**不需要 Authorization header**（与现有 `/api/health` 一致）。

---

## 4. Mock 替换清单（5 处）

### 4.1 `AdminJudgeView.vue` — 列表展示 topic（Gap #5）

**改动 1：列表表格的"任务 ID"列扩展为"任务"列**

```vue
<!-- 原 -->
<el-table-column label="任务 ID" prop="taskId" width="120" />

<!-- 改为 -->
<el-table-column label="任务" width="280">
  <template #default="{ row }">
    <div>
      <div class="text-mono">#{{ row.taskId }}</div>
      <div v-if="row.topic" class="text-xs text-gray-500 truncate">{{ row.topic }}</div>
    </div>
  </template>
</el-table-column>
```

**改动 2：Drawer 标题携带主题**

```vue
<el-drawer :title="`评分详情 #${current?.id ?? '-'}${current?.topic ? ' · ' + current.topic : ''}`" ...>
```

### 4.2 `AdminJudgeView.vue` — 裁判模型可选（Gap #6）

**改动 3：触发卡片的裁判模型位置从只读 Badge 改为 el-select**

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api/admin'

const judgeModels = ref<string[]>([])
const selectedJudgeModel = ref<string>('qwen-max')

onMounted(async () => {
  judgeModels.value = await adminApi.judgeModels()
  // 默认选中第一项（也是后端默认）
  if (judgeModels.value.length) selectedJudgeModel.value = judgeModels.value[0]
})
</script>

<template>
  <el-select v-model="selectedJudgeModel" size="small" style="width: 160px;">
    <el-option v-for="m in judgeModels" :key="m" :label="m" :value="m" />
  </el-select>
</template>
```

### 4.3 `AdminJudgeView.vue` — "再次评分"加 force 选项（Gap #7）

**改动 4：触发按钮逻辑**

```typescript
// 首次评分 —— force=false 兜底，DB 无缓存自然走真实调用
async function onStartJudge(taskId: number) {
  const vo = await adminApi.judgeRun(taskId, {
    judgeModel: selectedJudgeModel.value,
  })
  // ... 更新列表
}

// 再次评分 —— 提供两个入口：默认命中缓存（瞬时），二级菜单显式强制
async function onRejudge(taskId: number, force = false) {
  const vo = await adminApi.judgeRun(taskId, {
    judgeModel: selectedJudgeModel.value,
    force,
  })
  // ... 更新列表
}
```

**UI 建议（任选一种）：**

- A. el-dropdown 二级菜单：`再次评分` → `[使用缓存（瞬时）, 强制重新评分（5-15s）]`
- B. 触发按钮旁加 `el-switch` "强制重新评分"，默认关闭
- C. 主按钮"再次评分"默认 `force=false`，旁边小图标按钮 🔄 表示强制重评

推荐 A，最显式。

### 4.4 `AdminWorkflowView.vue` — 当前活跃卡片 + 加载历史（Gap #3 + #4）

**改动 5：删除 mock 引用，改成接口数据**

```typescript
// ❌ 删除
import { mockActiveWorkflow, mockWorkflowLoadLog } from '@/mock/admin-placeholders'

// ✅ 替换为
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import type { ActiveWorkflowVO, WorkflowLogVO } from '@/types/admin'

const activeWorkflow = ref<ActiveWorkflowVO | null>(null)
const workflowLogs = ref<WorkflowLogVO[]>([])

async function refreshAll() {
  const [active, logs] = await Promise.all([
    adminApi.activeWorkflow(),
    adminApi.workflowHistory(20),
  ])
  activeWorkflow.value = active
  workflowLogs.value = logs
}

onMounted(refreshAll)

async function onReload() {
  await adminApi.reloadWorkflow()    // 现有方法，POST /admin/workflow/reload
  await refreshAll()                  // ⚡ 关键：reload 完拉一次活跃 + 历史
  ElMessage.success('Workflow 已热更新')
}
```

模板里 `mockActiveWorkflow.name` → `activeWorkflow?.name`，`lastLoadedAt` 用 `formatEpochMillis(activeWorkflow?.lastLoadedAt)`。

### 4.5 `SystemHealthPanel.vue` — 子服务粒度（Gap #1）

**改动 6：把 `mockSystemHealthExtras` 替换成真实接口**

```typescript
// ❌ 删除
import { mockSystemHealthExtras } from '@/mock/admin-placeholders'

// ✅ 替换为
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { healthApi } from '@/api/health'
import type { ComponentHealthVO } from '@/types/admin'

const components = ref<ComponentHealthVO[]>([])
let timer: number | undefined

async function poll() {
  try {
    components.value = await healthApi.components()
  } catch (e) {
    // 后端 down 时 fallback：本地构造 DOWN 状态
    components.value = [
      { name: 'API', status: 'DOWN', subtitle: '后端接口服务', latencyMs: null, extra: null },
    ]
  }
}

onMounted(() => {
  poll()
  timer = window.setInterval(poll, 60_000)  // 60s 轮询，与原 /api/health 节奏一致
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
```

模板里循环 `components` 渲染三行（API/Redis/SSE）。Redis 行的 `latencyMs` 可显示为副文（如 "12.3 ms"）；SSE 行 `extra.connections` 可显示为 "{N} 个活跃连接"。

### 4.6 `HomeView.vue` + `AdminStatsView.vue` — StatCard 同比 + P95（Gap #2 + #8）

**改动 7：StatCard delta 字段连上 VO**

```typescript
const overview = ref<PlatformOverviewVO | null>(null)
// ... fetch logic 已有

const statItems = computed(() => {
  if (!overview.value) return []
  const v = overview.value
  return [
    {
      label: '总任务数',
      value: v.totalTasks,
      delta: v.totalTasksDelta,
      deltaLabel: v.compareWindowLabel,   // "较昨日"
    },
    {
      label: '成功率',
      value: `${(v.taskSuccessRate * 100).toFixed(1)}%`,
      delta: v.taskSuccessRateDelta != null
        ? `${(v.taskSuccessRateDelta * 100).toFixed(1)}pp`
        : null,
      deltaLabel: v.compareWindowLabel,
    },
    {
      label: '累计成本',
      value: `¥${v.totalCostCny.toFixed(2)}`,
      delta: v.totalCostCnyDelta != null ? `¥${v.totalCostCnyDelta.toFixed(2)}` : null,
      deltaLabel: v.compareWindowLabel,
    },
    {
      // ⚡ P95 优先；窗口内 DONE < 20 时 fallback 显示 avg
      label: v.p95TaskLatencyMs != null ? 'P95 耗时' : '平均耗时',
      value: v.p95TaskLatencyMs ?? v.avgTaskLatencyMs ?? '—',
      delta: v.avgTaskLatencyMsDelta,
      deltaLabel: v.compareWindowLabel,
    },
  ]
})
```

`StatCard.vue` 模板已经在 F3 修复 commit `d66e620` 中把 `delta` 改为可选 `v-if="item.delta"`，所以无需再改。

---

## 5. 单元类型映射速查表

| 后端字段 / 单位 | 前端 typed key | 显示建议 |
|---|---|---|
| `taskSuccessRate: 0~1` | `taskSuccessRate` | `(v * 100).toFixed(1) + '%'` |
| `taskSuccessRateDelta: 绝对差` | `taskSuccessRateDelta` | `(v * 100).toFixed(1) + 'pp'`（pp = 百分点） |
| `totalCostCny: 元` | `totalCostCny` | `¥${v.toFixed(2)}` |
| `p95TaskLatencyMs / avgTaskLatencyMs: ms` | 同名 | `v < 1000 ? v + 'ms' : (v/1000).toFixed(1) + 's'` |
| `lastLoadedAt / ts: epoch millis` | 同名 | `formatEpochMillis(v)` |
| `latencyMs: ms (Double，Health)` | 同名 | `v.toFixed(1) + 'ms'` |

---

## 6. 验收用例（前端集成后逐项核对）

> 全部假设你已经登录拿到 admin token，下文 `<TOKEN>` 替换为 `localStorage.getItem('jwt')` 的值。

### 6.1 Judge 增强（#5/#6/#7）

```bash
# A. /models 返回候选
curl -H "Authorization: Bearer <TOKEN>" http://localhost:8080/api/admin/eval/judge/models
# 期待：{"code":0,"data":["qwen-max","qwen-plus","deepseek-chat"],...}

# B. 首次评分（无缓存）→ 真实调用
curl -X POST -H "Authorization: Bearer <TOKEN>" \
  "http://localhost:8080/api/admin/eval/judge/<DONE_TASK_ID>?judgeModel=qwen-max"
# 期待：5-15s 返回；返回 JudgeRunVO，其中 topic 是任务主题字符串

# C. 再次评分 force=false → 命中缓存（瞬时）
curl -X POST -H "Authorization: Bearer <TOKEN>" \
  "http://localhost:8080/api/admin/eval/judge/<DONE_TASK_ID>?judgeModel=qwen-max&force=false"
# 期待：< 100ms 返回；id 与上一步相同（同一条 evalRun）

# D. 强制重评 force=true → 重新调用
curl -X POST -H "Authorization: Bearer <TOKEN>" \
  "http://localhost:8080/api/admin/eval/judge/<DONE_TASK_ID>?judgeModel=qwen-max&force=true"
# 期待：再次 5-15s；产生新 id

# E. 列表带 topic
curl -H "Authorization: Bearer <TOKEN>" "http://localhost:8080/api/admin/eval/judge?limit=10"
# 期待：每行有非空 topic（如对应任务有 topic）
```

### 6.2 Workflow 审计（#3/#4）

```bash
# A. 活跃信息
curl -H "Authorization: Bearer <TOKEN>" http://localhost:8080/api/admin/workflow/active
# 期待：{ name: "multi_agent_v1", version: "2", file: "classpath:workflow/...", nodes: [...], lastLoadedAt: <ms>, cached: true/false }

# B. 触发 reload → 验证日志写入
curl -X POST -H "Authorization: Bearer <TOKEN>" http://localhost:8080/api/admin/workflow/reload
curl -H "Authorization: Bearer <TOKEN>" "http://localhost:8080/api/admin/workflow/history?limit=10"
# 期待：最近 4 条日志依次为 activate / topology_check / yaml_reload / cache_clear（时间倒序），ts 单调递增
```

### 6.3 系统健康（#1）

```bash
curl http://localhost:8080/api/health/components
# 期待：[
#   { name: "API",   status: "UP", latencyMs: 0.0,  extra: null },
#   { name: "Redis", status: "UP", latencyMs: <小数>, extra: null },
#   { name: "SSE",   status: "UP", latencyMs: null, extra: { connections: 0 } }
# ]
# 停掉本地 Redis 后 Redis 行应变为 status: "DOWN"
```

### 6.4 Stats 同比 + P95（#2 + #8）

```bash
curl -H "Authorization: Bearer <TOKEN>" http://localhost:8080/api/admin/stats/overview
# 期待 VO 包含：
#   compareWindowLabel: "较昨日"
#   totalTasksDelta, taskSuccessRateDelta, totalCostCnyDelta, avgTaskLatencyMsDelta 都非 null（可以为 0）
#   p95TaskLatencyMs: 当日 DONE 任务数 < 20 时为 null，>= 20 时为 ms 数
```

---

## 7. 易踩坑提示（来自 F3 测试经验）

1. **boolean 字段 `cached`**：不要写 `isCached`。原因见 §2.3 注释和 [handoff 字段命名规约](2026-05-24-backend-api-gaps.md#vo-字段命名规约避免重蹈覆辙)。
2. **`createTime` vs `createdAt` 不一致**：F4 没改这个历史问题，`JudgeRunVO.createTime`、`PromptTemplateVO.createTime` 仍是 `createTime`；只有 `TaskBriefVO` 是 `createdAt`。F4 新增的字段（`lastLoadedAt`、`ts`）统一用 `xxxAt` 形式，未来扩 VO 也按这个走。
3. **P95 可能为 null**：当日完成任务 < 20 时后端故意返回 null（统计意义不足）。前端必须 fallback 显示 avg，不要直接 `value.p95TaskLatencyMs.toString()` 否则 NPE。
4. **Workflow `version` 是字符串**：后端 `WorkflowDef.version` 是 Integer，但 VO 出来是 `String`（如 `"2"`）。前端 typed key 保持 `string`，不要做数值比较。
5. **`/api/health/components` 不要带 Authorization header**：与现有 `/api/health` 一致，是无需鉴权的探测接口。带了不会出错，但语义不对。
6. **Workflow reload 后必须重新拉 active + history**：reload 本身只返回 `"reloaded"`，UI 状态要靠两个 GET 刷新；见 §4.4 改动 5 的 `refreshAll()`。

---

## 8. 完成后的清理动作

```bash
# 1. 删除整个 mock 文件（接口都接上后再删，避免半路打 import 错误）
rm frontend/src/mock/admin-placeholders.ts

# 2. 全局搜索还残留的 mock 引用，应该没有
cd frontend && grep -rn "admin-placeholders\|mockActiveWorkflow\|mockWorkflowLoadLog\|mockSystemHealthExtras" src/

# 3. 跑类型检查 + build
npx vue-tsc --noEmit && npm run build
# 期望：0 error
```

完成后可以发版。Admin 中台所有数据全部走真实接口，再无 mock 占位。

---

## 9. 后端联系点（如有问题）

| 问题类型 | 关键文件 |
|---|---|
| Judge 行为不符合预期 | `eval/ReportJudgeService.java` |
| Workflow active/history 数据错 | `workflow/WorkflowLoader.java` + `controller/AdminWorkflowController.java` |
| Stats 同比/P95 数字不对 | `trace/AdminStatsService.java` + `mapper/ReportTaskMapper.java` |
| 健康检查返回不对 | `service/HealthService.java` |
| 部署后 workflow_load_log 写不进去 | 检查 `schema-phase7.sql` 是否执行 |
| 接口认证报 401 | `security/JwtAuthFilter.java`（不在 F4 范围，无变更） |

后端 F4 测试：`mvn test -Dtest='ReportJudgeServiceTest,WorkflowLoaderAuditTest,AdminStatsServiceTest'`（11 个用例全部 green）。
