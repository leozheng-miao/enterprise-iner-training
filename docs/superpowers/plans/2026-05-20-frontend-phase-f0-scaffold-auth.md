# Phase F0 · 前端脚手架 + 鉴权 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `frontend/` 子目录搭起 Vite + Vue3 + TS 项目，完成 JWT 鉴权全链路（Axios 拦截器 / Pinia auth store / Vue Router 守卫 / 登录注册页 / 主布局骨架），用户能登录后看到首页占位页与自己的昵称。

**Architecture:** monorepo 子目录 `frontend/`，Vite dev server 通过 `/api` proxy 到后端 `localhost:8080`。Axios 拦截器统一注入 JWT、解 `BaseResponse<T>`、按 errorCode 分发 toast / 跳登录。Pinia `auth` store 持有 token + user，localStorage 持久化。路由守卫拦未登录请求。

**Tech Stack:** Vue 3.4+ / Vite 5+ / TypeScript 5+ / Pinia 2 / Vue Router 4 / Element Plus 2.7+ / Axios 1.6+ / `@element-plus/icons-vue`

---

## 重要约定（**所有 Task 必读**）

> 用户对 token 消耗极度敏感。下列命令**永远不由 agent 跑**，agent 写完代码即提交，由用户在 IDE 验证：
> - `npm install` / `npm run dev` / `npm run build` / `npm run lint`
> - 任何启动 / 重启后端服务的命令
> - `curl` 调后端接口
>
> Review 阶段也不要跑命令，只看 `git diff` 与本 plan / spec 静态对比。
>
> 每 Task 完成后 **直接 commit**，不写单测（spec §7 已定）。
>
> 所有 Task 都在仓库根目录 `enterprise-iner-training/` 下操作；新文件路径全部以 `frontend/` 开头。

---

## File Structure

本 Phase 产出文件清单（按 Task 顺序）：

```
frontend/                              # ← 新建子目录
├── .gitignore
├── .env.example
├── .env.development
├── package.json
├── vite.config.ts
├── tsconfig.json
├── tsconfig.node.json
├── env.d.ts
├── index.html
├── .eslintrc.cjs
├── .prettierrc.json
├── .prettierignore
└── src/
    ├── main.ts
    ├── App.vue
    ├── styles/
    │   ├── variables.css
    │   └── main.css
    ├── types/
    │   ├── api.ts                     # BaseResponse + ErrorCode
    │   └── auth.ts                    # User / Login / Register
    ├── api/
    │   ├── client.ts                  # axios 实例 + 拦截器
    │   └── user.ts                    # register / login / me
    ├── stores/
    │   └── auth.ts                    # Pinia auth store
    ├── composables/
    │   └── useAuth.ts                 # view 层语法糖
    ├── router/
    │   └── index.ts                   # 路由表 + 守卫
    ├── layouts/
    │   └── DefaultLayout.vue          # 侧栏 + 顶栏骨架
    └── views/
        ├── LoginView.vue
        ├── RegisterView.vue
        └── HomeView.vue               # 占位首页（仅显示 nickname）
```

外加 1 个修改：

- Modify: `.gitignore`（根目录）→ 追加 `frontend/node_modules/`、`frontend/dist/`

---

## Task 1: 创建 frontend/ 子目录与 Vite 脚手架配置

**Files:**
- Create: `frontend/.gitignore`
- Create: `frontend/.env.example`
- Create: `frontend/.env.development`
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/env.d.ts`
- Create: `frontend/index.html`
- Modify: `.gitignore`（仓库根，追加 frontend 产物）

- [ ] **Step 1.1: 追加根 `.gitignore`**

在仓库根 `.gitignore` 末尾追加：

```gitignore

