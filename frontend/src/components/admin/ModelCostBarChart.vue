<script setup lang="ts">
import { computed } from 'vue'
import BarChart from '@/components/chart/BarChart.vue'
import type { ModelCostVO } from '@/types/admin'

const props = defineProps<{
  data: ModelCostVO[]
  metric: 'cost' | 'tokens'   // 'cost' 显示成本（元）；'tokens' 显示 token 数
}>()

const barData = computed(() =>
  props.data.map((m) => ({
    label: m.model,
    value: props.metric === 'cost' ? m.costCny : m.tokensIn + m.tokensOut
  }))
)
</script>

<template>
  <div class="model-cost-bar">
    <BarChart :data="barData" />
  </div>
</template>

<style scoped>
.model-cost-bar {
  width: 100%;
}
</style>
