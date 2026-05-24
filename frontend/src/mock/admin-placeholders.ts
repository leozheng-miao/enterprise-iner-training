// frontend/src/mock/admin-placeholders.ts
//
// 设计稿里超出后端能力的 UI 元素的 mock 数据，集中在此处便于将来替换。
// 每个常量对应一处后端 TODO，见 docs/superpowers/handoff/2026-05-24-backend-api-gaps.md

export interface HealthRow {
  name: string
  subtitle: string
  status: 'UP' | 'DOWN'
  metric: string                // 例如 '响应时间 1.2 ms'，由前端拼好
  iconName: 'CoffeeCup' | 'Connection' | 'CloudFilled'  // Element Plus 图标名
}

// TODO(backend-api-gap #1): Stats 系统健康面板的子服务条目。
// 真实接口落地后改为 GET /api/health/components 拉取。
export const mockSystemHealthExtras: HealthRow[] = [
  {
    name: 'Redis 缓存',
    subtitle: '缓存与会话存储',
    status: 'UP',
    metric: '响应时间 1.2 ms',
    iconName: 'CoffeeCup'
  },
  {
    name: 'SSE 服务',
    subtitle: '流式推送服务',
    status: 'UP',
    metric: '连接数 12',
    iconName: 'Connection'
  }
]

// TODO(backend-api-gap #4): Workflow 当前活跃元信息。
// 真实接口落地后改为 GET /api/admin/workflow/active 拉取。
export interface ActiveWorkflowInfo {
  name: string
  version: string
  file: string
  nodes: string[]
  lastLoadedAt: string          // 已格式化的字符串
  cached: boolean
}

export const mockActiveWorkflow: ActiveWorkflowInfo = {
  name: 'multi_agent_v1',
  version: 'v2',
  file: 'classpath:workflow/multi_agent_v1.yaml',
  nodes: ['Planner', 'Researcher', 'Analyst', 'Writer', 'Critic'],
  lastLoadedAt: '2026-05-24 09:00:00',
  cached: true
}

// TODO(backend-api-gap #5): Workflow 加载日志时间轴。
// 真实接口落地后改为 GET /api/admin/workflow/history?limit=20 拉取。
export interface WorkflowLogEntry {
  time: string                  // 已格式化 HH:mm:ss
  event: string
  detail: string
  level: 'info' | 'success' | 'warning'
}

export const mockWorkflowLoadLog: WorkflowLogEntry[] = [
  { time: '09:00:00', event: '缓存清空', detail: '已清空 Workflow YAML 缓存，准备重新加载', level: 'info' },
  { time: '09:00:00', event: 'YAML 重新加载', detail: '成功加载 classpath:workflow/multi_agent_v1.yaml (v2)', level: 'success' },
  { time: '09:00:00', event: '节点校验通过', detail: '共 5 个节点，5 条边，依赖完整，拓扑有效', level: 'success' },
  { time: '09:00:00', event: '工作流生效', detail: 'Workflow multi_agent_v1(v2) 已成功生效，等待下次任务使用', level: 'info' }
]
