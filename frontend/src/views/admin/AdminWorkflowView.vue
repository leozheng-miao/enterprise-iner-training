<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Document, Refresh, WarningFilled } from '@element-plus/icons-vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import WorkflowGraph from '@/components/admin/WorkflowGraph.vue'
import QueryRewritePanel from '@/components/admin/QueryRewritePanel.vue'
import { adminApi } from '@/api/admin'
import {
  mockActiveWorkflow,
  mockWorkflowLoadLog
} from '@/mock/admin-placeholders'

interface ReloadResult {
  status: 'success' | 'fail'
  latencyMs: number
  finishedAt: string
  message: string
}

const reloading = ref(false)
const lastResult = ref<ReloadResult | null>(null)
const logRef = ref<HTMLElement | null>(null)

async function reload() {
  reloading.value = true
  const start = performance.now()
  try {
    const msg = await adminApi.reloadWorkflow()
    lastResult.value = {
      status: 'success',
      latencyMs: Math.round(performance.now() - start),
      finishedAt: new Date().toLocaleString('zh-CN'),
      message: msg ?? '下次任务将使用最新 YAML'
    }
    ElMessage.success('Workflow 缓存已清空，下次任务将使用最新 YAML')
  } catch (e) {
    lastResult.value = {
      status: 'fail',
      latencyMs: Math.round(performance.now() - start),
      finishedAt: new Date().toLocaleString('zh-CN'),
      message: (e as Error)?.message || '热更新失败'
    }
  } finally {
    reloading.value = false
  }
}

function scrollToLog() {
  logRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <div class="admin-workflow">
    <AdminPageHeader
      title="Workflow 管理"
      subtitle="清空 Workflow YAML 缓存、测试 Query Rewrite，确保下次任务使用最新编排配置。"
    />

    <section class="top-row">
      <!-- 当前活跃 Workflow -->
      <div class="card">
        <header class="card-header">
          <h3>当前活跃 Workflow</h3>
        </header>
        <!-- TODO(backend-api-gap #4): 待 /admin/workflow/active 上线后改为接口 -->
        <div class="info-grid">
          <div><span>Workflow</span><b>{{ mockActiveWorkflow.name }}</b></div>
          <div><span>版本</span><b>{{ mockActiveWorkflow.version }}</b></div>
          <div><span>文件</span><b class="mono">{{ mockActiveWorkflow.file }}</b></div>
          <div><span>节点</span><b>{{ mockActiveWorkflow.nodes.join(' → ') }}</b></div>
          <div><span>最近加载</span><b>{{ mockActiveWorkflow.lastLoadedAt }}</b></div>
          <div>
            <span>缓存状态</span>
            <el-tag size="small" type="success">{{ mockActiveWorkflow.cached ? '已缓存' : '未缓存' }}</el-tag>
          </div>
        </div>

        <WorkflowGraph :nodes="mockActiveWorkflow.nodes" style="margin: 16px 0" />

        <el-alert
          type="warning"
          :icon="WarningFilled"
          :closable="false"
          show-icon
          title="修改 workflow/*.yaml 后点击下方按钮清空缓存，无需重启服务。"
        />

        <div class="action-row">
          <el-button
            type="primary"
            size="large"
            :icon="Refresh"
            :loading="reloading"
            style="flex: 1"
            @click="reload"
          >清空 Workflow 缓存并热更新</el-button>
          <el-button
            size="large"
            :icon="Document"
            style="flex: 1"
            @click="scrollToLog"
          >查看完整日志</el-button>
        </div>
      </div>

      <!-- 右侧：热更新结果 + Query Rewrite 测试 -->
      <div class="right-col">
        <div class="card">
          <header class="card-header">
            <h3>热更新结果</h3>
          </header>
          <div v-if="!lastResult" class="empty">本次会话尚未触发热更新</div>
          <div v-else class="info-grid">
            <div><span>上次操作</span><b>清缓存并刷新</b></div>
            <div>
              <span>结果</span>
              <el-tag :type="lastResult.status === 'success' ? 'success' : 'danger'" size="small">
                <el-icon style="margin-right: 2px"><CircleCheck /></el-icon>
                {{ lastResult.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </div>
            <div><span>耗时</span><b>{{ lastResult.latencyMs }} ms</b></div>
            <div><span>说明</span><b>{{ lastResult.message }}</b></div>
            <div><span>完成时间</span><b>{{ lastResult.finishedAt }}</b></div>
          </div>
        </div>

        <QueryRewritePanel />
      </div>
    </section>

    <!-- 最近加载日志（mock） -->
    <div ref="logRef" class="card">
      <header class="card-header">
        <h3>最近加载日志</h3>
        <a class="link">查看完整日志 ›</a>
      </header>
      <!-- TODO(backend-api-gap #5): 待 /admin/workflow/history 上线后改为接口 -->
      <el-timeline>
        <el-timeline-item
          v-for="(row, i) in mockWorkflowLoadLog"
          :key="i"
          :type="row.level === 'success' ? 'success' : row.level === 'warning' ? 'warning' : 'primary'"
          :timestamp="row.time"
        >
          <div class="log-event">{{ row.event }}</div>
          <div class="log-detail">{{ row.detail }}</div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </div>
</template>

<style scoped>
.admin-workflow { display: flex; flex-direction: column; gap: 16px; }
.top-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  align-items: stretch;
}
.right-col { display: flex; flex-direction: column; gap: 16px; }
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
.card-header h3 { margin: 0; font-size: 14px; font-weight: 600; color: var(--text-primary); }
.link { font-size: 12px; color: var(--color-primary); cursor: pointer; }
.info-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px 16px;
}
.info-grid > div { display: flex; gap: 12px; font-size: 13px; align-items: center; }
.info-grid > div > span { color: var(--text-tertiary); min-width: 88px; }
.info-grid > div > b { color: var(--text-primary); font-weight: 500; }
.mono { font-family: 'JetBrains Mono', monospace; font-size: 12px; }
.action-row { display: flex; gap: 12px; margin-top: 12px; }
.empty { font-size: 13px; color: var(--text-tertiary); text-align: center; padding: 24px 0; }
.log-event { font-size: 13px; font-weight: 500; color: var(--text-primary); }
.log-detail { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
@media (max-width: 1280px) {
  .top-row { grid-template-columns: 1fr; }
}
</style>
