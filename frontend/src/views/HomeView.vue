<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const greetingHour = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const displayName = computed(
  () => auth.user?.nickname || auth.user?.username || '访客'
)
</script>

<template>
  <div class="home-placeholder">
    <h1 class="greeting">{{ greetingHour }}，{{ displayName }} 👋</h1>
    <p class="subtitle">
      欢迎使用 行业研报多 Agent 协作平台，助力高效研究与智能分析
    </p>

    <el-alert
      title="Phase F0 占位首页"
      type="info"
      :closable="false"
      class="hint"
    >
      <template #default>
        登录鉴权链路已贯通。Phase F1 将在此实现 dashboard（4 统计卡 / 6 阶段进度 / 核心能力 / 最近活动）。
      </template>
    </el-alert>

    <el-descriptions title="当前会话" :column="1" border class="session-info">
      <el-descriptions-item label="用户 ID">{{ auth.user?.id }}</el-descriptions-item>
      <el-descriptions-item label="用户名">{{ auth.user?.username }}</el-descriptions-item>
      <el-descriptions-item label="昵称">{{ auth.user?.nickname }}</el-descriptions-item>
      <el-descriptions-item label="角色">{{ auth.user?.role }}</el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<style scoped>
.home-placeholder {
  max-width: 720px;
}

.greeting {
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 8px;
  color: var(--text-primary);
}

.subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0 0 24px;
}

.hint {
  margin-bottom: 24px;
}

.session-info :deep(.el-descriptions__title) {
  font-size: 14px;
}
</style>
