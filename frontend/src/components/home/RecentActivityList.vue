<script setup lang="ts">
import { computed } from 'vue'
import * as ElIcons from '@element-plus/icons-vue'
import type { ActivityItem } from '@/mock/dashboard'

const props = defineProps<{
  activities: ActivityItem[]
}>()

function iconOf(name: string) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const all = ElIcons as any as Record<string, unknown>
  return all[name] ?? ElIcons.Document
}

const _ = computed(() => props.activities.length)
</script>

<template>
  <section class="rec-act">
    <header class="ra-header">
      <h3 class="ra-title">最近活动</h3>
      <a class="ra-link">查看全部 ›</a>
    </header>

    <ul class="ra-list">
      <li v-for="a in activities" :key="a.id" class="ra-item">
        <span class="ra-bullet" :style="{ background: a.iconColor }">
          <el-icon :size="14" style="color: #fff;">
            <component :is="iconOf(a.iconName)" />
          </el-icon>
        </span>
        <div class="ra-body">
          <div class="ra-row">
            <span class="ra-text">{{ a.title }}</span>
            <span class="ra-time">{{ a.time }}</span>
          </div>
          <div class="ra-desc">{{ a.description }}</div>
        </div>
      </li>
    </ul>

    <a class="ra-more">查看更多活动 ›</a>
  </section>
</template>

<style scoped>
.rec-act {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}

.ra-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.ra-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary);
}

.ra-link,
.ra-more {
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
}

.ra-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.ra-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--border-light);
}

.ra-item:last-child {
  border-bottom: none;
}

.ra-bullet {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.ra-body {
  flex: 1;
  min-width: 0;
}

.ra-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ra-text {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 500;
}

.ra-time {
  font-size: 11px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.ra-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ra-more {
  display: block;
  text-align: center;
  margin-top: 12px;
}
</style>
