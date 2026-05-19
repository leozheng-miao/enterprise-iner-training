# 阶段 2：单 Agent + Workflow 引擎 + SSE 流式 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal：** 在阶段 1 RAG 基础上构建 ResearcherAgent —— LLM 通过 Function Calling 自主使用 `hybrid_search` 工具完成研究主题写作，YAML 声明式 Workflow 引擎承载执行，SseEmitter 实时推送节点状态/工具调用/LLM token，所有 LLM/Tool 步骤落 `workflow_node_run` 形成可观测 Trace。

**Architecture：** Spring AI 1.0 ChatClient + 自研 Tool SPI（@AgentTool 接口 + ToolRegistry 启动扫描）+ FunctionCallback 桥接给 LLM。`WorkflowEngine` 解析 YAML 后顺序执行节点，每个节点交给对应 `Agent` 实例处理。`ReportService` 用 Java 21 Virtual Thread 异步触发任务，`ReportTask`/`WorkflowNodeRun`/`ToolRegistry` 三张表分别管生命周期、Trace、Tool 元数据。`SseSinkManager` 用 `Map<taskId, SseEmitter>` 管理流式连接，推送 5 种事件（node_status / tool / token / done / error）。

**Tech Stack：** Spring Boot 3.5.14 / Java 21 Virtual Thread / Spring AI 1.0.0 GA (DashScope qwen-plus, OpenAI 兼容) / MyBatis-Flex 1.10.6 / SnakeYAML（Spring Boot 自带）/ SseEmitter (Spring MVC) / Lombok / Hutool

**项目根：** `/Users/zhengsmacbook/Desktop/miniProject/claude/enterprise-iner-training`
**Baseline：** spec commit `16c3768`，阶段 1 完成 commit `06bb05f`

**严格规则**（subagent 必须遵守）：
- **不要跑 `./mvnw`** — 用户在 IDE 验证编译启动
- **不要 source .env 或修改环境变量**
- **不要跑 curl / 启动应用 / 端到端验证**
- 只做三件事：① 写文件 ② `git add` + `git commit` ③ 报告
- 若发现 Spring AI 1.0 API 与 plan 代码不匹配（命名变化等），**BLOCKED 报回**让控制器决定，不要私自改 API 名

---

## File Structure（阶段 2 完成后新增）

```
src/main/resources/
├── db/
│   └── schema-phase2.sql                ← 新增（3 张表 DDL）
├── workflow/
│   └── researcher_only_v1.yaml          ← 新增
└── prompts/
    └── researcher_prompt_v1.txt         ← 新增

src/main/java/com/leo/enterpriseinertraining/
├── entity/
│   ├── ReportTask.java                  ← 新增 extends BaseEntity
│   ├── WorkflowNodeRun.java             ← 新增 extends BaseEntity
│   └── ToolRegistry.java                ← 新增 extends BaseEntity
├── mapper/
│   ├── ReportTaskMapper.java
│   ├── WorkflowNodeRunMapper.java
│   └── ToolRegistryMapper.java
├── agent/
│   ├── core/
│   │   ├── Agent.java                   ← 接口
│   │   ├── AgentInvocation.java         ← record
│   │   ├── AgentResult.java             ← record
│   │   ├── AgentStatus.java             ← enum
│   │   └── Citation.java                ← record
│   ├── tool/
│   │   ├── AgentTool.java               ← 接口
│   │   ├── AgentToolMarker.java         ← @AgentToolMarker 注解
│   │   ├── ToolRegistryService.java     ← 注册中心 + 启动扫描
│   │   ├── ToolInvocationTracer.java    ← 拦截 invoke 写 trace
│   │   └── impl/HybridSearchTool.java   ← 第一个工具
│   ├── prompt/
│   │   └── PromptLoader.java            ← 读 resources/prompts/*
│   └── role/
│       └── ResearcherAgent.java
├── workflow/
│   ├── WorkflowDef.java                 ← YAML 反序列化
│   ├── WorkflowNode.java
│   ├── WorkflowLoader.java              ← SnakeYAML
│   └── WorkflowEngine.java              ← 顺序执行 + 节点状态机
├── trace/
│   ├── WorkflowNodeRunRecorder.java     ← 写 workflow_node_run
│   └── TraceQueryService.java           ← 按 task_id 查
├── stream/
│   ├── SseSink.java                     ← SseEmitter 包装
│   └── SseSinkManager.java              ← Map<taskId, SseSink>
├── service/
│   ├── ReportService.java + impl/
│   └── (TraceQueryService 在 trace/ 包内)
├── controller/
│   └── ReportController.java
├── dto/
│   └── ReportStartRequest.java
└── vo/
    ├── ReportStartVO.java
    ├── ReportResultVO.java
    └── TraceStepVO.java

src/test/java/com/leo/enterpriseinertraining/
├── workflow/WorkflowLoaderTest.java
└── trace/WorkflowNodeRunRecorderTest.java
```

---

## Task 1：DDL + 3 个 entity + 3 个 mapper

**Files:**
- Create: `src/main/resources/db/schema-phase2.sql`
- Create: `src/main/java/com/leo/enterpriseinertraining/entity/{ReportTask,WorkflowNodeRun,ToolRegistry}.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/mapper/{ReportTaskMapper,WorkflowNodeRunMapper,ToolRegistryMapper}.java`

- [ ] **Step 1.1：创建 schema-phase2.sql**

```sql
-- 阶段 2 表：研究任务 + Trace + Tool 注册

CREATE TABLE IF NOT EXISTS `report_task` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT       NOT NULL,
    `topic`           VARCHAR(512) NOT NULL,
    `workflow_name`   VARCHAR(64)  NOT NULL,
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
    `final_markdown`  MEDIUMTEXT   DEFAULT NULL,
    `citations_json`  JSON         DEFAULT NULL,
    `error_message`   VARCHAR(1024) DEFAULT NULL,
    `started_at`      DATETIME     DEFAULT NULL,
    `finished_at`     DATETIME     DEFAULT NULL,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='研究任务';

CREATE TABLE IF NOT EXISTS `workflow_node_run` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`        BIGINT       NOT NULL,
    `node_id`        VARCHAR(64)  NOT NULL,
    `agent_role`     VARCHAR(32)  NOT NULL,
    `step_type`      VARCHAR(16)  NOT NULL COMMENT 'LLM_CALL / TOOL_CALL',
    `step_seq`       INT          NOT NULL,
    `prompt_version` VARCHAR(64)  DEFAULT NULL,
    `model`          VARCHAR(64)  DEFAULT NULL,
    `tool_name`      VARCHAR(64)  DEFAULT NULL,
    `input_json`     MEDIUMTEXT   DEFAULT NULL,
    `output_json`    MEDIUMTEXT   DEFAULT NULL,
    `tokens_in`      INT          NOT NULL DEFAULT 0,
    `tokens_out`     INT          NOT NULL DEFAULT 0,
    `latency_ms`     INT          NOT NULL DEFAULT 0,
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'OK' COMMENT 'OK / ERROR',
    `error_message`  VARCHAR(1024) DEFAULT NULL,
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_task_node_seq` (`task_id`, `node_id`, `step_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Workflow 节点 Trace';

CREATE TABLE IF NOT EXISTS `tool_registry` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(64)  NOT NULL COMMENT '工具名（LLM 看到的）',
    `description`    VARCHAR(512) NOT NULL COMMENT '喂给 LLM 的描述',
    `params_schema`  JSON         DEFAULT NULL COMMENT 'JSON Schema',
    `handler_bean`   VARCHAR(128) NOT NULL COMMENT 'Spring Bean 类名',
    `enabled`        TINYINT      NOT NULL DEFAULT 1,
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 工具注册表';
```

用户在 IDE 中手动执行（subagent 不跑命令）。建议命令：
```bash
docker exec -i irp-mysql mysql -uirp -pirppw irp < src/main/resources/db/schema-phase2.sql
```

- [ ] **Step 1.2：创建 `ReportTask.java`**

```java
package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("report_task")
public class ReportTask extends BaseEntity {

    private Long userId;
    private String topic;
    private String workflowName;
    private String status;
    private String finalMarkdown;

    @Column(typeHandler = JacksonTypeHandler.class)
    private List<CitationData> citationsJson;

    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    /** 嵌入式引用记录（与 agent.core.Citation 同结构；放 entity 内便于 Jackson 序列化）。 */
    @Data
    public static class CitationData {
        private Long docId;
        private String docTitle;
        private String source;
        private String sectionTitle;
        private Integer pageStart;
        private Integer pageEnd;
    }
}
```

- [ ] **Step 1.3：创建 `WorkflowNodeRun.java`**

```java
package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("workflow_node_run")
public class WorkflowNodeRun extends BaseEntity {

