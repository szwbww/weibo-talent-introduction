# A2：手动执行日志可达性（含独立执行）

> 编号：**A2**（全链第 2 份）。依赖：**必须在 A1 之后执行**（同改 `app.js` 抽屉族与 `index.html` 缓存键）。
> 共享不变量 M-1…M-4、共享审计 X-1…X-3 见 `batch-console-log-drawer-main.md`，本文不重复。

## 需求描述

**Observable outcome**

1. 「手动执行」页签上有一个常驻的「最近执行日志」入口，任何时候点开都能看到最近 50 条批量发送
   执行记录（含定时触发、配置手动触发、以及**不关联任何定时配置的独立手动执行**），
   可在下拉里自由切换记录。
2. 在两个页签之间来回切换，已打开的日志抽屉**不再被关掉**，运行中执行的实时刷新也不再被清掉。
3. 独立手动执行（未选来源配置）产生的日志，在抽屉关闭后仍可从上述入口找回。

**What must NOT change**

- `confirmManualExecution` 的分派规则不变：有来源配置 → `openBatchConfigLogs(source.id, executionId)`；
  无来源配置 → `openBatchExecutionLogs(executionId)`
  （`batchManualExecutionLog.test.js` 直接断言这两条，见现状审计）。
- `GET /api/mail/batch-send/configs/{id}/executions` 与
  `GET /api/mail/batch-send/configs/{id}/executions/{executionId}` 的路径、语义、
  跨配置访问返回 404 的行为不变（`BatchSendExecutionDetailTest.kt:240` 断言）。
- `GET /api/mail/batch-send/executions/{executionId}` 的 taskType 校验与 404 行为不变
  （同文件 `:251/:269/:279`）。
- `BatchConfigExecutionSummary` 现有 11 个字段的名称、类型、取值来源不变（只追加字段）。
- 抽屉的渲染函数族（`renderBatchExecutionDetail` 及其下游）不改。

**Out of scope（明确延后）**

- 执行记录的分页、按配置/触发类型筛选、按时间范围检索 —— 本次只做「最近 N 条」。
- 在下拉里显示来源配置的**名称**（需要 N 次配置查询或一次 join，本次只回传
  `batchConfigId`，前端对 `null` 显示「独立执行」）。
- 「任务记录」页（`view-tasks`）与本入口的打通。
- `POST /api/mail/batch-send/manual-executions` 在 5 秒内拿不到 executionId 时不回传该字段
  （`BatchSendControlService.kt:409-413`）的兜底改造 —— 有了本计划的入口，运营已可自行找回。

## 关键不变量

### Invariant I2-1: 抽屉有三种来源模式，`logMode` 是唯一判别依据
- Rule: `batchTaskState.logMode` 取值为 `"config"`（按配置查）或 `"execution"`（按执行查，
  含独立执行）。所有依赖模式的分支**只能读 `logMode`**，不得再用 `logConfigId` 是否为空来推断模式。
- Applies to: `loadBatchLogDetail`（`app.js:15076-15079` 已有该判断，保持）、
  执行记录下拉的 `change` 监听（`app.js:15444-15452`，**当前违例**）、
  新增的 `openBatchRecentLogs` / `loadBatchGlobalExecutions`。
- Violation consequence: 现状即违例 —— `change` 监听写的是
  `if (executionId && batchTaskState.logConfigId)`，而 `execution` 模式下 `logConfigId` 恒为 `null`，
  切换记录静默无效。当前靠把下拉整个 `hidden` 掩盖，本计划要放开下拉，必须先修掉。
- 来源: original

### Invariant I2-2: 切换页签不销毁日志抽屉状态
- Rule: `switchBatchSendTab` 不得调用 `closeBatchLogDrawer()` / `clearBatchLogRefreshTimer()`。
  抽屉状态只在三处销毁：抽屉自身的关闭按钮、`closeBatchSendTaskModal`、进入配置编辑器。
- Applies to: `app.js` `switchBatchSendTab`、`openBatchConfigEditor`。
- Violation consequence: 运行中执行的 1.5s 实时轮询被清掉；用户切去列表看一眼再回来，
  抽屉已空，只能重新找记录 —— 即本次报障的直接成因。
