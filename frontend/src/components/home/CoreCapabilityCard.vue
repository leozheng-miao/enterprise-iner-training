<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import * as ElIcons from '@element-plus/icons-vue'
import type { CoreCapability } from '@/mock/dashboard'

const props = defineProps<{
  item: CoreCapability
}>()

const router = useRouter()

const iconComponent = computed(() => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const all = ElIcons as any as Record<string, unknown>
  return all[props.item.iconName] ?? ElIcons.Cpu
})

const disabled = computed(() => !props.item.link)

function go() {
  if (disabled.value) return
  router.push(props.item.link)
}
</script>

<template>
  <div class="cap-card" :class="{ disabled }">
    <div class="cap-icon" :style="{ background: item.iconBg }">
      <el-icon :size="22"><component :is="iconComponent" /></el-icon>
    </div>
    <div class="cap-title">{{ item.title }}</div>
    <p class="cap-desc">{{ item.description }}</p>
    <a class="cap-link" @click.prevent="go">
      {{ disabled ? '即将上线' : '立即使用 →' }}
    </a>
  </div>
</template>

<style scoped>
.cap-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cap-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: var(--color-primary);
}

.cap-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.cap-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  margin: 0;
  line-height: 1.5;
  flex: 1;
}

.cap-link {
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
  margin-top: 4px;
  display: inline-block;
}

.cap-card.disabled .cap-link {
  color: var(--text-tertiary);
  cursor: not-allowed;
}
</style>
