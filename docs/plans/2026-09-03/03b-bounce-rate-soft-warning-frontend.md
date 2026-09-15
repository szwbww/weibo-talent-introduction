# 03B · 发件账号列表显示硬退率软告警（前端）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `execute-p` to implement this approved plan, then use `fix-v` for independent verification. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在发件账号列表“当前状态”列显示黄色“硬退率过高”徽标；该徽标只读 `hardBounceRateHigh`，不产生恢复按钮、不改变账号启用或暂停状态。

**Architecture:** 只修改现有 `loadAccounts()` 的字符串模板，复用 `.badge.warn`，不增加 DOM 节点、CSS 或接口请求。同步把静态资源三联缓存键从 `20260902-legacy-retire` bump 为 `20260903-bounce-warning`，并更新通过当前键反查出的全部 7 个固定值测试文件。

**Tech Stack:** 原生 JavaScript、HTML、CSS、Node.js test runner。

**Spec:** 需求方 2026-09-03 决策：“不要把这个门槛做成硬门槛，只提示硬退率过高”。依赖 `03a-bounce-rate-soft-warning-backend.md`。

**Dependency:** 03A 已部署或同一发布中先完成，`GET /api/mail/sender-accounts` 每行含非空布尔 `hardBounceRateHigh`。

## Global Constraints

- 不新增 CSS；只复用 `.badge.warn`。
- 不增加第二次 API 请求；继续只请求一次 `/api/mail/sender-accounts`。
- 不改 `autoSendPaused`、`enabled`、恢复按钮的判定。
- 静态资源缓存键三项必须同值：`styles.css`、`trust-reply-workbench.js`、`app.js`。
- 新缓存键固定为 `20260903-bounce-warning`。

---

## 需求描述

### Observable outcome

1. `hardBounceRateHigh=true` 的账号在“当前状态”列增加黄色徽标，文字严格为 `硬退率过高`。
2. 仅硬退率告警、`autoSendPaused=false` 的账号仍显示“启用”，没有“恢复发送”按钮，自动发送行为不受 UI 影响。
3. `autoSendPaused=true` 的其他故障仍显示“自动暂停”及“恢复发送”；若同时有硬退率告警，两枚徽标并列显示。
4. 新版 `app.js` 通过 `?v=20260903-bounce-warning` 加载，避免浏览器继续使用旧缓存。

### What must NOT change

1. 账号表列数、列顺序、今日发信/预热展示、绑定专家数和全部管理按钮不变。
2. “自动暂停”徽标仍由 `account.autoSendPaused === true` 决定，tooltip 仍使用 `autoSendPausedReason`。
3. “恢复发送”按钮仍且仅在 `autoSendPaused===true` 时出现。
4. 无告警账号不出现“硬退率过高”。后端旧版本未返回字段时按 false 处理。
5. 不修改账号编辑/查看弹窗的 `updateAccountStatusBadge`。

### Out of scope

- 不在前端计算硬退率、阈值或最小样本。
- 不显示具体分子、分母、百分比，不新增弹窗、图表、toast 或通知中心。
- 不改监控页、批量任务页、Postmaster 图表。
- 不新增或修改 CSS。
- 不改任何 Kotlin、SQL、API DTO。

## 关键不变量

### Invariant I-1: 告警只读后端布尔值

- Rule: 唯一判断为 `account.hardBounceRateHigh === true`；不得读取 HARD 数量、sentCount、rate，不得在 JS 写 7/20/5%。缺失、null、false 均不显示。
- Applies to: `app.js.loadAccounts`。
- Violation consequence: 前后端判据重复并可能漂移，或旧后端返回缺字段时误告警。
- 来源: original；03A I-3。

### Invariant I-2: 告警与暂停是正交状态

- Rule: 告警徽标与自动暂停徽标分别独立拼接；告警不得改写 `autoPaused`，不得进入 actions 条件；恢复按钮条件保持 `if (autoPaused)`。
- Applies to: `loadAccounts` 的 statusCell 与 actions。
- Violation consequence: 软告警再次表现为硬暂停，或真实暂停丢失恢复入口。
- 来源: original。

### Invariant I-3: DOM 与表结构不扩张

