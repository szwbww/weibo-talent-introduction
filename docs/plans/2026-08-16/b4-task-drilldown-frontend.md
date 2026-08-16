# B4：任务明细跳转（读取路径 + UI）

主计划：`task-records-refactor-main.md`　全链顺序：`00-execution-order.md`
编号：**B4**（全链第 7 份）
前置计划：**B3 必须已合并**（`mail_record.task_execution_id` 列存在且已有数据写入）
子系统数：2（mail 后端 / 前端静态后台）　文件数：10
迁移版本：无　缓存键取值：`20260817-v6-task-drilldown`

> ⚠️ **与 A3 的区域重叠**：A3 把 `#bulkOutreachBtn` 从专家列表迁到**收发件箱面板标题栏**；本计划要在收发件箱插入过滤提示条。执行前先看 A3 落地后的收发件箱 DOM 实况，提示条插在标题栏**之下**、既有 `.toolbar` 之后，不要与迁入的按钮抢同一行。

---

## 需求描述

### Observable outcome

1. 任务记录页每一行提供一个跳转入口：
   - `MANUAL_INITIAL_OUTREACH` / `INITIAL_OUTREACH` → 「查看本次发出的邮件」，跳到「收发件箱」并按该次执行过滤。
   - `AUTO_REPLY_ALL` / `CHECK_REPLIES` → 展开的明细里每位专家可点击，跳到该专家详情。
   - 其余全部类型 → 入口为**禁用态** + 文案「该任务无个体明细」。
2. 收发件箱在按执行过滤时，顶部显示一条可清除的过滤提示条。

### What must NOT change

- **N2b-1** 「收发件箱」在**无** `taskExecutionId` 参数时的查询结果、排序、分页、标签渲染与改动前逐字相同。
- **N2b-2** `setMailboxPendingOnly` 的既有语义与其两个调用点（`app.js:10831` 的 `view-unmatched`/`open-pending`、`:11522` 的 `goto-manual-queue`）不变。
- **N2b-3** `openContactInList(contactId)`（`app.js:7260`）一行不改，本计划复用它。
- **N2b-4** P1 建立的 `/{id}/detail` 响应形状只做**新增字段**，既有字段不改。
- **N2b-5** 不新增侧栏视图（`viewMeta` / `.nav-tab` / `.view` section / `refreshCurrentView` 四处注册一处不动）。（来源: K-view-registration-triad）
- **N2b-6** `/recent-polls/{id}/detail` 一行不改（轮询日志弹窗仍用它）。

### Out of scope

- 不给 ES 类任务做时间窗近似跳转（需求方已定，见 M-4）。
- 不做历史数据回填，`task_execution_id IS NULL` 的历史执行显示明确文案。
- 不给收发件箱新增「按批次」的常驻筛选控件——过滤只由跳转带入，提示条可清除。
- 不做跳转后的面包屑返回。

---

## 关键不变量

### Invariant I2b-1: 无 drilldown 声明必须置灰（M-4 落地）

- Rule：`TaskTypeCatalog` 中 `drilldown = null` 的类型，渲染出的入口必须带 `disabled` 属性、不带 `data-action`、不带 `href`，并显示文案 `该任务无个体明细`。
- Applies to：`app.js` 的行渲染与明细渲染。
- Violation consequence：见主计划 M-4（ES 类任务的 `enrichedAt` 是 keyword 不是 date，任何近似查询都不可信）。
- 来源：M-4

### Invariant I2b-2: 三种「无法跳转」的原因必须区分文案

- Rule：入口不可用时，文案必须区分三种情形：
  1. `drilldown = null` → `该任务无个体明细`
  2. `drilldown = MAIL_BY_EXECUTION` 但查询结果为 0 且执行早于列上线 → `该执行早于本功能上线，无法关联`
  3. `drilldown = MAIL_BY_EXECUTION` 但该执行走了队列派发 → `该执行经队列派发，邮件未直接关联`
- Applies to：`/{id}/detail` 新增的 `drilldownState` 字段、`app.js` 的渲染。
- Violation consequence：三种情形都渲染成「无明细」时，运营无法区分「这个任务本来就没有明细」和「这次发送的关联数据丢了」，会把正常行为当故障上报。
- 来源：original（P2a 的 T2a-5 与 Out of scope 已明确前两种边界确实存在）

