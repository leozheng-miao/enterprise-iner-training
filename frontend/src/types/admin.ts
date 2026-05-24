// frontend/src/types/admin.ts
//
// Admin 中台接口的类型定义，字段与后端 com.leo.enterpriseinertraining.vo.* 严格对齐。
// 后端 Jackson 默认驼峰；但 QueryRewriteVO.sub_queries 有 @JsonProperty 注解，是蛇形。

export interface PlatformOverviewVO {
  totalTasks: number
  successRate: number          // 0.0~1.0
  totalCostCny: number
  avgLatencyMs: number
  p95LatencyMs: number
}

export interface ModelCostVO {
  model: string
  calls: number
  tokensIn: number
  tokensOut: number
  costCny: number
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
  topic: string
  status: TaskStatus
  phase: string | null
  progress: number | null
  startedAt: number | null
  finishedAt: number | null
}

export interface PromptTemplateVO {
  id: number
  name: string
  version: string
  content: string
  model: string | null
  temperature: number | null
  isActive: boolean
  createdAt: number
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
  overall: number
  structure: number
  factuality: number
  reasoning: number
  citation: number
  clarity: number
  comments: Record<string, string>
  latencyMs: number
  createTime: number
}

// 后端 @JsonProperty("sub_queries") -> 蛇形 key
export interface QueryRewriteVO {
  intent: string | null
  industry: string | null
  year: number | null
  geo: string | null
  sub_queries: string[] | null
}
