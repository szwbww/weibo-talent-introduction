# 计划 P2：可信回复工作台操作遮罩补全 + 确认弹窗对比度修复

- 基线：`main`，在 P1 之后
- 顺序：见 `ui-tweaks-00-execution-order.md`，本计划**第二**（与 P1、P3 仅在缓存键上串行）
- 子系统数：1（浮层视觉：`styles.css` + `trust-reply-workbench.js` 渲染）
- 变更文件数：5
- 缓存键：`20260821-v10-overlay-contrast`

---

## 需求描述

### Observable outcome

1. 可信回复工作台在**任一后台操作进行中**（一键预判 / 单条生成 / 事实增删 / 保存工作台状态 / 保存回复框架 / 整合整封回复）时，工作台内容区上方盖一层**明显可见**的遮罩：半透明白底 + 模糊 + 居中的转圈图标 + 一句说明当前在做什么的中文文案。遮罩期间内容区不可点击、不可编辑。
2. 遮罩**不遮**「可信回复工作台」这一行折叠标题（summary），运营随时可以折叠/展开；内容区很长时遮罩上的提示卡片**跟随滚动**始终可见。
3. 当前忙碌的是「生成」类操作时，遮罩卡片上带一枚 **取消生成** 按钮，点击等价于今天工具栏里的「取消生成」。
4. 「确认操作」确认弹窗（内容安全门禁二次确认、以及所有复用 `#actionDialog` 的确认框）**不再透出底层页面**：弹窗底色不透明；正文两段说明文字由浅灰变为正文主色，可正常阅读。

### What must NOT change

- 工作台的业务行为完全不变：不新增/不删除任何请求、不改状态机、不改按钮的 `disabled` 判据、不改 `renderStatus()` 的状态条内容。遮罩是**纯展示层叠加**。
- 工具栏里既有的「取消生成」按钮（`data-action="cancel-generation"`，`trust-reply-workbench.js:2063-2065`）保留不动；遮罩上的取消按钮是**同一个 action**，不新增 handler 分支。
- 只读的 `AUTO_PREVIEW` 模式（`state.readOnly === true`）行为不变 —— 它本就不会进入任何 pending 状态，遮罩自然不出现。
- `#actionDialog` 的 DOM 结构、`openActionDialog()` 的 schema / 校验 / setup-cleanup 配对（K-shared-action-dialog-cleanup）一律不动，本计划**只改 CSS**。
- `.action-dialog::backdrop` 的 `rgba(15, 23, 42, 0.5)` + `blur(6px)` 不动。
- 全局 `p { font-size: 12px; color: var(--text-muted); margin-top: 2px; }`（`styles.css:302-306`）**不动** —— 只在 `.action-dialog-body` 作用域内覆盖，避免波及全站数百处 `<p>`。

### Out of scope

见 `ui-tweaks-00-execution-order.md` 的「已明确不做」全部四条。另外：

- 不给工作台加进度百分比、阶段条或 SSE 活动指示（那是 `.ai-reply-loading-overlay` 的 stoppable 形态，属另一条链路，K-ai-stream-progress-no-fake-percent）。
- 不改 `.ai-reply-loading-overlay`（`styles.css:6048-6062`）本身 —— 收发件箱 AI 聊天面板继续用它。
- 不给其它使用 `var(--panel-bg)` 的浮层（抽屉、sticky 工具条）做同类修复，本轮只修 `#actionDialog`。
- 不做 `#actionDialog` 内部的暗色**新增**适配之外的任何视觉调整（不改字号层级、按钮、圆角、阴影）。

---

## 关键不变量

### Invariant I-1：遮罩是渲染产物，必须写进 `renderMarkup()`，不得事后 append
- Rule：工作台的 `render()`（`trust-reply-workbench.js:2036-2050`）每次都执行 `host.innerHTML = renderMarkup()`，整棵子树重建。遮罩元素必须由 `renderMarkup()` 生成；**禁止**仿照 `app.js` 的 `setAiReplyLoading()` 用 `document.createElement` + `appendChild` 挂载。
- Applies to：`trust-reply-workbench.js` 的 `renderMarkup()`（`:2052-2058`）。`renderShell()`（`:2029-2034`）不加遮罩（它本身就是加载态占位）。
- Violation consequence：任何一次状态变更（包括生成过程中的进度消息更新）都会把 append 上去的遮罩冲掉，遮罩变成一闪即逝，比现在更糟。
- 来源：K-ai-reply-loading-panel（「不能挂在会被结果 render 重写 innerHTML 的容器上」）—— 本仓库另一处的同类缺陷。

### Invariant I-2：遮罩锚点是 `.reply-workflow-content`，不是 `<details>`
- Rule：遮罩用 `position: absolute; inset: 0`，其定位祖先必须是 `.trust-reply-workbench .reply-workflow-content`（为此给该规则块新增 `position: relative`）。**禁止**锚在 `.trust-reply-workbench`（`styles.css:7222-7224`，已有 `position: relative`）。
- Applies to：`styles.css:7226-7232`（新增一条声明）、`trust-reply-workbench.js` 遮罩元素的插入位置（`.reply-workflow-content` 的最后一个子元素）。
- Violation consequence：锚在 `<details>` 上时遮罩会盖住 `summary` 那一行 —— 运营在生成期间无法折叠工作台，且点 summary 变成点遮罩，交互直接卡死。
- 来源：original（本轮阅读 `renderMarkup()` 与 `.trust-reply-workbench` 规则实证）

