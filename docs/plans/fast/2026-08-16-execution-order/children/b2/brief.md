# Fast-P Child Brief — b2 (epoch 2, amended)

- Child: b2
- Plan: docs/plans/2026-08-16/b2-task-type-catalog-semantics.md
- Plan identity: commit:38ce7ad494397d168663036e9252b3d6bf1c2089  (amended per ledger amendment A4, human-approved)
- Depends on: b1
- Base: ad005d98b706ceed67b34c96a89e642334ca819a  (b1 terminal Code head)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast
- Knowledge files (K-*) referenced by plans live in the MAIN worktree (uncommitted): /Users/lukai/IdeaProjects/weibo-talent-introduction/docs/knowledge/
- Family main plan (MUST read first for shared invariants M-1..M-7, audits X-1..X-7, authoritative verification commands): docs/plans/2026-08-16/task-records-refactor-main.md

## Epoch status

Epoch 1: no edits, no commit — PLAN_CONFLICT on 4 unlisted test files. Amendment A4 (human-approved) authorizes them (14 files total). The three ⏳ metricLabel decisions were RESOLVED in epoch 1 with source evidence — CARRY THEM, do not redo the investigation (details below). Start fresh from base; the plan file list rows 11-14 define the exact test-file adaptations.

## Epoch-1 metricLabel decisions (with evidence, see execution.md)

- INITIAL_OUTREACH -> metricLabel null. Evidence: InitialOutreachService.kt:32,147-153 — InitialOutreachBatchResult is NOT a TaskExecutionSummaryProvider; reflection failure side hits 'skipped' not 'failed'; queue-mode rows persist QueuePublishResult(accepted) as 1/0.
- AUTO_REPLY_ALL -> metricLabel 轮询账号/失败账号. Evidence: BatchAutoMailReplyService.kt:167-178 — BatchAutoMailReplyResult implements TaskExecutionSummaryProvider (taskSuccessCount=successAccountCount, taskFailureCount=failedAccountCount); persisted in default SYNC deployment (application.yml:48 mail-queue.enabled default false). Caveat: QUEUE-mode rows persist 1/0 accepted.
- OPERATOR_STATUS_RECONCILE -> metricLabel 一致/异常. Evidence: OperatorStatusReconcileService.kt:303-315 — ReconcileReport implements TaskExecutionSummaryProvider (taskSuccessCount=consistent, taskFailureCount=dbVsExpected+esVsDb); both write paths use runAndRecordWithResult; no queue variant.

## Global constraints (binding, from master plan docs/plans/2026-08-16/00-execution-order.md)

1. JDK 11 mandatory. Use JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home for every mvn command; bare mvn fails to build.
2. Cache key triad (M-7 / K-frontend-cache-key-triad): set all three ?v= values in index.html to `20260817-v5-task-type-catalog` AND the three literal assertions in batchSendTaskConsoleVisualFix.test.js to the same value — in the SAME commit. Chain: b1 left v4 (20260817-v4-task-records-paging); verify before editing.
3. No Flyway migration for this child. Do not touch src/main/resources/db/migration/.
4. Next child b3 depends on this child's terminal code head (needs TaskTypeCatalog.Drilldown present).
5. Git: commit locally only, exactly one implementation commit `feat(fast-p): implement b2`. Never push, merge, rebase, amend, rewrite. Exclude docs/plans/fast/ from the commit.

## Authorized files (14 files — amended A4; modify nothing else)

1. src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalog.kt   (NEW)
2. src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractor.kt   (NEW)
3. src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt
4. src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionController.kt
5. src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt
6. src/main/resources/static/app.js
7. src/main/resources/static/index.html
8. src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt   (NEW; extractor + catalog tests merged)
9. src/test/js/taskRecordsSemantics.test.js   (NEW)
10. src/test/js/batchSendTaskConsoleVisualFix.test.js   (ONLY the three cache-key literals)
11. src/test/js/taskRecordsPaging.test.js   (A4: N0-1 col5 assertion to I1-2 semantics; other six columns verbatim; paging behavior assertions untouched)
12. src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionControllerTest.kt   (A4: constructor-call adaptation only)
13. src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionListPagingTest.kt   (A4: constructor-call adaptation only)
14. src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskExecutionControllerMvcTest.kt   (A4: @MockBean additions only)

No further widening: if a 15th file is needed, STOP and report PLAN_CONFLICT.

## Required commands (run all)

- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskExecutionSummaryExtractorTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskProgressControllerExecutionsTest
- node --test src/test/js/taskRecordsSemantics.test.js
- node --test src/test/js/batchSendTaskConsoleVisualFix.test.js
- node --check src/main/resources/static/app.js
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test   (full regression; Tests run: N, Failures: 0, Errors: 0, exit 0; includes node --test exec; verify.sh NOT a gate)
- git diff --check   (clean)

## Verify-first items

1. metricLabel decisions already resolved (see above) — verify the evidence quickly if you like, but do not re-litigate; land the three values as decided.
2. Re-verify by grep: allowedTaskTypes hardcoded set in TaskProgressController; the three hardcoded lists (index.html:940 / app.js:678); delegated-away methods have no other callers.
3. Plan's internal self-correction: list DTO adds ONLY taskTypeLabel + metricLabel; summaryText is REMOVED from the list DTO.
4. N0-1 update (file 11): the fixture row without metricLabel must render '— 无统计' (I1-2, no 0/0); the other six columns keep verbatim assertions.

## Downstream interfaces

- b3 (next child) uses TaskTypeCatalog.Drilldown — catalog class shape per plan T1-1.
- N-1..N-7 binding (recent-polls endpoints unchanged; batchOnly semantics; runAndRecord untouched; taskButtonMapping NOT merged into catalog).
- Chain check: index.html cache values must equal v4 before editing, v5 after.

## Plan text (exact approved content incl. amendment A4; authoritative)

# B2：任务类型注册表与单列语义（TaskTypeCatalog）

主计划：`task-records-refactor-main.md`　全链顺序：`00-execution-order.md`
编号：**B2**（全链第 5 份）
前置计划：**B1 必须已合并**（本计划在 B1 的 `TaskExecutionListItemResponse` 上加字段；`V100` 已占用；缓存键取链上下一个值 v5）
子系统数：2（task 后端 / 前端静态后台）　文件数：10
迁移版本：无（本计划不动 schema）　缓存键取值：`20260817-v5-task-type-catalog`

---

## 需求描述

### Observable outcome

1. 表格「任务类型」列显示中文名；类型下拉在运行时从后端拉取，覆盖后端**实际写入**的全部 taskType。
2. 「发信统计/成功数」**保持单列**，列内数值语义随行变化并在行内标注（`10/0 已发送/失败`、`4/0 轮询账号/失败账号`、`12/1 补全成功/失败`）；无语义声明的类型显示 `— 无统计`。
3. 展开任意行都有内容：有结构化明细的按类型渲染，其余回落为 `requestPayload` / `resultSummary` 的 JSON 折叠视图。
4. 状态下拉补齐 `PARTIAL_SUCCESS` 与 `CANCELLED`。

