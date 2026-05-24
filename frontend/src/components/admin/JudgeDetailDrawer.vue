<script setup lang="ts">
import { computed } from 'vue'
import JudgeScoreRadar from '@/components/admin/JudgeScoreRadar.vue'
import type { JudgeRunVO } from '@/types/admin'
import { formatEpochMillis } from '@/utils/format'

const props = defineProps<{
  modelValue: boolean
  run: JudgeRunVO | null
  reRunLoading: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 're-run'): void
}>()

const grade = computed(() => {
  if (!props.run) return ''
  const o = props.run.overall
  if (o >= 9) return '优秀'
  if (o >= 7) return '良好'
  if (o >= 5) return '待提升'
  return '不合格'
})

const gradeColor = computed(() => {
  if (!props.run) return ''
  const o = props.run.overall
  if (o >= 9) return 'success'
  if (o >= 7) return 'primary'
  if (o >= 5) return 'warning'
  return 'danger'
})

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})
</script>

<template>
  <el-drawer v-model="visible" :title="run ? `评分详情 #${run.taskId}` : ''" size="500">
    <template v-if="run">
      <div class="head">
        <div class="overall">
          <div class="overall-label">总分</div>
          <div class="overall-value">{{ run.overall.toFixed(1) }} <span>/ 10</span></div>
          <el-tag :type="gradeColor" size="small" effect="light">{{ grade }}</el-tag>
        </div>
        <JudgeScoreRadar :run="run" class="radar-box" />
      </div>

      <el-divider />

      <div class="meta">
        <div><span>评估规则</span><b>{{ run.rubricVersion }}</b></div>
        <div><span>裁判模型</span><b>{{ run.judgeModel }}</b></div>
        <div><span>耗时</span><b>{{ run.latencyMs }} ms</b></div>
        <div><span>评估时间</span><b>{{ formatEpochMillis(run.createTime) }}</b></div>
      </div>

      <el-divider />

      <div class="comments">
        <h3>评论区</h3>
        <div
          v-for="(text, dim) in run.comments"
          :key="dim"
          class="comment-card"
        >
          <div class="comment-dim">{{ dim }} 评语</div>
          <div class="comment-text">{{ text }}</div>
        </div>
        <el-empty v-if="!Object.keys(run.comments).length" description="暂无评语" />
      </div>

      <div class="footer">
        <el-button
          type="primary"
          size="large"
          :loading="reRunLoading"
          @click="emit('re-run')"
        >
          {{ reRunLoading ? '评分中…' : '对该任务再次评分（约 5-15s）' }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.head { display: flex; gap: 16px; align-items: center; }
.overall { flex-shrink: 0; min-width: 110px; }
.overall-label { font-size: 12px; color: var(--text-tertiary); }
.overall-value {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.1;
}
.overall-value span { font-size: 14px; color: var(--text-tertiary); font-weight: 400; }
.radar-box { flex: 1; min-width: 0; }
.meta { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.meta div { font-size: 13px; }
.meta span { color: var(--text-tertiary); margin-right: 8px; }
.meta b { color: var(--text-primary); font-weight: 500; }
.comments h3 { margin: 0 0 12px; font-size: 14px; }
.comment-card {
  background: var(--bg-muted);
  border-radius: var(--radius-md);
  padding: 12px;
  margin-bottom: 8px;
}
.comment-dim { font-size: 12px; color: var(--text-secondary); margin-bottom: 4px; font-weight: 500; }
.comment-text { font-size: 13px; color: var(--text-primary); line-height: 1.6; }
.footer { margin-top: 24px; text-align: center; }
</style>
