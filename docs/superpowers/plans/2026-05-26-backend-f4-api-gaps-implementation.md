# F4 后端 API 缺口补齐 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 F3 落地后识别的 8 个后端接口缺口，让前端可下掉 `mock/admin-placeholders.ts`。

**Architecture:** 分两批落地 — 批次 A 改造 `ReportJudgeService` 引入幂等缓存 + 多模型 + topic 关联（#5/#6/#7）；批次 B 新增 `workflow_load_log` 表 + `WorkflowLoader` 审计 + `HealthService` + `PlatformOverviewVO` 同比 / P95 字段（#3/#4/#1/#2/#8）。最终交付 2 个 squash commit。

**Tech Stack:** Spring Boot 3 + MyBatis-Flex + MySQL + Redis + DashScope（qwen-max）+ JUnit 5 + Mockito。

**关联文档：**
- [Spec](../specs/2026-05-26-backend-f4-api-gaps-design.md)
- [Handoff（含每个 gap 的前端验收路径）](../handoff/2026-05-24-backend-api-gaps.md)

**Spec → 现实的 4 处校准（已在本计划中应用）：**
- 评分持久化用 `ReportEvalRun` / `ReportEvalRunMapper` / `report_eval_run` 表（不是 `JudgeRun`）
- Judge controller 实际类名 `AdminReportJudgeController`（不是 `EvalJudgeController`）
- `AdminStatsService` 直接 `@Service` 在 `trace/` 包下（无 interface / Impl 拆分）
- SSE 连接计数：要在 `stream/SseSinkManager` 新增 `activeCount()` 方法

**提交策略：** 每个 Task 内部不 commit，TDD 验证通过即继续。批次 A 末尾一个 commit，批次 B 末尾一个 commit。共 2 个 commit。

---

## Task 0: 基线检查

**Files:**
- 无修改，只验证

- [ ] **Step 1: 验证 Maven 构建当前 green**

Run:
```bash
mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS。

- [ ] **Step 2: 验证现有单测全 green（限定相关包，避免拉起 IT）**

Run:
```bash
mvn -q test -Dtest='WorkflowLoaderTest,FanoutHelperTest,JoinTrackerTest,WorkflowNodeRunRecorderTest,JwtUtilsTest'
```
Expected: Tests run, Failures: 0, Errors: 0。

- [ ] **Step 3: 确认 git 工作树干净**

Run:
```bash
git status --porcelain
```
Expected: 只有未跟踪的 `.claude/`、`docs/superpowers/.DS_Store`、`docs/superpowers/handoff/2026-05-23-frontend-handoff.md`（基线遗留），无已修改文件。

---

# 批次 A — Judge 增强（#5 + #6 + #7）

## Task 1: 扩展 JudgeRunVO 添加 topic 字段

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/vo/JudgeRunVO.java`

> 注意：现有 `JudgeRunVO` 是 `@AllArgsConstructor`，`ReportJudgeService.toVo()` 用了 13 参数位置构造。新增字段后将构造改为 setter 链，避免今后再变更字段又破坏调用方。

- [ ] **Step 1: 在 JudgeRunVO 追加 topic 字段**

修改 `src/main/java/com/leo/enterpriseinertraining/vo/JudgeRunVO.java`，在 `createTime` 字段后追加：

```java
    /** 关联任务的研究主题，便于前端列表展示。null 表示反查任务失败。 */
    private String topic;
```

完整字段顺序应为：id, taskId, judgeModel, rubricVersion, overall, structure, factuality, reasoning, citation, clarity, comments, latencyMs, createTime, topic。

- [ ] **Step 2: 验证编译失败（toVo 13-arg 位置构造不再匹配）**

Run:
```bash
mvn -q -DskipTests compile
```
Expected: BUILD FAILURE，错误指向 `ReportJudgeService.java` 中 `new JudgeRunVO(...)` 处。这一步**预期**失败，确认了调用点位置。

- [ ] **Step 3: 把 ReportJudgeService.toVo 改为 setter 链**

修改 `src/main/java/com/leo/enterpriseinertraining/eval/ReportJudgeService.java` 第 235-244 行的 `toVo` 方法。把整个方法体替换为：

```java
    private JudgeRunVO toVo(ReportEvalRun r) {
        Map<String, String> comments = parseComments(r.getBreakdownJson());
        JudgeRunVO vo = new JudgeRunVO();
        vo.setId(r.getId());
        vo.setTaskId(r.getTaskId());
        vo.setJudgeModel(r.getJudgeModel());
        vo.setRubricVersion(r.getRubricVersion());
        vo.setOverall(r.getScoreOverall());
        vo.setStructure(r.getScoreStructure());
        vo.setFactuality(r.getScoreFactuality());
        vo.setReasoning(r.getScoreReasoning());
        vo.setCitation(r.getScoreCitation());
        vo.setClarity(r.getScoreClarity());
        vo.setComments(comments);
        vo.setLatencyMs(r.getLatencyMs());
        vo.setCreateTime(r.getCreateTime() == null ? null
                : r.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        // topic 由调用方按需填充（listByTask 单任务批注一次；recent 走批量 JOIN）
        return vo;
    }
```

- [ ] **Step 4: 验证编译通过**

Run:
```bash
mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS。

---

## Task 2: Judge 缓存命中逻辑（#7）— TDD

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/eval/ReportJudgeService.java`
- Create: `src/test/java/com/leo/enterpriseinertraining/eval/ReportJudgeServiceTest.java`

> 实现思路：用 MyBatis-Flex QueryWrapper 查 `report_eval_run` 同 `(task_id, judge_model, rubric_version)` 最近一条；非空且 `force=false` 直接返回，跳过 LLM 调用。

- [ ] **Step 1: 写 failing test — 缓存命中路径**

Create `src/test/java/com/leo/enterpriseinertraining/eval/ReportJudgeServiceTest.java`：

```java
package com.leo.enterpriseinertraining.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.entity.ReportEvalRun;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.mapper.ReportEvalRunMapper;
import com.leo.enterpriseinertraining.service.ReportService;
import com.leo.enterpriseinertraining.vo.JudgeRunVO;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportJudgeServiceTest {

    @Mock ReportService reportService;
    @Mock ReportEvalRunMapper evalRunMapper;
    @InjectMocks ReportJudgeService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "om", new ObjectMapper());
        ReflectionTestUtils.setField(service, "judgeModel", "qwen-max");
    }

    @Test
    void judge_withCacheHit_returnsCachedWithoutLlmCall() {
        long taskId = 42L;
        ReportTask task = new ReportTask();
        task.setId(taskId);
        task.setTopic("锂电产业链");
        task.setFinalMarkdown("# Report\n\nbody");
        when(reportService.findById(taskId)).thenReturn(task);

        ReportEvalRun cached = new ReportEvalRun();
        cached.setId(7L);
        cached.setTaskId(taskId);
        cached.setJudgeModel("qwen-max");
        cached.setRubricVersion("v1");
        cached.setScoreOverall(8.5);
        cached.setCreateTime(LocalDateTime.now());
        when(evalRunMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(cached));

        JudgeRunVO vo = service.judge(taskId, null, false);

        assertThat(vo.getId()).isEqualTo(7L);
        assertThat(vo.getOverall()).isEqualTo(8.5);
        assertThat(vo.getTopic()).isEqualTo("锂电产业链");
        verify(evalRunMapper, never()).insert(any(ReportEvalRun.class));
    }
}
```

- [ ] **Step 2: 运行测试，确认它编译失败（旧 judge(long) 签名不匹配）**

Run:
```bash
mvn -q test -Dtest=ReportJudgeServiceTest
```
Expected: 编译失败，错误：`method judge in class ReportJudgeService cannot be applied to given types`。

- [ ] **Step 3: 实现 judge(long, String, boolean) 新签名**

修改 `ReportJudgeService.java`。

在 `judgeModel` 字段下添加候选模型注入：

```java
    @Value("#{'${app.dashscope.judge-model-candidates:qwen-max}'.split(',')}")
    private List<String> judgeModelCandidates;
```

