# 批量执行日志：执行过程可见性（第一期）

> 计划类型：单期可独立交付与验证。第二期（手动执行可观测性）见同目录
> `batch-manual-execution-observability-p2.md`，依赖本期产出的 `ExecutionProgressRow` DTO。

## 需求描述

### Observable outcome

1. 在「批量邮件任务控制台 → 定时任务 → 日志」抽屉中，操作员能看到一次执行的**完整过程时间线**：
   初始化行、每轮批次行、终态行，每行带**真实时间**、状态、消息文本（如「批量发送已暂停：无可用邮箱账号，请检查并恢复账号。」）与错误样例。
2. 一次「一封都没发出去」的执行（空快照 / 无可用账号 / 达上限），操作员能在时间线终态行直接读到**终止原因**，不需要查库或看服务端日志。
3. 执行**进行中**打开日志抽屉时，目标数、跳过数、失败原因分布、跳过原因分布是当前真实值，而不是全 0。

### What must NOT change

- `TaskProgressController.getProgressLogs` 的 `batchOnly` 语义：`batchOnly=true` 仍然只返回
  `batchNumber > 0` 且按批次去重的行。该行为由 `TaskProgressControllerExecutionsTest`
  第 275 行 / 第 301 行两条用例断言，且被 `task-modal-runtime.js:130`（通用任务进度弹窗）依赖。
  本计划不修改该 Controller。
- `GET /api/mail/batch-send/configs/{id}/executions/{executionId}` 的归属校验：
  `execution.batchConfigId != id` 仍返回 404。
- 执行完成后 `resultSummary` 仍是聚合指标（target/success/failure/skipped/remaining/reasons）的权威来源。
- `TaskProgressStore` 的并发语义：`tryStartWithToken` 的单槽占位、`update` 的
  `expectedExecutionId` 陈旧拒绝、`clearExecutionContext` 的身份校验，全部保持原样。
- 批量发送的任何写路径（发信、contact 状态流转、mail_record、ES 回写）不得改动。

### Out of scope（显式延后）

- 手动执行（独立执行）的日志入口与实时进度面板 → 第二期。
- 逐封收件明细（专家 / 账号 / SMTP 错误的结构化列表）、`mail_record.task_execution_id` → 第三期。
- `task_progress_log` 的写放大治理与保留窗口（每封一行、`detailsJson` 内嵌完整 accounts 数组）→ 第三期。
- 通用任务进度弹窗（`openTaskModal` / `renderBatchTable`）的批次表展示改造。
- `renderBatchSendAccountTable` 引用的 `#batchSendProgressPanel` 已不在 `index.html` 中（DOM 已随旧对话框移除，函数第一行 `if (!panel || !tbody) return;` 直接短路）——本期不修复该悬空引用，仅在审计中记录。

---

## 关键不变量

### Invariant I-1: 进度行三分类与保序

- Rule: 一次执行的进度行按 `id` 升序返回，并分成三类：
  - `INIT`：`batchNumber == 0` 且是该执行 `id` 最小的一条；
  - `ROUND`：`batchNumber > 0`，同一 `batchNumber` **只保留 `id` 最大的一条**；
  - `FINAL`：其余 `batchNumber == 0` 的行。
  `batchNumber == 0` 的行**不得被丢弃**——它们承载初始化文案与终止原因，是本期需求 2 的唯一数据来源。
- Applies to: `BatchSendConfigController.getConfigExecutionDetail`（当前 `BatchSendConfigController.kt:106-110`
  的 `.filter { it.batchNumber > 0 }` 是唯一违反点）。
- Violation consequence: 终止原因不可见，需求 2 不成立；或每轮出现数十条重复行淹没时间线。
- 来源: original

### Invariant I-2: 终止原因只从结构化字段读取

- Rule: `stopReason` 只从 `TaskProgressLog.detailsJson` 的 `stopReason` 键读取（该键由
  `ManualInitialOutreachService.updateProgressWithAccumulator` 在 `stopReason != null` 时写入）。
  **禁止**从 `message` 文本做正则/子串解析反推终止原因。
- Applies to: `BatchSendConfigController` 中构造 `ExecutionProgressRow` 的映射逻辑。
- Violation consequence: `stopReasonMessage()` 的文案一旦调整，前端展示的原因即静默错位。
- 来源: original

### Invariant I-3: 运行中聚合指标的取数优先级

- Rule: `BatchConfigExecutionDetail` 的 `target / success / failure / skipped / remaining /
  failureReasons / skippedReasons` 按固定优先级取数：
  1. `execution.resultSummary`（非空 → 权威，终态使用）；
  2. 该执行**最新一条** `task_progress_log` 的 `detailsJson`
     （键：`sentTotal` / `failedTotal` / `skippedTotal` / `pending` / `failureReasons` / `skippedReasons`）
     与该行的 `totalCount`（→ `target`）；
  3. 都缺失时才回落 `execution.successCount / failureCount`，其余为 0。
  **禁止**在 1、2 均缺失时把 0 当作"已知为零"对外表达。
- Applies to: `BatchSendConfigController.toDetail` / `parseOutcome`。
- Violation consequence: 运行中面板全 0，需求 3 不成立；且触发 I-4 的误报。
- 来源: original

### Invariant I-4: 完整性告警只在终态计算