### What must NOT change

- **N1-1** `GET /api/task-progress/{taskType}/logs` 的 `batchOnly` 语义与其两条既有用例（`TaskProgressControllerExecutionsTest` 的 "batchOnly filters out batchNumber zero and negative" / "batchOnly false returns all logs"）不变。（来源: K-progress-log-batchonly-two-readers）
- **N1-2** `GET /api/task-progress/{taskType}/executions` 的 `TaskRunSummaryResponse` **响应形状**不变（字段名、字段数、日期格式 `yyyy-MM-dd HH:mm:ss`）。本计划只把它的**内部取数实现**换成共享 extractor。
- **N1-3** `/recent-polls` 与 `/recent-polls/{id}/detail` 一行不改。
- **N1-4** `TaskResultSummary.from()` 反射机制**保留**，继续决定 `runAndRecord` 写入侧的 status/counts。本计划只让它不再决定**展示**。
- **N1-5** `task_execution` / `task_progress_log` 的任何写入行为不变。
- **N1-6** P0 建立的分页语义、`{items,total}` 形状、`.list-pager` 骨架不变。
- **N1-7** `app.js:678` 的 `taskButtonMapping`（6 项，服务于任务启动按钮的文案与 btnId）**不改**——它管的是「启动按钮」，与「记录页展示」是两件事。本计划不合并这两份映射，仅在知识回写中记录二者关系。

### Out of scope

- 不删 `TaskResultSummary.from()`（N1-4）。
- 不做跳转（P2a/P2b）。
- 不做 RUNNING 行的启动收敛。
- 不改 `taskButtonMapping`（N1-7）。
- 不给 `TaskProgressController.getExecutions` 放开全部类型——它服务的是「任务进度弹窗」，有 UI 前提；`allowedTaskTypes` 改为从 catalog 的 `hasProgressUi` 派生，**成员集合保持当前 6 项不变**，只是不再手工维护第二份字符串。

---

## 关键不变量

### Invariant I1-1: catalog 是 taskType 语义的唯一声明源（M-3 落地）

- Rule：中文名、分组、两个计数列的语义标签、summary 提取规则 key、drilldown 声明，全部只在 `TaskTypeCatalog` 声明一次。`TaskProgressController.allowedTaskTypes` 必须写成 `TaskTypeCatalog.entries.filter { it.value.hasProgressUi }.keys`；前端不得硬编码任何 taskType 中文名。
- Applies to：`TaskTypeCatalog.kt`、`TaskProgressController.kt`、`TaskExecutionController.kt`、`app.js`、`index.html`。
- Violation consequence：见主计划 M-3（当前三份互不相同的硬编码名单）。
- 来源：M-3 / K-allowedTaskTypes-whitelist

### Invariant I1-2: 计数列语义由 catalog 提供，未声明必须显式标注无统计

- Rule：`metricLabel` 为 `null` 的类型，前端渲染 `— 无统计`，**不得**渲染 `0/0` 或 `successCount/failureCount`。
- Applies to：`TaskTypeCatalog` 的 `metricLabel` 字段、`app.js` 的列渲染。
- Violation consequence：`TaskResultSummary.from()` 靠反射猜字段名（成功侧命中 `sent`/`replied`/`accepted`/`fetched`/`dispatched`，失败侧命中 `manualReview`/`skipped`/`failureCount`）。`EXPERT_ENRICHMENT` 的结果字段是 `enriched`/`failed`，**一个都不命中**，`0/0` 是「反射没匹配上」而非「真的处理了 0 个」。继续显示 `0/0` 是在输出假数据。
- 来源：original（本轮 Phase 1b 实测 `TaskResultSummary.from()` 的 `firstInt` 名单）

### Invariant I1-3: 指标取数三级优先级（M-2 落地）

- Rule：`TaskExecutionSummaryExtractor.extract(taskType, execution)` 的取数顺序固定为 ① `resultSummary` → ② 该 executionId 最新一条 `task_progress_log.detailsJson` + 该行 `totalCount` → ③ `successCount` / `failureCount`。三级都无有效值时返回 `metricLabel = null` 语义（即前端渲染 `— 无统计`）。
- Applies to：`TaskExecutionSummaryExtractor.kt`（新）、`TaskExecutionController` 的列表与 `/{id}/detail`、`TaskProgressController.getExecutions`。
- Violation consequence：`resultSummary` 只在 block 返回后写入，运行中一律 null，所有指标渲染为 0。
- 来源：M-2 / K-execution-detail-running-needs-progress-log

### Invariant I1-4: 提取规则单点，不得再分散成三处 when

- Rule：现有 `TaskProgressController` 内的 `parseResultSummary`（6 分支）与 `fallbackFromLog`（6 分支）必须整体迁入 `TaskExecutionSummaryExtractor`，`TaskProgressController` 改为委托调用。迁移后 `TaskProgressController.kt` 内**不得残留** `when (taskType)` 结构。
- Applies to：`TaskProgressController.kt`、`TaskExecutionSummaryExtractor.kt`。
- Violation consequence：当前新增一个任务类型要同时改 `allowedTaskTypes` + `parseResultSummary` + `fallbackFromLog` 三处；不收口则本计划新增 catalog 后变成四处，比改造前更糟。
- 来源：K-allowedTaskTypes-whitelist

### Invariant I1-5: 明细端点必须有通用兜底，不得返回空态

- Rule：`GET /api/task-executions/{id}/detail` 对任何 taskType 都返回 200。有结构化 renderer 的返回结构化字段；无 renderer 的返回 `rawRequestPayload` / `rawResultSummary` 两个原始字符串。**禁止**用 `require(...)` 对 taskType 做前置断言。
- Applies to：`TaskExecutionController` 新增的 `/{id}/detail`。
- Violation consequence：这正是当前故障——`/recent-polls/{id}/detail` 的 `require(exec.taskType == "AUTO_REPLY_ALL")` 经 `GlobalExceptionHandler` 变 400，`toggleTaskDetail` 的 catch 吞掉后渲染「暂无明细」，19 种任务只有 2 种能展开。
- 来源：original（本轮实测）+ K-custom-exception-http-status-mapping

### Invariant I1-6: 原始 JSON 兜底必须限长并标注截断

- Rule：`rawRequestPayload` / `rawResultSummary` 返回前按 **32 KB** 截断，截断时置 `rawTruncated = true` 并在前端标注「内容过长已截断，完整内容见服务端日志」。
- Applies to：`TaskExecutionController./{id}/detail`。
- Violation consequence：`AUTO_REPLY_ALL` 的 `result_summary` 内嵌 `accounts[].repliedExperts[]`，无限长兜底会把 P0 刚消除的传输问题在明细路径上重新引入。
- 来源：K-review-event-audit-payload-bounds（同源思想：面向操作端的审计 payload 须限长并标记截断）