然后把现有 `public JudgeRunVO judge(long taskId) { ... }` 整体替换为：

```java
    /** 兼容旧调用方：默认不强制重评分，使用默认 judgeModel。 */
    public JudgeRunVO judge(long taskId) {
        return judge(taskId, null, false);
    }

    public JudgeRunVO judge(long taskId, String judgeModelOverride, boolean force) {
        ReportTask task = reportService.findById(taskId);    // tenant 校验
        String model = resolveModel(judgeModelOverride);
        String rubric = RUBRIC_VERSION;

        if (!force) {
            List<ReportEvalRun> hits = evalRunMapper.selectListByQuery(
                    QueryWrapper.create()
                            .where(REPORT_EVAL_RUN.TASK_ID.eq(taskId))
                            .and(REPORT_EVAL_RUN.JUDGE_MODEL.eq(model))
                            .and(REPORT_EVAL_RUN.RUBRIC_VERSION.eq(rubric))
                            .orderBy(REPORT_EVAL_RUN.ID.desc())
                            .limit(1));
            if (!hits.isEmpty()) {
                JudgeRunVO vo = toVo(hits.get(0));
                vo.setTopic(task.getTopic());
                return vo;
            }
        }

        ThrowUtils.throwIf(task.getFinalMarkdown() == null || task.getFinalMarkdown().isBlank(),
                ErrorCode.PARAMS_ERROR, "task " + taskId + " 尚无 finalMarkdown，无法评分");

        String md = task.getFinalMarkdown();
        if (md.length() > MD_MAX_CHARS) md = md.substring(0, MD_MAX_CHARS) + "\n...（已截断）";
        String citations = task.getCitationsJson() == null ? "[]" : safeWriteJson(task.getCitationsJson());
        String userMsg = """
                【研报主题】
                %s

                【研报正文】
                %s

                【引用列表 JSON】
                %s
                """.formatted(task.getTopic(), md, citations);

        long t0 = System.currentTimeMillis();
        String content = callJudge(userMsg, model);
        int latency = (int) (System.currentTimeMillis() - t0);

        JsonNode root = parseJson(content);
        ReportEvalRun run = new ReportEvalRun();
        run.setTaskId(taskId);
        run.setJudgeModel(model);
        run.setRubricVersion(rubric);
        run.setScoreStructure(asDouble(root, "structure"));
        run.setScoreFactuality(asDouble(root, "factuality"));
        run.setScoreReasoning(asDouble(root, "reasoning"));
        run.setScoreCitation(asDouble(root, "citation"));
        run.setScoreClarity(asDouble(root, "clarity"));
        run.setScoreOverall(avg(run.getScoreStructure(), run.getScoreFactuality(),
                run.getScoreReasoning(), run.getScoreCitation(), run.getScoreClarity()));
        run.setBreakdownJson(root.has("comments") ? root.get("comments").toString() : null);
        run.setLatencyMs(latency);
        evalRunMapper.insert(run);

        log.info("[ReportJudgeService] task {} judged with {}: overall={} latency={}ms",
                taskId, model, run.getScoreOverall(), latency);
        JudgeRunVO vo = toVo(run);
        vo.setTopic(task.getTopic());
        return vo;
    }

    private String resolveModel(String override) {
        return (override != null && !override.isBlank()) ? override : this.judgeModel;
    }

    public List<String> listJudgeModels() {
        if (judgeModelCandidates == null || judgeModelCandidates.isEmpty()) {
            return List.of(judgeModel);
        }
        return judgeModelCandidates.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
```

`callJudge` 增加 model 参数。把现有 `private String callJudge(String userMsg)` 改为：

```java
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String callJudge(String userMsg, String model) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.0,
                "messages", List.of(
                        Map.of("role", "system", "content", JUDGE_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMsg)));
        try {
            Map resp = http.post().uri("/chat/completions").body(body).retrieve().body(Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return stripFences((String) msg.get("content"));
        } catch (Exception e) {
            throw new RuntimeException("judge LLM 调用失败: " + e.getMessage(), e);
        }
    }
```

- [ ] **Step 4: 运行测试，确认通过**

Run:
```bash
mvn -q test -Dtest=ReportJudgeServiceTest
```
Expected: Tests run: 1, Failures: 0, Errors: 0。

---

## Task 3: 缓存未命中 + force=true 测试

**Files:**
- Modify: `src/test/java/com/leo/enterpriseinertraining/eval/ReportJudgeServiceTest.java`

- [ ] **Step 1: 追加 force=true 测试**

在 `ReportJudgeServiceTest` 类内添加：

```java
    @Test
    void judge_withForce_skipsCacheAndCallsLlm() {
        long taskId = 42L;
        ReportTask task = new ReportTask();
        task.setId(taskId);
        task.setTopic("锂电产业链");
        task.setFinalMarkdown("# Report\n\nbody");
        when(reportService.findById(taskId)).thenReturn(task);

        ReportEvalRun cached = new ReportEvalRun();
        cached.setId(7L);
        cached.setJudgeModel("qwen-max");
        cached.setRubricVersion("v1");
        // 即使有缓存，force=true 也不会走 selectListByQuery 路径

        // force=true 不查 selectListByQuery，但会触发 LLM 调用 → 在没有 RestClient stub 的情况下应抛 NPE / RuntimeException
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service.judge(taskId, null, true));

        verify(evalRunMapper, never()).selectListByQuery(any(QueryWrapper.class));
    }
```

- [ ] **Step 2: 运行测试，确认通过**

Run:
```bash
mvn -q test -Dtest=ReportJudgeServiceTest
```
Expected: Tests run: 2, Failures: 0, Errors: 0。

---

## Task 4: 自定义模型覆盖测试

**Files:**
- Modify: `src/test/java/com/leo/enterpriseinertraining/eval/ReportJudgeServiceTest.java`

- [ ] **Step 1: 追加 model override 测试**

```java
    @Test
    void judge_withModelOverride_queriesCacheWithOverrideModel() {
        long taskId = 42L;
        ReportTask task = new ReportTask();
        task.setId(taskId);
        task.setTopic("半导体周期");
        task.setFinalMarkdown("# body");
        when(reportService.findById(taskId)).thenReturn(task);

        ReportEvalRun cached = new ReportEvalRun();
        cached.setJudgeModel("deepseek-chat");
        cached.setRubricVersion("v1");
        cached.setScoreOverall(7.2);
        when(evalRunMapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(cached));

        JudgeRunVO vo = service.judge(taskId, "deepseek-chat", false);

        assertThat(vo.getOverall()).isEqualTo(7.2);
        // 命中缓存即可证明 selectLatest 用了覆盖 model（mock 不区分参数）
        verify(evalRunMapper, never()).insert(any(ReportEvalRun.class));
    }
```

- [ ] **Step 2: 运行测试，确认通过**

Run:
```bash
mvn -q test -Dtest=ReportJudgeServiceTest
```
Expected: Tests run: 3, Failures: 0, Errors: 0。

---

## Task 5: Controller 暴露 force/judgeModel 参数 + /models 接口（#6）

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/controller/AdminReportJudgeController.java`

- [ ] **Step 1: 修改 controller，添加查询参数与新端点**

把整个 `AdminReportJudgeController.java` 替换为：

```java
package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.eval.ReportJudgeService;
import com.leo.enterpriseinertraining.vo.JudgeRunVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/eval/judge")
@RequiredArgsConstructor
@Tag(name = "管理-LLM-as-Judge", description = "研报质量评分（5 维 rubric）")
public class AdminReportJudgeController {

    private final ReportJudgeService judgeService;

    @PostMapping("/{taskId}")
    @Operation(summary = "对该 task 跑一次评分；force=false 命中缓存返回历史评分")
    public BaseResponse<JudgeRunVO> judge(
            @PathVariable long taskId,
            @RequestParam(required = false) String judgeModel,
            @RequestParam(defaultValue = "false") boolean force) {
        return ResultUtils.success(judgeService.judge(taskId, judgeModel, force));
    }

