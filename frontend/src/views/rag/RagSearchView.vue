<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import HitResultCard from '@/components/rag/HitResultCard.vue'
import CitationPanel from '@/components/rag/CitationPanel.vue'
import { ragApi } from '@/api/rag'
import type { HitData, SearchResponse } from '@/types/rag'

const query = ref('')
const topK = ref(10)
const useRerank = ref(true)

const loading = ref(false)
const lastResp = ref<SearchResponse | null>(null)
const activeHit = ref<HitData | null>(null)

const hits = computed(() => lastResp.value?.hits ?? [])
const tookMs = computed(() => lastResp.value?.tookMs ?? 0)

const activeIndex = computed(() => {
  if (!activeHit.value) return 0
  const i = hits.value.findIndex((h) => h.chunkId === activeHit.value!.chunkId)
  return i >= 0 ? i + 1 : 0
})

async function onSearch() {
  if (!query.value.trim()) {
    ElMessage.warning('请输入查询内容')
    return
  }
  loading.value = true
  try {
    const resp = await ragApi.search({
      query: query.value.trim(),
      topK: topK.value,
      useRerank: useRerank.value
    })
    lastResp.value = resp
    activeHit.value = resp.hits[0] ?? null
  } catch {
    /* ElMessage already shown by interceptor */
  } finally {
    loading.value = false
  }
}

function onSelect(h: HitData) {
  activeHit.value = h
}
</script>

<template>
  <div class="rag-search">
    <header class="rs-title-row">
      <h1 class="rs-title">Hybrid Search 检索</h1>
      <p class="rs-subtitle">
        基于向量检索 + BM25 关键词检索 + Rerank 的混合检索，快速定位知识库中的高相关内容。
      </p>
    </header>

    <!-- 顶部搜索表单 -->
    <section class="rs-form">
      <el-input
        v-model="query"
        placeholder="输入研究主题，如：新能源电池技术趋势与产业链变化"
        class="rs-query"
        clearable
        @keyup.enter="onSearch"
      />
      <div class="rs-form-field">
        <span class="rs-label">TopK</span>
        <el-select v-model="topK" style="width: 96px;">
          <el-option v-for="n in [5, 10, 20, 30, 50]" :key="n" :label="String(n)" :value="n" />
        </el-select>
      </div>
      <div class="rs-form-field">
        <span class="rs-label">启用 Rerank</span>
        <el-switch v-model="useRerank" />
      </div>
      <el-button type="primary" :loading="loading" @click="onSearch">开始检索</el-button>
    </section>

    <!-- 三统计卡 -->
    <section v-if="lastResp" class="rs-stats">
      <div class="rs-stat-card">
        <div class="rs-stat-label">耗时 tookMs</div>
        <div class="rs-stat-value">{{ tookMs }} <span class="rs-stat-unit">ms</span></div>
      </div>
      <div class="rs-stat-card">
        <div class="rs-stat-label">命中条数</div>
        <div class="rs-stat-value">{{ hits.length }}</div>
      </div>
      <div class="rs-stat-card">
        <div class="rs-stat-label">Rerank 状态</div>
        <div class="rs-stat-value">{{ useRerank ? '已开启' : '未开启' }}</div>
      </div>
    </section>

    <!-- 主区：左侧结果列表 + 右侧引用面板 -->
    <section class="rs-main">
      <div class="rs-results">
        <header v-if="lastResp" class="rs-results-header">
          <h2 class="rs-results-title">检索结果</h2>
          <span class="rs-results-meta">共 {{ hits.length }} 条结果（TopK={{ topK }}）</span>
        </header>

        <div v-if="loading" class="rs-empty">检索中…</div>
        <div v-else-if="!lastResp" class="rs-empty">输入查询并点击"开始检索"</div>
        <div v-else-if="hits.length === 0" class="rs-empty">未命中任何结果</div>

        <HitResultCard
          v-for="(h, idx) in hits"
          :key="h.chunkId"
          :index="idx + 1"
          :hit="h"
          :active="activeHit?.chunkId === h.chunkId"
          @select="onSelect"
        />
      </div>

      <div class="rs-side">
        <CitationPanel :active-hit="activeHit" :active-index="activeIndex" :all-hits="hits" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.rag-search {
  display: flex;
  flex-direction: column;
  gap: 16px;
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

/* Form ============================================ */
.rs-form {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.rs-query {
  flex: 1;
  min-width: 240px;
}

.rs-form-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rs-label {
  font-size: 12px;
  color: var(--text-secondary);
}

/* Stats =========================================== */
.rs-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.rs-stat-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px 20px;
}

.rs-stat-label {
  font-size: 12px;
  color: var(--text-tertiary);
}

.rs-stat-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.rs-stat-unit {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: 4px;
}

/* Main ============================================ */
.rs-main {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 16px;
  align-items: start;
}

.rs-results {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px;
  min-width: 0;
}

.rs-results-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.rs-results-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.rs-results-meta {
  font-size: 12px;
  color: var(--text-tertiary);
}

.rs-empty {
  padding: 32px 16px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

@media (max-width: 1280px) {
  .rs-main {
    grid-template-columns: 1fr;
  }
}
</style>