### Invariant I2b-3: 邮件过滤查询与既有查询共用同一投影与排序

- Rule：按 `taskExecutionId` 过滤的收发件箱查询，必须复用 `MailboxService` 既有的行装配逻辑（同一 DTO、同一标签计算、同一排序），只在 WHERE 上多一个条件。**禁止**新写一条独立的装配路径。
- Applies to：`MailRecordRepository`、`MailboxService`。
- Violation consequence：两条装配路径会出现字段/标签不一致——同一封邮件在「收发件箱」和「按批次查看」里显示不同标签。（同源问题参见 K-contact-list-dual-query-path / K-contact-list-dual-path-field-parity）
- 来源：K-contact-list-dual-path-field-parity

### Invariant I2b-4: 悬垂 executionId 不得报错

- Rule：`task_execution_id` 指向一条已被 P3 保留策略删除的执行时，收发件箱查询仍正常返回邮件（不 join `task_execution`），提示条显示 `执行 #{id}（记录已过保留期）`。
- Applies to：`MailboxService` 的过滤查询、提示条渲染。
- Violation consequence：P2a 刻意不加 FK 就是为了让 P3 能删；若读取侧 join 了 `task_execution` 并要求命中，删除后跳转即 500。
- 来源：主计划 IP-5 / I2a-4

### Invariant I2b-5: 跳转不新增视图（N2b-5 落地）

- Rule：邮件跳转复用 `setView("mailbox")`，专家跳转复用 `openContactInList(contactId)`。不新增 `viewMeta` 条目、不新增 `.nav-tab`、不新增 `.view` section。
- Applies to：`app.js`、`index.html`。
- Violation consequence：新增视图须四处同步注册，缺一即切换报错。
- 来源：K-view-registration-triad

### Invariant I2b-6: 缓存键三连必须与测试断言同步 bump

- Rule：同 B1 的 I0-6。本计划取值 `20260817-v6-task-drilldown`。
- Applies to：`index.html`、`batchSendTaskConsoleVisualFix.test.js`。
- Violation consequence：只 bump 不改断言 → 构建中止；只改代码不 bump → 浏览器加载旧 `app.js`，跳转入口看着没出现。
- 来源：K-frontend-cache-key-triad（成文时本计划**漏载**该条，2026-08-16 复盘补入）

---

## 样式契约

### S2b-1: 任务行的跳转入口（可用态）

- **复用**：`.link-btn`（`styles.css:2517-2530`：`border:none`、`background:none`、`padding:0`、`font-size:11px`、`font-weight:600`、`color:var(--primary)`；hover 见 `:2532-2534` 加下划线）。**不新增 CSS**。
- **新增**：无。
- **DOM 结构**（置于展开明细区顶部，逐字）：

```html
<button type="button" class="link-btn" data-action="task-drilldown-mail" data-execution-id="13023">查看本次发出的邮件（10 封）</button>
```

  专家类：

```html
<button type="button" class="link-btn" data-action="task-drilldown-contact" data-contact-id="4471">王某某 &lt;a@b.edu&gt;</button>
```

- **⚠️ 波及提示**：`.link-btn` 含 `margin-left: auto`（为其原用场景的 flex 容器设计）。本计划的入口容器**必须**是块级非 flex 容器，否则会被推到右侧。若目视发现位置异常，**不得修改 `.link-btn` 规则块**（须先 grep 其全部使用点），而应给外层容器加既有的布局 class 或用 `.text-muted` 包裹文案后另起一行。
- **禁止项**：inline style；新增 class；修改 `.link-btn` 规则块。

### S2b-2: 跳转入口（禁用态，I2b-1 / I2b-2）

- **复用**：`.text-muted`（`styles.css:2323-2326`）。**不新增 CSS**。
- **新增**：无。
- **DOM 结构**（逐字，三种文案之一）：

```html
<span class="text-muted">该任务无个体明细</span>
```

```html
<span class="text-muted">该执行早于本功能上线，无法关联</span>
```

```html
<span class="text-muted">该执行经队列派发，邮件未直接关联</span>
```

