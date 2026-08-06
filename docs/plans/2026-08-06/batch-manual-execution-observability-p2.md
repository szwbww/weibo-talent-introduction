# 手动执行可观测性：实时过程与日志入口（第二期）

> 依赖：同目录 `batch-execution-log-process-visibility-p1.md`（第一期）已合入。
> 本期复用第一期产出的 `ExecutionProgressRow` DTO、`renderBatchTimeline` 与
> `BatchConfigExecutionDetail` 的运行中取数逻辑（I-3 of P1）。

## 需求描述

### Observable outcome

1. 在「批量邮件任务控制台 → 手动执行」点「确认并执行」后，日志抽屉**自动打开并停在本次执行**，
   无论该次执行是否关联了定时任务配置。（当前：只有关联了配置时才打开；独立执行只弹一条 toast。）
2. 执行进行中时，日志抽屉顶部显示**实时区**：状态徽标、当前轮次、进度条与
   「已处理 N / 约 M」计数、当前动作文案（如「正在发送：a@b.edu」）、各发件账号的成功/失败计数。
   该区域随轮询刷新，执行结束后自动消失。
3. 执行进行中时，操作员可在实时区点「取消执行」终止本次批量发送，并被明确告知
   「将在当前批次结束后停止」。
4. 独立手动执行（未关联定时任务配置）产生的执行记录，可通过日志抽屉查看其
   聚合指标、失败/跳过原因、错误样例与完整过程时间线。

### What must NOT change

- `openBatchConfigLogs(configId, executionId)` 的函数签名与行为：仍按 configId 拉取执行记录列表、
  默认选中首条、先写 `batchTaskState.logConfigId / logExecutionId` 再请求详情、
  迟到响应按当前 configId 校验后丢弃。该行为由
  `src/test/js/expertTagBatchFix.test.js:555-640` 断言（以源码抽取方式运行）。
- `GET /api/mail/batch-send/configs/{id}/executions/{executionId}` 的归属校验：
  `execution.batchConfigId != id` 仍返回 404（K-batch-task-config-snapshot-log-identity）。
- `TaskProgressStore` 的单槽并发语义：`tryStartWithToken` 占位、`requestCancel` 仅在
  `status == "RUNNING"` 且 `executionId != null` 时接受、`isCancelled(taskType, executionId)`
  的按执行判定，全部保持原样。
- 批量发送的启动闸门：`validateSnapshotFields`、`validateTemplateAtLaunch`、
  `checkRemainingAccountCapacity`、dailyCap 校验（`sumSuccessCountTodayByBatchConfigId`）
  的顺序与语义不变。
- 「手动执行」tab 的来源选择、diff 计算、确认弹窗（`showBatchManualConfirm` /
  `computeManualDiffs`）行为不变。
- 批量发送的任何写路径（发信、contact 状态流转、mail_record、ES 回写）不得改动。

### Out of scope（显式延后）

- 逐封收件明细（专家 / 账号 / SMTP 错误的结构化列表）、`mail_record.task_execution_id` → 第三期。
- `task_progress_log` 的写放大治理与保留窗口 → 第三期。
- 通用任务进度弹窗（`openTaskModal` / `launchBatchSendWithProgress` / `task-modal-runtime.js`）
  的任何改造；本期**不**把手动执行接入该弹窗（会在控制台弹窗之上再叠一层模态）。
- 修复 `renderBatchSendAccountTable` 对已移除 DOM `#batchSendProgressPanel` 的悬空引用
  （P1 审计已记录）；本期实时区**另起**账号展示，不复用该函数。
- 独立手动执行的历史记录列表（跨执行浏览）；本期独立执行只支持「按 executionId 直达」。
- 暂停/恢复语义（`pause` / `resumeSchedule` / `pauseSchedule`）与 legacy 运行时状态机。

---

## 关键不变量

### Invariant I-1: live 块的存在性 = 当前正在运行且就是被查执行

- Rule: 执行详情响应中的 `live` 字段非空，**当且仅当**
  `progressStore.getCurrentExecutionId("MANUAL_INITIAL_OUTREACH") == 被查 executionId`。
  该判定只读内存槽（`TaskProgressStore.getCurrentExecutionId`，`TaskProgressStore.kt:132`，
  不走 `restoreFromLog`），因此重启后遗留的 RUNNING 记录不会产生 live 块。
  `execution.status == "RUNNING"` **不是**充分条件（进程重启后 DB 里会残留 RUNNING）。
- Applies to: `BatchSendControlService.getLiveExecutionView`（新增）、
  `BatchSendConfigController.toDetail`（两个详情端点共用）。
- Violation consequence: 查看历史执行时显示上一次/另一次执行的实时进度；
  或重启后永久显示一个不会推进的假进度条。
- 来源: original

### Invariant I-2: 取消的目标由服务端按 executionId 判定

- Rule: `POST /api/mail/batch-send/executions/{executionId}/cancel` 必须先校验
  `progressStore.getCurrentExecutionId("MANUAL_INITIAL_OUTREACH") == executionId`，
  不相等返回 409 且**不调用** `requestCancel`。
  前端**禁止**直接调用 taskType 级的 `POST /api/task-progress/MANUAL_INITIAL_OUTREACH/cancel`
  ——该接口不带 executionId，无法保证取消的是操作员正在看的那次执行。
- Applies to: `BatchSendControlService.cancelExecution`（新增）、前端取消按钮。
- Violation consequence: 操作员在看执行 101 的日志，点取消却终止了刚启动的执行 102。
- 来源: original

### Invariant I-3: 独立手动执行的日志路由

- Rule: `batchConfigId == null` 的执行只能通过 `GET /api/mail/batch-send/executions/{executionId}`
  访问；配置级路由 `/configs/{id}/executions/{executionId}` 的 404 归属校验不得放宽为
  「configId 为空时跳过校验」。
  新端点**不做** configId 校验，但必须校验 `execution.taskType == "MANUAL_INITIAL_OUTREACH"`，
  不得成为任意 task_execution 的通用读取口。
- Applies to: `BatchSendConfigController` 新增的两个 execution 级端点。
- Violation consequence: 配置 X 的日志抽屉能读到配置 Y 的执行详情（越权），
  或该端点被用来读取专家发现/复核等无关任务的执行体。
- 来源: K-batch-task-config-snapshot-log-identity

### Invariant I-4: 抽屉身份先写后请求，迟到响应按身份丢弃

