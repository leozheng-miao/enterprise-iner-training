<script setup lang="ts">
import { computed } from 'vue'
import * as ElIcons from '@element-plus/icons-vue'
import { InfoFilled } from '@element-plus/icons-vue'
import type { StatItem } from '@/types/dashboard'

const props = defineProps<{
  item: StatItem
}>()

const iconComponent = computed(() => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const all = ElIcons as any as Record<string, unknown>
  return all[props.item.iconName] ?? ElIcons.Document
})

// InfoFilled 直接在模板里用；<script setup> 自动暴露 import 标识符给模板。
</script>

<template>
  <div class="stat-card">
    <div class="stat-icon" :style="{ background: item.iconBg }">
      <el-icon :size="22"><component :is="iconComponent" /></el-icon>
    </div>

    <div class="stat-body">
      <div class="stat-label">
        {{ item.label }}
        <el-icon class="stat-info" :size="12"><InfoFilled /></el-icon>
      </div>
      <div class="stat-value">
        {{ item.value }}<span v-if="item.unit" class="stat-unit">{{ item.unit }}</span>
      </div>
      <div v-if="item.delta" class="stat-delta">
        {{ item.delta.note }} <span class="delta-raw">{{ item.delta.raw }}</span>
        <span class="delta-percent" :class="item.delta.trend">({{ item.delta.percent }})</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: var(--color-primary);
  flex-shrink: 0;
}
.stat-body { flex: 1; min-width: 0; }
.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}
.stat-info { color: var(--text-tertiary); }
.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin-top: 4px;
  line-height: 1.2;
}
.stat-unit { font-size: 14px; color: var(--text-secondary); margin-left: 4px; }
.stat-delta { margin-top: 6px; font-size: 12px; color: var(--text-tertiary); }
.delta-raw { color: var(--text-secondary); font-weight: 500; }
.delta-percent.up { color: var(--color-success); }
.delta-percent.down { color: var(--color-danger); }
</style>
