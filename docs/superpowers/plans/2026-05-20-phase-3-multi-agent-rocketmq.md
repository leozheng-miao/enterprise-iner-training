# 阶段 3：Multi-Agent + RocketMQ 编排 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal：** 把阶段 2 单 Agent 升级为 5 Agent（Planner/Researcher×N/Analyst/Writer/Critic）通过 RocketMQ 节点级 topic 驱动的分布式 DAG 编排，支持 fanout/join + Critic 回环（max_loops=1），单研报 P95 目标 ≤ 25s。

**Architecture：** ReportService 接收主题 → 发 MQ `task.created` → TaskOrchestratorConsumer 加载 Workflow → 派发首节点对应 MQ topic → 每个 Agent Consumer 完成节点后回调 WorkflowEngine.dispatchNext → 触发下一节点 MQ 消息或 fanout/join 等待。Fanout 通过 `workflow_subtask` 表存 N 行子任务状态；Join 通过乐观 update + `SELECT COUNT(*) WHERE status<>'DONE'` 检查最后完成者。Critic 回环通过 `workflow_loop_state` 表计数 + 重发 `write.section` 实现。

**Tech Stack：** Spring Boot 3.5.14 / Java 21 Virtual Thread / RocketMQ Spring Boot Starter 2.3.3 / Spring AI 1.0 GA (qwen-plus/qwen-max via DashScope) / MyBatis-Flex 1.10.6 / SnakeYAML / Lombok

**项目根：** `/Users/zhengsmacbook/Desktop/miniProject/claude/enterprise-iner-training`
**Baseline：** spec commit `7f1d2c3`（阶段 0/1/2 全部完成）

---

## ⚠️ 严格规则（subagent 必须遵守）

- **不要跑 `./mvnw` / `docker` / `curl`** — 用户在 IDE 验证
- **不要 source .env / 启动应用 / 执行 DDL**
- 只做三件事：① 写文件 ② `git add` + `git commit` ③ 报告
- 若 Spring AI 1.0 / RocketMQ Spring API 与 plan 代码不匹配 → **BLOCKED 报回**，不要私自更名
- review 只看 `git diff` 静态对照，不跑命令

---

## 关键架构决策（实施前必须理解）

### 决策 1：Agent 接口不变，AgentInvocation 加 fanout 字段

阶段 2 的 `Agent.execute(AgentInvocation, SseSink) → AgentResult` 保留。`AgentInvocation` 加两个字段：

```java
public record AgentInvocation(
    long taskId,
    String nodeId,
    String topic,
    List<AgentTool> tools,
    String promptRef,
    Integer fanoutIndex,     // 新增：fanout 子任务索引（非 fanout 节点为 null）
    String fanoutPayload     // 新增：子任务负载 JSON（subtopic / sectionPlan 序列化）
) {}
```

每个 Consumer 收到 MQ 消息 → 反序列化 `MqMessage` → 构造 `AgentInvocation` → 调对应 `Agent.execute(...)`。Agent 内部判断是否走 fanout 分支。

### 决策 2：WorkflowEngine v2 重构为事件驱动 dispatcher

阶段 2 的 `WorkflowEngine.execute(...)` 同步遍历节点 → 删除。
新增：
- `start(taskId, workflowName, topic)`：发 `task.created` MQ 入口
- `dispatchFirstNode(taskId, def)`：加载 YAML 拿第一节点，发对应 topic 消息
- `dispatchNext(taskId, completedNodeId, output)`：找下一节点（含 fanout 拆分 / join 等待）

阶段 2 的 `WorkflowEngine.execute()` 整体方法删除；阶段 2 的 `researcher_only_v1.yaml` 仍可用，作为"只有 1 个非 fanout 节点的 DAG"由新引擎驱动（向后兼容）。

### 决策 3：ReportServiceImpl.runWorkflow 删除 Virtual Thread executor

阶段 2 的 `executor.submit(() -> runWorkflow(...))` 改为 `engine.start(taskId, workflowName, topic)`。`@PostConstruct`/`@PreDestroy` 删除。task PENDING → RUNNING 的状态切换移到 `TaskOrchestratorConsumer`。

### 决策 4：节点输出存哪里？

每个节点完成后产出的"结构化数据"（如 Planner 的 subtopics 列表、Analyst 的 sections）需要给下一节点用。两种选择：

A. MQ payload 携带（消息体可能很大）
B. **DB 存储 + MQ 只携带 task_id**（推荐）

选 B：
- Planner 输出 → 直接写 `workflow_subtask` 表 N 行
- Analyst 输出 sections → 直接写 N 行 `report_section` (status=DRAFT, content_md=null)
- Writer 完成单 section → update `report_section` set status=FINAL, content_md=...
- Critic 读所有 `report_section` 行

这样 MQ 消息体永远很小（`{taskId, nodeId, fanoutIndex}`），DB 充当节点间数据交换 + 状态机。

---

## File Structure（阶段 3 完成后新增/修改）

```
src/main/resources/
├── db/
│   └── schema-phase3.sql                          ← 新增（3 表 + alter）
├── workflow/
│   └── multi_agent_v1.yaml                        ← 新增
└── prompts/
    ├── researcher_prompt_v2.txt                   ← 升级（替换 v1 的用户视角 → fanout 单 subtopic 视角）
    ├── planner_prompt_v1.txt                      ← 新增
    ├── analyst_prompt_v1.txt                      ← 新增
    ├── writer_prompt_v1.txt                       ← 新增
    └── critic_prompt_v1.txt                       ← 新增

src/main/java/com/leo/enterpriseinertraining/
├── entity/
│   ├── ReportTask.java                            ← 修改（加 phase / progress 字段）
│   ├── WorkflowSubtask.java                       ← 新增
│   ├── ReportSection.java                         ← 新增
│   └── WorkflowLoopState.java                     ← 新增
├── mapper/
│   ├── WorkflowSubtaskMapper.java                 ← 新增
│   ├── ReportSectionMapper.java                   ← 新增
│   └── WorkflowLoopStateMapper.java               ← 新增
├── mq/
│   ├── MqTopics.java                              ← 新增（常量）
│   ├── MqMessage.java                             ← 新增（record）
│   ├── MqProducerService.java                     ← 新增
│   ├── TaskOrchestratorConsumer.java              ← 新增（task.created）
│   ├── ResearcherWorker.java                      ← 新增（research.task）
│   ├── AnalystConsumer.java                       ← 新增（analyze.task）
│   ├── WriterConsumer.java                        ← 新增（write.section + revision）
│   └── CriticConsumer.java                        ← 新增（critic.task）
├── agent/
│   ├── core/
│   │   └── AgentInvocation.java                   ← 修改（加 fanoutIndex / fanoutPayload）
│   └── role/
│       ├── ResearcherAgent.java                   ← 改造（适配 fanoutPayload 输入）
│       ├── PlannerAgent.java                      ← 新增
│       ├── AnalystAgent.java                      ← 新增
│       ├── WriterAgent.java                       ← 新增
│       └── CriticAgent.java                       ← 新增
├── workflow/
│   ├── WorkflowDef.java                           ← 修改（加 model 字段）
│   ├── WorkflowNode.java                          ← 修改（加 fanout / join / maxLoops / onNeedsRevision / model 字段）
│   ├── FanoutSpec.java                            ← 新增（嵌套 record）
│   ├── WorkflowLoader.java                        ← 修改（加 reload 方法）
│   ├── WorkflowEngine.java                        ← 重构 v2
│   ├── FanoutHelper.java                          ← 新增（${expr} 解析）
│   └── JoinTracker.java                           ← 新增
├── service/
│   ├── ReportService.java                         ← 修改（runWorkflow 改发 MQ）
│   └── impl/
│       └── ReportServiceImpl.java                 ← 修改（删 executor，调 engine.start）
├── stream/
│   └── SseSink.java                               ← 修改（加 phaseChanged / sectionDone）
├── controller/
│   ├── ReportController.java                      ← 修改（加 /{id}/sections）
│   └── AdminWorkflowController.java               ← 新增
└── vo/
    ├── ReportResultVO.java                        ← 修改（加 phase / progress）
    └── SectionVO.java                             ← 新增

src/test/java/com/leo/enterpriseinertraining/
├── workflow/
│   ├── FanoutHelperTest.java                      ← TDD
│   └── JoinTrackerTest.java                       ← TDD（纯逻辑部分）
└── mq/
    └── MqProducerServiceTest.java                 ← 简单单测
```

---

## Task 1：DDL 升级 + 3 个 entity + 3 个 mapper + ReportTask 加字段

**Files:**
- Create: `src/main/resources/db/schema-phase3.sql`
- Modify: `src/main/java/com/leo/enterpriseinertraining/entity/ReportTask.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/entity/{WorkflowSubtask,ReportSection,WorkflowLoopState}.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/mapper/{WorkflowSubtaskMapper,ReportSectionMapper,WorkflowLoopStateMapper}.java`

- [ ] **Step 1.1：创建 `schema-phase3.sql`**

```sql
-- 阶段 3 DDL：3 张新表 + report_task 加 phase/progress

-- 1) report_task 加 phase / progress
ALTER TABLE `report_task`
  ADD COLUMN `phase` VARCHAR(32) NOT NULL DEFAULT 'PLANNING'
    COMMENT 'PLANNING/RESEARCHING/ANALYZING/WRITING/CRITICIZING/DONE' AFTER `status`,
  ADD COLUMN `progress` INT NOT NULL DEFAULT 0 COMMENT '0-100 前端进度条' AFTER `phase`;

-- 2) workflow_subtask: fanout 子任务状态表
CREATE TABLE IF NOT EXISTS `workflow_subtask` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`        BIGINT       NOT NULL,
    `sub_index`      INT          NOT NULL,
    `subtopic`       VARCHAR(512) NOT NULL,
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
    `result_json`    MEDIUMTEXT   DEFAULT NULL,
    `error_message`  VARCHAR(1024) DEFAULT NULL,
    `started_at`     DATETIME     DEFAULT NULL,
    `finished_at`    DATETIME     DEFAULT NULL,
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_subindex` (`task_id`, `sub_index`),
    KEY `idx_task_status` (`task_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Fanout 子任务状态';

