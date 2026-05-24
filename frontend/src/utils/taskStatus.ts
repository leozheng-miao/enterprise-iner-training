// frontend/src/utils/taskStatus.ts
//
// TaskStatus -> Element Plus el-tag 颜色 / 中文标签 / progress 状态 的映射。

import type { TaskStatus } from '@/types/admin'

export type ElTagType = 'info' | 'success' | 'warning' | 'danger'

export const STATUS_TAG_TYPE: Record<TaskStatus, ElTagType> = {
  PENDING: 'info',
  RUNNING: 'warning',
  DONE: 'success',
  FAILED: 'danger'
}

export const STATUS_LABEL: Record<TaskStatus, string> = {
  PENDING: 'PENDING',
  RUNNING: 'RUNNING',
  DONE: 'DONE',
  FAILED: 'FAILED'
}

/** el-progress 的 status 映射（FAILED 红、DONE 绿、其他默认蓝）。 */
export function progressStatus(s: TaskStatus): 'success' | 'exception' | undefined {
  if (s === 'DONE') return 'success'
  if (s === 'FAILED') return 'exception'
  return undefined
}