### Invariant I-3：新增 `position: relative` 不得改变任何既有子元素的定位
- Rule：`.trust-reply-workbench` 作用域内**当前没有任何** `position: absolute / fixed` 的后代规则（已 grep `styles.css` 全部 169 处 `trust-reply` 选择器，`position:` 声明只有 `.trust-reply-workbench{relative}` 与 `.trust-reply-summary{static}` 两处）。因此给 `.reply-workflow-content` 加 `position: relative` 除本计划的遮罩外**不改变任何元素的渲染位置**。执行时若发现新增了绝对定位后代，必须回到本节复核。
- Applies to：`styles.css:7226-7232`。
- Violation consequence：若将来有后代靠 `.trust-reply-workbench` 做定位祖先，加了这条后会静默改锚，位置偏移且无报错。
- 来源：original（本轮 grep 实证）

### Invariant I-4：遮罩上的取消按钮复用同一 action，不新增 handler
- Rule：遮罩内的取消按钮必须是 `data-action="cancel-generation"`，由既有委托 handler（`trust-reply-workbench.js:2325-2328` 的 `event.target.closest("[data-action]")` + `:2361` 的分支）接管。**禁止**在 `onClick` 里新增 `if (action === ...)` 分支，**禁止**给遮罩单独绑定 listener。
- Applies to：`trust-reply-workbench.js:2325-2361`（保持不变）、遮罩渲染函数。
- Violation consequence：新增 listener 后，每次 `render()` 重建 DOM 都会丢失绑定（旧节点已销毁），取消按钮变成死按钮；委托 handler 则天然免疫。
- 来源：K-ai-reply-modal-helper-scope

### Invariant I-5：遮罩文案的优先级与 `factActionBlockReason()` 一致
- Rule：遮罩文案的判定顺序必须与既有 `factActionBlockReason(flags)`（`trust-reply-workbench.js:187-194`）的分支顺序一一对应：`requestPending → factChangePending → stateSavePending → generationPending → frameSavePending`，本计划在其后追加 `completePending`。遮罩用自己的整句文案（面向整个内容区），**不复用**那五句「…完成后可调整事实」（它们是事实按钮的局部理由），但两者**不得对同一时刻给出互相矛盾的忙碌原因**。
- Applies to：新增的 `busyOverlayState()`；`factActionBlockReason()` 保持逐字不变。
- Violation consequence：遮罩说「正在保存回复框架」而事实按钮的 tooltip 说「本摘要正在生成」，运营无法判断到底在等什么。
- 来源：original

### Invariant I-6：遮罩标记不得引入 inline style
- Rule：遮罩的 HTML 片段中不得出现 `style=`。既有用例 `src/test/js/trustReplyWorkbench.test.js:573` 断言 `assert.ok(!/style=/.test(host.innerHTML));`。
- Applies to：遮罩渲染函数产出的模板字符串。
- Violation consequence：该用例直接失败，`mvn test` 中止。
- 来源：original（本轮读测试实证）

### Invariant I-7：`--panel-bg` 不是不透明底色，浮层必须写死不透明值并配暗色
- Rule：`--panel-bg` 实值为 `rgba(255, 255, 255, 0.55)`（`styles.css:15`）/ 暗色 `rgba(21, 31, 48, 0.55)`（`styles.css:9487`）。任何需要遮住底层内容的浮层不得用它当 `background`。`.action-dialog` 与新增遮罩一律写死 alpha ≥ 0.92 的值，并在 `@media (prefers-color-scheme: dark)` 块（`styles.css:9478-9566`）内**成对**补暗色覆盖 —— 只补浅色会让暗色主题下弹窗依旧透。
- Applies to：`styles.css:2618-2627`（`.action-dialog`）、新增的 `.trust-reply-busy-overlay` / `.trust-reply-busy-card`、`styles.css:9478-9566`（暗色块）。
- Violation consequence：z-index 排查不出来的「透底」缺陷 —— `elementFromPoint` 返回的仍是浮层本身，层级是对的，透的是像素。
- 来源：K-panel-bg-token-is-translucent

### Invariant I-8：缓存键三键同值、同时 bump，并同步逐字断言
- Rule：`index.html:11 / 2074 / 2075` 三处 `?v=` 统一改为 `20260821-v10-overlay-contrast`；同步 `src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51` 三条逐字断言。
- Violation consequence：`trustReplyWorkbenchSharedMount.test.js:347-348` 三键相等断言失败 → `mvn test` 中止 → WAR 构建失败。
- 来源：K-frontend-cache-key-triad

---

## 样式契约

### S-1：工作台遮罩定位祖先（就地修改既有规则）
- 就地修改：`styles.css:7226-7232`。改动前基线（逐字）：

```css
.trust-reply-workbench .reply-workflow-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
}
```

- 改动后（逐字，只增一条声明，其余四条与缩进风格 2 空格全部保持）：

```css
.trust-reply-workbench .reply-workflow-content {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
}
```

- 使用点核查：选择器 `.trust-reply-workbench .reply-workflow-content` 在 `styles.css` 中**唯一**（grep 1 命中），只作用于工作台内部；页面其他 `.reply-workflow-content`（原始正文、清洗后正文、历史信件、人工富文本回复等折叠块）**不受影响**。属**就地修改**，不派生新 class。
- 禁止项：不得改 `.trust-reply-workbench { position: relative; }`（`styles.css:7222-7224`）；不得改动上面四条既有声明的值或顺序。