-- 3) report_section: 研报章节存储
CREATE TABLE IF NOT EXISTS `report_section` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`         BIGINT       NOT NULL,
    `section_order`   INT          NOT NULL,
    `title`           VARCHAR(255) NOT NULL,
    `outline`         MEDIUMTEXT   DEFAULT NULL COMMENT 'Analyst 输出的章节大纲',
    `related_subtopics_json` JSON  DEFAULT NULL COMMENT '[0,2,5] 关联的 subtopic 索引',
    `content_md`      MEDIUMTEXT   DEFAULT NULL,
    `citations_json`  JSON         DEFAULT NULL,
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/FINAL/REVISING',
    `revision_count`  INT          NOT NULL DEFAULT 0,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_section_order` (`task_id`, `section_order`),
    KEY `idx_task_status` (`task_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='研报章节';

-- 4) workflow_loop_state: Critic 回环计数
CREATE TABLE IF NOT EXISTS `workflow_loop_state` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`      BIGINT       NOT NULL,
    `node_id`      VARCHAR(64)  NOT NULL,
    `loop_count`   INT          NOT NULL DEFAULT 0,
    `max_loops`    INT          NOT NULL DEFAULT 1,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_node` (`task_id`, `node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Workflow 回环计数';
```

用户后续在 IDE Terminal 执行：
`docker exec -i irp-mysql mysql -uirp -pirppw irp < src/main/resources/db/schema-phase3.sql`

- [ ] **Step 1.2：修改 `ReportTask.java` 加 2 字段**

读 `src/main/java/com/leo/enterpriseinertraining/entity/ReportTask.java`，在 `private String workflowName;` 后插入：

```java
    private String phase;            // PLANNING/RESEARCHING/ANALYZING/WRITING/CRITICIZING/DONE
    private Integer progress;        // 0-100
```

其它字段保持不变。

- [ ] **Step 1.3：`WorkflowSubtask.java`**

```java
package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("workflow_subtask")
public class WorkflowSubtask extends BaseEntity {

    private Long taskId;
    private Integer subIndex;
    private String subtopic;
    private String status;          // PENDING/RUNNING/DONE/FAILED
    private String resultJson;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
```

- [ ] **Step 1.4：`ReportSection.java`**

```java
package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("report_section")
public class ReportSection extends BaseEntity {

    private Long taskId;
    private Integer sectionOrder;
    private String title;
    private String outline;

    @Column(typeHandler = JacksonTypeHandler.class)
    private List<Integer> relatedSubtopicsJson;

    private String contentMd;

    @Column(typeHandler = JacksonTypeHandler.class)
    private List<ReportTask.CitationData> citationsJson;

    private String status;          // DRAFT/FINAL/REVISING
    private Integer revisionCount;
}
```

- [ ] **Step 1.5：`WorkflowLoopState.java`**

```java
package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("workflow_loop_state")
public class WorkflowLoopState extends BaseEntity {

    private Long taskId;
    private String nodeId;
    private Integer loopCount;
    private Integer maxLoops;
}
```

- [ ] **Step 1.6：3 个 Mapper**

`WorkflowSubtaskMapper.java`:
```java
package com.leo.enterpriseinertraining.mapper;

import com.leo.enterpriseinertraining.entity.WorkflowSubtask;
import com.mybatisflex.core.BaseMapper;

public interface WorkflowSubtaskMapper extends BaseMapper<WorkflowSubtask> {
}
```

`ReportSectionMapper.java`:
```java
package com.leo.enterpriseinertraining.mapper;

import com.leo.enterpriseinertraining.entity.ReportSection;
import com.mybatisflex.core.BaseMapper;

public interface ReportSectionMapper extends BaseMapper<ReportSection> {
}
```

`WorkflowLoopStateMapper.java`:
```java
package com.leo.enterpriseinertraining.mapper;

import com.leo.enterpriseinertraining.entity.WorkflowLoopState;
import com.mybatisflex.core.BaseMapper;

public interface WorkflowLoopStateMapper extends BaseMapper<WorkflowLoopState> {
}
```

- [ ] **Step 1.7：提交**

```bash
git add src/main/resources/db/schema-phase3.sql \
        src/main/java/com/leo/enterpriseinertraining/entity/ReportTask.java \
        src/main/java/com/leo/enterpriseinertraining/entity/WorkflowSubtask.java \
        src/main/java/com/leo/enterpriseinertraining/entity/ReportSection.java \
        src/main/java/com/leo/enterpriseinertraining/entity/WorkflowLoopState.java \
        src/main/java/com/leo/enterpriseinertraining/mapper/WorkflowSubtaskMapper.java \
        src/main/java/com/leo/enterpriseinertraining/mapper/ReportSectionMapper.java \
        src/main/java/com/leo/enterpriseinertraining/mapper/WorkflowLoopStateMapper.java
git commit -m "feat(phase3): DDL + 3 entities (subtask/section/loop_state) + report_task phase/progress"
```

---

## Task 2：RocketMQ 配置 + MqTopics 常量 + MqMessage record

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/mq/MqTopics.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/mq/MqMessage.java`
- Modify: `src/main/resources/application-dev.yml`（追加 consumer group 配置；producer 已有）

- [ ] **Step 2.1：`MqTopics.java`**

```java
package com.leo.enterpriseinertraining.mq;

/**
 * RocketMQ topic / consumer group 常量。
 *
 * <p>命名约定：{@code irp.<phase>.<event>}，全部小写。</p>
 */
public final class MqTopics {

    private MqTopics() {}

    public static final String TASK_CREATED   = "irp.task.created";
    public static final String RESEARCH_TASK  = "irp.research.task";
    public static final String ANALYZE_TASK   = "irp.analyze.task";
    public static final String WRITE_SECTION  = "irp.write.section";
    public static final String CRITIC_TASK    = "irp.critic.task";

    // Consumer groups
    public static final String GROUP_ORCHESTRATOR = "irp-task-orchestrator";
    public static final String GROUP_RESEARCHER   = "irp-researcher";
    public static final String GROUP_ANALYST      = "irp-analyst";
    public static final String GROUP_WRITER       = "irp-writer";
    public static final String GROUP_CRITIC       = "irp-critic";
}
```

- [ ] **Step 2.2：`MqMessage.java`**

```java
package com.leo.enterpriseinertraining.mq;

/**
 * 所有节点消息统一格式。
 *
 * <p>{@code fanoutIndex} 非 fanout 节点为 null；{@code payload} 节点专属 JSON（小负载），
 * 大块数据走 DB（{@code workflow_subtask} / {@code report_section}）。</p>
 */
public record MqMessage(
        long taskId,
        String nodeId,
        Integer fanoutIndex,
        String payload
) {
    public static MqMessage of(long taskId, String nodeId) {
        return new MqMessage(taskId, nodeId, null, null);
    }
    public static MqMessage fanout(long taskId, String nodeId, int index) {
        return new MqMessage(taskId, nodeId, index, null);
    }
}
```

- [ ] **Step 2.3：修改 `application-dev.yml`**

读 `src/main/resources/application-dev.yml`，找到 `rocketmq:` 块：
```yaml
rocketmq:
  name-server: 127.0.0.1:9877
  producer:
    group: irp-producer-group
    send-message-timeout: 5000
```

不动任何已有内容（consumer-group 在 `@RocketMQMessageListener` 注解里写，application.yml 无需配）。

- [ ] **Step 2.4：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/mq/MqTopics.java \
        src/main/java/com/leo/enterpriseinertraining/mq/MqMessage.java
git commit -m "feat(mq): MqTopics constants + MqMessage record"
```

---

## Task 3：MqProducerService（统一发送）+ 单测

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/mq/MqProducerService.java`
- Test: `src/test/java/com/leo/enterpriseinertraining/mq/MqProducerServiceTest.java`

- [ ] **Step 3.1：实现 `MqProducerService.java`**

```java
package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * 统一发送 {@link MqMessage} 到指定 topic。
 *
 * <p>用 {@link RocketMQTemplate#syncSend(String, org.springframework.messaging.Message)}
 * 同步发送，失败抛 RuntimeException。Consumer 端如果业务失败可以靠 RocketMQ 自动重试 3 次。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqProducerService {

    private final RocketMQTemplate template;
    private final ObjectMapper om;

    public void send(String topic, MqMessage msg) {
        try {
            String body = om.writeValueAsString(msg);
            var spring = MessageBuilder.withPayload(body).build();
            var result = template.syncSend(topic, spring);
            log.info("[MQ] send to {} taskId={} nodeId={} fanoutIndex={} → {}",
                    topic, msg.taskId(), msg.nodeId(), msg.fanoutIndex(), result.getSendStatus());
        } catch (Exception e) {
            throw new RuntimeException("MQ 发送失败: topic=" + topic + " err=" + e.getMessage(), e);
        }
    }

    /** 批量发送同 topic（fanout 场景用，单次循环 syncSend；RocketMQ 内部已有连接池）。 */
    public void sendAll(String topic, java.util.List<MqMessage> messages) {
        for (MqMessage m : messages) send(topic, m);
    }
}
```

- [ ] **Step 3.2：单测 `MqProducerServiceTest.java`（纯参数序列化校验，不调真实 MQ）**

```java
package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MqProducerServiceTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void mqMessage_of_no_fanout() {
        MqMessage m = MqMessage.of(42L, "research");
        assertEquals(42L, m.taskId());
        assertEquals("research", m.nodeId());
        assertNull(m.fanoutIndex());
        assertNull(m.payload());
    }

    @Test
    void mqMessage_fanout() {
        MqMessage m = MqMessage.fanout(42L, "research", 3);
        assertEquals(3, m.fanoutIndex());
    }

    @Test
    void mqMessage_serialize_roundtrip() throws Exception {
        MqMessage m = MqMessage.fanout(7L, "write", 2);
        String json = om.writeValueAsString(m);
        MqMessage back = om.readValue(json, MqMessage.class);
        assertEquals(m, back);
    }
}
```

- [ ] **Step 3.3：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/mq/MqProducerService.java \
        src/test/java/com/leo/enterpriseinertraining/mq/MqProducerServiceTest.java
git commit -m "feat(mq): MqProducerService + tests (record serialize roundtrip)"
```

---

## Task 4：5 个 prompt 文件 + multi_agent_v1.yaml

**Files:**
- Create: `src/main/resources/prompts/researcher_prompt_v2.txt`
- Create: `src/main/resources/prompts/planner_prompt_v1.txt`
- Create: `src/main/resources/prompts/analyst_prompt_v1.txt`
- Create: `src/main/resources/prompts/writer_prompt_v1.txt`
- Create: `src/main/resources/prompts/critic_prompt_v1.txt`
- Create: `src/main/resources/workflow/multi_agent_v1.yaml`

- [ ] **Step 4.1：`planner_prompt_v1.txt`**

```
你是一位行业研究项目经理。根据用户提供的研究主题，将其拆解为 **8-12 个互不重叠**的子主题，每个子主题描述一个具体的研究维度。

要求：
- 维度示例：市场规模 / 产业链上中下游 / 关键玩家 / 政策环境 / 技术路线 / 出口与全球化 / 风险挑战 / 趋势预测 / 典型案例 / 同业对比
- 子主题文本 10-25 字，具体而非抽象（"2026 中国动力电池出货量与全球份额" 而不是 "电池数据"）
- 互不重叠：避免两个子主题在同一维度
- 严格输出 JSON：{"subtopics": ["子主题1", "子主题2", "..."]}
- 不要输出任何元描述（"我将拆解..."），直接出 JSON
```

- [ ] **Step 4.2：`researcher_prompt_v2.txt`**

```
你是一位行业研究员。针对给定的"子主题"，使用 hybrid_search 工具检索企业知识库（1-3 次），输出该子主题的研究材料。

要求：
- 调用 hybrid_search 时，query 要具体且包含子主题的关键词
- 综合检索片段，写 200-400 字的子主题研究内容（中文，纯文本，不要 markdown 标题）
- 不要捏造数字；所有具体数据必须来自检索片段
- 严格输出 JSON：{"content": "...", "citations": [{"docId": 1, "docTitle": "...", "source": "...", "sectionTitle": "...", "pageStart": 0, "pageEnd": 0}]}
- 不要输出元描述，直接出 JSON
```

- [ ] **Step 4.3：`analyst_prompt_v1.txt`**

```
你是一位资深行业分析师。基于多个子主题的研究材料，进行跨主题分析，并设计研报章节大纲。

要求：
- 通读所有子主题的 content（已在 user message 中给出）
- 跨主题做：矛盾发现、趋势归纳、关键玩家对比、风险识别
- 设计 **4-6 个章节**（section），每个章节聚焦一个清晰主题
- 章节顺序：通常 行业概述 → 产业链/技术 → 玩家格局 → 风险与机遇 → 趋势预测
- 每个章节关联 1-N 个子主题索引（relatedSubtopics）
- 严格输出 JSON：{"sections": [{"order": 0, "title": "行业概述", "outline": "本章节论述...", "relatedSubtopics": [0, 1]}, ...]}
- 不要输出元描述，直接出 JSON
```

- [ ] **Step 4.4：`writer_prompt_v1.txt`**

```
你是一位研报作者。基于章节大纲和检索材料，写一段研报章节的正文。

要求：
- 章节标题、大纲、关联子主题的研究内容已在 user message 中给出
- 写 300-500 字 markdown：
  - 以 `## <title>` 二级标题开头
  - 主体 1-3 个段落，段落间自然衔接
  - 所有数字必须来自给定材料，绝不捏造
  - 末尾段落标注引用编号 `[1] [2]` 等（与子主题里 citations 的 docId 对应）
- 不要输出元描述、不要包裹 ```markdown 代码块，直接给出 markdown
```

- [ ] **Step 4.5：`critic_prompt_v1.txt`**

```
你是一位严苛的研报编辑。审查整份研报，找出问题。

要求：
- 通读整份 markdown（已在 user message 给出）
- 检查 4 类问题：
  1. 事实错误（数字明显错误、时间错乱）
  2. 引用与正文不符（[N] 引用编号与上下文论述无关）
  3. 逻辑断层（段落间跳跃、结论无依据）
  4. 数字不一致（同一指标在不同章节给出不同数值）
- 最多列 5 个最严重的 issue
- 如全部通过：{"needsRevision": false, "issues": []}
- 如有问题：{"needsRevision": true, "issues": [{"sectionOrder": 2, "problem": "...", "suggestion": "..."}]}
- 严格输出 JSON，不要元描述
```

- [ ] **Step 4.6：`multi_agent_v1.yaml`**

```yaml
name: multi_agent_v1
version: 1
nodes:
  - id: plan
    agent: Planner
    prompt: planner_prompt@v1
    model: qwen-max
    next: [research]

  - id: research
    agent: Researcher
    prompt: researcher_prompt@v2
    model: qwen-plus
    tools:
      - hybrid_search
    fanout:
      from: "${plan.subtopics}"
    join: all
    next: [analyze]

  - id: analyze
    agent: Analyst
    prompt: analyst_prompt@v1
    model: qwen-max
    next: [write]

  - id: write
    agent: Writer
    prompt: writer_prompt@v1
    model: qwen-plus
    fanout:
      from: "${analyze.sections}"
    join: all
    next: [critic]

  - id: critic
    agent: Critic
    prompt: critic_prompt@v1
    model: qwen-max
    max_loops: 1
    on_needs_revision: write
```

- [ ] **Step 4.7：提交**

```bash
git add src/main/resources/prompts/researcher_prompt_v2.txt \
        src/main/resources/prompts/planner_prompt_v1.txt \
        src/main/resources/prompts/analyst_prompt_v1.txt \
        src/main/resources/prompts/writer_prompt_v1.txt \
        src/main/resources/prompts/critic_prompt_v1.txt \
        src/main/resources/workflow/multi_agent_v1.yaml
git commit -m "feat(phase3): 5 agent prompts + multi_agent_v1 workflow YAML"
```

---

## Task 5：WorkflowDef / WorkflowNode 字段扩展 + FanoutSpec + WorkflowLoader.reload

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowDef.java`（无变化）
- Modify: `src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowNode.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/workflow/FanoutSpec.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowLoader.java`
- Modify: `src/test/java/com/leo/enterpriseinertraining/workflow/WorkflowLoaderTest.java`

- [ ] **Step 5.1：`FanoutSpec.java`**

```java
package com.leo.enterpriseinertraining.workflow;

import lombok.Data;

/**
 * fanout 配置：
 * <pre>
 * fanout:
 *   from: "${plan.subtopics}"
 * </pre>
 */
@Data
public class FanoutSpec {
    /** 引用前序节点输出的表达式，如 {@code ${plan.subtopics}}。 */
    private String from;
}
```

- [ ] **Step 5.2：扩展 `WorkflowNode.java`**

整个文件替换为：

```java
package com.leo.enterpriseinertraining.workflow;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowNode {
    private String id;
    private String agent;
    private String prompt;
    private String model;                  // 阶段 3 新增：覆盖默认模型
    private List<String> tools;
    private FanoutSpec fanout;              // 阶段 3 新增
    private String join;                    // 阶段 3 新增："all" 等
    private List<String> next;              // 阶段 3 新增：YAML 是 list
    private Integer maxLoops;               // 阶段 3 新增（Critic 用）
    private String onNeedsRevision;         // 阶段 3 新增（指向回环目标 nodeId）
}
```

> 注：阶段 2 的 `next` 字段类型从 `List<String>` 保持；YAML 里 `next: [research]` 与 `next: research` 都得能解析。`researcher_only_v1.yaml` 没有 `next`（只有 1 节点），保持兼容。

- [ ] **Step 5.3：`WorkflowLoader.java` 加 reload 方法**

读现有 `WorkflowLoader.java`，在 `load` 方法后追加：

```java
    /** 清空缓存，下次 load 会重新读 YAML（admin 接口用）。 */
    public synchronized void reload() {
        cache.clear();
        log.info("[WorkflowLoader] cache cleared (reload)");
    }
```

- [ ] **Step 5.4：扩展 `WorkflowLoaderTest.java`**

读现有 test，在末尾追加新测试：

```java
    @Test
    void load_multi_agent_v1_fanout_join() {
        WorkflowDef def = loader.load("multi_agent_v1");
        assertEquals("multi_agent_v1", def.getName());
        assertEquals(5, def.getNodes().size());

        // research 节点带 fanout
        WorkflowNode research = def.getNodes().stream()
                .filter(n -> "research".equals(n.getId())).findFirst().orElseThrow();
        assertNotNull(research.getFanout());
        assertEquals("${plan.subtopics}", research.getFanout().getFrom());
        assertEquals("all", research.getJoin());
        assertEquals("qwen-plus", research.getModel());
        assertTrue(research.getTools().contains("hybrid_search"));

        // critic 节点带 maxLoops + onNeedsRevision
        WorkflowNode critic = def.getNodes().stream()
                .filter(n -> "critic".equals(n.getId())).findFirst().orElseThrow();
        assertEquals(1, critic.getMaxLoops());
        assertEquals("write", critic.getOnNeedsRevision());
    }

    @Test
    void reload_clears_cache() {
        loader.load("researcher_only_v1");
        loader.reload();
        // 不抛异常即可（cache 已清）；再次 load 会重新读
        WorkflowDef def = loader.load("researcher_only_v1");
        assertNotNull(def);
    }
```

- [ ] **Step 5.5：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/workflow/FanoutSpec.java \
        src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowNode.java \
        src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowLoader.java \
        src/test/java/com/leo/enterpriseinertraining/workflow/WorkflowLoaderTest.java
git commit -m "feat(workflow): extend WorkflowNode (fanout/join/maxLoops/onNeedsRevision/model) + reload"
```

---

## Task 6：AgentInvocation 字段扩展 + FanoutHelper（TDD）

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/agent/core/AgentInvocation.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/workflow/FanoutHelper.java`
- Test: `src/test/java/com/leo/enterpriseinertraining/workflow/FanoutHelperTest.java`

- [ ] **Step 6.1：扩展 `AgentInvocation.java`**

整个文件替换为：

```java
package com.leo.enterpriseinertraining.agent.core;

import com.leo.enterpriseinertraining.agent.tool.AgentTool;

import java.util.List;

public record AgentInvocation(
        long taskId,
        String nodeId,
        String topic,
        List<AgentTool> tools,
        String promptRef,
        Integer fanoutIndex,         // 阶段 3：fanout 子任务索引，非 fanout 节点为 null
        String fanoutPayload         // 阶段 3：单个子任务负载 JSON 字符串
) {
    /** 阶段 2 兼容构造：不带 fanout 字段。 */
    public static AgentInvocation of(long taskId, String nodeId, String topic,
                                     List<AgentTool> tools, String promptRef) {
        return new AgentInvocation(taskId, nodeId, topic, tools, promptRef, null, null);
    }
}
```

> **影响**：阶段 2 创建 `AgentInvocation` 的地方（`WorkflowEngine.execute`）现在调用旧的 6 参数构造会编译错。Task 14 重构 `WorkflowEngine` 时改成 `AgentInvocation.of(...)`。其它现有调用没有了（`WorkflowEngine` 是唯一使用方）。

- [ ] **Step 6.2：先写测试 `FanoutHelperTest.java`**

```java
package com.leo.enterpriseinertraining.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FanoutHelperTest {

    private final FanoutHelper helper = new FanoutHelper(new ObjectMapper());

    @Test
    void resolve_simple_list_expression() {
        Map<String, Object> outputs = Map.of(
                "plan", Map.of("subtopics", List.of("a", "b", "c"))
        );
        List<String> result = helper.resolveAsStringList("${plan.subtopics}", outputs);
        assertEquals(3, result.size());
        assertEquals("a", result.get(0));
        assertEquals("c", result.get(2));
    }

    @Test
    void resolve_unknown_node_throws() {
        Map<String, Object> outputs = Map.of();
        assertThrows(RuntimeException.class,
                () -> helper.resolveAsStringList("${ghost.field}", outputs));
    }

    @Test
    void resolve_unknown_field_throws() {
        Map<String, Object> outputs = Map.of("plan", Map.of("other", List.of()));
        assertThrows(RuntimeException.class,
                () -> helper.resolveAsStringList("${plan.subtopics}", outputs));
    }

    @Test
    void resolve_non_list_value_throws() {
        Map<String, Object> outputs = Map.of("plan", Map.of("subtopics", "not-a-list"));
        assertThrows(RuntimeException.class,
                () -> helper.resolveAsStringList("${plan.subtopics}", outputs));
    }

    @Test
    void resolve_list_of_objects_to_json_strings() throws Exception {
        Map<String, Object> outputs = Map.of(
                "analyze", Map.of("sections", List.of(
                        Map.of("order", 0, "title", "概述"),
                        Map.of("order", 1, "title", "趋势")))
        );
        List<String> jsons = helper.resolveAsJsonStringList("${analyze.sections}", outputs);
        assertEquals(2, jsons.size());
        ObjectMapper om = new ObjectMapper();
        Map<?, ?> first = om.readValue(jsons.get(0), Map.class);
        assertEquals("概述", first.get("title"));
    }
}
```

- [ ] **Step 6.3：实现 `FanoutHelper.java`**

```java
package com.leo.enterpriseinertraining.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 {@code ${nodeId.field}} 表达式，从前序节点输出 Map 取值。
 *
 * <p>支持两种返回形态：</p>
 * <ul>
 *   <li>{@link #resolveAsStringList} — 期望取到 {@code List<String>}（如 planner 输出的 subtopics）</li>
 *   <li>{@link #resolveAsJsonStringList} — 期望取到 {@code List<Object>}，每项 JSON 序列化（如 analyst 输出的 sections）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class FanoutHelper {

    private static final Pattern EXPR = Pattern.compile("^\\$\\{([a-zA-Z_][\\w]*)\\.([a-zA-Z_][\\w]*)}$");

    private final ObjectMapper om;

    public List<String> resolveAsStringList(String expr, Map<String, Object> previousOutputs) {
        Object value = resolveRaw(expr, previousOutputs);
        if (!(value instanceof List<?> list)) {
            throw new RuntimeException("fanout 表达式期望 List，但拿到 " + className(value) + ": " + expr);
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof String s) result.add(s);
            else throw new RuntimeException("fanout List 元素期望 String，但拿到 " + className(item));
        }
        return result;
    }

    public List<String> resolveAsJsonStringList(String expr, Map<String, Object> previousOutputs) {
        Object value = resolveRaw(expr, previousOutputs);
        if (!(value instanceof List<?> list)) {
            throw new RuntimeException("fanout 表达式期望 List: " + expr);
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            try {
                result.add(om.writeValueAsString(item));
            } catch (Exception e) {
                throw new RuntimeException("fanout 元素 JSON 序列化失败: " + e.getMessage(), e);
            }
        }
        return result;
    }

    private Object resolveRaw(String expr, Map<String, Object> previousOutputs) {
        Matcher m = EXPR.matcher(expr.trim());
        if (!m.matches()) throw new RuntimeException("非法 fanout 表达式: " + expr);
        String node = m.group(1);
        String field = m.group(2);
        Object nodeOutput = previousOutputs.get(node);
        if (nodeOutput == null) throw new RuntimeException("未找到前序节点输出: " + node);
        if (!(nodeOutput instanceof Map<?, ?> map)) {
            throw new RuntimeException("节点输出不是 Map: " + node);
        }
        Object value = map.get(field);
        if (value == null) throw new RuntimeException("节点 " + node + " 输出无字段 " + field);
        return value;
    }

    private static String className(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName();
    }
}
```

- [ ] **Step 6.4：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/agent/core/AgentInvocation.java \
        src/main/java/com/leo/enterpriseinertraining/workflow/FanoutHelper.java \
        src/test/java/com/leo/enterpriseinertraining/workflow/FanoutHelperTest.java
git commit -m "feat(workflow): AgentInvocation +fanout fields + FanoutHelper (\${node.field} resolver, TDD)"
```

---

## Task 7：JoinTracker + WorkflowSubtaskService

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/workflow/JoinTracker.java`
- Test: `src/test/java/com/leo/enterpriseinertraining/workflow/JoinTrackerTest.java`

- [ ] **Step 7.1：先写测试 `JoinTrackerTest.java`（纯逻辑层 mock mapper）**

```java
package com.leo.enterpriseinertraining.workflow;

import com.leo.enterpriseinertraining.entity.WorkflowSubtask;
import com.leo.enterpriseinertraining.mapper.WorkflowSubtaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JoinTrackerTest {

    @Test
    void markCompleted_returns_true_when_last() {
        WorkflowSubtaskMapper mapper = mock(WorkflowSubtaskMapper.class);
        WorkflowSubtask row = new WorkflowSubtask();
        row.setId(1L); row.setTaskId(42L); row.setSubIndex(2);
        row.setStatus("RUNNING");
        when(mapper.selectListByQuery(any())).thenReturn(List.of(row));
        when(mapper.update(any())).thenReturn(1);

        JoinTracker tracker = new JoinTracker(mapper);
        // 模拟：当前是最后一个完成的（query "status != DONE" 返回 0 行）
        when(mapper.selectCountByQuery(any())).thenReturn(0L);

        boolean isLast = tracker.markCompletedAndCheckLast(42L, 2, "{\"content\":\"ok\"}");
        assertTrue(isLast);

        ArgumentCaptor<WorkflowSubtask> cap = ArgumentCaptor.forClass(WorkflowSubtask.class);
        verify(mapper).update(cap.capture());
        assertEquals("DONE", cap.getValue().getStatus());
        assertEquals("{\"content\":\"ok\"}", cap.getValue().getResultJson());
    }

    @Test
    void markCompleted_returns_false_when_others_running() {
        WorkflowSubtaskMapper mapper = mock(WorkflowSubtaskMapper.class);
        WorkflowSubtask row = new WorkflowSubtask();
        row.setId(1L); row.setTaskId(42L); row.setSubIndex(0);
        row.setStatus("RUNNING");
        when(mapper.selectListByQuery(any())).thenReturn(List.of(row));
        when(mapper.update(any())).thenReturn(1);
        when(mapper.selectCountByQuery(any())).thenReturn(3L);   // 还有 3 个没完成

        JoinTracker tracker = new JoinTracker(mapper);
        boolean isLast = tracker.markCompletedAndCheckLast(42L, 0, "{}");
        assertFalse(isLast);
    }

    @Test
    void markCompleted_subtask_not_found_throws() {
        WorkflowSubtaskMapper mapper = mock(WorkflowSubtaskMapper.class);
        when(mapper.selectListByQuery(any())).thenReturn(List.of());

        JoinTracker tracker = new JoinTracker(mapper);
        assertThrows(RuntimeException.class,
                () -> tracker.markCompletedAndCheckLast(42L, 99, "{}"));
    }
}
```

> 注：pom.xml 已经引入 `spring-boot-starter-test`，它默认包含 Mockito。如果 import 找不到，临时不写 Mockito 测试也行（只测逻辑）；但 Mockito 应该自动可用。

- [ ] **Step 7.2：实现 `JoinTracker.java`**

```java
package com.leo.enterpriseinertraining.workflow;

import com.leo.enterpriseinertraining.entity.WorkflowSubtask;
import com.leo.enterpriseinertraining.mapper.WorkflowSubtaskMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.leo.enterpriseinertraining.entity.table.WorkflowSubtaskTableDef.WORKFLOW_SUBTASK;

/**
 * Fanout/Join 状态追踪：
 *
 * <p>{@link #markCompletedAndCheckLast(long, int, String)}：把单个子任务标 DONE，
 * 再查同 task 是否还有非 DONE 行；返回 true 即"我是最后一个完成的"，调用方应触发下游节点。</p>
 *
 * <p>整体在事务里跑：update 与 count 之间不会有其它子任务竞争 race（依赖 MySQL 默认 RR 隔离 + 唯一索引）。
 * 实际并发场景下两个 worker 同时标完最后两条 → 都查到 count=0 → 都触发下游 —— 这种"双发"风险通过
 * 下游 consumer 的幂等去重（同 taskId + nodeId 已发过则忽略）容忍，详见 {@code dispatchNext}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JoinTracker {

    private final WorkflowSubtaskMapper mapper;

    @Transactional
    public boolean markCompletedAndCheckLast(long taskId, int subIndex, String resultJson) {
        // 1) 取自己这一行
        List<WorkflowSubtask> rows = mapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_SUBTASK.TASK_ID.eq(taskId))
                        .and(WORKFLOW_SUBTASK.SUB_INDEX.eq(subIndex)));
        if (rows.isEmpty()) {
            throw new RuntimeException("找不到子任务 taskId=" + taskId + " subIndex=" + subIndex);
        }
        WorkflowSubtask row = rows.get(0);
        row.setStatus("DONE");
        row.setResultJson(resultJson);
        row.setFinishedAt(LocalDateTime.now());
        mapper.update(row);

        // 2) 查同 task 还有几行非 DONE
        long remaining = mapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_SUBTASK.TASK_ID.eq(taskId))
                        .and(WORKFLOW_SUBTASK.STATUS.ne("DONE")));

        boolean isLast = remaining == 0;
        log.info("[Join] task={} subIndex={} done; remaining={}; isLast={}", taskId, subIndex, remaining, isLast);
        return isLast;
    }

    /** 把单子任务标 FAILED（不触发下游；workflow 整体标记 FAILED 由调用方决定）。 */
    @Transactional
    public void markFailed(long taskId, int subIndex, String errorMessage) {
        List<WorkflowSubtask> rows = mapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_SUBTASK.TASK_ID.eq(taskId))
                        .and(WORKFLOW_SUBTASK.SUB_INDEX.eq(subIndex)));
        if (rows.isEmpty()) return;
        WorkflowSubtask row = rows.get(0);
        row.setStatus("FAILED");
        row.setErrorMessage(errorMessage);
        row.setFinishedAt(LocalDateTime.now());
        mapper.update(row);
    }
}
```

- [ ] **Step 7.3：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/workflow/JoinTracker.java \
        src/test/java/com/leo/enterpriseinertraining/workflow/JoinTrackerTest.java
git commit -m "feat(workflow): JoinTracker (mark DONE + last-completed detection) with Mockito tests"
```

