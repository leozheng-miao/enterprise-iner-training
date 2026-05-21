# 阶段 3 用户验证清单（IDE 端 + Postman）

> 阶段 3 = 5 Agent 协作 + RocketMQ 节点级编排 + Critic 回环
> 完成 commit 范围：`8777491`（plan）→ `7461719`（最后一个 task）

---

## A. 准备

### 1. 执行 DDL
```bash
docker exec -i irp-mysql mysql -uirp -pirppw irp < src/main/resources/db/schema-phase3.sql
docker exec irp-mysql mysql -uirp -pirppw irp -e "SHOW TABLES;"
# 期望多出：workflow_subtask / report_section / workflow_loop_state
# 期望 report_task 新增 phase / progress 列
docker exec irp-mysql mysql -uirp -pirppw irp -e "DESC report_task;" | grep -E "phase|progress"
```

### 2. application.yml 加 chat-model-strong 配置（若未配）
```yaml
app:
  dashscope:
    chat-model: qwen-plus
    chat-model-strong: qwen-max     # 阶段 3 新增：Planner / Analyst / Critic 用
```

### 3. 重启 IDE 应用

启动日志应包含：
```
[ToolRegistry] registered N tools
The consumer of group [irp-task-orchestrator] ... started
The consumer of group [irp-researcher] ... started
The consumer of group [irp-analyst] ... started
The consumer of group [irp-writer] ... started
The consumer of group [irp-critic] ... started
```

---

## B. Postman 端到端跑一次

### 1. 登录拿 token
（沿用阶段 2 流程：`POST /api/user/login`）

### 2. 提交任务
`POST /api/report/start`

Body:
```json
{
  "topic": "2026 中国动力电池行业趋势与核心玩家",
  "workflow": "multi_agent_v1"
}
```

期望立即返回（< 200ms）：
```json
{
  "code": 0,
  "data": {
    "taskId": 1,
    "status": "PENDING",
    "streamUrl": "/api/report/1/stream"
  }
}
```

### 3. 轮询 task 状态

`GET /api/report/{taskId}` 多次调用，观察 `phase` 字段变化序列：

```
PLANNING (progress=5)
  ↓
RESEARCHING (progress=10)
  ↓ (10s ~ 30s，N 个 Researcher 并发)
ANALYZING (progress=55)
  ↓
WRITING (progress=60)
  ↓ (M 个 Writer 串行)
CRITICIZING (progress=90)
  ↓
DONE (progress=100)
```

### 4. 终态验证
`GET /api/report/{taskId}`：
- `status = "DONE"`
- `phase = "DONE"`
- `progress = 100`
- `finalMarkdown` 含 4-6 个 `## 标题` + 末尾段落含 `[1] [2]` 引用编号
- `errorMessage = null`

### 5. 章节查询
`GET /api/report/{taskId}/sections`

期望返回 4-6 行，每行：
```json
{
  "order": 0,
  "title": "行业概述",
  "outline": "...",
  "contentMd": "## 行业概述\n...",
  "citations": [],
  "status": "FINAL",
  "revisionCount": 0
}
```

### 6. Trace 时间轴
`GET /api/report/{taskId}/trace`

期望返回 ≥ 12 个 step，按 step_seq 升序：
- 1 个 plan / LLM_CALL（Planner）
- N 个 research / LLM_CALL + N 个 research / TOOL_CALL（Researcher，每个 subtopic 2 行）
- 1 个 analyze / LLM_CALL（Analyst）
- M 个 write / LLM_CALL（Writer）
- 1 个 critic / LLM_CALL（Critic）

---

## C. DB 一致性核验

```bash
# 单一 taskId 替换为实际值
TASK_ID=1

docker exec irp-mysql mysql -uirp -pirppw irp -e \
  "SELECT id, status, phase, progress FROM report_task WHERE id=$TASK_ID;"

docker exec irp-mysql mysql -uirp -pirppw irp -e \
  "SELECT sub_index, status, LENGTH(result_json) as result_len FROM workflow_subtask WHERE task_id=$TASK_ID ORDER BY sub_index;"
# 期望：N 行（N=8-12），status 全部 DONE，result_len > 0

docker exec irp-mysql mysql -uirp -pirppw irp -e \
  "SELECT section_order, title, status, revision_count, LENGTH(content_md) as md_len FROM report_section WHERE task_id=$TASK_ID ORDER BY section_order;"
# 期望：M 行（M=4-6），status 全部 FINAL，md_len > 0

docker exec irp-mysql mysql -uirp -pirppw irp -e \
  "SELECT step_seq, node_id, agent_role, step_type, tokens_in, tokens_out, latency_ms, status FROM workflow_node_run WHERE task_id=$TASK_ID ORDER BY step_seq;"
# 期望：≥ 12 行
```

