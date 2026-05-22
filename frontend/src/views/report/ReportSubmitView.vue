<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { reportApi } from '@/api/report'

const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  topic: (route.query.topic as string) || '',
  workflow: 'researcher_only_v1'
})

const rules: FormRules = {
  topic: [
    { required: true, message: '请输入研究主题', trigger: 'blur' },
    { min: 4, max: 500, message: '长度 4-500', trigger: 'blur' }
  ]
}

const workflowOptions = [
  { label: 'researcher_only_v1（仅检索员工作流）', value: 'researcher_only_v1' }
]

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const resp = await reportApi.start({
      topic: form.topic.trim(),
      workflow: form.workflow
    })
    ElMessage.success(`任务已提交：task_${String(resp.taskId).padStart(8, '0')}`)
    router.replace(`/report/${resp.taskId}`)
  } catch {
    /* ElMessage handled by interceptor */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="rs-submit">
    <header class="rs-title-row">
      <h1 class="rs-title">发起研究报告任务</h1>
      <p class="rs-subtitle">
        输入主题，平台将通过 Multi-Agent 工作流自动检索、分析并撰写一份带引用的行业研究报告。
      </p>
    </header>

    <section class="rs-form-card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <el-form-item label="研究主题" prop="topic">
          <el-input
            v-model="form.topic"
            placeholder="例：2026 年新能源汽车行业趋势研究"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            @keyup.enter.ctrl="onSubmit"
          />
        </el-form-item>

        <el-form-item label="工作流">
          <el-select v-model="form.workflow" style="width: 100%;">
            <el-option
              v-for="o in workflowOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="rs-submit-btn"
          :loading="submitting"
          @click="onSubmit"
        >
          提交并开始
        </el-button>

        <div class="rs-hint">
          Ctrl + Enter 提交 · 任务平均耗时 30-180s · 提交后会跳到详情页查看 SSE 实时流
        </div>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.rs-submit {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 720px;
  margin: 0 auto;
}

.rs-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.rs-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.rs-form-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 24px;
}

.rs-submit-btn {
  width: 100%;
}

.rs-hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
}
</style>