- Rule: "成功+失败+跳过+剩余 == 目标" 的一致性告警只在 `status` 为终态
  （非 `RUNNING`、非 `CANCELLING`）时计算并展示。
- Applies to: 前端 `renderIntegrityWarning`（`app.js:13881-13891`）。
- Violation consequence: 运行全程恒显红字"统计待核对"（因为运行中 `target` 曾恒为 0），
  真实的数据不一致被噪声淹没。
- 来源: original

### Invariant I-5: 进度行时间字段的唯一来源

- Rule: 进度行的时间只能来自 `TaskProgressLog.createdAt`。`TaskProgressLog`
  （`task/domain/TaskProgressLog.kt:24`，表定义 `V22__create_task_progress_log.sql:15`）
  **没有** `updatedAt` / `startedAt` 字段，前端不得读取或兜底这两个名字。
- Applies to: 前端 `renderBatchTimeline`（当前 `app.js:13952` 读 `b.updatedAt || b.startedAt`，
  两者恒为 `undefined`，时间列恒显 `—`）。
- Violation consequence: 时间线无时间，无法定位每轮进展（本期需求 1 的核心诉求）。
- 来源: original（现网 bug）

### Invariant I-6: pendingToken 行的归属修正必须幂等且不阻断启动

- Rule: `TaskProgressStore.tryStartWithToken` 落的首行（INIT）持久化时
  `task_execution_id = pendingToken`（`-System.nanoTime()`，负值），**不是**真实 executionId
  （`TaskProgressStore.kt:145-156`；`bindExecutionId` 只改内存槽，不回写日志行）。
  `bindExecutionId` 绑定成功后必须把该执行下所有 `task_execution_id = pendingToken`
  的行改写为真实 executionId。该改写：
  - 以负值 `pendingToken` 为条件，**不得**按 `taskType` 或时间范围批量改写；
  - 失败时只记 WARN 日志并返回，**不得**抛出、不得改变 `bindExecutionId` 的返回值、
    不得阻断任务启动（与 `persistProgressLog` 的 try/catch 容错策略一致）。
- Applies to: `TaskProgressStore.bindExecutionId`（被 6 个任务类型共用：
  `BatchSendControlService:347`、`MailAutomationController:151`、`ExpertDiscoveryController:89/167/236`、
  `ExpertDiscoveryScheduler:47`、`ExpertIndexController:133/172`）。
- Violation consequence:
  未修正 → INIT 行永远查不到（I-1 的 INIT 分类恒为空），且负 id 行在表中永久孤儿堆积；
  抛出未捕获 → 所有批量/发现/复核任务启动失败（P0 级回归）。
- 来源: original（本次 Phase 1b 审计发现，既有知识库无此条）

---

## 样式契约

> 既有样式引用 `file:line`，新增样式逐字给出。执行 agent 只许复制，不许改写。

### S-1: 进度时间线行（`.batch-timeline-row` 结构升级）

- **复用**（不得自造近似样式替代）：
  - `.batch-timeline`（`styles.css:9022`）
  - `.batch-timeline-row`（`styles.css:9023-9024`）
  - `.batch-timeline-batch`（`styles.css:9025`）
  - `.batch-timeline-time`（`styles.css:9026`）
  - `.batch-timeline-count`（`styles.css:9028`）
- **既有 class 就地修改**：`.batch-timeline-status`（`styles.css:9027`）
  - 全部使用点（grep `batch-timeline-status`，共 2 处）：定义 `styles.css:9027`；使用 `app.js:13956`。
    无其他文件、无测试断言该 class。
  - 改动：删除 `flex: 1`（改由新增的 `.batch-timeline-main` 承担弹性宽度）。修改后逐字为：

```css
.batch-timeline-status { color: #64748b; }
```

- **新增**（以下代码块整体追加到 `styles.css` 第 9028 行 `.batch-timeline-count` 规则之后，逐字复制，不得增删属性或改值）：

```css
/* 执行过程时间线：阶段行（INIT/FINAL）、消息与错误 */
.batch-timeline-row { align-items: flex-start; }
.batch-timeline-row.is-phase { background: #f8fafc; }
.batch-timeline-row.is-failed { background: #fef2f2; }
.batch-timeline-phase { min-width: 50px; font-weight: 700; color: #64748b; }
.batch-timeline-main { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 4px; }
.batch-timeline-message { color: #475569; word-break: break-word; }
.batch-timeline-row.is-failed .batch-timeline-message { color: #e11d48; }
.batch-timeline-stop { color: #d97706; font-weight: 600; }
.batch-timeline-errors { margin: 0; padding: 6px 8px; border-radius: 8px; background: #fff7ed; color: #c2410c; font-size: 11px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; }
```

- **DOM 结构**（`renderBatchTimeline` 必须输出此骨架，`ROUND` 行用 `.batch-timeline-batch` +
  文案 `批次 #N`，`INIT`/`FINAL` 行用 `.batch-timeline-phase` + 文案 `初始化`/`结束`）：

```html
<div class="batch-timeline-row is-phase">
  <span class="batch-timeline-phase">结束</span>
  <span class="batch-timeline-time">2026-08-06 11:20:33</span>
  <span class="batch-timeline-main">
    <span class="batch-timeline-status">已暂停</span>
    <span class="batch-timeline-message">批量发送已暂停：无可用邮箱账号，请检查并恢复账号。</span>
    <span class="batch-timeline-stop">终止原因：NO_AVAILABLE_ACCOUNT</span>
    <pre class="batch-timeline-errors">发送失败 (a@b.com): TIMEOUT</pre>
  </span>
  <span class="batch-timeline-count">已处理 0</span>
</div>
```