### Invariant I1-7: 任务类型下拉的选项来自实际数据，不是 catalog 全集

- Rule：`GET /api/task-executions/task-types` 返回 `SELECT DISTINCT task_type FROM task_execution`，与 catalog 做 **左连接**：catalog 有声明的取中文名，catalog 没声明的**仍然返回**并以原始枚举名兜底显示。
- Applies to：新增端点、`app.js` 的下拉注入。
- Violation consequence：若只返回 catalog 声明过的类型，将来新增任务类型而忘了写 catalog 时，它的记录在 UI 上**永久不可筛选**——把当前的硬编码问题换了个位置复现。
- 来源：original

### Invariant I1-8: 缓存键三连必须与测试断言同步 bump

- Rule：同 B1 的 I0-6。本计划取值 `20260817-v5-task-type-catalog`。
- Applies to：`index.html`、`batchSendTaskConsoleVisualFix.test.js`。
- Violation consequence：只 bump 不改断言 → 构建中止；只改代码不 bump → 浏览器加载旧 `app.js`，中文名与语义列看着完全没生效。
- 来源：K-frontend-cache-key-triad（成文时本计划**漏载**该条，2026-08-16 复盘补入）

---

## 样式契约

### S1-1: 任务类型下拉（运行时注入）

- **复用**：`.toolbar select`（`styles.css:362-363`）。**不新增 CSS**。
- **新增**：无。
- **DOM 结构**：`index.html` 内 `#taskTypeFilter` 只保留一个静态占位选项，其余由 JS 注入：

```html
<select id="taskTypeFilter">
    <option value="">全部自动化任务</option>
</select>
```

  JS 注入的每个 option 逐字为：`<option value="${code}">${label}（${count}）</option>`，其中 `label` 为 catalog 中文名或原始 code 兜底，`count` 为该类型的记录条数。
- **禁止项**：`index.html` 内保留任何具体 taskType 的 `<option>`（这是 I1-1 的落地断言点）；inline style；新增 class。

### S1-2: 状态下拉补齐

- **复用**：同 S1-1。
- **新增**：无。
- **DOM 结构**（`#taskStatusFilter` 逐字替换为）：

```html
<select id="taskStatusFilter">
    <option value="">全部执行状态</option>
    <option value="RUNNING">执行中</option>
    <option value="SUCCESS">执行成功</option>
    <option value="PARTIAL_SUCCESS">部分成功</option>
    <option value="FAILED">执行失败</option>
    <option value="CANCELLED">已取消</option>
</select>
```

  状态保持静态（值域由 `TaskExecutionService` 与 `TaskExecutionSummaryProvider` 固定，非动态发现）。
- **禁止项**：改动 `labelStatus()` 已有映射的既有取值（它服务于多个页面，须先 grep 使用点）。

### S1-3: 计数列的行内语义标注（单列）

- **复用**：`.text-muted`（`styles.css:2323-2326`）。**不新增 CSS**。
- **新增**：无。
- **DOM 结构**（单个 `<td>` 内，逐字）：

```html
<td>12/1 <span class="text-muted">补全成功/失败</span></td>
```

  无语义时逐字为：

```html
<td><span class="text-muted">— 无统计</span></td>
```

- **禁止项**：拆成两列（需求方已定「保持一列」）；用 `.badge` 承载语义标签（`.badge` 有底色，会与状态列的徽章视觉打架）；inline style。

### S1-4: 明细展开区的 JSON 兜底视图

- **复用**：`.pre`（`styles.css:1721`）、`.text-muted`（`:2323`）。**不新增 CSS**。
- **新增**：无。
- **DOM 结构**（`.task-detail-row` 的 `<td>` 内，逐字）：

```html
<div class="text-muted">请求参数</div>
<div class="pre">{JSON}</div>
<div class="text-muted">执行结果</div>
<div class="pre">{JSON}</div>
```

  `rawTruncated` 为 true 时，在对应 `.pre` 之后追加：

```html
<div class="text-muted">内容过长已截断，完整内容见服务端日志</div>
```

- **⚠️ 波及提示**：`.pre`（`styles.css:1721`，行号已复核）在 `app.js` 有多处使用点（专家详情、邮件预览、收发件箱、未匹配详情、自动回复预览等）。本契约**只新增使用点，不修改 `.pre` 的规则块**。（来源: K-mail-body-display-sites）
- **禁止项**：修改 `.pre` 规则块；inline style；用 `<pre>` 原生标签替代 `.pre` class。

### S1-5: 缓存键（I1-8）

- **复用 / 新增**：不适用（本计划不新增也不修改任何 CSS 规则块）。
- **DOM 结构**：`index.html` 三处逐字改为同一新值：

```html
<link rel="stylesheet" href="styles.css?v=20260817-v5-task-type-catalog">
<script src="trust-reply-workbench.js?v=20260817-v5-task-type-catalog"></script>
<script src="app.js?v=20260817-v5-task-type-catalog"></script>
```

  同步把 `batchSendTaskConsoleVisualFix.test.js` 的 "bumps the stylesheet cache key" 用例三条断言改成同一值。
- **禁止项**：只改其中一两处；改了代码却不 bump；bump 了却不改测试断言。

---

## 现状审计

### 后端实际写入的 taskType 全集（grep 回执，I1-1 / I1-7 依据）

```
$ grep -rn -A4 "runAndRecord" src/main/kotlin --include=*.kt | grep -oE '"[A-Z][A-Z0-9_]{3,}"' | sort | uniq -c | sort -rn
      9 "SCHEDULED"          ← triggerType，非 taskType
      7 "MANUAL"             ← triggerType
      5 "EXPERT_DISCOVERY"
      3 "QUEUE"              ← triggerType
      2 "RAW_PROMOTION_SCAN"
      2 "OPERATOR_STATUS_RECONCILE"
      2 "INITIAL_OUTREACH"
      2 "EXPERT_REVALIDATION"
      2 "CANDIDATE_OPERATOR_STATUS_SYNC"
      1 "POSTMASTER_REPUTATION"
      1 "MANUAL_SELECTIVE"   ← triggerType
      1 "MANUAL_ALL"         ← triggerType
      1 "DAILY_COUNT_RESET"
      1 "CHECK_REPLIES"
      1 "BOUNCE_COLLECTION"
      1 "AUTO_REPLY_ALL_DISPATCH"
      1 "AUTO_REPLY_ALL"
      1 "AUTO_REPLY_ACCOUNT"
      1 "AI_QA_EXTRACTION"
```

另有两处 taskType 以变量形式传入（`grep -rn "EXPERT_ENRICHMENT" src/main/kotlin`）：

