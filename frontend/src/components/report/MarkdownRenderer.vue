<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-light.css'
import { citationPlugin } from '@/utils/citation'

const props = defineProps<{
  /** 当前要渲染的完整 markdown 源文。流式时父组件持续追加这个 source。 */
  source: string
  /** 流式中：true 时启用节流（最多 60fps），结束时设 false 立即 final render */
  streaming?: boolean
}>()

const emit = defineEmits<{
  (e: 'cite-click', citationIndex: number): void
}>()

// ===== markdown-it 实例（带 hljs + citation plugin） =====
// 显式类型注解，避免 highlight 回调里引用 md.utils 触发的 TS7022 循环推导错误。
const md: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: false,
  highlight(code: string, lang: string): string {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="hljs"><code>${
          hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
        }</code></pre>`
      } catch {
        /* fall through */
      }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(code)}</code></pre>`
  }
})
md.use(citationPlugin)

// ===== 节流渲染 =====
const rendered = ref('')
let rafId: number | null = null
let lastRenderAt = 0

function scheduleRender() {
  if (rafId != null) return
  rafId = requestAnimationFrame(() => {
    rafId = null
    const now = performance.now()
    // 节流：流式状态下两次渲染至少间隔 16ms（约 60fps）
    if (props.streaming && now - lastRenderAt < 16) {
      scheduleRender()
      return
    }
    lastRenderAt = now
    rendered.value = md.render(props.source || '')
  })
}

watch(
  () => props.source,
  () => scheduleRender(),
  { immediate: true }
)

// 流式结束的瞬间强制 final render（不节流）
watch(
  () => props.streaming,
  (cur, prev) => {
    if (prev === true && cur === false) {
      if (rafId != null) {
        cancelAnimationFrame(rafId)
        rafId = null
      }
      rendered.value = md.render(props.source || '')
    }
  }
)

// ===== 引用点击事件代理 =====
const rootEl = ref<HTMLElement>()

function onClickDelegated(ev: MouseEvent) {
  const target = ev.target as HTMLElement
  const sup = target.closest('.cite-ref') as HTMLElement | null
  if (!sup) return
  const idx = Number(sup.dataset.cite)
  if (Number.isFinite(idx) && idx > 0) {
    emit('cite-click', idx)
  }
}

onMounted(() => {
  rootEl.value?.addEventListener('click', onClickDelegated)
})

onBeforeUnmount(() => {
  rootEl.value?.removeEventListener('click', onClickDelegated)
  if (rafId != null) cancelAnimationFrame(rafId)
})
</script>

<template>
  <div ref="rootEl" class="md-root" v-html="rendered" />
</template>

<style scoped>
.md-root {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
}

.md-root :deep(h1) {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 16px;
}

.md-root :deep(h2) {
  font-size: 18px;
  font-weight: 600;
  margin: 24px 0 12px;
}

.md-root :deep(h3) {
  font-size: 15px;
  font-weight: 600;
  margin: 20px 0 8px;
}

.md-root :deep(p) {
  margin: 0 0 12px;
}

.md-root :deep(ul),
.md-root :deep(ol) {
  margin: 0 0 12px;
  padding-left: 24px;
}

.md-root :deep(li) {
  margin: 4px 0;
}

.md-root :deep(strong) {
  color: var(--text-primary);
}

.md-root :deep(a) {
  color: var(--color-primary);
}

.md-root :deep(.cite-ref) {
  display: inline-block;
  font-size: 11px;
  color: var(--color-primary);
  background: rgba(47, 109, 245, 0.1);
  border-radius: 4px;
  padding: 0 4px;
  margin: 0 2px;
  cursor: pointer;
  vertical-align: super;
  line-height: 1.6;
}

.md-root :deep(.cite-ref:hover) {
  background: rgba(47, 109, 245, 0.2);
}

.md-root :deep(pre.hljs) {
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  overflow-x: auto;
  font-size: 13px;
}

.md-root :deep(code) {
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
}

.md-root :deep(blockquote) {
  margin: 0 0 12px;
  padding: 4px 12px;
  border-left: 3px solid var(--border-base);
  color: var(--text-secondary);
}
</style>
