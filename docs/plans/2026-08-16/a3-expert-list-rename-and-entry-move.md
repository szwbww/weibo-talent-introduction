# A3：「专家联系」改名为「专家列表」 + 批量发送入口迁至收发件箱

> 编号：**A3**（全链第 3 份）。依赖：**必须在 A1、A2 之后执行**（同改 `index.html` 缓存键与
> `batchSendTaskConsoleVisualFix.test.js` 的断言字符串）。
> 共享不变量 M-1…M-4、共享审计 X-1…X-3 见 `batch-console-log-drawer-main.md`，本文不重复。

## 需求描述

**Observable outcome**

1. 左侧导航第 5 项的文案由「专家联系」改为「专家列表」，页面右上角的视图标题同步变为「专家列表」。
2. 「批量发送」按钮从专家列表页的工具栏移到「收发件箱」页「已激活账号收发邮件记录」面板的
   标题栏右侧；点击后打开的仍是同一个批量邮件任务控制台。

**What must NOT change**

- 内部视图键 `contacts` 一律不动：`index.html` 的 `data-view="contacts"`、
  `id="view-contacts"`、`app.js` 的 `viewMeta.contacts` 键名、`setView("contacts")`
  调用点、`state.contacts`、`localStorage["contacts-list-width"]`、CSS 的
  `.contacts-layout` / `.contacts-list-panel` / `.contacts-toolbar` 全部保持原名
  （用户不可见，改名零收益且会让老用户的分栏宽度偏好丢失 —— 见现状审计）。
- `id="bulkOutreachBtn"` 全文件仅一处（M-4）；其 class、onclick、文案「批量发送」不变。
- `app.js` 中引用该 id 的 4 处（`:674`、`:682`、`:5124`、`:5626`）一行不改。
- 专家列表页工具栏其余 7 个控件（筛选、排序、漏斗层级、刷新、发现专家分裂按钮、检查回复、
  自动回复、回刷 ES）的顺序与位置不变。
- 收发件箱工具栏（`index.html:690-726`）的 14 个控件一个不动 —— 按钮进的是面板标题栏，不是工具栏。
- 专家列表页内左栏面板标题 `<h2>专家列表</h2>`（`index.html:649`）保持不变。

**Out of scope（明确延后）**

- 「专家联系」相关的后端命名、API 路径（`/api/expert-contacts`）、DB 表名 —— 一律不动。
- 收发件箱页里另开一个批量发送的**筛选态入口**（按当前收发件箱筛选条件预置收件范围）——
  本次只是搬运入口，不改语义。
- `#taskProgressBar`（`index.html:599`，位于专家列表页内）不迁移：它的点击监听
  （`app.js:11557-11573`）**显式 `continue` 掉 `MANUAL_INITIAL_OUTREACH`**，与批量发送无关。
- 导航图标（`index.html:102-105` 的人形 svg）不换。

## 关键不变量

### Invariant I3-1: 改名只落在两处显示文案
- Rule: 本次改名只修改 `index.html:106` 的 `<span>专家联系</span>` 与 `app.js:514`
  `viewMeta.contacts` 数组的**第 0 个元素**。`viewMeta` 的**键名**、数组第 1 个元素（副标题）
  以及任何含 `contacts` 的标识符一律不动。
- Applies to: `index.html`、`app.js`。
- Violation consequence: 动了视图键会同时打断四联注册契约
  （`data-view` / `view-<name>` / `viewMeta` / `refreshCurrentView`，K-view-registration-triad），
  并让 `localStorage["contacts-list-width"]` 失配，老用户的左右分栏宽度回退默认值
  （K-contacts-layout-width-preference）。
- 来源: K-view-registration-triad、K-contacts-layout-width-preference

### Invariant I3-2: 按钮是搬运不是重建
- Rule: `#bulkOutreachBtn` 的整个元素标签**原样剪切**到新位置，属性与文本一字不改；
  不得新建一个按钮再删旧的。
- Applies to: `index.html`。
- Violation consequence: 见 M-4。另：`taskButtonOriginalTexts.bulkOutreachBtn = "批量发送"`
  （`app.js:674`）在任务运行结束时用 `btn.innerHTML = escapeHtml(...)` 还原文案，
  文案改了会与该常量失配。
- 来源: original（grep 实证见现状审计）

