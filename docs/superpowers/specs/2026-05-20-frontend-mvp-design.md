# 行业研报多 Agent 协作平台 — 前端 MVP 设计文档

> 项目代号：`enterprise-iner-training`（前端子目录 `frontend/`）
> 设计日期：2026-05-20
> 工期估算：**5-7 天**（MVP F0+F1+F2）+ **1-2 天**（F-Figma 收尾）
> 上游 spec：`~/.claude/plans/now-in-china-sorted-treasure.md`
> 后端交接：`docs/superpowers/handoff/2026-05-20-frontend-session-handoff.md`

---

## Context（为什么做这个前端）

### 求职定位
- 主项目是面向 1-3 年 Java 社招转 **AI Agent 后端工程师** 的简历亮点工程
- 后端阶段 0/1/2 已完成（用户/JWT、RAG Hybrid Search、Single-Agent + SSE + Trace）
- 前端 MVP 的角色：**让简历亮点能够 demo**——尤其是 SSE 流式输出 + Trace 时间轴这两个核心面试爆点

### 设计输入
- 三张高保真设计图：首页 dashboard / RAG 检索 / 研报详情（SSE+Trace）
- 视觉风格：浅色管理后台、Ant Design Pro / Element Plus 风、卡片栈、侧栏导航、统计图表
- 后端 API 全部就绪（详见交接手册 §2），响应统一 `BaseResponse<T>` 包装

### 简历亮点公式（MVP 完成后）
> 基于 Vue 3 + TypeScript + Element Plus 构建行业研报多 Agent 协作平台前端。
> - **JWT 鉴权全链路**：Axios 拦截器自动注入 / 401 自动跳登录 / Pinia 状态持久化
> - **SSE 流式渲染**：基于 `@microsoft/fetch-event-source` 自实现支持 Header 鉴权的 SSE 消费层，解析 5 种事件类型（node_status / tool / token / done / error），实时驱动左侧时间轴与右侧 Markdown
> - **Trace 可视化**：Element Plus 表格 + 可展开 JSON，呈现每次 LLM_CALL / TOOL_CALL 的 tokens / latency / status
> - **设计-代码双向绑定**：用 Figma Code Connect 把 Vue 组件映射到 Figma 设计系统，设计稿与代码同源

---

## §1 范围（MVP = Phase F0 → F2 + F-Figma 收尾）

### 1.1 In-scope（必交付）

| Phase | 内容 | 对应后端接口 |
|---|---|---|
| **F0 · 脚手架 + 鉴权** | Vite + Vue3 + TS 项目初始化、Axios 拦截器、Pinia auth store、Vue Router 守卫、登录/注册/我的信息页 | `/api/user/register`、`/login`、`/me`、`/health` |
| **F1 · 首页 + RAG 检索** | 主布局（侧栏+顶栏）、首页 dashboard（阶段进度 / 4 统计卡 / 核心能力卡 / 最近活动）、RAG 检索页（query+topK+Rerank+结果卡+引用面板+饼图+柱图） | `/api/rag/search` |
| **F2 · 研报 Agent UI**（**核心 demo**） | 提交主题页、研报详情页（左 SSE 实时流 + 右 Markdown 渲染 + 底 Trace 表格） | `/api/report/start`、`/{id}`、`/{id}/stream`、`/{id}/trace` |
| **F-Figma · 收尾** | 在 Figma 里反推设计系统（colors / spacing / typography variables）+ 复刻 3 个页面 + 关键组件用 Code Connect 映射到 Vue | — |

### 1.2 Out-of-scope（明确砍掉，留到下阶段）

| 砍掉 | 理由 | 后续承接 |
|---|---|---|
| RAG Ingest 触发页 | 入库是 admin 后台动作，与简历 demo 主线无关 | Phase F1b 扩展 |
| RAG Eval 看板（Recall / NDCG / Rerank Lift 图表） | 数据来自离线评估，先不做前端 | Phase F1b 扩展 |
| 暗色主题切换 | 加分项，不影响 MVP 验收 | Phase F-Polish |
| 多语言 i18n | 项目本身就是中文 demo | 不做 |
| 移动端精美适配 | "能正常显示即可"，不做断点细化 | 不做 |
| 多租户 / 角色权限 UI | 后端阶段 4 才落地 | 与阶段 4 同步 |
| 生产 build / 部署 | 只要 `npm run dev` 跑通 | 阶段 6 收口 |