---

## Task 8：PlannerAgent + TaskOrchestratorConsumer（topic=task.created）

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/agent/role/PlannerAgent.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/mq/TaskOrchestratorConsumer.java`

- [ ] **Step 8.1：`PlannerAgent.java`**

```java
package com.leo.enterpriseinertraining.agent.role;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.prompt.PromptLoader;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.trace.WorkflowNodeRunRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Planner Agent：把 topic 拆解为 N 个子主题。
 * 无工具；qwen-max 输出 JSON {subtopics: [...]}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlannerAgent implements Agent {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptLoader promptLoader;
    private final WorkflowNodeRunRecorder recorder;
    private final ObjectMapper om;

    @Value("${app.dashscope.chat-model-strong:qwen-max}")
    private String strongModel;

    @Override
    public String role() { return "Planner"; }

    @Override
    public AgentResult execute(AgentInvocation inv, SseSink sink) {
        long t0 = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.load(inv.promptRef());
            ChatClient client = chatClientBuilder.build();

            var response = client.prompt()
                    .system(systemPrompt)
                    .user(inv.topic())
                    .call()
                    .chatResponse();

            String raw = response.getResult().getOutput().getText();
            int tokensIn = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getPromptTokens().intValue();
            int tokensOut = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getCompletionTokens().intValue();
            int latency = (int) (System.currentTimeMillis() - t0);

            // 解析 subtopics
            List<String> subtopics = parseSubtopics(raw);

            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), strongModel,
                    Map.of("topic", inv.topic()),
                    Map.of("subtopics", subtopics, "raw", raw),
                    tokensIn, tokensOut, latency, "OK", null);

            log.info("[Planner/{}] {} subtopics generated in {} ms", inv.taskId(), subtopics.size(), latency);

            // markdown 字段塞 raw（供调试）；citations 留空；下游 Consumer 不读这里，而是读 trace.output_json
            String resultJson = om.writeValueAsString(Map.of("subtopics", subtopics));
            return AgentResult.ok(resultJson, List.of());
        } catch (Exception e) {
            log.error("[Planner/{}] failed", inv.taskId(), e);
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), strongModel,
                    Map.of("topic", inv.topic()), null,
                    0, 0, (int) (System.currentTimeMillis() - t0),
                    "ERROR", e.getMessage());
            return AgentResult.error(e.getMessage());
        }
    }

    private List<String> parseSubtopics(String raw) throws Exception {
        // 容错：LLM 可能输出包含前后文本，找到第一个 { ... } 块
        int l = raw.indexOf('{'), r = raw.lastIndexOf('}');
        String json = (l >= 0 && r > l) ? raw.substring(l, r + 1) : raw;
        JsonNode node = om.readTree(json);
        JsonNode arr = node.get("subtopics");
        if (arr == null || !arr.isArray()) {
            throw new RuntimeException("Planner 输出缺少 subtopics 数组");
        }
        List<String> result = new ArrayList<>();
        arr.forEach(n -> result.add(n.asText()));
        if (result.size() < 3 || result.size() > 15) {
            log.warn("[Planner] subtopics 数量异常: {}, 截断到 3-12", result.size());
            if (result.size() > 12) return result.subList(0, 12);
        }
        return result;
    }
}
```

- [ ] **Step 8.2：`TaskOrchestratorConsumer.java`**

```java
package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.core.AgentStatus;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.entity.WorkflowSubtask;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.mapper.WorkflowSubtaskMapper;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.workflow.WorkflowDef;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import com.leo.enterpriseinertraining.workflow.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 消费 {@link MqTopics#TASK_CREATED}：加载 workflow → 调 PlannerAgent → 写 subtopics 到
 * workflow_subtask 表 → 发 N 条 research.task 消息触发 fanout。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopics.TASK_CREATED, consumerGroup = MqTopics.GROUP_ORCHESTRATOR)