### Invariant I3-3: 新宿主是面板标题栏，不是工具栏
- Rule: 按钮插入 `#view-mailbox` 内「已激活账号收发邮件记录」面板的
  `<div class="panel-head">`，作为 `<h2>` 之后的第二个子元素。
- Applies to: `index.html:729-731`。
- Violation consequence: 放进 `.toolbar` 会让该工具栏变成 15 个控件，且把一个与筛选无关的
  动作按钮混进筛选区；`.panel-head` 已是 `display:flex; justify-content:space-between`
  （`styles.css:815-822`），放进去零新增 CSS 即右对齐。
- 来源: original（用户在本轮明确选定「面板头部右侧」）

## 样式契约

### S3-1: 导航文案（就地改文本，零 CSS）

`index.html:101-107` 改后逐字为（**只有第 6 行的文本变了**）：

```html
<button class="nav-tab" data-view="contacts">
    <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round">
        <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>
        <path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
    </svg>
    <span>专家列表</span>
</button>
```

`app.js:514` 改后逐字为：

```js
    contacts: ["专家列表", "查看联系状态、邮件时间线和人工处理。"],
```

- 禁止项：改 `data-view`；改 svg；改副标题文案；给 `<span>` 加 class。

### S3-2: 批量发送按钮的新宿主（复用既有 class，零新增 CSS）

**复用**：`.panel-head`（`styles.css:815-822`，`display:flex; align-items:center;
justify-content:space-between; gap:12px; padding:12px 16px`）——
已有先例 `index.html:736-739`（`#closeUnmatchedDetailBtn` 就放在同类 `panel-head` 里）。
按钮自身沿用原有的 `.button.primary`。

**改动后 DOM**（`index.html` 现 729-733 行的 `<section class="panel">` 整块）：

```html
<section class="panel">
    <div class="panel-head">
        <h2>已激活账号收发邮件记录</h2>
        <button class="button primary" id="bulkOutreachBtn" onclick="handleBulkOutreach()">批量发送</button>
    </div>
    <div class="mailbox-list" id="mailboxList"></div>
    <div class="pagination" id="mailboxPagination" style="padding: 16px 24px; display: flex; justify-content: flex-end; gap: 8px;"></div>
</section>
```

**同时**，专家列表页工具栏（`index.html:589-594`）删掉整行第 591 行，改后该段逐字为：

```html
                    <button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>
                    <button class="button" id="bulkAutoReplyBtn">自动回复：加载中...</button>
                    <button class="button secondary" id="backfillOperatorStatusBtn" onclick="handleBackfillOperatorStatus()">回刷 ES</button>
```

- 禁止项：`styles.css` 任何改动（本计划零 CSS 改动）；inline style；新增 class；
  改 `#mailboxPagination` 上既有的 inline style（历史遗留，不在本计划范围）。

### S3-3: 缓存键

`index.html` 三处缓存键逐字改为 `20260817-v3-expert-list-entry-move`，
并同步 `batchSendTaskConsoleVisualFix.test.js` 中的三条断言字符串（M-2）。

## 现状审计

### 「专家联系」这一文案的全仓分布

```
$ grep -rn "专家联系" --include=*.js --include=*.html --include=*.kt --include=*.css src/
src/main/resources/static/index.html:106:                <span>专家联系</span>
src/main/resources/static/app.js:514:    contacts: ["专家联系", "查看联系状态、邮件时间线和人工处理。"],
src/main/kotlin/.../campaign/service/ConversationStateService.kt:71:  "该专家联系已关闭，无需继续自动跟进。"   ← 邮件/状态文案，与导航无关，不改
src/test/kotlin/.../campaign/OperatorStatusWriteSeamGuardTest.kt:52:  // 注释，不改
```

**结论：用户可见的导航文案恰好 2 处**，即 I3-1 覆盖的两处。
另注意 `index.html:649` 已有 `<h2>专家列表</h2>`（专家列表页左栏面板标题），
改名后导航与该面板标题同名 —— 这是**期望内的**（左侧导航项 vs 页内面板标题，层级不同），不做去重。

### 视图键 `contacts` 的全部使用点（论证「不改内部键」的代价）