---

## §2 技术栈定型

| 维度 | 选型 | 版本约束 | 选择理由 |
|---|---|---|---|
| 框架 | Vue 3 + `<script setup>` | ^3.4 | 国内主流、中文生态强、SSE 友好 |
| 构建 | Vite | ^5 | 启动快、DX 好 |
| 语言 | TypeScript | ^5 | 简历亮点、`openapi-typescript` 可自动生成接口类型 |
| 状态管理 | Pinia | ^2 | Vue 3 官方推荐 |
| 路由 | Vue Router | ^4 | 标配 |
| UI 库 | Element Plus | ^2.7 | 表格/Form/Drawer/Steps 组件齐全，覆盖三张设计图所有控件 |
| HTTP | Axios | ^1.6 | 拦截器写 JWT 注入 + 401 重定向 + BaseResponse 解包 |
| SSE | `@microsoft/fetch-event-source` | ^2 | 支持自定义 Header（原生 EventSource 不行） |
| Markdown 渲染 | `markdown-it` + `highlight.js` | latest | 渲染 LLM 输出，自定义 rule 处理 `[n]` 引用 |
| 图表 | ECharts | ^5（按需引入） | 来源饼图、Score 柱图（图2 右栏） |
| 工具集 | `@vueuse/core` | latest | useDebounceFn / useIntersectionObserver 等 |
| 类型生成 | `openapi-typescript` | latest | 一次 `/v3/api-docs` 拉取生成 `api/types.ts` |
| 代码规范 | ESLint + Prettier + `@vue/eslint-config-typescript` | latest | 统一风格 |

---

## §3 目录结构与模块边界

### 3.1 目录树（`enterprise-iner-training/frontend/`）

```
frontend/
├── package.json
├── vite.config.ts                  # /api → http://localhost:8080 proxy
├── tsconfig.json
├── .eslintrc.cjs
├── .prettierrc.json
├── index.html
├── src/
│   ├── main.ts                     # app 入口：pinia / router / element-plus
│   ├── App.vue                     # <router-view/>
│   │
│   ├── router/
│   │   └── index.ts                # 路由表 + meta.requiresAuth 守卫
│   │
│   ├── stores/
│   │   ├── auth.ts                 # token + currentUser + login/logout
│   │   └── report.ts               # 当前任务 + SSE 事件缓冲
│   │
│   ├── api/
│   │   ├── client.ts               # axios 实例 + 拦截器
│   │   ├── user.ts                 # login / register / me
│   │   ├── rag.ts                  # search
│   │   ├── report.ts               # start / get / trace
│   │   └── types.ts                # openapi-typescript 生成 + 手写补充
│   │
│   ├── composables/
│   │   ├── useSse.ts               # fetch-event-source 包装，回调式 API
│   │   └── useAuth.ts              # authStore 的 view 层语法糖
│   │
│   ├── layouts/
│   │   └── DefaultLayout.vue       # 侧栏 + 顶栏 + 环境标 + 通知 + 用户菜单
│   │
│   ├── views/
│   │   ├── LoginView.vue
│   │   ├── RegisterView.vue
│   │   ├── HomeView.vue            # 图1：dashboard
│   │   ├── rag/
│   │   │   └── RagSearchView.vue   # 图2：Hybrid Search
│   │   └── report/
│   │       ├── ReportSubmitView.vue
│   │       └── ReportDetailView.vue # 图3：SSE+Markdown+Trace
│   │
│   ├── components/
│   │   ├── common/
│   │   │   ├── PageHeader.vue
│   │   │   └── EmptyState.vue
│   │   ├── stat/
│   │   │   └── StatCard.vue              # 4 统计卡（图1）
│   │   ├── stage/
│   │   │   └── StageStepBar.vue          # 6 阶段水平进度（图1）
│   │   ├── rag/
│   │   │   ├── HitResultCard.vue         # 检索结果卡（图2）
│   │   │   └── CitationPanel.vue         # 右栏引用 + 饼图 + 柱图（图2）
│   │   ├── report/
│   │   │   ├── SseEventList.vue          # 左栏实时流（图3）
│   │   │   ├── MarkdownRenderer.vue      # 含 [n] 引用联动
│   │   │   └── TraceTable.vue            # 底部表格（图3）
│   │   └── chart/
│   │       ├── DonutChart.vue            # ECharts 饼图封装
│   │       └── BarChart.vue              # ECharts 柱图封装
│   │
│   ├── types/
│   │   ├── auth.ts | rag.ts | report.ts  # 手写补充类型
│   │   └── sse.ts                        # SSE 事件类型联合
│   │
│   ├── utils/
│   │   ├── format.ts                     # tookMs / 时间戳 / token 数 格式化
│   │   └── citation.ts                   # markdown-it [n] rule
│   │
│   └── styles/
│       ├── variables.css                  # CSS 变量（后续 F-Figma 改为 tokens）
│       └── main.css
└── ...
```