public class TaskOrchestratorConsumer implements RocketMQListener<String> {

    private final ObjectMapper om;
    private final WorkflowLoader workflowLoader;
    private final ReportTaskMapper taskMapper;
    private final WorkflowSubtaskMapper subtaskMapper;
    private final List<Agent> agents;       // Spring 注入所有 Agent
    private final SseSinkManager sinkManager;
    private final MqProducerService producer;

    private Map<String, Agent> agentByRole;

    @Override
    public void onMessage(String body) {
        try {
            MqMessage msg = om.readValue(body, MqMessage.class);
            log.info("[Orchestrator] received task.created taskId={}", msg.taskId());
            handleTaskCreated(msg.taskId());
        } catch (Exception e) {
            log.error("[Orchestrator] handle failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);   // 抛出让 RocketMQ 重试
        }
    }

    private void handleTaskCreated(long taskId) throws Exception {
        ReportTask task = taskMapper.selectOneById(taskId);
        if (task == null) {
            log.warn("[Orchestrator] task {} 不存在，丢弃消息", taskId);
            return;
        }

        // PENDING → RUNNING + phase=PLANNING
        task.setStatus("RUNNING");
        task.setPhase("PLANNING");
        task.setProgress(5);
        task.setStartedAt(LocalDateTime.now());
        taskMapper.update(task);

        SseSink sink = sinkManager.get(taskId);
        if (sink != null) {
            sink.phaseChanged("PLANNING", 5);
            sink.nodeStatus("plan", "RUNNING");
        }

        // 加载 workflow，找到 plan 节点
        WorkflowDef def = workflowLoader.load(task.getWorkflowName());
        WorkflowNode plan = def.getNodes().stream()
                .filter(n -> "plan".equals(n.getId())).findFirst()
                .orElseThrow(() -> new RuntimeException("workflow 缺少 plan 节点"));

        // 调 Planner
        Agent planner = agentByRole().get(plan.getAgent());
        if (planner == null) throw new RuntimeException("未知 agent: " + plan.getAgent());

        AgentInvocation inv = AgentInvocation.of(
                taskId, plan.getId(), task.getTopic(), List.of(), plan.getPrompt());
        AgentResult result = planner.execute(inv, sink);

        if (result.status() == AgentStatus.ERROR) {
            failTask(task, "Planner 失败: " + result.errorMessage(), sink);
            return;
        }

        // 解析 Planner 输出（markdown 字段塞了 {"subtopics":[...]}）
        JsonNode node = om.readTree(result.markdown());
        JsonNode subtopicsArr = node.get("subtopics");
        if (subtopicsArr == null || !subtopicsArr.isArray() || subtopicsArr.isEmpty()) {
            failTask(task, "Planner 返回 subtopics 为空", sink);
            return;
        }

        // 写 N 行 workflow_subtask
        int n = subtopicsArr.size();
        for (int i = 0; i < n; i++) {
            WorkflowSubtask st = new WorkflowSubtask();
            st.setTaskId(taskId);
            st.setSubIndex(i);
            st.setSubtopic(subtopicsArr.get(i).asText());
            st.setStatus("PENDING");
            subtaskMapper.insert(st);
        }
        log.info("[Orchestrator] task {} fanout to {} research subtasks", taskId, n);

        // phase → RESEARCHING
        task.setPhase("RESEARCHING");
        task.setProgress(10);
        taskMapper.update(task);
        if (sink != null) {
            sink.nodeStatus("plan", "DONE");
            sink.phaseChanged("RESEARCHING", 10);
        }

        // 发 N 条 research.task
        for (int i = 0; i < n; i++) {
            producer.send(MqTopics.RESEARCH_TASK, MqMessage.fanout(taskId, "research", i));
        }
    }

    private void failTask(ReportTask task, String errorMessage, SseSink sink) {
        task.setStatus("FAILED");
        task.setPhase("DONE");
        task.setErrorMessage(errorMessage);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.update(task);
        if (sink != null) sink.error(errorMessage);
        sinkManager.remove(task.getId());
        log.error("[Orchestrator] task {} FAILED: {}", task.getId(), errorMessage);
    }

    private Map<String, Agent> agentByRole() {
        if (agentByRole == null) {
            agentByRole = new java.util.HashMap<>();
            for (Agent a : agents) agentByRole.put(a.role(), a);
        }
        return agentByRole;
    }
}
```

- [ ] **Step 8.3：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/agent/role/PlannerAgent.java \
        src/main/java/com/leo/enterpriseinertraining/mq/TaskOrchestratorConsumer.java
git commit -m "feat(phase3): PlannerAgent (qwen-max → subtopics JSON) + TaskOrchestratorConsumer (task.created → research fanout)"
```

---

## Task 9：ResearcherAgent 改造 + ResearcherWorker

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/agent/role/ResearcherAgent.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/mq/ResearcherWorker.java`

- [ ] **Step 9.1：改造 `ResearcherAgent.java`**

阶段 2 的 ResearcherAgent 接收 `inv.topic()` 处理整篇研究；阶段 3 改为接收 `inv.fanoutPayload()`（单个 subtopic 文本）做单子主题研究。

整个文件替换为：

```java
package com.leo.enterpriseinertraining.agent.role;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.*;
import com.leo.enterpriseinertraining.agent.prompt.PromptLoader;
import com.leo.enterpriseinertraining.agent.tool.AgentTool;
import com.leo.enterpriseinertraining.agent.tool.ToolInvocationTracer;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.trace.WorkflowNodeRunRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Researcher Agent（阶段 3 版）：处理单个 subtopic（来自 fanout），调 hybrid_search 工具，
 * 输出 JSON {content, citations}。
 *
 * <p>与阶段 2 区别：</p>
 * <ul>
 *   <li>阶段 2 输入是用户原始 topic，输出整段 markdown</li>
 *   <li>阶段 3 输入是单个 subtopic（{@code inv.fanoutPayload}），输出结构化 JSON</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResearcherAgent implements Agent {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptLoader promptLoader;
    private final WorkflowNodeRunRecorder recorder;
    private final ToolInvocationTracer tracer;
    private final ObjectMapper om;

    @Value("${app.dashscope.chat-model:qwen-plus}")
    private String defaultModel;

    @Override
    public String role() { return "Researcher"; }

    @Override
    public AgentResult execute(AgentInvocation inv, SseSink sink) {
        long t0 = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.load(inv.promptRef());
            // 阶段 3：fanoutPayload 是 subtopic 文本；阶段 2 用 topic 字段（向后兼容）
            String userInput = inv.fanoutPayload() != null ? inv.fanoutPayload() : inv.topic();

            List<ToolCallback> callbacks = new ArrayList<>();
            for (AgentTool t : inv.tools()) {
                callbacks.add(toCallback(t, inv, sink));
            }

            ChatClient client = chatClientBuilder.build();
            long llmStart = System.currentTimeMillis();
            var response = client.prompt()
                    .system(systemPrompt)
                    .user("研究子主题：" + userInput)
                    .toolCallbacks(callbacks.toArray(ToolCallback[]::new))
                    .call()
                    .chatResponse();
            long llmLatency = System.currentTimeMillis() - llmStart;

            String raw = response.getResult().getOutput().getText();
            int tokensIn = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getPromptTokens().intValue();
            int tokensOut = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getCompletionTokens().intValue();

            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), defaultModel,
                    Map.of("subtopic", userInput, "fanoutIndex", inv.fanoutIndex()),
                    Map.of("raw", raw),
                    tokensIn, tokensOut, (int) llmLatency, "OK", null);

            // 解析输出 JSON：{content, citations}
            String json = extractJson(raw);
            JsonNode node = om.readTree(json);
            String content = node.has("content") ? node.get("content").asText() : raw;
            // 把整个 json（含 content + citations）作为 markdown 字段返回（上游 Worker 落到 workflow_subtask.result_json）
            List<Citation> citations = parseCitations(node);

