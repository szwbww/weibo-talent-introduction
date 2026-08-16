# B1：任务记录列表性能（投影 + 分页 + 索引）

主计划：`task-records-refactor-main.md`　全链顺序：`00-execution-order.md`
编号：**B1**（全链第 4 份）
前置计划：**A1、A2、A3 必须已合并**（三者都 bump `index.html` 缓存键并改
`batchSendTaskConsoleVisualFix.test.js` 的断言字符串；本计划取链上下一个值 v4）
子系统数：2（task 后端 / 前端静态后台）　文件数：9
迁移版本：**V100**　缓存键取值：`20260817-v4-task-records-paging`

---

## 需求描述

### Observable outcome

「任务记录」页从「一次拉全表 + 全量 TEXT」改为分页拉取（默认 50 条/页），首屏 < 2 秒。表格展示的字段与改动前**完全一致**，只是不再一次性加载全部历史。

### What must NOT change

- **N0-1** 表格的 7 列（审计 ID / 任务类型 / 触发方式 / 当前状态 / 发信统计-成功数 / 开始时间 / 异常堆栈-错误原因）的**取值与渲染**与改动前逐字相同。中文名、语义标签是 **P1** 的事，P0 一个字不改。
- **N0-2** `GET /api/task-executions/recent-polls`、`/recent-polls/{id}/detail`、`/{id}` 三个端点一行不改。
- **N0-3** `TaskExecutionService` 的 `runAndRecord` / `runAndRecordWithResult` / `getExecution` / `listRecentPolls` / `listRecentByBatchConfigId` / `lastExecutedAtByBatchConfigIds` / `sumSuccessCountTodayByBatchConfigId` / `updateProgressCounts` / `countScheduledSince` / `nextPollTime` 全部不改。本计划只改 `listExecutions`。
- **N0-4** `TaskExecutionRepository` 既有的 8 个查询方法签名与实现不改，只新增。
- **N0-5** `toggleTaskDetail()` 的行为不改（P1 才改）。
- **N0-6** 排序仍为 `started_at DESC`。

### Out of scope

- 不加中文名 / 语义标签 / catalog（P1）。
- 不加时间窗筛选参数。分页已把返回量约束住，再加默认时间窗会改变「能看到多久以前的记录」这一既有语义，且让 `COUNT` 查询复杂化。
- 不补 `PARTIAL_SUCCESS` / `CANCELLED` 状态选项（P1，与 catalog 一起做）。
- 不改 `toggleTaskDetail` 的空态。
- 不做前端虚拟滚动。分页已足够，虚拟滚动会引入与 `.list-pager` 范式冲突的第二套交互。

---

## 关键不变量

### Invariant I0-1: 列表查询禁止 SELECT 大 TEXT 列（M-1 落地）

- Rule：新增的分页查询必须显式列出 SELECT 列，且**不得包含** `request_payload`、`result_summary`。投影 DTO `TaskExecutionListItem` 也不得声明这两个属性。
- Applies to：`TaskExecutionRepository.findPage*`（4 个组合）、`TaskExecutionService.listExecutions`、`TaskExecutionController.listExecutions`。
- Violation consequence：分页优化被 TEXT 传输抵消，50 行仍可达数 MB。
- 来源：M-1（original，本轮 Phase 1b 实测）

### Invariant I0-2: 四种筛选组合必须走各自的索引前缀

- Rule：四种组合（无筛选 / 仅 taskType / 仅 status / 两者）各有独立查询，`ORDER BY started_at DESC LIMIT :size OFFSET :offset`；V100 必须同时提供 `(started_at)`、`(task_type, started_at)`、`(status, started_at)` 三个索引，使每种组合都有可用的索引前缀。
- Applies to：`V100__add_task_execution_indexes.sql`、`TaskExecutionRepository` 的 4 个分页查询 + 4 个 count 查询。
- Violation consequence：只建 `(started_at)` 单列索引时，`WHERE task_type = ? ORDER BY started_at DESC` 仍需回表过滤大量行；只建 `(task_type, started_at)` 时无筛选场景又无索引可用。
- 来源：original（X-2 实测：当前索引只有主键与 `(batch_config_id, started_at)`）

