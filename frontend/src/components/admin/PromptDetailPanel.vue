<script setup lang="ts">
import { ref, watch } from 'vue'
import { CircleCheck, EditPen, Plus, InfoFilled } from '@element-plus/icons-vue'
import type { PromptTemplateVO, PromptUpdateRequest } from '@/types/admin'
import { formatEpochMillis } from '@/utils/format'

const props = defineProps<{
  detail: PromptTemplateVO | null
  mode: 'view' | 'edit'
}>()

const emit = defineEmits<{
  (e: 'activate'): void
  (e: 'enter-edit'): void
  (e: 'create'): void
  (e: 'cancel-edit'): void
  (e: 'save', body: PromptUpdateRequest): void
}>()

const draftContent = ref('')
const draftModel = ref('')
const draftTemperature = ref<number | null>(null)

watch(
  () => [props.detail, props.mode],
  () => {
    if (props.detail && props.mode === 'edit') {
      draftContent.value = props.detail.content
      draftModel.value = props.detail.model ?? ''
      draftTemperature.value = props.detail.temperature
    }
  },
  { immediate: true }
)

function save() {
  if (!props.detail) return
  const body: PromptUpdateRequest = {}
  if (draftContent.value !== props.detail.content) body.content = draftContent.value
  if ((draftModel.value || null) !== props.detail.model)
    body.model = draftModel.value || undefined
  if (draftTemperature.value !== props.detail.temperature)
    body.temperature = draftTemperature.value ?? undefined
  emit('save', body)
}
</script>

<template>
  <div class="detail-panel">
    <header class="header">
      <h2>版本详情</h2>
      <div class="actions" v-if="detail && mode === 'view'">
        <el-button :icon="CircleCheck" :disabled="detail.isActive" @click="emit('activate')">
          设为生效版本
        </el-button>
        <el-button :icon="EditPen" @click="emit('enter-edit')">编辑内容</el-button>
        <el-button type="primary" :icon="Plus" @click="emit('create')">新建版本</el-button>
      </div>
    </header>

    <el-empty v-if="!detail" description="请选择左侧某个 Prompt 版本" />

    <template v-else>
      <div class="meta">
        <div class="meta-row"><span>名称</span><b>{{ detail.name }}</b></div>
        <div class="meta-row"><span>版本</span><b>{{ detail.version }}</b></div>
        <div class="meta-row">
          <span>模型</span>
          <template v-if="mode === 'edit'">
            <el-input v-model="draftModel" size="small" style="max-width: 200px" />
          </template>
          <b v-else>{{ detail.model ?? '—' }}</b>
        </div>
        <div class="meta-row">
          <span>温度 (temperature)</span>
          <template v-if="mode === 'edit'">
            <el-input-number
              v-model="draftTemperature"
              :step="0.1"
              :min="0"
              :max="2"
              size="small"
              style="width: 140px"
            />
          </template>
          <b v-else>{{ detail.temperature ?? '—' }}</b>
        </div>
        <div class="meta-row">
          <span>状态</span>
          <el-tag :type="detail.isActive ? 'success' : 'warning'" size="small">
            {{ detail.isActive ? '生效' : '未生效' }}
          </el-tag>
        </div>
        <div class="meta-row"><span>创建时间</span><b>{{ formatEpochMillis(detail.createdAt) }}</b></div>
      </div>

      <div class="content-block">
        <h3>Prompt 内容</h3>
        <el-input
          v-if="mode === 'edit'"
          v-model="draftContent"
          type="textarea"
          :rows="20"
          resize="vertical"
        />
        <pre v-else class="readonly">{{ detail.content }}</pre>
      </div>

      <div class="footer">
        <el-alert
          v-if="mode === 'view'"
          type="info"
          :closable="false"
          :icon="InfoFilled"
          show-icon
          title="切换生效后，下次 Agent 调用立即使用，无需重启。"
        />
        <div v-else class="edit-actions">
          <el-button @click="emit('cancel-edit')">取消</el-button>
          <el-button type="primary" @click="save">保存修改</el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-panel {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 600px;
}
.header { display: flex; align-items: center; justify-content: space-between; }
.header h2 { margin: 0; font-size: 16px; }
.actions { display: flex; gap: 8px; }
.meta {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px 24px;
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  padding: 16px;
}
.meta-row { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
.meta-row span { color: var(--text-tertiary); }
.meta-row b { color: var(--text-primary); font-weight: 500; }
.content-block h3 { margin: 0 0 8px; font-size: 14px; }
.readonly {
  background: #0b1020;
  color: #d1d5db;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  line-height: 1.6;
  padding: 16px;
  border-radius: var(--radius-md);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 540px;
  overflow-y: auto;
}
.footer { display: flex; justify-content: flex-end; }
.edit-actions { display: flex; gap: 8px; }
</style>