### 3.2 模块边界三原则

1. **`api/` 只管 HTTP I/O，不持有状态**：函数式，输入参数 → Promise<data>，错误抛出
2. **`stores/` 只管跨页状态**：auth（全局）、report（详情页跨组件）；单页内状态用 view 自己的 ref
3. **`components/` 默认 dumb**：状态通过 props/emit，副作用走 composables；只有 layouts/views 才能持有 store

---

## §4 数据流与状态

### 4.1 BaseResponse 与错误码

所有非 SSE 接口统一包装：

```ts
interface BaseResponse<T> {
  code: number      // 0=成功
  data: T | null
  message: string
}
```

错误码映射（`src/api/client.ts` 内常量表）：

| code | 处理 |
|---|---|
| 0 | resolve(data) |
| 40010 / 40011 / 40012 / 40000 | ElMessage.warning(message) + reject |
| 40100 / 40101 / 40110 | authStore.logout() + router.push('/login') |
| 50000 / 其他 | ElMessage.error(message) + reject |

### 4.2 鉴权链路

```
LoginView 提交
  → POST /api/user/login → BaseResponse 解包
  → authStore.token = data.token  +  localStorage.setItem('token', token)
  → authStore.user = data.user
  → router.push(redirect || '/')

axios request 拦截器：
  if (authStore.token) config.headers.Authorization = `Bearer ${authStore.token}`

axios response 拦截器：
  按 §4.1 错误码表分发

router.beforeEach：
  if (to.meta.requiresAuth && !authStore.token)
    next({ path: '/login', query: { redirect: to.fullPath } })

App.vue onMounted：
  authStore.bootFromLocalStorage() // 刷新页面后恢复
```

### 4.3 RAG 检索链路

```
RagSearchView：
  query / topK / useRerank 三个 ref
  → 点击"开始检索" → api.rag.search({ query, topK, useRerank })
  → 顶部统计卡（tookMs / hits.length / Rerank 状态）
  → 左侧结果列表（HitResultCard × N）
  → 右侧 CitationPanel 显示当前选中 hit 的 citation 详情
  → 右下 DonutChart（按 source 聚合 hits）+ BarChart（按 score 分桶）
```

### 4.4 研报 SSE 链路（最复杂）

```
ReportSubmitView：
  POST /api/report/start { topic } → { taskId, streamUrl }
  → router.push(`/report/${taskId}`)

ReportDetailView onMounted：
  1. GET /api/report/{id}                       // 拿初态
  2. if status ∈ {PENDING, RUNNING}:
       useSse(streamUrl) 开流
     else:
       直接渲染 finalMarkdown + citations
  3. SSE 事件按类型 push 到 reportStore.events[]
     - node_status → 左侧 timeline 状态徽章更新
     - tool       → timeline 加 tool 项
     - token      → 追加到 markdownBuffer（节流 60fps 渲染）
     - done       → markdownBuffer = finalMarkdown；citations = data.citations
     - error      → ElMessage.error + 关流
     - ping       → 忽略，但更新最后心跳时间（UI 显示"连接中"）
  4. status 变 DONE 后：GET /api/report/{id}/trace → 填底部 TraceTable
  onBeforeUnmount：sseHandle.close()
```

