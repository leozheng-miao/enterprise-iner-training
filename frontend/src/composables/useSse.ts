import { onBeforeUnmount } from 'vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { useAuthStore } from '@/stores/auth'
import type {
  DoneEvent,
  ErrorEvent,
  NodeStatusEvent,
  PhaseChangedEvent,
  PingEvent,
  SectionDoneEvent,
  TokenEvent,
  ToolEvent
} from '@/types/report'

export interface SseHandlers {
  onNodeStatus?: (data: NodeStatusEvent) => void
  onTool?: (data: ToolEvent) => void
  onToken?: (data: TokenEvent) => void
  onPhaseChanged?: (data: PhaseChangedEvent) => void
  onSectionDone?: (data: SectionDoneEvent) => void
  onDone?: (data: DoneEvent) => void
  onError?: (data: ErrorEvent) => void
  onPing?: (data: PingEvent) => void
  /** 网络层错误 / 连接关闭。 */
  onClose?: (reason: 'done' | 'error' | 'manual') => void
}

export interface SseHandle {
  close: () => void
}

/**
 * 用 @microsoft/fetch-event-source 消费支持 Authorization Header 的 SSE。
 * 浏览器原生 EventSource 不支持自定义 Header，因此用 fetch-event-source 替代。
 *
 * @param url     拼好的完整 SSE URL（例如 /api/report/123/stream）
 * @param handlers 7 种业务事件（含多 Agent 的 phase_changed / section_done）+ ping + close 的回调
 */
export function useSse(url: string, handlers: SseHandlers): SseHandle {
  const auth = useAuthStore()
  const ctrl = new AbortController()
  let closed = false

  function close(reason: 'done' | 'error' | 'manual' = 'manual') {
    if (closed) return
    closed = true
    ctrl.abort()
    handlers.onClose?.(reason)
  }

  fetchEventSource(url, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${auth.token ?? ''}`,
      Accept: 'text/event-stream'
    },
    signal: ctrl.signal,
    // 切走 tab 时不暂停（任务可能还在跑，回来想看到补帧）
    openWhenHidden: true,
    onopen: async (resp) => {
      if (!resp.ok) {
        // 401 / 403 由 onerror 路径走，这里抛出让 fetch-event-source 触发 onerror
        throw new Error(`SSE open failed: ${resp.status}`)
      }
    },
    onmessage: (ev) => {
      if (!ev.event) return
      let data: unknown = {}
      try {
        data = ev.data ? JSON.parse(ev.data) : {}
      } catch {
        return
      }
      switch (ev.event) {
        case 'node_status':
          handlers.onNodeStatus?.(data as NodeStatusEvent)
          break
        case 'tool':
          handlers.onTool?.(data as ToolEvent)
          break
        case 'token':
          handlers.onToken?.(data as TokenEvent)
          break
        case 'phase_changed':
          handlers.onPhaseChanged?.(data as PhaseChangedEvent)
          break
        case 'section_done':
          handlers.onSectionDone?.(data as SectionDoneEvent)
          break
        case 'done':
          handlers.onDone?.(data as DoneEvent)
          close('done')
          break
        case 'error':
          handlers.onError?.(data as ErrorEvent)
          close('error')
          break
        case 'ping':
          handlers.onPing?.(data as PingEvent)
          break
      }
    },
    onerror: (err) => {
      handlers.onError?.({ message: err?.message || 'SSE 连接错误' })
      // 抛出后 fetch-event-source 停止自动重连
      close('error')
      throw err
    }
  }).catch(() => {
    /* swallow: onerror 已经处理过了 */
  })

  onBeforeUnmount(() => close('manual'))

  return { close: () => close('manual') }
}
