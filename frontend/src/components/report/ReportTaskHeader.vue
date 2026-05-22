<script setup lang="ts">
import { computed } from 'vue'
import { ArrowLeft, Refresh, Download } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import type { ReportDetail, ReportTaskStatus } from '@/types/report'
import {
  formatDurationBetween,
  formatEpochMillis
} from '@/utils/format'

const props = defineProps<{
  detail: ReportDetail
  rerunDisabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'rerun'): void
  (e: 'download'): void
}>()

const router = useRouter()

const statusClass = computed<Record<ReportTaskStatus, string>>(() => ({
  PENDING: 'st-pending',
  RUNNING: 'st-running',
  DONE: 'st-done',
  FAILED: 'st-failed'
}))

const durationText = computed(() =>
  formatDurationBetween(
    props.detail.startedAtEpochMillis,
    props.detail.finishedAtEpochMillis
  )
)

function goBack() {
  router.back()
}
</script>

<template>
  <header class="rh">
    <div class="rh-top">
      <button class="rh-back" type="button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <h1 class="rh-title">研究报告任务详情</h1>
      <div class="rh-actions">
        <el-button :icon="Refresh" :disabled="rerunDisabled" @click="emit('rerun')">
          重新运行
        </el-button>
        <el-button type="primary" :icon="Download" :disabled="!detail.finalMarkdown" @click="emit('download')">
          下载报告
        </el-button>
      </div>
    </div>

    <div class="rh-meta">
      <div class="meta-cell">
        <div class="meta-key">任务 ID</div>
        <div class="meta-val link">task_{{ String(detail.taskId).padStart(8, '0') }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">研究主题</div>
        <div class="meta-val" :title="detail.topic">{{ detail.topic }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">状态</div>
        <div class="meta-val">
          <span class="status-pill" :class="statusClass[detail.status]">{{ detail.status }}</span>
        </div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">启动时间</div>
        <div class="meta-val">{{ formatEpochMillis(detail.startedAtEpochMillis) }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">完成时间</div>
        <div class="meta-val">{{ formatEpochMillis(detail.finishedAtEpochMillis) }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">耗时</div>
        <div class="meta-val">{{ durationText }}</div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.rh {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px 20px;
}

.rh-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.rh-back {
  appearance: none;
  background: transparent;
  border: none;
  font-size: 18px;
  color: var(--text-secondary);
  cursor: pointer;
  display: grid;
  place-items: center;
  padding: 4px;
  border-radius: var(--radius-sm);
}

.rh-back:hover {
  background: var(--bg-muted);
}

.rh-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.rh-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

/* Meta row */
.rh-meta {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  padding: 14px 16px;
  background: var(--bg-muted);
  border-radius: var(--radius-md);
}

.meta-cell {
  min-width: 0;
}

.meta-key {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-bottom: 2px;
}

.meta-val {
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.meta-val.link {
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.status-pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.4px;
}

.status-pill.st-pending {
  background: var(--bg-muted);
  color: var(--text-secondary);
}

.status-pill.st-running {
  background: rgba(47, 109, 245, 0.16);
  color: var(--color-primary);
}

.status-pill.st-done {
  background: rgba(16, 185, 129, 0.18);
  color: #047857;
}

.status-pill.st-failed {
  background: rgba(239, 68, 68, 0.18);
  color: #b91c1c;
}

@media (max-width: 1100px) {
  .rh-meta {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 720px) {
  .rh-meta {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