### S-2：遮罩与提示卡片（新增 class，逐字给出）
- 复用：转圈图标复用既有 `.ai-reply-loading-spinner`（`styles.css:6123-6130`）与其 `@keyframes ai-reply-spin`（`styles.css:6132-6136`）—— **不新增 spinner 样式，不复制关键帧**。取消按钮复用既有 `.button.danger`。
- 新增：把下列规则块**原样复制**到 `styles.css` 中 `.trust-reply-workbench .reply-workflow-content` 规则块之后、`/* 工具栏：白卡两段… */` 注释之前：

```css
/* P2 (S-2)：工作台忙碌遮罩。锚在 .reply-workflow-content 上，
   折叠时随内容一起隐藏，summary 始终可点（I-2）。 */
.trust-reply-busy-overlay {
  position: absolute;
  inset: 0;
  z-index: 6;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(3px);
  -webkit-backdrop-filter: blur(3px);
}

.trust-reply-busy-card {
  position: sticky;
  top: 96px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  max-width: 420px;
  margin: 48px 16px;
  padding: 18px 22px;
  border: 1px solid var(--panel-border);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: var(--shadow-lg);
  text-align: center;
}

.trust-reply-busy-text {
  color: var(--text-main);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.6;
}

.trust-reply-busy-hint {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.6;
}
```

- 暗色配对（I-7）：把下列规则块**原样复制**到 `@media (prefers-color-scheme: dark)` 块内、`.button.primary { … }`（`styles.css:9563-9565`）之后、该 media 块的收尾 `}`（`styles.css:9566`）之前：

```css
    .trust-reply-busy-overlay {
        background: rgba(13, 20, 32, 0.92);
    }

    .trust-reply-busy-card {
        background: rgba(21, 31, 48, 0.98);
        border-color: rgba(148, 163, 184, 0.22);
    }
```

- DOM 结构（`renderBusyOverlay()` 产出，逐字骨架；生成类忙碌才带最后那枚按钮）：

```html
<div class="trust-reply-busy-overlay" role="status" aria-live="polite">
  <div class="trust-reply-busy-card">
    <span class="ai-reply-loading-spinner" aria-hidden="true"></span>
    <span class="trust-reply-busy-text">正在生成回复…</span>
    <span class="trust-reply-busy-hint">生成期间不能改动事实与处理方式。</span>
    <button type="button" class="button danger" data-action="cancel-generation">取消生成</button>
  </div>
</div>
```

- 禁止项：inline style（I-6）；不得给遮罩加 `data-role`（避免与 `[data-role]` 角色树断言产生耦合）；不得新增 spinner 或关键帧；不得修改 `.ai-reply-loading-overlay` 及其配套规则。

### S-3：确认弹窗不透明化与正文对比度（就地修改 + 新增作用域覆盖）
- 就地修改一：`styles.css:2618-2627`。改动前基线（逐字）：

```css
.action-dialog {
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-lg);
    padding: 20px;
    width: min(500px, 90vw);
    margin: auto;
    background: var(--panel-bg);
    color: var(--text-main);
    box-shadow: var(--shadow-xl);
}
```

- 改动后（逐字，只改 `background` 一行并追加 `backdrop-filter` 两行，其余不动）：

```css
.action-dialog {
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-lg);
    padding: 20px;
    width: min(500px, 90vw);
    margin: auto;
    background: rgba(255, 255, 255, 0.97);
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    color: var(--text-main);
    box-shadow: var(--shadow-xl);
}
```

- 新增：把下列规则块**原样复制**到 `.action-dialog-body { … }` 规则块（`styles.css:2643-2648`）之后、`.action-dialog-footer` 之前：

```css
.action-dialog-body p {
    color: var(--text-main);
    font-size: 13px;
    line-height: 1.6;
}

.action-dialog-body .ai-reply-coverage {
    color: var(--text-secondary);
}
```

- 暗色配对（I-7）：把下列规则块**原样复制**到 S-2 的暗色块之后、该 media 块收尾 `}` 之前：

```css
    .action-dialog {
        background: rgba(21, 31, 48, 0.97);
    }
```

- 使用点核查：
  - `.action-dialog` 在 `index.html` 中只有 1 处（`index.html:1958` `<dialog id="actionDialog" class="action-dialog">`），为**全站共用**确认弹窗（`ACTION_DIALOG_SCHEMAS` 共 8 种 type：`resolve-handoff` / `bind-unmatched-contact` / `switch-to-manual` / `switch-to-auto` / `initiate-meeting-schedule` / `confirm` / `confirm-typed` 等，`app.js:11930-12015`）。就地修改会同时影响这 8 种弹窗 —— 这是**期望行为**（全都应该不透明）。
  - `.action-dialog-body p` 是**新增作用域覆盖**，只在该弹窗内生效，全局 `p`（`styles.css:302-306`）逐字不动。
  - `.ai-reply-coverage`（`styles.css:6144-6151`）在弹窗外的使用点不受影响 —— 覆盖被 `.action-dialog-body` 限定。
- 禁止项：不得改全局 `p`；不得改 `.action-dialog::backdrop`（`styles.css:2629-2633`）；不得改 `.action-dialog h3`（`:2635-2641`）与 `.action-dialog-footer`（`:2650-2654`）；不得改 `.ai-reply-warning` / `.ai-reply-error` 的配色（门禁条目的橙/红是有意的语义色）。

