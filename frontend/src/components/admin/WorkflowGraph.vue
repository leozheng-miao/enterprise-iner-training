<script setup lang="ts">
import { Document, Search, PieChart, EditPen, CircleCheck, Right } from '@element-plus/icons-vue'

defineProps<{
  nodes: string[]
}>()

const iconMap: Record<string, unknown> = {
  Planner: Document,
  Researcher: Search,
  Analyst: PieChart,
  Writer: EditPen,
  Critic: CircleCheck
}

const subtitle: Record<string, string> = {
  Planner: '规划',
  Researcher: '研究检索',
  Analyst: '分析整理',
  Writer: '撰写报告',
  Critic: '评审优化'
}
</script>

<template>
  <div class="graph">
    <template v-for="(n, i) in nodes" :key="n">
      <div class="node">
        <div class="node-icon">
          <el-icon :size="20"><component :is="iconMap[n] ?? Document" /></el-icon>
        </div>
        <div class="node-name">{{ n }}</div>
        <div class="node-sub">{{ subtitle[n] ?? '' }}</div>
      </div>
      <el-icon v-if="i < nodes.length - 1" class="arrow" :size="18"><Right /></el-icon>
    </template>
  </div>
</template>

<style scoped>
.graph {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.node {
  flex: 1;
  min-width: 90px;
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  padding: 14px 8px;
  text-align: center;
}
.node-icon {
  width: 32px;
  height: 32px;
  margin: 0 auto 6px;
  border-radius: 8px;
  background: rgba(47, 109, 245, 0.08);
  color: var(--color-primary);
  display: grid;
  place-items: center;
}
.node-name { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.node-sub { font-size: 11px; color: var(--text-tertiary); margin-top: 2px; }
.arrow { color: var(--color-primary); }
</style>