- **禁止项**：渲染成 `<button disabled>`（本仓库无禁用态 link-btn 的既有样式，用 `<span class="text-muted">` 是零新增 CSS 的正解）；渲染成可点击元素；三种文案合并成一种。

### S2b-3: 收发件箱的过滤提示条

- **复用**：`.toolbar`（`styles.css:351`）、`.text-muted`（`:2323`）、`.button.small`（`:2316`）。**不新增 CSS**。
- **新增**：无。
- **DOM 结构**（`index.html` 收发件箱视图内、既有 `.toolbar` 之后插入，逐字）：

```html
<div id="mailboxExecutionFilterBar" class="toolbar" hidden>
    <span class="text-muted" id="mailboxExecutionFilterText"></span>
    <button type="button" class="button small" id="mailboxExecutionFilterClear">清除过滤</button>
</div>
```

  `#mailboxExecutionFilterText` 的文案（逐字，两种之一）：
  - `正在查看：批量首发邮件 执行 #13023 发出的邮件`
  - `正在查看：执行 #13023（记录已过保留期）发出的邮件`

- **禁止项**：inline style；新增 class；修改 `.toolbar` 规则块（它有 5+ 处使用点）。

### S2b-4: 缓存键（I2b-6）

- **复用 / 新增**：不适用（本计划不新增也不修改任何 CSS 规则块）。
- **DOM 结构**：`index.html` 三处逐字改为同一新值：

```html
<link rel="stylesheet" href="styles.css?v=20260817-v6-task-drilldown">
<script src="trust-reply-workbench.js?v=20260817-v6-task-drilldown"></script>
<script src="app.js?v=20260817-v6-task-drilldown"></script>
```

  同步把 `batchSendTaskConsoleVisualFix.test.js` 的 "bumps the stylesheet cache key" 用例三条断言改成同一值。
- **禁止项**：只改其中一两处；改了代码却不 bump；bump 了却不改测试断言。

---

## 现状审计

### `mail_record` 读取路径与装配（I2b-3 依据）

`MailboxService.kt` 的 `toDetailFromMailRecord(record)`（`:241`）是 `mail_record → MailboxDetailResponse` 的唯一装配点，`:210` 是其调用处。**本计划必须复用它**，不新写装配。

⚠️ 执行前须完整读 `MailboxService` 的列表查询方法，确认：① 列表查询与详情查询是否共用装配；② 标签（`MAILBOX_TAG_BADGE_CLASS` 对应的 tags）在哪一层计算。**本审计段落在执行时须补全为逐字基线**——本计划成文时未展开该文件全文，这是已知的研究缺口，执行 agent 必须先补齐再动手。

### 前端跨视图跳转的既有范式（I2b-5 依据，逐字基线）

`app.js:10830-10836`：

```javascript
        if (target.dataset.action === "view-unmatched" || target.dataset.action === "open-pending") {
            setView("mailbox");
            setMailboxPendingOnly(true);
            state.mailbox.page = 0;
            await loadMailbox();
            await showUnmatchedDetail(target.dataset.id).catch((error) => showStatus(error.message, "error"));
        }
```

`app.js:11520-11527`：

```javascript
        if (action === "goto-manual-queue") {
            event.preventDefault();
            setView("mailbox");
            setMailboxPendingOnly(true);
            state.mailbox.page = 0;
            loadMailbox().catch((e) => showStatus(e.message, "error"));
            return;
        }
```

`app.js:7260`（专家跳转，本计划直接复用，N2b-3）：

```javascript
async function openContactInList(contactId) {
    setView("contacts");
    if (!state.contacts || state.contacts.length === 0) {
        await loadContacts();
    }
    const contact = await loadContactDetail(contactId);
    ...
}
```

**范式**：`setView(...)` → 设置过滤状态 → `state.<view>.page = 0` → `load<View>()`。本计划的邮件跳转逐字沿用，把 `setMailboxPendingOnly(true)` 换成 `state.mailbox.taskExecutionId = <id>`。

### 专家明细的现有数据源（`AUTO_REPLY_ALL` 的 drilldown 依据）

`/recent-polls/{id}/detail` 返回的 `PollAccountDetail.repliedExperts[]` 已含 `expertContactId` / `expertEmail` / `expertName` / `outcome`（`TaskExecutionController.kt` 的 `PollRepliedExpert`）。

