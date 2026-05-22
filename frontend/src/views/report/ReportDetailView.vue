<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ReportTaskHeader from '@/components/report/ReportTaskHeader.vue'
import SseEventList from '@/components/report/SseEventList.vue'
import MarkdownRenderer from '@/components/report/MarkdownRenderer.vue'
import CitationGrid from '@/components/report/CitationGrid.vue'
import TraceTable from '@/components/report/TraceTable.vue'
import { buildStreamUrl, reportApi } from '@/api/report'
import { useSse, type SseHandle } from '@/composables/useSse'
import type {
  ReportDetail,
  SseEvent,
  TraceRow
} from '@/types/report'

const route = useRoute()
const router = useRouter()

// ===== 路由参数 =====
const taskId = computed(() => Number(route.params.id))

// ===== 主状态 =====
const detail = ref<ReportDetail | null>(null)
const events = ref<SseEvent[]>([])
const streamingMarkdown = ref('')       // 流式累积；done 时被 final 覆盖
const finalMarkdown = ref('')           // done 后的最终稿
const isStreaming = ref(false)
const sseConnected = ref(false)
const activeCitation = ref<number | null>(null)
const traceRows = ref<TraceRow[]>([])
const traceLoading = ref(false)
const autoRefreshTrace = ref(true)
let sseHandle: SseHandle | null = null
let traceTimer: number | null = null

const displayMarkdown = computed(() =>
  finalMarkdown.value || streamingMarkdown.value
)

// ===== 入口：拉初态，决定要不要开 SSE =====
async function bootstrap() {
  if (!taskId.value || Number.isNaN(taskId.value)) {
    ElMessage.error('任务 ID 无效')
    router.replace('/report/submit')
    return
  }

  try {
    const d = await reportApi.get(taskId.value)
    detail.value = d
    finalMarkdown.value = d.finalMarkdown ?? ''
  } catch {
    return
  }

  if (detail.value?.status === 'DONE' || detail.value?.status === 'FAILED') {
    await loadTrace()
    return
  }

  openStream()
}

function openStream() {
  closeStream()
  events.value = []
  streamingMarkdown.value = ''
  isStreaming.value = true
  sseConnected.value = true

  sseHandle = useSse(buildStreamUrl(taskId.value), {
    onNodeStatus: (data) =>
      events.value.push({ type: 'node_status', ts: Date.now(), data }),
    onTool: (data) =>
      events.value.push({ type: 'tool', ts: Date.now(), data }),
    onToken: (data) => {
      events.value.push({ type: 'token', ts: Date.now(), data })
      streamingMarkdown.value += data.delta
      if (detail.value) detail.value.status = 'RUNNING'
    },
    onDone: (data) => {
      events.value.push({ type: 'done', ts: Date.now(), data })
      finalMarkdown.value = data.finalMarkdown
      if (detail.value) {
        detail.value.status = 'DONE'
        detail.value.citations = data.citations
        detail.value.finalMarkdown = data.finalMarkdown
        detail.value.finishedAtEpochMillis = Date.now()
      }
      isStreaming.value = false
      sseConnected.value = false
      void loadTrace()
    },
    onError: (data) => {
      events.value.push({ type: 'error', ts: Date.now(), data })
      if (detail.value) {
        detail.value.status = 'FAILED'
        detail.value.errorMessage = data.message
      }
      isStreaming.value = false
      sseConnected.value = false
      ElMessage.error(data.message)
    },
    onPing: (data) =>
      events.value.push({ type: 'ping', ts: Date.now(), data }),
    onClose: () => {
      sseConnected.value = false
    }
  })
}

function closeStream() {
  sseHandle?.close()
  sseHandle = null
}

async function loadTrace() {
  if (!taskId.value) return
  traceLoading.value = true
  try {
    traceRows.value = await reportApi.trace(taskId.value)
  } catch {
    /* interceptor handles */
  } finally {
    traceLoading.value = false
  }
}

function scheduleTracePoll() {
  clearTracePoll()
  if (!autoRefreshTrace.value) return
  if (detail.value?.status !== 'RUNNING' && detail.value?.status !== 'PENDING') return
  traceTimer = window.setInterval(() => {
    void loadTrace()
  }, 3000)
}

