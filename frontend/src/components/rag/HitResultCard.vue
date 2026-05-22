<script setup lang="ts">
import { computed, ref } from 'vue'
import type { HitData } from '@/types/rag'

const props = defineProps<{
  index: number          // 1-based ordinal (1, 2, 3 ...)
  hit: HitData
  active?: boolean
}>()

defineEmits<{
  (e: 'select', hit: HitData): void
}>()

const expanded = ref(false)

const scoreText = computed(() => props.hit.score.toFixed(4))
const rerankText = computed(() =>
  props.hit.rerankScore == null ? '—' : props.hit.rerankScore.toFixed(4)
)
</script>

<template>
  <article
    class="hit-card"
    :class="{ active }"
    @click="$emit('select', hit)"
  >
    <div class="hit-row-top">
      <span class="hit-index">{{ index }}</span>

      <div class="hit-scores">
        <div class="score-block">
          <div class="score-label">Chunk Score</div>
          <div class="score-value chunk">{{ scoreText }}</div>
        </div>
        <div class="score-block">
          <div class="score-label">Rerank Score</div>
          <div class="score-value rerank">{{ rerankText }}</div>
        </div>
      </div>

      <div class="hit-content" :class="{ expanded }">
        <p class="hit-text">{{ hit.content }}</p>
        <a class="hit-toggle" @click.stop="expanded = !expanded">
          {{ expanded ? '收起 ‹' : '展开 ›' }}
        </a>
      </div>
    </div>

    <div class="hit-meta-grid">
      <div class="meta-cell">
        <div class="meta-key">docTitle</div>
        <div class="meta-val" :title="hit.citation.docTitle">{{ hit.citation.docTitle }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">source</div>
        <div class="meta-val">{{ hit.citation.source }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">sectionTitle</div>
        <div class="meta-val" :title="hit.citation.sectionTitle">{{ hit.citation.sectionTitle }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">pageStart</div>
        <div class="meta-val">{{ hit.citation.pageStart }}</div>
      </div>
      <div class="meta-cell">
        <div class="meta-key">pageEnd</div>
        <div class="meta-val">{{ hit.citation.pageEnd }}</div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.hit-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
  padding: 16px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.hit-card:hover,
.hit-card.active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(47, 109, 245, 0.08);
}

.hit-row-top {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.hit-index {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.hit-scores {
  display: grid;
  grid-template-columns: repeat(2, max-content);
  gap: 0 16px;
  flex-shrink: 0;
}

.score-label {
  font-size: 11px;
  color: var(--text-tertiary);
}

.score-value {
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.score-value.chunk {
  color: var(--color-primary);
}

.score-value.rerank {
  color: var(--color-success);
}

.hit-content {
  flex: 1;
  min-width: 0;
}

.hit-text {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hit-content.expanded .hit-text {
  -webkit-line-clamp: unset;
  overflow: visible;
}

.hit-toggle {
  display: inline-block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
}

.hit-meta-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-light);
}

.meta-key {
  font-size: 11px;
  color: var(--text-tertiary);
}

.meta-val {
  font-size: 13px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