⚠️ **但 P1 已让任务记录页改调 `/{id}/detail`，不再调 `/recent-polls/{id}/detail`（N2b-6 保留端点本身）。** 因此 P2b 需在 P1 的 `/{id}/detail` 中，对 `drilldown = EXPERT_BY_POLL_DETAIL` 的类型附带同样的专家数组。实现上**复用** `TaskExecutionController` 内既有的 `PollDetailRaw` / `PollDetailAccountRaw` / `PollDetailExpertRaw` 三个私有解析类，不新写解析。

### 前端样式盘点

见主计划 X-7；本计划额外确认 `.link-btn`（`styles.css:2517`）的 `margin-left: auto` 副作用（见 S2b-1 的波及提示）。

`.link-btn` 全部使用点（执行前须重新 grep 确认，本计划成文时未枚举——**这是已知缺口，执行 agent 必须补 grep 回执后再决定是否需要外层容器调整**）。

### 交互点

| # | 写 | 读 | 处理 |
|---|---|---|---|
| IP2b-1 | P2a 的 `task_execution_id` | 本计划的 `WHERE task_execution_id = ?` | I2b-3 复用装配 |
| IP2b-2 | P3 删 `task_execution` 行 | 悬垂 id 的提示条 | I2b-4 |
| IP2b-3 | P1 的 `catalog.drilldown` | 本计划的入口渲染 | I2b-1 |
| IP2b-4 | 队列派发路径（P2a T2a-5） | 结果为 0 的兜底文案 | I2b-2 情形 3 |

---

## 实现方案

### T2b-1 catalog 补 drilldown 声明（I2b-1）

改 `TaskTypeCatalog.kt`，把 P1 中全部声明为 null 的 `drilldown` 改为：

- `MANUAL_INITIAL_OUTREACH` / `INITIAL_OUTREACH` → `Drilldown.MAIL_BY_EXECUTION`
- `AUTO_REPLY_ALL` / `CHECK_REPLIES` → `Drilldown.EXPERT_BY_POLL_DETAIL`
- **其余 12 项保持 null**（含全部 ES 类任务，M-4）

### T2b-2 Repository + Service 过滤查询（I2b-3 / I2b-4）

- `MailRecordRepository` 加 `findAllByTaskExecutionIdOrderByIdAsc(taskExecutionId: Long): List<MailRecord>`（派生查询，无需 `@Query`，无 `IN` 因而不涉及 `K-empty-list-in-query-guard`）。
- `MailboxService` 加 `listByTaskExecution(taskExecutionId: Long): List<MailboxRow>`，**内部复用既有装配**（I2b-3）。
- **不 join `task_execution`**（I2b-4）。

### T2b-3 Controller 暴露过滤（I2b-3）

收发件箱既有列表端点加可选 `@RequestParam(required = false) taskExecutionId: Long?`；为 null 时走原路径**一行不改**（N2b-1）。

### T2b-4 `/{id}/detail` 补 drilldown 字段（I2b-2 / N2b-4）

P1 的 `TaskExecutionDetailResponse` **新增**：

- `drilldown: String?`（`MAIL_BY_EXECUTION` / `EXPERT_BY_POLL_DETAIL` / null）
- `drilldownState: String`（`AVAILABLE` / `NONE` / `PRE_FEATURE` / `QUEUE_DISPATCHED`）
- `drilldownCount: Int`（邮件类：`COUNT(*) FROM mail_record WHERE task_execution_id = ?`；专家类：明细中专家总数）
- `experts: List<PollRepliedExpert>?`（仅 `EXPERT_BY_POLL_DETAIL`，复用既有解析类）

`drilldownState` 判定：

```
catalog.drilldown == null                                  → NONE
MAIL_BY_EXECUTION && count > 0                             → AVAILABLE
MAIL_BY_EXECUTION && count == 0 && requestPayload 含队列标记 → QUEUE_DISPATCHED
MAIL_BY_EXECUTION && count == 0                            → PRE_FEATURE
EXPERT_BY_POLL_DETAIL && experts 非空                       → AVAILABLE
EXPERT_BY_POLL_DETAIL && experts 为空                       → NONE
```

