<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import PromptGroupList from '@/components/admin/PromptGroupList.vue'
import PromptDetailPanel from '@/components/admin/PromptDetailPanel.vue'
import { adminApi } from '@/api/admin'
import type {
  PromptCreateRequest,
  PromptTemplateVO,
  PromptUpdateRequest
} from '@/types/admin'

const loading = ref(false)
const items = ref<PromptTemplateVO[]>([])
const selectedId = ref<number | null>(null)
const detail = ref<PromptTemplateVO | null>(null)
const mode = ref<'view' | 'edit'>('view')

const createDialog = ref(false)
const createForm = ref<PromptCreateRequest>({
  name: '',
  version: 'v1',
  content: '',
  model: '',
  temperature: undefined
})

async function loadList(preselect?: number) {
  loading.value = true
  try {
    items.value = await adminApi.listPrompts()
    const target =
      preselect ?? selectedId.value ?? items.value[0]?.id ?? null
    if (target != null) {
      selectedId.value = target
      detail.value = items.value.find((x) => x.id === target) ?? null
    }
  } finally {
    loading.value = false
  }
}

function onSelect(id: number) {
  if (mode.value === 'edit') {
    ElMessage.warning('请先取消或保存当前编辑')
    return
  }
  selectedId.value = id
  detail.value = items.value.find((x) => x.id === id) ?? null
}

async function activate() {
  if (!detail.value) return
  await ElMessageBox.confirm(
    '将该版本设为生效，下次 Agent 调用立即使用，是否继续？',
    '切换生效版本',
    { type: 'warning' }
  )
  await adminApi.activatePrompt(detail.value.id)
  ElMessage.success('已切换，下次 Agent 调用立即生效，无需重启')
  await loadList(detail.value.id)
}

async function save(body: PromptUpdateRequest) {
  if (!detail.value) return
  const updated = await adminApi.updatePrompt(detail.value.id, body)
  ElMessage.success('已保存（PromptLoader 缓存已清）')
  detail.value = updated
  // 更新列表里同 id 的项
  const idx = items.value.findIndex((x) => x.id === updated.id)
  if (idx >= 0) items.value[idx] = updated
  mode.value = 'view'
}

async function submitCreate() {
  if (!createForm.value.name || !createForm.value.version || !createForm.value.content) {
    ElMessage.warning('name / version / content 必填')
    return
  }
  const body: PromptCreateRequest = {
    name: createForm.value.name,
    version: createForm.value.version,
    content: createForm.value.content,
    model: createForm.value.model || undefined,
    temperature: createForm.value.temperature
  }
  const created = await adminApi.createPrompt(body)
  createDialog.value = false
  createForm.value = { name: '', version: 'v1', content: '', model: '', temperature: undefined }
  await loadList(created.id)
  ElMessage.success('已创建新版本（未生效）')
}

onMounted(() => loadList())
</script>

<template>
  <div v-loading="loading" class="admin-prompt">
    <AdminPageHeader
      title="Prompt 管理"
      subtitle="管理 Planner / Researcher / Analyst / Writer / Critic 的 Prompt 版本与灰度生效状态。"
    />

    <div class="layout">
      <PromptGroupList
        :items="items"
        :selected-id="selectedId"
        @select="onSelect"
      />
      <PromptDetailPanel
        :detail="detail"
        :mode="mode"
        @activate="activate"
        @enter-edit="mode = 'edit'"
        @create="createDialog = true"
        @cancel-edit="mode = 'view'"
        @save="save"
      />
    </div>

    <el-dialog v-model="createDialog" title="新建 Prompt 版本" width="560">
      <el-form label-width="100" label-position="right">
        <el-form-item label="name" required>
          <el-input v-model="createForm.name" placeholder="如 planner_prompt" />
        </el-form-item>
        <el-form-item label="version" required>
          <el-input v-model="createForm.version" placeholder="如 v3" />
        </el-form-item>
        <el-form-item label="model">
          <el-input v-model="createForm.model" placeholder="如 qwen-max（可空）" />
        </el-form-item>
        <el-form-item label="temperature">
          <el-input-number v-model="createForm.temperature" :step="0.1" :min="0" :max="2" />
        </el-form-item>
        <el-form-item label="content" required>
          <el-input v-model="createForm.content" type="textarea" :rows="10" resize="vertical" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-prompt { display: flex; flex-direction: column; gap: 16px; }
.layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  align-items: stretch;
}
@media (max-width: 1280px) {
  .layout { grid-template-columns: 1fr; }
}
</style>