```
$ grep -rn '"contacts"\|contacts:\|data-view="contacts"\|view-contacts\|contacts-' \
    src/main/resources/static/app.js src/main/resources/static/index.html src/main/resources/static/styles.css
app.js:22        state.contacts: []
app.js:514       viewMeta.contacts
app.js:1636      setView 内 if (view === "contacts") resumeProgressPollingIfNeeded()
app.js:1648      refreshCurrentView 内 if (state.view === "contacts") await loadContacts()
app.js:7261      setView("contacts")
app.js:8045      api("/api/expert-contacts")（后端路径，与视图键无关）
index.html:101   data-view="contacts"
index.html:448   id="view-contacts"
index.html:645   class="split-layout contacts-layout"
index.html:647   class="panel contacts-list-panel"
index.html:449   class="toolbar contacts-toolbar"
styles.css       .contacts-layout / .contacts-list-panel / .contacts-toolbar 等规则
```

外加 `localStorage["contacts-list-width"]`（K-contacts-layout-width-preference：
该键存左栏宽度，默认值只在键缺失时使用；CSS `.contacts-layout` 的基础宽度与 JS 默认值必须同值）。
**改内部键 = 至少 11 个代码位置 + 1 个持久化键 + 一批 CSS 规则**，且会让所有老用户的
分栏宽度偏好静默失效。用户已在本轮明确选择「只改显示文案」。

### `#bulkOutreachBtn` 的全部引用点

```
$ grep -rn "bulkOutreachBtn" src/
src/main/resources/static/index.html:591   <button ... id="bulkOutreachBtn" onclick="handleBulkOutreach()">批量发送</button>
src/main/resources/static/app.js:674       taskButtonOriginalTexts: { bulkOutreachBtn: "批量发送" }
src/main/resources/static/app.js:682       taskButtonMapping.MANUAL_INITIAL_OUTREACH.btnId
src/main/resources/static/app.js:5124      taskLaunchConfigs.MANUAL_INITIAL_OUTREACH.btnId
src/main/resources/static/app.js:5626      openTaskModal(taskType, "批量发送邮件", "bulkOutreachBtn", { launchRequested: true })
src/test/js/expertTagBatchFix.test.js:188-191   断言 index.html 中 id="bulkOutreachBtn" 恰好出现 1 次
```

四处 app.js 引用**全部走 `$('#'+btnId)` 按 id 全文档查询**
（`restoreTaskButton` `:687`、`setTaskButtonRunning` `:882`），
与按钮所在的 view 是否可见无关 —— 因此搬到收发件箱后，
「任务运行中按钮置灰 / 结束后还原文案」的行为自动继续生效，无需任何 JS 改动。

### `handleBulkOutreach` 的依赖面

```js
// app.js:5607-5611
async function handleBulkOutreach() {
    const modal = document.getElementById("batchSendTaskModal");
    if (!modal) return;
    openBatchSendTaskModal();
}
```

**不读任何专家列表页的筛选状态**，因此搬运不改变语义。
`#batchSendTaskModal` 是 `<body>` 直挂的 `.modal-overlay`（`index.html:1093`），与两个 view 都无父子关系。

### 收发件箱页的目标宿主

- `index.html:729-733`：
  ```html
  <section class="panel">
      <div class="panel-head"><h2>已激活账号收发邮件记录</h2></div>
      <div class="mailbox-list" id="mailboxList"></div>
      <div class="pagination" id="mailboxPagination" style="...">…</div>
  </section>
  ```
  `panel-head` 当前只有一个 `<h2>` 子元素，右侧是空的。
- 同页 `index.html:736-739` 已有「`panel-head` 里放按钮」的先例：
  `<button class="button secondary" id="closeUnmatchedDetailBtn">收起面板</button>`。
- `.panel-head { justify-content: space-between }`（`styles.css:815-822`）→
  第二个子元素自动右对齐，**零新增 CSS**。

### 前端样式盘点

- **可复用 class**：`.panel-head`（`styles.css:815-822`）、`.panel-head h2`（`:824-826`）、
  `.panel-head h2::before { display: none }`（`:828-830`）、`.button.primary`、`.nav-tab`。
- **设计基准 token**：`panel-head` 内边距 `12px 16px`、子元素间距 `gap: 12px`、
  下边框 `1px solid var(--line)`；导航项文案用 `<span>` 裸文本无 class。