---

## 现状审计

### `trust-reply-workbench.js` — 忙碌状态的全部来源（读路径）
| 状态位 | 置位点 | 复位点 | 今天的可见反馈 |
|---|---|---|---|
| `state.generation.pending` | `:919`, `:1275` | `:936/943/952/984/996/1004/1308/1315/1319` | 状态条文案 + 工具栏「取消生成」按钮 + 各控件 `disabled` |
| `request.pending`（逐条） | `:1014` | `:1036/1067/1072/1082/1085/1094/1480` | 该条按钮文案变「生成中…」、下拉/文本域 `disabled` |
| `state.factChangePending` | `:1602` | `:1616` | 仅事实按钮 tooltip（`factActionBlockReason`） |
| `state.stateSavePending` | `:964`, `:1235` | `:970/975/983/991/1241/1246/1255/1260` | 按钮文案变「保存中…」 |
| `state.frameSavePending` | 框架保存链路 | 同上 | 仅 tooltip |
| `state.completePending` | `:2005` | `:2011` | 「完成」按钮 `disabled`（`:2278`） |

- **结论（缺陷根因）**：以上 6 种忙碌**没有任何一种**产生整块遮罩。全仓 `grep "ai-reply-loading-overlay" trust-reply-workbench.js` **0 命中** —— 工作台从来没有遮罩，运营看到的只有零散的 disabled 与一行状态文字，所以「不明显」。`.trust-reply-workbench { position: relative; }`（`styles.css:7223`）是一个**没有绝对定位后代的空钩子**，正好是遮罩预留位。

### `trust-reply-workbench.js` — 渲染与事件（写路径）
- `render()`（`:2036-2050`）：`host.innerHTML = renderMarkup()` 全量重建 → 遮罩必须进 `renderMarkup()`（I-1）。
- `renderMarkup()`（`:2052-2058`）：产出 `<details class="detail-section reply-workflow-detail trust-reply-workbench" open>` → `<summary>` + `<div class="reply-workflow-content">…</div>`。遮罩插入点 = `.reply-workflow-content` 的最后一个子元素。
- `renderShell(message, allowRecovery)`（`:2029-2034`）：bootstrap 未落地时的占位壳，自带「正在加载工作台」文案 → **不加遮罩**。
- `renderStatusOnly()`（`:2298-2300`）：实际也走 `renderMarkup()` → 遮罩自动同步。
- `onClick(event)`（`:2325-2328`）：`event.target.closest("[data-action]")` + `host.contains(button)` 委托 → 遮罩里的 `data-action="cancel-generation"` 天然生效（I-4）。
- `factActionBlockReason(flags)`（`:187-194`）：既有的忙碌理由优先级表，遮罩文案顺序对齐它（I-5）。
- **Interaction point A**：遮罩盖住工具栏后，工具栏里的「取消生成」（`:2063-2065`）点不到 → 遮罩必须自带取消按钮，否则生成中的取消能力被本计划**弄丢**。

### `styles.css` — 定位与浮层
- `.trust-reply-workbench { position: relative; }` — `:7222-7224`
- `.trust-reply-workbench .reply-workflow-content` — `:7226-7232`（唯一命中）
- 全部 169 处 `trust-reply` 选择器中，`position:` 声明只有两处：上面的 `relative` 与 `.trust-reply-summary { position: static; }`（`:7541-7542`）。**无任何 `position: absolute/fixed`** → I-3 成立。
- 可复用浮层：`.ai-reply-loading-overlay`（`:6048-6062`，`rgba(255,255,255,0.84)` + `blur(2px)`）、`.ai-reply-loading-spinner`（`:6123-6130`）、`@keyframes ai-reply-spin`（`:6132-6136`）。本计划**只复用 spinner 与关键帧**，遮罩另立 class（`.ai-reply-loading-overlay` 由 `app.js:4301` 的 `setAiReplyLoading()` 用 append 方式管理，语义与生命周期都不同）。

### `#actionDialog` — 共用确认弹窗
- DOM：`index.html:1958-1966`，`<dialog id="actionDialog" class="action-dialog">` → `<form id="actionDialogForm" method="dialog">` → `h3#actionDialogTitle` + `div#actionDialogBody.action-dialog-body` + `.action-dialog-footer`（取消 / 确认执行）。
- 展开方式：`dialog.showModal()`（`app.js:12123`）→ `::backdrop` 生效。
- 写路径（往 `#actionDialogBody` 写 HTML 的唯一处）：`openActionDialog(type, options)`（`app.js:12017-12126`），`field.type === "html"` 时写 `<div>${options.message}</div>`。
- 触发本次截图那个弹窗的调用点：`app.js:10391`，`message` = `<p>本次发送命中 N 项内容安全门禁，请逐条核对后确认：</p><div class="ai-reply-feedback">…</div><p>确认已人工核对，仍要发送吗？</p>`。
- **缺陷根因（两条，叠加）**：
  1. **透底** —— `.action-dialog { background: var(--panel-bg); }`（`styles.css:2624`），而 `--panel-bg` = `rgba(255,255,255,0.55)`（`styles.css:15`）。弹窗只有 55% 不透明，`::backdrop` 的模糊底图直接透上来。
  2. **灰字** —— 弹窗正文那两段是裸 `<p>`，命中全局 `p { font-size: 12px; color: var(--text-muted); }`（`styles.css:302-306`），`--text-muted` = `#94a3b8`。叠加 55% 透明后对比度极低，正是截图里「看不清」的样子。