function clearTracePoll() {
  if (traceTimer != null) {
    clearInterval(traceTimer)
    traceTimer = null
  }
}

watch(autoRefreshTrace, scheduleTracePoll)
watch(() => detail.value?.status, (s) => {
  if (s === 'DONE' || s === 'FAILED') {
    clearTracePoll()
    closeStream()
  } else {
    scheduleTracePoll()
  }
})

// ===== 操作 =====
async function onRerun() {
  if (!detail.value) return
  try {
    const resp = await reportApi.start({ topic: detail.value.topic })
    ElMessage.success(`已重新发起任务 task_${String(resp.taskId).padStart(8, '0')}`)
    router.replace(`/report/${resp.taskId}`)
  } catch {
    /* handled */
  }
}

function onDownload() {
  const md = finalMarkdown.value || streamingMarkdown.value
  if (!md) return
  const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `report-${taskId.value}.md`
  a.click()
  URL.revokeObjectURL(url)
}

function onClearEvents() {
  events.value = []
}

function onCiteClick(idx: number) {
  activeCitation.value = idx
}

// ===== 生命周期 =====
watch(taskId, () => {
  closeStream()
  clearTracePoll()
  detail.value = null
  events.value = []
  streamingMarkdown.value = ''
  finalMarkdown.value = ''
  traceRows.value = []
  bootstrap()
}, { immediate: true })

onBeforeUnmount(() => {
  closeStream()
  clearTracePoll()
})
</script>

<template>
  <div class="rd">
    <!-- 顶部 -->
    <ReportTaskHeader
      v-if="detail"
      :detail="detail"
      :rerun-disabled="detail.status === 'PENDING' || detail.status === 'RUNNING'"
      @rerun="onRerun"
      @download="onDownload"
    />
    <div v-else class="rd-loading">加载任务详情…</div>

    <!-- 主区：左 SSE + 右 Markdown -->
    <section v-if="detail" class="rd-main">
      <div class="rd-left">
        <SseEventList
          :events="events"
          :connected="sseConnected"
          @clear="onClearEvents"
        />
      </div>

      <div class="rd-right">
        <header class="rd-md-header">
          <h2 class="rd-md-title">最终报告 Markdown 预览</h2>
          <el-button size="small" plain @click="onDownload" :disabled="!displayMarkdown">
            复制/下载 Markdown
          </el-button>
        </header>

        <div class="rd-md-body">
          <MarkdownRenderer
            v-if="displayMarkdown"
            :source="displayMarkdown"
            :streaming="isStreaming"
            @cite-click="onCiteClick"
          />
          <div v-else class="rd-md-empty">
            <template v-if="detail.status === 'PENDING'">任务排队中，等待 Worker 接管…</template>
            <template v-else-if="detail.status === 'RUNNING'">报告生成中，请稍候…</template>
            <template v-else-if="detail.status === 'FAILED'">
              任务失败：{{ detail.errorMessage || '未知错误' }}
            </template>
            <template v-else>暂无内容</template>
          </div>

          <CitationGrid
            v-if="detail.citations && detail.citations.length > 0"
            :citations="detail.citations"
            :active-index="activeCitation"
          />
        </div>
      </div>
    </section>

    <!-- 底部 Trace 表格 -->
    <TraceTable
      v-if="detail"
      :rows="traceRows"
      :task-id="taskId"
      :loading="traceLoading"
      :auto-refresh="autoRefreshTrace"
      @refresh="loadTrace"
      @update:auto-refresh="(v) => (autoRefreshTrace = v)"
    />
  </div>
</template>

<style scoped>
.rd {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rd-loading {
  text-align: center;
  padding: 80px 0;
  color: var(--text-tertiary);
}

.rd-main {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 16px;
  align-items: stretch;
  min-height: 480px;
}

.rd-left {
  min-height: 0;
}

.rd-right {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.rd-md-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-light);
}

.rd-md-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.rd-md-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.rd-md-empty {
  color: var(--text-tertiary);
  font-size: 13px;
  padding: 40px 0;
  text-align: center;
}

@media (max-width: 1100px) {
  .rd-main {
    grid-template-columns: 1fr;
  }
}
</style>