# Frontend (Vite + Vue3)
frontend/node_modules/
frontend/dist/
frontend/.env.local
frontend/.env.*.local
frontend/*.log
```

- [ ] **Step 1.2: 创建 `frontend/.gitignore`**

```gitignore
node_modules/
dist/
*.log
.env.local
.env.*.local
.DS_Store
.vscode/*
!.vscode/extensions.json
.idea/
*.suo
*.ntvs*
*.njsproj
*.sln
*.sw?
```

- [ ] **Step 1.3: 创建 `frontend/.env.example`**

```bash
# 后端 API 基础路径（通过 Vite proxy 转到后端 8080）
VITE_API_BASE=/api

# 环境标识，仅 UI 顶栏显示
VITE_ENV_LABEL=Dev
VITE_BACKEND_HINT=localhost:8080
```

- [ ] **Step 1.4: 创建 `frontend/.env.development`**

```bash
VITE_API_BASE=/api
VITE_ENV_LABEL=Dev
VITE_BACKEND_HINT=localhost:8080
```

- [ ] **Step 1.5: 创建 `frontend/package.json`**

```json
{
  "name": "enterprise-iner-frontend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "preview": "vite preview",
    "lint": "eslint . --ext .vue,.ts,.tsx --fix",
    "format": "prettier --write \"src/**/*.{vue,ts,tsx,css,json,md}\""
  },
  "dependencies": {
    "@element-plus/icons-vue": "^2.3.1",
    "@vueuse/core": "^10.11.0",
    "axios": "^1.7.2",
    "element-plus": "^2.7.6",
    "pinia": "^2.1.7",
    "vue": "^3.4.31",
    "vue-router": "^4.4.0"
  },
  "devDependencies": {
    "@types/node": "^20.14.10",
    "@typescript-eslint/eslint-plugin": "^7.16.0",
    "@typescript-eslint/parser": "^7.16.0",
    "@vitejs/plugin-vue": "^5.0.5",
    "@vue/eslint-config-prettier": "^9.0.0",
    "@vue/eslint-config-typescript": "^13.0.0",
    "eslint": "^8.57.0",
    "eslint-plugin-vue": "^9.27.0",
    "prettier": "^3.3.2",
    "typescript": "^5.5.3",
    "vite": "^5.3.3",
    "vue-tsc": "^2.0.26"
  }
}
```

- [ ] **Step 1.6: 创建 `frontend/vite.config.ts`**

```ts
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: false,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true
        },
        '/doc.html': 'http://localhost:8080',
        '/v3/api-docs': 'http://localhost:8080',
        '/webjars': 'http://localhost:8080'
      }
    },
    build: {
      sourcemap: true,
      target: 'es2020'
    }
  }
})
```

- [ ] **Step 1.7: 创建 `frontend/tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "preserve",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    },
    "types": ["node", "vite/client"]
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue", "env.d.ts"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

- [ ] **Step 1.8: 创建 `frontend/tsconfig.node.json`**

```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "types": ["node"]
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 1.9: 创建 `frontend/env.d.ts`**

```ts
/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE: string
  readonly VITE_ENV_LABEL: string
  readonly VITE_BACKEND_HINT: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const component: DefineComponent<{}, {}, any>
  export default component
}
```

- [ ] **Step 1.10: 创建 `frontend/index.html`**

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/vite.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>行业研报多 Agent 协作平台</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

- [ ] **Step 1.11: Commit**

```bash
git add .gitignore frontend/.gitignore frontend/.env.example frontend/.env.development \
  frontend/package.json frontend/vite.config.ts frontend/tsconfig.json \
  frontend/tsconfig.node.json frontend/env.d.ts frontend/index.html
git commit -m "$(cat <<'EOF'
feat(frontend): scaffold Vite + Vue3 + TS project with proxy config

- Create frontend/ subdirectory with package.json, Vite config, tsconfig
- Wire /api proxy to localhost:8080 for dev
- Add env files and TS shims for Vue SFCs + import.meta.env

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: ESLint + Prettier 配置

**Files:**
- Create: `frontend/.eslintrc.cjs`
- Create: `frontend/.prettierrc.json`
- Create: `frontend/.prettierignore`

- [ ] **Step 2.1: 创建 `frontend/.eslintrc.cjs`**

```js
/* eslint-env node */
module.exports = {
  root: true,
  env: { browser: true, es2022: true, node: true },
  extends: [
    'plugin:vue/vue3-recommended',
    'eslint:recommended',
    '@vue/eslint-config-typescript',
    '@vue/eslint-config-prettier'
  ],
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module'
  },
  rules: {
    'vue/multi-word-component-names': 'off',
    '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    'no-console': ['warn', { allow: ['warn', 'error'] }]
  }
}
```

- [ ] **Step 2.2: 创建 `frontend/.prettierrc.json`**

```json
{
  "semi": false,
  "singleQuote": true,
  "trailingComma": "none",
  "printWidth": 100,
  "tabWidth": 2,
  "endOfLine": "lf",
  "vueIndentScriptAndStyle": false
}
```

