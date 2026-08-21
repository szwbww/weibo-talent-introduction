# 计划 P1：「检查回复」移入收发件箱 + 删除「自动回复预览」

- 基线：`main`（工作区仅 `docs/releases.json` 既有改动）
- 顺序：见 `ui-tweaks-00-execution-order.md`，本计划**第一**
- 子系统数：1（静态前端入口：`static/` + 其 node 契约测试）
- 变更文件数：8
- 缓存键：`20260821-v9-check-replies-move`

---

## 需求描述

### Observable outcome

1. 「专家列表」工具栏右侧的按钮组变为 **刷新 · 发现专家▾ · 自动回复：… · 回刷 ES** —— 「检查回复」不再出现在该视图任何位置。
2. 「收发件箱」→「已激活账号收发邮件记录」面板标题栏右侧出现两枚按钮，从左到右为 **检查回复**（次要样式）、**批量发送**（主色）。点击「检查回复」的行为与今天在专家列表点击时**完全一致**：无运行中任务则打开任务启动弹窗，有运行中任务则直接打开任务进度弹窗。
3. 来信详情（收发件箱 → 打开一封来信）的「处理与回复」分组中，**不再出现**「自动回复预览」这个折叠块；其上方的「与该专家的历史信件记录」与其下方的「可信回复工作台」「人工富文本回复」位置与行为不变。
4. 未绑定专家联系人的来信，原先显示的「该来信尚未绑定专家联系人，无法解析自动回复上下文。请先在上方完成绑定。」灰字块**一并消失**（它是自动回复预览的降级文案）。

### What must NOT change

- 「检查回复」按钮的 `id` / `class` / `onclick` / 文案**逐字不变**：`<button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>`。
- 「检查回复」的勾选范围语义不变：仍读取 `.expert-select-cb:checked`（专家列表里的勾选）；有勾选则按 `contactIds` 定向检查，无勾选则全量。切到收发件箱后专家列表的勾选**依然生效**。
- 「批量发送」按钮的 `id="bulkOutreachBtn"` / `class="button primary"` / `onclick="handleBulkOutreach()"` / 文案「批量发送」逐字不变。
- `bulkAutoReplyBtn`（自动回复：…）与 `backfillOperatorStatusBtn`（回刷 ES）仍留在专家列表工具栏，位置与顺序不变。
- 「可信回复工作台」（LIVE 宿主）的挂载、卸载、采用到人工编辑器的行为完全不变。
- 后端 `AutoReplyPreviewService` 与 `GET /api/mail/unmatched-inbound/{id}/auto-reply-preview` 不删、不改。
- `trust-reply-workbench.js` **一个字节都不改**。

### Out of scope

见 `ui-tweaks-00-execution-order.md` 的「已明确不做」1/2/3/4 全部四条。另外：

- 不调整 `.panel-head` 既有规则（只新增 `.panel-head-actions` 容器类）。
- 不动「处理与回复」分组标题与其余折叠块的顺序、图标、状态文案。

---

## 关键不变量

### Invariant I-1：任务按钮 id 是跨模块契约，搬家只搬 DOM 节点
- Rule：字符串 `checkRepliesBtn` 在 `index.html` 中恰好出现 **1 次**，在 `app.js` 中的 5 个引用点**逐字保留**：`taskButtonOriginalTexts.checkRepliesBtn`（`app.js:690`）、`taskButtonMapping.CHECK_REPLIES.btnId`（`app.js:701`）、`handleCheckReplies` 里的 `openTaskModal(taskType, "检查回复", "checkRepliesBtn", …)`（`app.js:4885`）、`executeCheckReplies` 里的同一调用（`app.js:4907`）、`taskLaunchConfigs.CHECK_REPLIES.btnId`（`app.js:5208`）。
- Applies to：`index.html:601`（移出点）、`index.html` mailbox `.panel-head`（移入点）、上列 5 处 `app.js` 引用。
- Violation consequence：`setTaskButtonRunning(btnId)` / `restoreTaskButton(btnId)` 走 `$("#" + btnId)`，id 一改即取不到元素 → 任务运行态与恢复**静默**失效；而 DOM stub 测试永远返回 stub 元素，不会报（K-dom-stub-tests-hide-dangling-refs）。
- 来源：K-task-launch-config-registration

### Invariant I-2：`.view` 常驻 DOM，跨视图 id / 选择器查询照常生效
- Rule：`setView()`（`app.js:1640-1662`）只切换 `.view` 上的 `.active` class，**从不移除 section**。因此按钮换视图后：`$("#checkRepliesBtn")` 在任意视图下都能取到；`$$(".expert-select-cb:checked")` 在收发件箱视图下**仍能读到专家列表里的勾选**。本计划不得为「检查回复」引入任何「当前视图是否为 contacts」的判断，也不得在切视图时清空勾选。
- Applies to：`app.js:1640-1662`（setView）、`app.js:732-753`（resumeProgressPollingIfNeeded）、`app.js:4899-4903`（executeCheckReplies 读勾选）、`app.js:5211-5215`（taskLaunchConfigs.CHECK_REPLIES.preload 读勾选）。
- Violation consequence：若误加视图判断，运营在专家列表勾选 20 位专家后切到收发件箱点「检查回复」，会静默降级为**全量**检查所有已联系专家 —— 一次多发数百次 IMAP 拉取，且界面不提示。
- 来源：original（本轮 grep + 阅读 `setView` 实证）

