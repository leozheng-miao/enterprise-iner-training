<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, type ECharts } from 'echarts/core'
import { useResizeObserver } from '@vueuse/core'
import { chartPalette } from '@/utils/echarts'

interface DonutDatum {
  name: string
  value: number
}

const props = defineProps<{
  data: DonutDatum[]
  centerLabel?: string
}>()

const root = ref<HTMLElement>()
let chart: ECharts | null = null

function buildOption(data: DonutDatum[], centerLabel?: string) {
  const total = data.reduce((acc, d) => acc + d.value, 0)
  return {
    color: chartPalette,
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 0,
      top: 'center',
      itemWidth: 8,
      itemHeight: 8,
      icon: 'circle',
      textStyle: { fontSize: 12, color: '#4b5563' },
      formatter: (name: string) => {
        const d = data.find((x) => x.name === name)
        const pct = d && total ? ((d.value / total) * 100).toFixed(1) : '0.0'
        return `${name}  ${d?.value ?? 0} (${pct}%)`
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['55%', '75%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        label: {
          show: !!centerLabel,
          position: 'center',
          formatter: () => `{a|${total}}\n{b|${centerLabel ?? ''}}`,
          rich: {
            a: { fontSize: 22, fontWeight: 600, color: '#1f2937' },
            b: { fontSize: 12, color: '#9ca3af', padding: [4, 0, 0, 0] }
          }
        },
        labelLine: { show: false },
        data
      }
    ]
  }
}

function render() {
  if (!chart) return
  chart.setOption(buildOption(props.data, props.centerLabel), true)
}

onMounted(() => {
  if (!root.value) return
  chart = init(root.value)
  render()
  useResizeObserver(root, () => chart?.resize())
})

watch(
  () => [props.data, props.centerLabel],
  () => render(),
  { deep: true }
)

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="root" class="donut-chart" />
</template>

<style scoped>
.donut-chart {
  width: 100%;
  height: 220px;
}
</style>