### 4.5 useSse composable 雏形

```ts
// src/composables/useSse.ts
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { useAuthStore } from '@/stores/auth'

interface SseHandlers {
  onNodeStatus?: (data: NodeStatusEvent) => void
  onTool?: (data: ToolEvent) => void
  onToken?: (data: TokenEvent) => void
  onDone?: (data: DoneEvent) => void
  onError?: (data: ErrorEvent) => void
  onPing?: () => void
}

export function useSse(url: string, handlers: SseHandlers) {
  const auth = useAuthStore()
  const ctrl = new AbortController()

  fetchEventSource(url, {
    headers: { Authorization: `Bearer ${auth.token}` },
    signal: ctrl.signal,
    openWhenHidden: false,         // 切走 tab 时暂停
    onmessage(ev) {
      const data = ev.data ? JSON.parse(ev.data) : {}
      switch (ev.event) {
        case 'node_status': handlers.onNodeStatus?.(data); break
        case 'tool':        handlers.onTool?.(data); break
        case 'token':       handlers.onToken?.(data); break
        case 'done':        handlers.onDone?.(data); ctrl.abort(); break
        case 'error':       handlers.onError?.(data); ctrl.abort(); break
        case 'ping':        handlers.onPing?.(); break
      }
    },
    onerror(err) {
      handlers.onError?.({ message: err.message })
      throw err   // 抛出后 fetch-event-source 不再重连
    }
  })

  onBeforeUnmount(() => ctrl.abort())
  return { close: () => ctrl.abort() }
}
```

---

## §5 硬骨头（每条对应一个 Task）

| 难点 | 风险 | 设计对策 |
|---|---|---|
| **SSE 鉴权 + 浏览器 EOF 处理** | fetch-event-source 抛错时机难定，重连策略可能造成重复事件 | AbortController 显式管理 + onerror 抛错关流 + 由用户手动"重新运行" |
| **Markdown 流式渲染抖动** | 每个 token 触发 `markdown-it.render(全部)` 会 lag | 节流 60fps（约 16ms）；用 `requestAnimationFrame` 合并多个 token；done 时一次性 final render |
| **`[n]` 引用与 CitationCard 联动** | DOM 高亮跨组件 | markdown-it 自定义 rule：`[1]` → `<sup data-cite="1" class="cite-ref">1</sup>`；点击触发 `report.activeCitation = 1`；CitationPanel watch + scrollIntoView + 高亮 1s |
| **Trace 表格 input/output JSON 折叠** | 长 JSON 撑爆表格 | `el-table` expandable row + `<pre><code class="language-json">`（hl.js）；后端已截断 300 字符 + "…" |
| **首页 6 阶段进度条** | el-steps 不支持自定义颜色组（done/进行中/待开始三态） | 自定义 `StageStepBar.vue`：flex 横排，节点圈用三种 class，连接线用 `:after` 伪元素 |
| **CORS** | Vite 5173 ↔ Spring 8080 跨域，后端当前 disable cors | Vite proxy `/api` → `http://localhost:8080`；前端永远发同源；**不动后端** |
| **环境标识 "Dev / localhost:8080"** | 不同环境（dev/prod）展示 | `import.meta.env.VITE_API_BASE` 渲染到顶栏 |

---

## §6 分阶段交付（每 Phase 一个 PR / 一次 verification）

### Phase F0 · 脚手架 + 鉴权（~1 天）