- Rule: 任何打开/切换日志目标的入口（`openBatchConfigLogs`、新增的
  `openBatchExecutionLogs`）必须**先**写入 `batchTaskState.logConfigId` 与
  `batchTaskState.logExecutionId`，**再**发起请求；异步响应回来后必须确认
  这两个值仍等于本次请求的目标，不等则整体丢弃（不渲染、不启定时器）。
  轮询定时器的回调同样要做这一校验。
- Applies to: `openBatchConfigLogs`（现有，行为不变）、`openBatchExecutionLogs`（新增）、
  `loadBatchLogDetail`（扩展）、实时区轮询回调。
- Violation consequence: 切换执行记录后旧响应覆盖新内容；关闭抽屉后定时器仍在写 DOM。
- 来源: K-batch-console-default-log-selection

### Invariant I-5: 进度百分比不得伪造

- Rule: 实时区的百分比只能是 `processedCount / totalCount`（服务端
  `TaskProgress.percentage`，`TaskProgressStore.kt:264-265`，`totalCount <= 0` 时为 0）。
  `totalCount` 是 ES 命中数 + 可重试联系人的**估算值**（`ManualInitialOutreachService`
  的 `totalEstimate`），因此 UI 文案必须写成 `已处理 N / 约 M`，把估算性质显式暴露。
  **禁止**用耗时、轮询次数、事件数或任何时间外推估算完成率；
  **禁止**在 `totalCount == 0` 时展示一个非 0 的百分比。
- Applies to: `BatchSendControlService.getLiveExecutionView`、前端实时区渲染。
- Violation consequence: 进度条倒退或停在 99%，操作员据此误判任务已近完成。
- 来源: K-ai-stream-progress-no-fake-percent

### Invariant I-6: 来源配置身份在执行与日志间保持一致

- Rule: 手动执行携带 `sourceConfigId` 时，日志抽屉必须以该 configId 打开
  （走 `openBatchConfigLogs`，从而带出该配置的历史执行列表）；
  `sourceConfigId` 为 null 时才走 `openBatchExecutionLogs`。
  判定依据只能是 `batchTaskState.manualSource.id`，不得改为读取表单 DOM
  或按名称/顺序推断。
- Applies to: `confirmManualExecution`（`app.js:13615-13653`）。
- Violation consequence: 配置级执行被降级为独立执行的日志视图，历史记录列表丢失
  （K-batch-console-source-identity 记录过同类回归）。
- 来源: K-batch-console-source-identity

### Invariant I-7: 轮询生命周期与抽屉生命周期绑定

- Rule: 实时区轮询与详情轮询共用 `batchTaskState.logRefreshTimer` 这一个定时器句柄；
  在 `closeBatchLogDrawer`、`switchBatchSendTab`、`closeBatchSendTaskModal`、
  `resetBatchTaskState` 四处必须被清理（现有 `clearBatchLogRefreshTimer` 已在这四处调用，
  不得新增第二个未纳管的定时器）。
  详情返回终态（非 `RUNNING`/`CANCELLING`）时必须清理定时器并隐藏实时区。
- Applies to: `loadBatchLogDetail`、`clearBatchLogRefreshTimer` 的全部调用点
  （`app.js:12712`、`12737`、`12773`、`13778`、`13785`、`13833`、`13840`）。
- Violation consequence: 关闭抽屉/弹窗后定时器泄漏，持续请求并向已隐藏的 DOM 写入。
- 来源: K-shared-action-dialog-cleanup（共用弹窗必须成对 setup/cleanup）

---

## 样式契约

> 既有样式引用 `file:line`，新增样式逐字给出。执行 agent 只许复制，不许改写。

### S-1: 日志抽屉实时区（新增 DOM 块）

- **复用**（不得自造近似样式替代）：
  - `.task-progress-track`（`styles.css:3181-3188`）、`.task-progress-fill`（`styles.css:3190-3195`）
    —— 进度条轨道与填充。
  - `.badge` / `.badge ok` / `.badge warn` —— 状态徽标（全局既有 class）。
  - `.button small danger` —— 取消按钮（既有用法见 `app.js:12849` 的删除按钮）。
  - `.batch-log-drawer`（`styles.css:8652-8664`）—— 实时区所在的抽屉容器，不修改。
- **新增**（以下代码块整体追加到 `styles.css` 第 8983 行 `.batch-log-integrity-warning`
  规则之后，逐字复制，不得增删属性或改值）：

```css
/* 执行日志抽屉：运行中实时区 */
.batch-log-live { margin: 0 0 14px; padding: 12px; border: 1px solid rgba(37, 99, 235, .28); border-radius: 10px; background: #f8fafc; }
.batch-log-live-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.batch-log-live-round { flex: 1; min-width: 0; color: #64748b; font-size: 12px; }
.batch-log-live-counts { margin-top: 8px; color: #1e293b; font-size: 12px; font-weight: 600; }
.batch-log-live-message { margin-top: 6px; color: #475569; font-size: 12px; word-break: break-word; }
.batch-log-live-accounts { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.batch-log-live-account { padding: 3px 8px; border: 1px solid rgba(15, 23, 42, .08); border-radius: 999px; background: #fff; color: #475569; font-size: 11px; }
.batch-log-live-account.is-failing { border-color: rgba(225, 29, 72, .35); color: #e11d48; }
.batch-log-live-hint { margin-top: 8px; color: #94a3b8; font-size: 11px; }
```

- **DOM 结构**（逐字插入 `index.html`，位置：`<select id="batchLogExecutionSelect">`
  结束标签之后、`<div id="batchLogMetrics">` 之前，即当前 `index.html:1388` 与 `1389` 之间）：

```html
<div id="batchLogLive" class="batch-log-live" hidden>
    <div class="batch-log-live-head">
        <span class="badge ok" id="batchLogLiveStatus">运行中</span>
        <span class="batch-log-live-round" id="batchLogLiveRound"></span>
        <button class="button small danger" id="batchLogLiveCancelBtn" type="button">取消执行</button>
    </div>
    <div class="task-progress-track"><div class="task-progress-fill" id="batchLogLiveFill"></div></div>
    <div class="batch-log-live-counts" id="batchLogLiveCounts"></div>
    <div class="batch-log-live-message" id="batchLogLiveMessage"></div>
    <div class="batch-log-live-accounts" id="batchLogLiveAccounts"></div>
    <div class="batch-log-live-hint">取消后将在当前批次结束时停止，已发出的邮件不会撤回。</div>
</div>
```

