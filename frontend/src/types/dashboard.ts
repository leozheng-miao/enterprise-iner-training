// frontend/src/types/dashboard.ts
//
// HomeView 看板的展示类型。原先与 mock 数据混在 mock/dashboard.ts，
// F3 把类型迁到 types 目录，让真实接口数据也能复用。

export type StageStatus = 'done' | 'current' | 'pending'

export interface StageNode {
  index: number
  title: string
  subtitle: string
  status: StageStatus
}

export interface StageDetail {
  index: number
  title: string
  percent: number
  date: string
  statusLabel: string
  status: StageStatus
}

export interface CurrentStageInfo {
  index: number
  title: string
  description: string
  bullets: string[]
}

export interface StatItemDelta {
  raw: string
  percent: string
  trend: 'up' | 'down'
  note: string
}

export interface StatItem {
  key: string
  label: string
  value: string
  unit?: string
  iconName: string
  iconBg: string
  // F3 起改为可选：真实接口（PlatformOverviewVO）暂不提供同比，前端隐藏 delta 行。
  delta?: StatItemDelta
}

export interface CoreCapability {
  key: string
  title: string
  description: string
  iconName: string
  iconBg: string
  link: string
}

export interface ActivityItem {
  id: string
  title: string
  description: string
  time: string
  iconName: string
  iconColor: string
  /** 点击该行跳转的路由（绝对路径）。未设置则该行不可点击。 */
  to?: string
}
