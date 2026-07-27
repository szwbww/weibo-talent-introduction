# 任务日志两层展示 + 取消后按钮卡死修复 — 开发计划

> 适用任务类型：`EXPERT_REVALIDATION`（重新验证候选人）、`RAW_PROMOTION_SCAN`（扫描 RAW 可晋升）、`EXPERT_DISCOVERY`（发现专家）。
> 本计划交给执行 agent 实施。实施前请先通读本文「现状分析」一节，所有行号基于 2026-06-11 的代码，可能有少量漂移，请以符号名定位。

---

## 一、需求描述

### 需求 A：日志两层展示

当前任务弹窗里的「批次处理明细」表（`#taskModalLogBody`）把所有 `task_progress_log` 行平铺在一张表里，无法区分"哪一次点击执行"。要求改为两层：

- **第一层（执行层 / 大批次）**：用户每点击一次「开始执行」即产生一条记录（对应一行 `task_execution`）。列表按时间倒序展示，每行显示该次执行的**总量汇总**：开始时间、触发方式、状态、总处理数、总通过数、总拒绝数、耗时。
- **第二层（批次层 / 小批次）**：点击第一层的某行后，展开显示该次执行内部的逐批日志（现状的 500 条/批滚动批次，对应 `task_progress_log` 按 `task_execution_id` 过滤的行）：批次号、本批处理、通过、拒绝、累计进度、时间。

交互要求：

1. 列表默认只显示第一层；点击某行展开/收起第二层（手风琴式，同一时间允许只展开一行，简化实现）。
2. 任务正在运行时：当前执行自动出现在第一层顶部（状态 RUNNING），**自动展开**，其第二层日志按现有 3s 轮询持续刷新；终态（COMPLETED/FAILED/CANCELLED）后停止刷新但保留展示。
3. 历史执行（非运行中）的第二层日志点击展开时按需加载一次即可，无需轮询。

### 需求 B：取消后外部按钮卡死修复

复现路径：点击「开始执行」→ 弹窗中点「取消任务」→（在任务真正变为 CANCELLED 前）关闭弹窗 → 下拉菜单里的外部按钮（`#revalidateBtn` / `#promoteRawBtn` / `#discoverBtn`）永远停留在「执行中...」且 `disabled=true`，无法再点击，只能刷新页面。

---

## 二、现状分析（务必先读）

### 2.1 后端数据模型（已满足两层需求，无需新迁移）

- **第一层数据源**：`task_execution` 表（`task/domain/TaskExecution.kt`）。每次点击执行，controller 调 `TaskExecutionService.runAndRecord(WithResult)` 都会插入一行（status=RUNNING），结束时更新为 SUCCESS/PARTIAL_SUCCESS/FAILED，`resultSummary` 存结果 JSON。
  - 已有查询接口：`GET /api/task-executions?taskType=X`（`TaskExecutionController.listExecutions`，返回全量，无 limit）。
  - `TaskExecutionRepository.findRecentByTaskType(taskType, limit)` 已存在（带 LIMIT 的 @Query）。
- **第二层数据源**：`task_progress_log` 表（V22 迁移）。`TaskProgressStore.persistProgressLog` 在每次 `update()` 时写一行，含 `taskExecutionId`、`batchNumber`、`batchProcessed/batchPassed/batchRejected`、`processedCount/totalCount`、`status`、`detailsJson`。
  - 已有查询接口：`GET /api/task-progress/{taskType}/logs?executionId=N`（`TaskProgressController.getProgressLogs`，按 executionId 过滤、id 升序）。

**数据噪音注意点（实现第二层渲染时要处理）：**

1. `tryStartWithToken` 写入的首行日志 `taskExecutionId` 是**负数 pendingToken**（`-System.nanoTime()`），真实 executionId 绑定（`bindExecutionId`）之后的行才是正数。按 executionId 查询时该首行天然查不到，无影响，但**不要**试图把负数 token 行也归入某次执行。
2. 终态行（COMPLETED/FAILED/CANCELLED）`batchNumber = -1`；启动行 `batchNumber = 0`。第二层表格只渲染 `batchNumber > 0` 的行；终态行可用于第一层汇总兜底（见 3.2）。
3. `requestCancel` 会写一行 status=CANCELLING 的日志（沿用当前 progress 的 batchNumber），渲染时同样按规则 1/2 过滤。

### 2.2 第一层"总量"从哪来

按任务类型，终态执行的 `task_execution.resultSummary` JSON 结构不同：

