# 来信汇总页面 —— 前端（Plan B / 2）

> 依赖 **Plan A**（`inbound-mail-tag-backend.md`）的 `/api/inbound-summary/*` 接口。Plan A 未上线前本计划不可验收。
> 预览图：`/Users/lukai/.cursor/projects/Users-lukai-IdeaProjects-weibo-talent-introduction/assets/inbound-mail-summary-preview.png`

## 需求描述

**可观察结果**：左侧栏新增导航「来信汇总」，进入后：
- 顶部两图：**标签邮件数量排行**（横向条形）+ **标签占比**（环形/饼图），数据全量（决策#3）。
- 左侧：搜索 + 可点标签过滤 chip 行（含失效标签置灰、删除线）；来信列表，每行显示发件人、主题、时间、标签 chip；选中行高亮（蓝底 + 左蓝边）。
- 右侧：详情面板顶部「邮件标签」编辑区（每 chip 带删除 ×、`+ 添加标签`、`自动添加 QA 标签` 按钮、失效标签置灰）；下方该专家**历史来回记录**（气泡按方向左右分），当前选中那封高亮（金色描边）。
- 纯查看，不发信（决策#2）。

**不可改变**：现有 8 个视图（monitoring/accounts/qa/... /tasks）与既有 `app.js`/`styles.css` 行为；沿用现有 app 风格（决策：按现有 app 风格调整）。

**范围外**：任何发信/回复能力；后端逻辑（Plan A）；图表第三方库（用纯 CSS/SVG，与现有无图表库现状一致）。

## 关键不变量

### Invariant I-1: 置灰以后端 `active` 为准
- Rule: 标签 chip 是否置灰/删除线，**只依据**接口返回的 `tag.active` 字段，前端不自行判断规则是否存在。失效 chip 仍显示 `label` 文本，仅样式置灰、加删除线。
- Applies to: 列表行 chip、详情编辑区 chip、过滤 chip、统计项渲染。
- Violation consequence: 与后端置灰规则（Plan A I-2）不一致。
- 来源: original

### Invariant I-2: 统计全量、独立于列表过滤
- Rule: 两图数据来自 `GET /tags/stats`，与来信列表的标签/时间过滤**互不影响**；切换过滤只刷新列表，不刷新图（除非显式「刷新」）。
- Applies to: 图渲染与列表渲染的数据源分离。
- 来源: original（对齐 Plan A I-6）

### Invariant I-3: 当前来信高亮以 messageId 匹配
- Rule: 详情线程中「当前选中来信」的高亮，依据 `thread.currentMessageId` 与各条 `messageId` 相等匹配；无匹配（如线程为空/缺 messageId）时不高亮，不报错。
- Applies to: 线程渲染。
- 来源: original

## 现状审计

### 导航与视图切换（`index.html` + `app.js`）
- 侧栏 `<nav class="nav-tabs">` 内 `<button class="nav-tab" data-view="X">`；主区 `<section class="view" id="view-X">`。
- `app.js`:
  - `viewMeta`（:91）标题/副标题映射，新视图需加键。
  - `setView(view)`（:1185）：切 `.active`、设标题、`refreshCurrentView()`。
  - `refreshCurrentView()`（:1203）：按 `state.view` 分派 `loadXxx()`，新视图需加分派。
  - `state`（:24 起）：新增 `inboundSummary` 子状态。
- 通用工具：`api()`（fetch 封装）、`escapeHtml`、`badge()`、`$`/`$$`、分页范式（见 `mailboxPagination`）。
- 邮件正文渲染范式：`.pre`（`white-space:pre-wrap` + `escapeHtml`），见 K-mail-body-display-sites；线程气泡正文沿用 `escapeHtml`。

### 样式（`styles.css`）
- 变量：`--primary`、`--border`、`--surface`、`--panel-bg`、`--text-muted`、`--radius-sm` 等；`.panel`/`.panel-head`/`.button`/`.badge`/`.split-layout` 可复用（专家页 `.split-layout contacts-layout` 是左右分栏 + resizer 参考）。
- 无图表库 → 条形图用 div 宽度百分比；饼/环用内联 SVG `stroke-dasharray`。

### 接口（Plan A 提供）
`GET /api/inbound-summary/mails|/mails/{id}/thread|/tags/stats|/tags/options`、`POST /mails/{id}/tags/auto|/mails/{id}/tags`、`DELETE /tags/{id}`。

## 实现方案

### Task 1 `index.html`
- 侧栏在「收发件箱」后加 `<button class="nav-tab" data-view="inbound-summary">`（复用 svg 图标 + `<span>来信汇总</span>`）。
- 主区加 `<section class="view" id="view-inbound-summary">`：
  - toolbar：日期区间 `from/to` + 刷新按钮。
  - 图区 panel：两 `.panel` 并排（`#inboundTagBarChart`、`#inboundTagPieChart`）。
  - 主体 `.split-layout`：左 `.panel`（搜索框 `#inboundSearch` + 过滤 chip 容器 `#inboundTagFilters` + 列表 `#inboundMailList` + 分页 `#inboundPagination`）；右 `.panel`（标签编辑区 `#inboundTagEditor`（含 `自动添加 QA 标签`/`+ 添加标签`）+ 线程 `#inboundThread`）。

