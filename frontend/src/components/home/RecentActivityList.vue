<script setup lang="ts">
import * as ElIcons from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import type { ActivityItem } from '@/types/dashboard'

const props = defineProps<{
  activities: ActivityItem[]
  /** 「查看全部」「查看更多活动」点击跳转的路由。未设置则不渲染这两个链接。 */
  moreLink?: string
}>()

const router = useRouter()

function iconOf(name: string) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const all = ElIcons as any as Record<string, unknown>
  return all[name] ?? ElIcons.Document
}

function onItemClick(a: ActivityItem) {
  if (a.to) router.push(a.to)
}

function onMoreClick() {
  if (props.moreLink) router.push(props.moreLink)
}
</script>

<template>
  <section class="rec-act">
    <header class="ra-header">
      <h3 class="ra-title">最近活动</h3>
      <a v-if="moreLink" class="ra-link" @click="onMoreClick">查看全部 ›</a>
    </header>

    <el-empty v-if="activities.length === 0" description="暂无最近活动" :image-size="60" />

    <ul v-else class="ra-list">
      <li
        v-for="a in activities"
        :key="a.id"
        class="ra-item"
        :class="{ clickable: !!a.to }"
        @click="onItemClick(a)"
      >
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

    <a v-if="moreLink && activities.length > 0" class="ra-more" @click="onMoreClick">查看更多活动 ›</a>
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

.ra-link:hover,
.ra-more:hover {
  text-decoration: underline;
}

.ra-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.ra-item {
  display: flex;
  gap: 10px;
  padding: 10px 8px;
  border-bottom: 1px dashed var(--border-light);
  border-radius: var(--radius-md);
}

.ra-item.clickable {
  cursor: pointer;
}

.ra-item.clickable:hover {
  background: var(--bg-muted);
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
  gap: 8px;
}

.ra-text {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