### Invariant I0-3: `total` 与 `items` 必须来自同一筛选条件

- Rule：`total` 由与 `items` **完全相同**的 WHERE 条件 `COUNT(*)` 得出，不得用全表 `count()` 充当。
- Applies to：`TaskExecutionService.listExecutions`。
- Violation consequence：筛选后页码总数错误，用户翻到空页。
- 来源：original

### Invariant I0-4: `:5276` 调用点同时受形状变更与 M-1 双重影响，必须改成两段式请求

- Rule：`GET /api/task-executions` 的返回从 `List<TaskExecutionResponse>` 改为 `TaskExecutionPageResponse{items, total}`。`app.js:5276` 的调用点**不仅**要改读 `.items`，**还必须**改成两段式：`?taskType=...&size=1` 取到最新一行的 `id`，再 `GET /api/task-executions/{id}` 拿 `resultSummary`。
- Applies to：`app.js` 的 2 个调用点（`:5276`、`:8913`）。
- Violation consequence：**该调用点读 `task.resultSummary`**（实测见「现状审计」的逐字基线），而 I0-1/M-1 明令列表不得返回该字段。只改 `.items` 不改取数方式的话，代码不报错、`skipped` 恒为 0 —— 是**静默错值**，比崩溃更难发现。
- 来源：original（2026-08-16 补读 `app.js:5274-5295` 实测；成文时此处曾被标为「执行前须读」，现已补齐）

### Invariant I0-5: 分页参数取值必须夹紧

- Rule：`size` 夹在 `1..200`（默认 50）；`page` 夹到 `>= 0`。越界不抛异常，静默夹紧。
- Applies to：`TaskExecutionController.listExecutions`。
- Violation consequence：`size=100000` 会直接把分页优化绕过去；`require(...)` 抛 `IllegalArgumentException` 则经 `GlobalExceptionHandler` 变 400，前端会把整页打成错误态。（来源: K-custom-exception-http-status-mapping）
- 来源：original（对照 `BounceController.kt:31-32` 的既有夹紧范式 `pageSize.coerceIn(1, 100)`）

### Invariant I0-6: 缓存键三连必须与测试断言同步 bump

- Rule：改动 `app.js` / `index.html` / `styles.css` 中任何一个，`index.html` 的 `styles.css?v=`、`trust-reply-workbench.js?v=`、`app.js?v=` 三处必须**同时改为同一新值**，且 `src/test/js/batchSendTaskConsoleVisualFix.test.js` 的三条 literal 断言（"bumps the stylesheet cache key" 用例）必须同步改成同一字符串。本计划取值 `20260817-v4-task-records-paging`。
- Applies to：`index.html`、`batchSendTaskConsoleVisualFix.test.js`。
- Violation consequence：只 bump 不改断言 → 构建期 node 测试失败、WAR 构建中止（2026-08-13 发布 eda4853 实测踩坑）；只改代码不 bump → 浏览器加载旧 `app.js`，分页改动看着完全没生效，会被误判为实现有问题。
- 来源：K-frontend-cache-key-triad（成文时本计划**漏载**该条，2026-08-16 复盘补入）

---

## 样式契约

### S0-1: 任务记录分页条

- **复用**：`.list-pager`（`styles.css:1105-1113`）、`.list-pager-info`（`:1115-1119`）、`.button.small`（`:2316-2321`）。**不新增任何 CSS**。禁止执行 agent 自造「近似」分页样式。
- **新增**：无。
- **DOM 结构**：在 `index.html` 的 `view-tasks` 内、`</div>`（`.table-wrap` 闭合）之后、`</section>`（`.panel` 闭合）之前插入，逐字：

```html
<div id="taskPager" class="list-pager" hidden>
    <button class="button small" id="taskPrevPage">上一页</button>
    <span id="taskPageInfo" class="list-pager-info"></span>
    <button class="button small" id="taskNextPage">下一页</button>
</div>
```

  该骨架与 `index.html:439` / `:658` / `:822` / `:891` / `:928` 五处既有实例逐字同构，仅 id 前缀不同。

- **禁止项**：inline style；新增 class；修改 `.list-pager` / `.list-pager-info` / `.button.small` 的既有规则块（这三个 class 各有 5+ 处使用点，任何就地修改都会波及退订名单、专家联系、AI 训练三个页面）。

