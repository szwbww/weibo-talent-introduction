# 批量发送执行日志「总处理/总通过/总拒绝」恒为 0 修复计划

> 复验对象：本计划自身。执行 Agent 请严格按 `## 关键不变量` 与 `## 变更文件清单` 实施，验收用 `fix-v`。

## 需求描述

**可观察结果**：在「批量发送（MANUAL_INITIAL_OUTREACH）」任务的执行记录面板中，父行的 `总处理 / 通过 / 拒绝` 三列不再恒为 0，而是分别显示该次执行真实的「已处理数 / 成功发送数 / 失败数」。例如某次执行实发 2 封、失败 0 封、已处理 2 个，则父行显示 `2 / 2 / 0`，而非 `0 / 0 / 0`。

**必须不变（NOT change）**：
- `EXPERT_REVALIDATION`、`RAW_PROMOTION_SCAN`、`EXPERT_DISCOVERY`、`CHECK_REPLIES` 四类任务的父行总计解析逻辑与现有取值**完全不变**（它们的 `resultSummary` 结构与 outreach 不同，见审计）。
- `resultSummary` 为空/空白/非法 JSON 时的 `fallbackFromLog` 行为不变（含「非法 JSON 不回退查日志」这一既定行为）。
- `ManualOutreachResult` 数据类的字段、序列化结构、`TaskExecutionSummaryProvider` 实现不变。
- 子表（批次明细：批次/本批处理/通过/拒绝/累计进度）逻辑不变。
- `getExecutions` 的状态判定、`wasCancelled`、`durationSeconds`、白名单、limit clamp 等逻辑不变。

**Out of scope（显式延后，不在本计划内）**：
- 「每轮只发 1 条 / 累计进度分母为 84079（ES 估算）/ 多次定时各自生成独立 execution」造成的「日志看起来零碎」问题。这是进度分母语义与轮次发送量问题，需结合运行期配置（`batch_send_setting.roundSize`、ES 过滤器实际命中量）单独排查，**本计划不动**。见 `## 观察项（交人工评审，非任务）`。
- 子表「本批处理=0 而 通过=1」的轮内计数器快照不一致，同上，列为观察项，不在本计划修改。
- 前端任何展示层改动（`app.js` / `index.html` 无需改动）。

## 关键不变量

### Invariant I-1: outreach 父行总计取自 resultSummary 的「根层平铺字段」，不取 `stats` 子对象
- Rule: 对 `taskType == "MANUAL_INITIAL_OUTREACH"`，`parseResultSummary` 必须从 `resultSummary` JSON 的**根节点**读取 `total / sent / failed / remaining`，**禁止**经由 `root.path("stats")` 读取。因为该 `resultSummary` 是 `ManualOutreachResult` 的序列化结果，字段平铺在根层，不存在 `stats` 包裹层。
- Applies to: `TaskProgressController.parseResultSummary` 中的 `MANUAL_INITIAL_OUTREACH` 分支（唯一写/读交叉点）。
- Violation consequence: 若仍走 `stats.path(...)`，所有取值落到缺失节点 → `asLong(0)` → 父行 `总处理/通过/拒绝` 恒为 0（即当前线上 bug）。

### Invariant I-2: 三列语义一致且可与子表对账
- Rule: outreach 父行三列定义固定为
  - `totalPassed = sent`（成功发送数）
  - `totalRejected = failed`（发送失败数）
  - `totalProcessed = total − remaining`（= 实际已处理数 `processedTotal`，含被抑制邮箱跳过项）
- Applies to: 同 I-1 分支。
- 依据：`ManualOutreachResult.remaining = totalEstimate − processedTotal`，且 `total = totalEstimate`，故 `total − remaining` 恒等于 `processedTotal`（精确，含 suppressed 跳过）。`sent`/`failed` 直接对应 `sentCount`/`failedCount`。
- Violation consequence: 若把 `totalProcessed` 误设为 `total`（=ES 估算 84079），父行会显示巨大且与实际无关的「总处理」，造成新的误导。

### Invariant I-3: 不破坏其它任务类型与回退路径
- Rule: 仅修改 `MANUAL_INITIAL_OUTREACH` 这一个 `when` 分支；其余分支（含 `else`）、`fallbackFromLog`、`detectWasCancelled`、`try/catch` 包裹结构保持逐字不变。
- Applies to: `TaskProgressController.parseResultSummary` 整体。
- Violation consequence: 触碰其它分支会回归 4 类任务的既有正确行为，制造无关 P1。

## 现状审计