⚠️ 「队列标记」的判定依据是 `TaskDispatchRequest.dispatchMode == "QUEUE"`（`MailAutomationScheduler.dispatchMode()` 写入 `request_payload`）。执行前须确认 `request_payload` 的 JSON 中该字段名的确切拼写，**不得凭印象**。

### T2b-5 前端（I2b-1 / I2b-2 / I2b-5 / S2b-1 ~ S2b-3）

改 `app.js`：

1. 明细渲染（P1 的 `toggleTaskDetail` 重写产物）顶部按 `drilldownState` 渲染 S2b-1 或 S2b-2。
2. 新增全局 `data-action` 处理（挂在既有的 `document.addEventListener("click", ...)` 上，与 `goto-manual-queue` 同处）：

```javascript
if (action === "task-drilldown-mail") {
    event.preventDefault();
    state.mailbox.taskExecutionId = Number(element.dataset.executionId);
    setView("mailbox");
    state.mailbox.page = 0;
    loadMailbox().catch((e) => showStatus(e.message, "error"));
    return;
}
if (action === "task-drilldown-contact") {
    event.preventDefault();
    openContactInList(Number(element.dataset.contactId)).catch((e) => showStatus(e.message, "error"));
    return;
}
```

3. `loadMailbox()` 在有 `state.mailbox.taskExecutionId` 时把它加进 query，并按 S2b-3 显示提示条；`#mailboxExecutionFilterClear` 点击后置 null、隐藏提示条、`page = 0` 并重载。
4. **`setMailboxPendingOnly` 的两个既有调用点必须显式清空 `state.mailbox.taskExecutionId`**（否则从「待处理」入口进来会残留上一次的批次过滤）——这是 N2b-2 的落地要求。

改 `index.html`：按 S2b-3 插入提示条。

### T2b-6 测试

新建 `src/test/kotlin/.../mail/service/MailboxTaskExecutionFilterTest.kt`：
- `taskExecutionId = null` 时走原查询（Mockito verify 原方法被调，新方法未被调）（N2b-1）。
- 指定 id 时返回该批次邮件，且 DTO 字段与原装配一致（I2b-3：对同一 `MailRecord` 走两条路径，断言产出的 DTO `equals`）。
- 悬垂 id（`task_execution` 无对应行）时正常返回邮件，不抛异常（I2b-4）。

新建 `src/test/js/taskDrilldown.test.js`：
- `drilldownState = NONE` 时输出 `<span class="text-muted">该任务无个体明细</span>`，且**不含** `data-action` / `href` / `<button`（I2b-1，逐字断言 S2b-2）。
- 三种禁用文案各一条用例（I2b-2）。
- `AVAILABLE` 时输出 S2b-1 的 `<button class="link-btn" data-action="task-drilldown-mail" ...>` 逐字骨架。
- 点击邮件入口后 `state.mailbox.taskExecutionId` 被设置且 `state.mailbox.page === 0`。
- `setMailboxPendingOnly(true)` 后 `state.mailbox.taskExecutionId` 被清空（N2b-2）。
- 提示条文案两种形式逐字。

---

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/.../task/domain/TaskTypeCatalog.kt` | 修改 | 4 项 drilldown 声明 |
| 2 | `src/main/kotlin/.../task/controller/TaskExecutionController.kt` | 修改 | `/{id}/detail` 加 4 字段 |
| 3 | `src/main/kotlin/.../mail/repository/MailRecordRepository.kt` | 修改 | 加 1 个派生查询 |
| 4 | `src/main/kotlin/.../mail/service/MailboxService.kt` | 修改 | 加 `listByTaskExecution`，复用装配 |
| 5 | `src/main/kotlin/.../mail/controller/MailboxController.kt`（实际文件名执行时确认） | 修改 | 列表端点加可选参数 |
| 6 | `src/main/resources/static/app.js` | 修改 | 入口渲染 + 2 个 action + `loadMailbox` 过滤 + 提示条 |
| 7 | `src/main/resources/static/index.html` | 修改 | S2b-3 提示条 |
| 8 | `src/test/kotlin/.../mail/service/MailboxTaskExecutionFilterTest.kt` | 新增 | — |
| 9 | `src/test/js/taskDrilldown.test.js` | 新增 | — |
| 10 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | **仅**改三条缓存键 literal 断言（I2b-6）；其余用例一行不动 |

文件数 10 ≤ 10（已到上限；第 5 项定位后若发现须改 2 个以上后端文件，**停止并回报**）。子系统 2（mail 后端 / 前端）。

⚠️ 第 5 项的确切文件名与端点方法在本计划成文时未核实（收发件箱的 controller 归属未展开）。**执行第一步必须先 grep 定位并把结果补进本节**，若发现需要改的是 2 个以上文件，**停止并回报**。

---

## 验证命令

见主计划「验证命令」节。本计划相关：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailboxTaskExecutionFilterTest
node --test src/test/js/taskDrilldown.test.js

# 缓存键回归（改 index.html 后必跑）
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js

# app.js 语法检查
node --check src/main/resources/static/app.js
```