### S0-2: 分页信息文案

- **复用**：`.list-pager-info`（`styles.css:1115-1119`）。
- **新增**：无。
- **文案格式**（逐字）：`第 {page+1} 页 / 共 {total} 条`。`total` 为 0 时整个 `#taskPager` 保持 `hidden`。
- **禁止项**：不显示总页数（避免 `Math.ceil` 与后端 total 的取整分歧）；不加「跳转到第 N 页」输入框。

### S0-3: 缓存键（I0-6）

- **复用**：不适用。
- **新增**：不适用（不新增任何 CSS 规则）。
- **DOM 结构**：`index.html` 三处逐字改为同一新值：

```html
<link rel="stylesheet" href="styles.css?v=20260817-v4-task-records-paging">
<script src="trust-reply-workbench.js?v=20260817-v4-task-records-paging"></script>
<script src="app.js?v=20260817-v4-task-records-paging"></script>
```

  同步把 `batchSendTaskConsoleVisualFix.test.js` 的 "bumps the stylesheet cache key" 用例里三条 `assert.ok(html.includes('...?v=...'))` 的字符串改成同一值。
- **禁止项**：只改其中一两处；改了代码却不 bump；bump 了却不改测试断言。

> ⚠️ **本计划不新增也不修改任何 CSS 规则块**，但 `styles.css` 的 `?v=` 引用行在 `index.html` 中要改 —— 注意区分：改的是 `index.html` 的引用，不是 `styles.css` 本身。

---

## 现状审计

### `task_execution` 表与索引

见主计划 X-2（逐条核对，不重复）。要点：索引只有主键与 `(batch_config_id, started_at)`；`ORDER BY started_at DESC` 全表 filesort。

### 写路径

`TaskExecutionService.runAndRecord` / `runAndRecordWithResult`（各 1 处 `repository.save` 起始 + 1 处终态 save + 1 处异常 save）、`updateProgressCounts`（列级 UPDATE）。**本计划一处不改**，仅列出以证明新增索引不会引入写热点：三个新索引均为读优化，`task_execution` 的写入频率为「每次任务启动 1 次 + 结束 1 次」，索引维护成本可忽略。

### 读路径（`TaskExecutionRepository` 现有方法全集，grep 回执）

```
$ grep -n "fun " src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt
findAllByOrderByStartedAtDesc()                          ← ★ 本计划替换其调用方
findAllByTaskTypeOrderByStartedAtDesc(taskType)          ← ★
findAllByStatusOrderByStartedAtDesc(status)              ← ★
findAllByTaskTypeAndStatusOrderByStartedAtDesc(t, s)     ← ★
findRecentByTaskType(taskType, limit)                    （轮询日志 + TaskProgressController 用，不改）
countActiveSince(taskType, triggerType, since)           （不改）
findRecentByBatchConfigId(batchConfigId, limit)          （不改）
findLastStartedAtByBatchConfigIds(batchConfigIds)        （不改；DTO 投影先例）
sumSuccessCountByBatchConfigIdBetween(...)               （不改）
updateProgressCounts(...)                                （不改）
```

带 ★ 的 4 个是 `listExecutions` 的唯一使用方。**保留方法本身**（避免波及未知调用方），只让 `listExecutions` 改用新增的分页查询。

★ 4 个方法的其他调用方（grep 回执，确认无第三方依赖）：

```
$ grep -rn "findAllByOrderByStartedAtDesc\|findAllByTaskTypeOrderByStartedAtDesc\|findAllByStatusOrderByStartedAtDesc\|findAllByTaskTypeAndStatusOrderByStartedAtDesc" src/main src/test
src/main/kotlin/.../task/service/TaskExecutionService.kt:19,22,25,28   ← listExecutions 的 4 个分支
```

**仅此 4 处，无测试 stub。** 因此改 `listExecutions` 不会触发 `UnnecessaryStubbingException`。

### `GET /api/task-executions` 的前端调用点全集（I0-4 依据，grep 回执）