- 来源: original

### Invariant I2-3: 全局执行列表按 taskType 限定，不按 batchConfigId
- Rule: 新端点 `GET /api/mail/batch-send/executions` 的筛选条件是
  `task_type = BatchSendControlService.TASK_TYPE`（`"MANUAL_INITIAL_OUTREACH"`），
  **不带** `batch_config_id` 条件，因此独立手动执行（该列为 `NULL`）必须出现在结果中。
- Applies to: `BatchSendConfigController.listAllExecutions`、`TaskExecutionService.listRecentByTaskType`。
- Violation consequence: 沿用 `findRecentByBatchConfigId` 的话，`WHERE batch_config_id = :id`
  会天然排除独立执行（`TaskExecutionRepository.kt:11-14` 的注释已写明这一点），
  需求描述第 3 条直接落空。
- 来源: original（grep 实证见现状审计）

### Invariant I2-4: limit 双重钳制
- Rule: `limit` 在 Service 层 `require(limit in 1..200)`，在 Controller 层先
  `coerceIn(1, 200)` 再传入 —— 与 `listConfigExecutions`（`BatchSendConfigController.kt:112-118`）
  和 `listRecentByBatchConfigId`（`TaskExecutionService.kt:37-40`）完全同构。
- Applies to: 新增的 Service 方法与 Controller 端点。
- Violation consequence: 只在 Service 层 `require` 会让非法 `limit` 变成 500
  （`GlobalExceptionHandler` 对 `IllegalArgumentException` 映射为 400，但 Controller 不钳制时
  合法请求也可能因手抖参数变 400，与既有端点行为不一致）。
- 来源: K-custom-exception-http-status-mapping（就地复核：`require` 抛
  `IllegalArgumentException`，`GlobalExceptionHandler` 映射 400，符合预期）

## 样式契约

### S2-1: 手动执行页签的日志入口（复用既有 class，零新增 CSS）

**复用**：`.button.secondary`（全站按钮基类）；容器 `.batch-manual-actions`
（`styles.css:9152-9158`，`display:flex; justify-content:flex-end; gap:14px`）与
`.batch-manual-actions-sticky`（`styles.css:9166-9178`）保持不变。
`.batch-manual-actions > span { margin-right: auto }`（`styles.css:9160-9164`）已经把说明文字推到左侧，
因此新按钮插在 `#batchManualExecuteBtn` **之前**即自然贴在主按钮左边，**无需任何新增 CSS**。

**DOM 骨架**（`index.html` 现 1497-1500 行的整块，改后逐字为）：

```html
<div class="batch-manual-actions batch-manual-actions-sticky">
    <span>执行前将展示来源配置与变更项，请确认后继续。</span>
    <button class="button secondary" id="batchManualRecentLogBtn" type="button">最近执行日志</button>
    <button class="button primary" id="batchManualExecuteBtn">确认并执行</button>
</div>
```

- 禁止项：inline style；新增 class；修改 `.batch-manual-actions` / `.batch-manual-actions-sticky`
  的任何声明；改动 `#batchManualExecuteBtn` 的 class、id 或文案。
- `.batch-manual-actions-sticky { z-index: 2 }` 低于 `.batch-log-drawer { z-index: 4 }`，
  抽屉打开时会正常盖住这条工具条，无需额外处理。

### S2-2: 执行记录下拉的选项文案（无 CSS，仅文案契约）

`#batchLogExecutionSelect` 的 `<option>` 文案格式固定为：

```
{开始时间} | {状态中文} | {触发类型中文}{来源后缀}
```

- 时间用 `formatDateTime(e.startedAt)`；状态用 `statusLabel(e.status)`（`app.js:15282`）；
  触发类型用 `triggerTypeLabel(e.triggerType)`（`app.js:15294`）——
  **注意**：当前 `config` 模式的实现（`app.js:15053`）直接输出裸 `e.triggerType`（如 `MANUAL`），
  本计划统一改为走 `triggerTypeLabel`。
- `{来源后缀}`：`batchConfigId == null` 时为 ` | 独立执行`，否则为空串。
- 整串必须经 `escapeHtml` 后再拼进 `<option>`（沿用 `app.js:15054` 的现有写法）。

