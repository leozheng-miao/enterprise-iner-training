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

返回示例：

```typescript
[
  { name: 'API',   status: 'UP', latencyMs: 78,  subtitle: '后端接口服务' },
  { name: 'Redis', status: 'UP', latencyMs: 1.2, subtitle: '缓存与会话存储' },
  { name: 'SSE',   status: 'UP', latencyMs: null, subtitle: '流式推送服务',
    extra: { connections: 12 } }
]
```

**关联文件**：`frontend/src/components/admin/SystemHealthPanel.vue`、`frontend/src/mock/admin-placeholders.ts`

---

## #2 StatCard 同比指标

**前端当前实现**：`HomeView` 与 `AdminStatsView` 4 个 StatCard 不显示「较昨日 ±x.x%」（`StatCard.vue` 的 `delta` 字段已改为可选，模板用 `v-if="item.delta"` 控制渲染）。

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

返回示例：

```typescript
{
  name: 'multi_agent_v1',
  version: 'v2',
  file: 'classpath:workflow/multi_agent_v1.yaml',
  nodes: ['Planner', 'Researcher', 'Analyst', 'Writer', 'Critic'],
  lastLoadedAt: 1716534000000,
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

返回示例：

```typescript
[
  { eventType: 'cache_clear', message: '已清空 Workflow YAML 缓存…',           ts: 1716534000000, level: 'info' },
  { eventType: 'yaml_reload', message: '成功加载 classpath:workflow/multi_agent_v1.yaml (v2)', ts: 1716534000050, level: 'success' }
]
```

建议在 `WorkflowLoader.reload()` 内写一条审计日志到 `workflow_load_log` 表（新建）。

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

## 已知非阻塞偏差（前端实现层面）

以下不是后端缺口，是前端实现时与 plan 的小偏差（已记录便于追溯）：

- **`CloudFilled` 图标替换**：plan 在 `SystemHealthPanel.vue` 中引用 `@element-plus/icons-vue` 的 `CloudFilled`，但该图标实际不存在，已替换为 `Cloudy`。`mock/admin-placeholders.ts` 中 `HealthRow.iconName` 联合类型仍保留 `'CloudFilled'` 作为未来 mock 数据扩展位。

- **Task 2 类型迁移补充**：plan 仅列出更新 `StatCard.vue` / `RecentActivityList.vue` 两处 import，但实际还需更新 `CoreCapabilityCard.vue` / `StageOverview.vue` / `StageStepBar.vue` 三处（执行时一并修复）。

- **预先存在的 TS implicit-any 错误修复**：plan 验证步骤要求 `npm run build` 干净，但 main 分支上 `MarkdownRenderer.vue` 与 `TraceTable.vue` 已存在 3 处 TS7022/7023/7006 错误（pre-existing）。P0 commit 顺手修复，使 `npm run build` 可用。

---

## 接入说明

后端补好某接口后，前端只需要做两件事：

1. 在 `frontend/src/types/admin.ts` 增加对应类型，在 `frontend/src/api/admin.ts` 增加对应方法
2. 把目标文件里 `TODO(backend-api-gap #N)` 注释处的 mock 引用替换为接口调用

mock 数据可在所有 gap 补齐后整体删除 `frontend/src/mock/admin-placeholders.ts`。
