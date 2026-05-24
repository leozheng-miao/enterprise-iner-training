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

/** 千分位格式化（中国本地化）。 */
export function formatNumber(n: number | null | undefined): string {
  if (n == null) return '—'
  return n.toLocaleString('zh-CN')
}

/** 金额格式化（人民币，2 位小数）。 */
export function formatCny(n: number | null | undefined): string {
  if (n == null) return '—'
  return `¥${n.toFixed(2)}`
}

/** 把 ms 时长格式化为 "mm:ss"。负数或 null 返回 '—'。 */
export function formatDurationMs(ms: number | null | undefined): string {
  if (ms == null || ms < 0) return '—'
  const sec = Math.round(ms / 1000)
  const m = String(Math.floor(sec / 60)).padStart(2, '0')
  const s = String(sec % 60).padStart(2, '0')
  return `${m}:${s}`
}

/** 把 0.0~1.0 的成功率格式化为 "xx.x%"。 */
export function formatPercent(ratio: number | null | undefined): string {
  if (ratio == null || Number.isNaN(ratio)) return '—'
  return `${(ratio * 100).toFixed(1)}%`
}