### S2-3: 缓存键

`index.html` 三处缓存键逐字改为 `20260817-v2-batch-manual-log-entry`，
并同步 `batchSendTaskConsoleVisualFix.test.js` 中的三条断言字符串（M-2）。

## 现状审计

### `task_execution` 表（本计划唯一触及的数据存储）

- **Schema 相关列**：`id`、`task_type`、`trigger_type`、`status`、`started_at`、`finished_at`、
  `success_count`、`failure_count`、`result_summary`、`batch_config_id`（可空，
  由 `V73__add_batch_config_id_to_task_execution.sql` 引入）。
- **本计划只读，不新增写路径。** 现有写路径（供 interaction 判断，未改动）：
  `TaskExecutionService.runAndRecordWithResult`（`:96-107` 建 RUNNING 行、`:139-148` 回写终态）、
  `runAndRecord`、`updateProgressCounts`（`:64`）。
- **相关读路径**：

  ```
  $ grep -rn "findRecentByTaskType\|findRecentByBatchConfigId" src/main src/test
  src/main/kotlin/.../task/repository/TaskExecutionRepository.kt:32   findRecentByTaskType 定义
  src/main/kotlin/.../task/repository/TaskExecutionRepository.kt:47   findRecentByBatchConfigId 定义
  src/main/kotlin/.../task/controller/TaskProgressController.kt:85    findRecentByTaskType 生产调用（带 clampedLimit）
  src/main/kotlin/.../task/service/TaskExecutionService.kt:70         findRecentByTaskType("AUTO_REPLY_ALL", limit)
  src/test/kotlin/.../task/controller/TaskExecutionControllerTest.kt  ×7 Mockito stub
  ```

  **结论：`findRecentByTaskType(taskType, limit)` 已存在且已有生产调用先例，本计划
  不需要新增任何 repository 方法或 `@Query`。**（这一点很关键 —— CLAUDE.md
  `K-plan-quantified-claims-need-grep-receipts` 提醒过「Spring Data JDBC `@Query` 返回 DTO 投影
  在本仓库零先例」，本计划因此刻意避开新查询。）
- **`batch_config_id = NULL` 会被现有列表查询排除**，仓库代码自己写着：
  `TaskExecutionRepository.kt:11-14`
  「Covers MANUAL + SCHEDULED executions; rows with batch_config_id = null (independent
  manual runs) are excluded naturally by the WHERE clause.」
  以及 `TaskExecutionService.kt:42-46` 的同义注释。这是需求描述第 3 条的根因。

### `BatchConfigExecutionSummary` DTO

- 定义：`BatchSendConfigController.kt:422-434`，11 个字段，**无 `batchConfigId`**。
- **构造点全仓 1 处**：

  ```
  $ grep -rn "BatchConfigExecutionSummary(" src/
  src/main/kotlin/.../mail/controller/BatchSendConfigController.kt:237   （toSummary 内）
  src/main/kotlin/.../mail/controller/BatchSendConfigController.kt:422   （data class 定义）
  ```

  无测试构造该 DTO，追加一个可空字段无破坏面。
- **消费点**：`listConfigExecutions`（`:112-118`）→ 前端 `loadBatchLogExecutions`
  （`app.js:15043-15070`）只读 `executionId` / `startedAt` / `status` / `triggerType`。

### 抽屉的模式状态机（`app.js`）

- `batchTaskState` 字段：`logConfigId` / `logExecutionId` / `logMode` / `logRefreshTimer`
  （声明于 `app.js:13239-13243`，`resetBatchTaskState` 于 `:13288-13292` 复位）。
- `openBatchConfigLogs(configId, executionId)`（`:14978-14989`）：`logMode = "config"`，
  下拉 `hidden = false`，走 `loadBatchLogExecutions`。
- `openBatchExecutionLogs(executionId)`（`:14992-15007`）：`logMode = "execution"`，
  `logConfigId = null`，**下拉 `hidden = true`**，直接 `loadBatchLogDetail(null, executionId)`。
- `loadBatchLogDetail`（`:15071-15100`）已按 `configId == null || logMode === "execution"`
  选择 `/executions/{id}` 还是 `/configs/{id}/executions/{id}` —— 该判断保持不动。