- Rule: 新提示只是在现有 `<td>${statusCell}</td>` 内增加 `<span>`；不得修改 index.html 表头、tbody id、列数或操作列。
- Applies to: `app.js.loadAccounts`、`index.html#accountsTable`。
- Violation consequence: 表格列错位、既有 selector/test 失效。
- 来源: original。

### Invariant I-4: 静态资源缓存键三联一致

- Rule: `index.html` 中 `styles.css?v=`、`trust-reply-workbench.js?v=`、`app.js?v=` 必须全部为 `20260903-bounce-warning`；通过旧键反查出的 7 个测试文件全部同步；仓库不得残留旧键 `20260902-legacy-retire`。
- Applies to: index.html 与 7 个缓存键固定值测试。
- Violation consequence: Maven 的 Node 测试失败，或浏览器继续加载旧 app.js 看不到告警。
- 来源: `K-frontend-cache-key-triad`（2026-09-03 代码重新 grep 后确认实际为 7 个固定值测试文件）。

## 样式契约

### S-1: “硬退率过高”徽标

- 复用：`src/main/resources/static/styles.css:1042-1066` 的 `.badge` 与 `.badge.warn`；本计划不修改这些规则。
- 设计实值：
  - `.badge`: `display:inline-flex`、`align-items:center`、`padding:2px 8px`、`border-radius:999px`、`font-size:11px`、`font-weight:600`、`line-height:1`、`border:1px solid transparent`。
  - `.badge.warn`: background `var(--warning-bg)` = `rgba(217, 119, 6, 0.08)`；color `var(--warning)` = `#d97706`；border `var(--warning-border)` = `rgba(217, 119, 6, 0.2)`。
- DOM 结构：执行代码必须生成下列逐字元素；只允许外层拼接时保留现有前导空格。

```html
<span class="badge warn" title="近7天硬退率超过5%（已发至少20封）；仅提示，不影响自动发送">硬退率过高</span>
```

- 禁止项：inline style；新 class；修改 `.badge`/`.badge.warn`；增加 icon；把 tooltip 改成动态前端计算值。

### S-2: 当前状态单元格组合顺序

- 复用：现有 `badge(account.enabled ? "启用" : "禁用", ...)` 和现有自动暂停 span。
- DOM 结构：状态内容顺序固定为“启用/禁用” → “自动暂停（若有）” → “硬退率过高（若有）”。

```javascript
const autoPaused = account.autoSendPaused === true;
const hardBounceRateHigh = account.hardBounceRateHigh === true;
const statusCell = badge(account.enabled ? "启用" : "禁用", account.enabled ? "ok" : "error")
    + (autoPaused
        ? ` <span class="badge warn" title="${escapeHtml(account.autoSendPausedReason || "自动暂停")}">自动暂停</span>`
        : "")
    + (hardBounceRateHigh
        ? ` <span class="badge warn" title="近7天硬退率超过5%（已发至少20封）；仅提示，不影响自动发送">硬退率过高</span>`
        : "");
```

- 禁止项：改变 `if (autoPaused)` 恢复按钮分支；将两个状态合并成一枚徽标；修改状态主徽标颜色。

## 现状审计

### 发件账号列表 DOM

- Schema/structure:
  - `index.html:293-308` 固定 7 列：账号代码、邮箱地址、分发策略权重、今日发信上限、当前状态、绑定专家数、管理操作。
  - tbody 唯一 id 为 `accountsTable`。
- Write path:
  1. `app.js.loadAccounts()` 请求 `/api/mail/sender-accounts` 后一次性写 `#accountsTable.innerHTML`。
- Read/event paths:
  1. `handleAccountAction` 依赖各 button 的 `data-action/data-code`。
  2. `senderBindingDisplay.test.js` 通过 `extractFn("loadAccounts")` 与 DOM stub 验证输出。
- Interaction points:
  - 03A Controller JSON `hardBounceRateHigh` → `loadAccounts` → 状态单元格。
  - actions 仍只读取 `autoPaused`，因此软告警不会生成恢复按钮。

### 前端样式盘点