```
$ grep -n "task-executions" src/main/resources/static/app.js
5276:  const tasks = await api("/api/task-executions?taskType=CANDIDATE_OPERATOR_STATUS_SYNC");   ← ★ 受形状变更影响
6361:  data = await api("/api/task-executions/recent-polls?limit=10");                            （不同端点，不受影响）
6425:  const data = await api(`/api/task-executions/recent-polls/${id}/detail`);                  （不同端点）
8913:  const tasks = await api(`/api/task-executions${suffix}`);                                  ← ★ 本计划主改点
8938:  const task = await api(`/api/task-executions/${taskId}`);                                  （单行端点，不受影响）
8945:  data = await api(`/api/task-executions/recent-polls/${taskId}/detail`);                    （不同端点）
```

**受影响恰好 2 处**：`:5276` 与 `:8913`。

`:5276` 的上下文（改动前基线，`app.js:5274-5295`，2026-08-16 逐字实读）：

```javascript
    if (!btn) return;
    try {
        const tasks = await api("/api/task-executions?taskType=CANDIDATE_OPERATOR_STATUS_SYNC");
        if (!tasks || tasks.length === 0) {
            btn.title = "暂无同步记录";
            return;
        }
        const task = tasks[0];
        const startedAt = task.startedAt || "-";
        const status = task.status || "-";
        const success = Number(task.successCount || 0);
        const failure = Number(task.failureCount || 0);
        let skipped = 0;
        if (task.resultSummary) {
            try {
                const summary = typeof task.resultSummary === "string"
                    ? JSON.parse(task.resultSummary)
                    : task.resultSummary;
                skipped = Number(summary.skipped || 0);
```

**关键**：它取 `tasks[0]`（最新一条）并读 **`task.resultSummary`**。后者被 I0-1/M-1 从列表响应中移除，因此这里必须改成两段式（I0-4）：

```javascript
const page = await api("/api/task-executions?taskType=CANDIDATE_OPERATOR_STATUS_SYNC&size=1");
const items = page.items || [];
if (items.length === 0) { btn.title = "暂无同步记录"; return; }
const task = await api(`/api/task-executions/${items[0].id}`);   // 单行端点仍返回 resultSummary
```

`GET /{id}` 由 N0-2 保证不改，仍返回 `TaskExecutionResponse`（含 `resultSummary`），可直接承接后续逻辑。

### DTO 投影先例（无需 spike）

见主计划 X-1：本仓库已有 6 个 `@Query` + DTO 投影先例，其中 `BatchConfigLastExecution` 就在本文件内。写法照抄：列别名与 DTO 属性名对齐（`SELECT batch_config_id AS batch_config_id, MAX(started_at) AS last_started_at`）。

### 前端样式盘点

见主计划 X-7（`.list-pager` 骨架、`loadSuppressions` 分页范式、`loadTasks` / `view-tasks` 改动前基线）。

### 交互点

| # | 写 | 读 | 处理 |
|---|---|---|---|
| IP0-1 | `runAndRecord` 写行 | 新分页查询读 | 新增索引不改写入语义 |
| IP0-2 | 后端响应形状 `List` → `{items,total}` | `app.js:5276` + `:8913` | I0-4，T0-5 强制同步 |

---

## 实现方案

### T0-1 迁移 V100（I0-2）

新建 `src/main/resources/db/migration/V100__add_task_execution_indexes.sql`：

```sql
-- 任务记录列表：ORDER BY started_at DESC 以及按类型/状态筛选，
-- 在 V4 建表时均无索引可用，13k+ 行已导致全表扫 + filesort。
CREATE INDEX idx_te_started ON task_execution (started_at);
CREATE INDEX idx_te_type_started ON task_execution (task_type, started_at);
CREATE INDEX idx_te_status_started ON task_execution (status, started_at);
```

- 不含 `${...}`，不触发 `K-flyway-placeholder-replacement` 的占位符问题（且 `application.yml` 已显式设 `placeholder-replacement: false`，该约束须维持）。
- MySQL 的 `ORDER BY col DESC` 可用升序索引反向扫描，无需 `DESC` 索引（MySQL 5.7 忽略 `DESC` 关键字，8.0 才真正支持降序索引；写升序在两个版本上行为一致）。
  ⚠️ **这是 MySQL 通用行为，未在本仓库/本线上实例实测**（成文时未标注，2026-08-16 复盘补标）。执行时对新查询跑一次 `EXPLAIN`，确认 `Extra` 不含 `Using filesort`；若线上表现不同，改为显式写 `DESC` 索引。

