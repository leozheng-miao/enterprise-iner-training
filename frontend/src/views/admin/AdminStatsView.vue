<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Document, Histogram, Money, Timer } from '@element-plus/icons-vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import StatCard from '@/components/stat/StatCard.vue'
import ModelCostBarChart from '@/components/admin/ModelCostBarChart.vue'
import AgentCostDonutChart from '@/components/admin/AgentCostDonutChart.vue'
import RecentModelCallsTable from '@/components/admin/RecentModelCallsTable.vue'
import SystemHealthPanel from '@/components/admin/SystemHealthPanel.vue'
import { adminApi } from '@/api/admin'
import { formatCny, formatNumber, formatPercent } from '@/utils/format'
import type { StatItem } from '@/types/dashboard'
import type {
  AgentCostVO,
  ModelCostVO,
  PlatformOverviewVO
} from '@/types/admin'

const loading = ref(false)
const errorMsg = ref('')
const overview = ref<PlatformOverviewVO | null>(null)
const modelCost = ref<ModelCostVO[]>([])
const agentCost = ref<AgentCostVO[]>([])
const metric = ref<'cost' | 'tokens'>('cost')

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const [o, m, a] = await Promise.all([
      adminApi.overview(),
      adminApi.costByModel(),
      adminApi.costByAgent()
    ])
    overview.value = o
    modelCost.value = m
    agentCost.value = a
  } catch (e) {
    errorMsg.value = (e as Error)?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

const statItems = computed<StatItem[]>(() => {
  const o = overview.value
  if (!o) return []
  return [
    {
      key: 'total',
      label: '总任务数',
      value: formatNumber(o.totalTasks),
      iconName: 'Document',
      iconBg: '#dbeafe'
    },
    {
      key: 'success',
      label: '成功率',
      value: formatPercent(o.taskSuccessRate),
      iconName: 'Histogram',
      iconBg: '#dcfce7'
    },
    {
      key: 'cost',
      label: '总 Token 成本',
      value: formatCny(o.totalCostCny),
      iconName: 'Money',
      iconBg: '#fef3c7'
    },
    {
      key: 'avg',
      label: '平均耗时',
      value: formatNumber(o.avgTaskLatencyMs),
      unit: 'ms',
      iconName: 'Timer',
      iconBg: '#ede9fe'
    }
  ]
})

// 显式引用以让 vue-tsc 不把图标当未使用导入
void Document; void Histogram; void Money; void Timer
</script>

<template>
  <div v-loading="loading" class="admin-stats">
    <AdminPageHeader
      title="平台统计"
      subtitle="平台任务、Token 成本与端到端耗时总览。"
    />

    <el-alert
      v-if="errorMsg"
      :title="errorMsg"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    >
      <template #default>
        <span>{{ errorMsg }}</span>
        <el-button type="primary" link size="small" @click="load">重试</el-button>
      </template>
    </el-alert>

    <section class="stats-row">
      <StatCard v-for="s in statItems" :key="s.key" :item="s" />
    </section>

    <section class="charts-row">
      <div class="card">
        <header class="card-header">
          <h3>按模型 Token 成本</h3>
          <el-radio-group v-model="metric" size="small">
            <el-radio-button value="cost">成本（元）</el-radio-button>
            <el-radio-button value="tokens">Token 数</el-radio-button>
          </el-radio-group>
        </header>
        <ModelCostBarChart :data="modelCost" :metric="metric" />
      </div>

      <div class="card">
        <header class="card-header">
          <h3>Agent 成本占比</h3>
        </header>
        <AgentCostDonutChart :data="agentCost" />
      </div>
    </section>

    <section class="bottom-row">
      <RecentModelCallsTable :data="modelCost" />
      <SystemHealthPanel />
    </section>
  </div>
</template>

<style scoped>
.admin-stats { display: flex; flex-direction: column; gap: 16px; }
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.charts-row, .bottom-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.card-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
@media (max-width: 1280px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .charts-row, .bottom-row { grid-template-columns: 1fr; }
}
</style>