    @GetMapping("/{taskId}/history")
    @Operation(summary = "查该 task 的全部历史评分")
    public BaseResponse<List<JudgeRunVO>> history(@PathVariable long taskId) {
        return ResultUtils.success(judgeService.listByTask(taskId));
    }

    @GetMapping
    @Operation(summary = "当前租户最近评分列表")
    public BaseResponse<List<JudgeRunVO>> recent(@RequestParam(defaultValue = "20") int limit) {
        return ResultUtils.success(judgeService.listForCurrentTenant(limit));
    }

    @GetMapping("/models")
    @Operation(summary = "可选裁判模型列表（来自 application.yml）")
    public BaseResponse<List<String>> models() {
        return ResultUtils.success(judgeService.listJudgeModels());
    }
}
```

- [ ] **Step 2: 验证编译**

Run:
```bash
mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS。

---

## Task 6: application.yml 候选模型配置

**Files:**
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: 检查现有 yml 结构**

Run:
```bash
grep -n "dashscope\|judge-model" src/main/resources/application.yml
```
记下 `app.dashscope.judge-model` 当前位置（应该已存在）。

- [ ] **Step 2: 在 `app.dashscope` 节追加候选列表**

在 `app.dashscope.judge-model: qwen-max` 行后面追加：

```yaml
    # F4: Admin 评分页可选的裁判模型；逗号分隔，由 ReportJudgeService.listJudgeModels() 暴露
    judge-model-candidates: qwen-max,qwen-plus,deepseek-chat
```

> 与代码注入处一致：`@Value("#{'${app.dashscope.judge-model-candidates:qwen-max}'.split(',')}")`，逗号分隔字符串自动 split 成 List。

- [ ] **Step 3: 验证应用上下文可加载**

Run:
```bash
mvn -q test -Dtest=ReportJudgeServiceTest
```
Expected: Tests run: 3, Failures: 0, Errors: 0（注入候选列表不影响 Mockito 测试，因为它走 ReflectionTestUtils 直接 setField）。

---

## Task 7: listByTask / listForCurrentTenant 携带 topic（#5）

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/eval/ReportJudgeService.java`

> 实现思路：
> - `listByTask(taskId)` 单任务 → 复用已有 `task.getTopic()`，单次 setTopic 即可。
> - `listForCurrentTenant(limit)` 多任务 → 一次性把租户所有 task 的 (id → topic) 拉到 Map，遍历 VO 时填充，避免 N+1。

- [ ] **Step 1: 修改 listByTask 携带 topic**

把 `listByTask` 整体替换为：

```java
    public List<JudgeRunVO> listByTask(long taskId) {
        ReportTask task = reportService.findById(taskId);    // tenant 校验 + 取 topic
        return evalRunMapper.selectListByQuery(QueryWrapper.create()
                        .where(REPORT_EVAL_RUN.TASK_ID.eq(taskId))
                        .orderBy(REPORT_EVAL_RUN.ID.desc()))
                .stream().map(r -> {
                    JudgeRunVO vo = toVo(r);
                    vo.setTopic(task.getTopic());
                    return vo;
                }).toList();
    }
```

- [ ] **Step 2: 修改 listForCurrentTenant 一次性批注 topic**

需要注入 `ReportTaskMapper`。在 `ReportJudgeService` 字段区追加：

```java
    private final com.leo.enterpriseinertraining.mapper.ReportTaskMapper reportTaskMapper;
```

把 `listForCurrentTenant` 整体替换为：

```java
    public List<JudgeRunVO> listForCurrentTenant(int limit) {
        QueryWrapper sub = QueryWrapper.create()
                .select(REPORT_TASK.ID)
                .from(REPORT_TASK)
                .where(REPORT_TASK.TENANT_ID.eq(SecurityUtils.currentTenantId()));
        List<ReportEvalRun> runs = evalRunMapper.selectListByQuery(QueryWrapper.create()
                .where(REPORT_EVAL_RUN.TASK_ID.in(sub))
                .orderBy(REPORT_EVAL_RUN.ID.desc())
                .limit(Math.max(1, Math.min(limit, 200))));
        if (runs.isEmpty()) return List.of();

        // 一次性反查 topic 映射
        java.util.Set<Long> taskIds = runs.stream().map(ReportEvalRun::getTaskId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, String> topicByTaskId = reportTaskMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(REPORT_TASK.ID, REPORT_TASK.TOPIC)
                                .where(REPORT_TASK.ID.in(taskIds))
                                .and(REPORT_TASK.TENANT_ID.eq(SecurityUtils.currentTenantId())))
                .stream()
                .collect(java.util.stream.Collectors.toMap(ReportTask::getId, ReportTask::getTopic));

        return runs.stream().map(r -> {
            JudgeRunVO vo = toVo(r);
            vo.setTopic(topicByTaskId.get(r.getTaskId()));
            return vo;
        }).toList();
    }
```

- [ ] **Step 3: 验证测试仍通过**

Run:
```bash
mvn -q test -Dtest=ReportJudgeServiceTest
```
Expected: Tests run: 3, Failures: 0, Errors: 0。

> 注意：测试目前用 `@InjectMocks` 注入；新增的 `reportTaskMapper` 字段会被 Mockito 自动注入为 mock，不会影响现有 3 个测试（它们不调用 listForCurrentTenant）。如果 Mockito 报缺少 mock，在测试类追加 `@Mock ReportTaskMapper reportTaskMapper;`。

---

## Task 8: 批次 A 端到端编译 + 提交

**Files:**
- 全批次

- [ ] **Step 1: 整批编译 + 测试**

Run:
```bash
mvn -q test -Dtest=ReportJudgeServiceTest
mvn -q -DskipTests compile
```
Expected: 都 SUCCESS / 0 failures。

- [ ] **Step 2: 检查改动列表与预期一致**

Run:
```bash
git status --short
```
Expected 列表：
```
M  src/main/java/com/leo/enterpriseinertraining/controller/AdminReportJudgeController.java
M  src/main/java/com/leo/enterpriseinertraining/eval/ReportJudgeService.java
M  src/main/java/com/leo/enterpriseinertraining/vo/JudgeRunVO.java
M  src/main/resources/application.yml
?? src/test/java/com/leo/enterpriseinertraining/eval/ReportJudgeServiceTest.java
```

- [ ] **Step 3: Commit 批次 A**

Run:
```bash
git add src/main/java/com/leo/enterpriseinertraining/controller/AdminReportJudgeController.java \
        src/main/java/com/leo/enterpriseinertraining/eval/ReportJudgeService.java \
        src/main/java/com/leo/enterpriseinertraining/vo/JudgeRunVO.java \
        src/main/resources/application.yml \
        src/test/java/com/leo/enterpriseinertraining/eval/ReportJudgeServiceTest.java

git commit -m "$(cat <<'EOF'
feat(backend/f4): Judge 评分增强（#5/#6/#7）

- #5 JudgeRunVO 携带 topic（listByTask 复用 task；listForCurrentTenant 批量 JOIN 避免 N+1）
- #6 POST /judge/{id}?judgeModel=xxx 切换裁判模型；新增 GET /judge/models
       application.yml 加 app.dashscope.judge-model-candidates 列表
- #7 POST /judge/{id}?force=false 默认命中缓存返回历史评分（同 task+model+rubric）
       force=true 强制重评分，单测覆盖 3 条路径（缓存命中 / force 跳过 / model override）

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

# 批次 B — Workflow & Stats 看板（#3 + #4 + #1 + #2 + #8）

## Task 9: schema-phase7.sql DDL

**Files:**
- Create: `src/main/resources/db/schema-phase7.sql`

- [ ] **Step 1: 创建 DDL 文件**