- **账号 chip 骨架**（`#batchLogLiveAccounts` 的 innerHTML，每账号一个）：

```html
<span class="batch-log-live-account">a@example.com 成功 12 / 失败 0</span>
```

  失败数 > 0 时该 chip 追加 `is-failing`。

- **禁止项**：inline `style="..."` 属性；本契约未声明的新 class；
  对 `.task-progress-track` / `.task-progress-fill` / `.batch-log-metrics` /
  `.batch-reason-*` / `.batch-timeline-*` 规则块的任何修改。
- **唯一例外（显式许可）**：进度条填充宽度由 JS 通过
  `document.getElementById("batchLogLiveFill").style.width = pct + "%"` 设置，
  沿用仓库既有写法（`app.js:1104`）。除此之外不得用 JS 写任何行内样式。

### S-2: 抽屉标题与执行记录下拉的独立执行形态

- **就地修改** `index.html:1383` 的抽屉标题元素，仅新增 `id`，不改 class 与内联样式：

```html
<h3 id="batchLogDrawerTitle" style="margin:0;font-size:15px;">执行日志</h3>
```

  （该元素原本就带内联 style，属既有技术债，本期**保持原样**不清理，避免无关改动。）

- **复用** `.bsc-input.bsc-select`（`#batchLogExecutionSelect`，`index.html:1386`）：
  独立执行时对该 select 设置 `hidden = true`，**不新增样式**、不改其 class。
- 文案实值：
  - 配置级执行：标题 `执行日志`，select 可见。
  - 独立手动执行：标题 `执行日志（独立执行）`，select 隐藏。

---

## 现状审计

### Store: `task_execution`（MySQL）— 本期新增读取口

- Schema: `V4__create_task_execution.sql` + `V73__add_batch_config_id_to_task_execution.sql`
  （`batch_config_id BIGINT NULL`）。独立手动执行写入 `batch_config_id = NULL`。
- Write paths（本期均不改动）：
  1. `TaskExecutionService.runAndRecordWithResult:85/127/141` — 起始 RUNNING / 终态+resultSummary / 异常 FAILED。
  2. `TaskExecutionService.updateProgressCounts:53` — 运行中实时刷新 success/failure。
  3. `batchConfigId` 的来源：`BatchSendControlService.launchFromSnapshot(batchConfigId = ...)`；
     `startManual(request)` 传 `request.sourceConfigId`（可为 null，**独立执行**）；
     `startManualFromConfig(configId)` / `startScheduled(configId)` 传具体 id；
     legacy KV 路径 `launchLegacyKv` 传 `null`。
- Read paths:
  1. `BatchSendConfigController.listConfigExecutions` → `listRecentByBatchConfigId`（按 configId）。
  2. `BatchSendConfigController.getConfigExecutionDetail` → `getExecution(id)` + configId 归属校验。
  3. `TaskProgressController.getExecutions`（按 taskType，白名单含 `MANUAL_INITIAL_OUTREACH`）。
  4. `TaskExecutionController.listExecutions / getExecution`。
- **Interaction point IP-1**：写路径 3（`batchConfigId = null`）× 读路径 1/2（全部按 configId 索引）
  → **独立手动执行的 task_execution 行没有任何可达的读取口**。这是本期需求 1、4 的根因。
  本期新增按 executionId 的读取口消解该断链（I-3 限定其边界）。

### Store: `TaskProgressStore` 内存槽（非持久化）

- 结构：`ConcurrentHashMap<taskType, TaskProgress>` + `cancellationFlags<"taskType:executionId", Boolean>`
  （`TaskProgressStore.kt:19-20`）。**单槽**：同一 taskType 同时只有一个执行占位。
- Write paths:
  1. `tryStartWithToken:145` — 占位，`executionId = -System.nanoTime()`（负 pendingToken）。
  2. `bindExecutionId:158` — 换成真实 executionId（P1 已在此加日志行归属修正）。
  3. `update:22` — 每次进度更新，带 `expectedExecutionId` 陈旧校验。
  4. `requestCancel:81` — 仅当 `status == "RUNNING"` 且 `executionId != null` 才接受，
     置 `CANCELLING` 并写 `cancellationFlags["$taskType:$executionId"] = true`。
  5. `clearExecutionContext:57` — 执行结束把 `executionId` 置 null（**不清 status**，
     K-clearExecutionContext-status-leak）。
- Read paths:
  1. `get(taskType)` — 内存未命中时会 `restoreFromLog` 并把 RUNNING 映射为 `INTERRUPTED`。
     **本期不使用该方法判定 live**（会把重启后的残留读成活的）。
  2. `getCurrentExecutionId(taskType):132` — **只读内存槽**，正是 I-1 需要的语义。
  3. `isCancelled(taskType, executionId):108` — 发送循环每轮检查
     （`ManualInitialOutreachService:205`、`:475`）。
  4. `BatchSendControlService.getStatus:268` — legacy 状态视图，按 `details.sendType` 过滤。
- **Interaction point IP-2**：写路径 4（`requestCancel` 只认 taskType）×
  本期新增的按 executionId 取消入口 → 必须由 I-2 的服务端校验补齐身份，
  否则取消目标可能不是操作员所看的执行。
- **Interaction point IP-3**：`clearExecutionContext` 把 executionId 置 null（写路径 5）×
  `getCurrentExecutionId`（读路径 2）→ 执行结束瞬间 live 自然变为 null，
  实时区自动消失，无需额外清理逻辑。这是 I-1 选择该判据的原因之一。

### 现有执行链路（本期不改，仅确认边界）

- `POST /api/mail/batch-send/manual-executions` → `BatchSendControlService.startManual(request)`
  （`BatchSendControlService.kt:86-110`）→ `launchFromSnapshot(..., manageRuntimeStatus = 默认 false)`。
  **独立手动执行不参与 legacy 运行时状态机**，因此取消它不需要同步 runtime status。
- legacy typed 路径 `/types/{sendType}/manual` → `runManualOnce` →
  `launchFromSnapshot(manageRuntimeStatus = true)`。若本期新增的取消端点作用于此类执行，
  执行返回后 `applyResultToRuntimeStatus`（`:458-491`）会因 `result.wasCancelled == true`
  把 runtime status 落到 `PAUSED`，语义自洽，**无需**在取消端点里另做状态处理。
- `launchFromSnapshot` 返回体已含 `executionId`（`:397-404`，`executionIdFuture.get(5s)`），
  超时时该字段缺失 → 前端必须处理 `executionId == null` 的降级（提示已启动但无法定位日志）。