### T0-2 Repository 新增投影与分页查询（I0-1 / I0-2 / I0-3）

改 `TaskExecutionRepository.kt`，**只新增，不改既有方法**。

新增投影 DTO（置于文件内、`BatchConfigLastExecution` 之后）：

```kotlin
/**
 * 列表投影：刻意不含 request_payload / result_summary（两者均为 TEXT，
 * 单条 AUTO_REPLY_ALL 的 result_summary 内嵌 accounts[].repliedExperts[]，
 * 可达数十 KB，而列表页一个字段都不用）。见主计划 Invariant M-1。
 */
data class TaskExecutionListItem(
    val id: Long,
    val taskType: String,
    val triggerType: String,
    val status: String,
    val successCount: Int,
    val failureCount: Int,
    val errorMessage: String?,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?
)
```

新增 4 个分页查询 + 4 个 count 查询。SELECT 列表逐字如下（四个查询共用同一列清单，仅 WHERE 不同）：

```
SELECT id AS id, task_type AS task_type, trigger_type AS trigger_type,
       status AS status, success_count AS success_count, failure_count AS failure_count,
       error_message AS error_message, started_at AS started_at, finished_at AS finished_at
FROM task_execution
[WHERE ...]
ORDER BY started_at DESC
LIMIT :size OFFSET :offset
```

方法名：`findPage` / `findPageByTaskType` / `findPageByStatus` / `findPageByTaskTypeAndStatus`；对应 `countAll` / `countByTaskType` / `countByStatus` / `countByTaskTypeAndStatus`（返回 `Long`）。

⚠️ 不使用 `IN (:...)` 形式，因此不涉及 `K-empty-list-in-query-guard` 的空集合陷阱。

### T0-3 Service 分页（I0-3）

改 `TaskExecutionService.listExecutions`，签名改为：

```kotlin
fun listExecutions(taskType: String?, status: String?, page: Int, size: Int): TaskExecutionPage
```

返回 `data class TaskExecutionPage(val items: List<TaskExecutionListItem>, val total: Long)`。

四分支结构与现有 `when` 完全一致，只是每个分支同时调分页查询与 count 查询。`offset = page.toLong() * size`。

**保留**原 4 个 repository 方法不删（N0-4），但 `listExecutions` 不再调用它们。

### T0-4 Controller 分页（I0-4 / I0-5）

改 `TaskExecutionController.listExecutions`：

```kotlin
@GetMapping
fun listExecutions(
    @RequestParam(required = false) taskType: String?,
    @RequestParam(required = false) status: String?,
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "50") size: Int
): TaskExecutionPageResponse {
    val safeSize = size.coerceIn(1, 200)
    val safePage = page.coerceAtLeast(0)
    val result = service.listExecutions(taskType, status, safePage, safeSize)
    return TaskExecutionPageResponse(
        items = result.items.map { it.toListResponse() },
        total = result.total
    )
}
```

新增 `TaskExecutionListItemResponse`（9 个字段，时间格式化沿用现有 `startedAt.toString()` 语义以满足 N0-1）与 `TaskExecutionPageResponse(items, total)`。

**`TaskExecutionResponse` 与 `toResponse()` 保留不动**——`GET /{id}` 仍用它（N0-2）。

### T0-5 前端分页（I0-4 / S0-1 / S0-2）

改 `app.js`：

1. `state` 增加 `tasksPage: 0`、`tasksTotal: 0`（放在既有 `suppressionsPage` 附近，保持同构）。
2. 重写 `loadTasks()`，逐字对齐 `loadSuppressions()` 的范式：