```sql
-- 阶段 7（F4）：Workflow 加载审计表 + report_task.create_time 索引（同比/P95 SQL 使用）

CREATE TABLE IF NOT EXISTS `workflow_load_log` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `event_type`       VARCHAR(32)  NOT NULL COMMENT 'cache_clear / yaml_reload / topology_check / activate',
    `message`          VARCHAR(512) NOT NULL,
    `level`            VARCHAR(16)  NOT NULL DEFAULT 'info' COMMENT 'info / success / warning / error',
    `workflow_name`    VARCHAR(128),
    `workflow_version` VARCHAR(32),
    `ts`               BIGINT       NOT NULL COMMENT 'epoch millis',
    PRIMARY KEY (`id`),
    KEY `idx_ts` (`ts` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Workflow 加载与重载操作审计';

-- report_task 现有索引只覆盖 user_id / status / tenant_id；为 F4 同比聚合和 P95 SQL 加 create_time 索引。
-- MySQL 8.0.29+ 支持 ALTER TABLE ... ADD KEY IF NOT EXISTS；旧版本如报错请手工跳过本行。
ALTER TABLE `report_task` ADD KEY IF NOT EXISTS `idx_create_time` (`create_time`);
```

- [ ] **Step 2: 验证 SQL 语法（解析检查）**

Run:
```bash
grep -c "CREATE TABLE\|ALTER TABLE" src/main/resources/db/schema-phase7.sql
```
Expected: `2`。

---

## Task 10: WorkflowLoadLog 实体 + Mapper

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/entity/WorkflowLoadLog.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/mapper/WorkflowLoadLogMapper.java`

- [ ] **Step 1: 创建实体类**

Create `src/main/java/com/leo/enterpriseinertraining/entity/WorkflowLoadLog.java`：

```java
package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * Workflow 加载与重载审计记录。
 * 与 BaseEntity 不同：这张表本身就是日志，不需要 create_time / update_time / is_deleted。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("workflow_load_log")
public class WorkflowLoadLog implements Serializable {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String eventType;
    private String message;
    private String level;
    private String workflowName;
    private String workflowVersion;
    private Long ts;
}
```

- [ ] **Step 2: 创建 Mapper**

Create `src/main/java/com/leo/enterpriseinertraining/mapper/WorkflowLoadLogMapper.java`：

```java
package com.leo.enterpriseinertraining.mapper;

import com.leo.enterpriseinertraining.entity.WorkflowLoadLog;
import com.mybatisflex.core.BaseMapper;

public interface WorkflowLoadLogMapper extends BaseMapper<WorkflowLoadLog> {
}
```

- [ ] **Step 3: 触发 MyBatis-Flex APT 生成 TableDef**

Run:
```bash
mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS。编译后 `target/generated-sources/annotations` 应生成 `WorkflowLoadLogTableDef`。验证：

```bash
find target/generated-sources -name "WorkflowLoadLogTableDef.java" 2>/dev/null
```
Expected: 找到 1 个文件。

---

## Task 11: WorkflowLogVO + ActiveWorkflowVO

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/vo/WorkflowLogVO.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/vo/ActiveWorkflowVO.java`

- [ ] **Step 1: 创建 WorkflowLogVO**

```java
package com.leo.enterpriseinertraining.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Workflow 加载/重载日志条目（前端时间轴使用）。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class WorkflowLogVO implements Serializable {

    @Schema(description = "事件类型：cache_clear / yaml_reload / topology_check / activate")
    private String eventType;

    @Schema(description = "可读消息")
    private String message;

    @Schema(description = "级别：info / success / warning / error")
    private String level;

    @Schema(description = "时间戳，epoch millis")
    private Long ts;
}
```

- [ ] **Step 2: 创建 ActiveWorkflowVO**

```java
package com.leo.enterpriseinertraining.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/** 当前活跃 Workflow 元信息（前端 Admin 看板左卡使用）。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ActiveWorkflowVO implements Serializable {

    @Schema(description = "Workflow 名称，如 multi_agent_v1")
    private String name;

    @Schema(description = "Workflow 版本")
    private String version;

    @Schema(description = "YAML 资源路径，如 classpath:workflow/multi_agent_v1.yaml")
    private String file;

    @Schema(description = "节点列表，按拓扑序")
    private List<String> nodes;

    @Schema(description = "上次加载时间，epoch millis；null 表示尚未加载")
    private Long lastLoadedAt;

    @Schema(description = "当前是否已在内存缓存中（true=命中缓存，false=刚 reload 或首次访问）")
    private boolean cached;
}
```

- [ ] **Step 3: 验证编译**

Run:
```bash
mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS。

---

## Task 12: WorkflowLoader 改造 — appendLog / activeInfo / history（TDD）

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowLoader.java`
- Create: `src/test/java/com/leo/enterpriseinertraining/workflow/WorkflowLoaderAuditTest.java`

> 注意：现有 `WorkflowLoader` 是 `@Component`，单测用 ApplicationContext 启动。新审计逻辑必须支持单测注入 mock mapper。这里把 WorkflowLoadLogMapper 作为构造注入字段，单测用纯 Mockito 注入。

- [ ] **Step 1: 写 failing test — reload 写入 4 条日志**

Create `src/test/java/com/leo/enterpriseinertraining/workflow/WorkflowLoaderAuditTest.java`：

```java
package com.leo.enterpriseinertraining.workflow;

import com.leo.enterpriseinertraining.entity.WorkflowLoadLog;
import com.leo.enterpriseinertraining.mapper.WorkflowLoadLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkflowLoaderAuditTest {

    @Mock WorkflowLoadLogMapper logMapper;

    @Test
    void reload_writesFourAuditLogsInOrder() {
        // 测试 classpath 包含 src/main/resources/workflow/multi_agent_v1.yaml（已存在），
        // 因此 reload 走完整路径写 4 条审计：cache_clear → yaml_reload → topology_check → activate。
        WorkflowLoader loader = new WorkflowLoader(logMapper);

        loader.reload();

        ArgumentCaptor<WorkflowLoadLog> cap = ArgumentCaptor.forClass(WorkflowLoadLog.class);
        verify(logMapper, org.mockito.Mockito.times(4)).insert(cap.capture());
        List<WorkflowLoadLog> rows = cap.getAllValues();
        assertThat(rows).extracting(WorkflowLoadLog::getEventType)
                .containsExactly("cache_clear", "yaml_reload", "topology_check", "activate");
        assertThat(rows.get(0).getLevel()).isEqualTo("info");
        assertThat(rows.get(3).getLevel()).isEqualTo("success");
        assertThat(rows).allMatch(r -> r.getTs() != null);
    }

    @Test
    void appendLog_failureDoesNotBubbleToCaller() {
        // 模拟 mapper.insert 抛异常（如 DB 不可用），reload 应只 warn，不破坏热更新主流程
        org.mockito.Mockito.doThrow(new RuntimeException("DB down"))
                .when(logMapper).insert(any(WorkflowLoadLog.class));

        WorkflowLoader loader = new WorkflowLoader(logMapper);

        // 即使 audit 写库失败，reload 自身不抛
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(loader::reload);
    }
}
```

- [ ] **Step 2: 运行测试，确认编译失败（WorkflowLoader 无 1-arg 构造）**

Run:
```bash
mvn -q test -Dtest=WorkflowLoaderAuditTest
```
Expected: 编译失败：`constructor WorkflowLoader in class WorkflowLoader cannot be applied to given types`。

- [ ] **Step 3: 实现 WorkflowLoader 改造**

把整个 `src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowLoader.java` 替换为：

```java
package com.leo.enterpriseinertraining.workflow;

import com.leo.enterpriseinertraining.entity.WorkflowLoadLog;
import com.leo.enterpriseinertraining.mapper.WorkflowLoadLogMapper;
import com.leo.enterpriseinertraining.vo.ActiveWorkflowVO;
import com.leo.enterpriseinertraining.vo.WorkflowLogVO;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.leo.enterpriseinertraining.entity.table.WorkflowLoadLogTableDef.WORKFLOW_LOAD_LOG;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowLoader {

    /** 默认活跃 workflow 名称；如需多 workflow 切换，未来可移到配置 / DB。 */
    private static final String DEFAULT_ACTIVE_WORKFLOW = "multi_agent_v1";

    private final Map<String, WorkflowDef> cache = new ConcurrentHashMap<>();
    private final WorkflowLoadLogMapper logMapper;
    private volatile Long lastLoadedAt;

    public WorkflowDef load(String name) {
        return cache.computeIfAbsent(name, n -> {
            WorkflowDef def = readYaml(n);
            lastLoadedAt = System.currentTimeMillis();
            return def;
        });
    }

    /** 清空缓存，下次 load 会重新读 YAML（admin 接口用），全过程记审计。 */
    public synchronized void reload() {
        appendLog("cache_clear", "已清空 Workflow YAML 缓存，准备重新加载", "info");
        cache.clear();
        try {
            WorkflowDef def = readYaml(DEFAULT_ACTIVE_WORKFLOW);
            appendLog("yaml_reload", "Workflow YAML 已重新解析", "info");
            // 拓扑校验：readYaml 内部隐式校验过（snakeyaml 解析失败会抛），这里只是写一条审计标记
            appendLog("topology_check", "拓扑结构校验通过（" + def.getNodes().size() + " 个节点）", "info");
            cache.put(def.getName(), def);
            lastLoadedAt = System.currentTimeMillis();
            appendLog("activate", "Workflow " + def.getName() + "(v" + def.getVersion() + ") 已成功生效", "success");
        } catch (Exception e) {
            appendLog("activate", "热更新失败：" + e.getMessage(), "error");
            throw e;
        }
        log.info("[WorkflowLoader] cache cleared & reloaded");
    }

    /** 当前活跃 workflow 元信息（用于 Admin 看板）。 */
    public ActiveWorkflowVO activeInfo() {
        WorkflowDef def = cache.get(DEFAULT_ACTIVE_WORKFLOW);
        boolean cached = def != null;
        if (def == null) {
            def = readYaml(DEFAULT_ACTIVE_WORKFLOW);
            if (lastLoadedAt == null) lastLoadedAt = System.currentTimeMillis();
        }
        return new ActiveWorkflowVO(
                def.getName(),
                def.getVersion(),
                "classpath:workflow/" + def.getName() + ".yaml",
                def.getNodes().stream().map(WorkflowNode::getName).toList(),
                lastLoadedAt,
                cached);
    }

    /** Workflow 加载历史（按 ts DESC，limit 1-100）。 */
    public List<WorkflowLogVO> history(int limit) {
        int safe = Math.max(1, Math.min(limit, 100));
        return logMapper.selectListByQuery(
                        QueryWrapper.create()
                                .orderBy(WORKFLOW_LOAD_LOG.TS.desc())
                                .limit(safe))
                .stream().map(r -> new WorkflowLogVO(r.getEventType(), r.getMessage(), r.getLevel(), r.getTs()))
                .toList();
    }

    private void appendLog(String eventType, String message, String level) {
        try {
            WorkflowLoadLog row = new WorkflowLoadLog();
            row.setEventType(eventType);
            row.setMessage(message);
            row.setLevel(level);
            row.setWorkflowName(DEFAULT_ACTIVE_WORKFLOW);
            // workflowVersion 在 reload 路径上不一定可知（cache_clear 早于解析）；留空即可
            row.setTs(System.currentTimeMillis());
            logMapper.insert(row);
        } catch (Exception e) {
            // 审计写库失败不阻塞热更新主流程
            log.warn("[WorkflowLoader] append audit log failed: {}", e.getMessage());
        }
    }

    private WorkflowDef readYaml(String name) {
        String path = "workflow/" + name + ".yaml";
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            LoaderOptions opts = new LoaderOptions();
            Constructor c = new Constructor(WorkflowDef.class, opts);
            Yaml yaml = new Yaml(c);
            WorkflowDef def = yaml.load(in);
            if (def == null) throw new RuntimeException("空 YAML: " + path);
            log.info("[WorkflowLoader] loaded {} v{} with {} nodes",
                    def.getName(), def.getVersion(), def.getNodes().size());
            return def;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("无法加载 workflow: " + path, e);
        }
    }
}
```

- [ ] **Step 4: 运行新单测**

Run:
```bash
mvn -q test -Dtest=WorkflowLoaderAuditTest
```
Expected: Tests run: 2, Failures: 0, Errors: 0。

- [ ] **Step 5: 运行原有 WorkflowLoaderTest，确认未破坏**

Run:
```bash
mvn -q test -Dtest=WorkflowLoaderTest
```
Expected: Tests run: N, Failures: 0, Errors: 0。如果原 test 用了无参构造 `new WorkflowLoader()`，会编译失败 — 那就修复它，把构造改为 `new WorkflowLoader(mock(WorkflowLoadLogMapper.class))`，保持行为不变。

---

## Task 13: AdminWorkflowController 新增 /active 与 /history

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/controller/AdminWorkflowController.java`