- **违例点**：`app.js:15444-15452` 下拉 `change` 监听
  `if (executionId && batchTaskState.logConfigId)` —— 见 I2-1。
- **违例点**：`app.js:13334-13335` `switchBatchSendTab` 内的
  `closeBatchLogDrawer(); clearBatchLogRefreshTimer();` —— 见 I2-2。
  注意 `openManualTabFromConfig`（`:14453-14458`）自己就会调 `switchBatchSendTab("manual")`。
- **手动执行页签现有按钮**：整个 `#batchManualPanel`（`index.html:1327-1501`）内只有
  `#batchManualClearSourceBtn`（清除选择）与 `#batchManualExecuteBtn`（确认并执行），
  **没有任何日志入口**。

  ```
  $ grep -n "<button" src/main/resources/static/index.html | awk -F: '$1>=1327 && $1<=1501'
  1337:  batchManualClearSourceBtn
  1499:  batchManualExecuteBtn
  ```

- **浏览器实测（非推断）**：手动执行后自动开抽屉的两条路径均正常 —— 有来源配置时
  `logMode="config"`、下拉填出 1 条、指标渲染出「目标 2289 / 成功 10 / 剩余 2279」；
  独立执行时 `logMode="execution"`、走 `/executions/9001`、同样渲染成功。
  **问题不在开抽屉，在于关掉后没有回路。**

### 前端样式盘点

- **可复用 class**：`.button` / `.button.secondary` / `.button.primary`；
  `.batch-manual-actions`（`styles.css:9152-9158`）、`.batch-manual-actions > span`
  （`:9160-9164`）、`.batch-manual-actions-sticky`（`:9166-9178`）；
  `.bsc-input.bsc-select`（`#batchLogExecutionSelect` 现用，`index.html:1509`）。
- **设计基准 token**：sticky 工具条底色 `rgba(255, 255, 255, .96)` + `backdrop-filter: blur(8px)`；
  按钮间距 `gap: 14px`；工具条内边距 `14px 28px`；抽屉 `z-index: 4` > 工具条 `z-index: 2`。
- **DOM 结构约定**：动作按钮放在 `.batch-manual-actions` 内、说明文字用 `<span>` 打头
  （靠 `margin-right: auto` 左推）。
- **改动前基线**：`index.html:1497-1500`（动作条整块）、`app.js:15043-15070`
  （`loadBatchLogExecutions`）、`app.js:14992-15007`（`openBatchExecutionLogs`）、
  `app.js:15444-15452`（下拉监听）、`app.js:13326-13344`（`switchBatchSendTab`）—— 逐字内容已在上文引出。

### Interaction points

1. **新端点写出的 `batchConfigId` × 前端下拉文案**：后端返回 `null` ↔ 前端渲染「独立执行」后缀（S2-2）。
2. **`logMode` × `loadBatchLogDetail` 的 URL 选择**：`"execution"` 模式必须命中
   `/executions/{id}`，否则独立执行会被拼成 `/configs/null/executions/{id}` 而 404。
3. **`switchBatchSendTab` 不再关抽屉 × 配置编辑器**：编辑器渲染在 `#batchScheduledPanel` 内，
   抽屉会盖住它，因此进入编辑器时必须显式关抽屉（见 T2-B3）。

## 实现方案

### 阶段 A：后端全局执行列表（I2-3 / I2-4）

- **T2-A1** `TaskExecutionService`：新增

  ```kotlin
  fun listRecentByTaskType(taskType: String, limit: Int): List<TaskExecution> {
      require(limit in 1..200) { "limit must be between 1 and 200" }
      return repository.findRecentByTaskType(taskType, limit)
  }
  ```

  紧邻 `listRecentByBatchConfigId`（`:37-40`）放置，写法与之完全同构。遵守 I2-4。
