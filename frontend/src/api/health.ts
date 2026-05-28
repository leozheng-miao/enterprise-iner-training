// frontend/src/api/health.ts
//
// F4 #1: 系统健康子服务粒度。
// 路径：/api/health/components，与现有 /api/health 一致，无需 Authorization header。
// （client.ts 的请求拦截器只有 token 存在时才注入 Authorization，端点不强求鉴权。）

import { apiGet } from './client'
import type { ComponentHealthVO } from '@/types/admin'

export const healthApi = {
  /** 系统健康面板用：返回 API / Redis / SSE 三行子服务状态。 */
  components: () => apiGet<ComponentHealthVO[]>('/health/components')
}