```
discovery/controller/ExpertDiscoveryController.kt:218:  val taskType = "EXPERT_ENRICHMENT"
discovery/service/ExpertDiscoveryService.kt:846:        val taskType = "EXPERT_ENRICHMENT"
task/controller/TaskProgressController.kt:35:           "EXPERT_ENRICHMENT", "MANUAL_INITIAL_OUTREACH", "CHECK_REPLIES"
campaign/service/BatchSendControlService.kt:665:        const val TASK_TYPE = "MANUAL_INITIAL_OUTREACH"
```

**taskType 全集（15 种）**：`AI_QA_EXTRACTION`、`AUTO_REPLY_ACCOUNT`、`AUTO_REPLY_ALL`、`AUTO_REPLY_ALL_DISPATCH`、`BOUNCE_COLLECTION`、`CANDIDATE_OPERATOR_STATUS_SYNC`、`CHECK_REPLIES`、`DAILY_COUNT_RESET`、`EXPERT_DISCOVERY`、`EXPERT_ENRICHMENT`、`EXPERT_REVALIDATION`、`INITIAL_OUTREACH`、`MANUAL_INITIAL_OUTREACH`、`OPERATOR_STATUS_RECONCILE`、`POSTMASTER_REPUTATION`、`RAW_PROMOTION_SCAN`（16 项，含 `RAW_PROMOTION_SCAN`）。

⚠️ **正因为「靠 grep 数出来的全集」本身就不可靠（变量形式、future 新增），I1-7 要求下拉以 `DISTINCT task_type` 为准，catalog 只做 label 增强。**

### 三份互不相同的硬编码名单（M-3 的证据）

| 位置 | 内容 | 项数 |
|---|---|---|
| `index.html:940-947` `#taskTypeFilter` | `INITIAL_OUTREACH` / `MANUAL_INITIAL_OUTREACH` / `AUTO_REPLY_ALL` / `AUTO_REPLY_ACCOUNT` / `AUTO_REPLY_ALL_DISPATCH` | 5 |
| `TaskProgressController.kt:33-36` `allowedTaskTypes` | `EXPERT_REVALIDATION` / `RAW_PROMOTION_SCAN` / `EXPERT_DISCOVERY` / `EXPERT_ENRICHMENT` / `MANUAL_INITIAL_OUTREACH` / `CHECK_REPLIES` | 6 |
| `app.js:678-685` `taskButtonMapping` | 同上 6 项 | 6 |

三者交集只有 `MANUAL_INITIAL_OUTREACH` 一项，并集 10 项，均小于实际的 16 种。

### `TaskProgressController` 待迁出的两个方法（改动前基线）

`parseResultSummary(taskType, resultSummary, executionId)`（`TaskProgressController.kt:122-179`，2026-08-16 复核修正；成文时误写 `:120-172`）—— 6 个 `when` 分支：

| taskType | totalProcessed | totalPassed | totalRejected | summaryText |
|---|---|---|---|---|
| `EXPERT_REVALIDATION` | `stats.total` | `stats.passed` | `stats.demoted` | — |
| `RAW_PROMOTION_SCAN` | `stats.total` | `stats.promoted` | `stats.filtered + stats.emailRejected` | — |
| `EXPERT_DISCOVERY` | `stats.totalPapers` | `stats.indexed` | `totalPapers - indexed`（下限 0） | `root.summaryText` ?: `stats.summaryText` |
| `MANUAL_INITIAL_OUTREACH` | `root.total - root.remaining`（下限 0） | `root.sent` | `root.failed` | — |
| `CHECK_REPLIES` | `root.totalAccountsToPoll` | `root.successAccountCount` | `root.failedAccountCount` | — |
| `EXPERT_ENRICHMENT` | `enriched + failed` | `root.enriched` | `root.failed` | — |

`fallbackFromLog(executionId, taskType)`（`:180-235`，复核修正；成文时误写 `:174-227`）—— 同 6 分支，读 `task_progress_log` 最新一行的 `detailsJson`，字段名与上表一致但**不带 `stats` 前缀**（`EXPERT_REVALIDATION` 读 `details.passed` 而非 `details.stats.passed`）；`totalProcessed` 统一取该行的 `processedCount`。

`detectWasCancelled(resultSummary)`（`:236+`，复核修正）。私有 `ExecutionTotals` 在 `:115`—— 读 `root.wasCancelled`，用于把终态改判为 `CANCELLED`。**一并迁入 extractor。**

### `TaskResultSummary.from()` 反射名单（I1-2 的证据，`TaskExecutionService.kt` 文件末尾）

```kotlin
successCount = firstInt(fields, "sent", "replied", "accepted", "fetched", "dispatched")
failureCount = firstInt(fields, "manualReview", "skipped", "failureCount")
```

对照：`ManualOutreachResult` 有 `sent`/`failed` → 成功侧命中 `sent`，失败侧 `failed` **不在名单** → `failureCount` 恒为 0（除非结果类实现了 `TaskExecutionSummaryProvider`，`ManualOutreachResult` 恰好实现了，故走 provider 分支不受影响）。`EXPERT_ENRICHMENT` 的结果对象字段为 `enriched`/`failed`，两侧**全不命中** → `0/0`。

### 写路径 / 读路径

- **写**：`TaskExecutionService.runAndRecord*`（不改，N1-5）、`TaskProgressStore.persistProgressLog`（不改）。
- **读**：`TaskProgressController.getExecutions`（改为委托 extractor，形状不变 N1-2）、`TaskExecutionController.listExecutions`（P0 已改分页，本计划加字段）、新增 `/{id}/detail`、新增 `/task-types`。

### 交互点

| # | 写 | 读 | 处理 |
|---|---|---|---|
| IP1-1 | `runAndRecord*` 写 `result_summary`（终态才写） | extractor 第 ① 级 | I1-3 |
| IP1-2 | `persistProgressLog` 写 `details_json` | extractor 第 ② 级 | I1-3 |
| IP1-3 | catalog 声明 | `allowedTaskTypes` 派生 + 前端下拉 | I1-1 / I1-7 |
| IP1-4 | 未来新增 taskType 但漏写 catalog | 下拉 `DISTINCT` 兜底 | I1-7 |

### 前端样式盘点

见主计划 X-7。本计划额外确认：`.pre`（`styles.css:1721`）为新增使用点，不改规则块（K-mail-body-display-sites）。

---

## 实现方案

### T1-1 新建 `TaskTypeCatalog`（I1-1 / I1-2）

新建 `src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalog.kt`：

```kotlin
data class TaskTypeMeta(
    val code: String,
    val label: String,            // 中文名
    val group: String,            // SCHEDULED / MANUAL / QUEUE
    val metricLabel: String?,     // 计数列语义，如 "已发送/失败"；null → 前端渲染「— 无统计」
    val summaryRule: String?,     // extractor 的提取规则 key，null → 无结构化提取
    val hasProgressUi: Boolean,   // 派生 TaskProgressController.allowedTaskTypes
    val drilldown: Drilldown?     // P2 使用；本计划全部先声明为 null
)

enum class Drilldown { MAIL_BY_EXECUTION, EXPERT_BY_POLL_DETAIL }
```