- **T2-A2** `BatchSendConfigController`：
  - `BatchConfigExecutionSummary` 追加 `val batchConfigId: Long?`（放在 `executionId` 之后）；
    `toSummary`（`:232-250`）对应补 `batchConfigId = execution.batchConfigId`。
  - 新增端点，紧邻 `getExecutionDetail`（`:136-146`）放置：

    ```kotlin
    @GetMapping("/executions")
    fun listAllExecutions(
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<List<BatchConfigExecutionSummary>> {
        val clamped = limit.coerceIn(1, 200)
        val executions = taskExecutionService.listRecentByTaskType(
            BatchSendControlService.TASK_TYPE, clamped
        )
        return ResponseEntity.ok(executions.map { toSummary(it) })
    }
    ```

  遵守 I2-3 / I2-4。

### 阶段 B：前端状态机与入口（I2-1 / I2-2 / S2-1 / S2-2）

- **T2-B1** `index.html`：按 S2-1 在动作条中插入 `#batchManualRecentLogBtn`。
- **T2-B2** `app.js` 新增两个函数：
  - `openBatchRecentLogs(executionId)`：打开抽屉，标题设为「执行日志」，
    `logMode = "execution"`、`logConfigId = null`、下拉 `hidden = false`，
    先写 `logExecutionId` 再请求（M-3），然后调 `loadBatchGlobalExecutions(executionId)`。
  - `loadBatchGlobalExecutions(executionId)`：GET
    `/api/mail/batch-send/executions?limit=50`，按 S2-2 生成 `<option>`，
    目标记录取 `executionId ?? 首条`，写入 `logExecutionId` 与 `select.value`，
    再 `loadBatchLogDetail(null, targetId)`；空列表时调 `clearBatchLogDisplay()`。
    结构照抄 `loadBatchLogExecutions`（`:15043-15070`），包括其「响应回来先确认目标仍是当前抽屉」
    的守卫（M-3）。
- **T2-B3** `app.js` 三处就地修改：
  1. `switchBatchSendTab`（`:13334-13335`）删掉 `closeBatchLogDrawer(); clearBatchLogRefreshTimer();`
     两行（I2-2）。
  2. `openBatchConfigEditor`（`:13496`）进入编辑器前调一次 `closeBatchLogDrawer()`
     （interaction point 3）。
  3. 下拉 `change` 监听（`:15444-15452`）改为按 `logMode` 分派（I2-1）：
     `"config"` → `loadBatchLogDetail(batchTaskState.logConfigId, id)`；
     `"execution"` → `loadBatchLogDetail(null, id)`。
- **T2-B4** `app.js` `openBatchExecutionLogs`（`:14992-15007`）：保留函数名与签名
  （`batchManualExecutionLog.test.js` 断言 `confirmManualExecution` 调它），
  函数体改为 `openBatchRecentLogs(executionId)` 的薄封装 —— 下拉不再 `hidden`，
  用户执行完可直接在下拉里切别的记录。`executionId` 为空时保持现有的
  `showStatus("执行已启动，但未能定位到日志", "warn")` 提前返回。
- **T2-B5** `app.js` `loadBatchLogExecutions`（`:15053`）的 `<option>` 文案改为走
  `triggerTypeLabel`，与 S2-2 统一。
- **T2-B6** `index.html` 三处缓存键按 S2-3 改值。
- **T2-B7** 绑定 `#batchManualRecentLogBtn` 的 `click` → `openBatchRecentLogs(null)`，
  加在 `bindBatchSendTaskEvents` 的「Log drawer」段（`app.js:15437` 附近）。

### 阶段 C：测试

- **T2-C1** `BatchSendExecutionDetailTest.kt` 追加：
  - `GET /api/mail/batch-send/executions` 返回列表，且包含 `batchConfigId = null` 的独立执行行；
  - `limit` 传 0 / 500 时被钳到 1 / 200（用 Mockito 断言传给 service 的实参）；
  - 返回项的 `batchConfigId` 与 `TaskExecution.batchConfigId` 一致。
- **T2-C2** `batchManualExecutionLog.test.js` 追加：
  - `switchBatchSendTab` 的函数体源码中**不含** `closeBatchLogDrawer`（I2-2 的源码级断言）；
  - 下拉 `change` 分派：`logMode="execution"` 且 `logConfigId=null` 时仍调
    `loadBatchLogDetail(null, id)`（I2-1）；
  - `openBatchExecutionLogs` 不再把下拉设为 `hidden = true`。
