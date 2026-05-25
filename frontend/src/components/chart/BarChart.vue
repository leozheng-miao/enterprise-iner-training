<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, type ECharts } from 'echarts/core'
import { useResizeObserver } from '@vueuse/core'
import { chartPalette } from '@/utils/echarts'

interface BarDatum {
  label: string
  value: number
}

const props = defineProps<{
  data: BarDatum[]
  barColor?: string
}>()

const root = ref<HTMLElement>()
let chart: ECharts | null = null

function buildOption(data: BarDatum[], barColor?: string) {
  return {
    color: chartPalette,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 36, right: 12, top: 24, bottom: 28 },
    xAxis: {
      type: 'category',
      data: data.map((d) => d.label),
      axisLabel: { color: '#9ca3af', fontSize: 11 },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#9ca3af', fontSize: 11 },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f1f3f8' } }
    },
    series: [
      {
        type: 'bar',
        data: data.map((d) => d.value),
        itemStyle: {
          color: barColor ?? '#2f6df5',
          borderRadius: [6, 6, 0, 0]
        },
        barMaxWidth: 28,
        label: {
          show: true,
          position: 'top',
          color: '#4b5563',
          fontSize: 11
        }
      }
    ]
  }
}

function render() {
  if (!chart) return
  chart.setOption(buildOption(props.data, props.barColor), true)
}

// 容器尺寸变化时，先 resize（重画 canvas），再延后一帧 re-setOption（强制 ECharts 重新计算 layout）。
// 没有 re-setOption 时，缩窄 → 复原后 ECharts 的 bar layout 状态可能保持压缩态，柱子看起来被拉长。
let resizeTimer: number | null = null
function onResize() {
  chart?.resize()
  if (resizeTimer !== null) window.clearTimeout(resizeTimer)
  resizeTimer = window.setTimeout(() => {
    render()
    resizeTimer = null
  }, 120)
}

onMounted(() => {
  if (!root.value) return
  chart = init(root.value)
  render()
  useResizeObserver(root, onResize)
})

watch(
  () => [props.data, props.barColor],
  () => render(),
  { deep: true }
)

onBeforeUnmount(() => {
  if (resizeTimer !== null) window.clearTimeout(resizeTimer)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="root" class="bar-chart" />
</template>

<style scoped>
.bar-chart {
  width: 100%;
  height: 180px;
}
</style>