### Invariant I-3：缓存键三键同值、同时 bump，并同步逐字断言
- Rule：`index.html:11`（`styles.css?v=`）、`index.html:2074`（`trust-reply-workbench.js?v=`）、`index.html:2075`（`app.js?v=`）三处必须为同一值，本计划统一改为 `20260821-v9-check-replies-move`；同时把 `src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51` 的三条字符串断言改为同一新值。
- Applies to：上述 3 处引用 + 1 处测试断言，共 4 处。
- Violation consequence：只 bump 部分键 → `trustReplyWorkbenchSharedMount.test.js:347-348` 的三键相等断言失败，`mvn test` 在 `test` phase 直接中止，WAR 构建失败（2026-08-13 发布 eda4853 实测踩过）。
- 来源：K-frontend-cache-key-triad

### Invariant I-4：删 UI 必须成组删干净，并退休其契约测试
- Rule：自动回复预览的以下标识必须在 `app.js` 与 `index.html` 中**全部消失**，不得留任何一处：`data-trust-reply-auto-preview-host`、`data-auto-preview-status`、`data-auto-preview-body`、`auto-preview-section`、`autoPreviewTrustReplyInstance`、`unmountAutoPreviewTrustReply`、`mountAutoPreviewTrustReply`、`loadAutoPreviewIntoHost`、`renderAutoPreviewIntoHost`、`renderAutoPreviewError`、`waitForWorkbenchReady`、字符串 `auto-reply-` 与 `"preview"` 拼接而成的前端请求路径。同时 `src/test/js/autoPreviewWorkbenchHost.test.js` 改写为「已退休」守卫测试（不得原样保留，也不得只删不补）。
- Applies to：`app.js:144`、`app.js:168-171`、`app.js:176-177`、`app.js:9636-9710`、`app.js:9848-9866`、`app.js:9944`、`app.js:9985`；`src/test/js/autoPreviewWorkbenchHost.test.js` 全文。
- Violation consequence：留测试 → 全量 node 用例常红并阻塞发布（K-ui-removal-retires-obsolete-contract-tests）；留渲染函数 → `if (!host) return;` 式静默死代码，stub 测试仍绿（K-dom-stub-tests-hide-dangling-refs）。
- 来源：K-ui-removal-retires-obsolete-contract-tests、K-dom-stub-tests-hide-dangling-refs

### Invariant I-5：`unmountMailboxTrustReplyHosts()` 保名保调用点，只收缩函数体
- Rule：函数名 `unmountMailboxTrustReplyHosts` 与它在 `app.js` 的 **8 处调用点逐字不变**（`1643 / 9618 / 9740 / 10000 / 10017 / 10031 / 10072 / 11591`）。删除 auto-preview 宿主后，只把函数体收缩为一行 `unmountLiveTrustReply();`。**禁止**把 8 处调用改回直接调 `unmountLiveTrustReply()`。
- Applies to：`app.js:173-178` 及上列 8 处调用点。
- Violation consequence：下次再新增第二个工作台宿主时，必然又漏掉 8 处中的某一处 —— 这正是 K-workbench-mode-source-ternary-trap 记录的原始缺陷形态。
- 来源：K-workbench-mode-source-ternary-trap

### Invariant I-6：宿主数变化必须同步 `requireTrustReplyWorkbenchRuntime` 的次数断言
- Rule：`requireTrustReplyWorkbenchRuntime(host)` 在 `app.js` 中的出现次数从 **4 降为 3**；`src/test/js/trustReplyWorkbenchSharedMount.test.js:352` 的 `assert.strictEqual((appSource.match(/requireTrustReplyWorkbenchRuntime\(host\)/g) || []).length, 4);` 必须改为 `3`。
- Applies to：`app.js` 全文计数；`src/test/js/trustReplyWorkbenchSharedMount.test.js:352`。
- Violation consequence：不改 → 该用例失败，`mvn test` 中止；改成不等式或删断言 → 丢掉「宿主数量受控」这条既有保护。
- 来源：original（本轮 grep 实证）

---

## 样式契约

### S-1：`.panel-head` 双按钮容器（新增 class）
- 背景（改动前基线）：`.panel-head` 是 `display:flex; align-items:center; justify-content:space-between; gap:12px`（`styles.css:815-822`）。它当前只有 2 个子元素（`h2` + 1 枚按钮），`space-between` 把两者推到两端。**直接塞入第 2 枚按钮会变成 3 个子元素被均分**，「检查回复」会漂到标题与「批量发送」中间的空白处，而不是贴着「批量发送」。因此必须用容器包住两枚按钮，让 `.panel-head` 保持 2 个子元素。
- 新增：把下列规则块**原样复制**到 `styles.css` 的 `.panel-head h2::before { display: none; }` 规则块（`styles.css:828-830`）之后、`/* Tables */` 注释之前：

```css
.panel-head-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
}
```

