<script setup lang="ts">
import { computed, ref } from 'vue'
import { Download, Refresh } from '@element-plus/icons-vue'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-light.css'
import type { TraceRow } from '@/types/report'

const props = defineProps<{
  rows: TraceRow[]
  /** 任务 ID 用于导出文件名 */
  taskId: number
  loading?: boolean
  autoRefresh: boolean
}>()

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'update:autoRefresh', v: boolean): void
}>()

// ===== 过滤 =====
const nodeFilter = ref<string>('')

const nodeOptions = computed(() => {
  const set = new Set<string>()
  for (const r of props.rows) set.add(r.nodeId)
  return Array.from(set)
})

const filteredRows = computed(() => {
  if (!nodeFilter.value) return props.rows
  return props.rows.filter((r) => r.nodeId === nodeFilter.value)
})

// ===== 导出 JSON =====
function exportJson() {
  const blob = new Blob([JSON.stringify(props.rows, null, 2)], {
    type: 'application/json'
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `trace-${props.taskId}.json`
  a.click()
  URL.revokeObjectURL(url)
}

function tryHighlightJson(text: string | null): string {
  if (!text) return '<span style="color:var(--text-tertiary);">（无）</span>'
  try {
    // 后端可能已经截断成 "..." 结尾，hljs 也能处理
    return hljs.highlight(text, { language: 'json', ignoreIllegals: true }).value
  } catch {
    return text
  }
}

function tagType(t: TraceRow['stepType']): 'primary' | 'success' {
  return t === 'LLM_CALL' ? 'primary' : 'success'
}

function statusTagType(s: TraceRow['status']): 'success' | 'danger' {
  return s === 'OK' ? 'success' : 'danger'
}
</script>

<template>
  <section class="trace-wrap">
    <header class="tw-header">
      <h3 class="tw-title">Trace 时间轴</h3>
      <div class="tw-actions">
        <el-select
          v-model="nodeFilter"
          placeholder="全部节点"
          clearable
          style="width: 160px;"
          size="small"
        >
          <el-option label="全部节点" value="" />
          <el-option v-for="n in nodeOptions" :key="n" :label="n" :value="n" />
        </el-select>
        <el-button size="small" :icon="Download" @click="exportJson">
          导出 Trace (JSON)
        </el-button>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="emit('refresh')">
          刷新
        </el-button>
        <span class="tw-toggle">
          自动刷新
          <el-switch
            :model-value="autoRefresh"
            size="small"
            @update:model-value="(v: string | number | boolean) => emit('update:autoRefresh', v as boolean)"
          />
        </span>
      </div>
    </header>

    <el-table
      :data="filteredRows"
      stripe
      size="small"
      style="width: 100%;"
      header-cell-class-name="tw-cell"
    >
      <el-table-column type="expand">
        <template #default="{ row }: { row: TraceRow }">
          <div class="tw-expand">
            <div class="tw-expand-block">
              <div class="tw-expand-title">Input preview</div>
              <pre class="hljs"><code v-html="tryHighlightJson(row.inputJsonPreview)" /></pre>
            </div>
            <div class="tw-expand-block">
              <div class="tw-expand-title">Output preview</div>
              <pre class="hljs"><code v-html="tryHighlightJson(row.outputJsonPreview)" /></pre>
            </div>
            <div v-if="row.errorMessage" class="tw-expand-block">
              <div class="tw-expand-title err">Error</div>
              <pre class="err-text">{{ row.errorMessage }}</pre>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="stepSeq" label="stepSeq" width="80" />
      <el-table-column prop="nodeId" label="nodeId" width="120" />
      <el-table-column prop="agentRole" label="agentRole" width="140" />
      <el-table-column label="stepType" width="120">
        <template #default="{ row }: { row: TraceRow }">
          <el-tag size="small" :type="tagType(row.stepType)">{{ row.stepType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="model / toolName" min-width="160">
        <template #default="{ row }: { row: TraceRow }">
          {{ row.model || row.toolName || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="tokensIn" label="tokensIn" width="90" align="right" />
      <el-table-column prop="tokensOut" label="tokensOut" width="100" align="right" />
      <el-table-column prop="latencyMs" label="latencyMs" width="100" align="right" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }: { row: TraceRow }">
          <el-tag size="small" :type="statusTagType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>

      <template #empty>
        <div class="tw-empty">暂无 Trace 数据。任务完成后会自动加载。</div>
      </template>
    </el-table>
  </section>
</template>

<style scoped>
.trace-wrap {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px 20px;
}

.tw-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.tw-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.tw-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.tw-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.tw-expand {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 12px 12px 48px;
}

.tw-expand-block {
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  padding: 10px 12px;
}

.tw-expand-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.tw-expand-title.err {
  color: var(--color-danger);
}

.tw-expand pre {
  margin: 0;
  font-size: 12px;
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  white-space: pre-wrap;
  word-break: break-word;
}

.err-text {
  color: var(--color-danger);
}

.tw-empty {
  color: var(--text-tertiary);
  font-size: 12px;
  padding: 16px 0;
}
</style>