- **禁止项**：inline style；本契约未声明的新 class；对 `.batch-timeline-batch` /
  `.batch-timeline-time` / `.batch-timeline-count` / `.batch-log-*` / `.batch-reason-*`
  规则块的任何修改。
- **可选元素规则**：`.batch-timeline-stop` 仅当 `stopReason` 非空时输出；
  `.batch-timeline-errors` 仅当 `errors` 非空时输出。二者缺失时不得输出空标签。

### S-2: 状态文案映射扩展（无新样式，纯文案）

- **就地修改** `statusLabel`（`app.js:13962-13968`）。
  全部使用点（grep `statusLabel(`，共 3 处，均在批量日志抽屉内）：
  `app.js:13808`（执行记录下拉）、`app.js:13921`（`renderLogStatusInfo`）、`app.js:13956`（时间线状态列）。
  无 JS 测试断言该函数。
- 新增映射（其余分支与返回 `s || "—"` 的兜底保持不变）：

```javascript
    if (s === "PARTIAL_SUCCESS") return "部分成功";
    if (s === "CANCELLING") return "取消中";
    if (s === "PAUSED") return "已暂停";
    if (s === "INTERRUPTED") return "已中断";
```

---

## 现状审计

### Store: `task_progress_log`（MySQL）

- Schema: `V22__create_task_progress_log.sql`（id, task_type, task_execution_id **NULL 可空**,
  batch_number, status, processed_count, total_count, batch_processed, batch_passed,
  batch_rejected, message TEXT, details_json TEXT, errors_json TEXT, created_at）
  + `V35__add_task_progress_batch_reject_reasons.sql`（batch_reject_reasons_json）。
  索引仅 `idx_tpl_task_type`、`idx_tpl_execution_id`。**无 updated_at 字段**（I-5 依据）。
- Write paths（唯一写入类：`TaskProgressStore.persistProgressLog`，`TaskProgressStore.kt:181-202`，
  被以下 4 处调用）：
  1. `TaskProgressStore.update:39` — 每次进度更新写一行。
     `ManualInitialOutreachService.updateProgressWithAccumulator`（`:1269`）**每发一封邮件调用一次**，
     故批量发送每封邮件落一行，`batchNumber = roundNumber (>0)`。
  2. `TaskProgressStore.requestCancel:98` — 置 `CANCELLING` 时写一行。
  3. `TaskProgressStore.tryStart:140` — 未被批量发送路径使用。
  4. `TaskProgressStore.tryStartWithToken:153` — 写 INIT 行，
     **`task_execution_id = pendingToken`（负值）**，真实 executionId 尚未产生（I-6 依据）。
  - `batchNumber == 0` 的行来自：`tryStartWithToken` 的初始行；
    `ManualInitialOutreachService` 空快照终态（`:185`、`:447`）与最终收尾（`:375`、`:766`）；
    `BatchSendControlService` 的 FAILED 兜底（`:368`、`:387`）。
- Read paths：
  1. `BatchSendConfigController.getConfigExecutionDetail:106` — `findAllByTaskExecutionIdOrderByIdAsc`
     后 `.filter { batchNumber > 0 }.groupBy.map{last}.sortedBy{batchNumber}` ← **本期唯一改动点**。
  2. `TaskProgressController.getProgressLogs:66-73` — 同样的过滤，但受 `batchOnly` 参数控制，
     `batchOnly=false`（默认）已返回全部行。**本期不改**（must NOT change）。
  3. `TaskProgressController.fallbackFromLog:182` — `findTopByTaskExecutionIdOrderByIdDesc`，
     用于 `/executions` 列表汇总。本期不改。
  4. `TaskProgressStore.restoreFromLog:206` — `findTopByTaskTypeOrderByIdDesc`，重启后恢复内存进度，
     把 `RUNNING/CANCELLING` 映射为 `INTERRUPTED`。本期不改（但 S-2 需为该状态补文案）。
- Interaction points:
  - **IP-1**：写路径 1（每封一行，含 `stopReason` / `failureReasons` in `detailsJson`）
    × 读路径 1（详情接口）——本期要打通的主通道。
  - **IP-2**：写路径 4（pendingToken 负 id）× 读路径 1（按真实 executionId 查）——
    当前**断链**，INIT 行不可达（I-6）。
  - **IP-3**：写路径 1 × 读路径 2（通用任务弹窗 `batchOnly=true`）——已被测试锁定，本期不得触碰。

### Store: `task_execution`（MySQL）

- Schema: `V4__create_task_execution.sql` + `V73__add_batch_config_id_to_task_execution.sql`
  （`batch_config_id BIGINT NULL`）。
- Write paths：
  1. `TaskExecutionService.runAndRecordWithResult:85` — 起始写 `status=RUNNING`、`resultSummary=null`。
  2. `TaskExecutionService.runAndRecordWithResult:127` — 结束写终态 + `resultSummary`
     （**`resultSummary` 只在此刻产生**，I-3 依据）。
  3. `TaskExecutionService.runAndRecordWithResult:141` — 异常写 FAILED。
  4. `TaskExecutionService.updateProgressCounts:53` — 运行中实时更新 `success_count/failure_count`
     （被 `ManualInitialOutreachService:313`、`:639` 每次成功后调用）。
  5. `runAndRecord`（无返回值版，:162/:205/:215）— 调度类任务使用。