按「现状审计」的 16 种类型逐条声明。

⚠️ **`metricLabel` 的判定口径（2026-08-16 复盘修正，与成文版本不同）**：列表列渲染的是**已持久化的** `success_count` / `failure_count`（B1 的投影只取这两列，M-1 禁止列表读 `result_summary`）。因此 `metricLabel` 只能在**这两个存量值确实有意义**的类型上为非 null；存量值由反射或 provider 决定，与「这个任务概念上处理了多少个」不是一回事。真实业务指标由 extractor 在**展开明细**时给出。

写入侧取值机制（`TaskExecutionService.TaskResultSummary.from()`，逐字）：

```kotlin
successCount = firstInt(fields, "sent", "replied", "accepted", "fetched", "dispatched")
failureCount = firstInt(fields, "manualReview", "skipped", "failureCount")
```
实现了 `TaskExecutionSummaryProvider` 的结果类走 provider 分支，不经反射。

| code | label | 结果类字段（实测） | metricLabel | 证据 |
|---|---|---|---|---|
| `MANUAL_INITIAL_OUTREACH` | 批量首发邮件 | `ManualOutreachResult` 实现 provider：`taskSuccessCount=sent` / `taskFailureCount=failed` | `已发送/失败` | ✅ `ManualInitialOutreachService.kt:1429-1449` |
| `AUTO_REPLY_ACCOUNT` | 单账号轮询自动回复 | `AutoMailReplyBatchResult(fetched, recorded, replied, manualReview, meetingInvitations, repliedExperts)`；反射成功侧首命中 `replied`（`sent` 不存在），失败侧命中 `manualReview` | `已回复/转人工` | ✅ `AutoMailReplyService.kt:642` + `:1156-1163` |
| `AUTO_REPLY_ALL_DISPATCH` | 批量分发与调度 | `QueueFanOutResult(dispatched)`；成功侧命中 `dispatched`，失败侧无字段恒 0 | `派发账号数/—` | ✅ `MailQueueConsumer.kt:80-82` |
| `BOUNCE_COLLECTION` | 退信收集 | block 是 for 循环，**返回 Unit**，反射零命中 → 存量恒 `0/0` | **`null`** | ✅ `BounceCollectionScheduler.kt:30-44` |
| `EXPERT_ENRICHMENT` | 学术数据补全 | 结果字段 `enriched` / `failed`，两侧**全不命中**反射名单 → 存量恒 `0/0` | **`null`** | ✅ `TaskProgressController.kt:161-169` 读 `root.enriched`/`root.failed` |
| `EXPERT_DISCOVERY` | 深度发现（外部数据源） | 指标在 `stats.totalPapers`/`stats.indexed`，反射不命中 → 存量不可信 | **`null`** | ✅ `TaskProgressController.kt:134-146` |
| `EXPERT_REVALIDATION` | 专家重新验证 | 指标在 `stats.total`/`stats.passed`/`stats.demoted`，反射不命中 | **`null`** | ✅ `TaskProgressController.kt:126-130` |
| `RAW_PROMOTION_SCAN` | RAW 层晋升扫描 | 指标在 `stats.total`/`stats.promoted`/`stats.filtered`+`emailRejected` | **`null`** | ✅ `TaskProgressController.kt:131-135` |
| `CHECK_REPLIES` | 检查回复 | 指标在 `totalAccountsToPoll`/`successAccountCount`/`failedAccountCount`，反射不命中 | **`null`** | ✅ `TaskProgressController.kt:154-158` |
| `INITIAL_OUTREACH` | 定时首发邮件 | `sendInitialBatch(...)` 返回类型**未核实** | ⏳ 执行时核实 | ⏳ 先按 `null` 落地，核实后再改 |
| `AUTO_REPLY_ALL` | 全量账号自动收信回复 | `receiveAndAutoReplyAll(...)` / publisher 返回类型**未核实** | ⏳ 执行时核实 | ⏳ 同上 |
| `OPERATOR_STATUS_RECONCILE` | 运营状态对账 | `ReconcileReport`（字段**未核实**） | ⏳ 执行时核实 | ⏳ 同上 |
| `CANDIDATE_OPERATOR_STATUS_SYNC` | 候选人状态同步 | `reconcileAll()` 返回**未核实** | `null` | 保守取 null |
| `DAILY_COUNT_RESET` | 每日计数重置 | 未核实 | `null` | 保守取 null |
| `POSTMASTER_REPUTATION` | Postmaster 信誉拉取 | `checkAndAct()` 返回**未核实** | `null` | 保守取 null |
| `AI_QA_EXTRACTION` | AI QA 提炼 | `extractBatch(...)` 返回**未核实** | `null` | 保守取 null |

**执行规则**：⏳ 三项必须在动手前读结果类源码定夺，**先按 `null` 落地是安全的**（显示「— 无统计」，真实数字在展开明细里，不会输出错值）；反过来给一个假标签会让运营信一个错数字。**不得凭字段名猜语义。**

**这条修正的后果**：列表列上真正有数字的只有 3 种任务，其余 13 种显示「— 无统计」，业务指标改由展开明细承载。这比成文版本承诺的「每种任务都有语义数字」保守，但它是 M-1（列表禁读大 TEXT 列）与写入侧反射机制共同决定的唯一诚实结果。

`hasProgressUi = true` 的**恰好**是当前 `allowedTaskTypes` 的 6 项（N1-2 要求成员集合不变）：`EXPERT_REVALIDATION`、`RAW_PROMOTION_SCAN`、`EXPERT_DISCOVERY`、`EXPERT_ENRICHMENT`、`MANUAL_INITIAL_OUTREACH`、`CHECK_REPLIES`。

⚠️ `metricLabel` 为 null 的 6 项：这些任务当前确实没有可信的计数语义（`DAILY_COUNT_RESET` 等），显式声明为「无统计」比继续显示反射猜出来的 `0/0` 诚实。**不要为了让每一行都有数字而编造语义标签。**

### T1-2 新建 `TaskExecutionSummaryExtractor`（I1-3 / I1-4）

新建 `src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractor.kt`：

- 把 `TaskProgressController.parseResultSummary` / `fallbackFromLog` / `detectWasCancelled` **整体迁入**，`when` 的分支键改为 `TaskTypeMeta.summaryRule`。
- 对外方法：`fun extract(taskType: String, execution: TaskExecution): ExecutionTotals`，内部按 I1-3 的三级优先级。
- `ExecutionTotals` 从 `TaskProgressController` 的 private 内部类提升为该文件的公开 data class（字段不变：`totalProcessed` / `totalPassed` / `totalRejected` / `summaryText`）。
- 依赖注入 `TaskProgressLogRepository` + `ObjectMapper`（与 `TaskProgressController` 现有依赖一致）。

