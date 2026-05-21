import type { CitationData } from './rag'

/** 任务生命周期状态（对齐后端 ReportTask.status）。 */
export type ReportTaskStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

/** POST /api/report/start 请求。 */
export interface ReportStartRequest {
  topic: string
  workflow?: string
}

/** POST /api/report/start 响应 data。 */
export interface ReportStartResponse {
  taskId: number
  status: ReportTaskStatus
  streamUrl: string
}

/** GET /api/report/{id} 响应 data。 */
export interface ReportDetail {
  taskId: number
  status: ReportTaskStatus
  topic: string
  finalMarkdown: string | null
  citations: CitationData[]
  errorMessage: string | null
  startedAtEpochMillis: number | null
  finishedAtEpochMillis: number | null
}

/** GET /api/report/{id}/trace 单行（对齐后端 WorkflowNodeRun）。 */
export interface TraceRow {
  id: number
  stepSeq: number
  nodeId: string
  agentRole: string
  stepType: 'LLM_CALL' | 'TOOL_CALL'
  promptVersion: string | null
  model: string | null
  toolName: string | null
  tokensIn: number | null
  tokensOut: number | null
  latencyMs: number | null
  status: 'OK' | 'ERROR'
  errorMessage: string | null
  inputJsonPreview: string | null
  outputJsonPreview: string | null
}

// ====== SSE 事件 ======

export type NodeRunStatus = 'RUNNING' | 'DONE' | 'FAILED'

export interface NodeStatusEvent {
  nodeId: string
  status: NodeRunStatus
}

export interface ToolEvent {
  toolName: string
  paramsJson?: string
  resultPreview?: string
}

export interface TokenEvent {
  delta: string
}

export interface DoneEvent {
  finalMarkdown: string
  citations: CitationData[]
}

export interface ErrorEvent {
  message: string
}

export interface PingEvent {
  ts: number
}

/** 前端聚合的"已发生事件"结构，用于 SseEventList 渲染。 */
export type SseEvent =
  | { type: 'node_status'; ts: number; data: NodeStatusEvent }
  | { type: 'tool';        ts: number; data: ToolEvent }
  | { type: 'token';       ts: number; data: TokenEvent }
  | { type: 'done';        ts: number; data: DoneEvent }
  | { type: 'error';       ts: number; data: ErrorEvent }
  | { type: 'ping';        ts: number; data: PingEvent }