| taskType | resultSummary 结构 | 总处理 | 总通过 | 总拒绝 |
|---|---|---|---|---|
| EXPERT_REVALIDATION | `{"stats":{"total":N,"passed":N,"demoted":N,"demotionFailed":N,...},"wasCancelled":bool}` | stats.total | stats.passed | stats.demoted |
| RAW_PROMOTION_SCAN | `{"stats":{"total":N,"promoted":N,"filtered":N,"emailRejected":N,...},...}` | stats.total | stats.promoted | stats.filtered + stats.emailRejected |
| EXPERT_DISCOVERY | `DiscoveryResult` 序列化（含 `stats.totalPapers`、`stats.indexed`、`stats.promoted`、`summaryText` 等） | stats.totalPapers | stats.indexed | totalPapers − indexed（或留空显示 summaryText） |

注意：`runAndRecord`（EXPERT_DISCOVERY 用的非 WithResult 版本）在 block 抛异常时 status=FAILED 且 resultSummary=null；RUNNING 中的执行 resultSummary 也是 null。这两种情况第一层汇总需用**该 executionId 下最新一条 `task_progress_log`** 的 `processedCount/totalCount/detailsJson` 兜底。

### 2.3 前端现状（`src/main/resources/static/app.js`）

关键符号：

- `taskButtonMapping` / `taskButtonOriginalTexts`（约 L189-200）：taskType → 外部按钮 id 映射。
- `openTaskModal(taskType, label, btnId)`（约 L269）：打开进度弹窗。**注意 L314-320：把外部按钮 `disabled=true` + 文案"执行中..."**。启动 1s 进度轮询 + 3s 日志轮询，存入 `currentTaskModal.progressTimer/logTimer`。
- `closeTaskModal()`（约 L365）：`stopTaskModalPolling()` 后置 `currentTaskModal = null`。**关闭后无任何后台轮询存在** —— 这是 Bug B 的直接根因。
- `updateTaskModalFromProgress(progress)`（约 L399）：唯一会调 `restoreTaskButton(btnId)` 恢复外部按钮的地方（L429-434，仅当 status 终态且弹窗轮询还活着）。
- `setTaskButtonRunning(btnId)`（约 L262）：页面加载时 `resumeProgressPollingIfNeeded` 用它把按钮置为"执行中"，**但 `disabled=false`（可点击，点击会重新打开弹窗）** —— 与 `openTaskModal` 的 disabled=true 行为不一致。
- `updateTaskModalLogs(logs)`（约 L457）：现有平铺日志表渲染，目标改造点。
- 启动入口：`handleRevalidateCandidates`/`executeRevalidate`（L1266-1300）、`handlePromoteRaw`/`executePromoteRaw`、`handleDiscover`/`executeDiscover`（到约 L1424）。`handle*` 在任务已运行时直接 `openTaskModal` 查看；`execute*` 失败分支会 `restoreTaskButton`，但**成功/取消分支不恢复**（依赖弹窗轮询）。
- `resumeProgressPollingIfNeeded()`（约 L230）：仅在页面加载时跑一次，发现 RUNNING/CANCELLING 就 `setTaskButtonRunning`，**之后没有任何机制在任务结束时恢复按钮**（Bug B 的另一个变体：刷新页面后按钮显示"执行中"，任务结束后也不会自己恢复，需再次刷新）。

### 2.4 Bug B 根因总结

外部按钮的恢复只发生在「弹窗开着且其 1s 轮询观察到终态」这一条路径上。以下场景全部卡死：

1. 点取消后立刻关弹窗（用户报告的场景）：`stopTaskModalPolling` 后无人观察 CANCELLED → 按钮永久 disabled。
2. 任务运行中直接关弹窗，任务自然结束 → 同样卡死。
3. 刷新页面后由 `resumeProgressPollingIfNeeded` 置成"执行中"，任务结束 → 按钮文案不恢复（虽然此路径 disabled=false 还能点）。

---

## 三、实施方案

### 3.1 后端：新增「执行层汇总」接口

**文件**：`task/controller/TaskProgressController.kt`（推荐放这里，与 logs 接口同域）；依赖注入 `TaskExecutionRepository`、`TaskProgressLogRepository`、`ObjectMapper`。

新增接口：

```
GET /api/task-progress/{taskType}/executions?limit=10
```

返回 `List<TaskRunSummaryResponse>`，按 startedAt 倒序：

