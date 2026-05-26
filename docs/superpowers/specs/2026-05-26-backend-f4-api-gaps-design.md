# F4 后端 API 缺口补齐 设计规范

> 项目：行业研报多 Agent 协作平台（IRP）
> 阶段：F4（后端补齐 F3 落地后识别的 8 个接口缺口）
> 编写日期：2026-05-26
> 后端技术栈：Spring Boot 3 + MyBatis-Flex + MySQL + Redis + DashScope（qwen-max）
> 后端代码根目录：`src/main/java/com/leo/enterpriseinertraining/`
> 关联：[F3 前端 Spec](2026-05-23-frontend-f3-admin-design.md)、[后端接口缺口清单（handoff）](../handoff/2026-05-24-backend-api-gaps.md)

---

## 1. 目标与范围

### 1.1 目标

把 F3 前端测试期间识别出来的 **8 个后端接口缺口全部补齐**，让前端可以下掉 `frontend/src/mock/admin-placeholders.ts`，所有 Admin 中台数据都走真实接口。

### 1.2 范围

handoff 文档列出的 8 个 gap **全部纳入** F4：

| # | 缺口 | 优先级 | 工作量 | 所属批次 |
|---|---|---|---|---|
| 5 | Judge 评分关联主题 | P1 | 0.5h | A |
| 6 | Judge 裁判模型可选 | P3 | 1h | A |
| 7 | Judge 评分缓存（force 开关） | P1 | 1h | A |
| 3 | Workflow 当前活跃元信息 | P2 | 1h | B |
| 4 | Workflow 加载历史（新表） | P2 | 2h | B |
| 1 | 系统健康子服务粒度 | P3 | 1.5h | B |
| 2 | StatCard 同比指标 | P3 | 2h | B |
| 8 | PlatformOverviewVO P95 | P2 | 1h | B |

合计 ~10h。

### 1.3 不在范围

- **不**重构现有 VO 历史字段命名（`JudgeRunVO.createTime`、`PromptTemplateVO.active` 等保持原样，避免破坏 F3 前端）。
- **不**引入 OpenAPI typed client 生成。
- **不**做多租户隔离重构，沿用现有 `tenant_id` 过滤路径。
- **不**为 Controller 层新增 MockMvc 测试脚手架，只对核心 Service 写 JUnit。

### 1.4 交付节奏（2 个 commit）

| 批次 | 内容 | gap | 预计 |
|---|---|---|---|
| **A：Judge 增强** | `JudgeRunVO.topic` + judge 接口 `judgeModel`/`force` 参数 + `selectLatest` 缓存命中 + `/admin/eval/judge/models` 接口 + `application.yml` 候选模型 + 单测 | #5 #6 #7 | ~3h |
| **B：Workflow & Stats 看板** | `workflow_load_log` 表 + `WorkflowLoader.activeInfo()/history()/appendLog()` + `/admin/workflow/active`、`/admin/workflow/history` + `HealthService` + `/health/components` + `PlatformOverviewVO` 新增 5 个 `*Delta` + `p95TaskLatencyMs` + 单测 | #3 #4 #1 #2 #8 | ~7h |

每个批次结束都是独立可验收的状态：批次 A 后 Judge 页 mock 全清；批次 B 后 Workflow/Stats 页 mock 全清。

---

## 2. 字段命名规约（本次必须遵守）

采纳 handoff 文档第 45–61 行的建议，并加一条本次专属约束：

1. **新增 boolean 字段**：getter 输出 JSON key 必须与字段同名（避免 `private boolean active` 被 Jackson 序列化成 `active` 而前端写 `isActive`）。如果字段名要带 `is` 前缀，加 `@JsonProperty("isXxx")`。
2. **新增时间字段统一 `xxxAt`（epoch millis Long）**：例如 `lastLoadedAt`、`ts`、`compareWindowStartedAt`。不再新增 `createTime` 风格。
3. **新增字段必带 OpenAPI `@Schema(description=...)`**，明确语义和单位（ms / cny / 0~1 / 百分点）。
4. **本次不动**：已有的 `JudgeRunVO.createTime`、`PromptTemplateVO.active` 字段不重命名。