- 可复用 class:
  - `.badge` — `styles.css:1042-1054` — 通用胶囊徽标。
  - `.badge.ok` — `styles.css:1056-1060` — 启用状态。
  - `.badge.warn` — `styles.css:1062-1066` — 当前自动暂停徽标；本计划复用。
  - `.badge.error` — `styles.css:1068-1072` — 禁用状态。
- 设计基准 token:
  - `--warning:#d97706`、`--warning-bg:rgba(217,119,6,0.08)`、`--warning-border:rgba(217,119,6,0.2)`，定义于 `styles.css:45-49`。
- DOM 结构约定:
  - 主状态由 `badge()` helper 输出 `<span class="badge TYPE">TEXT</span>`。
  - 附加状态直接在同一字符串后拼 ` <span class="badge warn" ...>`。
- 改动前基线:

```javascript
const autoPaused = account.autoSendPaused === true;
const statusCell = badge(account.enabled ? "启用" : "禁用", account.enabled ? "ok" : "error")
    + (autoPaused
        ? ` <span class="badge warn" title="${escapeHtml(account.autoSendPausedReason || "自动暂停")}">自动暂停</span>`
        : "");
```

- 本计划不修改任何既有 class，故不产生 class 全局影响面。

### 静态资源缓存键

- Store: `index.html` 三个 URL query string；无数据库。
- Current value: `20260902-legacy-retire`。
- Write path: 人工源码修改。
- Read paths:
  1. 浏览器读取三条 asset URL。
  2. 固定值测试文件 7 个：
     - `batchSendTaskConsoleVisualFix.test.js`
     - `checkRepliesRelocation.test.js`
     - `manualReplySubjectPrefill.test.js`
     - `overlayAndDialogContrast.test.js`
     - `ragKnowledgeBasePage.test.js`
     - `ragWorkbenchRender.test.js`
     - `trustReplyWorkbenchSharedMount.test.js`
- Interaction point: 修改 app.js → index.html bump → 7 个固定值断言同步。（来源: `K-frontend-cache-key-triad`）

## 实现方案

### Task 1: 先写账号列表软告警测试（I-1、I-2、I-3、S-1、S-2）

**Files:**
- Modify: `src/test/js/senderBindingDisplay.test.js`

**Interfaces:**
- Consumes planned backend JSON field `hardBounceRateHigh: Boolean`.

- [ ] **Step 1: Add a failing warning-only rendering test**

在 `senderBindingDisplay accounts table` describe 内新增用例，sandbox 只返回一行：

```javascript
{
    accountCode: "WARN_ONLY",
    senderEmail: "warn@example.com",
    strategyWeight: 100,
    todaySentCount: 1,
    effectiveDailyLimit: 100,
    dailySendLimit: 100,
    enabled: true,
    autoSendPaused: false,
    hardBounceRateHigh: true,
    boundExpertCount: 12
}
```

断言：HTML 包含 `硬退率过高`、S-1 的完整 title、`badge warn`；不包含 `data-action="resume-auto-send"`，但包含主状态 `启用`。

- [ ] **Step 2: Add an orthogonal-state test**

构造 `autoSendPaused=true`、`autoSendPausedReason="SELF_CHECK_FAILED:timeout"`、`hardBounceRateHigh=true`，断言同一行同时包含 `自动暂停`、reason、`硬退率过高` 和 `resume-auto-send`。

- [ ] **Step 3: Run RED**

```bash
node --test src/test/js/senderBindingDisplay.test.js
```

Expected: 新断言失败，因为 `loadAccounts` 尚未读取 `hardBounceRateHigh`。

### Task 2: 最小修改 `loadAccounts`（I-1、I-2、I-3、S-1、S-2）

**Files:**
- Modify: `src/main/resources/static/app.js`

**Interfaces:**
- Consumes `account.hardBounceRateHigh` from 03A.
- Produces exact S-1 DOM in existing status cell.

- [ ] **Step 1: Replace only the statusCell setup with S-2 block**

逐字使用 S-2 JavaScript；不得改 actions 数组、`if (autoPaused)`、表格列模板、API URL。

- [ ] **Step 2: Run focused test GREEN**

```bash
node --test src/test/js/senderBindingDisplay.test.js
node --check src/main/resources/static/app.js
```

Expected: exit 0；Node test 输出 `# fail 0`。

