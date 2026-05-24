<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { PromptTemplateVO } from '@/types/admin'

const props = defineProps<{
  items: PromptTemplateVO[]
  selectedId: number | null
}>()

const emit = defineEmits<{
  (e: 'select', id: number): void
}>()

const keyword = ref('')

interface Group {
  name: string
  versions: PromptTemplateVO[]
}

const groups = computed<Group[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  const map = new Map<string, PromptTemplateVO[]>()
  for (const p of props.items) {
    if (kw && !p.name.toLowerCase().includes(kw)) continue
    const arr = map.get(p.name) ?? []
    arr.push(p)
    map.set(p.name, arr)
  }
  return [...map.entries()].map(([name, versions]) => ({
    name,
    versions: [...versions].sort((a, b) => a.version.localeCompare(b.version))
  }))
})
</script>

<template>
  <div class="prompt-list">
    <el-input
      v-model="keyword"
      placeholder="搜索 Prompt 名称"
      :prefix-icon="Search"
      clearable
      size="default"
    />

    <el-collapse :default-active="groups.map((g) => g.name)" class="groups">
      <el-collapse-item
        v-for="g in groups"
        :key="g.name"
        :name="g.name"
        :title="g.name"
      >
        <div
          v-for="v in g.versions"
          :key="v.id"
          class="version-row"
          :class="{ active: v.id === selectedId }"
          @click="emit('select', v.id)"
        >
          <span class="version-label">{{ v.version }}</span>
          <el-tag
            v-if="v.isActive"
            type="success"
            size="small"
            effect="light"
          >生效</el-tag>
          <el-tag v-else type="info" size="small" effect="plain">历史版本</el-tag>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.prompt-list {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.groups {
  flex: 1;
  overflow-y: auto;
  border: none;
}
:deep(.el-collapse-item__header) {
  font-weight: 600;
  font-size: 13px;
  color: var(--text-primary);
}
.version-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
}
.version-row:hover { background: var(--bg-muted); }
.version-row.active {
  background: rgba(47, 109, 245, 0.08);
  color: var(--color-primary);
  font-weight: 500;
}
.version-label { font-family: 'JetBrains Mono', monospace; }
</style>