```kotlin
data class TaskRunSummaryResponse(
    val executionId: Long,
    val taskType: String,
    val triggerType: String,        // MANUAL / SCHEDULED
    val status: String,             // RUNNING / SUCCESS / PARTIAL_SUCCESS / FAILED / CANCELLED(见下)
    val startedAt: String,          // yyyy-MM-dd HH:mm:ss
    val finishedAt: String?,
    val durationSeconds: Long?,
    val totalProcessed: Long,
    val totalPassed: Long,
    val totalRejected: Long,
    val summaryText: String?,       // EXPERT_DISCOVERY 的 details.summaryText，其余可为 null
    val errorMessage: String?
)
```

实现要点：

1. `taskType` 校验：仅允许 `EXPERT_REVALIDATION` / `RAW_PROMOTION_SCAN` / `EXPERT_DISCOVERY`（白名单外返回 400 或空列表均可，写明并测试）。`limit` 限定 1..50，默认 10。
2. 数据：`TaskExecutionRepository.findRecentByTaskType(taskType, limit)`（已存在）。
3. 汇总取值优先级：
   a. `resultSummary` 非空 → 按 2.2 表格解析（用 `JsonNode.path(...)` 容错解析，勿用强类型反序列化，结构不齐时各字段取 0）。
   b. `resultSummary` 为空（RUNNING 或异常 FAILED）→ 取 `findTopByTaskExecutionIdOrderByIdDesc(executionId)`（**需在 `TaskProgressLogRepository` 新增此方法**）：`totalProcessed=processedCount`，passed/rejected 从该行 `detailsJson` 解析（revalidation: passed/demoted；promotion: promoted/filtered；discovery: indexed/—），解析失败全 0。
   c. 两者都没有 → 全 0。
4. **CANCELLED 状态映射**：`task_execution.status` 没有 CANCELLED（取消的执行落库为 SUCCESS，因为 RevalidationResult/DiscoveryResult 的 wasCancelled 不影响 runAndRecord 的状态推断）。处理：解析 resultSummary 中 `wasCancelled==true` 时把响应里的 `status` 覆盖为 `"CANCELLED"`。EXPERT_DISCOVERY 的 DiscoveryResult 同样有 `wasCancelled` 字段。
5. `durationSeconds = Duration.between(startedAt, finishedAt).seconds`，finishedAt 为 null 时返回 null。

**已有接口不动**：第二层继续用 `GET /api/task-progress/{taskType}/logs?executionId=N`。

### 3.2 后端：第二层日志的噪音过滤（可选做在前端）

为简化前端，可在 `getProgressLogs` 增加可选参数 `?batchOnly=true`：过滤出 `batchNumber > 0` 的行。默认 false 保持兼容（现有调用方不受影响）。前端新 UI 统一带 `batchOnly=true`。
（若执行 agent 认为后端不值得动，也可纯前端过滤 `log.batchNumber > 0`，二选一，但要在代码注释里说明取舍。）

### 3.3 前端：两层日志 UI

**文件**：`src/main/resources/static/index.html`（约 L510-531 「批次处理明细」区块）、`app.js`。

#### index.html

把现有单表区块替换为两层结构：

```html
<div class="task-modal-logs" style="display:flex;flex-direction:column;gap:10px;">
    <h4 class="task-modal-logs-title">
        <span class="task-modal-logs-title-indicator"></span>
        执行记录（点击行展开批次明细）
    </h4>
    <div class="task-modal-log-table-wrapper" style="max-height:300px;overflow-y:auto;...">
        <table class="data-table compact" ...>
            <thead>
                <tr>
                    <th></th>            <!-- 展开箭头 -->
                    <th>开始时间</th>
                    <th>触发</th>
                    <th>状态</th>
                    <th>总处理</th>
                    <th>通过</th>
                    <th>拒绝</th>
                    <th>耗时</th>
                </tr>
            </thead>
            <tbody id="taskModalRunBody"></tbody>
        </table>
    </div>
</div>
```

第二层不单独建表：展开时在被点击的执行行下方插入一行 `<tr class="run-detail-row"><td colspan="8">…内嵌小批次表…</td></tr>`，内嵌表列 = 现有 6 列（批次/本批处理/通过/拒绝/累计进度/时间）。保留原 `updateTaskModalLogs` 的列语义。

#### app.js 改造