### T1-3 `TaskProgressController` 改为委托（I1-1 / I1-4 / N1-1 / N1-2）

- `allowedTaskTypes` 改为 `TaskTypeCatalog.entries.filterValues { it.hasProgressUi }.keys`。
- `parseResultSummary` / `fallbackFromLog` / `detectWasCancelled` / private `ExecutionTotals` **删除**，`getExecutions` 内改调 `extractor.extract(...)`。
- `getProgressLogs` 与其 `batchOnly` 分支**一行不改**（N1-1）。
- `TaskRunSummaryResponse` 的字段与日期格式不改（N1-2）。

### T1-4 列表加语义字段（I1-1 / I1-2 / I1-3）

改 `TaskExecutionController`：

- `TaskExecutionListItemResponse`（P0 建立）新增 3 个字段：`taskTypeLabel: String`（catalog 命中取 label，未命中回落原 code）、`metricLabel: String?`、`summaryText: String?`。
- `metricLabel` 直接取自 catalog（不经 extractor，避免列表页 N 次读 `task_progress_log`）。
- `summaryText` **仅当** `resultSummary` 已存在且 catalog 声明了 `summaryRule` 时提取；列表页**不做**第 ② 级 progress_log 回落（那是 `/{id}/detail` 的职责）。这是对 I1-3 的**刻意收窄**：列表页 N 行 × 一次 progress_log 查询会把 P0 的性能收益吃掉。**该收窄必须在代码注释中写明。**

⚠️ 由于 I1-1 要求列表不再读 TEXT 列（P0 的 M-1），`summaryText` 的提取需要 `result_summary`。解法：**列表页不提供 `summaryText`**，该字段固定为 null，摘要只在展开明细时出现。T1-4 据此简化为只加 `taskTypeLabel` 与 `metricLabel` 两个字段，`summaryText` 从列表 DTO 中**移除**。

> 执行说明：上一段是本计划内部的自我修正，**以本段为准** —— 列表 DTO 只加 `taskTypeLabel` + `metricLabel`，不加 `summaryText`。

### T1-5 新增 `/task-types` 端点（I1-7）

- Repository 加 `@Query("SELECT task_type AS task_type, COUNT(*) AS cnt FROM task_execution GROUP BY task_type")` 返回 DTO 投影 `TaskTypeCount(taskType, cnt)`（照 `BatchConfigLastExecution` 范式）。
- Controller 加 `GET /api/task-executions/task-types`，返回 `List<TaskTypeOption>{code, label, count}`，`label` 走 catalog 左连接兜底，按 `count` 降序。
- **不含** WHERE，不含 TEXT 列，满足 M-1。

### T1-6 新增 `/{id}/detail` 通用明细端点（I1-3 / I1-5 / I1-6）

- `GET /api/task-executions/{id}/detail` → `TaskExecutionDetailResponse`：
  - `id`、`taskType`、`taskTypeLabel`、`status`、`startedAt`、`finishedAt`、`durationSeconds`
  - `totals`（extractor 三级取数产出的 `ExecutionTotals`）
  - `metricLabel`
  - `rawRequestPayload: String?`、`rawResultSummary: String?`、`rawTruncated: Boolean`（I1-6，32 KB）
- **不得**使用 `require(...)` 对 taskType 断言（I1-5）。
- 记录不存在时抛 `NoSuchElementException` → `GlobalExceptionHandler` 映射 404（对照 `K-custom-exception-http-status-mapping`：自定义业务异常须继承 `IllegalArgumentException` / `IllegalStateException` / `NoSuchElementException`，否则一律 500）。

### T1-7 前端（I1-1 / I1-2 / I1-5 / S1-1 ~ S1-4）

改 `app.js`：

1. 新增 `loadTaskTypeOptions()`：拉 `/api/task-executions/task-types`，按 S1-1 注入 `#taskTypeFilter`（保留静态占位项，其余清空重建），保持当前选中值。在 `loadTasks()` 首次进入时调用一次并缓存于 `state.taskTypeOptions`。
2. `loadTasks()` 的第 2 列改渲染 `task.taskTypeLabel`；第 5 列按 S1-3 渲染。
3. 重写 `toggleTaskDetail(row)`：一律改调 `/api/task-executions/${taskId}/detail`；按 catalog renderer 分派（本计划只保留 `EXPERT_DISCOVERY` 的 `bySource` 表 + `summaryText` 两个既有渲染器），其余走 S1-4 的 JSON 兜底。**删除**对 `/recent-polls/{id}/detail` 的调用（该端点本身保留，N1-3；只是记录页不再用它）。
4. `normalizeDiscoveryResultSummary` / `renderDiscoverySummaryText` / `renderBySourceTable` 三个既有函数保留复用。

改 `index.html`：按 S1-1 清空 `#taskTypeFilter` 的具体选项，按 S1-2 替换 `#taskStatusFilter`。

### T1-8 测试

新建 `src/test/kotlin/.../task/service/TaskExecutionSummaryExtractorTest.kt`：
- 6 种 `summaryRule` 各一条：`resultSummary` 存在时按第 ① 级解析（断言与迁移前 `parseResultSummary` 的输出逐字一致，防迁移走样）。
- **RUNNING 场景**：`resultSummary = null` 但 `task_progress_log` 有 `detailsJson`，断言走第 ② 级取到非零值（I1-3，M-2 的机器验证点）。
- 三级全空时返回全 0 且 `summaryText = null`。
- `wasCancelled = true` 时终态改判 `CANCELLED`。

catalog 断言并入同一文件（原 `TaskTypeCatalogTest.kt`，为腾名额合并）：
- `hasProgressUi = true` 的集合**恰好等于** 6 项字面量集合（N1-2 锁定）。
- 每个 `summaryRule` 非 null 的条目，`TaskExecutionSummaryExtractor` 中都有对应分支（反向断言，防 catalog 与 extractor 漂移）。
- catalog 的 code 集合覆盖「现状审计」列出的 16 种（字面量断言）。

改 `src/test/kotlin/.../task/controller/`（既有 `TaskProgressControllerExecutionsTest`）：
- 保留两条 `batchOnly` 用例不动（N1-1）。
- 新增一条：`allowedTaskTypes` 校验行为不变（非白名单类型仍返回 400）。

新建 `src/test/js/taskRecordsSemantics.test.js`：
- `metricLabel` 为 null 时渲染 `— 无统计`，且**不含** `0/0`（I1-2）。
- `metricLabel` 非 null 时渲染 `12/1 <span class="text-muted">补全成功/失败</span>`（S1-3 逐字）。
- 第 2 列渲染 `taskTypeLabel`。
- 明细展开在无 renderer 时渲染两个 `.pre` 块（S1-4 逐字）；`rawTruncated` 时追加截断文案。
- `#taskTypeFilter` 注入后仍保留 `value=""` 的占位项，且选中值不丢。