            log.info("[Researcher/{}/{}] done in {} ms (LLM {} ms)", inv.taskId(), inv.fanoutIndex(),
                    System.currentTimeMillis() - t0, llmLatency);
            return AgentResult.ok(json, citations);
        } catch (Exception e) {
            log.error("[Researcher/{}/{}] failed", inv.taskId(), inv.fanoutIndex(), e);
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), defaultModel,
                    Map.of("subtopic", inv.fanoutPayload(), "fanoutIndex", inv.fanoutIndex()), null,
                    0, 0, (int) (System.currentTimeMillis() - t0),
                    "ERROR", e.getMessage());
            return AgentResult.error(e.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ToolCallback toCallback(AgentTool t, AgentInvocation inv, SseSink sink) {
        Function<Object, Object> wrapped = (Object params) -> {
            long start = System.currentTimeMillis();
            try {
                if (sink != null) sink.tool(t.name(), safeJson(params), "invoking...");
                Object result = t.invoke(params);
                int latency = (int) (System.currentTimeMillis() - start);
                tracer.recordSuccess(inv.taskId(), inv.nodeId(), role(), t.name(), params, result, latency);
                return result;
            } catch (Exception e) {
                int latency = (int) (System.currentTimeMillis() - start);
                tracer.recordError(inv.taskId(), inv.nodeId(), role(), t.name(), params, e, latency);
                throw new RuntimeException("tool '" + t.name() + "' invoke failed", e);
            }
        };
        return FunctionToolCallback.builder(t.name(), wrapped)
                .description(t.description())
                .inputType((Class) t.paramsType())
                .build();
    }

    private String extractJson(String s) {
        int l = s.indexOf('{'), r = s.lastIndexOf('}');
        return (l >= 0 && r > l) ? s.substring(l, r + 1) : s;
    }

    private List<Citation> parseCitations(JsonNode root) {
        List<Citation> result = new ArrayList<>();
        JsonNode arr = root.get("citations");
        if (arr != null && arr.isArray()) {
            for (JsonNode c : arr) {
                result.add(new Citation(
                        c.has("docId") ? c.get("docId").asLong() : null,
                        c.has("docTitle") ? c.get("docTitle").asText() : null,
                        c.has("source") ? c.get("source").asText() : null,
                        c.has("sectionTitle") ? c.get("sectionTitle").asText() : null,
                        c.has("pageStart") ? c.get("pageStart").asInt() : null,
                        c.has("pageEnd") ? c.get("pageEnd").asInt() : null));
            }
        }
        return result;
    }

    private String safeJson(Object o) {
        try { return om.writeValueAsString(o); } catch (Exception e) { return ""; }
    }
}
```

- [ ] **Step 9.2：`ResearcherWorker.java`**

```java
package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.core.AgentStatus;
import com.leo.enterpriseinertraining.agent.tool.AgentTool;
import com.leo.enterpriseinertraining.agent.tool.ToolRegistryService;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.entity.WorkflowSubtask;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.mapper.WorkflowSubtaskMapper;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.workflow.JoinTracker;
import com.leo.enterpriseinertraining.workflow.WorkflowDef;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import com.leo.enterpriseinertraining.workflow.WorkflowNode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.leo.enterpriseinertraining.entity.table.WorkflowSubtaskTableDef.WORKFLOW_SUBTASK;