- [ ] **Step 2.3: 创建 `frontend/.prettierignore`**

```
node_modules
dist
*.log
.env*
```

- [ ] **Step 2.4: Commit**

```bash
git add frontend/.eslintrc.cjs frontend/.prettierrc.json frontend/.prettierignore
git commit -m "$(cat <<'EOF'
chore(frontend): add ESLint + Prettier configs

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: 公共样式（CSS 变量 + 全局 reset）

**Files:**
- Create: `frontend/src/styles/variables.css`
- Create: `frontend/src/styles/main.css`

- [ ] **Step 3.1: 创建 `frontend/src/styles/variables.css`**

从设计图 #1 #2 #3 反推的核心调色板（蓝色主调，浅灰背景，与 Element Plus 默认主题兼容）。

```css
:root {
  /* Brand colors */
  --color-primary: #2f6df5;
  --color-primary-hover: #4f86ff;
  --color-success: #10b981;
  --color-warning: #f59e0b;
  --color-danger: #ef4444;

  /* Backgrounds */
  --bg-app: #f5f7fb;
  --bg-card: #ffffff;
  --bg-sidebar: #ffffff;
  --bg-topbar: #ffffff;
  --bg-muted: #f1f3f8;

  /* Text */
  --text-primary: #1f2937;
  --text-secondary: #4b5563;
  --text-tertiary: #9ca3af;
  --text-inverse: #ffffff;

  /* Border */
  --border-base: #e5e7eb;
  --border-light: #f1f3f8;

  /* Spacing */
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-8: 32px;

  /* Radius */
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;

  /* Shadow */
  --shadow-card: 0 1px 2px 0 rgba(15, 23, 42, 0.04), 0 1px 3px 0 rgba(15, 23, 42, 0.06);
  --shadow-popover: 0 10px 30px rgba(15, 23, 42, 0.08);

  /* Layout */
  --layout-sidebar-width: 220px;
  --layout-topbar-height: 60px;
}
```

- [ ] **Step 3.2: 创建 `frontend/src/styles/main.css`**

```css
@import './variables.css';

* {
  box-sizing: border-box;
}

html,
body,
#app {
  height: 100%;
  margin: 0;
  padding: 0;
}

body {
  font-family:
    -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue',
    Arial, sans-serif;
  font-size: 14px;
  color: var(--text-primary);
  background-color: var(--bg-app);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

a {
  color: var(--color-primary);
  text-decoration: none;
}

a:hover {
  color: var(--color-primary-hover);
}

/* Element Plus 主色覆盖（轻量，避免重写整套 SCSS 变量） */
:root {
  --el-color-primary: var(--color-primary);
}
```

- [ ] **Step 3.3: Commit**

```bash
git add frontend/src/styles/
git commit -m "$(cat <<'EOF'
style(frontend): add CSS variables (colors/spacing/radius) and global reset

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: 类型定义（BaseResponse + ErrorCode + Auth 模型）

**Files:**
- Create: `frontend/src/types/api.ts`
- Create: `frontend/src/types/auth.ts`

- [ ] **Step 4.1: 创建 `frontend/src/types/api.ts`**

```ts
/**
 * 后端统一响应包装。
 * 所有非 SSE 接口都返回这个结构。
 */
export interface BaseResponse<T> {
  code: number
  data: T | null
  message: string
}

/**
 * 错误码（与后端 com.leo.enterpriseinertraining.exception.ErrorCode 对齐）。
 */
export const ErrorCode = {
  SUCCESS: 0,
  PARAMS_ERROR: 40000,
  USERNAME_EXISTS: 40010,
  USER_NOT_FOUND: 40011,
  BAD_CREDENTIALS: 40012,
  NOT_LOGIN: 40100,
  NO_AUTH: 40101,
  JWT_INVALID: 40110,
  FORBIDDEN: 40300,
  NOT_FOUND: 40400,
  TOO_MANY_REQUESTS: 42900,
  SYSTEM_ERROR: 50000
} as const

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode]

/**
 * 触发自动跳登录的错误码集合（401/jwt 失效）。
 */
export const AUTH_FAIL_CODES: ReadonlyArray<number> = [
  ErrorCode.NOT_LOGIN,
  ErrorCode.JWT_INVALID
]

/**
 * 业务异常（response.code !== 0 时由拦截器抛出）。
 */
export class BizError extends Error {
  constructor(
    public readonly code: number,
    public readonly bizMessage: string
  ) {
    super(bizMessage)
    this.name = 'BizError'
  }
}
```

- [ ] **Step 4.2: 创建 `frontend/src/types/auth.ts`**

```ts
export type UserRole = 'USER' | 'ADMIN'

export interface User {
  id: number
  username: string
  nickname: string
  role: UserRole
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  user: User
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
}

export interface RegisterResponse {
  id: number
  username: string
  nickname: string
  role: UserRole
}
```

- [ ] **Step 4.3: Commit**

```bash
git add frontend/src/types/
git commit -m "$(cat <<'EOF'
feat(frontend): define BaseResponse, ErrorCode and Auth types

Aligned with backend com.leo.enterpriseinertraining.exception.ErrorCode
and User/Login/Register DTOs.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Axios 实例 + 拦截器（JWT 注入 / BaseResponse 解包 / 错误分发）

**Files:**
- Create: `frontend/src/api/client.ts`

- [ ] **Step 5.1: 创建 `frontend/src/api/client.ts`**

```ts
import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from 'axios'
import { ElMessage } from 'element-plus'
import {
  AUTH_FAIL_CODES,
  BizError,
  ErrorCode,
  type BaseResponse
} from '@/types/api'

const BASE_URL = import.meta.env.VITE_API_BASE || '/api'

const instance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' }
})

