<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { CitationData } from '@/types/rag'

const props = defineProps<{
  citations: CitationData[]
  activeIndex: number | null    // 1-based; null when nothing highlighted
}>()

const cardRefs = ref<HTMLElement[]>([])
function setCardRef(el: unknown, idx: number) {
  if (el) cardRefs.value[idx] = el as HTMLElement
}

watch(
  () => props.activeIndex,
  async (cur) => {
    if (cur == null || cur < 1) return
    await nextTick()
    const el = cardRefs.value[cur - 1]
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    el.classList.add('flash')
    window.setTimeout(() => el.classList.remove('flash'), 1200)
  }
)
</script>

<template>
  <section v-if="citations.length > 0" class="cg-block">
    <h2 class="cg-title">参考资料</h2>
    <div class="cg-grid">
      <div
        v-for="(c, idx) in citations"
        :key="idx"
        :ref="(el) => setCardRef(el, idx)"
        class="cite-card"
      >
        <div class="cite-no">[{{ idx + 1 }}]</div>
        <div class="cite-title" :title="c.docTitle">{{ c.docTitle }}</div>
        <div class="cite-section">{{ c.sectionTitle }}</div>
        <div class="cite-meta">
          <span class="meta-pill">{{ c.source }}</span>
          <span class="meta-pages">p. {{ c.pageStart }}-{{ c.pageEnd }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.cg-block {
  margin-top: 24px;
}

.cg-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 12px;
  color: var(--text-primary);
}

.cg-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.cite-card {
  border: 1px solid var(--border-light);
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: box-shadow 0.3s, border-color 0.3s, background 0.3s;
}

.cite-card.flash {
  border-color: var(--color-primary);
  background: rgba(47, 109, 245, 0.05);
  box-shadow: 0 0 0 4px rgba(47, 109, 245, 0.12);
}

.cite-no {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-primary);
}

.cite-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cite-section {
  font-size: 12px;
  color: var(--text-secondary);
}

.cite-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.meta-pill {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--bg-muted);
  color: var(--text-secondary);
}

.meta-pages {
  font-size: 11px;
  color: var(--text-tertiary);
}

@media (max-width: 900px) {
  .cg-grid {
    grid-template-columns: 1fr;
  }
}
</style>