- 复用：两枚按钮各自的 class 逐字沿用既有 `.button`（次要）与 `.button.primary`（主色），**不新增按钮样式**。
- DOM 结构（`index.html` 目标骨架，逐字）：

```html
                <div class="panel-head">
                    <h2>已激活账号收发邮件记录</h2>
                    <div class="panel-head-actions">
                        <button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>
                        <button class="button primary" id="bulkOutreachBtn" onclick="handleBulkOutreach()">批量发送</button>
                    </div>
                </div>
```

- 使用点核查：`.panel-head-actions` 是**新 class**，全仓无同名规则（已 grep `styles.css` 与 `index.html`，0 命中）。本次只在上述这一处 `.panel-head` 内使用，其余所有 `.panel-head` **保持单按钮/无按钮的现状不动**。
- 禁止项：inline style；不得修改 `.panel-head`（`styles.css:815-822`）既有规则块；不得给 `.button` / `.button.primary` 加任何覆盖。

### S-2：专家列表工具栏移除后的收尾
- 改动前基线（`index.html:599-603`，逐字）：

```html
                    <button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>
                    <button class="button" id="bulkAutoReplyBtn">自动回复：加载中...</button>
                    <button class="button secondary" id="backfillOperatorStatusBtn" onclick="handleBackfillOperatorStatus()">回刷 ES</button>
```

- 改动后（逐字，只删中间不存在的行——即删掉第一行，其余两行**位置、缩进、内容全部不动**）：

```html
                    <button class="button" id="bulkAutoReplyBtn">自动回复：加载中...</button>
                    <button class="button secondary" id="backfillOperatorStatusBtn" onclick="handleBackfillOperatorStatus()">回刷 ES</button>
```

- 禁止项：不得顺手调整 `.toolbar-actions` 的 `gap` / 换行 / 对齐；不得给剩余按钮补 class。

### S-3：自动回复预览 DOM 整块删除，不留占位
- 改动前基线（`app.js:9853-9866`，`renderUnmatchedDetail` 内的 `autoPreviewHtml` 常量，含三元两分支）：

```js
    const autoPreviewHtml = record.expertContactId
        ? `<details class="detail-section reply-workflow-detail compose-workbench-section auto-preview-section" data-record-id="${id}">
            <summary class="reply-workflow-summary">
                <span class="reply-workflow-icon" aria-hidden="true">自</span>
                <span class="reply-workflow-title"><strong>自动回复预览</strong><small>若此刻开启自动回复，系统会怎么处理（只读，不发送、不写库）</small></span>
                <span class="reply-workflow-status" data-auto-preview-status>未生成</span>
                <span class="reply-workflow-chevron" aria-hidden="true">⌄</span>
            </summary>
            <div class="reply-workflow-content" data-trust-reply-auto-preview-host></div>
        </details>`
        : `<div class="detail-section reply-workflow-detail">
            <div class="reply-workflow-content">
                <p class="text-muted">该来信尚未绑定专家联系人，无法解析自动回复上下文。请先在上方完成绑定。</p>
            </div>
        </div>`;
```

- 改动后：整个 `autoPreviewHtml` 常量声明**删除**；`panel.innerHTML` 模板里的插值行（`app.js:9944`）由

```
            ${autoPreviewHtml}

            ${composeWorkbenchHtml}
```

  变为

```
            ${composeWorkbenchHtml}
```

  （删掉插值行与其后紧随的那一个空行，保持 `${historyHtml}` 与 `${composeWorkbenchHtml}` 之间只有一个空行的既有节奏）。
- 禁止项：不得留空 `<div>`、注释占位或 `hidden` 元素；不得改动 `mail-detail-group-label` 的「处理与回复」标题块；不得新增 CSS（`.auto-preview-section` 在 `styles.css` 中**本就无任何规则**，已 grep 确认 0 命中，因此本计划 `styles.css` 只新增 S-1 一处）。

---

## 现状审计

### `index.html` — 「检查回复」按钮
- 当前位置：`index.html:601`，在 `view-contacts` 的 `.toolbar > .toolbar-group.toolbar-actions` 内，夹在 `discoverBtnGroup`（split-button）与 `bulkAutoReplyBtn` 之间。
- 目标位置：`view-mailbox` 内 `<section class="panel">` 的 `.panel-head`（`index.html:745-748`），与 `bulkOutreachBtn` 同容器。
- 该 id 在 `index.html` 中出现 **1 次**（grep 实证）。

### `app.js` — CHECK_REPLIES 任务链路（读该按钮 id 的全部路径）
- 写路径（写按钮文案 / disabled 状态）：
  1. `setTaskButtonRunning(btnId)` ← `resumeProgressPollingIfNeeded()`（`app.js:743`）、任务 watcher
  2. `restoreTaskButton(btnId)`（`app.js:704-710`）
- 读路径（按 id 取元素）：
  1. `taskButtonOriginalTexts.checkRepliesBtn`（`app.js:690`）— 恢复文案
  2. `taskButtonMapping.CHECK_REPLIES.btnId`（`app.js:701`）— 进度恢复与弹窗标题
  3. `handleCheckReplies()`（`app.js:4881-4889`）— `openTaskModal(..., "checkRepliesBtn", ...)`
  4. `executeCheckReplies()`（`app.js:4891-4929`）— 同上 + `POST /api/mail/auto-reply/check-replies`
  5. `taskLaunchConfigs.CHECK_REPLIES`（`app.js:5205-5215`）— `btnId: "checkRepliesBtn"` + `preload` 读勾选