- **T2-C3** `batchSendTaskConsoleVisualFix.test.js` 缓存键断言改值；
  追加断言 `index.html` 中 `#batchManualRecentLogBtn` 存在且位于 `#batchManualExecuteBtn` 之前。

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt` | 新增 `listRecentByTaskType` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` | 新增 `GET /executions`；DTO 追加 `batchConfigId`；`toSummary` 补字段 |
| 3 | `src/main/resources/static/index.html` | 新增 `#batchManualRecentLogBtn`；三处缓存键改值 |
| 4 | `src/main/resources/static/app.js` | 新增 `openBatchRecentLogs` / `loadBatchGlobalExecutions`；改 `switchBatchSendTab` / `openBatchConfigEditor` / 下拉监听 / `openBatchExecutionLogs` / `loadBatchLogExecutions` 文案；新增事件绑定 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendExecutionDetailTest.kt` | 新端点用例 |
| 6 | `src/test/js/batchManualExecutionLog.test.js` | I2-1 / I2-2 回归断言 |
| 7 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 缓存键 + 新按钮位置断言 |

文件数 7 ≤ 10；子系统 2（前端静态资源 / Kotlin 后端）≤ 2。
**无 styles.css 改动** —— S2-1 明确论证了零新增 CSS。

## 验证命令

> 全量回归、构建、`node --check`、`git diff --check` 一律使用主计划 `## 共享审计 / X-3`。

```bash
# 本计划新增的后端用例（单类）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=BatchSendExecutionDetailTest

# 本计划修改的前端用例（K-js-test-invocation-surface：verify.sh 不覆盖这些文件）
node --test src/test/js/batchManualExecutionLog.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js

# 抽屉族回归（P1 已建立的断言不得被本计划打破）
node --test src/test/js/batchLogDrawerLayout.test.js
node --test src/test/js/batchExecutionLogTimeline.test.js
node --test src/test/js/batchSendTaskConsoleInteraction.test.js
```

通过判据：`mvn test -Dtest=...` 输出 `Tests run: N, Failures: 0, Errors: 0`；
每条 `node --test` 输出 `# fail 0`；退出码均为 0。

## 验收标准

- **I2-1**：`batchManualExecutionLog.test.js` 的「`logMode="execution"` 且 `logConfigId=null`
  仍能切换记录」断言通过；`grep -n "batchTaskState.logConfigId" src/main/resources/static/app.js`
  的结果中，下拉 `change` 监听所在行段不再用它做模式判别。
- **I2-2**：同文件「`switchBatchSendTab` 源码不含 `closeBatchLogDrawer`」断言通过；
  `grep -n "closeBatchLogDrawer" src/main/resources/static/app.js` 的调用点恰为 3 处
  ——`closeBatchSendTaskModal`、抽屉关闭按钮绑定、`openBatchConfigEditor`。
- **I2-3**：`BatchSendExecutionDetailTest` 中「列表包含 `batchConfigId = null` 行」用例通过；
  `grep -n "findRecentByBatchConfigId" src/main/kotlin/.../mail/controller/BatchSendConfigController.kt`
  在新端点内**无命中**。
- **I2-4**：同测试类中 limit 钳制两条用例通过。
- **S2-1**：`git diff src/main/resources/static/styles.css` 为空（本计划零 CSS 改动）；
  `batchSendTaskConsoleVisualFix.test.js` 的按钮位置断言通过；
  `grep -n 'id="batchManualRecentLogBtn"' src/main/resources/static/index.html` 恰 1 处且带
  `class="button secondary"`，无 inline style。
- **S2-2**：`grep -n "triggerTypeLabel" src/main/resources/static/app.js` 在
  `loadBatchLogExecutions` 与 `loadBatchGlobalExecutions` 两处均有命中。
- **S2-3 / M-2**：`grep -c "20260817-v2-batch-manual-log-entry" src/main/resources/static/index.html`
  输出 `3`；同串在 `batchSendTaskConsoleVisualFix.test.js` 中出现 3 次。
- **回归**：执行主计划 X-3 的全量测试与构建命令通过；`BatchSendExecutionDetailTest.kt`
  原有 14 条用例（`:94` 起）全绿。

## 人工验收清单

