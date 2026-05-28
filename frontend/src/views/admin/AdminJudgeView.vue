<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, Refresh } from '@element-plus/icons-vue'
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

// F4 #6: 裁判模型可选
const judgeModels = ref<string[]>([])
const selectedJudgeModel = ref<string>('qwen-max')

// 前端分页
const pageSize = 10
const currentPage = ref(1)
const pagedRuns = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return runs.value.slice(start, start + pageSize)
})

async function loadModels() {
  try {
    const models = await adminApi.judgeModels()
    judgeModels.value = models
    if (models.length && !models.includes(selectedJudgeModel.value)) {
      selectedJudgeModel.value = models[0]
    }
  } catch {
    /* 接口失败时保留默认 qwen-max；拦截器已 toast */
  }
}

async function load() {
  loading.value = true
  try {
    runs.value = await adminApi.judgeRecent(50)
  } finally {
    loading.value = false
  }
}

/** F4 #7: 首次评分（force=false，DB 无缓存就走真实调用）。 */
async function judgeNew() {
  if (taskIdInput.value == null) {
    ElMessage.warning('请输入任务 ID')
    return
  }
  judgingNew.value = true
  try {
    const r = await adminApi.judgeRun(taskIdInput.value, {
      judgeModel: selectedJudgeModel.value
    })
    ElMessage.success(`评分完成：总分 ${fmtScore(r.overall)}`)
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

/**
 * F4 #7: 再次评分。
 * - force=false（默认）→ 命中缓存返回历史（瞬时）
 * - force=true → 强制重新调用 LLM（5-15s）
 */
async function reRun(taskId: number, force: boolean) {
  reRunLoading.value = true
  try {
    const r = await adminApi.judgeRun(taskId, {
      judgeModel: selectedJudgeModel.value,
      force
    })
    ElMessage.success(
      force
        ? `重新评分完成：总分 ${fmtScore(r.overall)}`
        : `已读取历史评分：总分 ${fmtScore(r.overall)}`
    )
    drawerRun.value = r
    await load()
  } finally {
    reRunLoading.value = false
  }
}

function onRowRejudge(row: JudgeRunVO, force: boolean) {
  drawerRun.value = row
  reRun(row.taskId, force)
}

function scoreColor(v: number | null) {
  if (v == null) return '#9ca3af'
  if (v >= 9) return '#10b981'
  if (v >= 7) return '#2f6df5'
  if (v >= 5) return '#f59e0b'
  return '#ef4444'
}

/** 分数显示兜底：null → '—'，否则保留 1 位小数。 */
function fmtScore(v: number | null): string {
  return v == null ? '—' : v.toFixed(1)
}

onMounted(() => {
  loadModels()
  load()
})
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
          <el-select v-model="selectedJudgeModel" size="default" style="width: 180px">
            <el-option
              v-for="m in judgeModels"
              :key="m"
              :label="m"
              :value="m"
            />
            <!-- 接口未加载完时至少有兜底项可选 -->
            <el-option
              v-if="judgeModels.length === 0"
              label="qwen-max"
              value="qwen-max"
            />
          </el-select>
        </div>
        <el-button
          type="primary"
          size="large"
          :loading="judgingNew"
          @click="judgeNew"
        >
          {{ judgingNew ? '评分中…' : '开始评分' }}
        </el-button>
        <span class="hint">首次评分约 5-15s；后续命中缓存瞬时返回</span>
      </div>
      <div class="variance-note">
        ⓘ F4 起，「开始评分 / 再次评分」默认走缓存（同 task+model+rubric 命中历史直接返回）。如需强制重新调用 LLM，使用行操作的「强制重评」选项；同任务多次强制重评结果可能 ±0.5 分波动属正常现象。
      </div>
    </div>

    <div class="list-card">
      <header class="card-header">
        <h3>最近评分记录</h3>
        <el-button :icon="Refresh" link @click="load">刷新</el-button>
      </header>

      <el-table :data="pagedRuns" stripe style="width: 100%">
        <!-- F4 #5: 「任务 ID」 列扩展为「任务」列（含 topic） -->
        <el-table-column label="任务" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="task-cell">
              <div class="task-id">#{{ (row as JudgeRunVO).taskId }}</div>
              <div v-if="(row as JudgeRunVO).topic" class="task-topic">
                {{ (row as JudgeRunVO).topic }}
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="总分" width="80">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).overall), fontWeight: 600 }">
              {{ fmtScore((row as JudgeRunVO).overall) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="结构" width="70">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).structure) }">{{ fmtScore((row as JudgeRunVO).structure) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="事实" width="70">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).factuality) }">{{ fmtScore((row as JudgeRunVO).factuality) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="推理" width="70">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).reasoning) }">{{ fmtScore((row as JudgeRunVO).reasoning) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="引用" width="70">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).citation) }">{{ fmtScore((row as JudgeRunVO).citation) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="清晰度" width="80">
          <template #default="{ row }">
            <span :style="{ color: scoreColor((row as JudgeRunVO).clarity) }">{{ fmtScore((row as JudgeRunVO).clarity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">
            {{ (row as JudgeRunVO).latencyMs == null ? '—' : `${(row as JudgeRunVO).latencyMs} ms` }}
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatEpochMillis((row as JudgeRunVO).createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row as JudgeRunVO)">查看详情</el-button>
            <!-- F4 #7: 再次评分二级菜单（A 方案） -->
            <el-dropdown
              trigger="click"
              :disabled="reRunLoading && drawerRun?.taskId === (row as JudgeRunVO).taskId"
              @command="(cmd: 'cache' | 'force') => onRowRejudge(row as JudgeRunVO, cmd === 'force')"
            >
              <el-button
                link
                type="primary"
                size="small"
                :loading="reRunLoading && drawerRun?.taskId === (row as JudgeRunVO).taskId"
              >
                再次评分<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="cache">使用缓存（瞬时）</el-dropdown-item>
                  <el-dropdown-item command="force" divided>强制重新评分（5-15s）</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
      @re-run="(force: boolean) => drawerRun && reRun(drawerRun.taskId, force)"
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
.variance-note {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  padding: 10px 12px;
  line-height: 1.6;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.card-header h3 { margin: 0; font-size: 14px; font-weight: 600; color: var(--text-primary); }
.pagination-row { display: flex; justify-content: flex-end; margin-top: 16px; }

.task-cell { line-height: 1.4; }
.task-id { font-family: 'JetBrains Mono', monospace; font-weight: 500; color: var(--text-primary); }
.task-topic { font-size: 12px; color: var(--text-tertiary); margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
