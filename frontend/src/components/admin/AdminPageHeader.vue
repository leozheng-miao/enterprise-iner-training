<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <header class="admin-page-header">
    <div class="left">
      <h1 class="title">{{ title }}</h1>
      <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
    </div>
    <div class="right">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.admin-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.left { min-width: 0; }
.title {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 4px;
  color: var(--text-primary);
}
.subtitle {
  font-size: 13px;
  margin: 0;
  color: var(--text-secondary);
}
.right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
</style>
