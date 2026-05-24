// frontend/src/api/admin.ts
//
// Admin 中台接口封装。所有 URL 在 client.ts BASE_URL ('/api') 之下，
// 真实路径形如 '/api/admin/stats/overview'。

import { apiGet, apiPost, apiPut } from './client'
import type {
  PlatformOverviewVO,
  ModelCostVO,
  AgentCostVO,
  TaskBriefVO,
  TaskStatus,
  PromptTemplateVO,
  PromptCreateRequest,
  PromptUpdateRequest,
  JudgeRunVO,
  QueryRewriteVO
} from '@/types/admin'

// MyBatis-Flex 分页结构
export interface Page<T> {
  records: T[]
  totalRow: number
  pageNumber: number
  pageSize: number
  totalPage: number
}

export const adminApi = {
  // ── 平台统计 ──
  overview: () => apiGet<PlatformOverviewVO>('/admin/stats/overview'),
  costByModel: () => apiGet<ModelCostVO[]>('/admin/stats/cost/model'),
  costByAgent: () => apiGet<AgentCostVO[]>('/admin/stats/cost/agent'),
  tasks: (status?: TaskStatus | '', page = 1, size = 20) =>
    apiGet<Page<TaskBriefVO>>('/admin/tasks', {
      status: status || undefined,
      page,
      size
    }),

  // ── Prompt 版本 ──
  listPrompts: () => apiGet<PromptTemplateVO[]>('/admin/prompts'),
  getPrompt: (id: number) => apiGet<PromptTemplateVO>(`/admin/prompts/${id}`),
  createPrompt: (body: PromptCreateRequest) =>
    apiPost<PromptTemplateVO>('/admin/prompts', body),
  updatePrompt: (id: number, body: PromptUpdateRequest) =>
    apiPut<PromptTemplateVO>(`/admin/prompts/${id}`, body),
  activatePrompt: (id: number) =>
    apiPost<PromptTemplateVO>(`/admin/prompts/${id}/activate`),

  // ── LLM-as-Judge ──
  judgeRun: (taskId: number) =>
    apiPost<JudgeRunVO>(`/admin/eval/judge/${taskId}`),
  judgeHistory: (taskId: number) =>
    apiGet<JudgeRunVO[]>(`/admin/eval/judge/${taskId}/history`),
  judgeRecent: (limit = 20) =>
    apiGet<JudgeRunVO[]>('/admin/eval/judge', { limit }),

  // ── Workflow & Query 改写 ──
  reloadWorkflow: () => apiPost<string>('/admin/workflow/reload'),
  rewriteQuery: (topic: string) =>
    apiPost<QueryRewriteVO>('/admin/query-rewrite', { topic })
}