// ===== 请求拦截器：注入 JWT =====
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

// ===== 响应拦截器：解 BaseResponse + 错误分发 =====
// 用一个延迟解析的回调避免循环 import（client.ts 不直接 import authStore）
let onAuthFail: (() => void) | null = null
export function setAuthFailHandler(handler: () => void): void {
  onAuthFail = handler
}

instance.interceptors.response.use(
  (resp: AxiosResponse<BaseResponse<unknown>>) => {
    const body = resp.data
    if (!body || typeof body.code !== 'number') {
      // 非标准响应（如 SSE 直返），原样返回
      return resp
    }

    if (body.code === ErrorCode.SUCCESS) {
      // 把 data 直接放到 resp.data，调用方拿到的就是 T
      return { ...resp, data: body.data } as AxiosResponse<unknown>
    }

    // 业务失败
    const err = new BizError(body.code, body.message || '请求失败')

    if (AUTH_FAIL_CODES.includes(body.code)) {
      onAuthFail?.()
    } else {
      ElMessage.warning(err.bizMessage)
    }
    return Promise.reject(err)
  },
  (error) => {
    // 网络错误 / HTTP 非 2xx
    const status = error?.response?.status
    if (status === 401 || status === 403) {
      onAuthFail?.()
      return Promise.reject(new BizError(status, '登录已失效，请重新登录'))
    }
    const msg = error?.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

/**
 * 类型化的 GET / POST 封装。
 * 拦截器已把 BaseResponse.data 平铺出来，所以这里返回 T。
 */
export async function http<T>(config: AxiosRequestConfig): Promise<T> {
  const resp = await instance.request<T>(config)
  return resp.data as T
}

export const apiGet = <T>(url: string, params?: object) =>
  http<T>({ method: 'GET', url, params })

export const apiPost = <T>(url: string, data?: object, params?: object) =>
  http<T>({ method: 'POST', url, data, params })

export const apiClient = instance
```

- [ ] **Step 5.2: Commit**

```bash
git add frontend/src/api/client.ts
git commit -m "$(cat <<'EOF'
feat(frontend): axios client with JWT interceptor and BaseResponse unwrap

- Request interceptor injects Authorization: Bearer <token>
- Response interceptor unwraps BaseResponse, throws BizError on code != 0
- AUTH_FAIL_CODES (40100/40110) trigger external onAuthFail handler
  (registered later by auth store to avoid circular import)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Pinia auth store（token + user + localStorage 持久化）

**Files:**
- Create: `frontend/src/stores/auth.ts`

- [ ] **Step 6.1: 创建 `frontend/src/stores/auth.ts`**

```ts
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { User } from '@/types/auth'

const TOKEN_KEY = 'token'
const USER_KEY = 'user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<User | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  /** 从 localStorage 恢复（在 main.ts 应用启动时调用一次）。 */
  function bootFromStorage(): void {
    const t = localStorage.getItem(TOKEN_KEY)
    const u = localStorage.getItem(USER_KEY)
    if (t) token.value = t
    if (u) {
      try {
        user.value = JSON.parse(u) as User
      } catch {
        user.value = null
        localStorage.removeItem(USER_KEY)
      }
    }
  }

  function setSession(payload: { token: string; user: User }): void {
    token.value = payload.token
    user.value = payload.user
    localStorage.setItem(TOKEN_KEY, payload.token)
    localStorage.setItem(USER_KEY, JSON.stringify(payload.user))
  }

  function setUser(u: User): void {
    user.value = u
    localStorage.setItem(USER_KEY, JSON.stringify(u))
  }

  function logout(): void {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    token,
    user,
    isLoggedIn,
    isAdmin,
    bootFromStorage,
    setSession,
    setUser,
    logout
  }
})
```

- [ ] **Step 6.2: Commit**

```bash
git add frontend/src/stores/auth.ts
git commit -m "$(cat <<'EOF'
feat(frontend): Pinia auth store with localStorage persistence

setSession/setUser/logout/bootFromStorage cover login, profile refresh,
and page reload recovery.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: User API（register / login / me）

**Files:**
- Create: `frontend/src/api/user.ts`

- [ ] **Step 7.1: 创建 `frontend/src/api/user.ts`**

```ts
import { apiGet, apiPost } from './client'
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  User
} from '@/types/auth'

export const userApi = {
  register(body: RegisterRequest) {
    return apiPost<RegisterResponse>('/user/register', body)
  },
  login(body: LoginRequest) {
    return apiPost<LoginResponse>('/user/login', body)
  },
  me() {
    return apiGet<User>('/user/me')
  }
}
```

- [ ] **Step 7.2: Commit**

```bash
git add frontend/src/api/user.ts
git commit -m "$(cat <<'EOF'
feat(frontend): user API methods (register/login/me)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: useAuth composable（view 层语法糖）

**Files:**
- Create: `frontend/src/composables/useAuth.ts`

- [ ] **Step 8.1: 创建 `frontend/src/composables/useAuth.ts`**

```ts
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import type { LoginRequest, RegisterRequest } from '@/types/auth'

/**
 * 包一层 useRouter + ElMessage，让 LoginView/RegisterView 调起来更短。
 */
export function useAuth() {
  const router = useRouter()
  const store = useAuthStore()

  async function login(body: LoginRequest, redirect?: string): Promise<void> {
    const resp = await userApi.login(body)
    store.setSession(resp)
    ElMessage.success(`欢迎回来，${resp.user.nickname || resp.user.username}`)
    await router.push(redirect || '/')
  }

  async function register(body: RegisterRequest): Promise<void> {
    await userApi.register(body)
    ElMessage.success('注册成功，请登录')
    await router.push('/login')
  }

  async function refreshMe(): Promise<void> {
    const u = await userApi.me()
    store.setUser(u)
  }

  function logout(): void {
    store.logout()
    router.push('/login')
  }

  return { store, login, register, refreshMe, logout }
}
```

- [ ] **Step 8.2: Commit**

```bash
git add frontend/src/composables/useAuth.ts
git commit -m "$(cat <<'EOF'
feat(frontend): useAuth composable wrapping login/register/logout/refreshMe

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: 路由表 + 守卫

**Files:**
- Create: `frontend/src/router/index.ts`

- [ ] **Step 9.1: 创建 `frontend/src/router/index.ts`**

```ts
import {
  createRouter,
  createWebHistory,
  type RouteLocationNormalized,
  type RouteRecordRaw
} from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { requiresAuth: false, title: '注册' }
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { requiresAuth: true, title: '首页' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to: RouteLocationNormalized) => {
  const auth = useAuthStore()
  const requiresAuth = to.matched.some((r) => r.meta.requiresAuth)

  if (requiresAuth && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if ((to.name === 'login' || to.name === 'register') && auth.isLoggedIn) {
    return { path: '/' }
  }

  // 更新页面标题
  const title = to.meta.title as string | undefined
  if (title) {
    document.title = `${title} · 行业研报多 Agent 协作平台`
  }
  return true
})

export default router
```

- [ ] **Step 9.2: Commit**

```bash
git add frontend/src/router/index.ts
git commit -m "$(cat <<'EOF'
feat(frontend): router with login/register/home routes and auth guard

- requiresAuth guard with redirect query param
- already-logged-in users hitting /login auto-redirect to /
- title meta drives document.title

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: LoginView + RegisterView

**Files:**
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/RegisterView.vue`

- [ ] **Step 10.1: 创建 `frontend/src/views/LoginView.vue`**

```vue
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuth } from '@/composables/useAuth'