### Task 2 `app.js`
- `state.inboundSummary = { from, to, page, pageSize, activeTagKey:"", search:"", mails:[], total:0, selectedId:null, stats:null, options:[] }`。
- `viewMeta["inbound-summary"] = ["来信汇总", "按标签汇总来信、查看往来记录与标签统计。"]`。
- `refreshCurrentView()` 加：`if (state.view==="inbound-summary") await loadInboundSummary();`。
- `loadInboundSummary()`：并行拉 `stats`+`options`+`mails`；渲染两图（I-2）、过滤 chip（I-1，失效置灰）、列表。
- `loadInboundMails()`：按 `activeTagKey/from/to/page` 拉 `/mails`，渲染行（发件人/主题/时间 + tags chip，I-1）；点行 → `selectInboundMail(id)`。
- `selectInboundMail(id)`：拉 `/mails/{id}/thread`；渲染标签编辑区（chip + × 删除 + `自动添加 QA 标签` + `+ 添加标签`）与线程（气泡左右分：INBOUND 左白 / OUTBOUND 右蓝；`currentMessageId` 高亮，I-3）；列表选中行加高亮类。
- 标签操作：
  - 点过滤 chip → 设 `activeTagKey`（再点取消）→ `loadInboundMails()`（不动图，I-2）。
  - 编辑区 × → `DELETE /tags/{tagId}` → 重渲染当前详情 + 刷新列表行 + 刷新图。
  - `自动添加 QA 标签` → `POST /mails/{id}/tags/auto` → 重渲染。
  - `+ 添加标签` → 轻量弹层：选 QA 规则（下拉，来自 `/tags/options` 或 QA 规则接口）或输入自定义文本 → `POST /mails/{id}/tags`。
- 图渲染：`renderTagBarChart(stats)` div 百分比条；`renderTagPieChart(stats)` SVG 环 + 图例（label/count/百分比）；失效项灰色（I-1）。
- 事件绑定：日期/刷新/搜索走既有 toolbar 范式；导航点击已由现有 `data-view` 委托处理（确认 `setView` 通用）。

### Task 3 `styles.css`
- `.inbound-tag-chip`（QA 彩色 / CUSTOM 中性）、`.inbound-tag-chip.inactive`（灰 + `text-decoration: line-through`，I-1）、`.inbound-tag-chip .chip-x`。
- `.inbound-mail-row` / `.inbound-mail-row.selected`（蓝底 + `border-left` 蓝）。
- `.inbound-thread-bubble.inbound`（左白）/`.outbound`（右蓝）/`.current`（金色 outline glow，I-3）。
- `.tag-bar-chart .bar` 宽度百分比 + 数值；`.tag-pie` SVG 容器 + `.tag-pie-legend`。
- 复用现有 `.split-layout`；如需可调宽度沿用 `layout-resizer`（可选，先不做）。

## 变更文件清单

| # | 文件 | 类型 |
|---|------|------|
| 1 | `src/main/resources/static/index.html` | 修改（导航 + 视图骨架） |
| 2 | `src/main/resources/static/app.js` | 修改（视图逻辑 + 图 + 标签操作） |
| 3 | `src/main/resources/static/styles.css` | 修改（chip/列表/气泡/图样式） |

计 3 文件（≤10 ✓）。子系统：前端静态（1 ✓）。

## 验收标准
- I-1: 停用某 QA 规则后刷新汇总页，其标签 chip（列表/详情/过滤/图例）灰显 + 删除线，文字仍在；启用后恢复。
- I-2: 切换标签过滤只改列表，两图数值不变；点「刷新」后图按全量更新。
- I-3: 点某来信，右侧线程中该 messageId 气泡金色高亮，其余不高亮；线程空/无 contact 时仅显示本封、无报错。
- 功能：数量排行 + 占比图正确渲染；列表可按标签过滤、可搜索、可分页；详情可增删 QA/自定义标签、`自动添加 QA 标签` 一键补齐（含历史来信）；页面风格与现有 app 一致；无发信入口（决策#2）。

## 自检清单
- [x] 关键不变量 ≥1（置灰/全量/高亮）
- [x] 现状审计列全导航/视图/状态/样式接入点（grep 确认 `viewMeta`:91、`setView`:1185、`refreshCurrentView`:1203）
- [x] 文件数 3 ≤10；子系统 1 ≤2
- [x] 每 Task 标注不变量
- [x] 验收每不变量 ≥1 检查
- [x] 无模糊文件项
- [x] 范围外显式（发信、后端、图表库）
- [x] Phase 0 知识：K-mail-body-display-sites（`.pre`/escapeHtml 渲染范式，气泡沿用）已用
- [x] 存于 docs/plans/2026-07-01/
