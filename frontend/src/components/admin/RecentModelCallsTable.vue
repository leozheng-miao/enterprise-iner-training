<script setup lang="ts">
import type { ModelCostVO } from '@/types/admin'
import { formatCny, formatNumber } from '@/utils/format'

defineProps<{
  data: ModelCostVO[]
}>()
</script>

<template>
  <div class="card">
    <header class="card-header">
      <h3>最近模型调用</h3>
      <a class="link" href="#">查看全部模型调用 ›</a>
    </header>
    <el-table :data="data" stripe size="small" style="width: 100%">
      <el-table-column prop="model" label="模型" min-width="140" />
      <el-table-column label="调用次数" width="100" align="right">
        <template #default="{ row }">{{ formatNumber(row.calls) }}</template>
      </el-table-column>
      <el-table-column label="输入 Token" width="120" align="right">
        <template #default="{ row }">{{ formatNumber(row.tokensIn) }}</template>
      </el-table-column>
      <el-table-column label="输出 Token" width="120" align="right">
        <template #default="{ row }">{{ formatNumber(row.tokensOut) }}</template>
      </el-table-column>
      <el-table-column label="总 Token" width="120" align="right">
        <template #default="{ row }">{{ formatNumber(row.tokensIn + row.tokensOut) }}</template>
      </el-table-column>
      <el-table-column label="成本（元）" width="110" align="right">
        <template #default="{ row }">{{ formatCny(row.costCny) }}</template>
      </el-table-column>
      <el-table-column label="平均耗时" width="100" align="right">
        <template #default="{ row }">
          {{ (row as ModelCostVO).avgLatencyMs == null ? '—' : `${(row as ModelCostVO).avgLatencyMs} ms` }}
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  padding: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.card-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.link {
  font-size: 12px;
  color: var(--color-primary);
}
</style>