- **Interaction point A**：`executeCheckReplies`（`4899-4903`）与 `taskLaunchConfigs.CHECK_REPLIES.preload`（`5211-5215`）都读 `$$(".expert-select-cb:checked")`，而这些 checkbox 渲染在 `view-contacts` 内。按钮迁到 `view-mailbox` 后，两者**跨视图**。已实证 `.view` 只切 `.active`、不移除 DOM（`app.js:1652`），故勾选仍可读 → 行为不变（见 I-2）。
- **Interaction point B**：`resumeProgressPollingIfNeeded()` 只在 `setView("contacts")`（`app.js:1658`）与启动时（`app.js:12437`）调用。按钮迁走后，「进入收发件箱」不会触发运行态恢复。但 `setTaskButtonRunning` 按 id 取元素、DOM 常驻，故进入专家列表或刷新页面时按钮运行态**照常恢复**；任务进度本身由全局任务弹窗 + watcher 承载，与按钮所在视图无关。判定：**不改**（见执行顺序文档「已明确不做」第 3 条）。

### `app.js` — 自动回复预览（待删）的全部路径
- 挂载/卸载：
  1. `let autoPreviewTrustReplyInstance = null;`（`app.js:144`）
  2. `unmountAutoPreviewTrustReply()`（`app.js:168-171`）
  3. `unmountMailboxTrustReplyHosts()` 中的调用（`app.js:177`）
  4. `mountAutoPreviewTrustReply(recordId)`（`app.js:9636-9649`）— `runtime.mount(host, { mode: "AUTO_PREVIEW", ... })`
  5. 唯一调用点：`renderUnmatchedDetail` 尾部 `mountAutoPreviewTrustReply(Number(id));`（`app.js:9985`）
- 渲染/取数：
  6. `renderAutoPreviewIntoHost(host, preview)`（`app.js:9653-9671`）
  7. `renderAutoPreviewError(host, error)`（`app.js:9674-9677`）
  8. `waitForWorkbenchReady(host)`（`app.js:9681-9692`）— **仅被第 9 项调用**，随之作废
  9. `loadAutoPreviewIntoHost(recordId, host)`（`app.js:9694-9710`）— `api('/api/mail/unmatched-inbound/${recordId}/auto-reply-' + "preview")`
- DOM：
  10. `autoPreviewHtml` 常量（`app.js:9853-9866`）与插值 `${autoPreviewHtml}`（`app.js:9944`）
- **Interaction point C**：`mountAutoPreviewTrustReply` 是 `requireTrustReplyWorkbenchRuntime(host)` 的 4 个调用点之一；删除后计数变 3，与 `trustReplyWorkbenchSharedMount.test.js:352` 的等值断言冲突（见 I-6）。
- **Interaction point D**：`unmountMailboxTrustReplyHosts()` 有 8 处调用点，函数体收缩后语义等价于 `unmountLiveTrustReply()`，但函数名与调用点保留（见 I-5）。

### `trust-reply-workbench.js` — AUTO_PREVIEW 模式（本计划不动）
- `MODES.AUTO_PREVIEW`（`:6`）、`MODE_SOURCE` 映射（`:13`）、`validateMount` 的 `onComplete` 放宽（`:228-230`）、`readOnly` 派生（`:248`）、`readOnly` 的 7 处消费（`:292 / :324 / :874 / :2020 / :2029 / :2069 / :2514`）。删除该模式后 `trustReplyWorkbenchSharedMount.test.js:420` 需重写 —— 超出本计划范围，已在执行顺序文档中记为后续清理项。

### 后端（本计划不动，仅确认边界）
- `UnmatchedInboundMailController.kt:259` `@GetMapping("/unmatched-inbound/{id}/auto-reply-preview")` → `AutoReplyPreviewService.preview(id)`。
- 该 service 另被 3 个测试类注入（`UnmatchedInboundAiReplyTurnKnowledgeTest`、`UnmatchedInboundTrustWorkbenchTest`），删掉会牵动 Kotlin 测试 → 明确保留。

### 现有测试对本次改动的直接约束（必须同步）
| 文件:行 | 断言 | 本计划的影响 |
|---|---|---|
| `batchEntryRelocation.test.js:31-37` | `.panel-head` 内 `<h2>已激活账号收发邮件记录</h2>` **紧跟** `bulkOutreachBtn` 按钮标签 | S-1 插入 `.panel-head-actions` 包裹层 → 正则必失败，**必须改写** |
| `batchEntryRelocation.test.js:45-51` | `view-contacts` 片段内不含 `bulkOutreachBtn` | 不受影响 |
| `trustReplyWorkbenchSharedMount.test.js:347-348` | 三个缓存键相等 | bump 后仍相等 → 不改 |
| `trustReplyWorkbenchSharedMount.test.js:352` | `requireTrustReplyWorkbenchRuntime(host)` 出现 **4** 次 | 变 3 → **必须改** |
| `batchSendTaskConsoleVisualFix.test.js:49-51` | 三个缓存键 **逐字等于** `20260820-v8-trust-fact-actions` | **必须改**为新键 |
| `autoPreviewWorkbenchHost.test.js` 全文（4 个用例） | AUTO_PREVIEW 宿主挂载、写闸门、`wouldBeBlockedBy` 渲染、无 `onComplete` 挂载 | 宿主删除 → **必须改写为退休守卫** |
| `trustReplyWorkbench.test.js:573` | `!/style=/.test(host.innerHTML)` | 本计划不改 workbench → 不受影响 |