const route = useRoute()
const router = useRouter()
const { login } = useAuth()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 32, message: '长度 4-32', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '长度 6-64', trigger: 'blur' }
  ]
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const redirect = (route.query.redirect as string) || '/'
    await login({ username: form.username, password: form.password }, redirect)
  } catch {
    // ElMessage 已由拦截器或 useAuth 提示
  } finally {
    submitting.value = false
  }
}

function goRegister() {
  router.push('/register')
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <div class="brand">
          <div class="brand-logo">行</div>
          <div class="brand-name">行业研报多 Agent 协作平台</div>
        </div>
        <div class="auth-title">登录</div>
        <div class="auth-subtitle">欢迎使用，请输入你的账号</div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="onSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="4-32 位" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6-64 位" />
        </el-form-item>
        <el-button type="primary" :loading="submitting" class="auth-submit" @click="onSubmit">
          登录
        </el-button>
      </el-form>

      <div class="auth-footer">
        还没有账号？<a @click.prevent="goRegister">立即注册</a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #eef2ff 0%, #f5f7fb 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  width: 100%;
  max-width: 380px;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-popover);
  padding: 32px;
}

.auth-header {
  margin-bottom: 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
}

.brand-logo {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
}

.brand-name {
  font-size: 14px;
  color: var(--text-secondary);
}