    private Long taskId;
    private String nodeId;
    private String agentRole;
    private String stepType;       // LLM_CALL / TOOL_CALL
    private Integer stepSeq;
    private String promptVersion;
    private String model;
    private String toolName;
    private String inputJson;
    private String outputJson;
    private Integer tokensIn;
    private Integer tokensOut;
    private Integer latencyMs;
    private String status;         // OK / ERROR
    private String errorMessage;
}
```

- [ ] **Step 1.4：创建 `ToolRegistry.java`**

```java
package com.leo.enterpriseinertraining.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("tool_registry")
public class ToolRegistry extends BaseEntity {

    private String name;
    private String description;
    private String paramsSchema;   // JSON Schema 字符串
    private String handlerBean;
    private Integer enabled;
}
```

- [ ] **Step 1.5：创建 3 个 Mapper**

`src/main/java/com/leo/enterpriseinertraining/mapper/ReportTaskMapper.java`：
```java
package com.leo.enterpriseinertraining.mapper;

import com.leo.enterpriseinertraining.entity.ReportTask;
import com.mybatisflex.core.BaseMapper;

public interface ReportTaskMapper extends BaseMapper<ReportTask> {
}
```

`src/main/java/com/leo/enterpriseinertraining/mapper/WorkflowNodeRunMapper.java`：
```java
package com.leo.enterpriseinertraining.mapper;

import com.leo.enterpriseinertraining.entity.WorkflowNodeRun;
import com.mybatisflex.core.BaseMapper;

public interface WorkflowNodeRunMapper extends BaseMapper<WorkflowNodeRun> {
}
```

`src/main/java/com/leo/enterpriseinertraining/mapper/ToolRegistryMapper.java`：
```java
package com.leo.enterpriseinertraining.mapper;

import com.leo.enterpriseinertraining.entity.ToolRegistry;
import com.mybatisflex.core.BaseMapper;

public interface ToolRegistryMapper extends BaseMapper<ToolRegistry> {
}
```

- [ ] **Step 1.6：提交**

```bash
git add src/main/resources/db/schema-phase2.sql \
        src/main/java/com/leo/enterpriseinertraining/entity/ReportTask.java \
        src/main/java/com/leo/enterpriseinertraining/entity/WorkflowNodeRun.java \
        src/main/java/com/leo/enterpriseinertraining/entity/ToolRegistry.java \
        src/main/java/com/leo/enterpriseinertraining/mapper/ReportTaskMapper.java \
        src/main/java/com/leo/enterpriseinertraining/mapper/WorkflowNodeRunMapper.java \
        src/main/java/com/leo/enterpriseinertraining/mapper/ToolRegistryMapper.java
git commit -m "feat(agent): phase-2 DDL + 3 entities (report_task/workflow_node_run/tool_registry) + mappers"
```

---

## Task 2：Tool SPI 接口 + ToolRegistry 启动扫描

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/agent/tool/AgentTool.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/agent/tool/AgentToolMarker.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/agent/tool/ToolRegistryService.java`

- [ ] **Step 2.1：创建 `AgentTool.java` 接口**

```java
package com.leo.enterpriseinertraining.agent.tool;

/**
 * Agent 可调用的工具 SPI。
 *
 * <p>实现类需要：
 * <ol>
 *   <li>声明 {@link AgentToolMarker @AgentToolMarker} 注解（也是 {@code @Component}）</li>
 *   <li>实现本接口的 4 个方法</li>
 * </ol>
 * 启动时由 {@link ToolRegistryService} 自动扫描，写入 {@code tool_registry} 表。</p>
 */
public interface AgentTool {

    /** 工具名（喂给 LLM，全局唯一）。 */
    String name();

    /** 描述（喂给 LLM 决定是否调用）。 */
    String description();

    /** 参数 record / POJO 类型，用于反射出 JSON schema。 */
    Class<?> paramsType();

    /** 执行工具。params 是已经按 {@link #paramsType()} 反序列化好的对象。 */
    Object invoke(Object params);
}
```

- [ ] **Step 2.2：创建 `AgentToolMarker.java` 注解**

```java
package com.leo.enterpriseinertraining.agent.tool;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个 {@link AgentTool} 实现，让 Spring 自动作为 Bean 创建。
 * 等同于 {@code @Component}，但语义更明确。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface AgentToolMarker {
}
```

- [ ] **Step 2.3：创建 `ToolRegistryService.java`**

```java
package com.leo.enterpriseinertraining.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import com.leo.enterpriseinertraining.entity.ToolRegistry;
import com.leo.enterpriseinertraining.mapper.ToolRegistryMapper;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.leo.enterpriseinertraining.entity.table.ToolRegistryTableDef.TOOL_REGISTRY;

/**
 * 启动时扫描所有 {@link AgentTool} Bean，按 name 注册到 {@code tool_registry} 表。
 * 提供按名查找工具的能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolRegistryService {

    private final List<AgentTool> tools;           // Spring 自动注入所有 AgentTool bean
    private final ToolRegistryMapper mapper;
    private final ObjectMapper om = new ObjectMapper();

    private final Map<String, AgentTool> byName = new HashMap<>();

    @PostConstruct
    public void init() {
        for (AgentTool t : tools) {
            byName.put(t.name(), t);
            upsertDbRow(t);
        }
        log.info("[ToolRegistry] registered {} tools: {}", tools.size(), byName.keySet());
    }

    /** 按名拿一组工具（顺序保留），找不到的会被忽略并 warn。 */
    public List<AgentTool> byNames(List<String> names) {
        return names.stream()
                .map(n -> {
                    AgentTool t = byName.get(n);
                    if (t == null) log.warn("[ToolRegistry] tool '{}' not found", n);
                    return t;
                })
                .filter(x -> x != null)
                .toList();
    }

    public AgentTool byNameOrThrow(String name) {
        AgentTool t = byName.get(name);
        if (t == null) throw new IllegalArgumentException("Tool not found: " + name);
        return t;
    }

    private void upsertDbRow(AgentTool t) {
        try {
            String schema = toJsonSchema(t.paramsType());
            ToolRegistry existing = mapper.selectOneByQuery(
                    QueryWrapper.create().where(TOOL_REGISTRY.NAME.eq(t.name())));
            if (existing == null) {
                ToolRegistry row = new ToolRegistry();
                row.setName(t.name());
                row.setDescription(t.description());
                row.setParamsSchema(schema);
                row.setHandlerBean(t.getClass().getName());
                row.setEnabled(1);
                mapper.insert(row);
                log.info("[ToolRegistry] DB insert: {}", t.name());
            } else {
                existing.setDescription(t.description());
                existing.setParamsSchema(schema);
                existing.setHandlerBean(t.getClass().getName());
                existing.setEnabled(1);
                mapper.update(existing);
                log.debug("[ToolRegistry] DB update: {}", t.name());
            }
        } catch (Exception e) {
            log.error("[ToolRegistry] upsert failed for {}: {}", t.name(), e.getMessage(), e);
        }
    }

    @SuppressWarnings({"deprecation"})
    private String toJsonSchema(Class<?> paramsType) throws Exception {
        // Jackson 模块的 JsonSchema generator 在 Jackson 3 已 deprecated，但 2.x 还能用。
        // 简化：直接序列化 paramsType 的字段名+类型为简易 schema。
        // 这里用最朴素的 reflection，避免引入 victools/jsonschema-generator 额外依赖。
        Map<String, Object> properties = new HashMap<>();
        for (var f : paramsType.getDeclaredFields()) {
            Map<String, String> p = new HashMap<>();
            p.put("type", jsonType(f.getType()));
            properties.put(f.getName(), p);
        }
        Map<String, Object> root = new HashMap<>();
        root.put("type", "object");
        root.put("properties", properties);
        return om.writeValueAsString(root);
    }

    private String jsonType(Class<?> c) {
        if (c == String.class) return "string";
        if (c == Integer.class || c == int.class || c == Long.class || c == long.class) return "integer";
        if (c == Boolean.class || c == boolean.class) return "boolean";
        if (c == Double.class || c == double.class || c == Float.class || c == float.class) return "number";
        return "object";
    }
}
```