- **Interaction point B**：`.action-dialog` 与 `.action-dialog-body p` 是 8 种 dialog type 共用样式，改动会同时影响 `resolve-handoff` / `switch-to-manual` / `confirm-typed` 等全部确认框 —— 期望行为，需在人工验收里抽查其中两种（A-6）。
- **Interaction point C**：暗色主题下 `--panel-bg` = `rgba(21,31,48,0.55)`；若只改浅色写死值而不补暗色覆盖，暗色主题会退化成「白底 0.97 的弹窗 + 暗色文字变量」→ 主题撕裂。必须成对（I-7）。

### 现有测试对本次改动的直接约束
| 文件:行 | 断言 | 影响 |
|---|---|---|
| `trustReplyWorkbench.test.js:573` | `!/style=/.test(host.innerHTML)` | 遮罩不得用 inline style（I-6）→ 遵守即通过 |
| `trustReplyWorkbenchSharedMount.test.js:386-388` | 训练/生产两宿主的 `data-role` 序列 `deepStrictEqual` | 两宿主在该断言点均为**空闲**态，遮罩不渲染；且遮罩不带 `data-role` → 不受影响 |
| `trustReplyWorkbenchSharedMount.test.js:347-348` | 三个缓存键相等 | bump 后仍相等 → 不改 |
| `batchSendTaskConsoleVisualFix.test.js:49-51` | 三键逐字等于旧值 | **必须改**为新键（I-8） |

### 前端样式盘点
- 可复用 class：
  - `.ai-reply-loading-spinner` — `styles.css:6123-6130` — 24px 转圈，`3px solid rgba(var(--primary-rgb),0.2)` + `border-top-color: var(--primary)` + `ai-reply-spin 0.8s linear infinite`
  - `@keyframes ai-reply-spin` — `styles.css:6132-6136`
  - `.button.danger` — 取消生成按钮
  - `.trust-reply-workbench { position: relative; }` — `styles.css:7222-7224`（保留不用）
- 设计基准 token（本计划相关实值）：
  - `--panel-bg` = `rgba(255,255,255,0.55)`（浅）/ `rgba(21,31,48,0.55)`（暗）— **本计划禁止用它做浮层底**
  - `--text-main` = `#1e293b`（浅）/ `#e2e8f0`（暗）
  - `--text-secondary` = `#475569`（浅）/ `#a8b6c8`（暗）
  - `--text-muted` = `#94a3b8`（浅）/ `#7d8ca3`（暗）— 正是当前灰字的来源
  - `--radius-md` = `10px`；`--radius-lg` = `18px`
  - `--shadow-lg` = `0 10px 28px -8px rgba(15,23,42,0.14), 0 2px 6px rgba(15,23,42,0.05)`
  - `--shadow-xl` = `0 20px 48px -12px rgba(15,23,42,0.2), 0 4px 12px rgba(15,23,42,0.06)`
  - 仓库既定「不透明浮层」写法：`background: rgba(255,255,255,.96); backdrop-filter: blur(8px);`（先例 `.batch-manual-actions-sticky` `styles.css:9166-9178`、`.batch-config-editor-actions` `styles.css:8684-8697`）
  - 暗色 media 块区间：`styles.css:9478-9566`
- DOM 结构约定：工作台 = `<details class="… trust-reply-workbench" open>` → `<summary class="reply-workflow-summary">` + `<div class="reply-workflow-content">`；`styles.css` 在 `.trust-reply-*` 区段使用 **2 空格**缩进，`.action-dialog` 区段使用 **4 空格**缩进 —— 新增规则按所在区段的缩进风格书写（S-2 用 2 空格，S-3 用 4 空格，暗色块内统一 4 空格）。
- 改动前基线：见 S-1 / S-3 各自的「改动前基线」代码块（逐字摘录）。

---

## 实现方案

### 阶段 1：工作台遮罩（I-1 … I-6、S-1、S-2）

**T1-1**　`styles.css`：按 S-1 给 `.trust-reply-workbench .reply-workflow-content` 增加 `position: relative;`。
**T1-2**　`styles.css`：按 S-2 插入 4 条新规则（`.trust-reply-busy-overlay` / `.trust-reply-busy-card` / `.trust-reply-busy-text` / `.trust-reply-busy-hint`），逐字复制。
**T1-3**　`styles.css`：按 S-2 在暗色 media 块内插入 2 条暗色覆盖，逐字复制。
**T1-4**　`trust-reply-workbench.js`：在 `factActionBlockReason` / `factActionReasonFor`（`:187-204`）之后**不改动它们**，在 `renderToolbar()`（`:2061`）之前新增两个函数，逐字：