既有测试适配（fast-p 修正 A4：T1-5/T1-6 给 `TaskExecutionController` 加依赖、I1-2 改第 5 列渲染语义，经人工批准新增 4 个授权文件）：

- `src/test/js/taskRecordsPaging.test.js`：N0-1 的「七列逐字」断言按新语义更新 —— 无 `metricLabel` 的行第 5 列渲染 `— 无统计`（I1-2），其余六列仍逐字断言；不改动分页行为断言。
- `src/test/kotlin/.../task/controller/TaskExecutionControllerTest.kt` 与 `.../TaskExecutionListPagingTest.kt`：仅适配 `TaskExecutionController(...)` 构造调用（新增依赖实参），不改既有断言。
- `src/test/kotlin/.../task/controller/TaskExecutionControllerMvcTest.kt`：`@WebMvcTest` 切片补新增依赖的 `@MockBean`，不新增/改动用例。

---

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/.../task/domain/TaskTypeCatalog.kt` | 新增 | 16 条声明 + `TaskTypeMeta` + `Drilldown` |
| 2 | `src/main/kotlin/.../task/service/TaskExecutionSummaryExtractor.kt` | 新增 | 迁入三方法 + 三级取数 |
| 3 | `src/main/kotlin/.../task/controller/TaskProgressController.kt` | 修改 | 白名单派生 + 委托 extractor + 删三方法 |
| 4 | `src/main/kotlin/.../task/controller/TaskExecutionController.kt` | 修改 | 列表加 2 字段 + `/task-types` + `/{id}/detail` |
| 5 | `src/main/kotlin/.../task/repository/TaskExecutionRepository.kt` | 修改 | 新增 `TaskTypeCount` 投影 + group by 查询 |
| 6 | `src/main/resources/static/app.js` | 修改 | 下拉注入 + 两列渲染 + `toggleTaskDetail` 重写 |
| 7 | `src/main/resources/static/index.html` | 修改 | S1-1 / S1-2 |
| 8 | `src/test/kotlin/.../task/service/TaskExecutionSummaryExtractorTest.kt` | 新增 | extractor 用例 **+ catalog 用例**（原计划的 `TaskTypeCatalogTest.kt` 并入此文件，为缓存键测试腾出名额） |
| 9 | `src/test/js/taskRecordsSemantics.test.js` | 新增 | — |
| 10 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 | **仅**改三条缓存键 literal 断言（I1-8）；其余用例一行不动 |
| 11 | `src/test/js/taskRecordsPaging.test.js` | 修改 | T1-8 适配：N0-1 第 5 列按 I1-2 新语义（fast-p 修正 A4） |
| 12 | `src/test/kotlin/.../task/controller/TaskExecutionControllerTest.kt` | 修改 | 构造调用适配（fast-p 修正 A4） |
| 13 | `src/test/kotlin/.../task/controller/TaskExecutionListPagingTest.kt` | 修改 | 构造调用适配（fast-p 修正 A4） |
| 14 | `src/test/kotlin/.../task/controller/TaskExecutionControllerMvcTest.kt` | 修改 | @MockBean 补充（fast-p 修正 A4） |

文件数 14；其中授权文件上限说明：原「10 ≤ 10 已到上限」规则经 fast-p 修正 A4 放宽至 14（仅本次四文件），后续仍不得自行扩围；发现第 15 个文件须停止回报。子系统 2（task 后端 / 前端）。

---

## 验证命令

见主计划「验证命令」节。本计划相关：

```bash
# 后端用例（catalog 断言已并入 extractor 测试文件）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskExecutionSummaryExtractorTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskProgressControllerExecutionsTest

# 前端用例
node --test src/test/js/taskRecordsSemantics.test.js

# 缓存键回归（改 index.html 后必跑）
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js

# app.js 语法检查
node --check src/main/resources/static/app.js
```

> ⚠️ `verify.sh` 只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件，不可作为本计划门禁。（来源: K-js-test-invocation-surface）

---

## 验收标准

- **I1-1**：`grep -n "allowedTaskTypes" src/main/kotlin/.../TaskProgressController.kt` 的赋值行含 `TaskTypeCatalog`；`sed -n '/id="view-tasks"/,/<\/section>/p' src/main/resources/static/index.html | grep -c 'option value="[A-Z]'` 在 `#taskTypeFilter` 段内为 **0**（状态下拉的 5 个大写 option 属 S1-2 允许，断言须限定在 `#taskTypeFilter` 的起止行内）。
- **I1-2**：JS 用例断言 `metricLabel` 为 null 时输出含 `— 无统计` 且不含 `0/0`。
- **I1-3**：`TaskExecutionSummaryExtractorTest` 的 RUNNING 用例通过（`resultSummary = null` + progress_log 有值 → 非零）。
- **I1-4**：`grep -c "when (taskType)" src/main/kotlin/.../TaskProgressController.kt` 为 **0**。
- **I1-5**：`grep -n "require(" src/main/kotlin/.../TaskExecutionController.kt` 的命中中，**无一处**在 `/{id}/detail` 方法体内；用例断言任意 taskType 均返回 200。
- **I1-6**：用例构造 > 32 KB 的 `resultSummary`，断言响应 `rawResultSummary.length == 32768` 且 `rawTruncated == true`。
- **I1-7**：`/task-types` 用例中构造一个 catalog 未声明的 taskType 行，断言它**出现在**返回列表中且 `label` 等于原 code。
- **S1-1 / S1-2**：`git diff src/main/resources/static/index.html` 的 `view-tasks` 段与契约骨架逐字一致。
- **S1-3 / S1-4**：JS 用例逐字断言两段 DOM。
- **S1-*（共同）**：本计划的 commit 中**不含对 `styles.css` 的任何规则块增删改**（按本计划自身 commit 范围核对；A1 在同一分支改过 CSS，整文件 diff 不为空是预期）。
- **S1-5 / I1-8**：`grep -c "20260817-v5-task-type-catalog" src/main/resources/static/index.html` 为 **3**；同值在 `batchSendTaskConsoleVisualFix.test.js` 中为 **3**；该测试文件通过。
- 回归：执行主计划「验证命令」节的全量测试命令通过；`TaskProgressControllerExecutionsTest` 的两条 `batchOnly` 用例仍通过（N1-1）。

---

## 人工验收清单

### A1-1: 中文名与动态下拉（Observable outcome 1）

- 前置条件：`task_execution` 至少含 `AUTO_REPLY_ALL`、`EXPERT_ENRICHMENT`、`BOUNCE_COLLECTION`、`MANUAL_INITIAL_OUTREACH` 四种。
- 操作步骤：
  1. 打开「任务记录」，看「任务类型」列。
  2. 展开「全部自动化任务」下拉，逐项查看。
  3. 选中「学术数据补全」，查询。