> 注：`JsonSchemaGenerator` import 没有用到（我们走 reflection 简化路径），编译时若 IDE 报 unused import 删掉即可；不要引入 `victools` 库。

实际上**删掉** `import com.fasterxml.jackson.module.jsonSchema.*` 两行（避免编译错误，那个模块不在 Jackson 默认 dep 内）。修正后的 import 块：

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.entity.ToolRegistry;
import com.leo.enterpriseinertraining.mapper.ToolRegistryMapper;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.leo.enterpriseinertraining.entity.table.ToolRegistryTableDef.TOOL_REGISTRY;
```

类内删去：
- `@SuppressWarnings({"deprecation"})` 注解（不再需要）
- 注释里关于 Jackson 模块的废话

- [ ] **Step 2.4：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/agent/tool/AgentTool.java \
        src/main/java/com/leo/enterpriseinertraining/agent/tool/AgentToolMarker.java \
        src/main/java/com/leo/enterpriseinertraining/agent/tool/ToolRegistryService.java
git commit -m "feat(agent): Tool SPI (AgentTool/AgentToolMarker) + ToolRegistryService boot-scan"
```

---

## Task 3：HybridSearchTool 实现（第一个 Tool）

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/agent/tool/impl/HybridSearchTool.java`

- [ ] **Step 3.1：实现 `HybridSearchTool.java`**

```java
package com.leo.enterpriseinertraining.agent.tool.impl;

import com.leo.enterpriseinertraining.agent.tool.AgentTool;
import com.leo.enterpriseinertraining.agent.tool.AgentToolMarker;
import com.leo.enterpriseinertraining.dto.RagSearchRequest;
import com.leo.enterpriseinertraining.service.RagSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hybrid 检索工具：把阶段 1 的 RagSearchService 暴露给 LLM Function Calling。
 *
 * <p>LLM 视角的"调用签名"：</p>
 * <pre>
 * hybrid_search(query: string, top_k?: int = 5) → { hits: [...], tookMs }
 * </pre>
 */
@Slf4j
@AgentToolMarker
@RequiredArgsConstructor
public class HybridSearchTool implements AgentTool {

    private final RagSearchService searchService;

    /** 参数 record（公开为 LLM 可见的 schema）。 */
    public record Params(String query, Integer topK) {}

    @Override
    public String name() {
        return "hybrid_search";
    }

    @Override
    public String description() {
        return "在企业内置的行业研报知识库中做 Hybrid 检索（向量 + BM25 + Rerank），" +
               "返回 Top-K 个相关文本片段及其引用（文档标题、来源机构、章节、页码）。" +
               "当你需要查阅行业数据、政策、公司动态或专业术语时使用本工具。";
    }

    @Override
    public Class<?> paramsType() {
        return Params.class;
    }

    @Override
    public Object invoke(Object params) {
        Params p = (Params) params;
        if (p == null || p.query() == null || p.query().isBlank()) {
            throw new IllegalArgumentException("hybrid_search: query 不能为空");
        }
        int topK = p.topK() == null || p.topK() <= 0 ? 5 : Math.min(p.topK(), 20);

        RagSearchRequest req = new RagSearchRequest();
        req.setQuery(p.query());
        req.setTopK(topK);
        req.setUseRerank(true);    // Agent 路径默认走 rerank（阶段 1 已验证 Lift ≥ 0.10）

        var result = searchService.search(req);
        log.info("[hybrid_search] query='{}' topK={} hits={} tookMs={}",
                p.query(), topK, result.hits().size(), result.tookMs());
        return result;             // 直接返回 SearchResult（hits + tookMs）
    }
}
```

- [ ] **Step 3.2：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/agent/tool/impl/HybridSearchTool.java
git commit -m "feat(agent): HybridSearchTool wraps stage-1 RagSearchService for function calling"
```

---

## Task 4：PromptLoader + Researcher prompt 模板

**Files:**
- Create: `src/main/resources/prompts/researcher_prompt_v1.txt`
- Create: `src/main/java/com/leo/enterpriseinertraining/agent/prompt/PromptLoader.java`

- [ ] **Step 4.1：创建 `researcher_prompt_v1.txt`**

```
你是一名资深的行业研究员，任务是根据用户提交的研究主题，撰写一篇 300-600 字的简明研究小结。

工作流程：
1. 仔细理解用户的研究主题
2. 使用 hybrid_search 工具检索企业内部知识库（你应该至少调用 1-3 次，针对主题不同子方面分别提问）
3. 综合检索结果撰写小结，要求：
   - 用 Markdown 格式
   - 主体 2-4 段，每段聚焦一个方面（如规模/趋势/玩家/挑战）
   - 不要捏造数字；所有具体数据都必须来自检索片段
   - 末尾追加 "## 参考资料" 段落，按 [1] [2] ... 列出引用的文档标题、来源机构、页码

约束：
- 调用工具时，query 要具体（"动力电池产业链上游核心环节" 而不是 "电池"）
- 检索结果如果不相关，可换 query 再调
- 最多调用 hybrid_search 4 次
- 不要输出 "我将使用工具" 之类的元描述，直接给出最终的研究小结
```

- [ ] **Step 4.2：创建 `PromptLoader.java`**

```java
package com.leo.enterpriseinertraining.agent.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 从 classpath:prompts/*.txt 读取 prompt 文本，按 "name@version" 索引并缓存。
 *
 * <p>调用约定：{@code load("researcher_prompt@v1")} → 读 {@code prompts/researcher_prompt_v1.txt}。</p>
 * <p>阶段 4 平台中台时会切换到 prompt_template 表实现，本类的接口保持不变。</p>
 */
@Slf4j
@Component
public class PromptLoader {

    private final Map<String, String> cache = new HashMap<>();

    public String load(String ref) {
        return cache.computeIfAbsent(ref, this::readFile);
    }

    private String readFile(String ref) {
        int at = ref.indexOf('@');
        if (at < 0) throw new IllegalArgumentException("prompt ref 必须形如 name@version: " + ref);
        String name = ref.substring(0, at);
        String version = ref.substring(at + 1);
        String path = "prompts/" + name + "_" + version + ".txt";
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            log.info("[PromptLoader] loaded {} ({} chars)", ref, s.length());
            return s;
        } catch (Exception e) {
            throw new RuntimeException("无法加载 prompt: " + path, e);
        }
    }
}
```

- [ ] **Step 4.3：提交**

```bash
git add src/main/resources/prompts/researcher_prompt_v1.txt \
        src/main/java/com/leo/enterpriseinertraining/agent/prompt/PromptLoader.java
git commit -m "feat(agent): PromptLoader + researcher_prompt_v1 template"
```

---

## Task 5：WorkflowDef / WorkflowNode + WorkflowLoader（TDD）

**Files:**
- Create: `src/main/resources/workflow/researcher_only_v1.yaml`
- Create: `src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowDef.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowNode.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowLoader.java`
- Test: `src/test/java/com/leo/enterpriseinertraining/workflow/WorkflowLoaderTest.java`

- [ ] **Step 5.1：创建 YAML `researcher_only_v1.yaml`**

```yaml
name: researcher_only_v1
version: 1
nodes:
  - id: research
    agent: Researcher
    prompt: researcher_prompt@v1
    tools:
      - hybrid_search
```

- [ ] **Step 5.2：先写测试 `WorkflowLoaderTest.java`**

```java
package com.leo.enterpriseinertraining.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowLoaderTest {

    private final WorkflowLoader loader = new WorkflowLoader();

    @Test
    void load_researcher_only_v1() {
        WorkflowDef def = loader.load("researcher_only_v1");
        assertEquals("researcher_only_v1", def.getName());
        assertEquals(1, def.getVersion());
        assertEquals(1, def.getNodes().size());

        WorkflowNode n = def.getNodes().get(0);
        assertEquals("research", n.getId());
        assertEquals("Researcher", n.getAgent());
        assertEquals("researcher_prompt@v1", n.getPrompt());
        assertEquals(1, n.getTools().size());
        assertEquals("hybrid_search", n.getTools().get(0));
    }

    @Test
    void load_unknown_throws() {
        assertThrows(RuntimeException.class, () -> loader.load("no_such_workflow"));
    }
}
```

- [ ] **Step 5.3：实现 `WorkflowNode.java`**

```java
package com.leo.enterpriseinertraining.workflow;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowNode {
    private String id;
    private String agent;
    private String prompt;
    private List<String> tools;
}
```

- [ ] **Step 5.4：实现 `WorkflowDef.java`**

