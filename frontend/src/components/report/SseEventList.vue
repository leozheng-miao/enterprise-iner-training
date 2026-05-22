<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { SseEvent } from '@/types/report'
import { formatClock } from '@/utils/format'

const props = defineProps<{
  events: SseEvent[]
  connected: boolean
  autoScroll?: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-pause'): void
  (e: 'clear'): void
}>()

const paused = ref(false)
function onTogglePause() {
  paused.value = !paused.value
  emit('toggle-pause')
}

function onClear() {
  emit('clear')
}

const listEl = ref<HTMLElement>()
const wantScroll = computed(() => props.autoScroll !== false)

watch(
  () => props.events.length,
  async () => {
    if (!wantScroll.value || paused.value) return
    await nextTick()
    if (listEl.value) {
      listEl.value.scrollTop = listEl.value.scrollHeight
    }
  }
)

function badgeLabel(e: SseEvent): string {
  switch (e.type) {
    case 'node_status':
      return 'node_status'
    case 'tool':
      return 'tool'
    case 'token':
      return 'token'
    case 'phase_changed':
      return 'phase'
    case 'section_done':
      return 'section'
    case 'done':
      return 'done'
    case 'error':
      return 'error'
    case 'ping':
      return 'ping'
  }
}

function badgeClass(e: SseEvent): string {
  return `badge badge-${e.type}`
}

function eventSummary(e: SseEvent): string {
  switch (e.type) {
    case 'node_status':
      return `${e.data.nodeId} 节点状态: ${e.data.status}`
    case 'tool': {
      const params = e.data.paramsJson ? ` ${truncate(e.data.paramsJson, 60)}` : ''
      return `调用工具 ${e.data.toolName}${params}`
    }
    case 'token':
      return truncate(e.data.delta, 80)
    case 'phase_changed':
      return `阶段切换 → ${e.data.phase}（${e.data.progress}%）`
    case 'section_done':
      return `章节完成 #${e.data.order}：${e.data.title}`
    case 'done':
      return '报告生成完成'
    case 'error':
      return `错误：${e.data.message}`
    case 'ping':
      return `ping ts=${e.data.ts}`
  }
}

function truncate(s: string, n: number): string {
  if (!s) return ''
  return s.length > n ? `${s.slice(0, n)}…` : s
}
</script>

<template>
  <section class="sse-panel">
    <header class="sse-header">
      <h3 class="sse-title">SSE 实时流</h3>
      <span class="sse-conn" :class="{ on: connected }">
        <span class="dot" />
        {{ connected ? '连接中' : '已断开' }}
      </span>
      <div class="sse-actions">
        <el-button size="small" plain @click="onTogglePause">
          {{ paused ? '继续' : '暂停' }}
        </el-button>
        <el-button size="small" plain @click="onClear">清空</el-button>
      </div>
    </header>

    <ul ref="listEl" class="sse-list">
      <li v-for="(e, idx) in events" :key="idx" class="sse-item">
        <span class="sse-time">{{ formatClock(e.ts) }}</span>
        <span :class="badgeClass(e)">{{ badgeLabel(e) }}</span>
        <span class="sse-summary">{{ eventSummary(e) }}</span>
      </li>
      <li v-if="events.length === 0" class="sse-empty">暂无事件</li>
    </ul>

    <footer class="sse-footer">
      <span v-if="wantScroll && !paused" class="hint">已自动滚动到底部</span>
      <span v-else class="hint muted">滚动已暂停</span>
    </footer>
  </section>
</template>

<style scoped>
.sse-panel {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.sse-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-light);
}

.sse-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.sse-conn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.sse-conn .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-tertiary);
}

.sse-conn.on {
  color: var(--color-success);
}

.sse-conn.on .dot {
  background: var(--color-success);
}

.sse-actions {
  margin-left: auto;
  display: flex;
  gap: 6px;
}

.sse-list {
  list-style: none;
  margin: 0;
  padding: 8px 12px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.sse-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 0;
  font-size: 12px;
  border-bottom: 1px dashed var(--border-light);
}

.sse-item:last-child {
  border-bottom: none;
}

.sse-empty {
  list-style: none;
  text-align: center;
  color: var(--text-tertiary);
  padding: 24px 0;
  font-size: 12px;
}

.sse-time {
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.badge {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}

.badge-node_status {
  background: rgba(47, 109, 245, 0.12);
  color: var(--color-primary);
}

.badge-tool {
  background: rgba(245, 158, 11, 0.18);
  color: #b45309;
}

.badge-token {
  background: rgba(99, 102, 241, 0.15);
  color: #4338ca;
}

.badge-phase_changed {
  background: rgba(6, 182, 212, 0.16);
  color: #0e7490;
}

.badge-section_done {
  background: rgba(139, 92, 246, 0.15);
  color: #6d28d9;
}

.badge-done {
  background: rgba(16, 185, 129, 0.18);
  color: #047857;
}

.badge-error {
  background: rgba(239, 68, 68, 0.18);
  color: #b91c1c;
}

.badge-ping {
  background: var(--bg-muted);
  color: var(--text-tertiary);
}

.sse-summary {
  color: var(--text-secondary);
  word-break: break-word;
  min-width: 0;
}

.sse-footer {
  padding: 10px 16px;
  border-top: 1px solid var(--border-light);
  text-align: center;
}

.hint {
  font-size: 12px;
  color: var(--color-primary);
}

.hint.muted {
  color: var(--text-tertiary);
}
</style>
