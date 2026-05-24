<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, type ECharts } from 'echarts/core'
import { useResizeObserver } from '@vueuse/core'
import '@/utils/echarts'
import type { JudgeRunVO } from '@/types/admin'

const props = defineProps<{
  run: JudgeRunVO
}>()

const root = ref<HTMLElement>()
let chart: ECharts | null = null

function build(run: JudgeRunVO) {
  return {
    color: ['#2f6df5'],
    tooltip: {},
    radar: {
      indicator: [
        { name: '结构', max: 10 },
        { name: '事实', max: 10 },
        { name: '推理', max: 10 },
        { name: '引用', max: 10 },
        { name: '清晰度', max: 10 }
      ],
      splitArea: { areaStyle: { color: ['#f9fafc', '#fff'] } },
      axisName: { color: '#4b5563', fontSize: 12 }
    },
    series: [
      {
        type: 'radar',
        areaStyle: { opacity: 0.18 },
        lineStyle: { width: 2 },
        data: [
          {
            value: [run.structure, run.factuality, run.reasoning, run.citation, run.clarity],
            name: `#${run.taskId}`
          }
        ]
      }
    ]
  }
}

function render() {
  if (!chart || !props.run) return
  chart.setOption(build(props.run), true)
}

onMounted(() => {
  if (!root.value) return
  chart = init(root.value)
  render()
  useResizeObserver(root, () => chart?.resize())
})

watch(() => props.run, () => render(), { deep: true })

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="root" class="radar" />
</template>

<style scoped>
.radar { width: 100%; height: 260px; }
</style>