```javascript
const TASK_PAGE_SIZE = 50;

async function loadTasks() {
    const params = new URLSearchParams();
    const taskType = $("#taskTypeFilter").value;
    const status = $("#taskStatusFilter").value;
    if (taskType) params.set("taskType", taskType);
    if (status) params.set("status", status);
    params.set("page", String(state.tasksPage));
    params.set("size", String(TASK_PAGE_SIZE));
    const data = await api(`/api/task-executions?${params}`);
    const tasks = data.items || [];
    state.tasksTotal = data.total ?? tasks.length;
    $("#tasksTable").innerHTML = tasks.map((task) => `
        <tr class="task-row" data-task-id="${task.id}" data-task-type="${escapeHtml(task.taskType)}" onclick="toggleTaskDetail(this)" style="cursor:pointer;">
            <td>${task.id}</td>
            <td>${escapeHtml(task.taskType)}</td>
            <td>${escapeHtml(task.triggerType)}</td>
            <td>${badge(labelStatus(task.status), task.status === "SUCCESS" ? "ok" : task.status === "FAILED" ? "error" : "warn")}</td>
            <td>${task.successCount}/${task.failureCount}</td>
            <td>${escapeHtml(task.startedAt)}</td>
            <td>${escapeHtml(task.errorMessage || "")}</td>
        </tr>
    `).join("");
    renderTaskPager();
}
```

  ⚠️ `<td>` 七列内容与改动前**逐字相同**（N0-1）。

3. 新增 `renderTaskPager()`：`total === 0` 时 `#taskPager.hidden = true`；否则显示，`#taskPageInfo.textContent = \`第 ${state.tasksPage + 1} 页 / 共 ${state.tasksTotal} 条\``；`#taskPrevPage.disabled = state.tasksPage === 0`；`#taskNextPage.disabled = (state.tasksPage + 1) * TASK_PAGE_SIZE >= state.tasksTotal`。
4. 绑定：`#taskPrevPage` / `#taskNextPage` 改页后 `loadTasks()`；`#loadTasksBtn`（`app.js:11378`）与两个筛选 select 的变更须先 `state.tasksPage = 0` 再 `loadTasks()`。
5. **`:5276` 改两段式（I0-4）**：按「现状审计」给出的改后代码替换取数部分（`?size=1` → `items[0].id` → `GET /{id}`），后续 `startedAt` / `status` / `successCount` / `failureCount` / `resultSummary` 的读取逻辑**一行不改**。

### T0-6 index.html（S0-1 / S0-3 / I0-6）

1. 按 S0-1 插入 `#taskPager` 三元素骨架。**其余 `view-tasks` 内容一字不动**（下拉选项是 B2 的事）。
2. 按 S0-3 把三处缓存键改为 `20260817-v4-task-records-paging`。
3. 同步改 `batchSendTaskConsoleVisualFix.test.js` 的三条 literal 断言（I0-6）。

### T0-7 测试

新建 `src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionListPagingTest.kt`：

- `size` 越界（0 / -1 / 100000）被夹到 `1..200`，不抛异常、不返回 400（I0-5）。
- `page` 为负被夹到 0（I0-5）。
- 四种筛选组合各自调用对应的分页 + count 方法，且 `total` 来自同组合的 count（I0-3）。
- 响应形状为 `{items, total}`，`items` 元素**不含** `requestPayload` / `resultSummary` 属性（I0-1，用 Jackson 序列化后断言 JSON 字符串不含这两个 key）。

新建 `src/test/js/taskRecordsPaging.test.js`：

- `total = 0` 时 `#taskPager` 保持 hidden。
- 第 1 页 `#taskPrevPage.disabled === true`；末页 `#taskNextPage.disabled === true`。
- `#taskPageInfo` 文案为 `第 1 页 / 共 137 条` 形式。
- 切换筛选后 `state.tasksPage` 归零。
- 表格 7 列渲染与改动前基线逐字一致（N0-1 回归）。

新增迁移的文本断言（沿用 `QaSeedEncodingRepairMigrationTest` 范式，不需 Docker）：在 `TaskExecutionListPagingTest` 内加一条用例，`Files.readString(Path.of("src/main/resources/db/migration/V100__add_task_execution_indexes.sql"))` 断言含三个 `CREATE INDEX` 且**不含** `${`。