/**
 * 消费 {@link MqTopics#RESEARCH_TASK}：从 workflow_subtask 读自己那一行的 subtopic →
 * 调 ResearcherAgent → 把 result_json 写回 + Join 检查 → 若是最后一个完成，发 analyze.task。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.RESEARCH_TASK,
        consumerGroup = MqTopics.GROUP_RESEARCHER,
        consumeThreadMax = 16,
        consumeThreadNumber = 4)
public class ResearcherWorker implements RocketMQListener<String> {

    private final ObjectMapper om;
    private final WorkflowSubtaskMapper subtaskMapper;
    private final ReportTaskMapper taskMapper;
    private final WorkflowLoader workflowLoader;
    private final ToolRegistryService toolRegistry;
    private final List<Agent> agents;
    private final JoinTracker joinTracker;
    private final SseSinkManager sinkManager;
    private final MqProducerService producer;

    private Map<String, Agent> agentByRole;

    @Override
    public void onMessage(String body) {
        try {
            MqMessage msg = om.readValue(body, MqMessage.class);
            handleResearchTask(msg);
        } catch (Exception e) {
            log.error("[ResearcherWorker] handle failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void handleResearchTask(MqMessage msg) throws Exception {
        long taskId = msg.taskId();
        int subIndex = msg.fanoutIndex();

        // 1) 取 subtask 行
        List<WorkflowSubtask> rows = subtaskMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_SUBTASK.TASK_ID.eq(taskId))
                        .and(WORKFLOW_SUBTASK.SUB_INDEX.eq(subIndex)));
        if (rows.isEmpty()) {
            log.warn("[ResearcherWorker] subtask 不存在 taskId={} subIndex={}", taskId, subIndex);
            return;
        }
        WorkflowSubtask st = rows.get(0);
        if ("DONE".equals(st.getStatus())) {
            log.info("[ResearcherWorker] subtask 已完成跳过 taskId={} subIndex={}", taskId, subIndex);
            return;
        }
        st.setStatus("RUNNING");
        st.setStartedAt(java.time.LocalDateTime.now());
        subtaskMapper.update(st);

        // 2) 加载 workflow 拿 research 节点配置
        ReportTask task = taskMapper.selectOneById(taskId);
        WorkflowDef def = workflowLoader.load(task.getWorkflowName());
        WorkflowNode researchNode = def.getNodes().stream()
                .filter(n -> "research".equals(n.getId())).findFirst().orElseThrow();

        // 3) 调 Researcher
        List<AgentTool> tools = toolRegistry.byNames(
                researchNode.getTools() == null ? List.of() : researchNode.getTools());
        Agent researcher = agentByRole().get(researchNode.getAgent());

        var sink = sinkManager.get(taskId);
        if (sink != null) sink.nodeStatus("research#" + subIndex, "RUNNING");

        AgentInvocation inv = new AgentInvocation(
                taskId, "research", task.getTopic(),
                tools, researchNode.getPrompt(), subIndex, st.getSubtopic());
        AgentResult result = researcher.execute(inv, sink);

        if (result.status() == AgentStatus.ERROR) {
            joinTracker.markFailed(taskId, subIndex, result.errorMessage());
            if (sink != null) {
                sink.nodeStatus("research#" + subIndex, "FAILED");
                // 整体不 fail —— 让其它子任务继续；最终如果全部失败再 fail
            }
            return;
        }

        // 4) Join 标 DONE + 检查是否最后
        boolean isLast = joinTracker.markCompletedAndCheckLast(taskId, subIndex, result.markdown());

        if (sink != null) sink.nodeStatus("research#" + subIndex, "DONE");

        if (isLast) {
            // 全部子任务完成 → phase ANALYZING + 发 analyze.task
            task.setPhase("ANALYZING");
            task.setProgress(55);
            taskMapper.update(task);
            if (sink != null) sink.phaseChanged("ANALYZING", 55);
            producer.send(MqTopics.ANALYZE_TASK, MqMessage.of(taskId, "analyze"));
        }
    }

    private Map<String, Agent> agentByRole() {
        if (agentByRole == null) {
            agentByRole = new java.util.HashMap<>();
            for (Agent a : agents) agentByRole.put(a.role(), a);
        }
        return agentByRole;
    }
}
```

- [ ] **Step 9.3：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/agent/role/ResearcherAgent.java \
        src/main/java/com/leo/enterpriseinertraining/mq/ResearcherWorker.java
git commit -m "feat(phase3): ResearcherAgent fanout payload + ResearcherWorker (consume research.task, join, dispatch analyze.task)"
```

---

## Task 10：AnalystAgent + AnalystConsumer

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/agent/role/AnalystAgent.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/mq/AnalystConsumer.java`

- [ ] **Step 10.1：`AnalystAgent.java`**

```java
package com.leo.enterpriseinertraining.agent.role;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.prompt.PromptLoader;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.trace.WorkflowNodeRunRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Analyst Agent：跨子主题分析 + 设计章节大纲。
 * 输入（fanoutPayload）：拼接好的所有子主题研究内容；输出：{sections: [...]} JSON。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalystAgent implements Agent {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptLoader promptLoader;
    private final WorkflowNodeRunRecorder recorder;
    private final ObjectMapper om;

    @Value("${app.dashscope.chat-model-strong:qwen-max}")
    private String strongModel;

    @Override
    public String role() { return "Analyst"; }

    @Override
    public AgentResult execute(AgentInvocation inv, SseSink sink) {
        long t0 = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.load(inv.promptRef());
            ChatClient client = chatClientBuilder.build();

            var response = client.prompt()
                    .system(systemPrompt)
                    .user("以下是 N 个子主题的研究材料（JSON 数组）：\n" + inv.fanoutPayload())
                    .call()
                    .chatResponse();

            String raw = response.getResult().getOutput().getText();
            int tokensIn = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getPromptTokens().intValue();
            int tokensOut = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getCompletionTokens().intValue();
            int latency = (int) (System.currentTimeMillis() - t0);

            // 截取 JSON
            int l = raw.indexOf('{'), r = raw.lastIndexOf('}');
            String json = (l >= 0 && r > l) ? raw.substring(l, r + 1) : raw;
            // 验证可解析
            JsonNode node = om.readTree(json);
            JsonNode sections = node.get("sections");
            if (sections == null || !sections.isArray() || sections.isEmpty()) {
                throw new RuntimeException("Analyst 输出缺少 sections 数组");
            }

            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), strongModel,
                    Map.of("inputPreview", inv.fanoutPayload() == null ? "" :
                            inv.fanoutPayload().substring(0, Math.min(500, inv.fanoutPayload().length()))),
                    Map.of("sections", sections, "raw", raw),
                    tokensIn, tokensOut, latency, "OK", null);

            log.info("[Analyst/{}] {} sections planned in {} ms", inv.taskId(), sections.size(), latency);
            return AgentResult.ok(json, List.of());
        } catch (Exception e) {
            log.error("[Analyst/{}] failed", inv.taskId(), e);
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), strongModel,
                    Map.of("inputPreview", ""), null,
                    0, 0, (int) (System.currentTimeMillis() - t0), "ERROR", e.getMessage());
            return AgentResult.error(e.getMessage());
        }
    }
}
```

- [ ] **Step 10.2：`AnalystConsumer.java`**

```java
package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.core.AgentStatus;
import com.leo.enterpriseinertraining.entity.ReportSection;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.entity.WorkflowSubtask;
import com.leo.enterpriseinertraining.mapper.ReportSectionMapper;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.mapper.WorkflowSubtaskMapper;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.workflow.WorkflowDef;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import com.leo.enterpriseinertraining.workflow.WorkflowNode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.leo.enterpriseinertraining.entity.table.WorkflowSubtaskTableDef.WORKFLOW_SUBTASK;

/**
 * 消费 {@link MqTopics#ANALYZE_TASK}：聚合所有 subtopic results → 调 AnalystAgent →
 * 把 sections 写到 report_section 表（status=DRAFT, content_md=null）→ 发 N 条 write.section。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopics.ANALYZE_TASK, consumerGroup = MqTopics.GROUP_ANALYST)
public class AnalystConsumer implements RocketMQListener<String> {

    private final ObjectMapper om;
    private final WorkflowSubtaskMapper subtaskMapper;
    private final ReportTaskMapper taskMapper;
    private final ReportSectionMapper sectionMapper;
    private final WorkflowLoader workflowLoader;
    private final List<Agent> agents;
    private final SseSinkManager sinkManager;
    private final MqProducerService producer;

    @Override
    public void onMessage(String body) {
        try {
            MqMessage msg = om.readValue(body, MqMessage.class);
            handleAnalyze(msg.taskId());
        } catch (Exception e) {
            log.error("[AnalystConsumer] failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void handleAnalyze(long taskId) throws Exception {
        ReportTask task = taskMapper.selectOneById(taskId);
        if (task == null) return;

        var sink = sinkManager.get(taskId);
        if (sink != null) sink.nodeStatus("analyze", "RUNNING");

        // 1) 读所有 DONE 子任务 → 拼成 JSON 数组
        List<WorkflowSubtask> subs = subtaskMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_SUBTASK.TASK_ID.eq(taskId))
                        .and(WORKFLOW_SUBTASK.STATUS.eq("DONE"))
                        .orderBy(WORKFLOW_SUBTASK.SUB_INDEX, true));
        if (subs.isEmpty()) {
            failTask(task, "无 DONE 子任务", sink);
            return;
        }
        List<Map<String, Object>> aggregate = new ArrayList<>();
        for (WorkflowSubtask s : subs) {
            Map<String, Object> item = new HashMap<>();
            item.put("subIndex", s.getSubIndex());
            item.put("subtopic", s.getSubtopic());
            try {
                item.put("result", om.readTree(s.getResultJson() == null ? "{}" : s.getResultJson()));
            } catch (Exception ignored) {
                item.put("result", Map.of("content", s.getResultJson()));
            }
            aggregate.add(item);
        }
        String aggregateJson = om.writeValueAsString(aggregate);

        // 2) 调 Analyst
        WorkflowDef def = workflowLoader.load(task.getWorkflowName());
        WorkflowNode analyzeNode = def.getNodes().stream()
                .filter(n -> "analyze".equals(n.getId())).findFirst().orElseThrow();
        Agent analyst = agentByRole().get(analyzeNode.getAgent());
        AgentInvocation inv = new AgentInvocation(
                taskId, "analyze", task.getTopic(),
                List.of(), analyzeNode.getPrompt(), null, aggregateJson);
        AgentResult result = analyst.execute(inv, sink);

        if (result.status() == AgentStatus.ERROR) {
            failTask(task, "Analyst 失败: " + result.errorMessage(), sink);
            return;
        }

        // 3) 把 sections 写 report_section 表
        JsonNode sectionsNode = om.readTree(result.markdown()).get("sections");
        int n = sectionsNode.size();
        for (int i = 0; i < n; i++) {
            JsonNode s = sectionsNode.get(i);
            ReportSection sec = new ReportSection();
            sec.setTaskId(taskId);
            sec.setSectionOrder(s.has("order") ? s.get("order").asInt() : i);
            sec.setTitle(s.has("title") ? s.get("title").asText() : "Section " + i);
            sec.setOutline(s.has("outline") ? s.get("outline").asText() : null);
            List<Integer> related = new ArrayList<>();
            if (s.has("relatedSubtopics") && s.get("relatedSubtopics").isArray()) {
                s.get("relatedSubtopics").forEach(r -> related.add(r.asInt()));
            }
            sec.setRelatedSubtopicsJson(related);
            sec.setStatus("DRAFT");
            sec.setRevisionCount(0);
            sectionMapper.insert(sec);
        }

        // 4) phase → WRITING
        task.setPhase("WRITING");
        task.setProgress(60);
        taskMapper.update(task);
        if (sink != null) {
            sink.nodeStatus("analyze", "DONE");
            sink.phaseChanged("WRITING", 60);
        }

        // 5) 发 N 条 write.section
        for (int i = 0; i < n; i++) {
            int order = sectionsNode.get(i).has("order") ? sectionsNode.get(i).get("order").asInt() : i;
            producer.send(MqTopics.WRITE_SECTION, MqMessage.fanout(taskId, "write", order));
        }
        log.info("[Analyst/{}] dispatched {} write.section messages", taskId, n);
    }

    private void failTask(ReportTask task, String errorMessage, com.leo.enterpriseinertraining.stream.SseSink sink) {
        task.setStatus("FAILED");
        task.setPhase("DONE");
        task.setErrorMessage(errorMessage);
        task.setFinishedAt(java.time.LocalDateTime.now());
        taskMapper.update(task);
        if (sink != null) sink.error(errorMessage);
        sinkManager.remove(task.getId());
    }

    private Map<String, Agent> agentByRole;
    private Map<String, Agent> agentByRole() {
        if (agentByRole == null) {
            agentByRole = new HashMap<>();
            for (Agent a : agents) agentByRole.put(a.role(), a);
        }
        return agentByRole;
    }
}
```

- [ ] **Step 10.3：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/agent/role/AnalystAgent.java \
        src/main/java/com/leo/enterpriseinertraining/mq/AnalystConsumer.java
git commit -m "feat(phase3): AnalystAgent + AnalystConsumer (aggregate subtopics → sections → fanout write)"
```

---

## Task 11：WriterAgent + WriterConsumer

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/agent/role/WriterAgent.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/mq/WriterConsumer.java`

- [ ] **Step 11.1：`WriterAgent.java`**

```java
package com.leo.enterpriseinertraining.agent.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.prompt.PromptLoader;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.trace.WorkflowNodeRunRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Writer Agent：写单个章节 markdown。
 * 输入（fanoutPayload）：含 {title, outline, materials} 的 JSON；输出：markdown 字符串。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WriterAgent implements Agent {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptLoader promptLoader;
    private final WorkflowNodeRunRecorder recorder;
    private final ObjectMapper om;

    @Value("${app.dashscope.chat-model:qwen-plus}")
    private String defaultModel;

    @Override
    public String role() { return "Writer"; }

    @Override
    public AgentResult execute(AgentInvocation inv, SseSink sink) {
        long t0 = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.load(inv.promptRef());
            ChatClient client = chatClientBuilder.build();

            var response = client.prompt()
                    .system(systemPrompt)
                    .user(inv.fanoutPayload())
                    .call()
                    .chatResponse();

            String md = response.getResult().getOutput().getText();
            int tokensIn = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getPromptTokens().intValue();
            int tokensOut = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getCompletionTokens().intValue();
            int latency = (int) (System.currentTimeMillis() - t0);

            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), defaultModel,
                    Map.of("sectionOrder", inv.fanoutIndex(),
                            "inputPreview", inv.fanoutPayload().substring(0, Math.min(300, inv.fanoutPayload().length()))),
                    Map.of("mdPreview", md.substring(0, Math.min(300, md.length())), "mdLen", md.length()),
                    tokensIn, tokensOut, latency, "OK", null);

            log.info("[Writer/{}/{}] section markdown {} chars in {} ms",
                    inv.taskId(), inv.fanoutIndex(), md.length(), latency);
            return AgentResult.ok(md, List.of());
        } catch (Exception e) {
            log.error("[Writer/{}/{}] failed", inv.taskId(), inv.fanoutIndex(), e);
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), defaultModel,
                    Map.of("sectionOrder", inv.fanoutIndex()), null,
                    0, 0, (int) (System.currentTimeMillis() - t0), "ERROR", e.getMessage());
            return AgentResult.error(e.getMessage());
        }
    }
}
```

- [ ] **Step 11.2：`WriterConsumer.java`**

```java
package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.core.AgentStatus;
import com.leo.enterpriseinertraining.entity.ReportSection;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.entity.WorkflowSubtask;
import com.leo.enterpriseinertraining.mapper.ReportSectionMapper;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.mapper.WorkflowSubtaskMapper;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.workflow.WorkflowDef;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import com.leo.enterpriseinertraining.workflow.WorkflowNode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.leo.enterpriseinertraining.entity.table.ReportSectionTableDef.REPORT_SECTION;
import static com.leo.enterpriseinertraining.entity.table.WorkflowSubtaskTableDef.WORKFLOW_SUBTASK;

/**
 * 消费 {@link MqTopics#WRITE_SECTION}：读 section + 关联 subtopic materials → 调 WriterAgent →
 * update report_section 的 content_md → 检查所有 section 完成则发 critic.task。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopics.WRITE_SECTION,
        consumerGroup = MqTopics.GROUP_WRITER,
        consumeThreadMax = 8,
        consumeThreadNumber = 2)
public class WriterConsumer implements RocketMQListener<String> {

    private final ObjectMapper om;
    private final ReportSectionMapper sectionMapper;
    private final ReportTaskMapper taskMapper;
    private final WorkflowSubtaskMapper subtaskMapper;
    private final WorkflowLoader workflowLoader;
    private final List<Agent> agents;
    private final SseSinkManager sinkManager;
    private final MqProducerService producer;

    @Override
    public void onMessage(String body) {
        try {
            MqMessage msg = om.readValue(body, MqMessage.class);
            handleWriteSection(msg);
        } catch (Exception e) {
            log.error("[WriterConsumer] failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void handleWriteSection(MqMessage msg) throws Exception {
        long taskId = msg.taskId();
        int sectionOrder = msg.fanoutIndex();

        // 1) 取 section
        List<ReportSection> rows = sectionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(REPORT_SECTION.TASK_ID.eq(taskId))
                        .and(REPORT_SECTION.SECTION_ORDER.eq(sectionOrder)));
        if (rows.isEmpty()) {
            log.warn("[WriterConsumer] section 不存在 taskId={} order={}", taskId, sectionOrder);
            return;
        }
        ReportSection sec = rows.get(0);

        // 2) 取关联 subtopic materials
        List<Integer> relatedIndices = sec.getRelatedSubtopicsJson() == null ? List.of() : sec.getRelatedSubtopicsJson();
        List<Map<String, Object>> materials = new ArrayList<>();
        if (!relatedIndices.isEmpty()) {
            List<WorkflowSubtask> subs = subtaskMapper.selectListByQuery(
                    QueryWrapper.create()
                            .where(WORKFLOW_SUBTASK.TASK_ID.eq(taskId))
                            .and(WORKFLOW_SUBTASK.SUB_INDEX.in(relatedIndices)));
            for (WorkflowSubtask s : subs) {
                Map<String, Object> item = new HashMap<>();
                item.put("subtopic", s.getSubtopic());
                try {
                    item.put("result", om.readTree(s.getResultJson() == null ? "{}" : s.getResultJson()));
                } catch (Exception ignored) {}
                materials.add(item);
            }
        }

        // 3) 构造 user input
        Map<String, Object> userInput = new HashMap<>();
        userInput.put("title", sec.getTitle());
        userInput.put("outline", sec.getOutline());
        userInput.put("materials", materials);
        String userJson = om.writeValueAsString(userInput);

        // 4) 调 Writer
        ReportTask task = taskMapper.selectOneById(taskId);
        WorkflowDef def = workflowLoader.load(task.getWorkflowName());
        WorkflowNode writeNode = def.getNodes().stream()
                .filter(n -> "write".equals(n.getId())).findFirst().orElseThrow();
        Agent writer = agentByRole().get(writeNode.getAgent());

        var sink = sinkManager.get(taskId);
        if (sink != null) sink.nodeStatus("write#" + sectionOrder, "RUNNING");

        AgentInvocation inv = new AgentInvocation(
                taskId, "write", task.getTopic(),
                List.of(), writeNode.getPrompt(), sectionOrder, userJson);
        AgentResult result = writer.execute(inv, sink);

        if (result.status() == AgentStatus.ERROR) {
            sec.setStatus("DRAFT");      // 保持 DRAFT；失败靠 MQ 重试
            sectionMapper.update(sec);
            if (sink != null) sink.nodeStatus("write#" + sectionOrder, "FAILED");
            throw new RuntimeException("Writer failed: " + result.errorMessage());
        }

        sec.setContentMd(result.markdown());
        sec.setStatus("FINAL");
        sectionMapper.update(sec);

        if (sink != null) {
            sink.nodeStatus("write#" + sectionOrder, "DONE");
            sink.sectionDone(sectionOrder, sec.getTitle(),
                    result.markdown().substring(0, Math.min(200, result.markdown().length())));
        }

        // 5) 检查是否所有 section 都 FINAL
        long pending = sectionMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(REPORT_SECTION.TASK_ID.eq(taskId))
                        .and(REPORT_SECTION.STATUS.ne("FINAL")));
        log.info("[Writer/{}] section {} done; pending={}", taskId, sectionOrder, pending);

        if (pending == 0) {
            // 全部完成 → phase=CRITICIZING + 发 critic.task
            task.setPhase("CRITICIZING");
            task.setProgress(90);
            taskMapper.update(task);
            if (sink != null) sink.phaseChanged("CRITICIZING", 90);
            producer.send(MqTopics.CRITIC_TASK, MqMessage.of(taskId, "critic"));
        }
    }

    private Map<String, Agent> agentByRole;
    private Map<String, Agent> agentByRole() {
        if (agentByRole == null) {
            agentByRole = new HashMap<>();
            for (Agent a : agents) agentByRole.put(a.role(), a);
        }
        return agentByRole;
    }
}
```

- [ ] **Step 11.3：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/agent/role/WriterAgent.java \
        src/main/java/com/leo/enterpriseinertraining/mq/WriterConsumer.java
git commit -m "feat(phase3): WriterAgent + WriterConsumer (write section markdown, dispatch critic.task on all-final)"
```

---

## Task 12：CriticAgent + CriticConsumer + 回环逻辑

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/agent/role/CriticAgent.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/mq/CriticConsumer.java`

- [ ] **Step 12.1：`CriticAgent.java`**

```java
package com.leo.enterpriseinertraining.agent.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.prompt.PromptLoader;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.trace.WorkflowNodeRunRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Critic Agent：审查整份研报，输出 {needsRevision, issues}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CriticAgent implements Agent {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptLoader promptLoader;
    private final WorkflowNodeRunRecorder recorder;
    private final ObjectMapper om;

    @Value("${app.dashscope.chat-model-strong:qwen-max}")
    private String strongModel;

    @Override
    public String role() { return "Critic"; }

    @Override
    public AgentResult execute(AgentInvocation inv, SseSink sink) {
        long t0 = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.load(inv.promptRef());
            ChatClient client = chatClientBuilder.build();

            var response = client.prompt()
                    .system(systemPrompt)
                    .user("以下是完整研报 markdown：\n\n" + inv.fanoutPayload())
                    .call()
                    .chatResponse();

            String raw = response.getResult().getOutput().getText();
            int tokensIn = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getPromptTokens().intValue();
            int tokensOut = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getCompletionTokens().intValue();
            int latency = (int) (System.currentTimeMillis() - t0);

            int l = raw.indexOf('{'), r = raw.lastIndexOf('}');
            String json = (l >= 0 && r > l) ? raw.substring(l, r + 1) : raw;
            // 验证可解析（不强制 needsRevision 字段；上游容错）
            om.readTree(json);

            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), strongModel,
                    Map.of("markdownLen", inv.fanoutPayload().length()),
                    Map.of("raw", raw),
                    tokensIn, tokensOut, latency, "OK", null);

            log.info("[Critic/{}] reviewed in {} ms", inv.taskId(), latency);
            return AgentResult.ok(json, List.of());
        } catch (Exception e) {
            log.error("[Critic/{}] failed", inv.taskId(), e);
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), strongModel,
                    Map.of(), null,
                    0, 0, (int) (System.currentTimeMillis() - t0), "ERROR", e.getMessage());
            return AgentResult.error(e.getMessage());
        }
    }
}
```

- [ ] **Step 12.2：`CriticConsumer.java`**

```java
package com.leo.enterpriseinertraining.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.core.AgentStatus;
import com.leo.enterpriseinertraining.entity.ReportSection;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.entity.WorkflowLoopState;
import com.leo.enterpriseinertraining.mapper.ReportSectionMapper;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.mapper.WorkflowLoopStateMapper;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.workflow.WorkflowDef;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import com.leo.enterpriseinertraining.workflow.WorkflowNode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.leo.enterpriseinertraining.entity.table.ReportSectionTableDef.REPORT_SECTION;
import static com.leo.enterpriseinertraining.entity.table.WorkflowLoopStateTableDef.WORKFLOW_LOOP_STATE;

/**
 * 消费 {@link MqTopics#CRITIC_TASK}：读所有 section → 拼成 markdown → 调 Critic →
 * 如 needsRevision 且 loopCount < maxLoops，重发 write.section；否则拼接 final_markdown 并 DONE。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopics.CRITIC_TASK, consumerGroup = MqTopics.GROUP_CRITIC)
public class CriticConsumer implements RocketMQListener<String> {

    private final ObjectMapper om;
    private final ReportSectionMapper sectionMapper;
    private final ReportTaskMapper taskMapper;
    private final WorkflowLoopStateMapper loopMapper;
    private final WorkflowLoader workflowLoader;
    private final List<Agent> agents;
    private final SseSinkManager sinkManager;
    private final MqProducerService producer;

    @Override
    public void onMessage(String body) {
        try {
            MqMessage msg = om.readValue(body, MqMessage.class);
            handleCritic(msg.taskId());
        } catch (Exception e) {
            log.error("[CriticConsumer] failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void handleCritic(long taskId) throws Exception {
        ReportTask task = taskMapper.selectOneById(taskId);
        if (task == null) return;

        var sink = sinkManager.get(taskId);
        if (sink != null) sink.nodeStatus("critic", "RUNNING");

        // 1) 拼接 markdown
        List<ReportSection> secs = sectionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(REPORT_SECTION.TASK_ID.eq(taskId))
                        .orderBy(REPORT_SECTION.SECTION_ORDER, true));
        StringBuilder md = new StringBuilder();
        for (ReportSection s : secs) {
            md.append(s.getContentMd() == null ? "" : s.getContentMd()).append("\n\n");
        }
        String fullMd = md.toString();

        // 2) 调 Critic
        WorkflowDef def = workflowLoader.load(task.getWorkflowName());
        WorkflowNode criticNode = def.getNodes().stream()
                .filter(n -> "critic".equals(n.getId())).findFirst().orElseThrow();
        Agent critic = agentByRole().get(criticNode.getAgent());
        AgentInvocation inv = new AgentInvocation(
                taskId, "critic", task.getTopic(),
                List.of(), criticNode.getPrompt(), null, fullMd);
        AgentResult result = critic.execute(inv, sink);

        if (result.status() == AgentStatus.ERROR) {
            // Critic 失败 → 跳过审查直接 DONE
            finalize(task, fullMd, secs, sink, "critic_failed_force_accept");
            return;
        }

        JsonNode node = om.readTree(result.markdown());
        boolean needsRevision = node.has("needsRevision") && node.get("needsRevision").asBoolean();
        int maxLoops = criticNode.getMaxLoops() == null ? 1 : criticNode.getMaxLoops();
        int loopCount = currentLoopCount(taskId, "critic");

        if (!needsRevision || loopCount >= maxLoops) {
            // 接受 → 拼接 final
            String reason = !needsRevision ? "no_revision_needed" : "max_loops_reached";
            finalize(task, fullMd, secs, sink, reason);
            return;
        }

        // 3) 触发修订：loopCount++ + 找出有问题的 sectionOrder，重发 write.section
        incrementLoopCount(taskId, "critic", maxLoops);

        java.util.Set<Integer> issueSectionOrders = new java.util.HashSet<>();
        JsonNode issues = node.get("issues");
        if (issues != null && issues.isArray()) {
            issues.forEach(it -> {
                if (it.has("sectionOrder")) issueSectionOrders.add(it.get("sectionOrder").asInt());
            });
        }
        if (issueSectionOrders.isEmpty()) {
            // needsRevision=true 但没列 sectionOrder → 当作接受
            finalize(task, fullMd, secs, sink, "no_section_specified");
            return;
        }

        // 重发对应 section 给 Writer，并把 section 标记为 REVISING + revisionCount++
        for (Integer order : issueSectionOrders) {
            for (ReportSection s : secs) {
                if (s.getSectionOrder().equals(order)) {
                    s.setStatus("REVISING");
                    s.setRevisionCount(s.getRevisionCount() == null ? 1 : s.getRevisionCount() + 1);
                    sectionMapper.update(s);
                    break;
                }
            }
            producer.send(MqTopics.WRITE_SECTION, MqMessage.fanout(taskId, "write", order));
        }
        task.setPhase("WRITING");
        task.setProgress(75);
        taskMapper.update(task);
        if (sink != null) {
            sink.nodeStatus("critic", "DONE");
            sink.phaseChanged("WRITING", 75);
        }
        log.info("[Critic/{}] revision triggered for sections {}", taskId, issueSectionOrders);
    }

    private void finalize(ReportTask task, String fullMd, List<ReportSection> secs,
                          SseSink sink, String reason) {
        task.setFinalMarkdown(fullMd);
        task.setStatus("DONE");
        task.setPhase("DONE");
        task.setProgress(100);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.update(task);

        // 聚合 citations
        List<ReportTask.CitationData> allCitations = new java.util.ArrayList<>();
        for (ReportSection s : secs) {
            if (s.getCitationsJson() != null) allCitations.addAll(s.getCitationsJson());
        }

        if (sink != null) {
            sink.nodeStatus("critic", "DONE");
            sink.phaseChanged("DONE", 100);
            sink.done(fullMd, allCitations);
        }
        sinkManager.remove(task.getId());
        log.info("[Critic/{}] task DONE (reason={})", task.getId(), reason);
    }

    private int currentLoopCount(long taskId, String nodeId) {
        List<WorkflowLoopState> rows = loopMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_LOOP_STATE.TASK_ID.eq(taskId))
                        .and(WORKFLOW_LOOP_STATE.NODE_ID.eq(nodeId)));
        return rows.isEmpty() ? 0 : rows.get(0).getLoopCount();
    }

    private void incrementLoopCount(long taskId, String nodeId, int maxLoops) {
        List<WorkflowLoopState> rows = loopMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_LOOP_STATE.TASK_ID.eq(taskId))
                        .and(WORKFLOW_LOOP_STATE.NODE_ID.eq(nodeId)));
        if (rows.isEmpty()) {
            WorkflowLoopState s = new WorkflowLoopState();
            s.setTaskId(taskId); s.setNodeId(nodeId); s.setLoopCount(1); s.setMaxLoops(maxLoops);
            loopMapper.insert(s);
        } else {
            WorkflowLoopState s = rows.get(0);
            s.setLoopCount(s.getLoopCount() + 1);
            loopMapper.update(s);
        }
    }

    private Map<String, Agent> agentByRole;
    private Map<String, Agent> agentByRole() {
        if (agentByRole == null) {
            agentByRole = new HashMap<>();
            for (Agent a : agents) agentByRole.put(a.role(), a);
        }
        return agentByRole;
    }
}
```

- [ ] **Step 12.3：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/agent/role/CriticAgent.java \
        src/main/java/com/leo/enterpriseinertraining/mq/CriticConsumer.java
git commit -m "feat(phase3): CriticAgent + CriticConsumer (needsRevision loop max=1, finalize on accept)"
```

---

## Task 13：SseSink 加 phaseChanged + sectionDone

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/stream/SseSink.java`

- [ ] **Step 13.1：在 `SseSink.java` 中追加 2 个事件方法**

读现有 `SseSink.java`，在 `ping()` 方法**之前**追加：

```java
    public synchronized void phaseChanged(String phase, int progress) {
        send("phase_changed", Map.of("phase", phase, "progress", progress));
    }

    public synchronized void sectionDone(int order, String title, String preview) {
        send("section_done", Map.of(
                "order", order,
                "title", title == null ? "" : title,
                "preview", preview == null ? "" : preview));
    }
```

- [ ] **Step 13.2：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/stream/SseSink.java
git commit -m "feat(stream): SseSink +phaseChanged +sectionDone events"
```

---

## Task 14：WorkflowEngine v2 重构 + ReportServiceImpl 改造发 MQ

**Files:**
- Modify: `src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowEngine.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/service/impl/ReportServiceImpl.java`

- [ ] **Step 14.1：重构 `WorkflowEngine.java`**

阶段 2 的 `WorkflowEngine` 类整体替换为：

```java
package com.leo.enterpriseinertraining.workflow;

import com.leo.enterpriseinertraining.mq.MqMessage;
import com.leo.enterpriseinertraining.mq.MqProducerService;
import com.leo.enterpriseinertraining.mq.MqTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Workflow Engine v2（阶段 3）：事件驱动 dispatcher。
 *
 * <p>阶段 2 的同步 execute() 方法已删除。新模型：</p>
 * <ol>
 *   <li>{@link #start(long, String)} 仅发 task.created MQ 消息</li>
 *   <li>后续节点由各自 MQ Consumer 触发，节点间编排已在每个 Consumer 内显式硬编码
 *       （PlannerOrchestrator → ResearcherWorker → AnalystConsumer → WriterConsumer → CriticConsumer）</li>
 * </ol>
 *
 * <p>本类目前仅做"启动入口"。未来如需通用 dispatchNext，可以扩展为读 YAML.next 决定 topic。
 * 阶段 3 保持简单：每个 Consumer 自己知道下一步去哪。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private final MqProducerService producer;
    private final WorkflowLoader workflowLoader;

    /** 启动一个 task：发 task.created MQ，立即返回。 */
    public void start(long taskId, String workflowName) {
        // 校验 workflow 存在（提前失败比 MQ Consumer 失败更好）
        WorkflowDef def = workflowLoader.load(workflowName);
        if (def.getNodes() == null || def.getNodes().isEmpty()) {
            throw new RuntimeException("workflow " + workflowName + " 无节点");
        }
        producer.send(MqTopics.TASK_CREATED, MqMessage.of(taskId, "task.created"));
        log.info("[WorkflowEngine] task {} started with workflow {}", taskId, workflowName);
    }
}
```

> 阶段 2 的 `WorkflowExecutionResult` record 也整体删除（不再有同步调用方）。各 Consumer 直接处理。

- [ ] **Step 14.2：改造 `ReportServiceImpl.java`**

替换整个文件为：

```java
package com.leo.enterpriseinertraining.service.impl;

