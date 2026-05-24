<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import StatCard from '@/components/stat/StatCard.vue'
import StageStepBar from '@/components/stage/StageStepBar.vue'
import StageOverview from '@/components/stage/StageOverview.vue'
import QuickStartCard from '@/components/home/QuickStartCard.vue'
import CoreCapabilityCard from '@/components/home/CoreCapabilityCard.vue'
import RecentActivityList from '@/components/home/RecentActivityList.vue'
import { adminApi } from '@/api/admin'
import {
  coreCapabilities,
  currentStage,
  stageDetails,
  stageNodes
} from '@/mock/dashboard'
import type { ActivityItem, StatItem } from '@/types/dashboard'
import type { PlatformOverviewVO, TaskBriefVO } from '@/types/admin'
import {
  formatCny,
  formatEpochMillis,
  formatNumber,
  formatPercent
} from '@/utils/format'
import { STATUS_LABEL } from '@/utils/taskStatus'

const auth = useAuthStore()

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const displayName = computed(
  () => auth.user?.nickname || auth.user?.username || '访客'
)

const overview = ref<PlatformOverviewVO | null>(null)
const recentTasks = ref<TaskBriefVO[]>([])

onMounted(async () => {
  try {
    const [o, page] = await Promise.all([
      adminApi.overview(),
      adminApi.tasks('', 1, 5)
    ])
    overview.value = o
    recentTasks.value = page.records
  } catch {
    /* 业务错误已由拦截器 toast，此处保留兜底空数据 */
  }
})

const statItems = computed<StatItem[]>(() => {
  const o = overview.value
  if (!o) return []
  return [
    { key: 'total',   label: '总任务数',      value: formatNumber(o.totalTasks),       iconName: 'Document',  iconBg: '#dbeafe' },
    { key: 'success', label: '成功率',        value: formatPercent(o.successRate),     iconName: 'Histogram', iconBg: '#dcfce7' },
    { key: 'cost',    label: '总 Token 成本', value: formatCny(o.totalCostCny),        iconName: 'Money',     iconBg: '#fef3c7' },
    { key: 'p95',     label: 'P95 耗时',      value: formatNumber(o.p95LatencyMs),     unit: 'ms', iconName: 'Timer', iconBg: '#ede9fe' }
  ]
})

const recentActivities = computed<ActivityItem[]>(() =>
  recentTasks.value.map((t) => ({
    id: String(t.id),
    title: t.topic,
    description: `#${t.id} · ${STATUS_LABEL[t.status]} · ${t.phase ?? '—'}`,
    time: formatEpochMillis(t.startedAt),
    iconName: 'VideoPlay',
    iconColor: '#3b82f6'
  }))
)
</script>

<template>
  <div class="home">
    <!-- Hero row：左侧问候，右侧 QuickStart -->
    <section class="hero-row">
      <div class="hero-greeting">
        <h1 class="greeting">{{ greeting }}，{{ displayName }} 👋</h1>
        <p class="subtitle">
          欢迎使用 行业研报多 Agent 协作平台，助力高效研究与智能分析
        </p>
      </div>

      <div class="hero-quickstart">
        <div class="qs-header">快速开始</div>
        <div class="qs-grid">
          <QuickStartCard
            title="开始检索"
            description="RAG 检索"
            icon-name="Search"
            icon-bg="#dbeafe"
            to="/rag"
          />
          <QuickStartCard
            title="发起报告任务"
            description="新建任务"
            icon-name="EditPen"
            icon-bg="#dcfce7"
            to="/report/submit"
          />
          <QuickStartCard
            title="查看 Trace"
            description="追踪链路"
            icon-name="Share"
            icon-bg="#ede9fe"
          />
        </div>
      </div>
    </section>

    <!-- 项目阶段概览 -->
    <StageStepBar :nodes="stageNodes" />

    <!-- 4 统计卡 -->
    <section class="stats-row">
      <StatCard v-for="s in statItems" :key="s.key" :item="s" />
    </section>

    <!-- 两栏：左侧 = 阶段进度总览 + 当前阶段；右侧 = 核心能力 + 最近活动 -->
    <section class="two-col">
      <div class="col-left">
        <StageOverview :details="stageDetails" :current="currentStage" />
      </div>

      <div class="col-right">
        <div class="capability-block">
          <header class="block-header">
            <h3 class="block-title">核心能力</h3>
            <a class="block-link">查看全部 ›</a>
          </header>
          <div class="capability-grid">
            <CoreCapabilityCard
              v-for="c in coreCapabilities"
              :key="c.key"
              :item="c"
            />
          </div>
        </div>

        <RecentActivityList :activities="recentActivities" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Hero ============================================ */
.hero-row {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 20px;
  align-items: stretch;
}

.hero-greeting {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 28px 28px 24px;
}

.greeting {
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 8px;
  color: var(--text-primary);
}

.subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.hero-quickstart {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px;
}

.qs-header {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.qs-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

/* Stats row ======================================= */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

/* Two-col ========================================= */
.two-col {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 20px;
  align-items: start;
}

.col-left {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.col-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.capability-block {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}

.block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.block-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.block-link {
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
}

.capability-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

/* Responsive：1280 以下右栏收掉 */
@media (max-width: 1280px) {
  .hero-row,
  .two-col {
    grid-template-columns: 1fr;
  }
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