### 前端现状

- `confirmManualExecution`（`app.js:13615-13653`）：成功后
  `showStatus("执行已启动 executionId: ...")`，随后 **`if (source)` 才** `openBatchConfigLogs(source.id, response.executionId)`。
  ← 需求 1 的直接缺口。
- `openBatchConfigLogs(configId, executionId)`（`app.js:13773-13780`）：
  写 `logConfigId` / `logExecutionId` → 显示抽屉 → `clearBatchLogRefreshTimer()` →
  `loadBatchLogExecutions(configId, executionId)`。**被 JS 测试以源码抽取方式运行，签名不可改。**
- `loadBatchLogDetail(configId, executionId)`（`app.js:13827-13847`）：
  请求 `/configs/{cfg}/executions/{eid}` → `renderBatchExecutionDetail(detail)` →
  `detail.status === "RUNNING"` 时以 **3000ms** 起轮询，回调内校验
  `logConfigId === configId && logExecutionId === executionId`。
- `batchTaskState`（`app.js:12677-12692`）：含 `logConfigId` / `logExecutionId` /
  `logRefreshTimer` / `manualSource` / `manualDraft`。
- `clearBatchLogRefreshTimer` 调用点（共 7 处）：`12712`（关弹窗）、`12737`（reset state）、
  `12773`（切 tab）、`13778`（打开日志）、`13785`（关抽屉）、`13833`、`13840`（详情轮询自管）。
- `switchBatchSendTab`（`app.js:12764-12782`）：每次切 tab 都调用
  `closeBatchLogDrawer()` + `clearBatchLogRefreshTimer()`。
  → 手动执行后打开的抽屉，若操作员切回「定时任务」tab 会被关闭；这是既有行为，本期**保留**。
- `#batchExecutionLogDrawer`（`index.html:1381-1408`）是 `#batchScheduledPanel`(`:1098`) 与
  `#batchManualPanel`(`:1254`) 的**同级兄弟**，绝对定位于弹窗右侧，两 tab 共用
  → 在手动 tab 打开抽屉无需新增容器。

### 前端样式盘点

- 可复用 class：
  - `.task-progress-track` — `styles.css:3181-3188` — `height: 6px; background: var(--surface);
    border-radius: 3px; overflow: hidden; border: 1px solid var(--panel-border);`
  - `.task-progress-fill` — `styles.css:3190-3195` — `height: 100%; background-color: var(--primary);
    border-radius: 3px; transition: width 0.3s ease;`
  - `.batch-log-drawer` — `styles.css:8652-8664`。
  - `.batch-log-metrics` / `.batch-log-metric*` — `styles.css:8971-8977`。
  - `.batch-log-integrity-warning` — `styles.css:8983`（新增块的插入锚点）。
  - `.badge` / `.badge ok` / `.badge warn`、`.button small danger`、`.bsc-input.bsc-select`、`.muted` — 全局既有。
- 设计基准 token（实值）：
  主蓝 `#2563eb`（实时区边框用其 28% 透明：`rgba(37, 99, 235, .28)`）；
  正文 `#475569`；强调 `#1e293b`；次要 `#64748b`；静默 `#94a3b8`；
  失败 `#e11d48`（chip 边框用 `rgba(225, 29, 72, .35)`）；
  面板底 `#f8fafc`；卡片底 `#fff`；通用边框 `rgba(15, 23, 42, .08)`；
  圆角：区块 10px、chip 999px、进度条 3px；字号：正文 12px、辅助 11px。
- DOM 结构约定：抽屉内部顺序固定为
  标题 → `#batchLogExecutionSelect` → `#batchLogMetrics` → `#batchLogIntegrityWarning` →
  失败原因 → 跳过原因 → 错误样例 → `#batchLogTimelineSection` → `#batchLogStatusInfo`。
  本期在 select 与 metrics 之间插入 `#batchLogLive`，其余顺序不变。
- 改动前基线（`confirmManualExecution` 的成功分支，`app.js:13644-13648`，逐字）：

```javascript
        closeBatchManualConfirmDialog();
        showStatus("执行已启动 executionId: " + (response.executionId || "—"), "ok");
        if (source) {
            openBatchConfigLogs(source.id, response.executionId);
        }
```

- 改动前基线（`loadBatchLogDetail`，`app.js:13827-13847`，逐字）：

```javascript
async function loadBatchLogDetail(configId, executionId) {
    if (!executionId) return;
    try {
        var detail = await api("/api/mail/batch-send/configs/" + configId + "/executions/" + executionId);
        renderBatchExecutionDetail(detail);
        if (detail.status === "RUNNING") {
            clearBatchLogRefreshTimer();
            batchTaskState.logRefreshTimer = setInterval(function() {
                if (batchTaskState.logConfigId === configId && batchTaskState.logExecutionId === executionId) {
                    loadBatchLogDetail(configId, executionId);
                }
            }, 3000);
        } else {
            clearBatchLogRefreshTimer();
        }
    } catch (e) {
        console.error("Failed to load log detail", e);
        var metrics = document.getElementById("batchLogMetrics");
        if (metrics) metrics.innerHTML = '<span class="muted">加载失败: ' + escapeHtml(e.message) + '</span>';
    }
}
```

### 既有测试契约（K-batch-console-regression-contract）

- `src/test/js/expertTagBatchFix.test.js:555-640` — 抽取并运行 `openBatchConfigLogs` /
  `closeBatchLogDrawer` / `clearBatchLogRefreshTimer` / `loadBatchLogExecutions` /
  `loadBatchLogDetail`（后者被 stub 覆盖），断言默认选中写入 `logExecutionId`、
  RUNNING 时创建定时器、旧 configId 的迟到响应不覆盖当前。
  → **本期保持这五个函数的名称与签名**，`loadBatchLogDetail` 仅在函数体内按
  `configId == null` 分支切换 URL，不改参数表。
- `src/test/js/batchSendTaskConsoleInteraction.test.js:137-140` — 以源码文本断言
  `confirmManualExecution` 含 `mailType: values.mailType`。
  → 本期修改该函数的成功分支，须保证 payload 构造部分文本不变。
- 执行详情端点当前**无 Kotlin 测试覆盖**（P1 新增了 `BatchSendExecutionDetailTest`，
  本期在其基础上扩展新端点用例）。

---

## 实现方案

### 阶段 A：后端 — live 视图与按执行取消（I-1 / I-2 / I-5）