### Task 3: 同步三联缓存键（I-4）

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/test/js/batchSendTaskConsoleVisualFix.test.js`
- Modify: `src/test/js/checkRepliesRelocation.test.js`
- Modify: `src/test/js/manualReplySubjectPrefill.test.js`
- Modify: `src/test/js/overlayAndDialogContrast.test.js`
- Modify: `src/test/js/ragKnowledgeBasePage.test.js`
- Modify: `src/test/js/ragWorkbenchRender.test.js`
- Modify: `src/test/js/trustReplyWorkbenchSharedMount.test.js`

- [ ] **Step 1: Replace the key in all audited locations**

机械替换：

```text
20260902-legacy-retire
→
20260903-bounce-warning
```

在 `index.html` 必须恰好得到：

```html
<link rel="stylesheet" href="styles.css?v=20260903-bounce-warning">
<script src="trust-reply-workbench.js?v=20260903-bounce-warning"></script>
<script src="app.js?v=20260903-bounce-warning"></script>
```

- [ ] **Step 2: Prove the audited set is complete**

```bash
rg -n -F "20260902-legacy-retire" src/main/resources/static/index.html src/test
rg -l -F "20260903-bounce-warning" src/test | sort
```

Expected: 第一条无输出；第二条恰好输出本 Task 列出的 7 个 test 文件，不多不少。

- [ ] **Step 3: Run cache-key tests**

```bash
node --test \
  src/test/js/batchSendTaskConsoleVisualFix.test.js \
  src/test/js/checkRepliesRelocation.test.js \
  src/test/js/manualReplySubjectPrefill.test.js \
  src/test/js/overlayAndDialogContrast.test.js \
  src/test/js/ragKnowledgeBasePage.test.js \
  src/test/js/ragWorkbenchRender.test.js \
  src/test/js/trustReplyWorkbenchSharedMount.test.js
```

Expected: exit 0、`# fail 0`。

### Task 4: 前端整体验证

- [ ] **Step 1: Run all JS tests and syntax checks**

```bash
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
node --check src/main/resources/static/task-modal-runtime.js
```

Expected: all exit 0；test output includes `# fail 0`。

