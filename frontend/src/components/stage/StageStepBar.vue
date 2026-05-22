<script setup lang="ts">
import { Check } from '@element-plus/icons-vue'
import type { StageNode } from '@/mock/dashboard'

defineProps<{
  nodes: StageNode[]
}>()
</script>

<template>
  <section class="stage-step-bar">
    <header class="ssb-header">
      <h3 class="ssb-title">项目阶段概览</h3>
      <div class="ssb-legend">
        <span class="legend-item">
          <span class="legend-dot done"><el-icon :size="10"><Check /></el-icon></span>
          已完成
        </span>
        <span class="legend-item">
          <span class="legend-dot current" />
          进行中
        </span>
        <span class="legend-item">
          <span class="legend-dot pending" />
          待开始
        </span>
      </div>
    </header>

    <div class="ssb-track">
      <div
        v-for="(n, idx) in nodes"
        :key="n.index"
        class="ssb-node"
        :class="n.status"
      >
        <div class="ssb-pill">
          <span class="ssb-pill-title">{{ n.title }}</span>
          <span class="ssb-pill-sub">{{ n.subtitle }}</span>
          <span v-if="n.status === 'done'" class="ssb-pill-icon done">
            <el-icon :size="12"><Check /></el-icon>
          </span>
          <span v-else-if="n.status === 'current'" class="ssb-pill-icon current">
            <span class="dot" />
          </span>
        </div>
        <div v-if="idx < nodes.length - 1" class="ssb-connector" :class="n.status" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.stage-step-bar {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px 24px;
}

.ssb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.ssb-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.ssb-legend {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--text-secondary);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.legend-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
}

.legend-dot.done {
  background: var(--color-success);
}

.legend-dot.current {
  background: var(--color-primary);
}

.legend-dot.pending {
  background: var(--bg-muted);
  border: 1px solid var(--border-base);
}

/* Track ===================================================== */
.ssb-track {
  display: flex;
  align-items: center;
}

.ssb-node {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.ssb-pill {
  position: relative;
  flex: 1;
  background: var(--bg-muted);
  border-radius: 999px;
  padding: 10px 32px 10px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.ssb-node.done .ssb-pill,
.ssb-node.current .ssb-pill {
  background: rgba(47, 109, 245, 0.06);
}

.ssb-pill-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}

.ssb-pill-sub {
  font-size: 12px;
  color: var(--text-tertiary);
}

.ssb-node.done .ssb-pill-title,
.ssb-node.current .ssb-pill-title {
  color: var(--color-primary);
}

.ssb-pill-icon {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
}

.ssb-pill-icon.done {
  background: var(--color-success);
}

.ssb-pill-icon.current {
  background: var(--color-primary);
}

.ssb-pill-icon.current .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
}

.ssb-connector {
  width: 14px;
  height: 2px;
  background: var(--border-base);
  flex-shrink: 0;
}

.ssb-connector.done {
  background: var(--color-primary);
}
</style>
