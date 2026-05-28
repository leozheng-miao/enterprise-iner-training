// frontend/src/types/admin.ts
//
// Admin 中台接口的类型定义，字段与后端 com.leo.enterpriseinertraining.vo.* 严格对齐。
// 后端 Jackson 默认驼峰；但 QueryRewriteVO.sub_queries 有 @JsonProperty 注解，是蛇形。

export interface PlatformOverviewVO {
  totalTasks: number
  doneTasks: number
  failedTasks: number
  runningTasks: number
  /** 已结束任务中 DONE 占比，0~1。 */
  taskSuccessRate: number
  totalNodeRuns: number
  errorNodeRuns: number
  totalTokensIn: number
  totalTokensOut: number
  /** 全平台累计 Token 成本，单位：元。 */
  totalCostCny: number
  /** DONE 任务平均端到端耗时（ms），无样本时为 null。 */
  avgTaskLatencyMs: number | null

  // ── F4 #8: P95 端到端耗时（DONE 任务 < 20 时为 null，前端 fallback avg） ──
  p95TaskLatencyMs: number | null

  // ── F4 #2: 同比指标（较上一窗口的变化量） ─────────────────────────────
  /** 任务总数变化量；正为增长。 */
  totalTasksDelta: number | null
  /** 成功率绝对差（如 +0.016 = +1.6 pp）。 */
  taskSuccessRateDelta: number | null
  /** Token 成本变化量（元）。 */
  totalCostCnyDelta: number | null
  /** 平均耗时变化量（ms）。 */
  avgTaskLatencyMsDelta: number | null
  /** 对比窗口标签，目前固定 "较昨日"。 */
  compareWindowLabel: string | null
}

export interface ModelCostVO {
  model: string
  calls: number
  tokensIn: number
  tokensOut: number
  /** 该模型累计成本，单位：元。 */
  costCny: number
  /** 平均单次调用耗时（ms），无样本时为 null。 */
  avgLatencyMs: number | null
}

export interface AgentCostVO {
  agentRole: string            // PLANNER / RESEARCHER / ANALYST / WRITER / CRITIC
  calls: number
  tokensIn: number
  tokensOut: number
  costCny: number
}

export type TaskStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

export interface TaskBriefVO {
  id: number
  userId: number | null
  topic: string
  status: TaskStatus
  phase: string | null
  progress: number | null
  errorMessage: string | null
  /** 端到端耗时（finishedAt - startedAt），未结束时为 null。 */
  latencyMs: number | null
  /** 创建时间，epoch 毫秒。 */
  createdAt: number | null
}

export interface PromptTemplateVO {
  id: number
  name: string
  version: string
  content: string
  model: string | null
  temperature: number | null
  /**
   * 是否为当前灰度生效版本。
   * 后端 Lombok boolean + Jackson 默认序列化去掉 is 前缀 → 字段名为 active（不是 isActive）。
   */
  active: boolean
  description: string | null
  createTime: number
  updateTime: number | null
}

export interface PromptCreateRequest {
  name: string
  version: string
  content: string
  model?: string
  temperature?: number
}

export type PromptUpdateRequest = Partial<
  Pick<PromptTemplateVO, 'content' | 'model' | 'temperature'>
>

export interface JudgeRunVO {
  id: number
  taskId: number
  judgeModel: string
  rubricVersion: string
  /** 各维度分数 F4 起改为可空（缓存命中且历史值为 null 时） */
  overall: number | null
  structure: number | null
  factuality: number | null
  reasoning: number | null
  citation: number | null
  clarity: number | null
  comments: Record<string, string>
  latencyMs: number | null
  createTime: number | null
  /** ⚡ F4 #5: 关联任务的研究主题；老数据可能为 null。 */
  topic: string | null
}

// 后端 @JsonProperty("sub_queries") -> 蛇形 key
export interface QueryRewriteVO {
  intent: string | null
  industry: string | null
  year: number | null
  geo: string | null
  sub_queries: string[] | null
}

// ── F4 #3: 当前活跃 Workflow 元信息 ──────────────────────────────────────
// ⚠️ boolean 字段：后端 `private boolean cached` 经 Lombok+Jackson 序列化为 `cached`
// （不带 is 前缀）。F3 在 PromptTemplateVO 上踩过同样的坑，F4 务必沿用 `cached`。
export interface ActiveWorkflowVO {
  name: string
  version: string                // 字符串化的版本号，如 "2"
  file: string                   // "classpath:workflow/multi_agent_v1.yaml"
  nodes: string[]                // 按拓扑序的节点 id 列表
  lastLoadedAt: number | null    // epoch millis；null = 尚未加载
  cached: boolean                // 不要写 isCached
}

// ── F4 #4: Workflow 加载日志 ─────────────────────────────────────────────
export interface WorkflowLogVO {
  eventType: 'cache_clear' | 'yaml_reload' | 'topology_check' | 'activate'
  message: string
  level: 'info' | 'success' | 'warning' | 'error'
  ts: number                     // epoch millis
}

// ── F4 #1: 系统健康子服务 ─────────────────────────────────────────────────
export interface ComponentHealthVO {
  name: string                                  // "API" / "Redis" / "SSE"
  status: 'UP' | 'DOWN' | 'DEGRADED'
  subtitle: string                              // "后端接口服务" 等说明
  latencyMs: number | null                      // ping 耗时；null 表示不适用
  extra: Record<string, unknown> | null         // SSE 行可能含 { connections: number }
}