- **T0.1** Vite 项目初始化 + ESLint/Prettier + 依赖锁定
- **T0.2** `api/client.ts`：axios 实例 + JWT 注入 + BaseResponse 解包 + 错误码分发
- **T0.3** `stores/auth.ts`：token + user + login/logout + localStorage 持久化 + boot
- **T0.4** `router/index.ts`：路由表 + meta.requiresAuth 守卫 + redirect query
- **T0.5** `LoginView.vue` + `RegisterView.vue`（Element Plus 表单 + 校验）
- **T0.6** `DefaultLayout.vue` 骨架（侧栏菜单 + 顶栏 + 用户下拉 + 环境标）
- **T0.7** 占位 `HomeView.vue`（仅显示"欢迎，{nickname}"+ `/api/user/me` 调用验证）

**出口标准**：用户在 IDE 启动后端 → 访问 `http://localhost:5173/login` → 登录成功 → 跳到首页占位页 → 看到自己昵称。

### Phase F1 · 首页 + RAG 检索（~2 天）

- **T1.1** `HomeView`：顶部问候 + 4 个快速入口卡 + 6 阶段进度条
- **T1.2** `StatCard` × 4（已接入文档 / 查询次数 / 任务数 / 平均响应耗时）
- **T1.3** "阶段进度总览" 卡片 + "当前阶段" + 核心能力 cards + 最近活动列表（用 mock 数据，数据接口阶段 3/4 才有）
- **T1.4** `RagSearchView` 上半：query / topK / Rerank toggle / 开始检索 + 三统计卡
- **T1.5** `HitResultCard`：分数 + content + meta 网格（docTitle/source/sectionTitle/page）
- **T1.6** `CitationPanel`：选中 hit 详情 + 来源饼图（DonutChart）+ Score 柱图（BarChart）+ 检索配置
- **T1.7** 视觉打磨 + Loading/Empty/Error 三态

**出口标准**：能登录后跳首页，首页与图1一致；侧栏点击 "RAG 检索"，输入 query → 看到与图2一致的 hits + 右侧饼图柱图。

### Phase F2 · 研报 Agent UI（~2-3 天 · **核心 demo**）

- **T2.1** `ReportSubmitView`：topic 输入 + workflow 下拉 + 提交 → start → 路由跳详情
- **T2.2** `useSse` composable + 类型定义（5 种事件 + ping）
- **T2.3** `ReportDetailView` 框架：顶部 meta（任务 ID / 主题 / 状态 / 启动/完成/耗时）+ 右上"重新运行"/"下载报告"按钮 + 三栏布局
- **T2.4** `SseEventList`：左栏实时流，时间戳 + 事件类型徽章 + 内容片段
- **T2.5** `MarkdownRenderer`：markdown-it + 自定义 `[n]` rule + 流式 buffer + 节流 final render
- **T2.6** `TraceTable`：el-table + expandable row（input/output JSON 折叠 + hl.js 高亮）
- **T2.7** 全链路联调（提交 → 流式 → 成稿 → trace 落入表格）

**出口标准**：用户在 IDE 启动后端 → 前端提交主题 "2026 年新能源汽车行业趋势研究" → 跳详情 → 看到左侧 SSE 实时事件滚动 → 看到右侧 Markdown 逐字出现 → done 后看到底部 Trace 表格的所有 LLM_CALL / TOOL_CALL 步骤。

### Phase F-Figma · 收尾（~1-2 天 · MVP 后）

- **TF.1** `figma-create-new-file`：新建 design 文件
- **TF.2** `figma-use`：建 design tokens（primary/success/warning/danger 色板、4/8/12/16/24 spacing、12/14/16/20/28 字号）
- **TF.3** `figma-use`：建组件库（Button / Card / StatCard / HitResultCard / Badge），含变体（size/状态）
- **TF.4** `figma-use`：拼出 3 个页面（HomeView / RagSearchView / ReportDetailView）
- **TF.5** `figma-code-connect`：写 .figma.ts 映射到 Vue 组件

**出口标准**：在 Figma 文件里能看到与代码一致的 3 个页面，组件 Inspect 面板能看到对应 Vue 组件的 import 路径与 props。

---

## §7 关键技术决策（可质询点）