```js
        // P2 (I-5): overlay busy reasons mirror factActionBlockReason()'s
        // priority so the mask and the per-fact tooltip never disagree.
        function busyOverlayState() {
            if (state.requests.some((request) => request.pending)) {
                return { text: "本条摘要正在生成…", hint: "完成后可继续调整该条目。", cancellable: false };
            }
            if (state.factChangePending) {
                return { text: "正在更新事实…", hint: "服务端重算证据矩阵中，完成后可继续调整。", cancellable: false };
            }
            if (state.stateSavePending) {
                return { text: "正在保存工作台状态…", hint: "完成后可调整事实与处理方式。", cancellable: false };
            }
            if (state.generation.pending) {
                return { text: state.generation.message || "正在生成回复…", hint: "生成期间不能改动事实与处理方式。", cancellable: true };
            }
            if (state.frameSavePending) {
                return { text: "正在保存回复框架…", hint: "完成后可调整事实与处理方式。", cancellable: false };
            }
            if (state.completePending) {
                return { text: "正在整合整封回复…", hint: "服务端合成中，请勿离开本页。", cancellable: false };
            }
            return null;
        }

        // P2 (I-1/I-2): the mask is part of renderMarkup() output and lives as
        // the last child of .reply-workflow-content, so <summary> stays clickable.
        // I-4: the cancel button reuses the existing delegated action.
        function renderBusyOverlay() {
            const busy = busyOverlayState();
            if (!busy) return "";
            const cancel = busy.cancellable
                ? `<button type="button" class="button danger" data-action="cancel-generation">取消生成</button>`
                : "";
            return `<div class="trust-reply-busy-overlay" role="status" aria-live="polite"><div class="trust-reply-busy-card"><span class="ai-reply-loading-spinner" aria-hidden="true"></span><span class="trust-reply-busy-text">${escapeText(busy.text)}</span><span class="trust-reply-busy-hint">${escapeText(busy.hint)}</span>${cancel}</div></div>`;
        }
```

**T1-5**　`trust-reply-workbench.js`：`renderMarkup()`（`:2052-2058`）中，把 `.reply-workflow-content` 的收尾由 `…</section></div>` 改为 `…</section>${renderBusyOverlay()}</div>`（`</section>` 指 `data-page-panel="frame"` 那个 section 的闭合标签），并给该容器加忙碌属性：`<div class="reply-workflow-content"${busyOverlayState() ? ' aria-busy="true"' : ''}>`。**只改这两处**，模板其余部分逐字不动。
**T1-6**　`trust-reply-workbench.js`：`onClick`（`:2325-2361`）**不改**（I-4）；`factActionBlockReason`（`:187-194`）**不改**（I-5）；`renderShell`（`:2029-2034`）**不改**。

### 阶段 2：确认弹窗（I-7、S-3）

**T2-1**　`styles.css`：按 S-3 改 `.action-dialog` 的 `background` 并追加 `backdrop-filter` 两行。
**T2-2**　`styles.css`：按 S-3 在 `.action-dialog-body` 之后插入 `.action-dialog-body p` 与 `.action-dialog-body .ai-reply-coverage` 两条新规则。
**T2-3**　`styles.css`：按 S-3 在暗色 media 块内插入 `.action-dialog` 暗色覆盖。
**T2-4**　`app.js`、`index.html` 的 `#actionDialog` 相关代码**不改**。

### 阶段 3：缓存键与测试（I-8）

**T3-1**　`index.html:11 / 2074 / 2075` 三处 `?v=` 改为 `20260821-v10-overlay-contrast`。
**T3-2**　`src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51` 三条字符串同步改为新键。
**T3-3**　新增 `src/test/js/overlayAndDialogContrast.test.js`：以「读源文件 + 正则/字符串断言」的方式验证 S-1/S-2/S-3 的逐字落地与 I-1/I-2/I-4/I-6/I-7；并用现有 `trustReplyWorkbench.test.js` 的 vm + FakeElement 套路，构造 `state.generation.pending === true` 的一次 render，断言 `host.innerHTML` 含 `class="trust-reply-busy-overlay"` 与 `data-action="cancel-generation"`、且 `aria-busy="true"` 出现在 `reply-workflow-content` 上、且不含 `style=`。

---

## 变更文件清单

| # | 文件 | 动作 | 覆盖 |
|---|---|---|---|
| 1 | `src/main/resources/static/styles.css` | 改 | T1-1, T1-2, T1-3, T2-1, T2-2, T2-3 |
| 2 | `src/main/resources/static/trust-reply-workbench.js` | 改 | T1-4, T1-5 |
| 3 | `src/main/resources/static/index.html` | 改（3 行） | T3-1 |
| 4 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 改（3 行） | T3-2 |
| 5 | `src/test/js/overlayAndDialogContrast.test.js` | 新增 | T3-3 |

合计 5 个文件，1 个子系统。`app.js` 与全部 Kotlin 源码/测试**不在清单内**。

---

## 验证命令

> 前提一：本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败（`CLAUDE.md` 的 Commands 章节）。
> 前提二：前端 JS 用例的权威门禁是 `node --test <file>` 单跑；`verify.sh` **只跑一个文件，不可作为本计划的回归门禁**（K-js-test-invocation-surface）。
> 环境实测：`node -v` = `v22.23.2`。

```bash
# 1) 本计划直接改动/新增的 JS 用例（快速迭代用）
node --test src/test/js/overlayAndDialogContrast.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js
node --test src/test/js/trustReplyWorkbench.test.js
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js

# 2) 前端全量 JS 用例（本计划的前端回归门禁）
node --test src/test/js/*.test.js

# 3) 语法检查
node --check src/main/resources/static/app.js
node --check src/main/resources/static/task-modal-runtime.js

# 4) 全量回归
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 5) 空白/换行卫生
git diff --check
```

