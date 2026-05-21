/**
 * Phase F1 首页 dashboard 假数据。
 *
 * 这些数据的真实接口将由 Phase 4 平台中台（Trace / Token 成本 / 任务统计聚合）提供。
 * 在那之前前端先用 mock 填表，保证视觉与设计图 #1 一致。
 *
 * 真接口落地后：保留这些 type 定义，改用 api/platform.ts 拉数据即可。
 */

export type StageStatus = 'done' | 'current' | 'pending'

/** 顶部"项目阶段概览"7 段水平条上的一站。 */
export interface StageNode {
  index: number          // 0-6
  title: string          // "阶段 0"
  subtitle: string       // "环境初始化"
  status: StageStatus
}

/** "阶段进度总览"下面的详情节点（含进度 + 日期 + 状态文案）。 */
export interface StageDetail {
  index: number          // 0-6
  title: string          // "环境初始化"
  percent: number        // 0-100
  date: string           // "2024-05-10" 或 ""
  statusLabel: string    // "100%" / "进行中" / "待开始"
  status: StageStatus
}

/** "当前阶段"卡上的描述。 */
export interface CurrentStageInfo {
  index: number
  title: string          // "阶段 3 - 报告生成"
  description: string
  bullets: string[]      // ["报告大纲生成", "内容生成", ...]
}

/** 4 个统计卡。 */
export interface StatItem {
  key: string
  label: string          // "已接入文档"
  value: string          // "12,842"
  unit?: string          // "s" 等可选
  iconName: string       // Element Plus 图标组件名
  iconBg: string         // CSS 颜色（卡片左上角图标圆角背景）
  delta: {
    raw: string          // "+532" / "-0.28s"
    percent: string      // "↑4.32%" / "↓13.33%"
    trend: 'up' | 'down'
    note: string         // "较昨日"
  }
}

/** "核心能力"4 卡。 */
export interface CoreCapability {
  key: string
  title: string          // "RAG 检索"
  description: string
  iconName: string
  iconBg: string
  link: string           // 路径，点击后路由跳转（未启用的 phase 用 ''）
}

/** "最近活动"列表项。 */
export interface ActivityItem {
  id: string
  title: string          // "用户登录"
  description: string
  time: string           // "09:24:18" / "昨天 17:42"
  iconName: string
  iconColor: string
}

// ====== 数据 ======

export const stageNodes: StageNode[] = [
  { index: 0, title: '阶段 0', subtitle: '环境初始化', status: 'done' },
  { index: 1, title: '阶段 1', subtitle: '数据接入', status: 'done' },
  { index: 2, title: '阶段 2', subtitle: '知识检索', status: 'done' },
  { index: 3, title: '阶段 3', subtitle: '报告生成', status: 'current' },
  { index: 4, title: '阶段 4', subtitle: '评估优化', status: 'pending' },
  { index: 5, title: '阶段 5', subtitle: '监控追踪', status: 'pending' },
  { index: 6, title: '阶段 6', subtitle: '系统增强', status: 'pending' }
]

export const stageDetails: StageDetail[] = [
  { index: 0, title: '环境初始化', percent: 100, date: '2024-05-10', statusLabel: '100%', status: 'done' },
  { index: 1, title: '数据接入',   percent: 100, date: '2024-05-11', statusLabel: '100%', status: 'done' },
  { index: 2, title: '知识检索',   percent: 100, date: '2024-05-12', statusLabel: '100%', status: 'done' },
  { index: 3, title: '报告生成',   percent: 60,  date: '',           statusLabel: '进行中', status: 'current' },
  { index: 4, title: '评估优化',   percent: 0,   date: '',           statusLabel: '待开始', status: 'pending' },
  { index: 5, title: '监控追踪',   percent: 0,   date: '',           statusLabel: '待开始', status: 'pending' },
  { index: 6, title: '系统增强',   percent: 0,   date: '',           statusLabel: '待开始', status: 'pending' }
]

export const currentStage: CurrentStageInfo = {
  index: 3,
  title: '阶段 3 - 报告生成',
  description: '基于检索到的知识与上下文，生成高质量行业研究报告，支持多模态内容与结构化输出。',
  bullets: ['报告大纲生成', '内容生成', '格式化输出', '引用与来源标注']
}

export const statItems: StatItem[] = [
  {
    key: 'docs',
    label: '已接入文档',
    value: '12,842',
    iconName: 'Document',
    iconBg: '#dbeafe',
    delta: { raw: '+532', percent: '↑4.32%', trend: 'up', note: '较昨日' }
  },
  {
    key: 'queries',
    label: 'RAG 查询次数',
    value: '45,723',
    iconName: 'Search',
    iconBg: '#dcfce7',
    delta: { raw: '+2,341', percent: '↑5.39%', trend: 'up', note: '较昨日' }
  },
  {
    key: 'tasks',
    label: '报告任务数',
    value: '156',
    iconName: 'Tickets',
    iconBg: '#ede9fe',
    delta: { raw: '+8', percent: '↑5.41%', trend: 'up', note: '较昨日' }
  },
  {
    key: 'latency',
    label: '平均响应耗时',
    value: '1.82',
    unit: 's',
    iconName: 'Timer',
    iconBg: '#ffedd5',
    delta: { raw: '-0.28s', percent: '↓13.33%', trend: 'down', note: '较昨日' }
  }
]

export const coreCapabilities: CoreCapability[] = [
  {
    key: 'rag',
    title: 'RAG 检索',
    description: '基于向量检索与语义理解，快速定位相关知识与信息。',
    iconName: 'Search',
    iconBg: '#dbeafe',
    link: '/rag'
  },
  {
    key: 'single-agent',
    title: 'Single-Agent 工作流',
    description: '单智能体驱动研究流程，自动规划与任务执行。',
    iconName: 'Cpu',
    iconBg: '#dcfce7',
    link: ''
  },
  {
    key: 'sse',
    title: 'SSE 流式输出',
    description: '基于 Server-Sent Events，实现流式响应与实时展示。',
    iconName: 'Connection',
    iconBg: '#ede9fe',
    link: ''
  },
  {
    key: 'trace',
    title: 'Trace 可观测',
    description: '全链路追踪可视化，提升系统稳定性与可解释性。',
    iconName: 'DataAnalysis',
    iconBg: '#fef3c7',
    link: ''
  }
]

export const recentActivities: ActivityItem[] = [
  {
    id: 'a1',
    title: '用户登录',
    description: '张伟 登录系统',
    time: '09:24:18',
    iconName: 'User',
    iconColor: '#9ca3af'
  },
  {
    id: 'a2',
    title: '报告任务已启动',
    description: '任务《2024 年新能源汽车行业研究》已启动',
    time: '09:15:33',
    iconName: 'VideoPlay',
    iconColor: '#3b82f6'
  },
  {
    id: 'a3',
    title: '报告任务已完成',
    description: '任务《半导体行业分析报告》已完成',
    time: '昨天 17:42',
    iconName: 'CircleCheck',
    iconColor: '#10b981'
  },
  {
    id: 'a4',
    title: 'RAG 检索执行',
    description: '执行查询"新能源电池技术趋势"，返回 23 条结果',
    time: '昨天 16:08',
    iconName: 'Search',
    iconColor: '#8b5cf6'
  },
  {
    id: 'a5',
    title: '文档入库',
    description: '新增文档《全球光伏产业链报告.pdf》',
    time: '昨天 15:21',
    iconName: 'Document',
    iconColor: '#f59e0b'
  }
]