---

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V100__add_task_execution_indexes.sql` | 新增 | 三个索引 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt` | 修改 | 新增 `TaskExecutionListItem` + 4 分页 + 4 count；既有方法不动 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt` | 修改 | 只改 `listExecutions`；新增 `TaskExecutionPage` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt` | 修改 | 只改 `listExecutions`；新增 2 个 response DTO |
| 5 | `src/main/resources/static/app.js` | 修改 | `loadTasks` 重写 + `renderTaskPager` + state + 绑定 + `:5276` 适配 |
| 6 | `src/main/resources/static/index.html` | 修改 | 插入 `#taskPager` |
| 7 | `src/test/kotlin/.../task/controller/TaskExecutionListPagingTest.kt` | 新增 | 后端用例 |
| 8 | `src/test/js/taskRecordsPaging.test.js` | 新增 | 前端用例 |
| 9 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | **仅**改三条缓存键 literal 断言（I0-6）；其余用例一行不动 |

文件数 9 ≤ 10。子系统 2（task 后端 / 前端）。

---

## 验证命令

见主计划「验证命令」节。本计划相关的快速迭代命令：

```bash
# 本计划后端用例
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskExecutionListPagingTest

# 本计划前端用例
node --test src/test/js/taskRecordsPaging.test.js

# 缓存键回归（改 index.html 后必跑）
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js

# app.js 语法检查（pom 的 exec-maven-plugin 也跑这条，改 app.js 后先跑它最省时间）
node --check src/main/resources/static/app.js
```

> ⚠️ `verify.sh` **只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件**，不可作为本计划的回归门禁。（来源: K-js-test-invocation-surface）

---

## 验收标准

- **I0-1**：`TaskExecutionListPagingTest` 中断言序列化后的 items JSON 不含 `requestPayload` / `resultSummary`；且 `grep -n "request_payload\|result_summary" TaskExecutionRepository.kt` 无命中（该文件的查询全部为新增投影，既有方法用的是派生查询不写 SQL）。
- **I0-2**：`V100` 文本断言含 `idx_te_started` / `idx_te_type_started` / `idx_te_status_started` 三行 `CREATE INDEX`。
- **I0-3**：四组合各有一条用例，断言 `total` 由对应 count 方法产出（Mockito verify 对应方法被调用、其他 count 方法未被调用）。
- **I0-4**：`grep -n "task-executions" src/main/resources/static/app.js` 的 `:5276` 与 `:8913` 两处均已取 `.items`；JS 用例覆盖列表渲染。
- **I0-5**：越界入参用例通过且**不返回 400**。
- **S0-1**：`grep -n 'id="taskPager"' -A 3 src/main/resources/static/index.html` 的输出与契约骨架逐字一致；本计划的 commit 中**不含对 `styles.css` 的任何规则块增删改**（按本计划自身的 commit 范围核对，不要用 `git diff styles.css 为空` —— A1 在同一分支上确实改过 CSS，整文件 diff 不为空是预期）。
- **S0-2**：JS 用例断言文案格式。
- **S0-3 / I0-6**：`grep -c "20260817-v4-task-records-paging" src/main/resources/static/index.html` 为 **3**；`grep -c "20260817-v4-task-records-paging" src/test/js/batchSendTaskConsoleVisualFix.test.js` 为 **3**；`node --test src/test/js/batchSendTaskConsoleVisualFix.test.js` 通过。
- 回归：执行主计划「验证命令」节的全量测试命令通过。

---

## 人工验收清单

### A0-1: 首屏加载时间与分页（Observable outcome）

- 前置条件：`task_execution` 有 ≥ 200 行。
- 操作步骤：
  1. 打开「任务记录」Tab，掐表。
  2. 数首屏表格行数。
  3. 滚到面板底部。
  4. 点「下一页」，再点「上一页」。
- 预期结果：
  1. < 2 秒出现数据（改动前为数十秒）。
  2. 恰好 50 行。
  3. 出现分页条，文案形如 `第 1 页 / 共 213 条`，「上一页」为禁用态。
  4. 「下一页」后首行审计 ID 明显小于第 1 页首行；「上一页」回到原样。
- 覆盖：Observable outcome

### A0-2: 回归 —— 七列内容逐字不变（N0-1）