通过判据：
- 命令 1/2：退出码 0，输出含 `# fail 0`。
- 命令 3：退出码 0，无输出。
- 命令 4：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`，且输出中出现 `node --test` 执行记录。
- 命令 5：退出码 0，无输出。

来源：`CLAUDE.md` 项目元信息 + `pom.xml:188-231` + 实测。

---

## 验收标准

- **I-1**：`grep -n 'renderBusyOverlay()' src/main/resources/static/trust-reply-workbench.js` 命中 2 处（定义 + `renderMarkup()` 内插值）；`grep -c 'createElement' src/main/resources/static/trust-reply-workbench.js` 与改动前相等（遮罩未用 append 方式）。
- **I-2**：`grep -n 'position: relative' src/main/resources/static/styles.css | grep 72` 中 `.trust-reply-workbench .reply-workflow-content` 规则块含 `position: relative`；`overlayAndDialogContrast.test.js` 断言 `renderMarkup` 模板里 `${renderBusyOverlay()}` 位于 `</section>` 与 `</div>` 之间，且 `<summary` 之前不含 `trust-reply-busy-overlay`。
- **I-3**：`grep -c 'position: absolute' src/main/resources/static/styles.css` 与改动前相等（遮罩自身除外，即差值恰为 `1`）；且 `.trust-reply-*` 选择器块内除新增遮罩外无新 `position` 声明。
- **I-4**：`git diff src/main/resources/static/trust-reply-workbench.js` 中不含对 `function onClick` 的改动；`grep -c 'data-action="cancel-generation"' src/main/resources/static/trust-reply-workbench.js` == `2`（工具栏 1 + 遮罩 1）；`grep -c 'cancel-generation"' ` 在 `onClick` 分支处仍为 1。
- **I-5**：`git diff` 中 `factActionBlockReason` 函数体为空 diff；`busyOverlayState()` 的分支顺序与 `factActionBlockReason` 前五条一一对应（测试以正则断言六个 `if (` 的出现顺序）。
- **I-6**：`overlayAndDialogContrast.test.js` 中构造 pending 状态渲染后断言 `!/style=/.test(host.innerHTML)`；`trustReplyWorkbench.test.js:573` 原用例通过。
- **I-7**：`grep -n 'var(--panel-bg)' src/main/resources/static/styles.css` 的结果中**不含** `.action-dialog` 规则块；`.action-dialog` / `.trust-reply-busy-overlay` / `.trust-reply-busy-card` 在暗色 media 块（`9478`–收尾）内**各有**一条对应覆盖（测试断言暗色块子串同时含这三个选择器）。
- **I-8**：`grep -o '?v=[^"]*' src/main/resources/static/index.html | sort -u` 只输出一行 `?v=20260821-v10-overlay-contrast`；`batchSendTaskConsoleVisualFix.test.js` 通过。
- **S-1**：`.trust-reply-workbench .reply-workflow-content` 规则块与契约「改动后」代码块 `diff` 为空。
- **S-2**：新增的 4 条规则与暗色 2 条规则，与契约代码块**逐字一致**（测试用去空白归一化后的字符串包含断言）；`grep -c 'ai-reply-loading-spinner' src/main/resources/static/styles.css` 与改动前相等（未新增 spinner 样式）；`grep -c '@keyframes ai-reply-spin' src/main/resources/static/styles.css` == `1`。
- **S-3**：`.action-dialog` 规则块与契约「改动后」代码块 `diff` 为空；`.action-dialog-body p` 与 `.action-dialog-body .ai-reply-coverage` 两条新规则逐字存在；`styles.css:302-306` 的全局 `p` 规则块 diff 为空。
- 回归：执行「验证命令」节的命令 2、3、4、5 全部通过。

---

## 人工验收清单

### A-1：一键预判期间遮罩明显可见且带取消
- 前置条件：收发件箱中有一封已绑定专家、含至少 2 条可识别请求的来信；LLM 可用。
- 操作步骤：1）打开该来信详情，展开「可信回复工作台」；2）点「一键预判」；3）在生成进行中观察工作台内容区；4）尝试点击内容区里的任意下拉框或「添加事实」按钮。
- 预期结果：第 3 步内容区被一层**白色半透明 + 轻微模糊**的遮罩盖住，遮罩正中偏上有一张白卡片，卡片内自上而下为：蓝色转圈图标、加粗深色文字（内容随阶段变化，如「GENERATING：正在生成 1/2」或「正在生成回复…」）、一行灰色小字「生成期间不能改动事实与处理方式。」、一枚红色「取消生成」按钮。第 4 步点不动任何控件。
- 覆盖：需求描述 observable outcome 1、3；S-2

### A-2：遮罩不遮折叠标题，长内容时卡片跟随滚动
- 前置条件：同 A-1，且该来信的请求条目足够多，使工作台内容区高度超过一屏。
- 操作步骤：1）点「一键预判」；2）生成进行中，点击「可信回复工作台」那一行折叠标题；3）再点一次展开；4）展开状态下上下滚动页面。
- 预期结果：第 2 步工作台正常折叠（内容区连同遮罩一起消失），标题行本身**没有**被遮罩盖住；第 3 步展开后遮罩仍在；第 4 步滚动时提示卡片**始终停留在可视区域内**（距视口顶部约 96px），不会滚出屏幕。
- 覆盖：需求描述 observable outcome 2；I-2

### A-3：遮罩上的「取消生成」真的能取消
- 前置条件：同 A-1。
- 操作步骤：1）点「一键预判」；2）生成进行中点遮罩卡片上的「取消生成」；3）观察工作台。
- 预期结果：遮罩消失，工作台恢复可操作；状态条显示取消结果；未完成的条目保持未生成状态，已完成的条目保留。行为与改动前点工具栏里的「取消生成」**完全一致**。
- 覆盖：需求描述 observable outcome 3；I-4；现状审计 Interaction point A

### A-4（跨路径）：非生成类忙碌也出遮罩，且文案说得对
- 前置条件：同 A-1，工作台已加载完成且处于空闲。
- 操作步骤：1）在任一摘要卡片上点「添加事实」并选中一条事实（触发 `factChangePending`）；2）观察遮罩文案；3）等待完成；4）走到「回复框架与整合」页，改一个框架下拉项（触发 `frameSavePending`），观察遮罩文案；5）在该页点整合/完成（触发 `completePending`），观察遮罩文案。
- 预期结果：第 2 步遮罩文案为「正在更新事实…」+「服务端重算证据矩阵中，完成后可继续调整。」且**没有**取消按钮；第 4 步为「正在保存回复框架…」；第 5 步为「正在整合整封回复…」+「服务端合成中，请勿离开本页。」三次遮罩都短暂但**肉眼可见**。
- 覆盖：需求描述 observable outcome 1；I-5

### A-5：确认弹窗不再透底、文字可读
- 前置条件：构造一封会命中内容安全门禁的人工回复 —— 在「人工富文本回复」正文里写入一句含未审核数字或链接的话（例如 `The subsidy is 500,000 RMB, see https://example.com`），填好主题，点发送。
- 操作步骤：1）点「发送」；2）弹出「确认操作」对话框后，先不点任何按钮，仔细观察。
- 预期结果：① 弹窗背景是**纯白（暗色主题下为深蓝灰）不透明**，底层表格/表单**完全看不见**；② 标题「确认操作」为深色加粗；③ 第一段「本次发送命中 1 项内容安全门禁，请逐条核对后确认：」与末段「确认已人工核对，仍要发送吗？」都是**深色正文**（与标题同色系），字号 13px，不再是浅灰小字；④ 中间橙色告警框「文本含未经审核的数字或链接，请依据 QA 事实手动核对。」配色不变；⑤ 「取消」「确认执行」两枚按钮外观不变。
- 覆盖：需求描述 observable outcome 4；S-3

### A-6（回归 · Interaction point B）：其他复用确认框同样不透且不变形
- 前置条件：存在一位处于「已转人工」状态的专家。
- 操作步骤：1）打开该专家详情，点「切换为自动回复」，观察弹窗；2）取消；3）点「切换为人工回复」，观察弹窗（含「转人工原因」下拉与「备注信息」文本域）；4）取消。
- 预期结果：两个弹窗底色同样**不透明**；标题、下拉框、文本域、按钮的排布与字号与改动前**完全一致**（下拉/文本域的 label 仍是加粗 12px，未被 `.action-dialog-body p` 影响）；取消后弹窗正常关闭，可重复打开。
- 覆盖：需求描述 What must NOT change 第 4 条；现状审计 Interaction point B

### A-7（回归 · Interaction point C）：暗色主题下两者都不透
- 前置条件：把操作系统/浏览器切到**深色**外观（`prefers-color-scheme: dark`）。
- 操作步骤：1）刷新控制台；2）重复 A-1 第 1-3 步观察工作台遮罩；3）重复 A-5 观察确认弹窗。
- 预期结果：遮罩为**深色**半透明（不是白色），提示卡片为深底浅字、边框可见；确认弹窗为深底不透明，正文两段为浅色（`#e2e8f0` 系）可读；两者都**没有**出现「深浅撕裂」（一半深色一半白色）。
- 覆盖：I-7；现状审计 Interaction point C

### A-8（回归）：工作台空闲时无任何遮罩残留
- 前置条件：同 A-1，一键预判已完成。
- 操作步骤：1）生成全部完成后观察工作台；2）折叠再展开；3）关闭详情面板再重新打开该来信。
- 预期结果：三步中工作台内容区**都没有**任何遮罩、卡片或转圈图标；所有下拉、文本域、按钮均可正常点击编辑。
- 覆盖：需求描述 What must NOT change 第 1 条

### A-9（回归）：收发件箱 AI 聊天面板的原有遮罩不受影响
- 前置条件：进入一个会使用 `.ai-reply-loading-overlay` 的 AI 生成入口（AI 训练模拟或收发件箱 AI 草稿）。
- 操作步骤：1）触发一次 AI 生成；2）观察其加载遮罩。
- 预期结果：外观与改动前**完全一致**（`rgba(255,255,255,0.84)` 白底 + 2px 模糊 + 转圈 + 阶段文字 + 进度条 + 「停止生成」按钮），未被本计划新增的遮罩样式串味。
- 覆盖：需求描述 Out of scope 第 2 条

### A-10（UI 目测 · 对照契约实值）：遮罩卡片排版
- 前置条件：触发 A-1 的生成中状态。
- 操作步骤：逐项目测遮罩卡片。
- 预期结果：① 卡片最大宽度约 **420px**，超宽时文字换行不撑破；② 卡片内元素纵向排列、居中对齐、间距 **10px**；③ 卡片内边距 **18px 22px**，圆角 **10px**，带一层柔和投影；④ 转圈图标直径 **24px**，蓝色；⑤ 主文案 13px 加粗深色，副文案 12px 中灰；⑥ 浏览器缩到 1280px 宽时卡片不溢出内容区左右边界（左右各留 16px）。
- 覆盖：S-2