- Read paths：
  1. `TaskExecutionService.listRecentByBatchConfigId` ← `BatchSendConfigController.listConfigExecutions`。
  2. `TaskExecutionService.getExecution` ← `BatchSendConfigController.getConfigExecutionDetail`。
  3. `TaskExecutionService.sumSuccessCountTodayByBatchConfigId` ← `BatchSendControlService` 的
     dailyCap 闸门（K-batch-send-daily-cap-cross-invocation）——本期只读不改。
  4. `TaskProgressController.getExecutions` / `TaskExecutionController.*`。
- Interaction points:
  - **IP-4**：写路径 2（终态才写 `resultSummary`）× 读路径 2（详情接口按 `resultSummary` 解析）
    → 运行中全 0（I-3 要解决的问题）。写路径 4 是运行中唯一实时的字段。

### 前端样式盘点

- 可复用 class：
  - `.batch-log-drawer` — `styles.css:8652-8664` — 抽屉容器，绝对定位右侧，`width: min(620px, 72%)`。
  - `.batch-log-metrics` / `.batch-log-metric` / `-label` / `-value` / `.is-success` / `.is-failure` / `.is-skipped`
    — `styles.css:8971-8977` — 指标卡片网格（5 列）。
  - `.batch-reason-list` / `.batch-reason-row` / `.batch-reason-count` — `styles.css:8979-8982` — 原因列表。
  - `.batch-log-integrity-warning` — `styles.css:8983` — 完整性告警条。
  - `.batch-timeline*` — `styles.css:9022-9028` — 见 S-1。
- 设计基准 token（实值，取自上述规则块）：
  主蓝 `#2563eb`；正文 `#475569`；强调 `#1e293b`；次要/静默 `#94a3b8`、`#64748b`；
  成功 `#059669`；失败 `#e11d48`；警告 `#d97706`；告警文字 `#c2410c`；
  面板底 `#f8fafc`；告警底 `#fff7ed`；分隔线 `rgba(15, 23, 42, .04)`；边框 `rgba(15, 23, 42, .08)`；
  圆角 10px（卡片/列表）、8px（内嵌块）；小字号 11px、正文 12px、指标值 18px/700。
- DOM 结构约定：日志抽屉 `<aside id="batchExecutionLogDrawer">` 位于
  `index.html:1381-1408`，是 `#batchScheduledPanel`(`:1098`) 与 `#batchManualPanel`(`:1254`) 的**同级兄弟**，
  两个 tab 共用。内部顺序固定：标题 → `#batchLogExecutionSelect` → `#batchLogMetrics` →
  `#batchLogIntegrityWarning` → 失败原因 → 跳过原因 → 错误样例 → `#batchLogTimelineSection` →
  `#batchLogStatusInfo`。
- 改动前基线（`renderBatchTimeline`，`app.js:13944-13959`，逐字）：

```javascript
function renderBatchTimeline(batches) {
    var container = document.getElementById("batchLogTimeline");
    if (!container) return;
    if (!Array.isArray(batches) || batches.length === 0) {
        container.innerHTML = '<div class="batch-timeline-row"><span style="color:#94a3b8;">无批次记录</span></div>';
        return;
    }
    container.innerHTML = batches.map(function(b) {
        var time = b.updatedAt ? formatDateTime(b.updatedAt) : (b.startedAt ? formatDateTime(b.startedAt) : "—");
        return '<div class="batch-timeline-row">' +
            '<span class="batch-timeline-batch">批次 #' + b.batchNumber + '</span>' +
            '<span class="batch-timeline-time">' + escapeHtml(time) + '</span>' +
            '<span class="batch-timeline-status">' + escapeHtml(statusLabel(b.status || "")) + '</span>' +
            '<span class="batch-timeline-count">已处理 ' + (b.batchProcessed || 0) + '</span>' +
            '</div>';
    }).join("");
```

  注：空态用了 inline `style="color:#94a3b8;"`（既有技术债）。本期改造中该空态改为
  `<span class="muted">无执行过程记录</span>`，`.muted` 为全局既有 class。

- 改动前基线（`renderIntegrityWarning`，`app.js:13881-13891`，逐字）：

```javascript
function renderIntegrityWarning(d) {
    var el = document.getElementById("batchLogIntegrityWarning");
    if (!el) return;
    var expected = d.success + d.failure + d.skipped + (d.remaining || 0);
    if (expected !== d.target) {
        el.hidden = false;
        el.textContent = "统计待核对：目标 " + d.target + "，但成功+失败+跳过+剩余=" + expected;
    } else {
        el.hidden = true;
    }
}
```

### 既有测试契约（K-batch-console-regression-contract）

- `src/test/js/expertTagBatchFix.test.js:555-640` 以源码抽取方式运行 `openBatchConfigLogs` /
  `loadBatchLogExecutions` / `loadBatchLogDetail`，并 stub 掉 `renderBatchExecutionDetail`。
  **本期不改这三个函数的签名与身份写入顺序**，该测试不受影响。
