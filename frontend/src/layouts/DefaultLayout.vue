<script setup lang="ts">
import { computed, onMounted, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  HomeFilled,
  Search,
  Document,
  TrendCharts,
  DataAnalysis,
  Connection,
  User,
  Bell,
  ArrowDown,
  PieChart,
  Tickets,
  EditPen,
  DataLine,
  Setting
} from '@element-plus/icons-vue'
import { useAuth } from '@/composables/useAuth'

const route = useRoute()
const router = useRouter()
const { store, refreshMe, logout } = useAuth()

const envLabel = import.meta.env.VITE_ENV_LABEL
const backendHint = import.meta.env.VITE_BACKEND_HINT

interface MenuItem {
  key: string
  label: string
  icon: Component
  path?: string
  disabled?: boolean
}

interface MenuGroup {
  key: string
  label: string
  items: MenuItem[]
}

const menus: MenuItem[] = [
  { key: 'home', label: '首页', icon: HomeFilled, path: '/' },
  { key: 'rag', label: 'RAG 检索', icon: Search, path: '/rag' },
  { key: 'ingest', label: '知识入库', icon: DataAnalysis, disabled: true },
  { key: 'eval', label: '评估看板', icon: TrendCharts, disabled: true },
  { key: 'report', label: '研究报告', icon: Document, path: '/report/submit' },
  { key: 'trace', label: 'Trace 追踪', icon: Connection, disabled: true },
  { key: 'user', label: '用户中心', icon: User, disabled: true }
]

const adminGroup: MenuGroup = {
  key: 'admin',
  label: '管理中心',
  items: [
    { key: 'admin-stats',    label: '平台统计',    icon: PieChart,     path: '/admin/stats' },
    { key: 'admin-tasks',    label: '任务管理',    icon: Tickets,      path: '/admin/tasks' },
    { key: 'admin-prompts',  label: 'Prompt 管理', icon: EditPen,      path: '/admin/prompts' },
    { key: 'admin-judge',    label: 'Judge 评估',  icon: DataLine,     path: '/admin/judge' },
    { key: 'admin-workflow', label: 'Workflow 管理', icon: Setting,    path: '/admin/workflow' }
  ]
}

const activeMenu = computed(() => {
  const p = route.path
  if (p === '/') return 'home'
  if (p.startsWith('/rag')) return 'rag'
  if (p.startsWith('/report')) return 'report'
  if (p.startsWith('/admin/stats')) return 'admin-stats'
  if (p.startsWith('/admin/tasks')) return 'admin-tasks'
  if (p.startsWith('/admin/prompts')) return 'admin-prompts'
  if (p.startsWith('/admin/judge')) return 'admin-judge'
  if (p.startsWith('/admin/workflow')) return 'admin-workflow'
  return ''
})

function onMenuClick(item: MenuItem) {
  if (item.disabled || !item.path) return
  router.push(item.path)
}

function onUserCommand(cmd: string) {
  if (cmd === 'logout') logout()
}

onMounted(async () => {
  // 验证 token 仍有效（顺带刷新昵称等信息）
  if (store.isLoggedIn) {
    try {
      await refreshMe()
    } catch {
      /* 401 已由拦截器处理 */
    }
  }
})
</script>

<template>
  <div class="layout">
    <!-- 侧栏 -->
    <aside class="sidebar">
      <div class="sidebar-brand">
        <div class="brand-logo">行</div>
        <div class="brand-name">行业研报多 Agent 协作平台</div>
      </div>

      <nav class="sidebar-menu">
        <div
          v-for="m in menus"
          :key="m.key"
          class="menu-item"
          :class="{ active: activeMenu === m.key, disabled: m.disabled }"
          @click="onMenuClick(m)"
        >
          <el-icon :size="18"><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
        </div>

        <div class="menu-group-label">{{ adminGroup.label }}</div>
        <div
          v-for="m in adminGroup.items"
          :key="m.key"
          class="menu-item"
          :class="{ active: activeMenu === m.key, disabled: m.disabled }"
          @click="onMenuClick(m)"
        >
          <el-icon :size="18"><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
        </div>
      </nav>

      <div class="sidebar-footer">
        <div class="env-block">
          <div class="env-dot" />
          <div class="env-text">
            <div class="env-label">当前环境</div>
            <div class="env-value">{{ envLabel }} / {{ backendHint }}</div>
          </div>
        </div>
        <div class="version">v1.0.0</div>
      </div>
    </aside>

    <!-- 顶栏 + 主内容 -->
    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <el-icon :size="18" class="collapse-trigger"><HomeFilled /></el-icon>
        </div>

        <div class="topbar-search">
          <el-input placeholder="搜索文档、报告、任务、Trace…" :prefix-icon="Search" clearable />
        </div>

        <div class="topbar-right">
          <div class="env-tag">
            <span class="env-dot small" />
            {{ envLabel }} / {{ backendHint }}
          </div>

          <el-badge :value="12" class="notify-badge">
            <el-icon :size="18"><Bell /></el-icon>
          </el-badge>

          <el-dropdown trigger="click" @command="onUserCommand">
            <div class="user-trigger">
              <el-avatar :size="32" class="user-avatar">{{
                (store.user?.nickname || store.user?.username || '?').slice(0, 1)
              }}</el-avatar>
              <span class="user-name">{{ store.user?.nickname || store.user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ store.user?.username }}</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100%;
}

/* ===== Sidebar ===== */
.sidebar {
  width: var(--layout-sidebar-width);
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  height: var(--layout-topbar-height);
  padding: 0 16px;
  border-bottom: 1px solid var(--border-light);
}

.brand-logo {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  font-weight: 700;
  display: grid;
  place-items: center;
}

.brand-name {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 600;
}

.sidebar-menu {
  flex: 1;
  padding: 12px 8px;
  overflow-y: auto;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  cursor: pointer;
  user-select: none;
  font-size: 14px;
}

.menu-item:hover:not(.disabled) {
  background: var(--bg-muted);
  color: var(--text-primary);
}

.menu-item.active {
  background: rgba(47, 109, 245, 0.08);
  color: var(--color-primary);
  font-weight: 500;
}

.menu-item.disabled {
  color: var(--text-tertiary);
  cursor: not-allowed;
}

.menu-group-label {
  margin: 12px 12px 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-tertiary);
  letter-spacing: 0.4px;
}

.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border-light);
  font-size: 12px;
  color: var(--text-tertiary);
}

.env-block {
  display: flex;
  align-items: center;
  gap: 8px;
}

.env-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-success);
}

.env-dot.small {
  width: 6px;
  height: 6px;
}

.env-label {
  font-size: 11px;
  color: var(--text-tertiary);
}

.env-value {
  font-size: 12px;
  color: var(--text-secondary);
}

.version {
  margin-top: 8px;
  font-size: 11px;
}

/* ===== Main ===== */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: var(--layout-topbar-height);
  background: var(--bg-topbar);
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 16px;
  flex-shrink: 0;
}

.topbar-left .collapse-trigger {
  color: var(--text-tertiary);
  cursor: pointer;
}

.topbar-search {
  flex: 1;
  max-width: 560px;
}

.topbar-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 16px;
}

.env-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--border-base);
  border-radius: 999px;
  font-size: 12px;
  color: var(--text-secondary);
}

.notify-badge {
  cursor: pointer;
  color: var(--text-secondary);
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-md);
}

.user-trigger:hover {
  background: var(--bg-muted);
}

.user-avatar {
  background: var(--color-primary);
  color: #fff;
}

.user-name {
  font-size: 14px;
  color: var(--text-primary);
}

.content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