.auth-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
}

.auth-subtitle {
  margin-top: 4px;
  color: var(--text-tertiary);
}

.auth-submit {
  width: 100%;
  margin-top: 8px;
}

.auth-footer {
  margin-top: 16px;
  font-size: 13px;
  color: var(--text-secondary);
  text-align: center;
}

.auth-footer a {
  color: var(--color-primary);
  cursor: pointer;
}
</style>
```

- [ ] **Step 10.2: 创建 `frontend/src/views/RegisterView.vue`**

```vue
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuth } from '@/composables/useAuth'

const router = useRouter()
const { register } = useAuth()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({
  username: '',
  password: '',
  passwordConfirm: '',
  nickname: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 32, message: '长度 4-32', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '长度 6-64', trigger: 'blur' }
  ],
  passwordConfirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, cb) => {
        if (value !== form.password) cb(new Error('两次密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined
    })
  } catch {
    /* ElMessage handled */
  } finally {
    submitting.value = false
  }
}

function goLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <div class="brand">
          <div class="brand-logo">行</div>
          <div class="brand-name">行业研报多 Agent 协作平台</div>
        </div>
        <div class="auth-title">注册</div>
        <div class="auth-subtitle">创建一个新账号</div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="onSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="4-32 位" />
        </el-form-item>
        <el-form-item label="昵称（可选）">
          <el-input v-model="form.nickname" placeholder="留空将使用用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6-64 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="passwordConfirm">
          <el-input
            v-model="form.passwordConfirm"
            type="password"
            show-password
            placeholder="再输入一次"
          />
        </el-form-item>
        <el-button type="primary" :loading="submitting" class="auth-submit" @click="onSubmit">
          注册
        </el-button>
      </el-form>

      <div class="auth-footer">
        已有账号？<a @click.prevent="goLogin">去登录</a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #eef2ff 0%, #f5f7fb 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  width: 100%;
  max-width: 380px;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-popover);
  padding: 32px;
}

.auth-header {
  margin-bottom: 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
}

.brand-logo {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--color-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
}

.brand-name {
  font-size: 14px;
  color: var(--text-secondary);
}

.auth-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
}

.auth-subtitle {
  margin-top: 4px;
  color: var(--text-tertiary);
}

.auth-submit {
  width: 100%;
  margin-top: 8px;
}

.auth-footer {
  margin-top: 16px;
  font-size: 13px;
  color: var(--text-secondary);
  text-align: center;
}