1. **新增状态**：`currentTaskModal` 增加 `expandedExecutionId`（当前展开的执行）与 `runListTimer`。
2. **新增函数**：
   - `fetchRunList(taskType)` → `GET /api/task-progress/{taskType}/executions?limit=10`，渲染 `#taskModalRunBody`。每行 `data-execution-id`，状态用现有 badge 风格（RUNNING 蓝/SUCCESS 绿/CANCELLED 灰/FAILED 红，PARTIAL_SUCCESS 黄）。
   - `renderRunRow(run)` / `renderBatchTable(logs)`：纯渲染函数，**保持可被 `src/test/js` 的 `extractFn` 正则提取**（即用 `function xxx(...) {...}` 顶层声明，不要用箭头函数/const），便于补单测。
   - `toggleRunDetail(taskType, executionId)`：手风琴展开。展开时 `GET .../logs?executionId=N&batchOnly=true` 渲染内嵌表；若该执行 status=RUNNING，则记录 `expandedExecutionId` 并由现有 3s logTimer 持续刷新该内嵌表；非 RUNNING 只加载一次。
3. **改造 `openTaskModal`**：
   - 打开时先 `fetchRunList(taskType)`；启动 `runListTimer`（每 5s 刷新第一层，终态后自动停，见第 5 点）。
   - 原 3s `logTimer` 改为：仅当 `expandedExecutionId` 非空时拉取该 executionId 的 logs 并刷新对应内嵌表（替代原来无条件平铺渲染）。
   - 任务启动场景（execute* 调用路径）：拿到 `progress.executionId` 后（现有 L331-333 已捕获），若 `expandedExecutionId` 为空则自动展开该执行。
4. **删除/改写 `updateTaskModalLogs` 的旧平铺逻辑**：改为渲染内嵌批次表的实现（函数名可保留，签名加 executionId 上下文），同步更新 `openTaskLaunchModal` 中 L1245、L1256-1259 的初始加载调用为 `fetchRunList`。
5. **轮询停止条件**：`updateTaskModalFromProgress` 观察到终态时，额外做一次 `fetchRunList`（让第一层状态从 RUNNING 翻成终态）然后 `clearInterval(runListTimer)`；logTimer 保持现有停止逻辑（弹窗关闭时 stop）。
6. **`stopTaskModalPolling`**：增加清理 `runListTimer`。

### 3.4 前端：取消后按钮卡死修复（Bug B）

核心思路：**按钮恢复不再依赖弹窗存活**。引入与弹窗解耦的"后台任务监视器"。

1. **统一运行态按钮表现**：`openTaskModal` 中 L314-320 删除 `btn.disabled = true`，改为调用已有 `setTaskButtonRunning(btnId)`（disabled=false + "执行中"指示）。运行中点击按钮的行为已由 `handle*` 正确处理（isTaskRunning → 重新打开弹窗查看），不会重复启动——后端 `tryStartWithToken` 也有 409 兜底。
2. **新增后台监视器**（app.js 顶层）：

```js
const taskWatchers = {}; // taskType -> intervalId

function startTaskWatcher(taskType) {
    if (taskWatchers[taskType]) return;            // 单例
    taskWatchers[taskType] = setInterval(async () => {
        try {
            const response = await fetch(`${contextPath}/api/task-progress/${taskType}`);
            if (response.status === 204) { stopTaskWatcher(taskType, true); return; }
            if (!response.ok) return;               // 网络抖动，下轮再试
            const progress = await response.json();
            if (progress.status !== "RUNNING" && progress.status !== "CANCELLING") {
                stopTaskWatcher(taskType, true);
                const label = taskButtonMapping[taskType]?.label || taskType;
                const verb = progress.status === "CANCELLED" ? "已取消"
                           : progress.status === "COMPLETED" ? "已完成" : "已结束";
                showStatus(`${label} ${verb}`, progress.status === "FAILED" ? "error" : "ok");
            }
        } catch (e) { /* 静默，下轮再试 */ }
    }, 3000);
}

function stopTaskWatcher(taskType, restoreButton) {
    if (taskWatchers[taskType]) {
        clearInterval(taskWatchers[taskType]);
        delete taskWatchers[taskType];
    }
    if (restoreButton) {
        const mapping = taskButtonMapping[taskType];
        if (mapping) restoreTaskButton(mapping.btnId);
    }
}
```

3. **接线点**：
   - `closeTaskModal()`：关闭前若 `currentTaskModal` 存在，检查当前 progress（可直接复用最后一次轮询缓存的状态，或简单地无条件 `startTaskWatcher(taskType)` —— watcher 自己会在非运行态时立即恢复按钮并自杀，逻辑更简单，推荐后者）。
   - `updateTaskModalFromProgress` 终态分支：调用 `stopTaskWatcher(taskType, false)`（弹窗自己已 restore，避免双重 showStatus）。
   - `resumeProgressPollingIfNeeded()`：发现 RUNNING/CANCELLING 时除 `setTaskButtonRunning` 外，同时 `startTaskWatcher(taskType)`（修复 2.4 场景 3）。
   - `openTaskModal()`：打开时 `stopTaskWatcher(taskType, false)`（弹窗轮询接管，避免双轨）。
