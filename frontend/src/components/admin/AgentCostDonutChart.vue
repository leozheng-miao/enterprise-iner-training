<script setup lang="ts">
import { computed } from 'vue'
import DonutChart from '@/components/chart/DonutChart.vue'
import type { AgentCostVO } from '@/types/admin'
import { formatCny } from '@/utils/format'

const props = defineProps<{
  data: AgentCostVO[]
}>()

const donutData = computed(() =>
  props.data.map((a) => ({ name: a.agentRole, value: a.costCny }))
)

const totalLabel = computed(() => {
  const total = props.data.reduce((acc, a) => acc + a.costCny, 0)
  return `总成本 ${formatCny(total)}`
})
</script>

<template>
  <DonutChart :data="donutData" :center-label="totalLabel" />
</template>