.auth-footer a {
  color: var(--color-primary);
  cursor: pointer;
}
</style>
```

- [ ] **Step 10.3: Commit**

```bash
git add frontend/src/views/LoginView.vue frontend/src/views/RegisterView.vue
git commit -m "$(cat <<'EOF'
feat(frontend): LoginView and RegisterView with Element Plus forms

- Username/password validation matches backend constraints (4-32 / 6-64)
- Register has passwordConfirm validator
- After login, useAuth redirects to ?redirect=... or '/'

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: DefaultLayout 骨架 + HomeView 占位

**Files:**
- Create: `frontend/src/layouts/DefaultLayout.vue`
- Create: `frontend/src/views/HomeView.vue`

- [ ] **Step 11.1: 创建 `frontend/src/layouts/DefaultLayout.vue`**

按设计图 #1 的左侧栏（7 个一级菜单）+ 顶栏（搜索框 / 环境标 / 通知 / 用户菜单）+ 底部环境信息。F0 阶段菜单先全部渲染但点击除"首页"外都暂时禁用（路由还没接上）。

```vue
<script setup lang="ts">
import { computed, onMounted } from 'vue'
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
  ArrowDown
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
  icon: unknown
  path?: string
  disabled?: boolean
}

const menus: MenuItem[] = [
  { key: 'home', label: '首页', icon: HomeFilled, path: '/' },
  { key: 'rag', label: 'RAG 检索', icon: Search, disabled: true },
  { key: 'ingest', label: '知识入库', icon: DataAnalysis, disabled: true },
  { key: 'eval', label: '评估看板', icon: TrendCharts, disabled: true },
  { key: 'report', label: '研究报告', icon: Document, disabled: true },
  { key: 'trace', label: 'Trace 追踪', icon: Connection, disabled: true },
  { key: 'user', label: '用户中心', icon: User, disabled: true }
]

const activeMenu = computed(() => {
  if (route.path === '/') return 'home'
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
```

- [ ] **Step 11.2: 创建 `frontend/src/views/HomeView.vue`**

F0 阶段的首页**只做占位**（验证 `/api/user/me` 成功 + 展示用户昵称）。完整的 dashboard（统计卡 / 阶段进度 / 核心能力）由 Phase F1 的 plan 实现。

```vue
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
```

- [ ] **Step 11.3: Commit**

```bash
git add frontend/src/layouts/DefaultLayout.vue frontend/src/views/HomeView.vue
git commit -m "$(cat <<'EOF'
feat(frontend): DefaultLayout (sidebar + topbar) and HomeView placeholder

- Sidebar shows 7 first-level menus; non-home items disabled until later phases
- Topbar has search box, env tag, notification badge, user dropdown with logout
- HomeView placeholder verifies /api/user/me by displaying current session info

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: App.vue + main.ts 总装

**Files:**
- Create: `frontend/src/App.vue`
- Create: `frontend/src/main.ts`

- [ ] **Step 12.1: 创建 `frontend/src/App.vue`**

```vue
<script setup lang="ts">
// 根组件仅渲染 router-view；布局由 DefaultLayout 提供。
</script>

<template>
  <router-view />
</template>
```

- [ ] **Step 12.2: 创建 `frontend/src/main.ts`**

main.ts 负责：
1. 创建 Vue app
2. 安装 Pinia
3. 在装路由前调用 `auth.bootFromStorage()`（守卫第一次执行时就要拿到 token 状态）
4. 装路由 + Element Plus
5. 把 auth 失败回调注册到 axios client（解循环依赖）

```ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { setAuthFailHandler } from './api/client'

import './styles/main.css'

async function bootstrap() {
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)

  // 在装路由之前恢复 auth 状态，守卫才能拿到正确的 isLoggedIn
  const auth = useAuthStore()
  auth.bootFromStorage()

  // 注册 401/40110 跳登录回调（避免 client.ts → store 的循环 import）
  setAuthFailHandler(() => {
    if (auth.isLoggedIn) auth.logout()
    if (router.currentRoute.value.name !== 'login') {
      router.push({
        path: '/login',
        query: { redirect: router.currentRoute.value.fullPath }
      })
    }
  })

  app.use(router)
  app.use(ElementPlus)

  await router.isReady()
  app.mount('#app')
}

bootstrap()
```

- [ ] **Step 12.3: Commit**

```bash
git add frontend/src/App.vue frontend/src/main.ts
git commit -m "$(cat <<'EOF'
feat(frontend): wire up App.vue and main.ts bootstrap