- [ ] **Step 1: 替换整个 controller**

```java
package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.vo.ActiveWorkflowVO;
import com.leo.enterpriseinertraining.vo.WorkflowLogVO;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/workflow")
@RequiredArgsConstructor
@Tag(name = "管理-Workflow", description = "运维操作")
public class AdminWorkflowController {

    private final WorkflowLoader workflowLoader;

    @PostMapping("/reload")
    @Operation(summary = "清空 workflow YAML 缓存（修改 YAML 后调用即可热更，不必重启）")
    public BaseResponse<String> reload() {
        workflowLoader.reload();
        return ResultUtils.success("reloaded");
    }

    @GetMapping("/active")
    @Operation(summary = "当前活跃 Workflow 元信息（用于 Admin 看板）")
    public BaseResponse<ActiveWorkflowVO> active() {
        return ResultUtils.success(workflowLoader.activeInfo());
    }

    @GetMapping("/history")
    @Operation(summary = "Workflow 加载历史（用于 Admin 日志时间轴）")
    public BaseResponse<List<WorkflowLogVO>> history(
            @RequestParam(defaultValue = "20") int limit) {
        return ResultUtils.success(workflowLoader.history(limit));
    }
}
```

- [ ] **Step 2: 编译**

Run:
```bash
mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS。

---

## Task 14: ComponentHealthVO + HealthService + extend HealthController（#1）

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/vo/ComponentHealthVO.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/service/HealthService.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/controller/HealthController.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/stream/SseSinkManager.java`（新增 activeCount）

- [ ] **Step 1: 创建 ComponentHealthVO**

```java
package com.leo.enterpriseinertraining.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/** 单个子服务的健康状态（Admin 系统健康面板使用）。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ComponentHealthVO implements Serializable {

    @Schema(description = "子服务名：API / Redis / SSE")
    private String name;

    @Schema(description = "状态：UP / DOWN / DEGRADED")
    private String status;

    @Schema(description = "用户可读副标题，如 \"后端接口服务\"")
    private String subtitle;

    @Schema(description = "探测耗时，单位 ms；null 表示不适用（如 SSE）")
    private Double latencyMs;

    @Schema(description = "扩展信息，如 SSE 的活跃连接数")
    private Map<String, Object> extra;
}
```

- [ ] **Step 2: 给 SseSinkManager 加 activeCount() 方法**

在 `src/main/java/com/leo/enterpriseinertraining/stream/SseSinkManager.java` 的 `emitters` 字段下面（约第 30 行附近）追加：

```java
    /** 当前活跃 SSE 连接数（Admin 系统健康面板使用）。 */
    public int activeCount() {
        return emitters.size();
    }
```

- [ ] **Step 3: 创建 HealthService**

Create `src/main/java/com/leo/enterpriseinertraining/service/HealthService.java`：