**A-1. `BatchSendControlService` 新增 `ExecutionLiveView` 与 `getLiveExecutionView`**

```kotlin
data class ExecutionLiveView(
    val status: String,          // RUNNING | CANCELLING
    val message: String?,
    val roundNumber: Int,
    val processedCount: Long,
    val totalCount: Long,        // ES 估算值，前端须标注"约"
    val percentage: Int,
    val accounts: List<AccountStatRow>,
    val cancellable: Boolean     // status == "RUNNING"
)
```

- 实现：`getCurrentExecutionId(TASK_TYPE)` 与入参 executionId 不等 → 返回 null（I-1）；
  相等则读 `progressStore.get(TASK_TYPE)`，`percentage` 取 `TaskProgress.percentage`
  计算属性（I-5，不自行换算），`accounts` 复用**已有的私有** `extractAccountStats(details)`
  （`BatchSendControlService.kt:616-646`），`roundNumber` 取 `details["roundNumber"]`。
- 放在 `BatchSendControlService` 而非 Controller，是为了复用 `extractAccountStats` 与
  `AccountStatRow`，避免在 Controller 里重写一份账号解析（双事实源）。

**A-2. `BatchSendControlService` 新增 `cancelExecution(executionId: Long)`**（I-2）

- `getCurrentExecutionId(TASK_TYPE) != executionId` → 409
  `{"message": "该执行已结束或不是当前正在运行的执行"}`，**不调用** `requestCancel`。
- 相等 → `progressStore.requestCancel(TASK_TYPE)`；返回 false（已在 CANCELLING）→ 409
  `{"message": "取消请求已在处理中"}`；true → 200
  `{"message": "已发送取消请求，将在当前批次结束后停止"}`。
- 不触碰 legacy runtime status（见现状审计：由 `applyResultToRuntimeStatus` 自洽收敛）。

### 阶段 B：后端 — 端点与 DTO（I-3）

**B-1. `BatchConfigExecutionDetail` 新增 `live: ExecutionLiveView?` 字段**（默认 null）。

**B-2. `toDetail` 注入 live**：`toDetail(execution, progressRows, live = batchSendControlService.getLiveExecutionView(execution.id))`。
两个详情端点共用同一 `toDetail`，保证配置级与执行级视图一致。

**B-3. 新增执行级端点**（`BatchSendConfigController`）：

```
GET  /api/mail/batch-send/executions/{executionId}          -> BatchConfigExecutionDetail
POST /api/mail/batch-send/executions/{executionId}/cancel   -> Map<String, Any>
```

- `GET`：`taskExecutionService.getExecution(executionId)`；
  `execution.taskType != "MANUAL_INITIAL_OUTREACH"` → 404（I-3）；
  不做 configId 校验；进度行分类复用 P1 的实现（抽成私有方法 `buildProgressRows(executionId)`，
  两个端点共用）。
- `POST .../cancel`：委托 `batchSendControlService.cancelExecution(executionId)`。
- **不修改** `/configs/{id}/executions/{executionId}` 的 404 归属校验（must NOT change）。

### 阶段 C：前端 — 入口与实时区（I-4 / I-6 / I-7 / S-1 / S-2）

**C-1. `index.html`**：按 S-1 插入 `#batchLogLive` 块；按 S-2 给抽屉标题加 `id`。

**C-2. `styles.css`**：按 S-1 追加新规则块（不修改任何既有规则）。

**C-3. `app.js` — `batchTaskState` 新增字段**：`logMode`（`"config" | "execution"`），
在 `resetBatchTaskState`（`app.js:12721-12736`）中一并初始化为 `null`。

**C-4. `app.js` — 新增 `openBatchExecutionLogs(executionId)`**（I-4）：

```
1. 若 executionId 为空 → showStatus 提示"执行已启动，但未能定位到日志"，return
2. batchTaskState.logMode = "execution"
3. batchTaskState.logConfigId = null
4. batchTaskState.logExecutionId = executionId     ← 先写身份
5. 抽屉 hidden = false；标题设为 "执行日志（独立执行）"；select hidden = true
6. clearBatchLogRefreshTimer()
7. loadBatchLogDetail(null, executionId)           ← 后请求
```

**C-5. `app.js` — `openBatchConfigLogs` 仅补两行**（保持签名与既有顺序，
must-NOT-change / 被 JS 测试锁定）：在函数体内设置
`batchTaskState.logMode = "config"`、标题设为 `执行日志`、select `hidden = false`。
其余逻辑（写身份 → 显示抽屉 → 清定时器 → `loadBatchLogExecutions`）**一字不改**。

**C-6. `app.js` — `loadBatchLogDetail` 扩展**（保持参数表 `(configId, executionId)`）：

- URL：`configId == null`（或 `logMode === "execution"`）→ `/api/mail/batch-send/executions/{eid}`；
  否则维持 `/api/mail/batch-send/configs/{cfg}/executions/{eid}`。
- 轮询条件：`detail.status === "RUNNING" || detail.live != null`（覆盖 `CANCELLING`）。
- 轮询间隔：`detail.live != null` 时 **1500ms**，否则维持 3000ms。
- 定时器回调内的身份校验保持原样（I-4），并复用同一个 `logRefreshTimer` 句柄（I-7）。
- 终态（`live == null` 且 status 非 RUNNING/CANCELLING）→ `clearBatchLogRefreshTimer()` +
  隐藏实时区。

**C-7. `app.js` — 新增 `renderBatchLiveSection(d)`**，由
`renderBatchExecutionDetail`（`app.js:13849-13857`）在首位调用：

- `d.live == null` → `#batchLogLive.hidden = true`，return。
- 否则填充：状态徽标（`RUNNING` → `.badge ok` + `运行中`；`CANCELLING` → `.badge warn` + `取消中`）、
  `第 N 轮`、`#batchLogLiveFill.style.width = live.percentage + "%"`（S-1 唯一例外）、
  计数文案 **`已处理 ${live.processedCount} / 约 ${live.totalCount}（${live.percentage}%）`**（I-5），
  `live.totalCount <= 0` 时计数文案退化为 `已处理 ${processedCount}`、进度条宽度 0%、不显示百分比。
- 账号 chips：按 S-1 骨架输出，`failed > 0` 时加 `is-failing`；`accounts` 为空时该容器置空。
- 取消按钮：`live.cancellable === true` 时可见且启用，否则隐藏。
- 所有服务端文本经 `escapeHtml`。