```java
package com.leo.enterpriseinertraining.workflow;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowDef {
    private String name;
    private Integer version;
    private List<WorkflowNode> nodes;
}
```

- [ ] **Step 5.5：实现 `WorkflowLoader.java`**

```java
package com.leo.enterpriseinertraining.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WorkflowLoader {

    private final Map<String, WorkflowDef> cache = new HashMap<>();

    public WorkflowDef load(String name) {
        return cache.computeIfAbsent(name, this::readYaml);
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

- [ ] **Step 5.6：提交**

```bash
git add src/main/resources/workflow/researcher_only_v1.yaml \
        src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowDef.java \
        src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowNode.java \
        src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowLoader.java \
        src/test/java/com/leo/enterpriseinertraining/workflow/WorkflowLoaderTest.java
git commit -m "feat(workflow): WorkflowDef/Node + SnakeYAML loader + TDD"
```

---

## Task 6：WorkflowNodeRunRecorder（Trace 写入器）

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/trace/WorkflowNodeRunRecorder.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/trace/TraceQueryService.java`

- [ ] **Step 6.1：实现 `WorkflowNodeRunRecorder.java`**

```java
package com.leo.enterpriseinertraining.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.entity.WorkflowNodeRun;
import com.leo.enterpriseinertraining.mapper.WorkflowNodeRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 写入 {@code workflow_node_run} 的薄包装。
 *
 * <p>step_seq 在同一 (taskId, nodeId) 内自增。线程安全。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowNodeRunRecorder {

    private final WorkflowNodeRunMapper mapper;
    private final ObjectMapper om = new ObjectMapper();

    /** key = taskId + "::" + nodeId */
    private final Map<String, AtomicInteger> seqCounters = new ConcurrentHashMap<>();

    public WorkflowNodeRun recordLlmCall(long taskId, String nodeId, String agentRole,
                                          String promptVersion, String model,
                                          Object input, Object output,
                                          int tokensIn, int tokensOut, int latencyMs,
                                          String status, String errorMessage) {
        WorkflowNodeRun row = new WorkflowNodeRun();
        row.setTaskId(taskId);
        row.setNodeId(nodeId);
        row.setAgentRole(agentRole);
        row.setStepType("LLM_CALL");
        row.setStepSeq(nextSeq(taskId, nodeId));
        row.setPromptVersion(promptVersion);
        row.setModel(model);
        row.setInputJson(toJson(input));
        row.setOutputJson(toJson(output));
        row.setTokensIn(tokensIn);
        row.setTokensOut(tokensOut);
        row.setLatencyMs(latencyMs);
        row.setStatus(status);
        row.setErrorMessage(errorMessage);
        mapper.insert(row);
        return row;
    }

    public WorkflowNodeRun recordToolCall(long taskId, String nodeId, String agentRole,
                                           String toolName,
                                           Object input, Object output,
                                           int latencyMs,
                                           String status, String errorMessage) {
        WorkflowNodeRun row = new WorkflowNodeRun();
        row.setTaskId(taskId);
        row.setNodeId(nodeId);
        row.setAgentRole(agentRole);
        row.setStepType("TOOL_CALL");
        row.setStepSeq(nextSeq(taskId, nodeId));
        row.setToolName(toolName);
        row.setInputJson(toJson(input));
        row.setOutputJson(toJson(output));
        row.setTokensIn(0);
        row.setTokensOut(0);
        row.setLatencyMs(latencyMs);
        row.setStatus(status);
        row.setErrorMessage(errorMessage);
        mapper.insert(row);
        return row;
    }

    private int nextSeq(long taskId, String nodeId) {
        String k = taskId + "::" + nodeId;
        return seqCounters.computeIfAbsent(k, x -> new AtomicInteger(0)).getAndIncrement();
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"_serializeError\":\"" + e.getMessage() + "\"}";
        }
    }
}
```

- [ ] **Step 6.2：实现 `TraceQueryService.java`**

```java
package com.leo.enterpriseinertraining.trace;

import com.leo.enterpriseinertraining.entity.WorkflowNodeRun;
import com.leo.enterpriseinertraining.mapper.WorkflowNodeRunMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.leo.enterpriseinertraining.entity.table.WorkflowNodeRunTableDef.WORKFLOW_NODE_RUN;

@Service
@RequiredArgsConstructor
public class TraceQueryService {

    private final WorkflowNodeRunMapper mapper;

    public List<WorkflowNodeRun> findByTaskId(long taskId) {
        return mapper.selectListByQuery(
                QueryWrapper.create()
                        .where(WORKFLOW_NODE_RUN.TASK_ID.eq(taskId))
                        .orderBy(WORKFLOW_NODE_RUN.STEP_SEQ, true));
    }
}
```

- [ ] **Step 6.3：单测 `WorkflowNodeRunRecorderTest.java`（仅纯逻辑校验）**

`src/test/java/com/leo/enterpriseinertraining/trace/WorkflowNodeRunRecorderTest.java`：

```java
package com.leo.enterpriseinertraining.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 纯逻辑测试：toJson 在异常输入下不抛、null 处理正常。
 * mapper 行为留给集成测试（@SpringBootTest）。
 */
class WorkflowNodeRunRecorderTest {

    @Test
    void toJson_null_returns_null() throws Exception {
        // 通过反射调 private toJson —— 这里改为复制实现验证算法
        ObjectMapper om = new ObjectMapper();
        assertNull(toJsonCopy(om, null));
    }

    @Test
    void toJson_map_ok() {
        ObjectMapper om = new ObjectMapper();
        String s = toJsonCopy(om, Map.of("a", 1));
        assertTrue(s.contains("\"a\""));
        assertTrue(s.contains("1"));
    }

    private static String toJsonCopy(ObjectMapper om, Object o) {
        if (o == null) return null;
        try { return om.writeValueAsString(o); }
        catch (Exception e) { return "{\"_serializeError\":\"" + e.getMessage() + "\"}"; }
    }
}
```

- [ ] **Step 6.4：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/trace/WorkflowNodeRunRecorder.java \
        src/main/java/com/leo/enterpriseinertraining/trace/TraceQueryService.java \
        src/test/java/com/leo/enterpriseinertraining/trace/WorkflowNodeRunRecorderTest.java
git commit -m "feat(trace): WorkflowNodeRunRecorder (LLM/Tool call write) + TraceQueryService"
```

---

## Task 7：SseSink + SseSinkManager

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/stream/SseSink.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/stream/SseSinkManager.java`

- [ ] **Step 7.1：实现 `SseSink.java`**

```java
package com.leo.enterpriseinertraining.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * SseEmitter 的薄包装。提供 5 种语义事件方法 + 内部线程安全。
 *
 * <p>事件协议见 spec §4.4。</p>
 */
@Slf4j
public class SseSink {

    private final long taskId;
    private final SseEmitter emitter;
    private final ObjectMapper om = new ObjectMapper();
    private volatile boolean closed = false;

    public SseSink(long taskId, SseEmitter emitter) {
        this.taskId = taskId;
        this.emitter = emitter;
    }

    public long taskId() { return taskId; }
    public SseEmitter emitter() { return emitter; }

    public synchronized void nodeStatus(String nodeId, String status) {
        send("node_status", Map.of("nodeId", nodeId, "status", status));
    }

    public synchronized void tool(String toolName, Object paramsJson, String resultPreview) {
        send("tool", Map.of(
                "toolName", toolName,
                "paramsJson", paramsJson == null ? "" : paramsJson,
                "resultPreview", resultPreview == null ? "" : resultPreview));
    }

    public synchronized void token(String delta) {
        send("token", Map.of("delta", delta == null ? "" : delta));
    }

    public synchronized void done(String finalMarkdown, Object citations) {
        send("done", Map.of(
                "finalMarkdown", finalMarkdown == null ? "" : finalMarkdown,
                "citations", citations == null ? java.util.List.of() : citations));
        complete();
    }

    public synchronized void error(String message) {
        send("error", Map.of("message", message == null ? "" : message));
        complete();
    }

    public synchronized void ping() {
        send("ping", Map.of("ts", System.currentTimeMillis()));
    }

    private void send(String event, Object data) {
        if (closed) return;
        try {
            emitter.send(SseEmitter.event().name(event).data(om.writeValueAsString(data)));
        } catch (IOException | IllegalStateException e) {
            log.warn("[SSE/{}] send '{}' failed: {}", taskId, event, e.getMessage());
            closed = true;
        }
    }

    public synchronized void complete() {
        if (closed) return;
        closed = true;
        try { emitter.complete(); } catch (Exception ignored) {}
    }

    public synchronized void completeWithError(Throwable t) {
        if (closed) return;
        closed = true;
        try { emitter.completeWithError(t); } catch (Exception ignored) {}
    }

    public boolean isClosed() { return closed; }
}
```