- [ ] **Step 2: Run Maven integration gate**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH \
mvn test package
```

Expected: `BUILD SUCCESS`；exec-maven-plugin 重跑全部 JS tests。（来源: `K-js-tests-run-via-exec-plugin`）

- [ ] **Step 3: Static scope and style guards**

```bash
rg -n "hardBounceRateHigh|硬退率过高" src/main/resources/static/app.js src/test/js/senderBindingDisplay.test.js
git diff -- src/main/resources/static/styles.css
git diff --check
```

Expected:
- 生产判断仅 `account.hardBounceRateHigh === true` 一处。
- `styles.css` diff 为空。
- `git diff --check` 无输出。

## 变更文件清单

| # | 文件 | 改动 | 子系统 |
|---|---|---|---|
| 1 | `src/main/resources/static/app.js` | 状态列新增软告警徽标 | 账号管理前端 |
| 2 | `src/main/resources/static/index.html` | 三联缓存键 bump | 账号管理前端 |
| 3 | `src/test/js/senderBindingDisplay.test.js` | 告警与暂停正交测试 | 测试 |
| 4 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 缓存键固定值同步 | 测试 |
| 5 | `src/test/js/checkRepliesRelocation.test.js` | 缓存键固定值同步 | 测试 |
| 6 | `src/test/js/manualReplySubjectPrefill.test.js` | 缓存键固定值同步 | 测试 |
| 7 | `src/test/js/overlayAndDialogContrast.test.js` | 缓存键固定值同步 | 测试 |
| 8 | `src/test/js/ragKnowledgeBasePage.test.js` | 缓存键固定值同步 | 测试 |
| 9 | `src/test/js/ragWorkbenchRender.test.js` | 缓存键固定值同步 | 测试 |
| 10 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 缓存键固定值同步 | 测试 |

合计 10 文件；生产子系统 1 个；无新增 CSS、DOM id、API 请求或持久化字段。

## 验收标准

- I-1：`app.js` 只出现一次生产判断 `account.hardBounceRateHigh === true`；无 `0.05`、`20`、硬退计数计算。
- I-2：JS 测试覆盖 warning-only 与 paused+warning；前者无 resume action，后者有；`if (autoPaused)` diff 为零。
- I-3：`index.html` 账号表头和 `accountsTable` tbody diff 为零；渲染仍是 7 个 `<td>`。
- I-4：旧键全仓目标范围零命中；新键在 index.html 恰好三处且同值；7 个固定值测试全部通过。
- S-1：生产 HTML 与契约 span 逐字一致；`styles.css` 零 diff；浏览器 computed style 对应契约实值。
- S-2：状态顺序为主状态→自动暂停→硬退率过高；actions 仍只由 autoPaused 决定。
- Regression：全部 Node tests、JS syntax、JDK 11 `mvn test package` 通过。

## 人工验收清单

### A-1: 仅硬退率告警账号

- 前置条件: 03A 已部署；账号 `WARN_ONLY` API 数据为 `enabled=true`、`autoSendPaused=false`、`hardBounceRateHigh=true`。
- 操作步骤: 1. 强制刷新页面。2. 打开“发件账号管理”。3. 找到 `WARN_ONLY` 行。4. 将鼠标悬停在告警徽标。
- 预期结果: 状态列依次显示绿色“启用”和黄色“硬退率过高”；tooltip 逐字为“近7天硬退率超过5%（已发至少20封）；仅提示，不影响自动发送”；操作列没有“恢复发送”。
- 覆盖: Observable 1、2；I-1、I-2；S-1、S-2；API→DOM interaction。

### A-2: 无告警账号回归

- 前置条件: 账号 `NORMAL` API 数据为 `enabled=true`、`autoSendPaused=false`、`hardBounceRateHigh=false`。
- 操作步骤: 打开“发件账号管理”，找到 `NORMAL`。
- 预期结果: 状态列只有绿色“启用”；页面中该行没有“硬退率过高”和“恢复发送”。
- 覆盖: What must NOT change 4；I-1。

### A-3: 真实暂停与告警同时存在

- 前置条件: 账号 `FAULT` API 数据为 `enabled=true`、`autoSendPaused=true`、`autoSendPausedReason=SELF_CHECK_FAILED:timeout`、`hardBounceRateHigh=true`。
- 操作步骤: 打开账号页并找到 `FAULT`；悬停“自动暂停”。
- 预期结果: 状态列依次显示“启用”“自动暂停”“硬退率过高”；自动暂停 tooltip 为 `SELF_CHECK_FAILED:timeout`；操作列有且仅有一个“恢复发送”。
- 覆盖: Observable 3；What must NOT change 2、3；I-2；S-2。

### A-4: 表结构与原操作回归

- 前置条件: 至少存在一个启用账号、一个禁用账号、一个预热账号。
- 操作步骤: 打开账号页，逐列核对表头；分别查看三行；点击“查看”后关闭弹窗。
- 预期结果: 表头仍为 7 列且顺序不变；今日发信上限仍显示 `todaySentCount/effectiveDailyLimit`；预热账号仍有“预热中”；绑定专家数与所有原按钮仍在；查看弹窗状态显示逻辑不变。
- 覆盖: What must NOT change 1、5；I-3。

### A-5: 告警徽标视觉值

- 前置条件: 同 A-1；浏览器开发者工具可查看 computed style。
- 操作步骤: 选中“硬退率过高” span，查看 computed style。
- 预期结果: font-size `11px`、font-weight `600`、padding `2px 8px`、border-radius `999px`、文字色 `rgb(217, 119, 6)`、背景 `rgba(217, 119, 6, 0.08)`、边框色 `rgba(217, 119, 6, 0.2)`；元素无 inline style。
- 覆盖: S-1。

### A-6: 新静态资源版本生效

- 前置条件: 03B 已部署。
- 操作步骤: 打开浏览器 Network，刷新页面，过滤 `styles.css`、`trust-reply-workbench.js`、`app.js`。
- 预期结果: 三个请求 URL 均带 `?v=20260903-bounce-warning`；账号列表能显示 A-1 告警。
- 覆盖: Observable 4；I-4；cache URL→browser asset interaction。

> 人工验收开始时，才从本节导出 `03b-bounce-rate-soft-warning-frontend-acceptance.md`；现在不要生成。