- **DOM 结构约定**：`nav-tab` = `<button class="nav-tab" data-view="X"><svg/><span>文案</span></button>`；
  面板 = `<section class="panel"><div class="panel-head"><h2/>[动作按钮]</div>…</section>`。
- **改动前基线**：`index.html:101-107`（导航项）、`index.html:591`（按钮原位）、
  `index.html:729-733`（目标面板）、`app.js:514`（viewMeta 行）—— 逐字内容已在上文引出。

### Interaction points

1. **按钮位置（index.html）× 任务按钮状态机（app.js 四处 btnId 引用）**：
   靠 id 全文档查询解耦，搬运不需要 JS 改动 —— 但必须由 A3-4 黑盒验证「运行中置灰、结束还原」仍生效。
2. **按钮出现次数（index.html）× `expertTagBatchFix.test.js:188-191`**：剪切而非复制才能保持恰好 1 次。

## 实现方案

### 阶段 A：改名（I3-1 / S3-1）

- **T3-A1** `index.html:106`：`专家联系` → `专家列表`。
- **T3-A2** `app.js:514`：`contacts: ["专家联系", ...]` → `contacts: ["专家列表", ...]`，
  键名与副标题不动。

### 阶段 B：搬运入口（I3-2 / I3-3 / S3-2）

- **T3-B1** `index.html`：删除第 591 行整行。
- **T3-B2** `index.html:730`：把 `<div class="panel-head"><h2>已激活账号收发邮件记录</h2></div>`
  展开成三行结构，把 T3-B1 剪下的按钮标签**原样**粘进 `<h2>` 之后。遵守 I3-2 / I3-3 / S3-2。

### 阶段 C：缓存键与测试（M-2 / M-4 / X-2）

- **T3-C1** `index.html` 三处缓存键按 S3-3 改值。
- **T3-C2** `batchSendTaskConsoleVisualFix.test.js` 三条缓存键断言同步改值。
- **T3-C3** 新建 `src/test/js/batchEntryRelocation.test.js`，对 `index.html` 源码断言：
  - `id="bulkOutreachBtn"` 恰好出现 1 次（与 `expertTagBatchFix.test.js` 重复但独立守护本计划）；
  - 该按钮出现在 `id="view-mailbox"` 之后、`id="view-inbound-summary"` 之前（下标比较）；
  - 该按钮的前一个非空白兄弟是 `<h2>已激活账号收发邮件记录</h2>`；
  - 按钮标签中 `class="button primary"`、`onclick="handleBulkOutreach()"`、
    文本 `批量发送` 三者逐字保持；
  - `id="view-contacts"` 与 `id="view-mailbox"` 之间的片段中**不含** `bulkOutreachBtn`；
  - 导航项：`data-view="contacts"` 所在 button 内的 `<span>` 文本为 `专家列表`；
  - `app.js` 中 `viewMeta` 的 `contacts` 项第 0 元素为 `"专家列表"`，且
    `data-view="contacts"` / `id="view-contacts"` / `viewMeta` 键 / `refreshCurrentView`
    的 `"contacts"` 四联注册点全部仍为 `contacts`（I3-1 的源码级守护）。

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/resources/static/index.html` | 导航文案；删第 591 行按钮；面板标题栏插入该按钮；三处缓存键改值 |
| 2 | `src/main/resources/static/app.js` | `viewMeta.contacts` 标题文案（仅 1 行） |
| 3 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 缓存键断言改值 |
| 4 | `src/test/js/batchEntryRelocation.test.js` | **新建** |

文件数 4 ≤ 10；子系统 1（前端静态资源 + 其 JS 测试）≤ 2。
**无 styles.css 改动** —— S3-2 已论证零新增 CSS。
`expertTagBatchFix.test.js` 不在清单内：它只数出现次数，剪切后仍是 1 次，无需改。

## 验证命令

> 全量回归、构建、`node --check`、`git diff --check` 一律使用主计划 `## 共享审计 / X-3`。

```bash
# 本计划新建的用例
node --test src/test/js/batchEntryRelocation.test.js

# 被本计划修改的既有用例
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js

# 直接数 bulkOutreachBtn 出现次数的既有用例（必跑）
node --test src/test/js/expertTagBatchFix.test.js
```

通过判据：每条输出 `# fail 0` 且退出码 0。

## 验收标准