**C-8. `app.js` — 新增 `handleBatchLiveCancel()`**（I-2）：

- `confirm("确定取消本次批量发送吗？将在当前批次结束后停止，已发出的邮件不会撤回。")`。
- `POST /api/mail/batch-send/executions/{batchTaskState.logExecutionId}/cancel`
  （**禁止**打 `/api/task-progress/.../cancel`）。
- 成功 → `showStatus(response.message, "ok")` + 立即 `loadBatchLogDetail(logConfigId, logExecutionId)`。
- 409 → `showStatus(e.message, "warn")` + 刷新详情（让实时区自我纠正）。
- 按钮在请求期间 `disabled = true`，`finally` 中恢复（K-shared-action-dialog-cleanup 的成对恢复要求）。

**C-9. `app.js` — `confirmManualExecution` 成功分支改写**（I-6）：

```javascript
        closeBatchManualConfirmDialog();
        showStatus("执行已启动 executionId: " + (response.executionId || "—"), "ok");
        if (source && source.id != null) {
            openBatchConfigLogs(source.id, response.executionId);
        } else {
            openBatchExecutionLogs(response.executionId);
        }
```

  payload 构造部分（含 `mailType: values.mailType`）**一字不改**（既有 JS 测试锁定）。

**C-10. `app.js` — `bindBatchSendTaskEvents` 注册取消按钮**
（`app.js:13999` 起的绑定函数内，与其他按钮绑定同风格）：

```javascript
    var liveCancelBtn = document.getElementById("batchLogLiveCancelBtn");
    if (liveCancelBtn) liveCancelBtn.addEventListener("click", handleBatchLiveCancel);
```

**C-11. `closeBatchLogDrawer` 补充**：隐藏 `#batchLogLive` 并把 `logMode` 置 null
（I-7；定时器清理沿用既有 `clearBatchLogRefreshTimer()`，不新增第二个句柄）。

### 阶段 D：测试

**D-1. 扩展 `BatchSendExecutionDetailTest.kt`**（P1 新建，本期追加）：

- `执行级端点返回详情且不做 configId 校验`
- `执行级端点对非 MANUAL_INITIAL_OUTREACH 的 taskType 返回 404`（I-3）
- `配置级端点的 configId 归属校验仍返回 404`（回归，must-NOT-change）
- `live 在当前执行时非空、在其他执行时为空`（I-1）

**D-2. 新增 `BatchSendLiveExecutionViewTest.kt`**（`campaign/service` 包）：

- `getCurrentExecutionId 不等于入参时返回 null`（I-1）
- `内存槽为空时返回 null 且不触发 restoreFromLog`（I-1）
- `percentage 取自 TaskProgress 且 totalCount 为 0 时为 0`（I-5）
- `accounts 复用 extractAccountStats 的解析结果`
- `cancelExecution 在 executionId 不匹配时返回 409 且不调用 requestCancel`（I-2）
- `cancelExecution 在 requestCancel 返回 false 时返回 409`
- `cancelExecution 匹配且成功时返回 200`

**D-3. 新增 `src/test/js/batchManualExecutionLog.test.js`**（`node --test`，
沿用既有 `extractFn` + DOM stub 模式）：

- `confirmManualExecution 无 source 时调用 openBatchExecutionLogs`（I-6）
- `confirmManualExecution 有 source 时仍调用 openBatchConfigLogs 且传 source.id`（I-6 回归）
- `openBatchExecutionLogs 先写 logExecutionId 再请求`（I-4）
- `loadBatchLogDetail 在 configId 为 null 时使用 executions 路由`
- `renderBatchLiveSection 在 live 为 null 时隐藏实时区`
- `renderBatchLiveSection 输出 "已处理 N / 约 M" 且 totalCount 为 0 时不显示百分比`（I-5）
- `renderBatchLiveSection 对 message 与 accountCode 做 escapeHtml`
- `cancellable 为 false 时取消按钮隐藏`
- `handleBatchLiveCancel 请求的是 executions cancel 路由而非 task-progress`（I-2）
- `closeBatchLogDrawer 隐藏实时区并清理定时器`（I-7）

---

## 变更文件清单

| # | 文件 | 类型 | 改动 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 修改 | `ExecutionLiveView`、`getLiveExecutionView`、`cancelExecution`（A-1、A-2） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` | 修改 | `live` 字段、`buildProgressRows` 抽取、2 个执行级端点（B-1~B-3） |
| 3 | `src/main/resources/static/index.html` | 修改 | `#batchLogLive` 块、抽屉标题加 id（C-1 / S-1 / S-2） |
| 4 | `src/main/resources/static/app.js` | 修改 | C-3~C-11 |
| 5 | `src/main/resources/static/styles.css` | 修改 | 追加实时区规则块（C-2 / S-1） |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt` | 修改 | D-1（P1 已创建） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendLiveExecutionViewTest.kt` | 新增 | D-2 |
| 8 | `src/test/js/batchManualExecutionLog.test.js` | 新增 | D-3 |

共 8 个文件；子系统 2 个（backend campaign/mail 服务与控制层 + frontend 静态资源）。
**无数据库迁移**（不新增字段，不改 schema）。

---

## 验证命令

> 本项目必须使用 JDK 11（zulu-11）；裸 `mvn` 会因 JDK 版本不符构建失败。
> JS 测试由 `exec-maven-plugin` 绑定在 `test` 阶段执行（`pom.xml:186-235`），
> `mvn test` 会一并跑 `node --test src/test/js/*.test.js` 与 `node --check`。