---

## 3. 批次 A 详细设计（#5 + #6 + #7）

### 3.1 VO 改动

`vo/JudgeRunVO.java` 追加：

```java
@Schema(description = "关联任务的研究主题，便于前端列表展示")
private String topic;
```

### 3.2 Service 改动 — `eval/ReportJudgeService.java`

把 `judge(long taskId)` 改成 `judge(long taskId, String judgeModelOverride, boolean force)`：

```java
public JudgeRunVO judge(long taskId, String judgeModelOverride, boolean force) {
    String model = resolveModel(judgeModelOverride);  // override 或注入的默认 judgeModel
    String rubric = currentRubricVersion();

    if (!force) {
        Optional<JudgeRun> cached = judgeRunMapper.selectLatest(taskId, model, rubric);
        if (cached.isPresent()) return toVO(cached.get(), loadTopic(taskId));
    }

    // ... 原有真实 LLM 调用路径，结尾 toVO 时附 topic
}

private String resolveModel(String override) {
    return (override != null && !override.isBlank()) ? override : this.judgeModel;
}
```

`listByTask` / `listForCurrentTenant` 在组装 VO 时通过 `report_task` JOIN 取 topic，避免 N+1。

### 3.3 Mapper 改动 — `mapper/JudgeRunMapper.java`

新增两个方法：

```java
// 用 QueryWrapper 写，按 tenant/task/model/rubric 过滤，按 create_time DESC LIMIT 1
Optional<JudgeRun> selectLatest(long taskId, String judgeModel, String rubricVersion);

// 列表场景 LEFT JOIN report_task 取 topic，返回 (JudgeRun + topic) 投影
List<JudgeRunWithTopic> selectRecentWithTopic(Long tenantId, int limit);
```

### 3.4 Controller 改动

定位现有 judge controller（`controller/EvalJudgeController.java` 或同义类）：

```java
@PostMapping("/{taskId}")
public BaseResponse<JudgeRunVO> judge(
        @PathVariable long taskId,
        @RequestParam(required = false) String judgeModel,
        @RequestParam(defaultValue = "false") boolean force) {
    return ResultUtils.success(judgeService.judge(taskId, judgeModel, force));
}

@GetMapping("/models")
public BaseResponse<List<String>> models() {
    return ResultUtils.success(judgeModelCandidates);  // 从 @Value 注入
}
```

### 3.5 配置 — `application.yml`

```yaml
app:
  dashscope:
    judge-model: qwen-max
    judge-model-candidates:
      - qwen-max
      - qwen-plus
      - deepseek-chat
```

### 3.6 单测 — `eval/ReportJudgeServiceTest.java`

覆盖：

- `force=false` + 数据库已有同 `(taskId, model, rubric)` 评分 → 返回缓存，**不调用 LLM mock**。
- `force=false` + 数据库无缓存 → 调用 LLM mock 一次，落库。
- `force=true` + 数据库已有缓存 → 仍然调用 LLM mock 一次。
- `judgeModelOverride="deepseek-chat"` → 实际调用使用 `deepseek-chat`，落库 model 字段也是它。

LLM 调用用 `@MockBean RestClient` 或 Service 内已封装的 client 接口替身。

---

## 4. 批次 B 详细设计（#3 + #4 + #1 + #2 + #8）

### 4.1 DDL — `src/main/resources/db/schema-phase7.sql`

```sql
-- F4: workflow 加载与重载操作审计
CREATE TABLE IF NOT EXISTS workflow_load_log (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type       VARCHAR(32)  NOT NULL COMMENT 'cache_clear / yaml_reload / topology_check / activate',
    message          VARCHAR(512) NOT NULL,
    level            VARCHAR(16)  NOT NULL DEFAULT 'info' COMMENT 'info / success / warning / error',
    workflow_name    VARCHAR(128),
    workflow_version VARCHAR(32),
    ts               BIGINT       NOT NULL COMMENT 'epoch millis',
    INDEX idx_ts (ts DESC)
) COMMENT='Workflow 加载与重载操作审计';

-- F4: 为同比 / P95 SQL 提供 create_time 索引
ALTER TABLE `report_task` ADD KEY IF NOT EXISTS `idx_create_time` (`create_time`);
```