- Restore auth state from localStorage before router install
- Register auth-fail handler with axios client (breaks circular import)
- Install Pinia, Element Plus, and global styles

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase F0 出口验证（**由用户在 IDE 完成**）

> ⚠️ Agent 不要跑这些命令；写完所有 Task 后告诉用户照下面 checklist 自验。

- [ ] **V1: 安装依赖（用户在 IDE 终端跑）**

```bash
cd frontend
npm install
```

预期：`node_modules/` 生成，无红色 ERR。warn 可忽略。

- [ ] **V2: 启动后端**

用户在 IDE 启动 Spring Boot 应用（按 main 方法 Run），等 `Started ...Application` 日志出现。

- [ ] **V3: 启动前端 dev server**

```bash
cd frontend
npm run dev
```

预期：终端打印 `Local: http://127.0.0.1:5173/`。

- [ ] **V4: 注册流程**

1. 浏览器访问 `http://127.0.0.1:5173/`
2. 守卫将你跳到 `/login?redirect=%2F`
3. 点"立即注册" → 跳 `/register`
4. 填表注册（如 `testuser` / `Test1234` / 昵称`测试`）→ 弹"注册成功，请登录" → 跳 `/login`

- [ ] **V5: 登录流程**

1. 用刚注册的账号登录
2. 弹 toast "欢迎回来，测试"
3. 跳到 `/`，看到首页占位页：上午好/下午好 + 昵称 + 角色 USER
4. 顶栏右上角看到用户头像 + 昵称 + 12 通知 badge + 环境标 "Dev / localhost:8080"
5. 侧栏看到 7 个菜单，仅"首页"高亮可点，其他 6 个灰色不可点

- [ ] **V6: 刷新保持登录**

1. F5 刷新页面
2. 应仍停留在 `/`，不跳登录（localStorage 已恢复）

- [ ] **V7: 退出登录**

1. 点右上头像 → "退出登录"
2. 跳回 `/login`
3. 此时手动访问 `/` 应再次被守卫跳到 `/login`

- [ ] **V8: 401 自动跳登录**

1. 登录后，浏览器 DevTools → Application → Local Storage → 把 `token` 改成无效字符串
2. 刷新 `/`
3. 后端 `/api/user/me` 返回 40110 → axios 拦截器触发 setAuthFailHandler → 自动跳 `/login`

如果以上 8 项全过，Phase F0 验收通过。

---

## Self-Review（已执行）

**1. Spec coverage（对照 `2026-05-20-frontend-mvp-design.md` §6 Phase F0）：**

| Spec 子项 | 覆盖 Task |
|---|---|
| T0.1 Vite + ESLint/Prettier + 依赖锁定 | Task 1, 2 |
| T0.2 `api/client.ts` + JWT 注入 + BaseResponse + 错误码 | Task 5 |
| T0.3 `stores/auth.ts` + localStorage 持久化 + boot | Task 6, 12 |
| T0.4 路由表 + 守卫 + redirect query | Task 9 |
| T0.5 LoginView + RegisterView | Task 10 |
| T0.6 DefaultLayout 骨架 | Task 11 |
| T0.7 占位 HomeView + `/api/user/me` 验证 | Task 11 |

全部覆盖。额外补了 Task 3（公共 CSS 变量）/ Task 4（类型）/ Task 7（user API）/ Task 8（useAuth composable）/ Task 12（main.ts 总装），它们是 spec 隐含但未单列的支撑文件。

**2. Placeholder scan：** 无 TBD / TODO / "implement later"。所有代码块都是完整可运行的代码。

**3. Type consistency：**
- `User` / `LoginResponse` / `RegisterResponse` 在 Task 4 定义，Task 6/7/8/10/11 引用一致
- `BaseResponse<T>` / `BizError` / `ErrorCode` / `AUTH_FAIL_CODES` 在 Task 4 定义，Task 5 引用一致
- `setAuthFailHandler` 在 Task 5 export，Task 12 import 调用，签名 `(handler: () => void) => void` 对齐
- `useAuthStore` 返回的属性（`token / user / isLoggedIn / setSession / setUser / logout / bootFromStorage`）在 Task 6 定义，Task 8/9/11/12 引用一致

无类型漂移。
