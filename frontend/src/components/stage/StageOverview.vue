<script setup lang="ts">
import { computed } from 'vue'
import { Check } from '@element-plus/icons-vue'
import type { CurrentStageInfo, StageDetail } from '@/mock/dashboard'

const props = defineProps<{
  details: StageDetail[]
  current: CurrentStageInfo
  showCurrentCard?: boolean
}>()

const _ = computed(() => props.details.length)
</script>

<template>
  <section class="stage-overview">
    <header class="so-header">
      <h3 class="so-title">阶段进度总览</h3>
      <a class="so-link">查看详情 ›</a>
    </header>

    <div class="so-track">
      <div v-for="(d, idx) in details" :key="d.index" class="so-step">
        <div class="so-node" :class="d.status">
          <el-icon v-if="d.status === 'done'" :size="14"><Check /></el-icon>
          <span v-else>{{ d.index + 1 }}</span>
        </div>
        <div class="so-meta">
          <div class="so-meta-title">{{ d.title }}</div>
          <div class="so-meta-percent">{{ d.statusLabel }}</div>
          <div class="so-meta-date">{{ d.date || '—' }}</div>
        </div>
        <div v-if="idx < details.length - 1" class="so-connector" :class="d.status" />
      </div>
    </div>

    <div v-if="showCurrentCard !== false" class="so-current">
      <div class="so-current-body">
        <div class="so-current-title">
          <span class="so-pill">当前阶段</span>
          <span class="so-current-name">：{{ current.title }}</span>
        </div>
        <p class="so-current-desc">{{ current.description }}</p>
        <div class="so-current-grid">
          <div v-for="b in current.bullets" :key="b" class="so-current-bullet">
            <span class="bullet-dot" /> {{ b }}
          </div>
        </div>
      </div>
      <div class="so-current-illu" aria-hidden="true">
        <!-- 简单占位插画：堆叠的几何图块 -->
        <div class="illu illu-a" />
        <div class="illu illu-b" />
        <div class="illu illu-c" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.stage-overview {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px 24px;
}

.so-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.so-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.so-link {
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
}

.so-track {
  display: flex;
  align-items: flex-start;
  margin-bottom: 24px;
}

.so-step {
  flex: 1;
  display: flex;
  align-items: flex-start;
  position: relative;
  min-width: 0;
}

.so-node {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
  z-index: 1;
}

.so-node.done {
  background: var(--color-primary);
  color: #fff;
}

.so-node.current {
  background: var(--color-primary);
  color: #fff;
  box-shadow: 0 0 0 4px rgba(47, 109, 245, 0.15);
}

.so-node.pending {
  background: var(--bg-muted);
  color: var(--text-tertiary);
  border: 1px solid var(--border-base);
}

.so-meta {
  margin-left: 10px;
  min-width: 0;
}

.so-meta-title {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.so-meta-percent {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.so-meta-date {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.so-connector {
  position: absolute;
  top: 17px;
  left: calc(34px + 8px);
  right: 8px;
  height: 2px;
  background: var(--border-base);
  z-index: 0;
}

.so-connector.done {
  background: var(--color-primary);
}

/* Current stage card =========================================== */
.so-current {
  display: flex;
  gap: 24px;
  align-items: stretch;
  border-radius: var(--radius-lg);
  border: 1px solid rgba(47, 109, 245, 0.2);
  background: rgba(47, 109, 245, 0.04);
  padding: 20px;
}

.so-current-body {
  flex: 1;
  min-width: 0;
}

.so-current-title {
  display: flex;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
}

.so-pill {
  font-size: 12px;
  background: rgba(47, 109, 245, 0.12);
  border-radius: 4px;
  padding: 2px 8px;
}

.so-current-name {
  margin-left: 4px;
}

.so-current-desc {
  margin: 12px 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.so-current-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 24px;
}

.so-current-bullet {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.bullet-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--color-primary);
  flex-shrink: 0;
}

/* Illustration placeholder ================================== */
.so-current-illu {
  width: 160px;
  height: 110px;
  position: relative;
  flex-shrink: 0;
}

.illu {
  position: absolute;
  border-radius: 6px;
}

.illu-a {
  width: 90px;
  height: 60px;
  background: rgba(47, 109, 245, 0.2);
  bottom: 0;
  left: 0;
}

.illu-b {
  width: 70px;
  height: 90px;
  background: rgba(16, 185, 129, 0.18);
  bottom: 0;
  left: 50px;
}

.illu-c {
  width: 50px;
  height: 50px;
  background: rgba(245, 158, 11, 0.2);
  bottom: 30px;
  right: 0;
}
</style>
