import { apiGet, apiPost } from './client'
import type {
  ReportDetail,
  ReportStartRequest,
  ReportStartResponse,
  TraceRow
} from '@/types/report'

export const reportApi = {
  start(body: ReportStartRequest) {
    return apiPost<ReportStartResponse>('/report/start', body)
  },
  get(id: number) {
    return apiGet<ReportDetail>(`/report/${id}`)
  },
  trace(id: number) {
    return apiGet<TraceRow[]>(`/report/${id}/trace`)
  }
}

/** SSE 端点不走 axios（要保留长连接），直接拼绝对 URL；交给 useSse 自己 fetch。 */
export function buildStreamUrl(id: number): string {
  const base = import.meta.env.VITE_API_BASE || '/api'
  return `${base}/report/${id}/stream`
}
