# 后端接口缺口清单（F3 前端测试后修订版）

> 编写日期：2026-05-24（2026-05-25 测试后大幅修订）
> 上下文：F3 前端 Admin 中台落地 + 端到端测试后，下面是真正需要后端配合的工作。前端在缺接口的地方先以 mock 占位（集中在 `frontend/src/mock/admin-placeholders.ts`），等后端补完即可替换。
> 关联：[F3 Spec](../specs/2026-05-23-frontend-f3-admin-design.md)、[F3 Plan](../plans/2026-05-24-frontend-f3-admin-implementation.md)
> 后端代码根目录：`src/main/java/com/leo/enterpriseinertraining/`

---

## 目录

- [优先级矩阵 / 推荐执行顺序](#优先级矩阵--推荐执行顺序)
- [VO 字段命名规约（避免重蹈覆辙）](#vo-字段命名规约避免重蹈覆辙)
- [Gap #1 系统健康子服务粒度](#gap-1-系统健康子服务粒度)
- [Gap #2 StatCard 同比指标](#gap-2-statcard-同比指标)
- [Gap #3 Workflow 当前活跃元信息](#gap-3-workflow-当前活跃元信息)
- [Gap #4 Workflow 加载历史](#gap-4-workflow-加载历史)
- [Gap #5 Judge 评分关联主题](#gap-5-judge-评分关联主题)
- [Gap #6 Judge 评分裁判模型可选](#gap-6-judge-评分裁判模型可选)
- [Gap #7 Judge 评分缓存（"再次评分"幂等）](#gap-7-judge-评分缓存再次评分幂等)
- [Gap #8 PlatformOverviewVO P95 耗时](#gap-8-platformoverviewvo-p95-耗时)
- [前端测试期间已自行解决的 plan 错误](#前端测试期间已自行解决的-plan-错误)

---

## 优先级矩阵 / 推荐执行顺序

| # | 缺口 | 影响 | 工作量 | 推荐优先级 | 建议归批 |
|---|---|---|---|---|---|
| 5 | Judge 评分关联主题 | Judge 列表当前只显示 `Task #id`，没主题不直观 | 0.5h | **P1** | 与 #6/#7 一批 |
| 7 | Judge 评分缓存 | "再次评分" 当前会重新调用 LLM 产生 ±0.5 波动，业务可能希望幂等 | 1h | **P1** | 与 #5/#6 一批 |
| 3 | Workflow 当前活跃元信息 | Workflow 页左卡是 mock，没真实数据 | 1h | **P2** | 与 #4 一批 |
| 4 | Workflow 加载历史 | Workflow 页底部时间轴是 mock | 2h | **P2** | 与 #3 一批，共用 `WorkflowLoader` 改造 |
| 8 | PlatformOverviewVO P95 耗时 | StatCard "P95 耗时" 已改名为"平均耗时"（用 avgTaskLatencyMs 兜底） | 1h | **P2** | 看板增强 |
| 1 | 系统健康子服务粒度 | Stats 页系统健康面板 Redis/SSE 两行 mock 显示"正常" | 1.5h | **P3** | 看板增强 |
| 2 | StatCard 同比指标 | 4 张 StatCard 当前无"较昨日 ±x%" | 2h | **P3** | 看板增强 |
| 6 | Judge 裁判模型可选 | 触发卡上裁判模型只读，无法切换 | 1h | **P3** | Judge 增强 |

**总计**：~10 小时。建议两批合并执行：
- **批次 A（Judge 增强，~3h）**：#5 + #6 + #7（涉及 `JudgeRunVO` / `ReportJudgeService`）
- **批次 B（Workflow 与 Stats 看板增强，~7h）**：#3 + #4 + #1 + #2 + #8（涉及 `WorkflowLoader` / `AdminStatsService` 及新表 `workflow_load_log`）

---

## VO 字段命名规约（避免重蹈覆辙）

F3 前端开发过程中**踩了 3 个字段映射坑**，根因都在后端 Lombok + Jackson 默认序列化行为：

| 后端字段声明 | Jackson 实际序列化为 | 前端容易误写 |
|---|---|---|
| `private boolean active;` | `active` | `isActive`（**踩坑**：`PromptTemplateVO`，前端写成 `isActive` 永远拿不到值）|
| `@Data` 上 `boolean` 字段 | 同上 | 同上 |
| `private Long createTime;` | `createTime` | `createdAt`（不一致：`TaskBriefVO` 用 `createdAt`，`PromptTemplateVO` 与 `JudgeRunVO` 用 `createTime`）|
| `private Long startedAt;` / `private Long finishedAt;` | 不存在！ | `TaskBriefVO` 只有 `latencyMs`（耗时直接给）+ `createdAt`，没有 startedAt/finishedAt |

**给后端的建议**（不在本次 gap 范围，但请未来扩 VO 时遵守）：

1. **boolean 字段建议显式加 `@JsonProperty("isXxx")`**，否则 Lombok getter 的 `isActive()` Jackson 默认序列化为 `active`，前端、其他 SDK 都容易踩坑。
2. **时间字段统一命名**：建议全部 VO 用 `createdAt` / `updatedAt`（动词过去式），与目前 `report_task` 表的字段保持一致，不要混用 `createTime`/`createdAt`。
3. **加 OpenAPI 注解**：在 `@Schema(description=...)` 中明确每个字段的语义和单位（ms / cny / 0~1 等），前端按 Swagger 生成 typed client 就能避免猜字段。

---

## Gap #1 系统健康子服务粒度

**前端现状**：`SystemHealthPanel.vue` 顶部一条「API 服务」走真实 `GET /api/health` + 60s 轮询；下面「Redis 缓存」「SSE 服务」两条从 `mock/admin-placeholders.ts: mockSystemHealthExtras` 读 mock，固定显示「正常」。

**用户可见症状**：Stats 页系统健康面板显示 3 条都"正常"，但其实 Redis/SSE 是假的。

**建议后端接口**

```java
// HealthController.java
@GetMapping("/components")
@Operation(summary = "返回各子服务健康状态，供 Admin 系统健康面板使用")
public BaseResponse<List<ComponentHealthVO>> components() {
    return ResultUtils.success(healthService.checkAll());
}
```

```java
// 新增 vo/ComponentHealthVO.java
@Data @NoArgsConstructor @AllArgsConstructor
public class ComponentHealthVO {
    private String name;           // "API" / "Redis" / "SSE"
    private String status;         // "UP" / "DOWN" / "DEGRADED"
    private String subtitle;       // "后端接口服务" 等说明
    private Double latencyMs;      // ping 耗时；null 表示不适用（如 SSE）
    private Map<String, Object> extra;  // 可扩展，如 SSE 的 connections
}
```

```java
// 新增 health/HealthService.java
@Service @RequiredArgsConstructor
public class HealthService {
    private final RedisTemplate<String, ?> redisTemplate;
    private final SseHub sseHub;   // 现有的 SSE 连接管理（如有）

    public List<ComponentHealthVO> checkAll() {
        List<ComponentHealthVO> rows = new ArrayList<>();
        rows.add(new ComponentHealthVO("API", "UP", "后端接口服务", 0.0, null));

        long t = System.nanoTime();
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            double ms = (System.nanoTime() - t) / 1_000_000.0;
            rows.add(new ComponentHealthVO("Redis", "UP", "缓存与会话存储", ms, null));
        } catch (Exception e) {
            rows.add(new ComponentHealthVO("Redis", "DOWN", "缓存与会话存储", null, null));
        }

        int conns = sseHub.activeConnectionCount();   // 若无此 API 可省
        rows.add(new ComponentHealthVO("SSE", "UP", "流式推送服务", null,
                Map.of("connections", conns)));
        return rows;
    }
}
```

**前端验收**：`SystemHealthPanel.vue` 把 `mockSystemHealthExtras` 改成 `apiGet<ComponentHealthVO[]>('/health/components')`，60s 轮询同一接口；删除 `mock/admin-placeholders.ts` 中 `mockSystemHealthExtras`。

**工作量**：~1.5h（含写少量单测）

**关联文件**：`frontend/src/components/admin/SystemHealthPanel.vue`、`frontend/src/mock/admin-placeholders.ts`

---

## Gap #2 StatCard 同比指标

**前端现状**：`HomeView` 与 `AdminStatsView` 的 4 张 StatCard 不显示「较昨日 ±x.x%」副行（`StatCard.delta` 已改为可选，模板 `v-if="item.delta"`）。

**建议方案 A**（推荐）：扩 `PlatformOverviewVO`，每个核心指标加 `*Delta` 字段（与上一时间窗的绝对差）。

```java
// PlatformOverviewVO.java 追加
private Long  totalTasksDelta;        // 较上一周期任务数变化量（正为增长）
private Double taskSuccessRateDelta;  // 成功率绝对差，如 +0.016 = +1.6 个百分点
private Double totalCostCnyDelta;
private Long  avgTaskLatencyMsDelta;
private String compareWindowLabel;    // "较昨日" / "较上周"，前端直接展示
```

`AdminStatsService.overview()` 增加两次聚合：当前窗口 + 上一窗口（默认"昨日 00:00 ~ 今天 00:00" vs "前天 00:00 ~ 昨天 00:00"），SQL：

```sql
SELECT COUNT(*) FROM report_task WHERE created_at >= ? AND created_at < ?
```

**建议方案 B**：新建独立接口 `GET /admin/stats/overview/compare?range=1d`，返回 `{ current: PlatformOverviewVO, previous: PlatformOverviewVO, label: '较昨日' }`，前端自己算同比。**不推荐**（前端要做两次请求等）。

**前端验收**：`StatCard.delta` 用 VO 的 `*Delta` 字段填充；`HomeView` / `AdminStatsView` 的 statItems computed 增加 `delta` 字段。

**工作量**：~2h（含两次聚合的 SQL 索引检查）

**关联文件**：`frontend/src/views/HomeView.vue`、`frontend/src/views/admin/AdminStatsView.vue`、`frontend/src/components/stat/StatCard.vue`

---

## Gap #3 Workflow 当前活跃元信息

**前端现状**：`AdminWorkflowView.vue` 左上「当前活跃 Workflow」卡片全部来自 `mock/admin-placeholders.ts: mockActiveWorkflow`：

```typescript
{ name: 'multi_agent_v1', version: 'v2',
  file: 'classpath:workflow/multi_agent_v1.yaml',
  nodes: ['Planner', 'Researcher', 'Analyst', 'Writer', 'Critic'],
  lastLoadedAt: '2026-05-24 09:00:00', cached: true }
```

**症状**：永远显示这一组数据，即使后端切了 workflow 也看不到。

**注意**：`WorkflowLoader` 当前实现是 `Map<String, WorkflowDef> cache`，**没有"当前活跃 workflow"概念**（cache 可同时容纳多个 workflow 定义）。要暴露"活跃 workflow"需要在 `WorkflowLoader` 内显式追踪：

```java
// AdminWorkflowController.java 追加
@GetMapping("/active")
@Operation(summary = "当前活跃 Workflow 元信息（用于 Admin 看板）")
public BaseResponse<ActiveWorkflowVO> active() {
    return ResultUtils.success(workflowLoader.activeInfo());
}
```

```java
// 新增 vo/ActiveWorkflowVO.java
@Data @NoArgsConstructor @AllArgsConstructor
public class ActiveWorkflowVO {
    private String name;             // "multi_agent_v1"
    private String version;          // "v2"
    private String file;             // "classpath:workflow/multi_agent_v1.yaml"
    private List<String> nodes;      // ["Planner", ...] 按拓扑序
    private Long lastLoadedAt;       // epoch millis
    private boolean cached;          // 是否已在内存缓存中
}
```

```java
// WorkflowLoader.java 增加
private volatile String activeWorkflowName = "multi_agent_v1";  // 从配置/数据库读
private volatile Long lastLoadedAt;

public ActiveWorkflowVO activeInfo() {
    WorkflowDef def = cache.get(activeWorkflowName);
    if (def == null) {
        def = readYaml(activeWorkflowName);
        lastLoadedAt = System.currentTimeMillis();
    }
    return new ActiveWorkflowVO(
        def.getName(), def.getVersion(),
        "classpath:workflow/" + def.getName() + ".yaml",
        def.getNodes().stream().map(n -> n.getName()).toList(),
        lastLoadedAt,
        cache.containsKey(activeWorkflowName)
    );
}
```

**前端验收**：`AdminWorkflowView.vue` 把 `mockActiveWorkflow` 改成 `await adminApi.activeWorkflow()`；前端 `lastLoadedAt` 用 `formatEpochMillis()` 渲染。删除 `mockActiveWorkflow`。

**工作量**：~1h

**关联文件**：`frontend/src/views/admin/AdminWorkflowView.vue`、`frontend/src/mock/admin-placeholders.ts`、`frontend/src/api/admin.ts`

---

## Gap #4 Workflow 加载历史

**前端现状**：`AdminWorkflowView.vue` 底部「最近加载日志」时间轴来自 `mockWorkflowLoadLog`（4 条固定时间戳）。

**症状**：永远显示同样 4 条；点"清空缓存并热更新"按钮后日志区域不刷新。

**建议方案**：新建审计表 + 接口。

**Schema**（建议建在 `report` 库或独立 `audit` 库）：

```sql
CREATE TABLE workflow_load_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_type  VARCHAR(32) NOT NULL COMMENT 'cache_clear / yaml_reload / topology_check / activate',
  message     VARCHAR(512) NOT NULL,
  level       VARCHAR(16)  NOT NULL DEFAULT 'info' COMMENT 'info / success / warning / error',
  workflow_name VARCHAR(128),
  workflow_version VARCHAR(32),
  ts          BIGINT NOT NULL,
  INDEX idx_ts (ts DESC)
) COMMENT='Workflow 加载与重载操作审计';
```

**接口**：

```java
@GetMapping("/history")
@Operation(summary = "Workflow 加载历史（用于 Admin 日志时间轴）")
public BaseResponse<List<WorkflowLogVO>> history(
    @RequestParam(defaultValue = "20") int limit) {
    return ResultUtils.success(workflowLoader.history(Math.min(limit, 100)));
}
```

```java
// 新增 vo/WorkflowLogVO.java
@Data @NoArgsConstructor @AllArgsConstructor
public class WorkflowLogVO {
    private String eventType;      // cache_clear / yaml_reload / ...
    private String message;
    private String level;          // info / success / warning / error
    private Long ts;               // epoch millis
}
```

**关键修改**：`WorkflowLoader.reload()` 与 `readYaml()` 在关键节点写日志：

```java
public synchronized void reload() {
    appendLog("cache_clear", "已清空 Workflow YAML 缓存，准备重新加载", "info");
    cache.clear();
    try {
        WorkflowDef def = readYaml(activeWorkflowName);   // 触发 yaml_reload + 节点校验
        appendLog("activate", "Workflow " + def.getName() + "(" + def.getVersion()
                + ") 已成功生效", "success");
    } catch (Exception e) {
        appendLog("activate", "热更新失败：" + e.getMessage(), "error");
        throw e;
    }
}

private void appendLog(String eventType, String message, String level) {
    workflowLoadLogMapper.insert(new WorkflowLoadLog(eventType, message, level,
        activeWorkflowName, currentVersion, System.currentTimeMillis()));
}
```

**前端验收**：`AdminWorkflowView.vue` 把底部时间轴的数据源改成 `await adminApi.workflowHistory(20)`；点"清空缓存并热更新"后再次拉取日志，时间轴自动刷新最新条目。

**工作量**：~2h（含建表 + Mapper + 单测）

**关联文件**：`frontend/src/views/admin/AdminWorkflowView.vue`、`frontend/src/mock/admin-placeholders.ts`

---

## Gap #5 Judge 评分关联主题

**前端现状**：`AdminJudgeView.vue` 列表首列只显示 `Task #{taskId}`，**没研究主题**，对运营/产品同学来说很难辨识是哪个任务。

**症状**：列表行不可读，必须点详情才能知道这是哪个研报。

**建议方案 A**（推荐）：在 `JudgeRunVO` 增加 `topic` 字段，由 `ReportJudgeService.judge()` 在写库或返回时 join 取 `report_task.topic`。

```java
// JudgeRunVO.java 追加
private String topic;   // 关联任务的研究主题，便于前端列表展示
```

```java
// ReportJudgeService.judge() 内（或返回前组装 VO 时）
JudgeRunVO vo = ...;
vo.setTopic(task.getTopic());
return vo;
```

**listByTask / listForCurrentTenant** 走 SQL JOIN：

```sql
SELECT j.*, t.topic
FROM judge_run j
LEFT JOIN report_task t ON j.task_id = t.id
WHERE j.tenant_id = ? ORDER BY j.create_time DESC LIMIT ?
```

**方案 B**（不推荐）：前端在拿到 `judgeRecent` 后批量调 `/admin/tasks` 反查。N+1 查询，性能差。

**前端验收**：`AdminJudgeView.vue` 列表的"任务 ID"列扩展为"任务"列：上面 `#id`，下面研究主题（多行小字 truncate）。Drawer 标题也可改为 `评分详情：${topic}`。

**工作量**：~0.5h

**关联文件**：`frontend/src/views/admin/AdminJudgeView.vue`、`frontend/src/types/admin.ts`（types 增加 `topic`）

---

## Gap #6 Judge 评分裁判模型可选

**前端现状**：触发卡片裁判模型位置**只读 Badge 显示 `qwen-max`**，无法切换。

**症状**：想用 `deepseek-chat` 或 `gpt-4o` 当裁判对比时无路径。

**建议方案**：

**Step 1**：`POST /admin/eval/judge/{taskId}` 接收可选 query 参数 `judgeModel`：

```java
@PostMapping("/{taskId}")
public BaseResponse<JudgeRunVO> judge(
    @PathVariable long taskId,
    @RequestParam(required = false) String judgeModel) {
    return ResultUtils.success(judgeService.judge(taskId, judgeModel));
}
```

```java
// ReportJudgeService 改造
public JudgeRunVO judge(long taskId, String judgeModelOverride) {
    String model = (judgeModelOverride != null && !judgeModelOverride.isBlank())
        ? judgeModelOverride
        : this.judgeModel;  // 默认 qwen-max
    // ... 用 model 替换 @Value 注入的值传给 RestClient
}
```

**Step 2**：新增 `GET /admin/eval/judge/models` 返回可选列表：

```java
@GetMapping("/models")
public BaseResponse<List<String>> models() {
    return ResultUtils.success(List.of("qwen-max", "qwen-plus", "deepseek-chat"));
}
```

更优：把可选模型放 `application.yml`：

```yaml
app:
  dashscope:
    judge-model: qwen-max
    judge-model-candidates:
      - qwen-max
      - qwen-plus
      - deepseek-chat
```

**前端验收**：触发卡片裁判模型位置改成 `el-select`（候选来自 `/admin/eval/judge/models`），选中后随 `judgeRun` 请求一起发；Drawer 元信息「裁判模型」字段已展示实际用的 model，无需改。

**工作量**：~1h

**关联文件**：`frontend/src/views/admin/AdminJudgeView.vue`、`frontend/src/types/admin.ts`、`frontend/src/api/admin.ts`

---

## Gap #7 Judge 评分缓存（"再次评分"幂等）

**前端现状**：「再次评分」按钮会真的再调一次 LLM，由于 LLM 服务端微小不确定性（即使 `temperature=0`），同一任务两次评分可能差 ±0.5 分。这是**测试中用户反馈的 issue #7**：

> 在没有任何修改的情况下，对某一个 task "再次评分" 后评分会变，在业务逻辑上正常情况下是不是应该与第一次评分结果保持一致？

**业务决策**：取决于「再次评分」的语义：

- **语义 A（重新评估）**：每次都真实调 LLM，接受波动 — **目前的行为**。优点：可以拿多次评分取平均提高稳定性；缺点：成本翻倍 + 用户疑惑。
- **语义 B（幂等返回最近一次）**：同 `(task_id, rubric_version, judge_model)` 命中缓存就返回历史评分，不重新调用。优点：稳定 + 省钱；缺点：失去多次评估的可能性。
- **语义 C（开关）**：接口加 `force=true` 参数。默认走缓存，`force=true` 强制重新评。

**推荐方案 C**，最灵活：

```java
@PostMapping("/{taskId}")
public BaseResponse<JudgeRunVO> judge(
    @PathVariable long taskId,
    @RequestParam(required = false) String judgeModel,
    @RequestParam(defaultValue = "false") boolean force) {
    return ResultUtils.success(judgeService.judge(taskId, judgeModel, force));
}
```

```java
public JudgeRunVO judge(long taskId, String judgeModelOverride, boolean force) {
    String model = resolveModel(judgeModelOverride);
    String rubric = currentRubricVersion();

    if (!force) {
        // 查最近一次同 (taskId, judgeModel, rubricVersion) 的评分
        Optional<JudgeRun> cached = judgeRunMapper.selectLatest(taskId, model, rubric);
        if (cached.isPresent()) return toVO(cached.get());
    }

    // ... 真实调用 LLM
}
```

**前端验收**：
- 「开始评分」默认 `force=false`（首次评分时数据库无缓存就走真实调用，后续就稳定返回）
- 「再次评分」按钮在 UI 上加二级菜单：「使用缓存（瞬时）」/「强制重新评分（5-15s）」，分别传 `force=false` / `true`
- 或更简单：UI 默认 `force=false`，加 `el-switch` "强制重新评分"

**工作量**：~1h

**关联文件**：`frontend/src/views/admin/AdminJudgeView.vue`、`frontend/src/api/admin.ts`、`frontend/src/components/admin/JudgeDetailDrawer.vue`

---

## Gap #8 PlatformOverviewVO P95 耗时

**前端现状**：原 plan 假设后端有 `p95LatencyMs` 字段，但**实际 `PlatformOverviewVO` 只有 `avgTaskLatencyMs`**。前端已在 fix commit 中把 StatCard 的"P95 耗时"标签改为"平均耗时"，读 `avgTaskLatencyMs` 兜底，但**P95 在可观测意义上比平均更有价值**（避免长尾被平均稀释）。

**症状**：平均耗时无法反映尾部延迟。比如 1 个 30s 任务 + 9 个 5s 任务，平均=7.5s，但 P95≈30s 才能反映用户体验最差情况。

**建议后端方案**：在 `PlatformOverviewVO` 增加 `p95TaskLatencyMs: Long`，由 SQL 窗口函数计算：

```sql
SELECT
  PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY (finished_at - started_at))
FROM report_task
WHERE status = 'DONE' AND finished_at IS NOT NULL
```

如果 MySQL 不支持 `PERCENTILE_CONT`（5.7/8.0 都不支持），用近似算法：

```sql
SELECT (finished_at - started_at) AS latency_ms
FROM report_task
WHERE status = 'DONE' AND finished_at IS NOT NULL
ORDER BY latency_ms
LIMIT 1 OFFSET (SELECT FLOOR(COUNT(*) * 0.95) FROM report_task WHERE status = 'DONE')
```

或者用 Redis SortedSet 在每个任务结束时 ZADD 耗时，定时取 95 分位（更高效，适合大数据量）。

**前端验收**：StatCard "平均耗时" → "P95 耗时"，读 `p95TaskLatencyMs`；保留 `avgTaskLatencyMs` 作为副指标用 tooltip 展示。

**工作量**：~1h（含 SQL 优化）

**关联文件**：`frontend/src/views/HomeView.vue`、`frontend/src/views/admin/AdminStatsView.vue`、`frontend/src/types/admin.ts`

---

## 前端测试期间已自行解决的 plan 错误

这些**不是**后端要做的事，仅作为追溯记录，避免后端误以为前端在等：

### 1. `TaskBriefVO` 字段名误读
- plan 中假设字段 `startedAt` / `finishedAt`，实际后端只有 `latencyMs` + `createdAt`。已在 `d66e620` fix 修正前端 types/AdminTaskListView/HomeView 字段引用。

### 2. `PromptTemplateVO` 字段名误读
- plan 中写 `isActive` / `createdAt`，实际后端字段为 `active` / `createTime`（Lombok boolean + Jackson 序列化规则）。已在 `d66e620` fix 修正。

### 3. `PlatformOverviewVO` 字段名误读
- plan 中写 `successRate` / `p95LatencyMs`，实际后端字段为 `taskSuccessRate` / `avgTaskLatencyMs`（**没有 P95**）。前端已校准到正确字段；P95 列入 Gap #8 待后端补。

### 4. `ModelCostVO.avgLatencyMs` 误以为缺失
- 原 plan 标 `#3 RecentModelCallsTable 平均耗时列固定显示 —` 为缺口，但**后端 `ModelCostVO` 已经有 `avgLatencyMs: Long` 字段**。前端 plan 写错了，已校准前端读取。Gap #3 从清单移除。

### 5. `CloudFilled` 图标替换
- plan 在 `SystemHealthPanel.vue` 引用 `@element-plus/icons-vue` 的 `CloudFilled`，但该图标实际不存在，已替换为 `Cloudy`。

### 6. 预先存在的 TS implicit-any
- main 上 `MarkdownRenderer.vue` / `TraceTable.vue` 有 3 处 TS7022/7023/7006 错误（pre-existing，与 F3 无关）。P0 commit 顺手修复使 `npm run build` 可用。

---

## 接入说明（每补一个 gap 后前端的动作）

后端补好某接口后，前端通常按这个步骤接入：

1. 在 `frontend/src/types/admin.ts` 增加对应 VO 类型（注意 boolean 字段名规则）
2. 在 `frontend/src/api/admin.ts` 增加对应方法（按需 GET/POST 工具函数）
3. 找到目标文件里 `// TODO(backend-api-gap #N)` 注释处，把 mock 引用替换为接口调用
4. 跑 `cd frontend && npx vue-tsc --noEmit && npm run build`，确保 0 error
5. 浏览器手动验证：参考 `F3 测试用例`第 4 / 6 / 7 / 8 节对应页面

mock 数据全部 gap 补齐后可整体删除 `frontend/src/mock/admin-placeholders.ts`。