> MySQL 5.7 / 8.0 早期版本不支持 `ADD KEY IF NOT EXISTS`；如目标库版本不支持，改用先 `SELECT` 信息架构判定再 `ALTER` 的存储过程，或者部署时手工跳过此行。

### 4.2 Workflow #3 + #4 — `workflow/WorkflowLoader.java`

引入 `WorkflowLoadLogMapper`，新增三个方法：

```java
public ActiveWorkflowVO activeInfo() { ... }                   // #3
public List<WorkflowLogVO> history(int limit) { ... }          // #4 (Math.min(limit, 100))
private void appendLog(String eventType, String message, String level) { ... }  // 内部
```

`reload()` 的关键节点写日志：

- 进入 → `cache_clear / "已清空 Workflow YAML 缓存"`
- `readYaml` 成功 → `yaml_reload / "Workflow YAML 已重新解析"`
- 拓扑校验成功 → `topology_check / "拓扑校验通过"`
- 最终生效 → `activate / "Workflow xxx(vY) 已成功生效" / level=success`
- 任意失败 → `activate / "热更新失败：<msg>" / level=error`，然后 rethrow

`activeWorkflowName` 默认 `multi_agent_v1`，先用常量；后续如果有多 workflow 切换需求再做配置化。

### 4.3 Workflow Controller — `controller/AdminWorkflowController.java`

新增：

```java
@GetMapping("/active")
public BaseResponse<ActiveWorkflowVO> active() { ... }

@GetMapping("/history")
public BaseResponse<List<WorkflowLogVO>> history(@RequestParam(defaultValue = "20") int limit) { ... }
```

### 4.4 Health #1 — `service/HealthService.java` + `controller/HealthController.java`

`HealthService.checkAll()` 返回 `List<ComponentHealthVO>`：

- **API** 行：固定 `UP`，`latencyMs = 0`。
- **Redis** 行：`redisTemplate.getConnectionFactory().getConnection().ping()` 计时；失败标 `DOWN`，`latencyMs = null`。
- **SSE** 行：尝试从现有 SSE 连接管理器（`stream/` 包内）拿活跃连接数；拿不到则 `extra` 留空，仍标 `UP`。

新增 endpoint `GET /api/health/components`。原有 `/api/health` 保持不变。

### 4.5 Stats #2 同比 — `service/impl/AdminStatsServiceImpl.java`

> **DB 列名注意**：`report_task` 实际列名是 `create_time`（DATETIME），不是 `created_at`；VO 层在序列化时才转成 `createdAt`。本节所有 SQL 一律使用 `create_time`。

`overview()` 改为做两次时间窗聚合：

- **当前窗口**：默认 `[今天 00:00, 现在)`。
- **对比窗口**：`[昨天 00:00, 今天 00:00)`。
- 窗口边界在 Java 层用 `LocalDate.now(ZoneId.systemDefault()).atStartOfDay()` 计算后传参，SQL 不写死。
- `compareWindowLabel` = `"较昨日"`。

每个 `*Delta` = 当前 - 上一窗口（保留符号，正为增长）。`taskSuccessRateDelta` 单位是绝对差（如 `0.016` 表示 +1.6 pp）。

现有 `report_task` 索引仅 `idx_user_id` / `idx_status` / `idx_tenant`，**本次新增 `INDEX idx_create_time (create_time)` 到 phase7 DDL 末尾**（写法见 4.1 节末尾）。

### 4.6 Stats #8 P95 — 同上 Service 内

新增字段 `p95TaskLatencyMs: Long` 到 `PlatformOverviewVO`。`started_at` / `finished_at` 是 DATETIME，必须用 `TIMESTAMPDIFF(MICROSECOND, ...)` / 1000 得到毫秒：

```sql
SELECT TIMESTAMPDIFF(MICROSECOND, started_at, finished_at) / 1000 AS latency_ms
FROM report_task
WHERE status = 'DONE'
  AND started_at IS NOT NULL AND finished_at IS NOT NULL
  AND create_time >= ? AND create_time < ?
ORDER BY latency_ms
LIMIT 1 OFFSET ?  -- OFFSET 在 Java 层算好：FLOOR(count * 0.95)
```