> ⚠️ `verify.sh` 只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件，不可作为本计划门禁。（来源: K-js-test-invocation-surface）

---

## 验收标准

- **I2b-1**：JS 用例断言 `NONE` 态输出不含 `data-action`、不含 `href`、不含 `<button`。
- **I2b-2**：三条 JS 用例分别断言三种文案逐字。
- **I2b-3**：Kotlin 用例对同一 `MailRecord` 走两条路径，断言产出 DTO `equals`。
- **I2b-4**：悬垂 id 用例不抛异常且返回非空列表。
- **I2b-5**：`git diff src/main/resources/static/index.html` 中**无** `.nav-tab` / `class="view"` 的新增；`grep -c "viewMeta" src/main/resources/static/app.js` 的 diff 为 0 新增条目。
- **S2b-1 / S2b-2 / S2b-3**：JS 用例逐字断言三段 DOM；本计划的 commit 中**不含对 `styles.css` 的任何规则块增删改**（按本计划自身 commit 范围核对；A1 在同一分支改过 CSS，整文件 diff 不为空是预期）。
- **S2b-4 / I2b-6**：`grep -c "20260817-v6-task-drilldown" src/main/resources/static/index.html` 为 **3**；同值在 `batchSendTaskConsoleVisualFix.test.js` 中为 **3**；该测试文件通过。
- **N2b-1**：Kotlin 用例断言 `taskExecutionId = null` 时新查询方法未被调用。
- 回归：执行主计划「验证命令」节的全量测试命令通过。

---

## 人工验收清单

### A2b-1: 邮件跳转（Observable outcome 1）

- 前置条件：P2a 上线后完成过一次批量发送（发出 3 封）。
- 操作步骤：
  1. 任务记录页找到该 `批量首发邮件` 行，展开。
  2. 点「查看本次发出的邮件（3 封）」。
  3. 数收发件箱的行数。
  4. 点提示条上的「清除过滤」。
- 预期结果：
  1. 展开区顶部有蓝色文字链接，括号内数字为 3。
  2. 跳到「收发件箱」，顶部出现提示条 `正在查看：批量首发邮件 执行 #13023 发出的邮件`。
  3. 恰好 3 行，均为该批次发出的介绍邮件。
  4. 提示条消失，列表恢复为全部邮件。
- 覆盖：Observable outcome 1 / 2

### A2b-2: 专家跳转（Observable outcome 1）

- 前置条件：存在一次有专家回复的 `AUTO_REPLY_ALL` 执行。
- 操作步骤：展开该行 → 点其中一位专家的姓名/邮箱。
- 预期结果：跳到「专家联系」页并自动打开该专家详情，右侧详情面板展示该专家信息。
- 覆盖：Observable outcome 1 / N2b-3

### A2b-3: ES 类任务置灰（I2b-1 / M-4）

- 前置条件：存在已完成的 `学术数据补全` 执行。
- 操作步骤：展开该行 → 观察入口区域 → 打开 DevTools Network 面板 → 尝试点击该文案。
- 预期结果：入口为灰色小字 `该任务无个体明细`，鼠标悬停无手型指针、无下划线；点击**不发起任何网络请求**。
- 覆盖：I2b-1 / M-4

### A2b-4: 三种禁用原因可区分（I2b-2）