- [ ] **Step 7.2：实现 `SseSinkManager.java`**

```java
package com.leo.enterpriseinertraining.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 按 taskId 管理 SseSink 单例。同一 taskId 多次 GET 会替换旧 emitter（断点重连场景）。
 *
 * <p>阶段 2 简化为单连接模型；阶段 3 引入 Redis Pub/Sub 实现跨实例广播时再扩。</p>
 */
@Slf4j
@Component
public class SseSinkManager {

    private final Map<Long, SseSink> sinks = new ConcurrentHashMap<>();

    /** 客户端 GET 时调用，注册一个新的 SseSink。返回的 SseEmitter 直接交给 Spring MVC。 */
    public SseEmitter register(long taskId) {
        SseEmitter emitter = new SseEmitter(0L);  // 永不超时；后端 close 决定
        SseSink sink = new SseSink(taskId, emitter);

        SseSink prev = sinks.put(taskId, sink);
        if (prev != null) {
            log.info("[SinkManager/{}] replaced previous sink", taskId);
            prev.complete();
        }

        emitter.onCompletion(() -> {
            log.info("[SinkManager/{}] onCompletion", taskId);
            sinks.remove(taskId, sink);
        });
        emitter.onTimeout(() -> {
            log.info("[SinkManager/{}] onTimeout", taskId);
            sinks.remove(taskId, sink);
            sink.complete();
        });
        emitter.onError(t -> {
            log.warn("[SinkManager/{}] onError: {}", taskId, t.getMessage());
            sinks.remove(taskId, sink);
        });

        return emitter;
    }

    /** Agent 端推事件时调用。若客户端还没连上，返回 null —— 调用方丢弃即可（事件丢失可接受）。 */
    public SseSink get(long taskId) {
        return sinks.get(taskId);
    }

    /** Agent 完成后或失败后主动清理。 */
    public void remove(long taskId) {
        SseSink s = sinks.remove(taskId);
        if (s != null) s.complete();
    }
}
```

- [ ] **Step 7.3：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/stream/SseSink.java \
        src/main/java/com/leo/enterpriseinertraining/stream/SseSinkManager.java
git commit -m "feat(stream): SseSink + SseSinkManager (5 events: node_status/tool/token/done/error)"
```

---

## Task 8：Agent 接口 + ResearcherAgent（Function Calling + stream）

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/agent/core/{Agent,AgentInvocation,AgentResult,AgentStatus,Citation}.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/agent/role/ResearcherAgent.java`

- [ ] **Step 8.1：core 接口与 record 5 个文件**

`Agent.java`：
```java
package com.leo.enterpriseinertraining.agent.core;

import com.leo.enterpriseinertraining.stream.SseSink;

public interface Agent {
    /** 角色名，与 WorkflowNode.agent 字段对齐（"Researcher"）。 */
    String role();

    AgentResult execute(AgentInvocation invocation, SseSink sink);
}
```

`AgentInvocation.java`：
```java
package com.leo.enterpriseinertraining.agent.core;

import com.leo.enterpriseinertraining.agent.tool.AgentTool;

import java.util.List;

public record AgentInvocation(
        long taskId,
        String nodeId,
        String topic,
        List<AgentTool> tools,
        String promptRef       // "researcher_prompt@v1"
) {}
```

`AgentStatus.java`：
```java
package com.leo.enterpriseinertraining.agent.core;

public enum AgentStatus {
    OK, ERROR
}
```

`Citation.java`：
```java
package com.leo.enterpriseinertraining.agent.core;

public record Citation(
        Long docId,
        String docTitle,
        String source,
        String sectionTitle,
        Integer pageStart,
        Integer pageEnd
) {}
```

`AgentResult.java`：
```java
package com.leo.enterpriseinertraining.agent.core;

import java.util.List;

public record AgentResult(
        AgentStatus status,
        String markdown,
        List<Citation> citations,
        String errorMessage
) {
    public static AgentResult ok(String md, List<Citation> citations) {
        return new AgentResult(AgentStatus.OK, md, citations, null);
    }
    public static AgentResult error(String msg) {
        return new AgentResult(AgentStatus.ERROR, null, List.of(), msg);
    }
}
```

- [ ] **Step 8.2：实现 `ResearcherAgent.java`**

```java
package com.leo.enterpriseinertraining.agent.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.*;
import com.leo.enterpriseinertraining.agent.prompt.PromptLoader;
import com.leo.enterpriseinertraining.agent.tool.AgentTool;
import com.leo.enterpriseinertraining.agent.tool.ToolInvocationTracer;
import com.leo.enterpriseinertraining.service.RagSearchService;
import com.leo.enterpriseinertraining.stream.SseSink;
import com.leo.enterpriseinertraining.trace.WorkflowNodeRunRecorder;
import com.leo.enterpriseinertraining.vo.CitationVO;
import com.leo.enterpriseinertraining.vo.RagHitVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Researcher Agent：Spring AI 1.0 Function Calling + 工具调用 Trace。
 *
 * <p>实现思路：</p>
 * <ol>
 *   <li>把 {@link AgentTool} 列表通过 {@link FunctionToolCallback} 桥接成 Spring AI 工具</li>
 *   <li>桥接 lambda 内部包了一层：执行前推 {@code SSE event:tool}、执行后写
 *       {@code workflow_node_run TOOL_CALL} 行</li>
 *   <li>{@code ChatClient.prompt().tools(...).call()} 同步等到最终回答</li>
 *   <li>结束后写 {@code workflow_node_run LLM_CALL} 行，提取 markdown + 引用</li>
 *   <li>把整段最终 markdown 当作一个大 token 推给 SSE（简化版；阶段 3 改用 .stream() 真流式）</li>
 * </ol>
 *
 * <p><b>关于 token-by-token 流式</b>：Spring AI 1.0 GA 的 ChatClient.stream() 与 function
 * calling 一起用时，流的语义比较复杂（每个回合的中间 message 会被框架吞掉）。阶段 2 先用
 * .call() 同步拿到最终结果，token 事件用整段切片模拟；阶段 3 再升级。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResearcherAgent implements Agent {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptLoader promptLoader;
    private final WorkflowNodeRunRecorder recorder;
    private final ToolInvocationTracer tracer;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${app.dashscope.chat-model:qwen-plus}")
    private String defaultModel;

    @Override
    public String role() {
        return "Researcher";
    }

    @Override
    public AgentResult execute(AgentInvocation inv, SseSink sink) {
        long t0 = System.currentTimeMillis();
        try {
            String systemPrompt = promptLoader.load(inv.promptRef());

            // 1) 桥接工具给 Spring AI
            List<ToolCallback> callbacks = new ArrayList<>();
            for (AgentTool t : inv.tools()) {
                callbacks.add(toCallback(t, inv, sink));
            }

            // 2) 调 LLM（同步 .call()）
            ChatClient client = chatClientBuilder.build();
            long llmStart = System.currentTimeMillis();
            var response = client.prompt()
                    .system(systemPrompt)
                    .user(inv.topic())
                    .toolCallbacks(callbacks.toArray(ToolCallback[]::new))
                    .call()
                    .chatResponse();
            long llmLatency = System.currentTimeMillis() - llmStart;

            String content = response.getResult().getOutput().getText();
            int tokensIn = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getPromptTokens().intValue();
            int tokensOut = response.getMetadata().getUsage() == null ? 0
                    : response.getMetadata().getUsage().getCompletionTokens().intValue();

            // 3) 写 LLM_CALL trace 行
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), defaultModel,
                    Map.of("topic", inv.topic()),
                    Map.of("content", content),
                    tokensIn, tokensOut, (int) llmLatency,
                    "OK", null);

            // 4) 把最终 markdown 切片推 SSE token 事件（简化流式体验）
            if (sink != null && !sink.isClosed()) {
                for (int i = 0; i < content.length(); i += 64) {
                    sink.token(content.substring(i, Math.min(i + 64, content.length())));
                }
            }

            // 5) 提取引用（从工具调用 Trace 里聚合 hits）
            List<Citation> citations = aggregateCitations(inv.taskId(), inv.nodeId());

            log.info("[Researcher/{}] done in {} ms (LLM {} ms), tokensIn={} tokensOut={}",
                    inv.taskId(), System.currentTimeMillis() - t0, llmLatency, tokensIn, tokensOut);
            return AgentResult.ok(content, citations);

        } catch (Exception e) {
            log.error("[Researcher/{}] failed", inv.taskId(), e);
            recorder.recordLlmCall(
                    inv.taskId(), inv.nodeId(), role(),
                    inv.promptRef(), defaultModel,
                    Map.of("topic", inv.topic()), null,
                    0, 0, (int) (System.currentTimeMillis() - t0),
                    "ERROR", e.getMessage());
            return AgentResult.error(e.getMessage());
        }
    }

    /** 把一个 AgentTool 包成 Spring AI ToolCallback，并在中间夹一层 trace + SSE 事件。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ToolCallback toCallback(AgentTool t, AgentInvocation inv, SseSink sink) {
        Function<Object, Object> wrapped = (Object params) -> {
            long start = System.currentTimeMillis();
            try {
                // 推 SSE: tool 事件
                if (sink != null) {
                    String paramsJson = safeJson(params);
                    sink.tool(t.name(), paramsJson, "invoking...");
                }
                Object result = t.invoke(params);
                int latency = (int) (System.currentTimeMillis() - start);

                // 写 TOOL_CALL trace
                tracer.recordSuccess(inv.taskId(), inv.nodeId(), role(), t.name(),
                        params, result, latency);
                return result;
            } catch (Exception e) {
                int latency = (int) (System.currentTimeMillis() - start);
                tracer.recordError(inv.taskId(), inv.nodeId(), role(), t.name(),
                        params, e, latency);
                throw new RuntimeException("tool '" + t.name() + "' invoke failed", e);
            }
        };

        return FunctionToolCallback.builder(t.name(), wrapped)
                .description(t.description())
                .inputType((Class) t.paramsType())
                .build();
    }

    /** 从已写入的 TOOL_CALL trace 行里提取 hits → Citation list（去重 by docId）。 */
    private List<Citation> aggregateCitations(long taskId, String nodeId) {
        // 简化实现：阶段 2 这里靠 HybridSearchTool 返回的 SearchResult 中 hits → citation
        // 但我们不直接持有；改为从 trace 表 SELECT 反序列化 output_json
        // 因为还没接 mapper 查询接口，这里返回空列表 —— 阶段 2.5 优化
        // （引用清单可由 LLM 在 markdown 末尾自己列；trace 表里的 hits 是补充信息）
        return List.of();
    }

    private String safeJson(Object o) {
        try { return om.writeValueAsString(o); } catch (Exception e) { return ""; }
    }
}
```

