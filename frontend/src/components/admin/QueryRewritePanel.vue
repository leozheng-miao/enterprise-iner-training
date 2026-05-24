<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { adminApi } from '@/api/admin'
import type { QueryRewriteVO } from '@/types/admin'

const topic = ref('')
const loading = ref(false)
const result = ref<QueryRewriteVO | null>(null)
const resultTime = ref<string>('')

async function rewrite() {
  const t = topic.value.trim()
  if (t.length < 2) {
    ElMessage.warning('主题至少 2 个字符')
    return
  }
  loading.value = true
  try {
    result.value = await adminApi.rewriteQuery(t)
    resultTime.value = new Date().toLocaleString('zh-CN')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="card">
    <header class="card-header">
      <h3>Query Rewrite 测试</h3>
    </header>

    <div class="field">
      <label>输入研究主题</label>
      <el-input
        v-model="topic"
        type="textarea"
        :rows="4"
        :maxlength="500"
        show-word-limit
        placeholder="输入研究主题"
        resize="vertical"
      />
    </div>

    <el-button
      type="primary"
      size="large"
      :icon="Promotion"
      :loading="loading"
      style="width: 100%; margin-top: 12px"
      @click="rewrite"
    >测试改写</el-button>

    <div v-if="result" class="result">
      <header class="result-header">
        <span>示例输出（本次改写）</span>
        <span class="time">{{ resultTime }}</span>
      </header>

      <div class="row">
        <span class="row-label">用户意图</span>
        <el-tag size="small" type="primary">{{ result.intent ?? '—' }}</el-tag>
      </div>
      <div class="row">
        <span class="row-label">行业 / 时间 / 地域</span>
        <el-tag size="small">{{ result.industry ?? '—' }}</el-tag>
        <el-tag size="small">{{ result.year ?? '—' }}</el-tag>
        <el-tag size="small">{{ result.geo ?? '—' }}</el-tag>
      </div>
      <div class="row">
        <span class="row-label">检索子查询</span>
        <ul class="sub-list">
          <li v-for="(q, i) in (result.sub_queries ?? [])" :key="i">{{ q }}</li>
          <li v-if="!(result.sub_queries?.length)" class="empty">（无子查询）</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}
.card-header { margin-bottom: 12px; }
.card-header h3 { margin: 0; font-size: 14px; font-weight: 600; color: var(--text-primary); }
.field label { font-size: 12px; color: var(--text-tertiary); margin-bottom: 4px; display: block; }
.result {
  margin-top: 16px;
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  padding: 12px;
}
.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 8px;
}
.result-header .time { font-family: 'JetBrains Mono', monospace; }
.row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.row-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-right: 6px;
  min-width: 84px;
}
.sub-list { margin: 0; padding-left: 16px; font-size: 13px; color: var(--text-primary); }
.sub-list li { line-height: 1.6; }
.sub-list li.empty { color: var(--text-tertiary); font-style: italic; }
</style>