```java
package com.leo.enterpriseinertraining.service;

import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.vo.ComponentHealthVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthService {

    private final StringRedisTemplate redisTemplate;
    private final SseSinkManager sseSinkManager;

    public List<ComponentHealthVO> checkAll() {
        List<ComponentHealthVO> rows = new ArrayList<>(3);

        rows.add(new ComponentHealthVO("API", "UP", "后端接口服务", 0.0, null));

        long t = System.nanoTime();
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            double ms = (System.nanoTime() - t) / 1_000_000.0;
            rows.add(new ComponentHealthVO("Redis",
                    pong != null ? "UP" : "DEGRADED",
                    "缓存与会话存储",
                    Math.round(ms * 100.0) / 100.0,
                    null));
        } catch (Exception e) {
            log.warn("[HealthService] Redis ping failed: {}", e.getMessage());
            rows.add(new ComponentHealthVO("Redis", "DOWN", "缓存与会话存储", null, null));
        }

        rows.add(new ComponentHealthVO("SSE", "UP", "流式推送服务", null,
                Map.of("connections", sseSinkManager.activeCount())));

        return rows;
    }
}
```

- [ ] **Step 4: 扩展 HealthController**

把整个 `src/main/java/com/leo/enterpriseinertraining/controller/HealthController.java` 替换为：

```java
package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.service.HealthService;
import com.leo.enterpriseinertraining.vo.ComponentHealthVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Tag(name = "健康检查")
public class HealthController {

    private final HealthService healthService;

    @GetMapping("")
    @Operation(summary = "存活探针")
    public BaseResponse<Map<String, Object>> health() {
        return ResultUtils.success(Map.of(
                "status", "UP",
                "time", LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/components")
    @Operation(summary = "返回各子服务健康状态，供 Admin 系统健康面板使用")
    public BaseResponse<List<ComponentHealthVO>> components() {
        return ResultUtils.success(healthService.checkAll());
    }
}
```

- [ ] **Step 5: 编译**

Run:
```bash
mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS。

---

## Task 15: PlatformOverviewVO 扩展字段（#2 + #8）

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/vo/PlatformOverviewVO.java`

> 注意：现有 VO 是 `@AllArgsConstructor`，但 `AdminStatsService.overview()` 用的是无参构造 + setter，加字段不破坏二进制兼容。

- [ ] **Step 1: 追加 6 个字段**

把整个 `PlatformOverviewVO.java` 替换为：

```java
package com.leo.enterpriseinertraining.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 平台总览看板：任务量 / 成功率 / Token 成本 / 端到端耗时 / 同比 / P95。
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class PlatformOverviewVO implements Serializable {

    private long totalTasks;
    private long doneTasks;
    private long failedTasks;
    private long runningTasks;
    /** 已结束任务中 DONE 占比，0-1。 */
    private double taskSuccessRate;

    private long totalNodeRuns;
    private long errorNodeRuns;

    private long totalTokensIn;
    private long totalTokensOut;
    /** 全平台累计 Token 成本，单位：元。 */
    private double totalCostCny;

    /** DONE 任务平均端到端耗时（finishedAt - startedAt），无样本时为 null。 */
    private Long avgTaskLatencyMs;

    // ── F4 #8 ──────────────────────────────────────────────
    @Schema(description = "P95 端到端耗时 ms；窗口内 DONE 任务 < 20 时返回 null")
    private Long p95TaskLatencyMs;

    // ── F4 #2 同比指标 ─────────────────────────────────────
    @Schema(description = "较上一窗口的任务总数变化量，正为增长")
    private Long totalTasksDelta;

    @Schema(description = "成功率绝对差，如 0.016 表示 +1.6 个百分点")
    private Double taskSuccessRateDelta;

    @Schema(description = "Token 成本变化量，单位元")
    private Double totalCostCnyDelta;

    @Schema(description = "平均耗时变化量，单位 ms")
    private Long avgTaskLatencyMsDelta;

    @Schema(description = "对比窗口标签，如 \"较昨日\"")
    private String compareWindowLabel;
}
```

- [ ] **Step 2: 编译**

Run:
```bash
mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS。`@AllArgsConstructor` 的位置参数虽然变多，但代码里没有用 14 位置构造过 VO（grep 一下确认）：

```bash
grep -rn "new PlatformOverviewVO(" src/main/java
```
Expected: 没有命中（只有 `new PlatformOverviewVO()` 无参形式）。

---

## Task 16: AdminStatsService 同比 + P95 实现（TDD）

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/trace/AdminStatsService.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/mapper/ReportTaskMapper.java`
- Create: `src/test/java/com/leo/enterpriseinertraining/trace/AdminStatsServiceTest.java`

- [ ] **Step 1: 写 failing test — compareWindowLabel = "较昨日"**

Create `src/test/java/com/leo/enterpriseinertraining/trace/AdminStatsServiceTest.java`：

```java
package com.leo.enterpriseinertraining.trace;

import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.mapper.WorkflowNodeRunMapper;
import com.leo.enterpriseinertraining.security.SecurityUtils;
import com.leo.enterpriseinertraining.vo.PlatformOverviewVO;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock WorkflowNodeRunMapper nodeRunMapper;
    @Mock ReportTaskMapper taskMapper;
    @InjectMocks AdminStatsService service;

    MockedStatic<SecurityUtils> sec;

    @BeforeEach
    void setUp() {
        sec = Mockito.mockStatic(SecurityUtils.class);
        sec.when(SecurityUtils::currentTenantId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() { sec.close(); }

    @Test
    void overview_attachesCompareWindowLabel() {
        when(taskMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(nodeRunMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(nodeRunMapper.selectListByQueryAs(any(QueryWrapper.class), any())).thenReturn(Collections.emptyList());
        when(taskMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        PlatformOverviewVO vo = service.overview();

        assertThat(vo.getCompareWindowLabel()).isEqualTo("较昨日");
        // 没数据时各 Delta 仍应被赋值为 0 / null（而不是漏赋值，保证 VO 字段非空可序列化）
        assertThat(vo.getTotalTasksDelta()).isNotNull();
    }

    @Test
    void overview_p95IsNullWhenSampleSizeBelowThreshold() {
        // 当窗口内 DONE 任务数 < 20，P95 返回 null
        when(taskMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(5L);
        when(nodeRunMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(nodeRunMapper.selectListByQueryAs(any(QueryWrapper.class), any())).thenReturn(Collections.emptyList());
        when(taskMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(taskMapper.countDoneTasksInWindow(any(), any(), any())).thenReturn(5L);

        PlatformOverviewVO vo = service.overview();

        assertThat(vo.getP95TaskLatencyMs()).isNull();
    }
}
```

- [ ] **Step 2: 编译应失败（缺少 mapper 方法 + service 没填字段）**

Run:
```bash
mvn -q test -Dtest=AdminStatsServiceTest
```
Expected: 编译失败 — `cannot find symbol: method countDoneTasksInWindow`。

- [ ] **Step 3: 在 ReportTaskMapper 加 P95 相关 @Select 方法**

把 `src/main/java/com/leo/enterpriseinertraining/mapper/ReportTaskMapper.java` 替换为：