- `src/test/kotlin/.../TaskProgressControllerExecutionsTest.kt:275/301` 断言 `batchOnly` 过滤行为
  → 本期不触碰该 Controller，测试不受影响。
- `getConfigExecutionDetail` / `progressBatches` **当前无任何测试覆盖**
  （grep 结果为空）——本期新增覆盖（K-batch-task-config-implementation-evidence：
  旧测试绿灯不能证明新行为已落地）。

---

## 实现方案

### 阶段 A：后端 — 进度行归属修正（I-6）

**A-1. `TaskProgressLogRepository` 新增改写方法**（遵循同模块 `TaskExecutionRepository`
的 `@Modifying @Query` 既有写法）

```kotlin
@Modifying
@Query("UPDATE task_progress_log SET task_execution_id = :executionId WHERE task_execution_id = :pendingToken")
fun rebindPendingExecutionId(pendingToken: Long, executionId: Long): Int
```

**A-2. `TaskProgressStore.bindExecutionId` 在绑定成功后调用 A-1**（I-6）

- 位置：`TaskProgressStore.kt:173-177` 现有 `if (accepted) { ... cancellationFlags ... }` 块内。
- 必须整体包 `try/catch (e: Exception)`，`catch` 内只 `log.warn(...)`，不重抛。
- 不改 `bindExecutionId` 的返回值语义。

### 阶段 B：后端 — 详情接口（I-1 / I-2 / I-3）

**B-1. 新增 `ExecutionProgressRow` DTO**（与 `BatchConfigExecutionDetail` 同文件）

```kotlin
data class ExecutionProgressRow(
    val kind: String,            // INIT | ROUND | FINAL
    val batchNumber: Int,
    val status: String,
    val message: String?,
    val stopReason: String?,
    val processedCount: Long,
    val totalCount: Long,
    val batchProcessed: Int,
    val batchPassed: Int,
    val batchRejected: Int,
    val errors: List<String>,
    val createdAt: java.time.LocalDateTime
)
```

**B-2. `BatchConfigExecutionDetail` 字段替换**

- `progressBatches: List<TaskProgressLog>` → `progressRows: List<ExecutionProgressRow>`。
- 该字段唯一消费者是前端 `renderBatchExecutionDetail`（同计划内一并改），无其他读者、无测试断言，
  故直接改名而非并存，避免留下双事实源。

**B-3. `getConfigExecutionDetail` 行分类**（I-1 / I-2）

- 取 `progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(executionId)`（保持不变）。
- 分类：`zeroRows = logs.filter { it.batchNumber == 0 }`；
  `roundRows = logs.filter { it.batchNumber > 0 }.groupBy { it.batchNumber }.map { it.value.last() }`。
- `INIT` = `zeroRows.firstOrNull()`；`FINAL` = `zeroRows.drop(1)`；`ROUND` = `roundRows`。
- 合并后**按 `id` 升序**排序输出（不是按 `batchNumber`）。
- `stopReason` 从 `detailsJson` 的 `stopReason` 键解析（I-2）；解析失败按 null 处理并 WARN。
- `errors` 从 `errorsJson` 解析为 `List<String>`；解析失败按空列表处理并 WARN。

**B-4. `toDetail` 运行中取数**（I-3）

- 当 `execution.resultSummary` 为空/空白时，读该执行最新一行
  `progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(executionId)`，
  从其 `detailsJson` 取 `sentTotal / failedTotal / skippedTotal / pending / failureReasons / skippedReasons`，
  从该行 `totalCount` 取 `target`。
- 三级回落顺序严格按 I-3；`failureReasons` / `skippedReasons` 的元素结构沿用既有
  `ReasonCount(label, count)`，复用现有 `parseReasonMap`。
- `errorSamples` 在 `resultSummary` 缺失时取该行 `errorsJson`。

### 阶段 C：前端 — 渲染（I-4 / I-5 / S-1 / S-2）

**C-1. `renderBatchExecutionDetail`**：`renderBatchTimeline(d.progressBatches)` →
`renderBatchTimeline(d.progressRows)`（`app.js:13855`）。

**C-2. 重写 `renderBatchTimeline`**（`app.js:13944-13959`），遵守 S-1 骨架、I-5 时间来源：

- 时间：`formatDateTime(r.createdAt)`，**不读** `updatedAt` / `startedAt`。
- `kind === "ROUND"` → `.batch-timeline-batch` + `批次 #N`；
  `kind === "INIT"` → `.batch-timeline-phase` + `初始化` + 行加 `is-phase`；
  `kind === "FINAL"` → `.batch-timeline-phase` + `结束` + 行加 `is-phase`。
- `status` 为 `FAILED` / `CANCELLED` 时行加 `is-failed`。
- 所有服务端文本经 `escapeHtml`（含 `message`、`stopReason`、每条 `error`）。
- 空态：`<div class="batch-timeline-row"><span class="muted">无执行过程记录</span></div>`。

**C-3. `renderIntegrityWarning` 增加终态门禁**（I-4）：函数开头插入

```javascript
    if (d.status === "RUNNING" || d.status === "CANCELLING") { el.hidden = true; return; }
```

**C-4. `statusLabel` 扩展**（S-2）。

**C-5. 样式**：按 S-1 修改 `.batch-timeline-status` 并追加新规则块。

### 阶段 D：测试

