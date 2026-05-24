// frontend/src/mock/dashboard.ts
//
// Phase F1 首页 dashboard 的项目阶段 / 核心能力 mock 数据。
// F3 起，平台运行时数据（statItems / recentActivities）改由 /api/admin/* 提供，
// 本文件只保留项目阶段叙事内容（项目展示文案，与运行时无关）。

import type {
  CoreCapability,
  CurrentStageInfo,
  StageDetail,
  StageNode
} from '@/types/dashboard'

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