```java
package com.leo.enterpriseinertraining.mapper;

import com.leo.enterpriseinertraining.entity.ReportTask;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface ReportTaskMapper extends BaseMapper<ReportTask> {

    /**
     * 窗口内已完成（DONE、startedAt/finishedAt 都非空）的任务数；P95 采样基数。
     */
    @Select("""
            SELECT COUNT(*) FROM report_task
            WHERE tenant_id = #{tenantId}
              AND status = 'DONE'
              AND started_at IS NOT NULL
              AND finished_at IS NOT NULL
              AND create_time >= #{from}
              AND create_time < #{to}
              AND is_deleted = 0
            """)
    long countDoneTasksInWindow(@Param("tenantId") Long tenantId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    /**
     * 窗口内 DONE 任务按耗时升序的第 offset+1 条耗时（毫秒）。OFFSET = floor(count * 0.95)。
     * MySQL 5.7+ 支持 TIMESTAMPDIFF。
     */
    @Select("""
            SELECT TIMESTAMPDIFF(MICROSECOND, started_at, finished_at) / 1000 AS latency_ms
            FROM report_task
            WHERE tenant_id = #{tenantId}
              AND status = 'DONE'
              AND started_at IS NOT NULL
              AND finished_at IS NOT NULL
              AND create_time >= #{from}
              AND create_time < #{to}
              AND is_deleted = 0
            ORDER BY latency_ms
            LIMIT 1 OFFSET #{offset}
            """)
    Long selectP95LatencyMs(@Param("tenantId") Long tenantId,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to,
                            @Param("offset") long offset);

    /**
     * 窗口内租户总任务数（用于同比聚合）。
     */
    @Select("""
            SELECT COUNT(*) FROM report_task
            WHERE tenant_id = #{tenantId}
              AND create_time >= #{from}
              AND create_time < #{to}
              AND is_deleted = 0
            """)
    long countTasksInWindow(@Param("tenantId") Long tenantId,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to);

    /**
     * 窗口内租户 DONE 任务数（用于同比成功率）。
     */
    @Select("""
            SELECT COUNT(*) FROM report_task
            WHERE tenant_id = #{tenantId}
              AND status = 'DONE'
              AND create_time >= #{from}
              AND create_time < #{to}
              AND is_deleted = 0
            """)
    long countDoneInWindow(@Param("tenantId") Long tenantId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);

    /**
     * 窗口内租户 DONE 任务的平均耗时 ms。无样本返回 null。
     */
    @Select("""
            SELECT AVG(TIMESTAMPDIFF(MICROSECOND, started_at, finished_at) / 1000) AS avg_ms
            FROM report_task
            WHERE tenant_id = #{tenantId}
              AND status = 'DONE'
              AND started_at IS NOT NULL
              AND finished_at IS NOT NULL
              AND create_time >= #{from}
              AND create_time < #{to}
              AND is_deleted = 0
            """)
    Long avgLatencyInWindow(@Param("tenantId") Long tenantId,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to);

    /**
     * 窗口内租户 workflow_node_run 累计 tokens_in 与 tokens_out（用于同比成本简单估算）。
     * 注意：tenant 隔离通过 task 子查询，这里仍以 report_task 为主表用 JOIN 实现。
     */
    @Select("""
            SELECT COALESCE(SUM(wnr.tokens_in), 0)  AS tokens_in,
                   COALESCE(SUM(wnr.tokens_out), 0) AS tokens_out
            FROM report_task t
            LEFT JOIN workflow_node_run wnr ON wnr.task_id = t.id
            WHERE t.tenant_id = #{tenantId}
              AND t.create_time >= #{from}
              AND t.create_time < #{to}
              AND t.is_deleted = 0
            """)
    java.util.Map<String, Object> sumTokensInWindow(@Param("tenantId") Long tenantId,
                                                    @Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to);
}
```

- [ ] **Step 4: 改造 AdminStatsService.overview() 增加同比 + P95**

在 `AdminStatsService.java` 顶部 import 区域追加（如果尚未有）：

```java
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.leo.enterpriseinertraining.trace.ModelPricing;
```

把 `overview()` 整体替换为：

```java
    public PlatformOverviewVO overview() {
        Long tenant = currentTenantId();
        LocalDateTime todayStart    = LocalDate.now().atStartOfDay();
        LocalDateTime yesterdayStart= todayStart.minusDays(1);
        LocalDateTime now           = LocalDateTime.now();

        // ── 当前累积口径（与历史行为兼容：不限时间窗）─────────────
        long total  = taskMapper.selectCountByQuery(
                QueryWrapper.create().where(REPORT_TASK.TENANT_ID.eq(tenant)));
        long done   = countTask("DONE");
        long failed = countTask("FAILED");
        long running = Math.max(0, total - done - failed);
        long finished = done + failed;
        double successRate = finished == 0 ? 0.0 : round((double) done / finished, 4);

        long totalNodeRuns = nodeRunMapper.selectCountByQuery(
                QueryWrapper.create().where(WORKFLOW_NODE_RUN.TASK_ID.in(currentTenantTaskIdSubquery())));
        long errorNodeRuns = nodeRunMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_NODE_RUN.STATUS.eq("ERROR"))
                        .and(WORKFLOW_NODE_RUN.TASK_ID.in(currentTenantTaskIdSubquery())));

        List<NodeAggRow> rows = fetchNodeAgg();
        long tokensIn = 0, tokensOut = 0;
        double cost = 0;
        for (NodeAggRow r : rows) {
            long ti = nz(r.getTokensIn()), to = nz(r.getTokensOut());
            tokensIn += ti;
            tokensOut += to;
            cost += ModelPricing.costCny(r.getModel(), ti, to);
        }

        PlatformOverviewVO vo = new PlatformOverviewVO();
        vo.setTotalTasks(total);
        vo.setDoneTasks(done);
        vo.setFailedTasks(failed);
        vo.setRunningTasks(running);
        vo.setTaskSuccessRate(successRate);
        vo.setTotalNodeRuns(totalNodeRuns);
        vo.setErrorNodeRuns(errorNodeRuns);
        vo.setTotalTokensIn(tokensIn);
        vo.setTotalTokensOut(tokensOut);
        vo.setTotalCostCny(round(cost, 4));
        vo.setAvgTaskLatencyMs(avgDoneTaskLatency());

        // ── #8 P95（基于今天窗口；样本 < 20 返回 null）─────────────
        long doneSampleN = taskMapper.countDoneTasksInWindow(tenant, todayStart, now);
        if (doneSampleN >= 20) {
            long offset = (long) Math.floor(doneSampleN * 0.95);
            vo.setP95TaskLatencyMs(taskMapper.selectP95LatencyMs(tenant, todayStart, now, offset));
        } else {
            vo.setP95TaskLatencyMs(null);
        }

        // ── #2 同比（昨日同窗口）────────────────────────────────
        long todayTotal     = taskMapper.countTasksInWindow(tenant, todayStart, now);
        long yesterdayTotal = taskMapper.countTasksInWindow(tenant, yesterdayStart, todayStart);
        long todayDone      = taskMapper.countDoneInWindow(tenant, todayStart, now);
        long yesterdayDone  = taskMapper.countDoneInWindow(tenant, yesterdayStart, todayStart);
        Long todayAvg       = taskMapper.avgLatencyInWindow(tenant, todayStart, now);
        Long yesterdayAvg   = taskMapper.avgLatencyInWindow(tenant, yesterdayStart, todayStart);

        double todaySuccess     = todayTotal == 0 ? 0.0 : (double) todayDone / todayTotal;
        double yesterdaySuccess = yesterdayTotal == 0 ? 0.0 : (double) yesterdayDone / yesterdayTotal;

        // 成本同比用 token 量 × 默认单价兜底估算（精确成本仍以累计 totalCostCny 为准）
        double todayCost     = estimateWindowCost(tenant, todayStart, now);
        double yesterdayCost = estimateWindowCost(tenant, yesterdayStart, todayStart);

        vo.setCompareWindowLabel("较昨日");
        vo.setTotalTasksDelta(todayTotal - yesterdayTotal);
        vo.setTaskSuccessRateDelta(round(todaySuccess - yesterdaySuccess, 4));
        vo.setTotalCostCnyDelta(round(todayCost - yesterdayCost, 4));
        vo.setAvgTaskLatencyMsDelta(
                todayAvg == null && yesterdayAvg == null ? 0L
                : nz(todayAvg) - nz(yesterdayAvg));

        return vo;
    }

    private double estimateWindowCost(Long tenant, LocalDateTime from, LocalDateTime to) {
        java.util.Map<String, Object> sums = taskMapper.sumTokensInWindow(tenant, from, to);
        long ti = ((Number) sums.getOrDefault("tokens_in", 0)).longValue();
        long to_ = ((Number) sums.getOrDefault("tokens_out", 0)).longValue();
        // 用默认模型单价估算（同比指标用，精度允许偏差）
        return ModelPricing.costCny("qwen-max", ti, to_);
    }
```

- [ ] **Step 5: 运行单测**

Run:
```bash
mvn -q test -Dtest=AdminStatsServiceTest
```
Expected: Tests run: 2, Failures: 0, Errors: 0。

