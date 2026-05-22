/**
 * 把 epoch milliseconds 格式化成 "YYYY-MM-DD HH:mm:ss"。
 * null/undefined → '—'
 */
export function formatEpochMillis(ms: number | null | undefined): string {
  if (ms == null) return '—'
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * 把两个 epoch ms 之间的差格式化成 "Nm Ms"（不到 1 分钟则 "Ns"）。
 * 任一为 null → '—'
 */
export function formatDurationBetween(
  startMs: number | null | undefined,
  endMs: number | null | undefined
): string {
  if (startMs == null || endMs == null) return '—'
  const totalSec = Math.max(0, Math.floor((endMs - startMs) / 1000))
  if (totalSec < 60) return `${totalSec}s`
  const m = Math.floor(totalSec / 60)
  const s = totalSec % 60
  return `${m}m ${s}s`
}

/** SSE 事件流里的时间戳：HH:mm:ss */
export function formatClock(ms: number): string {
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