```bash
# 全量测试（回归门禁；含 Kotlin 单测 + node --test + node --check）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划涉及的 Kotlin 测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendLiveExecutionViewTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendExecutionDetailTest

# must-NOT-change 回归：批量发送控制服务与配置控制器既有行为
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendControlServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendConfigControllerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskRuntimeIntegrationTest

# 本计划新增的 JS 测试（单独运行）
node --test src/test/js/batchManualExecutionLog.test.js

# must-NOT-change 回归：日志抽屉身份契约 + 手动执行 payload 契约
node --test src/test/js/expertTagBatchFix.test.js
node --test src/test/js/batchSendTaskConsoleInteraction.test.js

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

- **I-1**：`BatchSendLiveExecutionViewTest` 断言 ——
  `getCurrentExecutionId` 返回 `null` / 返回 `999`（≠ 入参 `101`）两种情况下
  `getLiveExecutionView(101)` 均为 `null`；返回 `101` 时非 null。
  另断言实现调用的是 `getCurrentExecutionId` 而非 `get(taskType)`
  （mock `get` 抛异常时 `getLiveExecutionView` 在不匹配分支仍正常返回 null）。
  `BatchSendExecutionDetailTest` 断言：`execution.status == "RUNNING"` 但
  `getCurrentExecutionId` 返回 null 时，详情的 `live == null`。
- **I-2**：`BatchSendLiveExecutionViewTest` 断言 `cancelExecution(102)` 在
  `getCurrentExecutionId == 101` 时返回 409 且 `progressStore.requestCancel` 调用次数为 0；
  匹配且 `requestCancel` 返回 true 时返回 200；返回 false 时返回 409。
  JS 测试断言 `handleBatchLiveCancel` 请求的 URL 匹配
  `/api/mail/batch-send/executions/\d+/cancel`；grep 断言 `app.js` 中
  `handleBatchLiveCancel` 函数体不含 `task-progress`。
- **I-3**：`BatchSendExecutionDetailTest` 断言 —— 执行级 `GET` 对
  `taskType = "EXPERT_DISCOVERY"` 的执行返回 404；对 `batchConfigId == null` 的
  `MANUAL_INITIAL_OUTREACH` 执行返回 200；配置级 `GET` 在
  `execution.batchConfigId != pathId` 时仍返回 404。
- **I-4**：JS 测试断言 `openBatchExecutionLogs` 在 `api` 被调用前
  `batchTaskState.logExecutionId` 已等于目标 id；构造两次连续打开（A 后 B），
  先 resolve B 再 resolve A，断言 A 的迟到响应未覆盖 B 的渲染结果。
- **I-5**：JS 测试断言 `live = {processedCount: 7, totalCount: 120, percentage: 5}` 时
  计数文案逐字为 `已处理 7 / 约 120（5%）`；`totalCount: 0` 时文案为 `已处理 7`、
  不含 `%`、`#batchLogLiveFill.style.width === "0%"`。
  grep 断言 `renderBatchLiveSection` 函数体内不存在除 `live.percentage` 之外的百分比计算表达式
  （无 `Date.now()`、无 `/ elapsed`、无自行 `Math.round(x / y * 100)`）。
- **I-6**：JS 测试断言 `confirmManualExecution` 在 `batchTaskState.manualSource = {id: 7}` 时
  调用 `openBatchConfigLogs(7, <executionId>)`；在 `manualSource = null` 时调用
  `openBatchExecutionLogs(<executionId>)`；两种情况下 POST 的 body 中
  `sourceConfigId` 分别为 `7` 与 `null`。
- **I-7**：JS 测试断言 `closeBatchLogDrawer()` 后 `#batchLogLive.hidden === true`
  且 `batchTaskState.logRefreshTimer === null`；
  grep 断言 `app.js` 中 `setInterval` 的赋值目标只有 `batchTaskState.logRefreshTimer` 一处
  出现在批量日志抽屉相关函数内（无第二个未纳管句柄）。
- **S-1**：`git diff src/main/resources/static/styles.css` 中新增规则块与本契约代码块**逐字一致**，
  且 diff 中不含对 `.task-progress-track` / `.task-progress-fill` / `.batch-log-metrics` /
  `.batch-reason-*` / `.batch-timeline-*` 的修改行；
  `git diff src/main/resources/static/index.html` 中 `#batchLogLive` 块与本契约骨架逐字一致；
  grep 断言 `renderBatchLiveSection` 输出的 HTML 字符串中不含 `style="`；
  grep 断言 `app.js` 中 `.style.` 赋值在该函数内**仅** `batchLogLiveFill` 的 `width` 一处。
- **S-2**：JS 测试断言 `openBatchExecutionLogs` 后标题文本为 `执行日志（独立执行）`
  且 `#batchLogExecutionSelect.hidden === true`；`openBatchConfigLogs` 后标题为
  `执行日志` 且 select `hidden === false`。
- **must-NOT-change 回归**：执行「验证命令」节的 `expertTagBatchFix.test.js`、
  `batchSendTaskConsoleInteraction.test.js`、`BatchSendControlServiceTest`、
  `BatchSendConfigControllerTest`、`BatchSendTaskRuntimeIntegrationTest` 五条命令通过。
- **整体回归**：执行「验证命令」节的全量测试命令通过。

---

## 人工验收清单

### A-1: 独立手动执行后日志抽屉自动打开

- 前置条件：ES CANDIDATE 层有 ≥ 5 个未联系、带邮箱的专家；至少一个发件账号可用；
  「手动执行」tab 的来源输入框为空（未选择任何定时任务配置）。
- 操作步骤：
  1. 打开「批量邮件任务控制台」→ 切到「手动执行」tab。
  2. 不选择来源配置，直接填写日限额 5、每轮 2、每封间隔 3 秒，其余保持默认。
  3. 点「确认并执行」→ 在确认弹窗（标题应为 `确认独立手动执行？`）点确定。
- 预期结果：
  - 弹窗右侧自动滑出日志抽屉，标题为 **`执行日志（独立执行）`**。
  - 「执行记录」下拉**不可见**。
  - 抽屉内显示本次执行的指标卡片与批次时间线（不是空白、不是"暂无执行记录"）。
- 覆盖：需求 observable outcome 1、4；I-3、I-6、S-2。

### A-2: 运行中实时区展示进度、动作与账号

- 前置条件：同 A-1，且每封间隔设为 5 秒以保证有足够观察时间。
- 操作步骤：
  1. 按 A-1 触发一次独立手动执行。
  2. 抽屉打开后，持续观察抽屉顶部（指标卡片上方）的实时区约 20 秒。
- 预期结果：
  - 出现浅灰底、蓝边框的实时区，左上角绿色徽标 `运行中`，右侧有红色 `取消执行` 按钮。
  - 徽标右侧显示 `第 1 轮`（随轮次推进变为 `第 2 轮`…）。
  - 进度条有可见填充，下方计数文案形如 **`已处理 3 / 约 12（25%）`**，数字随刷新递增。
  - 计数下方显示当前动作，形如 `正在发送：someone@example.edu`，内容会变化。
  - 再下方是账号 chip，形如 `a@weibo.com 成功 3 / 失败 0`。
  - 实时区底部固定提示：`取消后将在当前批次结束时停止，已发出的邮件不会撤回。`
- 覆盖：需求 observable outcome 2；I-5、S-1。

