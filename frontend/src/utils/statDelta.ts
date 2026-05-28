// frontend/src/utils/statDelta.ts
//
// F4 #2: 把后端 PlatformOverviewVO 的 *Delta 字段构造成 StatItemDelta，供 StatCard 显示。
//
// 注意 trend 方向：
//   - 任务数 / 成功率：正向变化是好事 → 正数 = up（绿）
//   - 成本 / 耗时：正向变化是坏事 → 正数 = down（红，因为指标恶化）
// 通过 goodWhenPositive 控制语义反转。

import type { StatItemDelta } from '@/types/dashboard'
import { formatCny } from '@/utils/format'

type DeltaKind = 'count' | 'percent' | 'cny' | 'ms'

/**
 * 构造 StatItemDelta；rawNum 为 null 时返回 undefined（StatCard 不渲染 delta 行）。
 *
 * @param rawNum            原始绝对差（同后端字段单位）
 * @param windowLabel       "较昨日" / "较上周"
 * @param kind              格式化模板
 * @param goodWhenPositive  正向变化是否为"好"（决定 trend 颜色）
 */
export function buildStatDelta(
  rawNum: number | null | undefined,
  windowLabel: string | null,
  kind: DeltaKind,
  goodWhenPositive: boolean
): StatItemDelta | undefined {
  if (rawNum == null) return undefined

  const sign = rawNum > 0 ? '+' : rawNum < 0 ? '' : ''   // 负数自带 -，0 时无符号
  let raw: string
  switch (kind) {
    case 'count':
      raw = `${sign}${rawNum.toLocaleString('zh-CN')}`
      break
    case 'percent':
      // taskSuccessRateDelta 是 0~1 范围的绝对差。乘 100 显示成 pp。
      raw = `${sign}${(rawNum * 100).toFixed(1)} pp`
      break
    case 'cny':
      raw = `${sign}${formatCny(Math.abs(rawNum))}${rawNum < 0 ? ' (↓)' : ''}`
      // 负数已经被 formatCny 吃掉绝对值；用 ↓ 兜底显式表示下降
      if (rawNum >= 0) raw = `${sign}${formatCny(rawNum)}`
      else raw = `-${formatCny(Math.abs(rawNum))}`
      break
    case 'ms':
      raw = `${sign}${rawNum} ms`
      break
  }

  const isPositive = rawNum > 0
  const isNegative = rawNum < 0
  let trend: 'up' | 'down' = 'up'
  if (isPositive) trend = goodWhenPositive ? 'up' : 'down'
  else if (isNegative) trend = goodWhenPositive ? 'down' : 'up'
  else trend = 'up' // 0 → 灰色其实更准，但 StatItemDelta.trend 只能 up/down；用 up 顺其自然

  return {
    raw,
    percent: '',                       // 后端只给了绝对差，模板会跳过空 percent
    trend,
    note: windowLabel ?? '较昨日'
  }
}
