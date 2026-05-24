<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Cloudy, CoffeeCup, Connection } from '@element-plus/icons-vue'
import { apiGet } from '@/api/client'
import { mockSystemHealthExtras } from '@/mock/admin-placeholders'

interface HealthApiRow {
  status: string
  time: string
}

const apiLatencyMs = ref<number | null>(null)
const apiStatus = ref<'UP' | 'DOWN'>('UP')

const iconMap = {
  CoffeeCup,
  Connection,
  CloudFilled: Cloudy
} as const

async function pingApi() {
  const start = performance.now()
  try {
    await apiGet<HealthApiRow>('/health')
    apiLatencyMs.value = Math.round(performance.now() - start)
    apiStatus.value = 'UP'
  } catch {
    apiLatencyMs.value = null
    apiStatus.value = 'DOWN'
  }
}

let timer: number | null = null
onMounted(() => {
  pingApi()
  timer = window.setInterval(pingApi, 60_000)
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

    <div class="row">
      <div class="row-icon api">
        <el-icon :size="20"><Cloudy /></el-icon>
      </div>
      <div class="row-body">
        <div class="row-name">API 服务</div>
        <div class="row-sub">后端接口服务</div>
      </div>
      <div class="row-status">
        <el-tag :type="apiStatus === 'UP' ? 'success' : 'danger'" size="small">
          {{ apiStatus === 'UP' ? '正常' : '异常' }}
        </el-tag>
        <div class="row-metric">
          响应时间 {{ apiLatencyMs == null ? '—' : `${apiLatencyMs} ms` }}
        </div>
      </div>
    </div>

    <!-- TODO(backend-api-gap #1): 以下两条为 mock 子服务，待 /api/health/components 上线后接入 -->
    <div v-for="row in mockSystemHealthExtras" :key="row.name" class="row">
      <div class="row-icon" :class="row.iconName">
        <el-icon :size="20"><component :is="iconMap[row.iconName]" /></el-icon>
      </div>
      <div class="row-body">
        <div class="row-name">{{ row.name }}</div>
        <div class="row-sub">{{ row.subtitle }}</div>
      </div>
      <div class="row-status">
        <el-tag :type="row.status === 'UP' ? 'success' : 'danger'" size="small">
          {{ row.status === 'UP' ? '正常' : '异常' }}
        </el-tag>
        <div class="row-metric">{{ row.metric }}</div>
      </div>
    </div>
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
  background: #e0f2fe;
  color: #0284c7;
}
.row-icon.CoffeeCup { background: #fee2e2; color: #dc2626; }
.row-icon.Connection { background: #ede9fe; color: #7c3aed; }
.row-body { flex: 1; min-width: 0; }
.row-name { font-size: 13px; font-weight: 500; color: var(--text-primary); }
.row-sub { font-size: 12px; color: var(--text-tertiary); }
.row-status { text-align: right; }
.row-metric { font-size: 12px; color: var(--text-tertiary); margin-top: 4px; }
</style>