### 数据载体：`task_execution.result_summary`（MySQL，Spring Data JDBC）
- 写入路径（唯一）：
  1. `TaskExecutionService.runAndRecordWithResult`（`src/.../task/service/TaskExecutionService.kt:101-110`）—— 任务成功结束时 `resultSummary = toJson(resultValue)`。对 outreach，`resultValue` 是 `ManualInitialOutreachService.runScheduledBatch(...)` 返回的 `ManualOutreachResult`。`runAndRecord`（同文件 177-186）同理。
  2. 异常分支（同文件 113-124）不写 `resultSummary`（保持为 RUNNING 时写入的 null）。
- `ManualOutreachResult` 序列化形状（`src/.../campaign/service/ManualInitialOutreachService.kt:616-625`，**平铺，无 `stats`**）：
  ```json
  {"total":<totalEstimate>,"sent":<sentCount>,"failed":<failedCount>,
   "skippedNoAccount":<n>,"wasCancelled":<bool>,"finalStatus":"...",
   "stopReason":"...","remaining":<totalEstimate-processedTotal>}
  ```
  - `total = totalEstimate`（= retryable + ES `countExperts` 估算，本案约 84079），见 `runScheduledBatch` 返回（行 472-477）。
  - `remaining = totalEstimate − processedTotal`（行 476）。
  - 空快照早退分支（行 144-147）：`total=0, sent=0, failed=0, remaining` 取默认 0。
- 读取路径（与本计划相关，唯一）：
  1. `TaskProgressController.getExecutions` → `parseResultSummary`（`src/.../task/controller/TaskProgressController.kt:73-110, 119-165`）。`MANUAL_INITIAL_OUTREACH` 分支（行 147-151）当前错误地读 `root.path("stats").path("total"/"sent"/"failed")`。`stats` 节点在 outreach 的 JSON 里**不存在** → 全 0。
  2. `fallbackFromLog`（同文件 167-217）仅在 `resultSummary` 为 null/空白时触发。其 `MANUAL_INITIAL_OUTREACH` 分支（行 190-193）已正确地从 `details` **根层**读 `sent/failed`，并以 `latestLog.processedCount` 作 `totalProcessed` —— 即回退路径语义本就正确，正可作为修复后对账参照。
- 其它 `resultSummary` 写入者的结构（证明只有 outreach 平铺，其余确有 `stats`，故不可统一改 `else`）：
  - `RevalidationResult`（`expert/domain/ExpertRevalidationDomain.kt:14-15`）含 `val stats: RevalidationStats` → `{"stats":{...}}`。
  - `PromotionScanResult`（同上 42-43）含 `val stats`。
  - `DiscoveryResult`（`discovery/domain/DiscoveryResult.kt:5-7`）含 `val stats`。
  - `CHECK_REPLIES` 分支已用 `root.path("totalAccountsToPoll")`（平铺，单独处理）。

### 交互点
- 唯一交互点：写路径 `TaskExecutionService` 序列化平铺的 `ManualOutreachResult` ↔ 读路径 `parseResultSummary` 期望 `stats` 包裹。两者在不同模块（`task/service` vs `task/controller`），契约不一致即本 bug。修复在读端对齐写端的实际结构。

### 现有测试
- `TaskProgressControllerExecutionsTest`（`src/test/.../task/controller/TaskProgressControllerExecutionsTest.kt`）覆盖 revalidation/promotion/discovery/fallback/clamp/status 等 23 例，**但无 `MANUAL_INITIAL_OUTREACH` 用例**——即此分支从未被测试覆盖，bug 因此逃逸。revalidation 等用例均以 `{"stats":{...}}` 构造，证明修改 outreach 分支不影响它们。

## 实现方案

### Task 1 — 修正 `parseResultSummary` 的 outreach 分支（遵守 I-1 / I-2 / I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt`

将该分支（当前 147-151 行）由：
```kotlin
"MANUAL_INITIAL_OUTREACH" -> ExecutionTotals(
    totalProcessed = stats.path("total").asLong(0),
    totalPassed = stats.path("sent").asLong(0),
    totalRejected = stats.path("failed").asLong(0)
)
```
改为从**根节点** `root` 读取，并按 I-2 计算 `totalProcessed`：
```kotlin
"MANUAL_INITIAL_OUTREACH" -> ExecutionTotals(
    // outreach 的 resultSummary 是 ManualOutreachResult 平铺序列化，无 stats 包裹层（I-1）
    totalProcessed = (root.path("total").asLong(0) - root.path("remaining").asLong(0))
        .coerceAtLeast(0),               // = processedTotal（I-2）
    totalPassed = root.path("sent").asLong(0),
    totalRejected = root.path("failed").asLong(0)
)
```
约束：
- 不得修改同函数内其它分支、`val stats = root.path("stats")` 这一行（其余分支仍依赖它）、`try/catch`、`fallbackFromLog` 调用结构（I-3）。
- `coerceAtLeast(0)` 防御空快照/异常时 `total<remaining` 出现负值。

