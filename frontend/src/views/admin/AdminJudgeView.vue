<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import JudgeDetailDrawer from '@/components/admin/JudgeDetailDrawer.vue'
import { adminApi } from '@/api/admin'
import type { JudgeRunVO } from '@/types/admin'
import { formatEpochMillis } from '@/utils/format'

const loading = ref(false)
const runs = ref<JudgeRunVO[]>([])
const taskIdInput = ref<number | null>(null)
const judgingNew = ref(false)

const drawerOpen = ref(false)
const drawerRun = ref<JudgeRunVO | null>(null)
const reRunLoading = ref(false)

// 前端分页
const pageSize = 10
const currentPage = ref(1)
const pagedRuns = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return runs.value.slice(start, start + pageSize)
})

async function load() {
  loading.value = true
  try {
    runs.value = await adminApi.judgeRecent(50)
  } finally {
    loading.value = false
  }
}

async function judgeNew() {
  if (taskIdInput.value == null) {
    ElMessage.warning('请输入任务 ID')
    return
  }
  judgingNew.value = true
  try {
    const r = await adminApi.judgeRun(taskIdInput.value)
    ElMessage.success(`评分完成：总分 ${r.overall.toFixed(1)}`)
    await load()
    openDetail(r)
  } finally {
    judgingNew.value = false
  }
}

function openDetail(r: JudgeRunVO) {
  drawerRun.value = r
  drawerOpen.value = true
}

async function reRunCurrent() {
  if (!drawerRun.value) return
  reRunLoading.value = true
  try {
    const r = await adminApi.judgeRun(drawerRun.value.taskId)
    ElMessage.success(`评分完成：总分 ${r.overall.toFixed(1)}`)
    drawerRun.value = r
    await load()
  } finally {
    reRunLoading.value = false
  }
}

function scoreColor(v: number) {
  if (v >= 9) return '#10b981'
  if (v >= 7) return '#2f6df5'
  if (v >= 5) return '#f59e0b'
  return '#ef4444'
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="admin-judge">
    <AdminPageHeader
      title="LLM-as-Judge 评分"
      subtitle="对研报任务进行结构、事实、推理、引用、清晰度五维质量评估。"
    />

    <div class="trigger-card">
      <div class="trigger-fields">
        <div class="field">
          <label>任务 ID</label>
          <el-input-number
            v-model="taskIdInput"
            :min="1"
            placeholder="输入任务 ID"
            style="width: 200px"
          />
        </div>
        <div class="field">
          <label>裁判模型</label>
          <el-tag size="default" type="info" effect="plain">qwen-max</el-tag>
        </div>
        <el-button
          type="primary"
          size="large"
          :loading="judgingNew"
          @click="judgeNew"
        >
          {{ judgingNew ? '评分中…' : '开始评分' }}
        </el-button>
        <span class="hint">评分约 5-15s，请耐心等待</span>
      </div>
    </div>

    <div class="list-card">
      <header class="card-header">
        <h3>最近评分记录</h3>
        <el-button :icon="Refresh" link @click="load">刷新</el-button>
      </header>

      <el-table :data="pagedRuns" stripe style="width: 100%">
        <el-table-column label="任务 ID" width="100">
          <template #default="{ row }">
            <span>Task #{{ (row as JudgeRunVO).taskId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总分" width="80">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).overall), fontWeight: 600 }">
              {{ (row as JudgeRunVO).overall.toFixed(1) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="结构" width="70">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).structure) }">{{ (row as JudgeRunVO).structure.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="事实" width="70">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).factuality) }">{{ (row as JudgeRunVO).factuality.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="推理" width="70">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).reasoning) }">{{ (row as JudgeRunVO).reasoning.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="引用" width="70">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).citation) }">{{ (row as JudgeRunVO).citation.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="清晰度" width="80">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).clarity) }">{{ (row as JudgeRunVO).clarity.toFixed(1) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ (row as JudgeRunVO).latencyMs }} ms</template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatEpochMillis((row as JudgeRunVO).createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="160">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row as JudgeRunVO)">查看详情</el-button>
            <el-button
              link
              type="primary"
              size="small"
              :loading="reRunLoading && drawerRun?.taskId === (row as JudgeRunVO).taskId"
              @click="() => { drawerRun = row as JudgeRunVO; reRunCurrent() }"
            >再次评分</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="currentPage"
          :page-size="pageSize"
          :total="runs.length"
          @current-change="(p: number) => (currentPage = p)"
        />
      </div>
    </div>

    <JudgeDetailDrawer
      v-model="drawerOpen"
      :run="drawerRun"
      :re-run-loading="reRunLoading"
      @re-run="reRunCurrent"
    />
  </div>
</template>

<style scoped>
.admin-judge { display: flex; flex-direction: column; gap: 16px; }
.trigger-card,
.list-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}
.trigger-fields {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.field { display: flex; flex-direction: column; gap: 4px; }
.field label { font-size: 12px; color: var(--text-tertiary); }
.hint { font-size: 12px; color: var(--text-tertiary); }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.card-header h3 { margin: 0; font-size: 14px; font-weight: 600; color: var(--text-primary); }
.pagination-row { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