- **I3-1**：`batchEntryRelocation.test.js` 的「四联注册点仍为 contacts」与
  「导航 span 为 专家列表」两组断言通过；
  `grep -c "专家联系" src/main/resources/static/index.html src/main/resources/static/app.js`
  两者均输出 `0`；
  `grep -rn "contacts-list-width" src/main/resources/static/app.js` 仍有命中且键名未变。
- **I3-2 / M-4**：`grep -c 'id="bulkOutreachBtn"' src/main/resources/static/index.html` 输出 `1`；
  `git diff src/main/resources/static/app.js` 中**不含** `bulkOutreachBtn` 相关行
  （四处引用一行未动）；`expertTagBatchFix.test.js` 全绿。
- **I3-3 / S3-2**：`batchEntryRelocation.test.js` 的位置类断言（在 view-mailbox 区间内、
  紧跟目标 `<h2>`、不在 view-contacts 区间内）全部通过；
  `git diff src/main/resources/static/styles.css` 为空。
- **S3-1**：`git diff src/main/resources/static/app.js` 恰为 1 行改动，且改后内容与契约代码块逐字一致。
- **S3-3 / M-2**：`grep -c "20260817-v3-expert-list-entry-move" src/main/resources/static/index.html`
  输出 `3`；同串在 `batchSendTaskConsoleVisualFix.test.js` 中出现 3 次。
- **回归**：执行主计划 X-3 的全量测试与构建命令通过。

## 人工验收清单

### A3-1: 导航与视图标题同步改名
- 前置条件：无。
- 操作步骤：
  1. 打开后台，观察左侧导航第 5 项。
  2. 点进去，观察页面右上角（内容区顶部）的视图标题与副标题。
- 预期结果：导航项文案为「专家列表」（图标不变）；视图标题为「专家列表」，
  副标题仍为「查看联系状态、邮件时间线和人工处理。」；页内左栏面板标题仍是「专家列表」。
- 覆盖：需求描述 1、I3-1

### A3-2: 专家列表页不再有批量发送按钮
- 前置条件：无。
- 操作步骤：进入「专家列表」，看工具栏右侧一排按钮。
- 预期结果：依次为「刷新」「发现专家 ▾」「检查回复」「自动回复：…」「回刷 ES」，
  **没有**「批量发送」。其余按钮顺序与位置与改动前一致。
- 覆盖：需求描述 2、What must NOT change 第 5 条

### A3-3: 收发件箱面板标题栏出现批量发送
- 前置条件：无。
- 操作步骤：
  1. 进入「收发件箱」。
  2. 观察「已激活账号收发邮件记录」面板的标题栏。
  3. 点击右侧的「批量发送」。
- 预期结果：标题栏左侧是「已激活账号收发邮件记录」，右侧是蓝色主按钮「批量发送」，
  两者在同一水平线上、按钮贴右边缘；上方的筛选工具栏 14 个控件一个不多一个不少。
  点击后弹出「批量邮件任务控制台」，默认停在「定时任务」页签，任务列表正常加载。
- 覆盖：需求描述 2、I3-3、S3-2

### A3-4: 跨路径 —— 按钮搬家后任务状态机仍打在它身上（interaction point 1）
- 前置条件：能触发一次批量发送（在控制台里「手动执行」跑一轮即可）。
- 操作步骤：
  1. 收发件箱 → 点「批量发送」→ 在控制台里发起一次执行。
  2. 关掉控制台弹窗，回到收发件箱页面，观察面板标题栏里的「批量发送」按钮。
  3. 等这一轮执行结束后再观察一次。
- 预期结果：执行期间该按钮变为禁用态（置灰不可点）；执行结束后恢复可点，
  文案仍是「批量发送」（不是空白或别的文字）。
- 覆盖：interaction point 1、I3-2

### A3-5: 视图切换与分栏宽度偏好未受影响（回归）
- 前置条件：先在「专家列表」页拖动中缝，把左栏调成一个明显不同于默认的宽度，然后刷新页面。
- 操作步骤：
  1. 刷新后进入「专家列表」，确认左栏宽度保持你调过的值。
  2. 在「专家列表」「收发件箱」「来信汇总」之间来回切换 3 次。
- 预期结果：左栏宽度保持不变（说明 `localStorage` 键未失配）；三个页签切换均正常加载数据，
  控制台无报错。
- 覆盖：What must NOT change 第 1 条、I3-1