### A-3: 执行结束后实时区自动消失

- 前置条件：接 A-2，执行仍在进行中且抽屉打开。
- 操作步骤：保持抽屉打开，等待执行自然结束（或按 A-4 取消后等待停止）。
- 预期结果：
  - 实时区整体消失（不再占位、不留空框）。
  - 指标卡片切换为终态数值，批次时间线出现 `结束` 行。
  - 浏览器不再持续发出该执行的详情请求（可在开发者工具 Network 面板确认请求停止）。
- 覆盖：需求 observable outcome 2；I-1、I-7。

### A-4: 取消执行

- 前置条件：一次正在运行的手动执行，抽屉打开且实时区可见。
- 操作步骤：
  1. 点实时区的 `取消执行`。
  2. 在浏览器确认框点确定。
- 预期结果：
  - 顶部出现绿色提示 `已发送取消请求，将在当前批次结束后停止`。
  - 实时区状态徽标在数秒内变为橙色 `取消中`，取消按钮消失。
  - 当前批次发完后执行停止，实时区消失，批次时间线终态行状态为 `已取消`。
  - **已发出的邮件数量不减少**（指标卡片「成功」数保持取消时刻的值或更高，不回退）。
- 覆盖：需求 observable outcome 3；I-2。

### A-5: 取消只作用于当前执行（跨执行安全）

- 前置条件：无正在运行的批量任务。
- 操作步骤：
  1. 按 A-1 触发一次执行，抽屉打开，**记下抽屉中显示的 executionId**（可从终态或接口确认）。
  2. 等待该执行自然结束。
  3. 立即再触发第二次执行。
  4. 在浏览器地址栏/curl 对**第一次**的 executionId 发起
     `POST /api/mail/batch-send/executions/{第一次的executionId}/cancel`。
- 预期结果：
  - 返回 HTTP 409，消息为 `该执行已结束或不是当前正在运行的执行`。
  - **第二次执行不受影响**，继续正常发送直到自然结束。
- 覆盖：I-2、IP-2。

### A-6: 配置级手动执行仍走配置日志（回归）

- 前置条件：存在一个定时任务配置，且它已有 ≥ 1 条历史执行记录。
- 操作步骤：
  1. 在「定时任务」列表点该配置行的「手动」按钮（进入手动 tab，来源已带入）。
  2. 点「确认并执行」并确定。
- 预期结果：
  - 抽屉标题为 **`执行日志`**（不带"独立执行"）。
  - 「执行记录」下拉**可见**，且列出该配置的历史执行；当前选中项是刚触发的这次。
  - 在下拉中切到一条历史记录，内容随之切换且不残留本次执行的实时区。
- 覆盖：需求 What must NOT change 第 1 条；I-4、I-6、S-2。

### A-7: 配置归属校验未被放宽（回归）

- 前置条件：存在两个不同的定时任务配置 X 和 Y，Y 有至少一条执行记录（记下 executionId）。
- 操作步骤：
  1. 请求 `/api/mail/batch-send/configs/{X的id}/executions/{Y的executionId}`。
  2. 请求 `/api/mail/batch-send/executions/{Y的executionId}`。
- 预期结果：第 1 个请求返回 404；第 2 个请求返回 200 并给出 Y 的执行详情。
- 覆盖：需求 What must NOT change 第 2 条；I-3。

### A-8: 执行级端点不是通用读取口（回归）

- 前置条件：存在一条「专家发现」任务的执行记录（记下其 executionId）。
- 操作步骤：请求 `/api/mail/batch-send/executions/{专家发现的executionId}`。
- 预期结果：返回 HTTP 404，不返回该执行的详情。
- 覆盖：I-3。

### A-9: 启动闸门未被改动（回归）

- 前置条件：把所有发件账号停用或将其今日发送量改到达上限。
- 操作步骤：在「手动执行」tab 点「确认并执行」。
- 预期结果：出现红色错误提示 `今日发送额度已用尽（含预热限制），暂不可手动发送`，
  **不创建执行记录**、**不打开日志抽屉**。
- 覆盖：需求 What must NOT change 第 4 条。

### A-10: 抽屉与弹窗关闭后无残留轮询（回归）

- 前置条件：一次正在运行的手动执行，抽屉打开且实时区可见。
- 操作步骤：
  1. 打开浏览器开发者工具 Network 面板，确认正在周期性请求执行详情。
  2. 点抽屉右上角 `×` 关闭抽屉。
  3. 再关闭整个「批量邮件任务控制台」弹窗。
- 预期结果：关闭抽屉后详情请求立即停止；关闭弹窗后无任何该执行的后台请求；
  控制台无 JS 报错。
- 覆盖：I-7；K-shared-action-dialog-cleanup。

---

## 附：本计划消费的知识条目

| 知识 ID | 用途 | 状态 |
|---|---|---|
| K-batch-task-config-snapshot-log-identity | 独立执行用 null configId 与配置日志隔离 → I-3、A-7 | 已应用 |
| K-batch-console-default-log-selection | 身份先写后请求、迟到响应按身份丢弃 → I-4、A-6 | 已应用 |
| K-batch-console-source-identity | 来源 id 决定日志归属，不得降级 → I-6、A-6 | 已应用 |
| K-batch-console-regression-contract | 改 UI 契约必须同步列出受影响 JS 测试 → 现状审计「既有测试契约」、验证命令回归项 | 已应用 |
| K-ai-stream-progress-no-fake-percent | 不得伪造完成率 → I-5、A-2 | 已应用 |
| K-shared-action-dialog-cleanup | 共用弹窗成对 setup/cleanup、按钮 disabled 恢复 → I-7、C-8、A-10 | 已应用 |
| K-clearExecutionContext-status-leak | store 终态可能遗留 RUNNING → I-1 选用 `getCurrentExecutionId` 而非 `get()` 判活 | 已应用 |
| K-manual-outreach-executor-shared | 单槽执行器语义 → 现状审计 IP-2/IP-3 的单执行前提 | 已应用 |
| K-batch-console-log-timeline | 时间线与空态 → 由第一期承担 | 已评估，本期不适用 |
| K-batch-send-daily-cap-cross-invocation | 本期不触碰 dailyCap 闸门 → A-9 仅做回归 | 已评估，不修改 |
| K-allowedTaskTypes-whitelist | 本期不新增任务类型 | 已评估，不适用 |
| K-batch-send-legacy-routes-entity-ssot | 本期不新增配置读写路由 | 已评估，不适用 |