import com.leo.enterpriseinertraining.dto.ReportStartRequest;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.exception.BusinessException;
import com.leo.enterpriseinertraining.exception.ErrorCode;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.service.ReportService;
import com.leo.enterpriseinertraining.vo.ReportStartVO;
import com.leo.enterpriseinertraining.workflow.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportTaskMapper taskMapper;
    private final WorkflowEngine engine;

    @Override
    public ReportStartVO start(long userId, ReportStartRequest req) {
        ReportTask task = new ReportTask();
        task.setUserId(userId);
        task.setTopic(req.getTopic());
        task.setWorkflowName(req.getWorkflow());
        task.setStatus("PENDING");
        task.setPhase("PLANNING");
        task.setProgress(0);
        taskMapper.insert(task);

        Long taskId = task.getId();
        log.info("[Report] task {} submitted: topic='{}' workflow={}", taskId, req.getTopic(), req.getWorkflow());

        engine.start(taskId, req.getWorkflow());

        return new ReportStartVO(taskId, "PENDING", "/api/report/" + taskId + "/stream");
    }

    @Override
    public ReportTask findById(long taskId) {
        ReportTask t = taskMapper.selectOneById(taskId);
        if (t == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        return t;
    }
}
```

> 删除阶段 2 的 `executor` 字段、`@PostConstruct/@PreDestroy`、`runWorkflow` 方法、`toCitationData` 辅助方法。整体大瘦身。

- [ ] **Step 14.3：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowEngine.java \
        src/main/java/com/leo/enterpriseinertraining/service/impl/ReportServiceImpl.java
git commit -m "refactor(phase3): WorkflowEngine v2 (event-driven) + ReportServiceImpl uses MQ dispatch"
```

---

## Task 15：ReportController 加 /{id}/sections + AdminWorkflowController + VO 升级

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/vo/SectionVO.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/vo/ReportResultVO.java`
- Modify: `src/main/java/com/leo/enterpriseinertraining/controller/ReportController.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/controller/AdminWorkflowController.java`

- [ ] **Step 15.1：`SectionVO.java`**

```java
package com.leo.enterpriseinertraining.vo;

import com.leo.enterpriseinertraining.entity.ReportTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class SectionVO implements Serializable {
    private Integer order;
    private String title;
    private String outline;
    private String contentMd;
    private List<ReportTask.CitationData> citations;
    private String status;          // DRAFT/FINAL/REVISING
    private Integer revisionCount;
}
```

- [ ] **Step 15.2：扩展 `ReportResultVO.java` 加 phase / progress**

读现有 `ReportResultVO.java`，把字段定义改成（保持顺序方便 controller 构造）：

```java
package com.leo.enterpriseinertraining.vo;

import com.leo.enterpriseinertraining.entity.ReportTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class ReportResultVO implements Serializable {
    private Long taskId;
    private String status;
    private String phase;            // 阶段 3 新增
    private Integer progress;        // 阶段 3 新增
    private String topic;
    private String finalMarkdown;
    private List<ReportTask.CitationData> citations;
    private String errorMessage;
    private Long startedAtEpochMillis;
    private Long finishedAtEpochMillis;
}
```

- [ ] **Step 15.3：修改 `ReportController.java`**

整个文件替换为：

```java
package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.dto.ReportStartRequest;
import com.leo.enterpriseinertraining.entity.ReportSection;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.entity.WorkflowNodeRun;
import com.leo.enterpriseinertraining.mapper.ReportSectionMapper;
import com.leo.enterpriseinertraining.security.SecurityUtils;
import com.leo.enterpriseinertraining.service.ReportService;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.trace.TraceQueryService;
import com.leo.enterpriseinertraining.vo.ReportResultVO;
import com.leo.enterpriseinertraining.vo.ReportStartVO;
import com.leo.enterpriseinertraining.vo.SectionVO;
import com.leo.enterpriseinertraining.vo.TraceStepVO;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.ZoneId;
import java.util.List;