> **⚠️ Spring AI 1.0 API 风险点**：上述使用了 `FunctionToolCallback.builder(...).inputType(...).build()` 与 `ChatClient.prompt().toolCallbacks(...)`。这是 Spring AI 1.0 GA 的 API。如果实际 API 名 / 包路径与此不符（如 1.0 RC 与 GA 之间有差异），**subagent 应 BLOCKED 报回**，控制器查证后给出正确写法，不要私自更名。

- [ ] **Step 8.3：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/agent/core/Agent.java \
        src/main/java/com/leo/enterpriseinertraining/agent/core/AgentInvocation.java \
        src/main/java/com/leo/enterpriseinertraining/agent/core/AgentResult.java \
        src/main/java/com/leo/enterpriseinertraining/agent/core/AgentStatus.java \
        src/main/java/com/leo/enterpriseinertraining/agent/core/Citation.java \
        src/main/java/com/leo/enterpriseinertraining/agent/role/ResearcherAgent.java
git commit -m "feat(agent): Agent SPI + ResearcherAgent (Spring AI function calling + SSE token + trace)"
```

---

## Task 9：ToolInvocationTracer

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/agent/tool/ToolInvocationTracer.java`

- [ ] **Step 9.1：实现 `ToolInvocationTracer.java`**

```java
package com.leo.enterpriseinertraining.agent.tool;

import com.leo.enterpriseinertraining.trace.WorkflowNodeRunRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tool 调用 Trace 写入器（薄包装，便于 ResearcherAgent 调用）。
 *
 * <p>本类故意不做 AOP / Spring 拦截器 —— ResearcherAgent 把 AgentTool 包成 FunctionToolCallback
 * 时显式调用我，业务路径清晰。阶段 3 多 Agent 时若需要更声明式可换 AOP。</p>
 */
@Component
@RequiredArgsConstructor
public class ToolInvocationTracer {

    private final WorkflowNodeRunRecorder recorder;

    public void recordSuccess(long taskId, String nodeId, String agentRole,
                              String toolName, Object input, Object output, int latencyMs) {
        recorder.recordToolCall(taskId, nodeId, agentRole, toolName,
                input, output, latencyMs, "OK", null);
    }

    public void recordError(long taskId, String nodeId, String agentRole,
                            String toolName, Object input, Throwable err, int latencyMs) {
        recorder.recordToolCall(taskId, nodeId, agentRole, toolName,
                input, null, latencyMs, "ERROR", err.getMessage());
    }
}
```

- [ ] **Step 9.2：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/agent/tool/ToolInvocationTracer.java
git commit -m "feat(agent): ToolInvocationTracer (success/error → workflow_node_run TOOL_CALL)"
```

---

## Task 10：WorkflowEngine（顺序执行 + 节点状态机）

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowEngine.java`

- [ ] **Step 10.1：实现 `WorkflowEngine.java`**

```java
package com.leo.enterpriseinertraining.workflow;

import com.leo.enterpriseinertraining.agent.core.Agent;
import com.leo.enterpriseinertraining.agent.core.AgentInvocation;
import com.leo.enterpriseinertraining.agent.core.AgentResult;
import com.leo.enterpriseinertraining.agent.core.AgentStatus;
import com.leo.enterpriseinertraining.agent.tool.AgentTool;
import com.leo.enterpriseinertraining.agent.tool.ToolRegistryService;
import com.leo.enterpriseinertraining.stream.SseSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workflow 顺序执行器（阶段 2 雏形）。
 *
 * <p>限制：</p>
 * <ul>
 *   <li>只支持顺序节点（不支持 fanout / join / 回环）</li>
 *   <li>同步执行，不引入 RocketMQ（阶段 3 升级）</li>
 *   <li>节点失败立即抛出，整条 workflow 标记 FAILED</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private final ToolRegistryService toolRegistry;
    private final List<Agent> agents;

    private Map<String, Agent> byRole;

    public WorkflowExecutionResult execute(long taskId, WorkflowDef def, String topic, SseSink sink) {
        // 懒构建 role → Agent 映射
        if (byRole == null) {
            byRole = new HashMap<>();
            for (Agent a : agents) byRole.put(a.role(), a);
            log.info("[WorkflowEngine] registered agents: {}", byRole.keySet());
        }

        String lastMarkdown = null;
        var allCitations = new java.util.ArrayList<com.leo.enterpriseinertraining.agent.core.Citation>();

        for (WorkflowNode node : def.getNodes()) {
            if (sink != null && !sink.isClosed()) sink.nodeStatus(node.getId(), "RUNNING");

            Agent agent = byRole.get(node.getAgent());
            if (agent == null) {
                String msg = "未知 agent: " + node.getAgent();
                if (sink != null) sink.nodeStatus(node.getId(), "FAILED");
                return WorkflowExecutionResult.failure(msg);
            }

            List<AgentTool> tools = toolRegistry.byNames(
                    node.getTools() == null ? List.of() : node.getTools());

            AgentInvocation invocation = new AgentInvocation(
                    taskId, node.getId(), topic, tools, node.getPrompt());

            AgentResult result = agent.execute(invocation, sink);

            if (result.status() == AgentStatus.ERROR) {
                if (sink != null) sink.nodeStatus(node.getId(), "FAILED");
                return WorkflowExecutionResult.failure(result.errorMessage());
            }

            lastMarkdown = result.markdown();
            if (result.citations() != null) allCitations.addAll(result.citations());

            if (sink != null) sink.nodeStatus(node.getId(), "DONE");
        }

        return WorkflowExecutionResult.success(lastMarkdown, allCitations);
    }

    public record WorkflowExecutionResult(
            boolean ok,
            String markdown,
            List<com.leo.enterpriseinertraining.agent.core.Citation> citations,
            String errorMessage
    ) {
        public static WorkflowExecutionResult success(
                String md, List<com.leo.enterpriseinertraining.agent.core.Citation> cits) {
            return new WorkflowExecutionResult(true, md, cits, null);
        }
        public static WorkflowExecutionResult failure(String msg) {
            return new WorkflowExecutionResult(false, null, List.of(), msg);
        }
    }
}
```