| 决策 | 选定值 | 理由 |
|---|---|---|
| **路由 mode** | history | URL 干净，Vite dev 自动兜底 |
| **token 持久化** | localStorage | MVP 阶段简单优先；安全性升级留到阶段 6 |
| **是否做 Mock 层** | 否 | 直连本地后端开发，避免 MSW 维护成本；后端启动用户在 IDE 负责 |
| **是否生成 OpenAPI 类型** | 部分 | auth 等核心类型手写（少且稳），rag/report 长结构用 `openapi-typescript` 生成 |
| **图标方案** | Element Plus 内置 + `@element-plus/icons-vue` | 不再引第三方图标库 |
| **CSS 方案** | scoped + CSS 变量 | F-Figma 阶段把变量改为 design tokens |
| **是否做单测** | 否 | MVP 不写 vitest；只做手工联调 + 静态 spec 比对 |

---

## §8 出口标准（前端 MVP 整体验收）

- [ ] 注册 / 登录 / 退出 / 我的信息全链路通
- [ ] Axios 401 拦截器自动跳登录
- [ ] 首页 dashboard 视觉与图1一致（mock 数据填充）
- [ ] RAG Hybrid Search 视觉与图2一致 + 引用 + 饼图柱图
- [ ] 研报详情视觉与图3一致 + SSE 实时流 + Markdown 流式 + Trace 表格
- [ ] 移动端基本可用（不强求精美）
- [ ] Loading / Empty / Error 三态覆盖
- [ ] `npm run dev` 本地能跑通
- [ ] Figma 设计文件与代码 3 个页面对齐 + Code Connect 关键组件映射（F-Figma 完成后）

---

## §9 与后端的对接节奏

| 阶段 | 后端状态 | 前端动作 |
|---|---|---|
| 当前（2026-05-20） | 阶段 0/1/2 已完成 | **可以开始 F0** |
| 阶段 3 落地后 | Multi-Agent + RocketMQ | ReportDetailView 加多节点 / 多 Agent 状态展示 |
| 阶段 4 落地后 | Prompt 版本管理 / Token 成本看板接口 | 加 RAG Ingest / Eval 页 + 平台中台页 |
| 阶段 6 落地后 | RAGAS / LLM-as-Judge | 加评估对比图表 |

---

## §10 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| 后端未启动导致联调失败 | 中 | 阻塞 | 每 Phase 出口标准明确"用户在 IDE 启动后端"；spec 不让 subagent 跑 docker/mvn |
| SSE 连接被 Vite proxy 切断 | 低 | F2 阻塞 | Vite 5 默认 SSE 友好；fallback 方案：后端加 `?token=` query 参数（需后端动） |
| Figma MCP 未授权 / 连接失败 | 中 | F-Figma 阻塞 | F-Figma 第一步 `figma:plugin_figma_figma__authenticate`；失败则降级为"用截图 + Mermaid 画架构图"作为简历素材 |
| Markdown 流式渲染性能差 | 中 | F2 体验差 | 节流 60fps + done 时 final render；最坏情况下退化为"每行 token 出现"而非"每字符" |
| openapi-typescript 与手写类型冲突 | 低 | 编译报错 | 接受并存：auth 手写，rag/report generated；生成产物加入 `.gitignore` 或锁定提交 |
| Token 预算超支 | 中 | 用户体验差 | 严格执行交接手册红线：不让 subagent 跑 mvn/npm/curl；review 阶段只 git diff 静态比对 |

---

## §11 后续步骤

1. 用户审阅本设计文档 → 通过
2. 切到 `superpowers:writing-plans` 写 **Phase F0 的可执行 plan**（bite-sized tasks + 完整代码）
3. 切到 `superpowers:subagent-driven-development` 逐 Task 执行
4. 每个 Phase 完成做 `superpowers:verification-before-completion`
5. 重复 2-4 直到 F0 → F1 → F2 → F-Figma 全部完成

---

**附：与上游 spec 的对齐**

- 本 spec 服务于 `~/.claude/plans/now-in-china-sorted-treasure.md` 全局规划中"Web 层（Vue3 + ElementPlus + SSE 流式渲染）"部分
- 阶段 3-6 的前端扩展不在本 spec 范围内，会在对应阶段独立 spec
