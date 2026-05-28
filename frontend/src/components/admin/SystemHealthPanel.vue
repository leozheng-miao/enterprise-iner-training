<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Cloudy, CoffeeCup, Connection } from '@element-plus/icons-vue'
import { healthApi } from '@/api/health'
import type { ComponentHealthVO } from '@/types/admin'

const components = ref<ComponentHealthVO[]>([])

// name → 图标 / 背景色映射（API/Redis/SSE 三档样式）
const VISUAL = {
  API:   { icon: Cloudy,     bg: '#e0f2fe', color: '#0284c7' },
  Redis: { icon: CoffeeCup,  bg: '#fee2e2', color: '#dc2626' },
  SSE:   { icon: Connection, bg: '#ede9fe', color: '#7c3aed' }
} as const

function visualOf(name: string) {
  return VISUAL[name as keyof typeof VISUAL] ?? VISUAL.API
}

/** 状态 → el-tag type 映射 */
function statusType(s: ComponentHealthVO['status']) {
  if (s === 'UP') return 'success'
  if (s === 'DEGRADED') return 'warning'
  return 'danger'
}
function statusLabel(s: ComponentHealthVO['status']) {
  if (s === 'UP') return '正常'
  if (s === 'DEGRADED') return '降级'
  return '异常'
}

/** 拼右下角的小副文，例如 "响应时间 1.2 ms" / "连接数 12" / "—" */
function metricOf(row: ComponentHealthVO): string {
  const conns = row.extra && typeof row.extra.connections === 'number'
    ? (row.extra.connections as number)
    : null
  if (conns != null) return `连接数 ${conns}`
  if (row.latencyMs == null) return '—'
  return `响应时间 ${row.latencyMs.toFixed(1)} ms`
}

async function poll() {
  try {
    components.value = await healthApi.components()
  } catch {
    // 接口完全不可达时给一个本地兜底（API DOWN）
    components.value = [
      { name: 'API', status: 'DOWN', subtitle: '后端接口服务', latencyMs: null, extra: null }
    ]
  }
}

let timer: number | null = null
onMounted(() => {
  poll()
  timer = window.setInterval(poll, 60_000)
})
onBeforeUnmount(() => {
  if (timer !== null) window.clearInterval(timer)
})
</script>

<template>
  <div class="card">
    <header class="card-header">
      <h3>系统健康</h3>
    </header>

    <div v-for="row in components" :key="row.name" class="row">
      <div class="row-icon" :style="{ background: visualOf(row.name).bg, color: visualOf(row.name).color }">
        <el-icon :size="20"><component :is="visualOf(row.name).icon" /></el-icon>
      </div>
      <div class="row-body">
        <div class="row-name">{{ row.name }} 服务</div>
        <div class="row-sub">{{ row.subtitle }}</div>
      </div>
      <div class="row-status">
        <el-tag :type="statusType(row.status)" size="small">
          {{ statusLabel(row.status) }}
        </el-tag>
        <div class="row-metric">{{ metricOf(row) }}</div>
      </div>
    </div>

    <el-empty v-if="components.length === 0" description="加载中…" :image-size="60" />
  </div>
</template>

<style scoped>
.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}
.card-header {
  margin-bottom: 12px;
}
.card-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid var(--border-light, #eef2f7);
}
.row:first-of-type { border-top: none; }
.row-icon {
  width: 36px; height: 36px;
  border-radius: 8px;
  display: grid; place-items: center;
}
.row-body { flex: 1; min-width: 0; }
.row-name { font-size: 13px; font-weight: 500; color: var(--text-primary); }
.row-sub { font-size: 12px; color: var(--text-tertiary); }
.row-status { text-align: right; }
.row-metric { font-size: 12px; color: var(--text-tertiary); margin-top: 4px; }
</style>
