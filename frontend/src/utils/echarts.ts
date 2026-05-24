import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart, RadarChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  RadarComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components'

/**
 * 一次性注册项目里用到的所有 ECharts 模块。
 * main.ts 不需要 import 这个；由具体使用的图表组件在 onMounted 之前 import 此文件即可。
 */
use([
  CanvasRenderer,
  PieChart,
  BarChart,
  RadarChart,
  GridComponent,
  LegendComponent,
  RadarComponent,
  TitleComponent,
  TooltipComponent
])

/** 项目调色板（与 variables.css 对齐）。 */
export const chartPalette = [
  '#2f6df5',
  '#10b981',
  '#8b5cf6',
  '#f59e0b',
  '#ef4444',
  '#06b6d4'
]