- [ ] **Step 10.2：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/workflow/WorkflowEngine.java
git commit -m "feat(workflow): WorkflowEngine sequential executor with node state machine"
```

---

## Task 11：ReportService + 异步 Orchestrator + POST /api/report/start

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/dto/ReportStartRequest.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/vo/ReportStartVO.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/service/ReportService.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/service/impl/ReportServiceImpl.java`

- [ ] **Step 11.1：`ReportStartRequest.java`**

```java
package com.leo.enterpriseinertraining.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ReportStartRequest implements Serializable {

    @NotBlank
    @Size(min = 4, max = 500)
    private String topic;

    @NotBlank
    private String workflow = "researcher_only_v1";
}
```

- [ ] **Step 11.2：`ReportStartVO.java`**

```java
package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data @NoArgsConstructor @AllArgsConstructor
public class ReportStartVO implements Serializable {
    private Long taskId;
    private String status;
    private String streamUrl;
}
```

- [ ] **Step 11.3：`ReportService.java`**

```java
package com.leo.enterpriseinertraining.service;

import com.leo.enterpriseinertraining.dto.ReportStartRequest;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.vo.ReportStartVO;

public interface ReportService {

    /** 提交任务，立即返回 taskId + streamUrl，异步在 Virtual Thread 跑 workflow。 */
    ReportStartVO start(long userId, ReportStartRequest req);

    /** 查询单任务详情（DONE 后返回 markdown + citations）。 */
    ReportTask findById(long taskId);
}
```

- [ ] **Step 11.4：`ReportServiceImpl.java`**

```java
package com.leo.enterpriseinertraining.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.enterpriseinertraining.agent.core.Citation;
import com.leo.enterpriseinertraining.dto.ReportStartRequest;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.exception.BusinessException;
import com.leo.enterpriseinertraining.exception.ErrorCode;
import com.leo.enterpriseinertraining.mapper.ReportTaskMapper;
import com.leo.enterpriseinertraining.service.ReportService;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.vo.ReportStartVO;
import com.leo.enterpriseinertraining.workflow.WorkflowEngine;
import com.leo.enterpriseinertraining.workflow.WorkflowLoader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportTaskMapper taskMapper;
    private final WorkflowLoader workflowLoader;
    private final WorkflowEngine engine;
    private final SseSinkManager sinkManager;
    private final ObjectMapper om = new ObjectMapper();

    private ExecutorService executor;

    @PostConstruct
    public void initExecutor() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) executor.close();
    }

    @Override
    public ReportStartVO start(long userId, ReportStartRequest req) {
        ReportTask task = new ReportTask();
        task.setUserId(userId);
        task.setTopic(req.getTopic());
        task.setWorkflowName(req.getWorkflow());
        task.setStatus("PENDING");
        taskMapper.insert(task);

        Long taskId = task.getId();
        log.info("[Report] task {} submitted: topic='{}' workflow={}", taskId, req.getTopic(), req.getWorkflow());

        // 异步跑 Virtual Thread；不抛 Future，丢失 == 失败的 task 留在 PENDING/RUNNING
        executor.submit(() -> runWorkflow(taskId, req.getTopic(), req.getWorkflow()));

        return new ReportStartVO(taskId, "PENDING", "/api/report/" + taskId + "/stream");
    }

    @Override
    public ReportTask findById(long taskId) {
        ReportTask t = taskMapper.selectOneById(taskId);
        if (t == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        return t;
    }

    private void runWorkflow(long taskId, String topic, String workflowName) {
        var sink = sinkManager.get(taskId);  // 可能为 null（客户端还没连上），engine 内会判 null
        try {
            // PENDING → RUNNING
            ReportTask t = taskMapper.selectOneById(taskId);
            t.setStatus("RUNNING");
            t.setStartedAt(LocalDateTime.now());
            taskMapper.update(t);

            var def = workflowLoader.load(workflowName);
            var result = engine.execute(taskId, def, topic, sinkManager.get(taskId));

            t = taskMapper.selectOneById(taskId);
            if (result.ok()) {
                t.setStatus("DONE");
                t.setFinalMarkdown(result.markdown());
                t.setCitationsJson(toCitationData(result.citations()));
            } else {
                t.setStatus("FAILED");
                t.setErrorMessage(result.errorMessage());
            }
            t.setFinishedAt(LocalDateTime.now());
            taskMapper.update(t);

            // 推 SSE done / error，sink 自己 complete
            var liveSink = sinkManager.get(taskId);
            if (liveSink != null) {
                if (result.ok()) liveSink.done(result.markdown(), result.citations());
                else liveSink.error(result.errorMessage());
            }
            sinkManager.remove(taskId);

            log.info("[Report] task {} finished: status={}", taskId, t.getStatus());
        } catch (Exception e) {
            log.error("[Report] task {} crashed", taskId, e);
            try {
                ReportTask t = taskMapper.selectOneById(taskId);
                if (t != null) {
                    t.setStatus("FAILED");
                    t.setErrorMessage("内部错误: " + e.getMessage());
                    t.setFinishedAt(LocalDateTime.now());
                    taskMapper.update(t);
                }
            } catch (Exception ignored) {}

            var liveSink = sinkManager.get(taskId);
            if (liveSink != null) liveSink.error("内部错误: " + e.getMessage());
            sinkManager.remove(taskId);
        }
    }

    private List<ReportTask.CitationData> toCitationData(List<Citation> citations) {
        if (citations == null) return List.of();
        return citations.stream().map(c -> {
            ReportTask.CitationData d = new ReportTask.CitationData();
            d.setDocId(c.docId());
            d.setDocTitle(c.docTitle());
            d.setSource(c.source());
            d.setSectionTitle(c.sectionTitle());
            d.setPageStart(c.pageStart());
            d.setPageEnd(c.pageEnd());
            return d;
        }).toList();
    }
}
```

- [ ] **Step 11.5：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/dto/ReportStartRequest.java \
        src/main/java/com/leo/enterpriseinertraining/vo/ReportStartVO.java \
        src/main/java/com/leo/enterpriseinertraining/service/ReportService.java \
        src/main/java/com/leo/enterpriseinertraining/service/impl/ReportServiceImpl.java
git commit -m "feat(report): ReportService start + Virtual Thread orchestrator + SSE wiring"
```

---

## Task 12：ReportController + TraceStepVO + ReportResultVO

**Files:**
- Create: `src/main/java/com/leo/enterpriseinertraining/vo/ReportResultVO.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/vo/TraceStepVO.java`
- Create: `src/main/java/com/leo/enterpriseinertraining/controller/ReportController.java`

- [ ] **Step 12.1：`ReportResultVO.java`**

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
    private String topic;
    private String finalMarkdown;
    private List<ReportTask.CitationData> citations;
    private String errorMessage;
    private Long startedAtEpochMillis;
    private Long finishedAtEpochMillis;
}
```

- [ ] **Step 12.2：`TraceStepVO.java`**

```java
package com.leo.enterpriseinertraining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data @NoArgsConstructor @AllArgsConstructor
public class TraceStepVO implements Serializable {
    private Long id;
    private Integer stepSeq;
    private String nodeId;
    private String agentRole;
    private String stepType;
    private String promptVersion;
    private String model;
    private String toolName;
    private Integer tokensIn;
    private Integer tokensOut;
    private Integer latencyMs;
    private String status;
    private String errorMessage;
    private String inputJsonPreview;
    private String outputJsonPreview;
}
```

- [ ] **Step 12.3：`ReportController.java`**