两步执行：先 `SELECT COUNT(*)` 拿 N，Java 算 `offset = (long) Math.floor(N * 0.95)`，再二次查询。原因：MySQL 不允许 OFFSET 中使用子查询。

窗口内完成任务数 **< 20** → P95 不稳定，直接返回 `null`（不发第二条 SQL），前端 fallback 显示 avg。

### 4.7 VO 改动 — `vo/PlatformOverviewVO.java`

追加：

```java
private Long   totalTasksDelta;
private Double taskSuccessRateDelta;
private Double totalCostCnyDelta;
private Long   avgTaskLatencyMsDelta;
private String compareWindowLabel;     // "较昨日"
private Long   p95TaskLatencyMs;       // #8，可能为 null
```

### 4.8 单测

- `workflow/WorkflowLoaderAuditTest.java`：mock `WorkflowLoadLogMapper`，调用 `reload()` 验证 4 条日志按预期顺序写入；模拟 YAML 解析失败验证写 error 日志后 rethrow。
- `service/AdminStatsServiceTest.java`：mock 任务数据，验证当前 / 上一窗口的边界计算正确（`compareWindowLabel = "较昨日"`、`*Delta = 当前 - 上一`），验证完成数 < 20 时 P95 返回 null。

---

## 5. 验收清单

每个批次合并前必须满足：

### 批次 A 验收

- [ ] `mvn -q -DskipTests=false test -Dtest=ReportJudgeServiceTest` 通过。
- [ ] `POST /admin/eval/judge/{id}?force=false`：同 `(task, model, rubric)` 第二次请求 ≤ 100ms（命中缓存路径）。
- [ ] `POST /admin/eval/judge/{id}?force=true`：每次都重新评分，耗时 5-15s。
- [ ] `POST /admin/eval/judge/{id}?judgeModel=qwen-plus`：落库的 `judge_run.judge_model = 'qwen-plus'`。
- [ ] `GET /admin/eval/judge/models` 返回与 `application.yml` 一致的列表。
- [ ] `GET /admin/eval/judge/recent` 返回值的每一项包含非空 `topic`（任务有 topic 时）。

### 批次 B 验收

- [ ] `mvn -q -DskipTests=false test -Dtest=WorkflowLoaderAuditTest,AdminStatsServiceTest` 通过。
- [ ] 启动后 `GET /admin/workflow/active` 返回真实数据，`lastLoadedAt` 非空。
- [ ] 调用 `POST /admin/workflow/reload`，再 `GET /admin/workflow/history?limit=10`，能看到刚写入的 4 条日志（cache_clear → yaml_reload → topology_check → activate）。
- [ ] `GET /api/health/components` 返回 3 行（API / Redis / SSE）；停掉 Redis 后 Redis 行 status=DOWN。
- [ ] `GET /admin/stats/overview` 返回值包含 5 个 `*Delta` 字段、`compareWindowLabel = "较昨日"`、`p95TaskLatencyMs`（可为 null）。
- [ ] 前端 `mock/admin-placeholders.ts` 可整体删除，`HomeView` / 4 个 Admin 页面零 mock。

---

## 6. 风险与回滚

| 风险 | 缓解 |
|---|---|
| `WorkflowLoader.reload()` 改造后日志写库失败可能阻塞热更新 | `appendLog` try-catch，写库失败只打 `log.warn`，不影响主流程 |
| `selectLatest` 命中缓存绕过 LLM 后，业务方期望"再次评分" UI 行为变化 | 通过 `force=true` 显式覆盖；前端默认按钮文案不变，由前端在二级菜单提供 "强制" |
| P95 SQL 在大数据量下慢 | 走 `created_at` 索引；本次新增 `idx_created_at`；数据量大于百万再考虑 Redis ZSet 方案 |
| Phase7 DDL 在已部署环境需手工执行 | DDL 全部 `IF NOT EXISTS`，重复执行幂等 |

回滚：每批次单独 commit，`git revert <sha>` 即可回滚某批次而不影响另一批次。