import static com.leo.enterpriseinertraining.entity.table.ReportSectionTableDef.REPORT_SECTION;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
@Tag(name = "研究报告", description = "提交主题 → 5 Agent 协作 → SSE 流式返回")
public class ReportController {

    private final ReportService reportService;
    private final TraceQueryService traceQueryService;
    private final SseSinkManager sinkManager;
    private final ReportSectionMapper sectionMapper;

    @PostMapping("/start")
    @Operation(summary = "提交研究主题，返回 taskId + streamUrl")
    public BaseResponse<ReportStartVO> start(@RequestBody @Valid ReportStartRequest req) {
        long userId = SecurityUtils.currentUserId();
        return ResultUtils.success(reportService.start(userId, req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单任务最终结果")
    public BaseResponse<ReportResultVO> get(@PathVariable long id) {
        ReportTask t = reportService.findById(id);
        ReportResultVO vo = new ReportResultVO(
                t.getId(), t.getStatus(), t.getPhase(), t.getProgress(),
                t.getTopic(),
                t.getFinalMarkdown(), t.getCitationsJson(), t.getErrorMessage(),
                t.getStartedAt() == null ? null : t.getStartedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                t.getFinishedAt() == null ? null : t.getFinishedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return ResultUtils.success(vo);
    }

    @GetMapping(value = "/{id}/stream", produces = "text/event-stream")
    @Operation(summary = "SSE 实时流（node_status / tool / token / phase_changed / section_done / done / error）")
    public SseEmitter stream(@PathVariable long id) {
        reportService.findById(id);
        return sinkManager.register(id);
    }

    @GetMapping("/{id}/trace")
    @Operation(summary = "查 Trace 时间轴")
    public BaseResponse<List<TraceStepVO>> trace(@PathVariable long id) {
        List<WorkflowNodeRun> rows = traceQueryService.findByTaskId(id);
        List<TraceStepVO> vos = rows.stream().map(r -> new TraceStepVO(
                r.getId(), r.getStepSeq(), r.getNodeId(), r.getAgentRole(), r.getStepType(),
                r.getPromptVersion(), r.getModel(), r.getToolName(),
                r.getTokensIn(), r.getTokensOut(), r.getLatencyMs(),
                r.getStatus(), r.getErrorMessage(),
                preview(r.getInputJson()), preview(r.getOutputJson())
        )).toList();
        return ResultUtils.success(vos);
    }

    @GetMapping("/{id}/sections")
    @Operation(summary = "查所有章节（含修订状态）")
    public BaseResponse<List<SectionVO>> sections(@PathVariable long id) {
        reportService.findById(id);
        List<ReportSection> rows = sectionMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(REPORT_SECTION.TASK_ID.eq(id))
                        .orderBy(REPORT_SECTION.SECTION_ORDER, true));
        List<SectionVO> vos = rows.stream().map(s -> new SectionVO(
                s.getSectionOrder(), s.getTitle(), s.getOutline(),
                s.getContentMd(), s.getCitationsJson(),
                s.getStatus(), s.getRevisionCount()
        )).toList();
        return ResultUtils.success(vos);
    }

    private String preview(String s) {
        if (s == null) return null;
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
```

- [ ] **Step 15.4：`AdminWorkflowController.java`**

```java
package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
```

- [ ] **Step 15.5：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/vo/SectionVO.java \
        src/main/java/com/leo/enterpriseinertraining/vo/ReportResultVO.java \
        src/main/java/com/leo/enterpriseinertraining/controller/ReportController.java \
        src/main/java/com/leo/enterpriseinertraining/controller/AdminWorkflowController.java
git commit -m "feat(phase3): /{id}/sections + /admin/workflow/reload + ReportResultVO +phase/progress"
```

---

## Task 16：阶段 3 收尾 commit（empty marker）+ 用户验证清单写入 README 风格文件

**Files:**
- Create: `docs/superpowers/handoff/2026-05-20-phase-3-verify.md`

- [ ] **Step 16.1：写用户验证手册**

```markdown
# 阶段 3 用户验证清单（IDE 端 + Postman）

## A. 准备
1. 执行 DDL：
   ```bash
   docker exec -i irp-mysql mysql -uirp -pirppw irp < src/main/resources/db/schema-phase3.sql
   docker exec irp-mysql mysql -uirp -pirppw irp -e "SHOW TABLES;"
   # 期望多出：workflow_subtask / report_section / workflow_loop_state
   ```
2. application.yml 加（若没有）：
   ```yaml
   app:
     dashscope:
       chat-model: qwen-plus
       chat-model-strong: qwen-max
   ```
3. **重启 IDE 应用**，启动日志应包含：
   ```
   [ToolRegistry] registered N tools
   The consumer of group [irp-task-orchestrator] ... started
   The consumer of group [irp-researcher] ... started
   The consumer of group [irp-analyst] ... started
   The consumer of group [irp-writer] ... started
   The consumer of group [irp-critic] ... started
   ```

## B. Postman 端到端跑一次
1. 登录拿 token（沿用阶段 2）
2. `POST /api/report/start` body：
   ```json
   {"topic":"2026 中国动力电池行业趋势与核心玩家","workflow":"multi_agent_v1"}
   ```
   预期立即返回 taskId
3. 轮询 `GET /{id}` 看 phase 字段变化：
   `PLANNING → RESEARCHING → ANALYZING → WRITING → CRITICIZING → DONE`
4. 终态 `GET /{id}`：`status=DONE`，`finalMarkdown` 含 4-6 个 `## 标题`
5. `GET /{id}/sections` 返回 4-6 个章节
6. `GET /{id}/trace` 返回 ≥ 12 条 step（plan LLM + N×research LLM + N×research TOOL + analyze LLM + M×write LLM + critic LLM）

## C. 出口指标
- [ ] 单研报 P95 ≤ 25s（多跑 5 次取 P95）
- [ ] DB 行数：`report_section` = 4-6；`workflow_subtask` = 8-12（status=DONE）；`workflow_node_run` ≥ 12
- [ ] 同一 taskId 多次 SSE 看 phase_changed / section_done 事件正常

## D. 如果出问题，按这个顺序排查
1. 启动日志有没有 5 个 consumer group 注册成功
2. Planner 跑完后 `workflow_subtask` 表有没有 N 行 PENDING
3. Researcher 处理完后 `workflow_subtask` 行 status 是否变为 DONE
4. Analyst 跑完后 `report_section` 表有没有 N 行 DRAFT
5. Writer 处理完后对应 section status=FINAL
6. Critic 跑完 task.status=DONE / phase=DONE

## E. Critic 回环手工测试（可选）
临时改 `prompts/critic_prompt_v1.txt` 末尾加一行强制 `needsRevision=true`，重启 + reload workflow，跑一次任务，应能看到：
- 第一轮 Critic → needsRevision=true → loopCount=1
- 重发 write.section（看 SSE）
- 第二轮 Critic → maxLoops=1 强制接受 → DONE
- `workflow_loop_state` 表有 1 行 loopCount=1
- 相关 `report_section.revisionCount` = 1
```

- [ ] **Step 16.2：提交**

```bash
git add docs/superpowers/handoff/2026-05-20-phase-3-verify.md
git commit -m "docs(phase3): user verify checklist + Critic loop manual test"
```

---

## 阶段 3 出口条件（DoD）

由**用户在 IDE 端**验证（见 Task 16 手册）：

### 功能层
- [ ] schema-phase3.sql 执行后 3 新表存在 + report_task 加 phase/progress
- [ ] 启动日志含 5 个 RocketMQ consumer group 注册
- [ ] `POST /start { workflow: multi_agent_v1 }` 立即返回 taskId
- [ ] 通过 SSE 看到事件序列（phase_changed 切换 6 次：PLANNING/RESEARCHING/ANALYZING/WRITING/CRITICIZING/DONE）
- [ ] `GET /{id}` 不同时刻返回不同 phase + progress
- [ ] `GET /{id}/sections` 返回 4-6 个 section
- [ ] `GET /{id}/trace` 返回 ≥ 12 step
- [ ] `final_markdown` 含 4-6 个二级标题

### 性能层
- [ ] 单研报 P95 ≤ 25s（Researcher fanout 并发收益）

### 工程层
- [ ] Researcher 子任务失败 3 次后 RocketMQ 进 DLQ；该子任务标 FAILED 但其它子任务继续
- [ ] Critic 回环（手工注入 needsRevision=true）：loop_count=1，重发 write，再次到 Critic 强制接受
- [ ] WorkflowLoader.reload() 后修改 YAML 立即生效

### 测试层
- [ ] `WorkflowLoaderTest` 含 multi_agent_v1 解析（4 测试通过）
- [ ] `FanoutHelperTest` 5 测试通过
- [ ] `JoinTrackerTest` 3 测试通过（Mockito）
- [ ] `MqProducerServiceTest` 3 测试通过

---

## Self-Review

**1. Spec 覆盖：**

- spec §1 决策汇总 → T1-T16 全部对应 ✓
- spec §2 端到端流程 → 全程在 T8-T12 五个 Consumer + Agent 中实现 ✓
- spec §3 数据模型（3 新表 + report_task 加字段）→ T1 ✓
- spec §4 WorkflowEngine v2 → T14 重构 ✓
- spec §5 RocketMQ topic / consumer group 设计 → T2 常量 + T8-T12 @RocketMQMessageListener 注解 ✓
- spec §6 5 Agent 详细职责 → T8 Planner / T9 Researcher / T10 Analyst / T11 Writer / T12 Critic ✓
- spec §7 SSE 协议扩展（phase_changed + section_done）→ T13 + 各 Consumer 显式 call ✓
- spec §8 关键接口（4 个保留 + /sections + /admin/reload）→ T15 ✓
- spec §9 文件结构 → 与各 Task 文件清单完全对齐 ✓
- spec §10 阶段化交付（14-16 task）→ 本 plan 16 task ✓
- spec §11 验证清单 → T16 用户手册 ✓
- spec §12 风险与延后 → 在 Task 内通过 try/catch + DLQ + max_loops=1 兜底 ✓

**2. Placeholder 扫描：** 无 TBD / TODO / incomplete。所有 Step 含完整代码或具体命令。

**3. 类型 / 方法签名一致性：**

- `MqMessage(taskId, nodeId, fanoutIndex, payload)` 在 T2 定义 + T3/T8/T9/T10/T11/T12 使用一致 ✓
- `MqTopics.*` 常量在 T2 定义 + T8/T9/T10/T11/T12 引用一致 ✓
- `AgentInvocation` 7 字段（+fanoutIndex/+fanoutPayload）在 T6 定义 + T8/T9/T10/T11/T12 Consumer 构造一致；阶段 2 `AgentInvocation.of(...)` 兼容方法在 T8 Orchestrator 用 ✓
- `JoinTracker.markCompletedAndCheckLast/markFailed` 在 T7 定义 + T9 ResearcherWorker 使用一致 ✓
- `FanoutHelper.resolveAsStringList/resolveAsJsonStringList` 在 T6 定义；阶段 3 plan 中实际只用到 `resolveAsStringList`（plan.subtopics）和 `resolveAsJsonStringList`（analyze.sections）—— **但 Consumer 路径里没显式调 FanoutHelper**，因为消息体只携带 fanoutIndex，Consumer 直接从 DB 读对应行。FanoutHelper 留给 T14 WorkflowEngine v2 未来通用扩展用，本阶段是"未来扩展点" — 建议在 T14 spec 中点出 / 或在 plan §决策 1 末尾说明 ✓（已在文件顶部"决策"段说明）
- `SseSink.phaseChanged/sectionDone` 在 T13 定义 + T8/T9/T10/T11/T12 Consumer 使用一致 ✓
- `WorkflowEngine.start(taskId, workflowName)` 在 T14 定义 + T14 ReportServiceImpl 使用一致 ✓
- `ReportResultVO` 字段顺序（taskId, status, phase, progress, topic, finalMarkdown, citations, errorMessage, startedAt, finishedAt）在 T15 定义 + T15 Controller 构造一致 ✓
- MyBatis-Flex 生成的 `WorkflowSubtaskTableDef.WORKFLOW_SUBTASK` / `ReportSectionTableDef.REPORT_SECTION` / `WorkflowLoopStateTableDef.WORKFLOW_LOOP_STATE` 静态字段在 T7/T9/T10/T11/T12 使用，由 T1 entity 注解触发 APT 生成 ✓
- `ReportSection.relatedSubtopicsJson` 类型 `List<Integer>`（T1）↔ T10 写入 `List<Integer>` ↔ T11 读取 `List<Integer>` 一致 ✓
- `ReportTask.CitationData` 字段（docId/docTitle/source/sectionTitle/pageStart/pageEnd）在阶段 2 已定义；本阶段不重新声明 ✓

无遗漏。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-20-phase-3-multi-agent-rocketmq.md`.

两种执行方式：

**1. Subagent-Driven（推荐，沿用阶段 0/1/2 模式）** —— 每个 Task 派 fresh subagent + 极简 review。共 16 Task。

**2. Inline Execution** —— 当前会话连续跑。token 压力较大。

请选择 1 或 2。