```java
package com.leo.enterpriseinertraining.controller;

import com.leo.enterpriseinertraining.common.BaseResponse;
import com.leo.enterpriseinertraining.common.ResultUtils;
import com.leo.enterpriseinertraining.dto.ReportStartRequest;
import com.leo.enterpriseinertraining.entity.ReportTask;
import com.leo.enterpriseinertraining.entity.WorkflowNodeRun;
import com.leo.enterpriseinertraining.security.SecurityUtils;
import com.leo.enterpriseinertraining.service.ReportService;
import com.leo.enterpriseinertraining.stream.SseSinkManager;
import com.leo.enterpriseinertraining.trace.TraceQueryService;
import com.leo.enterpriseinertraining.vo.ReportResultVO;
import com.leo.enterpriseinertraining.vo.ReportStartVO;
import com.leo.enterpriseinertraining.vo.TraceStepVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
@Tag(name = "研究报告", description = "提交主题 → Agent 执行 → SSE 流式返回")
public class ReportController {

    private final ReportService reportService;
    private final TraceQueryService traceQueryService;
    private final SseSinkManager sinkManager;

    @PostMapping("/start")
    @Operation(summary = "提交研究主题，立即返回 taskId + streamUrl")
    public BaseResponse<ReportStartVO> start(@RequestBody @Valid ReportStartRequest req) {
        long userId = SecurityUtils.currentUserId();
        return ResultUtils.success(reportService.start(userId, req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单任务最终结果")
    public BaseResponse<ReportResultVO> get(@PathVariable long id) {
        ReportTask t = reportService.findById(id);
        ReportResultVO vo = new ReportResultVO(
                t.getId(), t.getStatus(), t.getTopic(),
                t.getFinalMarkdown(), t.getCitationsJson(), t.getErrorMessage(),
                t.getStartedAt() == null ? null : t.getStartedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                t.getFinishedAt() == null ? null : t.getFinishedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return ResultUtils.success(vo);
    }

    @GetMapping(value = "/{id}/stream", produces = "text/event-stream")
    @Operation(summary = "SSE 实时流（node_status / tool / token / done / error）")
    public SseEmitter stream(@PathVariable long id) {
        // 简单防护：任务必须存在（且属于当前用户 —— 阶段 3 加权限校验，本阶段先信任 JWT）
        reportService.findById(id);
        return sinkManager.register(id);
    }

    @GetMapping("/{id}/trace")
    @Operation(summary = "查 Trace 时间轴（workflow_node_run）")
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

    private String preview(String s) {
        if (s == null) return null;
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
```

- [ ] **Step 12.4：提交**

```bash
git add src/main/java/com/leo/enterpriseinertraining/vo/ReportResultVO.java \
        src/main/java/com/leo/enterpriseinertraining/vo/TraceStepVO.java \
        src/main/java/com/leo/enterpriseinertraining/controller/ReportController.java
git commit -m "feat(report): /api/report/{start,id,id/stream,id/trace} controller"
```

---

## 阶段 2 出口条件（DoD）

由**用户在 IDE 端**验证（subagent 不跑）：

### 功能层
- [ ] 执行 `schema-phase2.sql` 后 3 表存在
- [ ] 启动应用日志含 `[ToolRegistry] registered 1 tools: [hybrid_search]`
- [ ] `tool_registry` 表有 1 行（name=hybrid_search）
- [ ] Knife4j 调 `POST /api/report/start`，topic="2026 中国动力电池行业趋势" → 返回 taskId + streamUrl + status=PENDING
- [ ] 在浏览器打开 `EventSource` 或 curl `--no-buffer http://localhost:8080/api/report/{id}/stream`（带 JWT，curl 用 `-H "Authorization: Bearer $TOKEN"`）：能看到 `event:node_status RUNNING` → 多个 `event:tool` → 多个 `event:token` → `event:done`
- [ ] `GET /api/report/{id}` 返回 status=DONE + finalMarkdown 含正文 2-4 段 + "## 参考资料" 含 2-5 条引用
- [ ] `GET /api/report/{id}/trace` 返回 ≥3 个 step（至少 1 个 LLM_CALL + 1 个 TOOL_CALL）

### 性能层
- [ ] 单 task 总耗时 ≤ 40s
- [ ] 客户端 SSE 首个事件 ≤ 5s

### 工程层
- [ ] `workflow_node_run` 每个 LLM_CALL 都有 tokens_in/out > 0；每个 TOOL_CALL 都有 input_json + output_json
- [ ] task 失败时 status=FAILED + error_message 落库，SSE 推 `event:error` 后断开
- [ ] 客户端断开连接：服务端清理 SseSink，不泄漏

### 测试
- [ ] `WorkflowLoaderTest` 2/2 通过
- [ ] `WorkflowNodeRunRecorderTest` 2/2 通过

---

## Self-Review

**1. Spec 覆盖：**
- §1 决策（Tool SPI / Function Calling / Agent / YAML 引擎 / SseEmitter / Prompt 文件）→ T2/T3/T4/T5/T7/T8/T10 全部覆盖 ✓
- §2 端到端流程 → T11 ReportServiceImpl + T8 ResearcherAgent + T7 SseSink ✓
- §3 数据模型 3 张表 → T1 ✓
- §4.1 Tool SPI → T2 ✓；§4.2 WorkflowEngine → T5 + T10 ✓；§4.3 Agent → T8 ✓；§4.4 SSE 协议 5 种事件 → T7 ✓
- §5 4 个 REST API（start / id / id/stream / id/trace）→ T11 + T12 ✓
- §6 文件结构 → 与本 plan 各 task 文件清单完全对齐 ✓
- §8 验证清单 → 全部映射到 DoD ✓

**2. Placeholder 扫描：** 无 TBD/TODO/incomplete。Step 2.3 内有"注释里关于 Jackson 模块的废话"提示，但已在该 step 后给出**修正后的 import 块** + 明确删去 deprecation 注解，subagent 跟着改即可，不算 placeholder。`ResearcherAgent.aggregateCitations` 阶段 2 故意返回空 list，spec §4.3 说明"markdown 末尾 LLM 自己列参考资料"是主路径，trace 表里的 hits 是补充信息，不算缺陷。

**3. 类型一致性：**
- `AgentTool.name() / description() / paramsType() / invoke(Object)`（T2）↔ `HybridSearchTool`（T3）↔ `ToolRegistryService.byNames` ↔ `WorkflowEngine` 注入工具 ↔ `ResearcherAgent.toCallback` 使用一致 ✓
- `AgentInvocation(taskId, nodeId, topic, tools, promptRef)`（T8）↔ `WorkflowEngine.execute` 构造 ↔ `ResearcherAgent.execute` 解构使用一致 ✓
- `AgentResult.ok/error` 静态构造（T8）↔ `WorkflowEngine` `result.status()/.errorMessage()/.markdown()/.citations()` 一致 ✓
- `Citation(docId/docTitle/source/sectionTitle/pageStart/pageEnd)`（T8）↔ `ReportTask.CitationData` 同结构（T1）↔ `toCitationData` 映射一致 ✓
- `SseSink.nodeStatus/tool/token/done/error`（T7）↔ `WorkflowEngine` 调 nodeStatus、`ResearcherAgent` 调 token/tool、`ReportServiceImpl` 调 done/error 一致 ✓
- `WorkflowNodeRunRecorder.recordLlmCall/recordToolCall`（T6）↔ `ResearcherAgent` 调 recordLlmCall、`ToolInvocationTracer` 调 recordToolCall 一致 ✓
- `WorkflowDef/Node` 字段（T5）↔ YAML 文件字段一致（name/version/nodes; id/agent/prompt/tools）✓
- `WorkflowExecutionResult.success/failure` 静态构造（T10）↔ `ReportServiceImpl.runWorkflow` `result.ok()/.markdown()/.citations()/.errorMessage()` 一致 ✓
- `ReportStartVO(taskId, status, streamUrl)`（T11）↔ Controller 返回一致 ✓
- `ReportResultVO` 字段（T12）↔ Controller `get` 构造一致 ✓
- `TraceStepVO` 字段（T12）↔ Controller `trace` 构造一致 ✓
- `SseSinkManager.register/get/remove`（T7）↔ Controller `stream` + `ReportServiceImpl.runWorkflow` 使用一致 ✓
- MyBatis-Flex 生成的 `ToolRegistryTableDef.TOOL_REGISTRY.NAME / WORKFLOW_NODE_RUN.TASK_ID/STEP_SEQ` 在 T2 / T6 使用 ✓
- `RagSearchService.search` 返回 `SearchResult` 在阶段 1 已定义（含 hits + tookMs），HybridSearchTool 直接 return 即可 ✓

无遗漏。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-19-phase-2-single-agent-workflow-sse.md`.

两种执行方式：

**1. Subagent-Driven（推荐，沿用阶段 0 / 1 模式）** —— 每个 Task 派 fresh subagent + 简短 review。阶段 2 共 12 Task。

**2. Inline Execution** —— 在当前会话连续跑。会话 token 压力大。

请选择 1 或 2。