### Task 2 — 新增针对 outreach 分支的单元测试
文件：`src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt`

新增用例（沿用现有 `execution(...)` 辅助与 mock 风格），覆盖 I-1/I-2：
1. `outreach resultSummary maps sent failed processed from flat fields`：
   - `summary = """{"total":84079,"sent":2,"failed":0,"skippedNoAccount":0,"wasCancelled":false,"finalStatus":"COMPLETED","stopReason":null,"remaining":84077}"""`
   - mock `findRecentByTaskType("MANUAL_INITIAL_OUTREACH", 10)`。
   - 断言 `totalProcessed == 2`（84079−84077）、`totalPassed == 2`、`totalRejected == 0`。
2. `outreach with failures maps rejected`：
   - `{"total":10,"sent":3,"failed":2,"remaining":5,...}` → `totalProcessed==5`、`totalPassed==3`、`totalRejected==2`。
3. `outreach empty snapshot yields zeros without negative`：
   - `{"total":0,"sent":0,"failed":0,"remaining":0,"finalStatus":"COMPLETED"}` → 三列均 0（验证 `coerceAtLeast(0)`）。
4. 回归保护（可选但建议）：`outreach blank resultSummary falls back to log`，mock 一条 `detailsJson={"sent":1,"failed":0}`、`processedCount=1` 的 `TaskProgressLog`，断言 `totalProcessed==1, totalPassed==1`（确认回退路径未被波及）。

约束：仅新增测试方法，不得修改既有用例。

### 验证（最终步骤）
执行：
```
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskProgressControllerExecutionsTest
```
要求：新增用例全绿，且既有 23 例（revalidation/promotion/discovery/fallback/clamp/status…）全部保持通过（I-3 回归保证）。

## 变更文件清单

| # | 文件 | 改动 | 说明 |
|---|------|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt` | 改 | 仅 `parseResultSummary` 内 `MANUAL_INITIAL_OUTREACH` 一个分支（约 147-151 行） |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt` | 改（仅新增方法） | 新增 outreach 分支单测 3~4 例 |

文件数：2（≤10 ✓）。子系统数：1（任务执行日志读取）（≤2 ✓）。新增共享存储字段数：0 ✓。

## 验收标准

- **I-1**：构造无 `stats` 包裹、字段平铺的 outreach `resultSummary`，`getExecutions("MANUAL_INITIAL_OUTREACH")` 返回的父行三列非 0；断言取值来自根层 `sent/failed/total/remaining`。
- **I-2**：
  - `totalPassed == sent`、`totalRejected == failed`；
  - `totalProcessed == total − remaining`，且在 `total < remaining`/空快照时 `>= 0`；
  - 与子表对账：父行 `通过` 应等于子表各批 `roundPassed` 之和（`sentCount`），父行 `拒绝` 等于 `failedCount`。
- **I-3**：`TaskProgressControllerExecutionsTest` 既有全部用例不改且全绿；revalidation/promotion/discovery 仍各自取到 100/80/15、200/150/40、500/400/100 等原值。
- **集成场景**：以一次真实「只发 2 封、0 失败」的 outreach 执行为例（`{"total":84079,"sent":2,"failed":0,"remaining":84077}`），父行显示 `总处理=2 / 通过=2 / 拒绝=0`，复现用户上报场景由 `0/0/0` 变为正确值。

## 观察项（交人工评审，非任务）

以下为本次排查发现、但**不在本计划修复范围**的现象，仅记录供后续单独立项：

1. **累计进度分母为 ES 估算（84079）**：`runScheduledBatch` 用 `retryable + countExperts(notContactedWithEmailFilters)` 作 `totalCount`/`total`。它是「待联系候选人估算」，并非本次真实可发集合，故进度恒显示极小百分比（`1/84079 0%`）。是否改为更贴近真实可发量的分母属语义设计决策，需独立评估，勿在本计划顺手改动。
2. **每轮只发约 1 条、多次定时各生成独立 execution**：默认 `roundSize=50`，但用户观察到每轮仅处理 1 条。需在运行期核对 `batch_send_setting.roundSize` 实际值、ES `notContactedWithEmailFilters` 实际命中量、以及 `OutreachTargetIterator.loadNextEsPage` 中 `esOffset` 复位（`TargetIterator.kt:54`）配合 ES 近实时刷新延迟的行为，确认是配置还是缺陷后再立项。
3. **子表「本批处理=0 而 通过=1」**：源于 `updateProgress` 多次快照同一 `batchNumber` 后按批次号去重，属轮内计数器展示口径问题，非生产数据错误，P2 观察。

> 若执行 Agent 认为上述某项确为缺陷并应纳入修复，应作为**观察记录**上报人工评审，不得在本计划内擅自扩范围（scope discipline）。