4. **`handleCancelTask` 不改行为**，但确认：取消请求成功后弹窗 cancel 按钮保持"取消中..."由 `updateTaskModalFromProgress` 在 CANCELLED 时翻成"已取消"——现有逻辑已覆盖，无需改。
5. **`execute*` 三个函数的 catch 分支**（L1296-1298 等）：把 `restoreTaskButton(...)` 替换为 `stopTaskWatcher(taskType, true)`，保证恢复路径唯一收口（行为等价 + 顺带清 watcher）。

### 3.5 不要做的事

- 不改 `TaskProgressStore` 的并发/token 语义（tryStartWithToken / bindExecutionId / clearExecutionContext 这套有 stale-update 防护，动了会破坏 `TaskExecutionServiceTest` 等现有测试）。
- 不新增 Flyway 迁移（现有 schema 足够）。
- 不动 `AUTO_REPLY_ALL` 轮询日志（`recent-polls` 一套是独立 UI）。
- 不改 `ConversationStateService` / mail 模块任何东西。

---

## 四、测试要求

### 4.1 Kotlin 单测（必须）

新建 `src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt`（或并入现有 controller 测试风格，参考 `ExpertDiscoveryControllerTest.kt` 的 mock 方式）：

1. resultSummary 为 revalidation JSON → totalProcessed/passed/rejected 正确映射（total/passed/demoted）。
2. resultSummary 含 `"wasCancelled":true` → status 覆盖为 CANCELLED。
3. resultSummary 为 null、存在 progress log → 从最新 log 兜底（processedCount + detailsJson）。
4. resultSummary 为非法 JSON → 不抛异常，各汇总字段为 0。
5. limit 越界（0、51）→ 400 或夹紧（按实现写断言）。
6. taskType 不在白名单 → 按实现断言（400/空列表）。
7. 若实现了 `batchOnly` 参数：`batchOnly=true` 过滤掉 batchNumber ≤ 0 的行。

### 4.2 JS 单测（有条件必须）

仿照 `src/test/js/normalizeDiscoveryResultSummary.test.js`（node:test + vm 提取 app.js 顶层 function）：

1. `renderRunRow`：RUNNING/SUCCESS/CANCELLED/FAILED 状态渲染、耗时格式、XSS（summaryText 含 `<script>` 须被 escapeHtml）。
2. `renderBatchTable`：空数组 → "暂无批次日志"；正常数组 → 行数与列值正确；`batchNumber<=0` 行被过滤（若选了前端过滤方案）。

运行方式：`node --test src/test/js/`（与现有测试一致）。

### 4.3 手工验收清单

1. 启动任一任务 → 弹窗第一层顶部出现 RUNNING 行且自动展开，第二层每 3s 增长。
2. 任务完成 → 第一层该行翻成 SUCCESS 并显示总量，第二层停止刷新。
3. 关闭弹窗重新打开 → 第一层显示历史执行列表；点击任意历史行可展开其批次明细；再次点击收起。
4. **Bug B 主场景**：启动任务 → 点「取消任务」→ 立刻关闭弹窗 → 等任务实际取消后（观察 status 条提示「xxx 已取消」），外部按钮自动恢复为原文案且可点击。
5. 任务运行中关闭弹窗 → 点外部按钮（应显示"执行中"且可点）→ 重新打开弹窗能看到进行中的执行。
6. 刷新页面（任务运行中）→ 按钮显示"执行中"；任务结束后按钮自动恢复（无需再刷新）。
7. 两个任务类型并发互斥提示仍正常（启动 A 后启动 B 应提示"已有其他任务正在执行中"）。

### 4.4 回归命令

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --test src/test/js/
```

全部测试必须通过；不允许修改既有测试断言来迁就实现（若现有测试确实因合理行为变化而失败，需在 PR 描述中逐条说明）。

---

## 五、实施顺序建议

1. 后端：`TaskProgressLogRepository.findTopByTaskExecutionIdOrderByIdDesc` + executions 接口 + 单测（3.1、3.2、4.1）。
2. 前端 Bug B（3.4）：改动小、独立可验，先做先验收。
3. 前端两层 UI（3.3）+ JS 单测（4.2）。
4. 手工验收（4.3）→ 回归（4.4）。

步骤 2 与 1/3 无依赖，可并行。
