<script setup lang="ts">
import { computed } from 'vue'
import DonutChart from '@/components/chart/DonutChart.vue'
import BarChart from '@/components/chart/BarChart.vue'
import { RAG_CONFIG, type HitData } from '@/types/rag'

const props = defineProps<{
  activeHit: HitData | null
  activeIndex: number              // 1-based; 0 when no active
  allHits: HitData[]
}>()

// 来源分布：按 citation.source 聚合所有 hits
const sourceData = computed(() => {
  const map = new Map<string, number>()
  for (const h of props.allHits) {
    const k = h.citation.source || '未知'
    map.set(k, (map.get(k) ?? 0) + 1)
  }
  return Array.from(map.entries()).map(([name, value]) => ({ name, value }))
})

// Score 分布：将 hits[].score 落到 5 个桶
const scoreBuckets = computed(() => {
  const bins = [
    { label: '0-0.2', value: 0 },
    { label: '0.2-0.4', value: 0 },
    { label: '0.4-0.6', value: 0 },
    { label: '0.6-0.8', value: 0 },
    { label: '0.8-1.0', value: 0 }
  ]
  for (const h of props.allHits) {
    const idx = Math.min(Math.floor(h.score * 5), 4)
    bins[idx].value++
  }
  return bins
})

const score = computed(() => {
  const h = props.activeHit
  if (!h) return null
  return h.rerankScore != null
    ? h.rerankScore.toFixed(4)
    : h.score.toFixed(4)
})
</script>

<template>
  <aside class="cit-panel">
    <!-- 引用信息 -->
    <section class="cp-section">
      <header class="cp-section-header">
        <h3 class="cp-section-title">引用信息</h3>
      </header>
      <div v-if="activeHit" class="cp-detail">
        <div class="cp-detail-head">
          <span class="cp-index">{{ activeIndex }}</span>
          <span class="cp-doc-title" :title="activeHit.citation.docTitle">
            {{ activeHit.citation.docTitle }}
          </span>
          <span class="cp-score">{{ score }}</span>
        </div>
        <div class="cp-detail-grid">
          <div class="cp-row"><span class="cp-k">sectionTitle</span><span class="cp-v">{{ activeHit.citation.sectionTitle }}</span></div>
          <div class="cp-row"><span class="cp-k">pageStart</span><span class="cp-v">{{ activeHit.citation.pageStart }}</span></div>
          <div class="cp-row"><span class="cp-k">pageEnd</span><span class="cp-v">{{ activeHit.citation.pageEnd }}</span></div>
          <div class="cp-row"><span class="cp-k">source</span><span class="cp-v">{{ activeHit.citation.source }}</span></div>
          <div class="cp-row"><span class="cp-k">chunkId</span><span class="cp-v">{{ activeHit.chunkId }}</span></div>
          <div class="cp-row"><span class="cp-k">docId</span><span class="cp-v">{{ activeHit.citation.docId }}</span></div>
        </div>
      </div>
      <div v-else class="cp-empty">点击左侧任一结果查看引用详情</div>
    </section>

    <!-- 来源分布 -->
    <section class="cp-section">
      <header class="cp-section-header">
        <h3 class="cp-section-title">来源分布</h3>
      </header>
      <DonutChart v-if="sourceData.length > 0" :data="sourceData" center-label="总命中" />
      <div v-else class="cp-empty">检索一次即可看到分布</div>
    </section>

    <!-- Score 分布 -->
    <section class="cp-section">
      <header class="cp-section-header">
        <h3 class="cp-section-title">Score 分布</h3>
      </header>
      <BarChart :data="scoreBuckets" />
    </section>

    <!-- 检索配置 -->
    <section class="cp-section">
      <header class="cp-section-header">
        <h3 class="cp-section-title">检索配置</h3>
      </header>
      <div class="cp-config">
        <div class="cp-cfg-row"><span class="cp-k">向量模型</span><span class="cp-v">{{ RAG_CONFIG.embeddingModel }}</span></div>
        <div class="cp-cfg-row"><span class="cp-k">向量相似度</span><span class="cp-v">{{ RAG_CONFIG.vectorSimilarity }}</span></div>
        <div class="cp-cfg-row"><span class="cp-k">BM25 权重</span><span class="cp-v">{{ RAG_CONFIG.bm25Weight }}</span></div>
        <div class="cp-cfg-row"><span class="cp-k">向量权重</span><span class="cp-v">{{ RAG_CONFIG.vectorWeight }}</span></div>
        <div class="cp-cfg-row"><span class="cp-k">Rerank 模型</span><span class="cp-v">{{ RAG_CONFIG.rerankModel }}</span></div>
      </div>
    </section>
  </aside>
</template>

<style scoped>
.cit-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cp-section {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px;
}

.cp-section-header {
  margin-bottom: 12px;
}

.cp-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.cp-detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.cp-index {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  background: var(--color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.cp-doc-title {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cp-score {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-success);
  font-variant-numeric: tabular-nums;
}

.cp-detail-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cp-row,
.cp-cfg-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  border-bottom: 1px dashed var(--border-light);
  padding-bottom: 6px;
}

.cp-row:last-child,
.cp-cfg-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.cp-k {
  color: var(--text-tertiary);
}

.cp-v {
  color: var(--text-secondary);
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
}

.cp-empty {
  color: var(--text-tertiary);
  font-size: 12px;
  text-align: center;
  padding: 16px 0;
}

.cp-config {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
