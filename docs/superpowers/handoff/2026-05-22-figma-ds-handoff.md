# F-Figma · 设计系统反向工程 — 交付与停止说明

> **状态**：v1 部分交付，因 Figma Starter tier 限额停止
> **决定**：止步于此，接受当前交付（用户 2026-05-22 选择）
> **下一次 session 想继续**：先升级 Figma plan，参考 §"Resume Protocol"

---

## §0 Figma 文件

- **文件名**：`Industry Research Agent DS v1`
- **类型**：Design file
- **拥有者**：`Leo Zheng's team` (starter tier)
- **fileKey**：`Mwq5UncP3cI4km5TT2f3yo`
- **URL**：<https://www.figma.com/design/Mwq5UncP3cI4km5TT2f3yo>
- **State ledger**：`/tmp/dsb-state-industry-ds-v1.json`（包含所有 collection / variable / style 的 GUID，便于后续 session resume）

---

## §1 实际交付（已 commit 到 Figma 云端，不会丢）

### Phase 1 · Foundations（**100% 完成**）

| 集合 | 模式 | 变量 | scopes | Web 代码语法 |
|---|---|---|---|---|
| **Primitives** | Value | 12 colors | `[]`（隐藏，仅作 alias 目标）| 不暴露 |
| **Color** | Light | 16 semantic colors，**全部 alias 到 Primitives** | 按角色分（`FRAME_FILL`/`TEXT_FILL`/`STROKE_COLOR`/all-accent）| `var(--bg-app)` `var(--color-primary)` … 与 `frontend/src/styles/variables.css` **1:1 对齐** |
| **Spacing** | Value | 7 floats（4/8/12/16/20/24/32）| `[GAP]` | `var(--space-N)` |
| **Radius** | Value | 3 floats（4/8/12）| `[CORNER_RADIUS]` | `var(--radius-X)` |

**Effect Styles**：`shadow/card`（双层）+ `shadow/popover`（单层）
**Text Styles**：`text/heading/h1·h2·h3` + `text/body/default·sm·xs·strong`（Inter 字体族，Regular/Semi Bold，行高 PERCENT）

合计 **38 design tokens + 9 styles**。

### Phase 2 · File Structure & Docs（**~30%**）

- 3 个 page 骨架：`Foundations` · `Components`（空，原计划 Phase 3 填）· `Showcase`（空，原计划 Phase 4 填）
- Foundations 页有：
  - **Hero 区块**：大标题 + 副标
  - **Colors 文档段**：完整可视化色卡 —— 12 primitives（按 brand/status/neutrals 分行）+ 16 semantics（按 backgrounds/text/borders/brand/feedback 分行），每张色卡包含色块 + 变量名 + hex

### 未完成（受限于 Starter tier）

| 计划项 | 阻塞原因 |
|---|---|
| Foundations 剩余 4 段（Spacing/Radius/Typography/Shadows 可视化文档）| MCP 调用次数到顶 |
| Phase 3：3 个 Vue 组件 → Figma Components（StatCard / HitResultCard / CoreCapabilityCard）| MCP 调用次数到顶 |
| Phase 4：HomeView mockup 拼装页 | 同上 |
| Phase 5：Code Connect `.figma.ts` 映射 | 同上 + Code Connect 要求 Figma Professional+ Dev Mode |

---

## §2 撞墙记录（给后续 session 排坑用）

1. **Page 数 ≤ 3** — Starter 限制。原计划 13 页（Cover / Getting Started / Foundations 5 子页 / 2 分隔 / Components 3 页 / 分隔 / Showcase）。压缩成 3 页 + 内部 Sections 可绕开。
2. **MCP tool 调用次数封顶** — 单日/单周封顶后必须等额度刷新或升级。约第 **6 次** `use_figma` 写操作后撞墙（含 Phase 1 的 3 次 + Phase 2 的 2 次 = 5 次成功 + 第 6 次失败）。
3. **Code Connect 需 Pro+** — 还没走到，但 Phase 5 必撞。

---

## §3 调整后的简历亮点（v1 实际交付）

> 基于 Figma Plugin API 反向工程前端 CSS 变量为设计系统：4 个变量集合（Primitives / Color / Spacing / Radius）共 **38 个 design tokens**，全部带具体 `scopes` 与 `var(--xxx)` 形式的 Web 代码语法，与代码侧 `variables.css` **1:1 对齐**；semantic 颜色全部 alias 到 primitives，遵循 Material 3 / Polaris 的两层 token 架构。配套 9 个共享 style（2 shadows + 7 typography）。文件可被未来任何 Vue 组件直接订阅使用，为后续 Phase 3 组件库与 Code Connect 留出干净的接入面。

**面试讲点**：
- "为什么 primitives 用 `scopes: []`？" → 不让原始色出现在 UI 取色器，semantic 才是公共面
- "Web syntax 为什么是 `var(--bg-app)` 不是 `--bg-app`？" → Figma Dev Mode 默认不带 var() 包装，加上才能直接复制成可用的 CSS
- "为什么 semantic 必须 alias 而非复制 hex？" → 改 primitive 一处，所有 semantic 自动同步；复制 hex 会让多模式（Light/Dark）无法实现
- "Starter tier 撞了什么墙？" → 3 页 + MCP 次数 + Code Connect，这些是 Pro+ 的 SaaS 商业模型边界

---

## §4 你（用户）的手动收尾 checklist

1. 打开 [文件](https://www.figma.com/design/Mwq5UncP3cI4km5TT2f3yo)
2. 截图 1：右侧 `Local variables` 面板，展开 4 个 collection（作为"我建了 38 个变量"的视觉证据）
3. 截图 2：Foundations 页 Colors 段 全屏（作为"色板文档"的视觉证据）
4. 截图 3：Local styles 面板的 shadow + text 两组（作为"建了 9 个 style"的视觉证据）
5. 把 3 张截图归档到作品集 / 简历附件

---

## §5 Resume Protocol（未来 session 想继续 F-Figma）

如果未来升级了 Figma plan 并想继续：

1. 启动新 session，告诉 Claude：
   > "我要继续 F-Figma 项目（Industry Research Agent DS v1）。Run ID: `industry-ds-v1`。请先加载 `figma:figma-generate-library` skill，然后读取 `/tmp/dsb-state-industry-ds-v1.json` 恢复状态。下一步是 Phase 2 剩余（Spacing/Radius/Typography/Shadows 文档段），然后进 Phase 3。"
2. 文件 fileKey 在本 doc §0
3. 已建 GUID 在 state ledger 文件里
4. Phase 3 起点：3 个组件优先级 = StatCard > HitResultCard > CoreCapabilityCard
5. Phase 5 起点：Code Connect 需要 Figma Professional+ Dev Mode 才能用 `add_code_connect_map` MCP tool

---

## §6 关联文件

- Spec：`docs/superpowers/specs/2026-05-20-frontend-mvp-design.md` §1.1 / §6（F-Figma 原计划）
- Plan：（无独立 plan 文件 —— Phase 0 brainstorming 直接进 execution）
- 前一阶段 final commit：`4e045ac` (chore: polish — clear non-blocking issues)
