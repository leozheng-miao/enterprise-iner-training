<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh } from '@element-plus/icons-vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { adminApi } from '@/api/admin'
import type { Page } from '@/api/admin'
import type { TaskBriefVO, TaskStatus } from '@/types/admin'
import {
  STATUS_LABEL,
  STATUS_TAG_TYPE,
  progressStatus
} from '@/utils/taskStatus'
import { formatDurationMs, formatEpochMillis } from '@/utils/format'

const router = useRouter()

type FilterValue = '' | TaskStatus
const filter = ref<FilterValue>('')
const page = ref(1)
const size = 20
const loading = ref(false)
const result = ref<Page<TaskBriefVO> | null>(null)

async function load() {
  loading.value = true
  try {
    result.value = await adminApi.tasks(filter.value, page.value, size)
  } finally {
    loading.value = false
  }
}

watch(filter, () => {
  page.value = 1
  load()
})

function onPageChange(p: number) {
  page.value = p
  load()
}

function goSubmit() {
  router.push('/report/submit')
}

function goDetail(id: number) {
  router.push(`/report/${id}`)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="admin-tasks">
    <AdminPageHeader
      title="任务管理"
      subtitle="查看 Multi-Agent 研报任务状态、阶段进度与执行耗时。"
    />

    <div class="toolbar">
      <el-radio-group v-model="filter" size="default">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="PENDING">PENDING</el-radio-button>
        <el-radio-button value="RUNNING">RUNNING</el-radio-button>
        <el-radio-button value="DONE">DONE</el-radio-button>
        <el-radio-button value="FAILED">FAILED</el-radio-button>
      </el-radio-group>

      <div class="toolbar-right">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="goSubmit">新建报告任务</el-button>
      </div>
    </div>

    <el-table :data="result?.records ?? []" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="topic" label="研究主题" min-width="240" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="STATUS_TAG_TYPE[(row as TaskBriefVO).status]" size="small">
            {{ STATUS_LABEL[(row as TaskBriefVO).status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前阶段" width="140">
        <template #default="{ row }">
          {{ (row as TaskBriefVO).phase ?? '—' }}
        </template>
      </el-table-column>
      <el-table-column label="进度" width="180">
        <template #default="{ row }">
          <el-progress
            :percentage="(row as TaskBriefVO).progress ?? 0"
            :status="progressStatus((row as TaskBriefVO).status)"
            :stroke-width="10"
          />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">
          {{ formatEpochMillis((row as TaskBriefVO).createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="完成耗时" width="110">
        <template #default="{ row }">
          {{ formatDurationMs((row as TaskBriefVO).latencyMs) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="goDetail((row as TaskBriefVO).id)">查看详情</el-button>
          <el-button type="primary" link size="small" @click="goDetail((row as TaskBriefVO).id)">Trace</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-row">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :current-page="page"
        :page-size="size"
        :total="result?.totalRow ?? 0"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.admin-tasks {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.toolbar-right { display: flex; gap: 8px; }
.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
