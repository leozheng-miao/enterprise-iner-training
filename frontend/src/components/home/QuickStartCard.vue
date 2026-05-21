<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import * as ElIcons from '@element-plus/icons-vue'

const props = defineProps<{
  title: string
  description: string
  iconName: string
  iconBg: string
  to?: string
}>()

const router = useRouter()

const iconComponent = computed(() => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const all = ElIcons as any as Record<string, unknown>
  return all[props.iconName] ?? ElIcons.Search
})

function onClick() {
  if (props.to) router.push(props.to)
}
</script>

<template>
  <button class="qs-card" type="button" :disabled="!to" @click="onClick">
    <div class="qs-icon" :style="{ background: iconBg }">
      <el-icon :size="22"><component :is="iconComponent" /></el-icon>
    </div>
    <div class="qs-body">
      <div class="qs-title">{{ title }}</div>
      <div class="qs-description">{{ description }}</div>
    </div>
  </button>
</template>

<style scoped>
.qs-card {
  appearance: none;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  text-align: center;
  transition: border-color 0.15s, box-shadow 0.15s;
  font: inherit;
}

.qs-card:hover:not(:disabled) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(47, 109, 245, 0.08);
}

.qs-card:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.qs-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  color: var(--color-primary);
}

.qs-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.qs-description {
  font-size: 11px;
  color: var(--text-tertiary);
}
</style>