### 前端样式盘点
- 可复用 class：
  - `.button` — `styles.css`（基础按钮）— 「检查回复」沿用，次要视觉
  - `.button.primary` — 「批量发送」沿用，主色
  - `.panel-head` — `styles.css:815-822` — 容器，不改
  - `.panel-head h2` / `.panel-head h2::before` — `styles.css:824-830` — 不改
- 设计基准 token（本计划相关实值）：
  - `.panel-head`：`display:flex; align-items:center; justify-content:space-between; gap:12px; padding:12px 16px; border-bottom:1px solid var(--line)`
  - 按钮间距刻度：本仓库工具栏/操作组普遍用 `gap: 8px`（如 `.contact-head-actions`，`styles.css:1358`），S-1 沿用 `8px`
- DOM 结构约定：`<section class="panel"> > <div class="panel-head"> > <h2> + 操作区`
- 改动前基线：见 S-1 / S-2 / S-3 各自的「改动前基线」代码块（逐字摘录）

---

## 实现方案

### 阶段 1：搬按钮（I-1、I-2、S-1、S-2）

**T1-1**　`src/main/resources/static/index.html`：删除 `:601` 那一行「检查回复」按钮（S-2）。
**T1-2**　`src/main/resources/static/index.html`：把 mailbox `.panel-head`（`:745-748`）替换为 S-1 给出的逐字骨架。
**T1-3**　`src/main/resources/static/styles.css`：在 `:830` 之后插入 S-1 的 `.panel-head-actions` 规则块（逐字复制，不增删属性）。
**T1-4**　`app.js` **不改**任何 CHECK_REPLIES 相关代码（I-1）。

### 阶段 2：删自动回复预览（I-4、I-5、I-6、S-3）

**T2-1**　`app.js`：删 `:144` 的 `autoPreviewTrustReplyInstance` 声明。
**T2-2**　`app.js`：删 `:168-171` 的 `unmountAutoPreviewTrustReply()` 整个函数。
**T2-3**　`app.js`：`unmountMailboxTrustReplyHosts()`（`:173-178`）函数体收缩为单行 `unmountLiveTrustReply();`；函数名、注释语义与 8 处调用点**不动**（I-5）。注释同步改为说明「保留统一入口，便于将来再加宿主」。
**T2-4**　`app.js`：删 `:9636-9710` 的 5 个函数（`mountAutoPreviewTrustReply` / `renderAutoPreviewIntoHost` / `renderAutoPreviewError` / `waitForWorkbenchReady` / `loadAutoPreviewIntoHost`）及其上方注释。
**T2-5**　`app.js`：删 `:9848-9866` 的 `autoPreviewHtml` 常量与其上方 `// I-4:` 注释块（S-3）。
**T2-6**　`app.js`：删 `:9944` 的 `${autoPreviewHtml}` 插值行及紧随空行（S-3）。
**T2-7**　`app.js`：删 `:9985` 的 `mountAutoPreviewTrustReply(Number(id));`，`if (record.expertContactId) { … }` 块内只剩 `mountLiveTrustReply(Number(id));`。

### 阶段 3：缓存键与测试同步（I-3、I-6、I-4）

**T3-1**　`index.html:11 / 2074 / 2075` 三处 `?v=` 统一改为 `20260821-v9-check-replies-move`（I-3）。
**T3-2**　`src/test/js/batchSendTaskConsoleVisualFix.test.js:49-51` 三条字符串同步改为新键（I-3）。
**T3-3**　`src/test/js/trustReplyWorkbenchSharedMount.test.js:352` 的期望值 `4` → `3`（I-6）。
**T3-4**　`src/test/js/batchEntryRelocation.test.js`：改写 `I3-2: button is the second child of the panel-head, right after the panel h2` 这一用例，使其断言新的 `.panel-head-actions` 骨架（S-1），并保留其余 6 个用例逐字不变。新用例正文：

```js
    it("I3-2: both head buttons live in .panel-head-actions, 检查回复 before 批量发送", () => {
        const headPattern = new RegExp(
            `<div class="panel-head">\\s*<h2>${PANEL_HEADER}</h2>\\s*` +
            `<div class="panel-head-actions">\\s*` +
            `<button class="button" id="checkRepliesBtn" onclick="handleCheckReplies\\(\\)">检查回复</button>\\s*` +
            `${BUTTON_TAG.replace(/[.*+?^${}()|[\\]\\\\]/g, "\\\\$&")}\\s*` +
            `</div>`
        );
        assert.match(html, headPattern, "panel-head must wrap both buttons in .panel-head-actions with 检查回复 first");
    });
```