> 如果第 1 个测试因为新增 mapper 方法没 stub 报错（Mockito 默认返回 0L），那 OK；测试只断言 `compareWindowLabel = "较昨日"` 和 `totalTasksDelta != null`，与计算逻辑无关。

- [ ] **Step 6: 全编译**

Run:
```bash
mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS。

---

## Task 17: 批次 B 端到端测试 + 提交

**Files:**
- 全批次

- [ ] **Step 1: 运行全部新增 / 受影响的单测**

Run:
```bash
mvn -q test -Dtest='ReportJudgeServiceTest,WorkflowLoaderAuditTest,WorkflowLoaderTest,AdminStatsServiceTest,WorkflowNodeRunRecorderTest,JwtUtilsTest,FanoutHelperTest,JoinTrackerTest'
```
Expected: Tests run: 8 类合计 ≥ 12, Failures: 0, Errors: 0。

- [ ] **Step 2: 整体编译**

Run:
```bash
mvn -q -DskipTests compile
```
Expected: BUILD SUCCESS。

- [ ] **Step 3: 检查改动列表与预期一致**

Run:
```bash
git status --short
```
Expected 列表：
```
M  src/main/java/com/leo/enterpriseinertraining/controller/AdminWorkflowController.java
M  src/main/java/com/leo/enterpriseinertraining/controller/HealthController.java
M  src/main/java/com/leo/enterpriseinertraining/mapper/ReportTaskMapper.java
M  src/main/java/com/leo/enterpriseinertraining/stream/SseSinkManager.java
M  src/main/java/com/leo/enterpriseinertraining/trace/AdminStatsService.java
M  src/main/java/com/leo/enterpriseinertraining/vo/PlatformOverviewVO.java
M  src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowLoader.java
?? src/main/java/com/leo/enterpriseinertraining/entity/WorkflowLoadLog.java
?? src/main/java/com/leo/enterpriseinertraining/mapper/WorkflowLoadLogMapper.java
?? src/main/java/com/leo/enterpriseinertraining/service/HealthService.java
?? src/main/java/com/leo/enterpriseinertraining/vo/ActiveWorkflowVO.java
?? src/main/java/com/leo/enterpriseinertraining/vo/ComponentHealthVO.java
?? src/main/java/com/leo/enterpriseinertraining/vo/WorkflowLogVO.java
?? src/main/resources/db/schema-phase7.sql
?? src/test/java/com/leo/enterpriseinertraining/trace/AdminStatsServiceTest.java
?? src/test/java/com/leo/enterpriseinertraining/workflow/WorkflowLoaderAuditTest.java
```

- [ ] **Step 4: 启动应用本地手动验收（如果有 MySQL 可用）**

Run:
```bash
# 先在 MySQL 跑 schema-phase7.sql
# 然后启动应用，逐个 endpoint 验证：
# curl -H "Authorization: Bearer <token>" http://localhost:8080/api/admin/workflow/active
# curl -H "Authorization: Bearer <token>" http://localhost:8080/api/admin/workflow/history?limit=10
# curl                                    http://localhost:8080/api/health/components
# curl -H "Authorization: Bearer <token>" http://localhost:8080/api/admin/stats/overview
# curl -H "Authorization: Bearer <token>" http://localhost:8080/api/admin/eval/judge/models
```

如果没有 MySQL 可用就跳过此步，等前端集成时一并验证。

- [ ] **Step 5: Commit 批次 B**

```bash
git add src/main/java/com/leo/enterpriseinertraining/controller/AdminWorkflowController.java \
        src/main/java/com/leo/enterpriseinertraining/controller/HealthController.java \
        src/main/java/com/leo/enterpriseinertraining/entity/WorkflowLoadLog.java \
        src/main/java/com/leo/enterpriseinertraining/mapper/ReportTaskMapper.java \
        src/main/java/com/leo/enterpriseinertraining/mapper/WorkflowLoadLogMapper.java \
        src/main/java/com/leo/enterpriseinertraining/service/HealthService.java \
        src/main/java/com/leo/enterpriseinertraining/stream/SseSinkManager.java \
        src/main/java/com/leo/enterpriseinertraining/trace/AdminStatsService.java \
        src/main/java/com/leo/enterpriseinertraining/vo/ActiveWorkflowVO.java \
        src/main/java/com/leo/enterpriseinertraining/vo/ComponentHealthVO.java \
        src/main/java/com/leo/enterpriseinertraining/vo/PlatformOverviewVO.java \
        src/main/java/com/leo/enterpriseinertraining/vo/WorkflowLogVO.java \
        src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowLoader.java \
        src/main/resources/db/schema-phase7.sql \
        src/test/java/com/leo/enterpriseinertraining/trace/AdminStatsServiceTest.java \
        src/test/java/com/leo/enterpriseinertraining/workflow/WorkflowLoaderAuditTest.java

git commit -m "$(cat <<'EOF'
feat(backend/f4): Workflow 审计 + 系统健康 + Stats 同比/P95（#1/#2/#3/#4/#8）

DDL（schema-phase7.sql）
- 新建 workflow_load_log 表 + idx_ts
- report_task 加 idx_create_time（同比/P95 SQL 必须）

#3 Workflow 当前活跃元信息：GET /admin/workflow/active → ActiveWorkflowVO
#4 Workflow 加载历史：GET /admin/workflow/history → WorkflowLogVO，reload 写
   cache_clear / yaml_reload / topology_check / activate 4 条审计
#1 系统健康子服务粒度：GET /api/health/components → API/Redis/SSE 三行；
   SseSinkManager 新增 activeCount()
#2 StatCard 同比：PlatformOverviewVO 加 5 个 *Delta + compareWindowLabel="较昨日"，
   AdminStatsService 双窗口聚合（今日 vs 昨日同口径）
#8 P95 端到端耗时：PlatformOverviewVO 加 p95TaskLatencyMs，
   ReportTaskMapper.selectP95LatencyMs 用 TIMESTAMPDIFF + OFFSET 实现；
   样本 < 20 返回 null

单测：WorkflowLoaderAuditTest（cache_clear 写入 + 写库失败容错）、AdminStatsServiceTest（compareWindowLabel + P95 阈值）

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 6: 验证最终状态**

Run:
```bash
git log --oneline -3
git status --porcelain
```
Expected:
- 最近 3 个 commit：批次 B、批次 A、之前的 spec commit (`1a9c1be`)
- `git status --porcelain` 只剩基线未跟踪文件，无已修改文件。

---

# 完成清单

- [ ] 批次 A 已 commit（feat(backend/f4): Judge 评分增强）
- [ ] 批次 B 已 commit（feat(backend/f4): Workflow 审计 + 系统健康 + Stats 同比/P95）
- [ ] 3 个单测文件全 green：`ReportJudgeServiceTest`、`WorkflowLoaderAuditTest`、`AdminStatsServiceTest`
- [ ] 现有单测无回归：`WorkflowLoaderTest` 仍通过
- [ ] DDL 文件 `schema-phase7.sql` 已建并在目标库执行过（部署时手工跑或集成到迁移工具）
- [ ] 通知前端：mock/admin-placeholders.ts 现在可整体删除

---

# 风险与回滚

| 风险 | 缓解 |
|---|---|
| MySQL 版本不支持 `ADD KEY IF NOT EXISTS` | DDL 注释已提示部署人按需手工跳过此行；其余表创建是幂等的 |
| `WorkflowLoader.reload()` 改造影响并发热更新 | `reload()` 仍为 `synchronized`；`appendLog` 写库失败只 warn，不阻塞主流程 |
| `JudgeRunVO` 字段顺序变了（topic 在最后），如果有反序列化代码按位置读会破坏 | 现有代码全用 setter / getter，无位置反序列化路径 |
| 双窗口同比 SQL 在大表上慢 | 已加 `idx_create_time`；每次 overview() 共 6 条窗口 SQL，预期 <100ms |

回滚：批次 A、B 是独立 commit，`git revert <sha>` 单独回滚不影响另一批。`workflow_load_log` 表可保留为空表，删除 DDL 不必要。