**D-1. 新增 `BatchSendExecutionDetailTest.kt`**（Kotlin，`mail/controller` 包，
仿 `BatchSendConfigControllerTest.kt:38` 的 `controller()` 构造 helper 手工装配，
用 mockk/stub 仓储，不起 Spring 上下文）：

- `INIT 与 FINAL 行不被过滤且分类正确`
- `同一 batchNumber 只保留 id 最大的一条`
- `输出按 id 升序而非 batchNumber 升序`
- `stopReason 从 detailsJson 解析而非 message`
- `errorsJson 解析失败时降级为空列表且不抛出`
- `RUNNING 且 resultSummary 为空时 target 与 reasons 取自最新进度行`
- `resultSummary 非空时优先于进度行`
- `batchConfigId 不匹配仍返回 404`（回归，must-NOT-change）

**D-2. 新增 `src/test/js/batchExecutionLogTimeline.test.js`**（`node --test`，
沿用仓库既有 `extractFn` + DOM stub 模式）：

- `renderBatchTimeline 读取 createdAt 且不引用 updatedAt/startedAt`
- `INIT/FINAL 行输出 .batch-timeline-phase 与 is-phase`
- `ROUND 行输出 .batch-timeline-batch`
- `message / stopReason / errors 经过 escapeHtml`
- `stopReason 为空时不输出 .batch-timeline-stop 空标签`
- `renderIntegrityWarning 在 RUNNING 时隐藏`
- `statusLabel 覆盖 PARTIAL_SUCCESS / CANCELLING / PAUSED / INTERRUPTED`

**D-3. `TaskProgressStore` 的 rebind 单测**加入既有
`src/test/kotlin/.../task/service/` 目录下新建 `TaskProgressStoreRebindTest.kt`：

- `bindExecutionId 成功后调用 rebindPendingExecutionId 且参数为 pendingToken 与真实 id`
- `rebind 抛异常时 bindExecutionId 仍返回 true 且不抛出`（I-6 的 fail-safe）
- `绑定被拒绝（token 不匹配）时不调用 rebind`

---

## 变更文件清单

| # | 文件 | 类型 | 改动 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskProgressLogRepository.kt` | 修改 | 新增 `rebindPendingExecutionId`（A-1） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStore.kt` | 修改 | `bindExecutionId` 调用 rebind，try/catch 容错（A-2） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` | 修改 | `ExecutionProgressRow` DTO、行分类、运行中取数（B-1~B-4） |
| 4 | `src/main/resources/static/app.js` | 修改 | `renderBatchExecutionDetail` / `renderBatchTimeline` / `renderIntegrityWarning` / `statusLabel`（C-1~C-4） |
| 5 | `src/main/resources/static/styles.css` | 修改 | `.batch-timeline-status` 就地修改 + 新增规则块（C-5 / S-1） |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt` | 新增 | D-1 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStoreRebindTest.kt` | 新增 | D-3 |
| 8 | `src/test/js/batchExecutionLogTimeline.test.js` | 新增 | D-2 |

共 8 个文件；子系统 2 个（backend task/mail controller 层 + frontend 静态资源）。
**无数据库迁移**（`rebindPendingExecutionId` 只是 UPDATE 语句，不改 schema）。

---

## 验证命令

> 本项目必须使用 JDK 11（zulu-11）；裸 `mvn` 会因 JDK 版本不符构建失败。
> JS 测试由 `exec-maven-plugin` 绑定在 `test` 阶段执行（`pom.xml:186-235`），
> `mvn test` 会一并跑 `node --test src/test/js/*.test.js` 与 `node --check`。

```bash
# 全量测试（回归门禁；含 Kotlin 单测 + node --test + node --check）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的 Kotlin 测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendExecutionDetailTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskProgressStoreRebindTest

# must-NOT-change 回归：batchOnly 契约未被破坏
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskProgressControllerExecutionsTest

# 本计划新增的 JS 测试（单独运行）
node --test src/test/js/batchExecutionLogTimeline.test.js

# must-NOT-change 回归：日志抽屉身份契约未被破坏
node --test src/test/js/expertTagBatchFix.test.js

# app.js 语法检查
node --check src/main/resources/static/app.js

# 空白/换行卫生
git diff --check
```