**T3-5**　`src/test/js/autoPreviewWorkbenchHost.test.js`：全文改写为退休守卫（I-4）。新内容（完整文件）：

```js
const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources", "static");
const appSource = fs.readFileSync(path.join(root, "app.js"), "utf-8");
const htmlSource = fs.readFileSync(path.join(root, "index.html"), "utf-8");
const cssSource = fs.readFileSync(path.join(root, "styles.css"), "utf-8");

// P1 (I-4): the AUTO_PREVIEW host in the mailbox detail panel is retired.
// The workbench runtime keeps the mode; only the host adapter is gone.
const RETIRED_TOKENS = [
    "data-trust-reply-auto-preview-host",
    "data-auto-preview-status",
    "data-auto-preview-body",
    "auto-preview-section",
    "autoPreviewTrustReplyInstance",
    "unmountAutoPreviewTrustReply",
    "mountAutoPreviewTrustReply",
    "loadAutoPreviewIntoHost",
    "renderAutoPreviewIntoHost",
    "renderAutoPreviewError",
    "waitForWorkbenchReady",
    "自动回复预览"
];

describe("AUTO_PREVIEW workbench host (retired in P1)", () => {
    it("I-4: no retired identifier survives in app.js / index.html / styles.css", () => {
        for (const token of RETIRED_TOKENS) {
            assert.ok(!appSource.includes(token), `app.js must not contain ${token}`);
            assert.ok(!htmlSource.includes(token), `index.html must not contain ${token}`);
            assert.ok(!cssSource.includes(token), `styles.css must not contain ${token}`);
        }
    });

    it("I-4: the degraded copy of the removed preview is gone too", () => {
        assert.ok(!appSource.includes("无法解析自动回复上下文"),
            "the auto-preview degraded notice must be removed with the section");
    });

    it("I-4: app.js no longer calls the auto-reply-preview endpoint", () => {
        assert.ok(!/auto-reply-\W*\+?\s*["']preview["']/.test(appSource),
            "app.js must not build the /auto-reply-preview path any more");
        assert.ok(!appSource.includes("auto-reply-preview"),
            "app.js must not reference the auto-reply-preview endpoint literally");
    });

    it("I-5: unmountMailboxTrustReplyHosts survives as the single teardown seam", () => {
        assert.match(appSource, /function unmountMailboxTrustReplyHosts\(\) \{\s*unmountLiveTrustReply\(\);\s*\}/,
            "the shared teardown entry must remain, with a LIVE-only body");
        const callSites = (appSource.match(/unmountMailboxTrustReplyHosts\(\)/g) || []).length;
        assert.ok(callSites >= 9, `expected the definition plus 8 call sites, found ${callSites}`);
    });

    it("I-4: the LIVE workbench host is untouched", () => {
        assert.ok(appSource.includes("data-trust-reply-live-host"), "LIVE host must remain");
        assert.ok(appSource.includes("function mountLiveTrustReply(recordId)"), "LIVE mount must remain");
    });
});
```

**T3-6**　新增 `src/test/js/checkRepliesRelocation.test.js`（I-1、I-2、S-1、S-2 的机器验证）。

---

## 变更文件清单

| # | 文件 | 动作 | 覆盖 |
|---|---|---|---|
| 1 | `src/main/resources/static/index.html` | 改 | T1-1, T1-2, T3-1 |
| 2 | `src/main/resources/static/app.js` | 改 | T2-1 … T2-7 |
| 3 | `src/main/resources/static/styles.css` | 改 | T1-3 |
| 4 | `src/test/js/batchEntryRelocation.test.js` | 改 | T3-4 |
| 5 | `src/test/js/autoPreviewWorkbenchHost.test.js` | 全文改写 | T3-5 |
| 6 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 改（1 行） | T3-3 |
| 7 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 改（3 行） | T3-2 |
| 8 | `src/test/js/checkRepliesRelocation.test.js` | 新增 | T3-6 |

合计 8 个文件，1 个子系统。`trust-reply-workbench.js` 与全部 Kotlin 源码/测试**不在清单内**。

---

## 验证命令

> 前提一：本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败（`CLAUDE.md` 的 Commands 章节）。
> 前提二：前端 JS 用例的权威门禁是 `node --test <file>` 单跑；`verify.sh` **只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件，不可作为本计划的回归门禁**（K-js-test-invocation-surface）。
> 环境实测：`node -v` = `v22.23.2`；`node --test src/test/js/batchEntryRelocation.test.js` 在改动前实测 `# pass 7 / # fail 0`。

```bash
# 1) 本计划直接改动/新增的 5 个 JS 用例（快速迭代用，可逐条复制）
node --test src/test/js/checkRepliesRelocation.test.js
node --test src/test/js/autoPreviewWorkbenchHost.test.js
node --test src/test/js/batchEntryRelocation.test.js
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js

# 2) 前端全量 JS 用例（本计划的前端回归门禁）
node --test src/test/js/*.test.js

# 3) 语法检查（pom 的 node-check-app / node-check-task-modal 等价物）
node --check src/main/resources/static/app.js
node --check src/main/resources/static/task-modal-runtime.js

# 4) 全量回归（Java/Kotlin + 上述 node 用例经 exec-maven-plugin 绑定在 test phase）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 5) 空白/换行卫生
git diff --check
```