- 预期结果：
  1. 显示「全量账号自动收信回复」「学术数据补全」「退信收集」「批量首发邮件」，无大写枚举。
  2. 下拉项数 **> 5**（改动前为 5），每项形如 `学术数据补全（37）`，含 `退信收集`、`每日计数重置` 等改动前不存在的选项。
  3. 表格只剩 `EXPERT_ENRICHMENT` 的记录，条数与下拉括号内数字一致。
- 覆盖：Observable outcome 1 / I1-1 / I1-7

### A1-2: 计数列单列语义（Observable outcome 2，**预期已按 2026-08-16 复盘修正**）

- 前置条件：同 A1-1。
- 操作步骤：不加筛选，看「发信统计/成功数」列在四种任务上的显示；再展开 `学术数据补全` 那一行。
- 预期结果：
  - `批量首发邮件` → `10/0 已发送/失败`（数值来自 provider，可信）
  - `单账号轮询自动回复` → 形如 `3/1 已回复/转人工`
  - `学术数据补全` → **`— 无统计`**（灰色，无数字）—— 这是**正确行为**：它的存量 `success_count`/`failure_count` 恒为 0，显示「无统计」比显示 `0/0` 诚实
  - `每日计数重置` → `— 无统计`
  - 展开 `学术数据补全` 后，明细区显示真实的补全成功/失败数（由 extractor 三级取数产出）
  - 语义标签为灰色小字，与数值在**同一列同一格**内
- 覆盖：Observable outcome 2 / I1-2 / S1-3

### A1-11: 缓存键（I1-8）

- 前置条件：改动已构建部署。
- 操作步骤：查看网页源代码里三条 `?v=` 的值；不清缓存正常刷新后进「任务记录」。
- 预期结果：三条值均为 `20260817-v5-task-type-catalog` 且完全相同；不清缓存也能看到中文任务名。
- 覆盖：I1-8 / S1-5

### A1-3: 任意行都能展开（Observable outcome 3）

- 前置条件：同 A1-1。
- 操作步骤：依次点开四种任务各一行。
- 预期结果：四行全部展开出内容。`EXPERT_DISCOVERY` 行（若有）显示按数据源分组的表格；其余至少显示「请求参数」+「执行结果」两个等宽 JSON 块。**任何一行都不出现「暂无明细」。**
- 覆盖：Observable outcome 3 / I1-5

### A1-4: 运行中的任务不再显示 0（I1-3）

- 前置条件：发起一次批量发送任务（额度设 5 封以上，保证有足够运行时间）。
- 操作步骤：任务**运行中**打开「任务记录」，找到该行（状态为「执行中」），展开。
- 预期结果：计数列显示实时的非零已发送数；展开区的聚合指标同样非零。**不出现全 0。**
- 覆盖：I1-3 / M-2

### A1-5: 超长结果的截断标注（I1-6）

- 前置条件：存在一条 `AUTO_REPLY_ALL` 执行，其 `result_summary` 超过 32 KB（可由一次多账号多回复的轮询自然产生，或手工 UPDATE 构造）。
- 操作步骤：展开该行，滚到「执行结果」块底部。
- 预期结果：JSON 块被截断；其下方有灰色文案「内容过长已截断，完整内容见服务端日志」。浏览器不卡顿。
- 覆盖：I1-6 / S1-4

### A1-6: 状态下拉补齐（Observable outcome 4）

- 前置条件：存在至少一条 `PARTIAL_SUCCESS` 或 `CANCELLED` 的执行（可取消一次批量发送任务构造 `CANCELLED`）。
- 操作步骤：展开「全部执行状态」下拉；选中「已取消」，查询。
- 预期结果：下拉含「执行中 / 执行成功 / 部分成功 / 执行失败 / 已取消」五项；选「已取消」后能筛出改动前无法筛出的记录。
- 覆盖：Observable outcome 4 / S1-2

### A1-7: 回归 —— 批量任务进度弹窗批次明细（N1-1）

- 前置条件：发起一次批量发送任务。
- 操作步骤：运行中打开进度弹窗，切「批次明细」标签。
- 预期结果：逐批展示，无重复批次行，与改动前一致。
- 覆盖：N1-1 / K-progress-log-batchonly-two-readers

### A1-8: 回归 —— 任务历史记录弹窗形状（N1-2）

- 前置条件：存在 `EXPERT_ENRICHMENT` 的历史执行。
- 操作步骤：在「专家联系」页打开该任务的历史记录列表。
- 预期结果：每行的开始时间格式仍为 `yyyy-MM-dd HH:mm:ss`，处理/通过/拒绝三个数字与改动前一致，耗时秒数正常。
- 覆盖：N1-2

### A1-9: 回归 —— 轮询日志弹窗（N1-3）

- 前置条件：任意。
- 操作步骤：点顶部「轮询日志」。
- 预期结果：与改动前一致（本计划虽让记录页不再调 `/recent-polls/{id}/detail`，但该端点与弹窗本身未改）。
- 覆盖：N1-3

### A1-10: 回归 —— 任务启动按钮文案（N1-7）

- 前置条件：任意。
- 操作步骤：在「专家联系」页查看「发现专家」下拉与「批量发送」「检查回复」按钮。
- 预期结果：按钮文案与改动前完全一致（`taskButtonMapping` 未被合并进 catalog）。
- 覆盖：N1-7

---

## 知识回写（Phase 6）

- **新增** `docs/knowledge/task/K-task-type-semantics-three-lists.md`：M-3 的三份硬编码名单全集（`index.html:940` 5 项 / `TaskProgressController:33` 6 项 / `app.js:678` 6 项，交集 1 项、并集 10 项、实际 16 种），以及「`taskButtonMapping` 管启动按钮、catalog 管记录展示，二者刻意不合并」这一边界。
- **新增** `docs/knowledge/task/K-metric-label-not-reflection.md`：`TaskResultSummary.from()` 的反射名单（成功侧 5 词 / 失败侧 3 词）与它对 `EXPERT_ENRICHMENT`（`enriched`/`failed`）全不命中导致 `0/0` 的机制；展示侧一律以 catalog 的 `metricLabel` 为准，null 即「无统计」，禁止编造语义。
- **更正** `docs/knowledge/task/K-allowedTaskTypes-whitelist.md`：白名单已改为 catalog 派生，「新增类型要改三处」的结论作废，改为「在 catalog 加一条即可；`hasProgressUi` 决定是否进白名单，`summaryRule` 决定是否有结构化提取」。`created` 重置为 2026-08-16（re-validated）。
- **命中续期**：`K-execution-detail-running-needs-progress-log`、`K-progress-log-batchonly-two-readers`、`K-custom-exception-http-status-mapping`、`K-review-event-audit-payload-bounds`、`K-mail-body-display-sites`。