通过判据：
- Maven 命令退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0, Skipped: 0` 且 `BUILD SUCCESS`。
- `node --test` 退出码 0，输出含 `fail 0`。
- `node --check` 与 `git diff --check` 均无输出、退出码 0。

来源：CLAUDE.md「Commands」章节与项目元信息 `test_command`；JS 部分取自 `pom.xml:186-235` 的
`exec-maven-plugin` 实际参数。

---

## 验收标准

- **I-1**：`BatchSendExecutionDetailTest` 断言 —— 输入含 `batchNumber` 为 `0,1,1,2,0` 的 5 行日志时，
  输出恰为 4 行，`kind` 依次为 `INIT, ROUND, ROUND, FINAL`，`ROUND` 行取到的是各 batchNumber 中 id 最大者，
  整体按 `id` 升序。另 grep 断言 `BatchSendConfigController.kt` 中不再存在
  `.filter { it.batchNumber > 0 }`。
- **I-2**：测试构造一条 `message="第3轮完成"`、`detailsJson={"stopReason":"DAILY_CAP_REACHED"}` 的行，
  断言 `stopReason == "DAILY_CAP_REACHED"`；再构造 `message` 含 "已达到今日发送上限" 但
  `detailsJson` 无 `stopReason` 的行，断言 `stopReason == null`。
- **I-3**：测试断言三级优先级：(a) `resultSummary` 非空 → 取 `resultSummary`；
  (b) `resultSummary` 为空 + 存在进度行 → `target` 等于该行 `totalCount`、`skipped` 等于
  `detailsJson.skippedTotal`、`failureReasons` 非空；(c) 两者皆无 → `target == 0`
  且 `success == execution.successCount`。
- **I-4**：`batchExecutionLogTimeline.test.js` 断言 `renderIntegrityWarning({status:"RUNNING",
  target:0, success:3, failure:0, skipped:0, remaining:0})` 后元素 `hidden === true`；
  同样入参但 `status:"SUCCESS"` 时 `hidden === false`。
- **I-5**：JS 测试断言渲染出的 HTML 含 `formatDateTime(createdAt)` 的结果；
  并 grep 断言 `renderBatchTimeline` 函数体内不含字符串 `updatedAt` 与 `startedAt`。
- **I-6**：`TaskProgressStoreRebindTest` 断言 rebind 被调用一次且入参为
  `(pendingToken, executionId)`；断言 rebind 抛 `RuntimeException` 时 `bindExecutionId`
  仍返回 `true` 且不向外抛出；断言 token 不匹配时 rebind 调用次数为 0。
- **S-1**：`git diff src/main/resources/static/styles.css` 中新增规则块与本契约代码块**逐字一致**；
  `.batch-timeline-status` 修改后逐字为 `.batch-timeline-status { color: #64748b; }`；
  grep 断言 `renderBatchTimeline` 输出中无 `style="` 内联样式；
  JS 测试断言 INIT/FINAL 行含 `is-phase` 与 `.batch-timeline-phase`、ROUND 行含 `.batch-timeline-batch`；
  断言 `stopReason` 为空时输出中不含 `batch-timeline-stop`。
- **S-2**：JS 测试断言 `statusLabel` 对 `PARTIAL_SUCCESS / CANCELLING / PAUSED / INTERRUPTED`
  分别返回 `部分成功 / 取消中 / 已暂停 / 已中断`，且未知值仍返回原值、空值返回 `—`。
- **IP-3 回归（must NOT change）**：执行「验证命令」节的 `TaskProgressControllerExecutionsTest`
  与 `expertTagBatchFix.test.js` 两条命令通过。
- **整体回归**：执行「验证命令」节的全量测试命令通过。

---

## 人工验收清单

### A-1: 一次正常执行的过程时间线可见

- 前置条件：存在一个启用中的定时任务配置（`批量邮件任务控制台 → 定时任务` 列表中任意一行），
  且 ES CANDIDATE 层有 ≥ 3 个未联系、带邮箱的专家；至少一个发件账号可用。
- 操作步骤：
  1. 打开「批量邮件任务控制台」→「定时任务」，点该配置行的「手动」按钮进入手动执行 tab。
  2. 点「确认并执行」，在确认弹窗点确定。
  3. 待执行结束后，回到「定时任务」tab，点该配置行的「日志」按钮。
  4. 在「执行记录」下拉中选中刚才那次执行。
  5. 滚动到「批次时间线」区域。
- 预期结果：
  - 时间线**第一行**左侧为灰底、标签文字 `初始化`，消息列显示 `正在初始化发送队列...`。
  - 中间为若干 `批次 #1`、`批次 #2`… 行。
  - **最后一行**为灰底、标签文字 `结束`，消息列显示终态文案（如 `发送任务已完成`）。
  - **每一行的时间列都是形如 `2026/08/06 11:20:33` 的具体时间，不是 `—`**。
  - 抽屉顶部指标卡片的「目标 / 成功 / 失败 / 跳过 / 耗时」为终态真实值，
    且与实际收到介绍邮件的专家数一致（回归：终态仍以 `resultSummary` 为权威）。
- 覆盖：需求描述 observable outcome 1；What must NOT change 第 3 条；I-1、I-5、S-1、S-2。

### A-2: 一封都没发出去的执行能读到终止原因

- 前置条件：把所有发件账号在「发件账号」管理中停用（或将其今日发送量改到达上限），
  使批量发送启动后无可用账号。
- 操作步骤：
  1. 同 A-1 步骤 1-2 触发一次执行。
  2. 执行结束后打开该配置的「日志」抽屉，选中这次执行。
  3. 查看「批次时间线」最后一行。
- 预期结果：
  - 最后一行标签为 `结束`，状态列显示 `已暂停`，消息列显示
    `批量发送已暂停：无可用邮箱账号，请检查并恢复账号。`
  - 该行下方另起一行黄色文字 `终止原因：NO_AVAILABLE_ACCOUNT`。
  - 时间线**不为空**（改造前此场景时间线显示"无批次记录"）。
- 覆盖：需求描述 observable outcome 2；I-1、I-2、S-1。

### A-3: 运行中的聚合指标是真实值

- 前置条件：ES CANDIDATE 层有 ≥ 30 个可发送专家；配置的「每封间隔」设为 3 秒以上，
  保证有足够时间在运行中观察。
- 操作步骤：
  1. 触发一次执行（同 A-1 步骤 1-2）。
  2. **执行尚未结束时**，回到「定时任务」tab，点该配置的「日志」按钮。
  3. 观察抽屉顶部指标卡片区与「失败原因/跳过原因」两栏。
- 预期结果：
  - 「目标」卡片显示**大于 0** 的数字（本次执行的目标总数），不是 `0`。
  - 「成功」卡片数字随轮询递增。
  - 抽屉顶部**不出现**橙色的「统计待核对：目标 0，但成功+失败+跳过+剩余=N」告警条。
  - 若已有跳过/失败，对应原因栏显示条目而非「无失败原因/无跳过原因」。
- 覆盖：需求描述 observable outcome 3；I-3、I-4。

### A-4: 通用任务进度弹窗的批次表未被破坏（回归）

- 前置条件：无。
- 操作步骤：
  1. 在主界面触发一次「专家发现」或「专家复核」任务（任一走通用任务进度弹窗的任务）。
  2. 在弹窗中展开执行记录行的批次明细表。
- 预期结果：批次明细表仍**只列出批次行**（批次 #1、#2…），**没有**出现 `批次 #0` 行，
  行数与改造前一致。
- 覆盖：需求描述 What must NOT change 第 1 条（`batchOnly` 契约）；IP-3。

### A-5: 配置归属校验未被放宽（回归）

- 前置条件：存在两个不同的定时任务配置 X 和 Y，且 Y 有至少一条执行记录（记下其 executionId）。
- 操作步骤：在浏览器地址栏或 curl 直接请求
  `/api/mail/batch-send/configs/{X的id}/executions/{Y的executionId}`。
- 预期结果：返回 HTTP 404，响应体为空，不返回 Y 的执行详情。
- 覆盖：需求描述 What must NOT change 第 2 条。

### A-6: 任务启动未被 rebind 改造影响（回归）

- 前置条件：无。
- 操作步骤：
  1. 依次触发一次「专家发现」、一次「收取回复（CHECK_REPLIES）」、一次批量发送。
  2. 观察每次触发后是否正常进入运行状态。
- 预期结果：三个任务均正常启动并进入 `运行中`，无「启动失败」提示，服务端无未捕获异常堆栈。
- 覆盖：I-6 的 fail-safe 边界（`bindExecutionId` 被 6 个任务类型共用）。

### A-7: 日志抽屉切换执行记录仍稳定（回归）

- 前置条件：某配置有 ≥ 2 条历史执行记录。
- 操作步骤：打开该配置的「日志」抽屉，在「执行记录」下拉中来回切换两次不同记录。
- 预期结果：每次切换后，指标卡片、原因列表与时间线内容都对应当前所选记录；
  不出现上一条记录的内容残留，不出现内容闪烁后被旧数据覆盖。
- 覆盖：现状审计「既有测试契约」中的抽屉身份契约（K-batch-console-default-log-selection）。

---

## 附：本计划消费的知识条目

| 知识 ID | 用途 | 状态 |
|---|---|---|
| K-batch-console-log-timeline | 时间线必须有明确空态并对服务端文本安全输出 → S-1、C-2 | 已应用 |
| K-batch-console-regression-contract | 改 UI 契约必须同步列出受影响 JS 测试 → 现状审计「既有测试契约」、A-4/A-7 | 已应用 |
| K-batch-console-default-log-selection | 抽屉身份写入顺序不得破坏 → must-NOT-change、A-7 | 已应用（本期不改该逻辑） |
| K-batch-task-config-snapshot-log-identity | execution 的 nullable configId 与配置级日志隔离 → must-NOT-change、A-5 | 已应用 |
| K-batch-task-config-implementation-evidence | 旧测试绿灯不能证明新行为落地 → D-1 新增覆盖 | 已应用 |
| K-clearExecutionContext-status-leak | 进度 store 终态遗留 RUNNING → S-2 补 `INTERRUPTED` 文案 | 部分应用（不改 store 语义） |
| K-manual-outreach-executor-shared | `bindExecutionId` 被多任务共用 → I-6 的 fail-safe 与 A-6 | 已应用 |
| K-allowedTaskTypes-whitelist | 本期不新增任务类型 | 已评估，不适用 |
| K-batch-send-daily-cap-cross-invocation | 本期不触碰 dailyCap 取数 | 已评估，不适用 |

---

## 修正记录

| 日期 | 修订对象 | 修订内容 | 理由 | 决策出处 |
|---|---|---|---|---|
| 2026-08-07 | `## 样式契约` → `### S-1` → 「可选元素规则」 | 可选元素集合由 `{.batch-timeline-stop, .batch-timeline-errors}` 扩展为 `{.batch-timeline-status, .batch-timeline-stop, .batch-timeline-errors}`；`.batch-timeline-status` 的输出条件为 `r.status !== "RUNNING"`。本 S-1 的 DOM 骨架样例（终态行 `已暂停`）仍然有效，仅对 `status === "RUNNING"` 的行不再输出该元素。 | 本期 S-1 用终态行举例，未考虑 INIT 行与全部 ROUND 行的 `status` 恒为 `RUNNING`（写入侧 `ManualInitialOutreachService` 七处硬编码 `"RUNNING"`：`:264/:340/:353/:537/:600/:728/:742`）。无条件输出导致**已完成执行的每一行都渲染为「运行中」**，操作端据此误判任务卡死。 | `docs/plans/2026-08-07/batch-timeline-running-status-render.md`（I-1 / S-1） |