通过判据：
- 命令 1/2：退出码 0，且输出含 `# fail 0`（`node --test src/test/js/*.test.js` 另需 `# cancelled 0`）。
- 命令 3：退出码 0，无输出。
- 命令 4：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0` 且 **输出中出现 `node --test` 的执行记录**（确认 `skipNodeTests` 未生效，K-js-test-invocation-surface 标注此点为推断，首次执行须确认）。
- 命令 5：退出码 0，无输出。

来源：`CLAUDE.md` 项目元信息（JDK 前缀与 mvn 命令）+ `pom.xml:188-231`（node 绑定）+ 实测（`node --test` 单跑）。

---

## 验收标准

- **I-1**：`grep -c 'id="checkRepliesBtn"' src/main/resources/static/index.html` == `1`；`grep -c 'checkRepliesBtn' src/main/resources/static/app.js` == `5` 且 5 处内容与改动前 diff 为空（`git diff src/main/resources/static/app.js | grep checkRepliesBtn` 无输出）。
- **I-2**：`git diff src/main/resources/static/app.js` 中**不含** `setView`、`resumeProgressPollingIfNeeded`、`expert-select-cb` 任一处改动；`checkRepliesRelocation.test.js` 断言 `app.js` 仍含 `$$(".expert-select-cb:checked")` 且 `setView` 仍用 `classList.toggle("active", ...)`。
- **I-3**：`grep -o '?v=[^"]*' src/main/resources/static/index.html | sort -u` 只输出一行 `?v=20260821-v9-check-replies-move`；`batchSendTaskConsoleVisualFix.test.js` 通过。
- **I-4**：`autoPreviewWorkbenchHost.test.js`（改写后）全部用例通过；`grep -rn 'auto-preview\|autoPreview\|AUTO_PREVIEW' src/main/resources/static/app.js src/main/resources/static/index.html src/main/resources/static/styles.css` 无输出。
- **I-5**：`grep -c 'unmountMailboxTrustReplyHosts()' src/main/resources/static/app.js` == `9`（1 处定义调用 + 8 处调用点；实际以改写后测试中的 `>= 9` 断言为准）；函数体正则匹配成功。
- **I-6**：`grep -c 'requireTrustReplyWorkbenchRuntime(host)' src/main/resources/static/app.js` == `3`；`trustReplyWorkbenchSharedMount.test.js` 通过。
- **S-1**：`batchEntryRelocation.test.js` 改写后的 I3-2 用例通过；`grep -n 'panel-head-actions' src/main/resources/static/styles.css` 命中且规则块与契约代码块 `diff` 为空；`grep -c 'panel-head-actions' src/main/resources/static/index.html` == `1`。
- **S-2**：`sed -n '/id="view-contacts"/,/id="view-mailbox"/p' src/main/resources/static/index.html | grep -c checkRepliesBtn` == `0`；同片段仍含 `bulkAutoReplyBtn` 与 `backfillOperatorStatusBtn`。
- **S-3**：`git diff src/main/resources/static/app.js` 中 `autoPreviewHtml` 相关行只有删除、无新增；`grep -c 'reply-workflow-detail' src/main/resources/static/app.js` 比改动前少 2（原折叠块 + 降级块）。
- 回归：执行「验证命令」节的命令 2、3、4、5 全部通过。

---

## 人工验收清单

### A-1：专家列表工具栏不再有「检查回复」
- 前置条件：以运营账号登录控制台，停留在「专家列表」视图，且当前**无**运行中的 CHECK_REPLIES 任务。
- 操作步骤：1）打开「专家列表」；2）观察右上角工具栏按钮组。
- 预期结果：按钮从左到右依次是 **刷新**、**发现专家**（带下拉箭头）、**自动回复：已开启 / 全部关闭 / 部分关闭**（文案随配置）、**回刷 ES**。整条工具栏**没有**「检查回复」四个字。
- 覆盖：需求描述 observable outcome 1；S-2

### A-2：收发件箱面板标题栏出现「检查回复」，紧贴「批量发送」左侧
- 前置条件：同 A-1。
- 操作步骤：1）左侧导航点「收发件箱」；2）观察「已激活账号收发邮件记录」面板的标题栏右侧。
- 预期结果：标题「已激活账号收发邮件记录」贴左；右侧是两枚横向排列的按钮，左为白底灰边的 **检查回复**，右为蓝底白字的 **批量发送**，两者间距约 8px 且**紧邻**（中间没有大片空白）；两枚按钮整体贴在标题栏最右侧。
- 覆盖：需求描述 observable outcome 2；S-1

### A-3：「检查回复」功能与迁移前一致（无运行中任务）
- 前置条件：无运行中任务；专家列表**不勾选**任何专家。
- 操作步骤：1）进入「收发件箱」；2）点「检查回复」；3）在弹出的任务启动弹窗里点确认执行；4）观察弹窗。
- 预期结果：第 2 步弹出标题为「检查回复」、描述为「检查所有已联系专家的邮箱回复。」的启动弹窗；第 3 步后弹窗切换到任务进度视图，进度条与日志正常滚动；任务完成后弹窗显示终态。
- 覆盖：需求描述 observable outcome 2；I-1

### A-4（跨路径 · Interaction point A）：专家列表勾选 → 收发件箱点「检查回复」仍定向生效
- 前置条件：无运行中任务；专家列表中至少有 3 位处于「已联系」的专家。
- 操作步骤：1）进入「专家列表」，勾选其中 **2 位**专家的复选框；2）**不取消勾选**，左侧导航切到「收发件箱」；3）点「检查回复」；4）在启动弹窗上查看它对本次范围的描述；5）确认执行，任务结束后查看任务进度弹窗里的处理条数。
- 预期结果：第 4 步弹窗显示的是**定向**范围（与迁移前在专家列表点击、勾选 2 位时显示的文案一致）；第 5 步处理条数为 **2**，不是全量。
- 覆盖：I-2；现状审计 Interaction point A

### A-5（回归 · Interaction point B）：运行中任务的按钮状态仍能恢复
- 前置条件：先在「收发件箱」启动一次「检查回复」，趁其 RUNNING 时进行下一步。
- 操作步骤：1）任务 RUNNING 时，按 F5 刷新整个页面；2）页面加载完成后进入「收发件箱」，观察「检查回复」按钮；3）再切到「专家列表」再切回「收发件箱」，再次观察。
- 预期结果：第 2、3 步「检查回复」按钮均显示为运行中态（禁用 + 运行文案），并且任务进度弹窗自动打开；任务结束后按钮恢复为可点击的「检查回复」四字。
- 覆盖：I-1、I-2；现状审计 Interaction point B

### A-6（回归）：「批量发送」按钮功能未受影响
- 前置条件：批量发送配置正常、有可用邮箱账号。
- 操作步骤：1）进入「收发件箱」；2）点「批量发送」；3）在弹窗中查看模板与收件人预估；4）取消（不实际发送）。
- 预期结果：弹窗正常打开、模板下拉与预估数字正常显示、取消后回到列表；按钮文案仍为「批量发送」，样式仍为蓝底白字主色按钮。
- 覆盖：需求描述 What must NOT change 第 3 条

### A-7：来信详情不再有「自动回复预览」
- 前置条件：收发件箱中存在一封**已绑定专家联系人**的来信。
- 操作步骤：1）进入「收发件箱」；2）点开该来信的详情；3）滚动到「处理与回复」分组，逐块查看。
- 预期结果：「处理与回复」分组下的折叠块依次为 **与该专家的历史信件记录**（若有历史）、**可信回复工作台**、**人工富文本回复**。**没有**任何标题为「自动回复预览」的折叠块，也没有写着「若此刻开启自动回复，系统会怎么处理」的副标题。
- 覆盖：需求描述 observable outcome 3；S-3

### A-8：未绑定专家的来信，降级灰字块一并消失
- 前置条件：收发件箱中存在一封**未绑定**专家联系人的来信（工单详情显示候选推荐列表）。
- 操作步骤：1）点开该来信详情；2）滚动到「处理与回复」分组。
- 预期结果：**看不到**「该来信尚未绑定专家联系人，无法解析自动回复上下文。请先在上方完成绑定。」这句灰字；「可信回复工作台」与「人工富文本回复」的显示与迁移前一致（未绑定时工作台本就不挂载）。
- 覆盖：需求描述 observable outcome 4；S-3

### A-9（回归）：可信回复工作台照常可用
- 前置条件：同 A-7 的已绑定来信，且该来信含至少 1 条可识别请求。
- 操作步骤：1）打开详情，展开「可信回复工作台」；2）点「一键预判」；3）等待生成完成；4）走到「回复框架与整合」页并点整合；5）把结果采用到人工回复区。
- 预期结果：工作台正常加载出摘要卡片；一键预判正常生成；整合正常产出；采用后「人工富文本回复」的正文编辑器被填入内容并弹出「草稿已采用到人工回复区，请确认后发送」提示。全程无「可信回复工作台资源加载失败」错误。
- 覆盖：需求描述 What must NOT change 第 6 条；I-5

### A-10（UI 目测 · 对照契约实值）：面板标题栏排版
- 前置条件：进入「收发件箱」。
- 操作步骤：逐项目测「已激活账号收发邮件记录」标题栏。
- 预期结果：① 两枚按钮垂直居中对齐，与标题 `h2` 同一水平基线；② 两枚按钮间距 **8px**；③ 按钮组紧贴标题栏右内边距（`padding: 12px 16px`）；④ 浏览器窗口缩到 1280px 宽时按钮不换行、不被裁切；⑤ 「检查回复」为默认 `.button` 外观（浅底 + 边框），「批量发送」为 `.button.primary`（蓝底白字），两者视觉层级有明显主次区分。
- 覆盖：S-1

### A-11（回归）：其余面板标题栏未受牵连
- 前置条件：无。
- 操作步骤：依次打开「邮箱账号」「邮件模板」「任务记录」「监控」四个视图，目测各自 `.panel-head` 的标题与按钮排布。
- 预期结果：各面板标题栏排版与本次改动前**完全一致**（标题贴左、按钮贴右，无新增空白或错位）。
- 覆盖：S-1 的「使用点核查」（新 class 只在一处使用）