- 前置条件：截图保存改动前任一页的表格。
- 操作步骤：改动后打开同一页（同样的筛选条件、同样的第 1 页），逐列比对。
- 预期结果：审计 ID、任务类型（**仍是大写枚举**，如 `AUTO_REPLY_ALL`）、触发方式、状态徽章文案与颜色、`4/0` 形式的计数、开始时间字符串、错误原因 —— 七列**逐字相同**。P0 阶段中文名尚未上线属预期。
- 覆盖：N0-1

### A0-3: 筛选后页码归零

- 前置条件：任务记录页停留在第 3 页。
- 操作步骤：把「任务类型」下拉切到 `AUTO_REPLY_ALL`，点「查询任务执行记录」。
- 预期结果：回到第 1 页；`共 N 条` 的 N 变为该类型的条数，小于全部条数；「上一页」为禁用态。
- 覆盖：I0-3 / T0-5 第 4 点

### A0-4: 空结果不显示分页条

- 前置条件：任意。
- 操作步骤：把「任务类型」与「执行状态」组合成必然无结果的条件（如 `INITIAL_OUTREACH` + `RUNNING`，若确无该组合数据），查询。
- 预期结果：表格为空；**分页条整体不显示**（不出现「第 1 页 / 共 0 条」）。
- 覆盖：S0-2

### A0-5: 回归 —— 轮询日志弹窗（N0-2）

- 前置条件：任意。
- 操作步骤：点顶部「轮询日志」。
- 预期结果：弹窗正常，10 行轮询记录，字段齐全，与改动前一致。
- 覆盖：N0-2

### A0-6: 回归 —— 状态同步查询的 skipped 值（I0-4，**最容易静默错**）

- 前置条件：`task_execution` 中有一条 `CANDIDATE_OPERATOR_STATUS_SYNC` 行，且其 `result_summary` 的 JSON 里 `skipped` **不为 0**（可先跑一次同步，或手工 UPDATE 构造，例如 `skipped: 7`）。
- 操作步骤：改动前先把该按钮的 tooltip 文案抄下来；改动后打开同一视图，鼠标悬停该按钮读 tooltip；同时看浏览器控制台。
- 预期结果：tooltip 里的「跳过」数仍是 **7**，与改动前逐字相同。**若变成 0，说明两段式请求没做，只改了 `.items`** —— 这是本计划最容易静默出错的一处。控制台无报错。
- 覆盖：I0-4

### A0-7: 缓存键与静态资源刷新（I0-6）

- 前置条件：改动已构建部署。
- 操作步骤：
  1. 浏览器硬刷新前，直接看页面源码（右键「查看网页源代码」）里三条 `?v=` 的值。
  2. 正常刷新（不清缓存）页面，进「任务记录」。
- 预期结果：
  1. 三条值均为 `20260817-v4-task-records-paging`，三者完全相同。
  2. **不清缓存**也能看到分页条（说明缓存键确实生效了，浏览器重新拉了 `app.js`）。
- 覆盖：I0-6 / S0-3

### A0-8: 回归 —— A1/A2/A3 的成果未被回退（前置计划）

- 前置条件：A1–A3 已上线。
- 操作步骤：打开批量邮件任务控制台，看定时任务列表行的列对齐；打开执行日志抽屉，看背景是否不透明、关闭按钮是否重叠。
- 预期结果：列不错位、抽屉不透明、两个关闭按钮不重叠 —— 与 A1–A3 上线后一致，未被本计划的 `index.html` / `app.js` 改动回退。
- 覆盖：全链串行的正确性

---

## 知识回写（Phase 6）

- **新增** `docs/knowledge/task/K-task-execution-list-full-scan.md`：`GET /api/task-executions` 的三重放大（全表返回 / 全量 TEXT / 无索引）与不变量 M-1；附 `TaskExecutionResponse` 携带两个 TEXT 列而前端只用 7 个标量字段的对照。
- **新增** `docs/knowledge/frontend/K-list-pager-skeleton-reuse.md`：`.list-pager` 骨架在 `index.html` 有 5 处逐字一致的实例（`:439` / `:658` / `:822` / `:891` / `:928`），新增分页一律复制该三元素结构并只改 id 前缀，禁止新增 CSS。
- **更正** `docs/knowledge/audit/K-plan-quantified-claims-need-grep-receipts.md` 陷阱 #3（见主计划 X-1）。