### A2-1: 手动执行页签能直接看日志
- 前置条件：系统里至少有 1 条批量发送执行记录（定时或手动均可）。
- 操作步骤：
  1. 打开批量邮件任务控制台 → 点「手动执行」页签。
  2. 页面拉到底，点动作条上的「最近执行日志」。
- 预期结果：右侧抽屉打开，标题「执行日志」，顶部下拉已选中最近一条，形如
  `2026/08/16 10:39:14 | 已完成 | 手动触发`；下方指标卡、批次时间线正常渲染。
  全程**没有**切换到「定时任务」页签。
- 覆盖：需求描述 1

### A2-2: 下拉可自由切换记录
- 前置条件：至少 2 条执行记录。
- 操作步骤：在 A2-1 打开的抽屉里，展开顶部下拉，选中另一条记录。
- 预期结果：指标卡、失败/跳过原因、批次时间线全部刷新为所选记录的数据；
  下拉显示的文案与所选项一致。
- 覆盖：I2-1、需求描述 1

### A2-3: 独立手动执行的日志能找回
- 前置条件：无。
- 操作步骤：
  1. 「手动执行」页签，**不选**任何来源配置（「当前来源」显示「独立手动执行（未关联定时配置）」）。
  2. 填好参数，点「确认并执行」→「确认执行」。
  3. 待抽屉自动打开后，点抽屉右上角 × 把它关掉。
  4. 重新点动作条的「最近执行日志」。
- 预期结果：下拉第一条即为刚才那次执行，文案末尾带 ` | 独立执行`；选中后能看到它的
  指标与时间线。（改动前：这条记录在关掉抽屉后**无法**再打开。）
- 覆盖：I2-3、需求描述 3

### A2-4: 切页签不丢抽屉（回归）
- 前置条件：一次正在运行中的批量发送（状态显示「运行中」，抽屉顶部有实时进度条）。
- 操作步骤：
  1. 抽屉打开、显示实时进度时，点「定时任务」页签。
  2. 再点回「手动执行」页签。
  3. 观察实时进度区的「已处理 N」数字。
- 预期结果：两次切换过程中抽屉**始终保持打开**；实时进度数字仍在持续增长（说明 1.5 秒轮询
  没被清掉）。（改动前：切页签抽屉立刻消失，轮询停止。）
- 覆盖：I2-2、需求描述 2

### A2-5: 进配置编辑器时抽屉让位
- 前置条件：抽屉处于打开状态。
- 操作步骤：切到「定时任务」页签 → 点任一行的「编辑」。
- 预期结果：抽屉自动关闭，配置编辑表单完整可见、不被遮挡。
- 覆盖：现状审计 interaction point 3

### A2-6: 既有入口未受影响（回归）
- 前置条件：某条定时任务有多条执行记录。
- 操作步骤：
  1. 「定时任务」页签 → 该行「日志」。
  2. 展开下拉切换记录。
  3. 「手动执行」页签选中该配置为来源 → 执行一轮。
- 预期结果：步骤 1、2 与改动前完全一致（下拉只列该配置的记录、切换正常）；
  步骤 3 执行后自动打开的抽屉里，下拉仍只列该配置的执行记录（不是全局列表）。
- 覆盖：What must NOT change 第 1 条

### A2-7: 跨路径 —— 独立执行写入 → 全局列表读到（interaction point 1）
- 前置条件：数据库 `task_execution` 中存在 `task_type='MANUAL_INITIAL_OUTREACH'` 且
  `batch_config_id IS NULL` 的行（由 A2-3 步骤 2 产生，或直接 SQL 确认：
  `SELECT id, batch_config_id, trigger_type, status FROM task_execution
   WHERE task_type='MANUAL_INITIAL_OUTREACH' ORDER BY started_at DESC LIMIT 10;`）。
- 操作步骤：浏览器直接访问 `/api/mail/batch-send/executions?limit=50`。
- 预期结果：返回 JSON 数组中能找到该 `executionId`，其 `batchConfigId` 为 `null`，
  `triggerType` 为 `MANUAL`；同一数组里也能看到 `batchConfigId` 非空的定时/配置手动记录。
- 覆盖：I2-3、interaction point 1