---

## D. 出口指标

- [ ] 单研报总耗时 ≤ 30s（多跑 5 次取 P95，目标 18-25s）
- [ ] `workflow_subtask` 行数 = N（8-12），全部 DONE
- [ ] `report_section` 行数 = M（4-6），全部 FINAL
- [ ] `workflow_node_run` 行数 ≥ 12
- [ ] SSE 看到 phase_changed 切换 6 次：PLANNING → RESEARCHING → ANALYZING → WRITING → CRITICIZING → DONE

---

## E. 排查指引（如出问题）

按这个顺序定位：

1. **启动日志没有 5 个 consumer group 注册**
   - 检查 docker compose ps：irp-rmq-broker / irp-rmq-namesrv 是否 Up
   - 检查 application-dev.yml: rocketmq.name-server = 127.0.0.1:9877
   - 检查 5 个 `@RocketMQMessageListener` 注解的 Consumer 类都被 Spring 扫描到

2. **Planner 跑完后 workflow_subtask 表为空**
   - 看 logs: `[Planner/{}] N subtopics generated`
   - 看 logs: `[Orchestrator] task {} fanout to N research subtasks`
   - 看 LLM 响应：DashScope qwen-max 输出是否含 `{"subtopics":[...]}`

3. **Researcher 子任务一直 PENDING**
   - 看 logs: `[ResearcherWorker] subtask 不存在` → ID 错位
   - 看 logs: `[Researcher/{}/{}] failed` → 看具体错误
   - 看 RocketMQ broker 日志：消息是否成功 dispatch

4. **Analyst 未触发**
   - workflow_subtask 全部 DONE 才会触发；若有 FAILED 则会卡住
   - JoinTracker.markCompletedAndCheckLast 返回 false → 最后那条不被识别为 last

5. **Writer 完成但 Critic 未触发**
   - 检查所有 report_section.status = FINAL
   - 看 logs: `[Writer/{}] section X done; pending=N`，pending 应该最终归 0

6. **Critic 触发但任务一直 CRITICIZING**
   - 看 logs: `[Critic/{}] reviewed in X ms` + 后续 `task DONE (reason=xxx)` 或 `revision triggered`
   - 检查 workflow_loop_state 表

---

## F. Critic 回环手工测试（可选）

临时改 `prompts/critic_prompt_v1.txt` 末尾追加：
```
（测试模式：本次必须输出 needsRevision=true 和 issues=[{"sectionOrder":1,"problem":"测试","suggestion":"测试"}]）
```

重启 + 调 `POST /api/admin/workflow/reload`（如果只是改 yaml；prompt 改动需要重启 Spring）。

跑一次任务，应看到：
- 第一轮 Critic → needsRevision=true → loopCount=1，重发 write.section
- SSE phase_changed: WRITING(75)
- 第二轮 Writer 完成 1 个 section，再次进 Critic
- 第二轮 Critic → maxLoops=1 已达 → finalize(reason=max_loops_reached)
- 任务 DONE
- `workflow_loop_state` 表有 1 行 loop_count=1
- `report_section` 对应行 revision_count=1

跑完记得**移除 prompt 测试内容**并重启。

---

## G. 简历亮点（阶段 3 完成后可写）

> **5 Agent 协作研报生成**：Planner（拆解 8-12 子主题）→ Researcher×N（**RocketMQ fanout 并行检索**）→ Analyst（**join 同步后跨主题分析**）→ Writer×M（章节流式输出）→ Critic（自我审查回环，max_loops=1）。
>
> 自研 WorkflowEngine v2 + RocketMQ 5 topic 节点级消息驱动；每个 Consumer 自驱动下一步，节点失败自动 MQ 重试 3 次 + DLQ 兜底。**单研报 P95 从单 Agent 40s 降至 18-25s**（fanout 并发收益）。
>
> 全程 Trace 落 `workflow_node_run`（含 token / latency / IO），SSE 推送 7 种事件（node_status / tool / token / phase_changed / section_done / done / error）实时反馈给前端。