- 前置条件：分别构造三行——① 一条 `学术数据补全` 执行；② 一条 P2a 上线**之前**的历史 `批量首发邮件` 执行；③ 一条队列模式下的 `定时首发邮件` 执行（可临时开 RabbitMQ 触发一次）。
- 操作步骤：依次展开三行。
- 预期结果：三行的文案分别为 `该任务无个体明细`、`该执行早于本功能上线，无法关联`、`该执行经队列派发，邮件未直接关联` —— **三条各不相同**。
- 覆盖：I2b-2

### A2b-5: 过期执行的悬垂关联（I2b-4）

- 前置条件：P3 已上线；手工把某条已关联邮件的 `task_execution` 行的 `started_at` / `created_at` 改为 91 天前并触发一次保留清理（该行被删，`mail_record.task_execution_id` 成悬垂值）。
- 操作步骤：直接访问收发件箱并带上该 executionId 参数（或从浏览器地址栏构造），观察结果。
- 预期结果：邮件正常列出，**不报错**；提示条显示 `正在查看：执行 #13023（记录已过保留期）发出的邮件`。
- 覆盖：I2b-4 / 主计划 IP-5

### A2b-6: 回归 —— 收发件箱无过滤时不变（N2b-1）

- 前置条件：截图保存改动前的收发件箱首页。
- 操作步骤：从侧栏正常进入「收发件箱」（不经跳转），逐项比对行数、排序、每行标签徽章。
- 预期结果：与改动前逐字相同；提示条**不显示**。
- 覆盖：N2b-1

### A2b-7: 回归 —— 待处理入口清空批次过滤（N2b-2）

- 前置条件：先经任务跳转进入收发件箱（提示条可见）。
- 操作步骤：不点「清除过滤」，直接点「邮件监控」页的「查看待处理」入口（`goto-manual-queue` / `open-pending`）。
- 预期结果：进入收发件箱的待处理视图；**批次过滤提示条消失**，列表不残留上一次的批次限制。
- 覆盖：N2b-2

### A2b-9: 缓存键（I2b-6）

- 前置条件：改动已构建部署。
- 操作步骤：查看网页源代码里三条 `?v=` 的值；不清缓存正常刷新后进「任务记录」展开一行。
- 预期结果：三条值均为 `20260817-v6-task-drilldown` 且完全相同；不清缓存也能看到跳转入口。
- 覆盖：I2b-6 / S2b-4

### A2b-10: 与 A3 的收发件箱布局共存

- 前置条件：A3 已上线（`#bulkOutreachBtn` 已迁至收发件箱面板标题栏）。
- 操作步骤：经任务跳转进入收发件箱，观察「批量发送」按钮与本计划新增的过滤提示条。
- 预期结果：两者各占一行、互不遮挡；「批量发送」按钮仍可点击并正常打开批量任务弹窗。
- 覆盖：与 A3 的区域重叠

### A2b-8: 回归 —— 侧栏无新增（N2b-5）

- 前置条件：任意。
- 操作步骤：数侧栏 Tab 数量并逐个点击切换。
- 预期结果：Tab 数量与改动前一致；每个 Tab 切换均正常加载，控制台无 `viewMeta[view] is undefined` 类报错。
- 覆盖：N2b-5 / K-view-registration-triad

---

## 知识回写（Phase 6）

- **新增** `docs/knowledge/frontend/K-cross-view-drilldown-pattern.md`：跨视图跳转的既有范式（`setView` → 设过滤状态 → `page = 0` → `load<View>()`），三个既有实例（`app.js:7260` / `:10831` / `:11522`）；新增跳转一律复用现有视图，不新增 view（指向 K-view-registration-triad）；**新增过滤状态时必须同步在其他入口清空它**，否则跨入口残留。
- **新增** `docs/knowledge/frontend/K-link-btn-margin-auto-side-effect.md`：`.link-btn`（`styles.css:2517`）带 `margin-left: auto`，在非 flex 容器中复用需注意；该 class 有多处使用点，禁止就地改规则块。
- **新增** `docs/knowledge/task/K-drilldown-state-three-negatives.md`：「无法跳转」有三种语义不同的原因（类型本无明细 / 早于功能上线 / 队列派发），合并成一种文案会让正常行为被当故障上报。
- **命中续期**：`K-view-registration-triad`、`K-contact-list-dual-path-field-parity`、`K-empty-list-in-query-guard`。
