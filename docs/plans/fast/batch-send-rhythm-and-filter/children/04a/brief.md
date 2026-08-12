# 04a · cron 预览接口与执行时间字段（后端）

> 主计划：`batch-send-rhythm-and-filter-00-master.md`
> 覆盖需求：第 6 条（后端）、第 7 条（后端）
> 依赖：无（不触碰 dailyCap / roundsPerRun / regions，可与 01/02/03 并行）
> 后继：04b（前端消费这两项能力）

## 需求描述

### Observable outcome

1. 新增 cron 预览接口：给定一个 cron 表达式，返回它是否合法，以及自当前时刻起**最近 5 次**将要执行的时间。非法表达式返回明确的错误原因而非 500。
2. `GET /api/mail/batch-send/configs`（列表）与 `GET /configs/{id}`（详情）返回的配置对象新增两个字段：
   - `nextFireTime` —— 该配置下一次自动触发的时间；`autoEnabled = false` 或 cron 不合法时为 `null`
   - `lastExecutedAt` —— 该配置最近一次执行的开始时间（取自 `task_execution.started_at`）；从未执行过为 `null`
3. 列表接口在 N 条配置下对 `task_execution` 的查询次数为 **1 次**（批量聚合），不随配置数增长。

### What must NOT change

- `BatchSendTaskConfigView` 现有全部字段的名称、类型与取值。
- `list()` / `get()` 的排序、过滤（`q` 参数）与软删除语义。
- `BatchSendScheduler` 的调度注册逻辑、`ConfigCronTrigger`、`BatchSendCronChangedEvent` 发布时机——本计划**只读** cron，不改调度。
- `normalizeAndValidate()` 中已有的 `CronExpression.parse(fields.cron)` 校验（`BatchSendTaskConfigService.kt:233-238`）保持不变；本计划新增的预览接口是**独立**的只读校验，不替代它。
- `task_execution` 表结构、`TaskExecutionService.runAndRecordWithResult()` 的写入行为。
- 现有 `GET /configs/{id}/executions` 执行日志接口的响应形态。
- 前端不改（→ 04b）。

### Out of scope

- 前端 cron 输入框、测试按钮、执行时间列 → 04b
- 修改调度器行为、支持 cron 之外的调度语法
- 时区配置化（本计划固定用 JVM 默认时区，与 `CronTrigger` 一致）
- 执行**结束**时间、执行状态等其他执行元数据（`lastExecutedAt` 只取 `started_at`）

## 关键不变量

### Invariant I-1: nextFireTime 必须与调度器同源同语义
- Rule: `nextFireTime` 用 `org.springframework.scheduling.support.CronExpression.parse(cron).next(LocalDateTime.now())` 计算。这与 `BatchSendScheduler` 实际使用的 `CronTrigger(cron)`（`BatchSendScheduler.kt:115`）同属 Spring 的 6 段 cron 实现，语义与时区一致。**禁止**引入第三方 cron 库或自写解析。
- Applies to: `BatchSendTaskConfigService` 新增的 `nextFireTime` 计算、cron 预览方法。
- Violation consequence: 用不同实现（例如 Quartz 语法或 5 段 Unix cron）计算，界面显示的「下次执行」与实际触发时刻不符，运营据此排查会被误导。项目已有同源先例：`TaskExecutionService.nextPollTime()`（`:65-73`）就用 `CronExpression.parse`。
- 来源: original（由 `BatchSendScheduler.kt:115` 与 `TaskExecutionService.kt:66-70` 审计得出）

### Invariant I-2: autoEnabled = false 时 nextFireTime 必须为 null
- Rule: 配置未启用时不返回下次执行时间。`BatchSendScheduler.reload()` 只为 `findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc()` 的结果注册 future（`BatchSendScheduler.kt:60`），且 `triggerBatchSend()` 会二次检查 `!config.autoEnabled` 后跳过（`:83-86`）——未启用的配置**根本不会触发**。
- Applies to: `BatchSendTaskConfigService.toView()`。
- Violation consequence: 停用的任务在列表里显示一个「下次执行：明天 09:00」，运营会以为它还会跑。
- 来源: original

### Invariant I-3: cron 解析失败必须降级为 null / 结构化错误，不得抛出
- Rule: `toView()` 中的 cron 解析必须包在 `runCatching` / try-catch 中，失败返回 `null`；预览接口对非法 cron 返回 `200` + `{valid: false, message: "..."}`，**不返回 4xx/5xx**。
- Applies to: `toView()`、cron 预览方法与其控制器端点。
- Violation consequence: 存量库中若有一条 cron 不被 `CronExpression` 接受（例如历史遗留的 Quartz `?` 用法差异），整个配置列表接口会 500，运营连列表都打不开。这类「一条脏数据打挂整个列表」的失效模式必须在设计上排除。
- 来源: original（参照 `BatchSendScheduler.ConfigCronTrigger.nextExecutionTime()` 已有的 `catch { null }` 降级，`:113-118`）

### Invariant I-4: lastExecutedAt 的批量查询必须是单次聚合
- Rule: `list()` 必须先用**一次** `SELECT batch_config_id, MAX(started_at) ... WHERE batch_config_id IN (:ids) GROUP BY batch_config_id` 取回 map，再逐行填充 View。禁止在 `toView()` 内部对每行发起查询。
- Applies to: `BatchSendTaskConfigService.list()`、新增的 `TaskExecutionRepository` 查询。
- Violation consequence: N+1 查询；配置数增长后列表接口线性变慢。
- 来源: original

### Invariant I-5: lastExecutedAt 覆盖手动与定时两种触发
- Rule: 取值不按 `trigger_type` 过滤——`MANUAL` 与 `SCHEDULED` 的执行都算「最近一次执行」。但**只统计带 `batch_config_id` 的执行**：独立手动执行（`batch_config_id IS NULL`，见 `TaskExecution.kt:23-24` 的注释）不归属任何配置，天然被 `WHERE batch_config_id IN (:ids)` 排除。
- Applies to: 新增的 `TaskExecutionRepository` 查询。
- Violation consequence: 若加 `trigger_type = 'SCHEDULED'` 过滤，运营刚点过「手动」的任务仍显示「从未执行」。
- 来源: K-batch-task-config-snapshot-log-identity（configId 是执行归属的稳定身份）

## 现状审计

### 读路径：`BatchSendTaskConfigView` 的生产者

`BatchSendTaskConfigService.toView(row)`（`:330-351`）是**唯一**的 row→View 转换点，被 5 个方法调用：
`list()`（`:37`）、`get()`（`:43`）、`create()`（`:47-75` 内）、`update()`（`:76-105` 内）、`setEnabled()`（`:106-129` 内）。

→ 在 `toView()` 加字段可一次覆盖 5 个出口。但 `lastExecutedAt` 需要外部数据，故 `toView` 需要一个可选形参（I-4：`list()` 预加载，其余四个单条查询即可）。

### 控制器现状

`BatchSendConfigController`（`@RequestMapping("/api/mail/batch-send")`，`:37`）现有配置相关端点：

| 方法 | 路径 | 行号 |
|---|---|---|
| GET | `/configs` | `:50` |
| POST | `/configs` | `:54` |
| GET | `/configs/{id}` | `:58` |
| PUT | `/configs/{id}` | `:62` |
| PATCH | `/configs/{id}/enabled` | `:70` 附近 |
| DELETE | `/configs/{id}` | `:76` |
| POST | `/configs/{id}/execute` | `:82` |
| POST | `/manual-executions` | `:86` |
| GET | `/configs/{id}/executions` | `:90` |
| GET | `/configs/{id}/executions/{executionId}` | `:100` |
| GET | `/executions/{executionId}` | `:116` |
| POST | `/executions/{executionId}/cancel` | `:129` |

→ 新增端点应置于 CRUD 段落之后、执行段落之前；路径选 `POST /api/mail/batch-send/cron/preview`（用 POST 而非 GET，避免 cron 表达式中的 `?` `*` 在 query string 中转义出错——**这是选 POST 的实质理由，不是风格偏好**）。

### `task_execution` 与其仓储

`TaskExecution`（`task/domain/TaskExecution.kt`）关键字段：`taskType`、`triggerType`、`status`、`startedAt`、`finishedAt`、`successCount`、`failureCount`、`batchConfigId: Long? = null`（`:24`，注释明确「Source batch_send_task_config id at launch; null for independent manual runs. Soft-delete safe.」）。

`TaskExecutionRepository`（全文已审计）现有 6 个自定义查询，其中两个按 `batch_config_id`：
- `findRecentByBatchConfigId(batchConfigId, limit)` —— 单配置，取最近 N 条完整行
- `sumSuccessCountByBatchConfigIdBetween(...)` —— 单配置，当日成功数

→ **无批量聚合查询**，本计划需新增。

`TaskExecutionService` 现有 `nextPollTime()`（`:65-73`）已示范了 `CronExpression.parse` + try-catch 降级的写法，本计划的 cron 计算应与之同形态（I-1、I-3）。

### 交互点

| # | 写路径 | 读路径 | 处理 |
|---|---|---|---|
| X-1 | `TaskExecutionService.runAndRecordWithResult()` 写入带 `batchConfigId` 的 `task_execution` 行（`:85-97`） | 新增的批量 MAX(started_at) 查询 | I-5：不按 trigger_type 过滤；`batchConfigId = null` 的独立手动执行自动排除 |
| X-2 | `BatchSendTaskConfigService.update()` 改 cron 并 `publishReload()` | `toView()` 的 `nextFireTime` 计算 | 同一次请求内先保存后转 View，返回的 `nextFireTime` 即为新 cron 的结果；无需额外同步 |
| X-3 | `softDelete()` 置 `deletedAt` | `list()` 的 `findAllActive...` | 软删除配置不出现在列表，其历史 `task_execution` 行仍保留 `batch_config_id`（外键 nullable，K-batch-task-config-snapshot-log-identity），不影响本计划 |
| X-4 | 存量库中可能存在 `CronExpression` 不接受的 cron 字符串 | `toView()` | I-3：必须降级为 null，不得让列表接口 500 |

## 实现方案

### A-1 `TaskExecutionRepository.kt`：新增批量聚合查询（I-4、I-5）

文件：`src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt`

在 `findRecentByBatchConfigId` 之后新增：

```kotlin
@Query(
    """
    SELECT batch_config_id AS batch_config_id, MAX(started_at) AS last_started_at
    FROM task_execution
    WHERE batch_config_id IN (:batchConfigIds)
    GROUP BY batch_config_id
    """
)
fun findLastStartedAtByBatchConfigIds(batchConfigIds: Collection<Long>): List<BatchConfigLastExecution>
```

同文件（或 `task/domain/`）新增投影 data class：

```kotlin
data class BatchConfigLastExecution(
    val batchConfigId: Long,
    val lastStartedAt: LocalDateTime
)
```

> **调用方必须先判空**：`batchConfigIds` 为空集合时 `IN ()` 是非法 SQL，`list()` 中须 `if (ids.isEmpty()) emptyMap() else ...`。

---

#### ⚠ 未经本仓库验证的框架假设 —— 执行前必须先做 spike

本设计假定 **Spring Data JDBC 的 `@Query` 支持返回非实体 DTO 投影**。这是框架层面的通行做法，但**本仓库没有任何先例**。

grep 实测：`src/main/kotlin` 下全部 `@Query` 方法的返回类型只有三类——实体（`MailRecord?` / `TaskExecution` / `MailSendAttempt?` …）、标量（`Long` / `Int`）、`List<String>`（`findDistinctDomains()`）。**零个 DTO 投影**。

**执行顺序要求**：先写 `A-1` 的仓储方法 + 一个最小用例跑通，**再**继续 A-2~A-6。若 DTO 投影不работ（典型症状：`ConverterNotFoundException` 或列名→构造参数映射失败），按以下**已验证有先例**的方案降级，二选一：

- **降级方案 甲**（推荐）：改用 `List<String>` 返回，SQL 侧拼接
  ```sql
  SELECT CONCAT(batch_config_id, ',', MAX(started_at)) ...
  ```
  服务层 split 解析。有先例（`findDistinctDomains(): List<String>`），但字符串拼接解析脆弱。
- **降级方案 乙**：改用 `NamedParameterJdbcTemplate` 直接查询并用 `RowMapper` 映射。执行前须 grep 确认本仓库是否已注入 `NamedParameterJdbcTemplate`；若无则该方案会引入新依赖，需重新评估。

无论采用哪种，**I-4（单次聚合）必须保持**——降级不得退化为 N+1。选定方案后在本节记录实际结论，并在提交信息中说明。

### A-2 `TaskExecutionService.kt`：暴露批量查询（I-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt`

```kotlin
/** 批量取每个配置最近一次执行的开始时间（含 MANUAL 与 SCHEDULED；不含 batchConfigId 为 null 的独立手动执行）。 */
fun lastExecutedAtByBatchConfigIds(batchConfigIds: Collection<Long>): Map<Long, LocalDateTime> {
    if (batchConfigIds.isEmpty()) return emptyMap()
    return repository.findLastStartedAtByBatchConfigIds(batchConfigIds)
        .associate { it.batchConfigId to it.lastStartedAt }
}
```

### A-3 `BatchSendTaskConfig.kt`：View 加两个字段

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`

`BatchSendTaskConfigView` 末尾（`updatedAt` 之后）加：
```kotlin
val nextFireTime: LocalDateTime? = null,
val lastExecutedAt: LocalDateTime? = null
```
（带默认值，避免打断既有构造点）

### A-4 `BatchSendTaskConfigService.kt`：填充两个字段 + cron 预览（I-1、I-2、I-3、I-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`

1. 构造函数新增依赖 `private val taskExecutionService: TaskExecutionService`
2. `toView(row)` 改为 `toView(row: BatchSendTaskConfig, lastExecutedAt: LocalDateTime? = null)`，末尾加：
   ```kotlin
   nextFireTime = computeNextFireTime(row.autoEnabled, row.cron),
   lastExecutedAt = lastExecutedAt
   ```
3. 新增私有方法（I-1、I-2、I-3）：
   ```kotlin
   private fun computeNextFireTime(autoEnabled: Boolean, cron: String): LocalDateTime? {
       if (!autoEnabled) return null
       return runCatching { CronExpression.parse(cron).next(LocalDateTime.now()) }.getOrNull()
   }
   ```
4. `list()`（`:30-38`）改为：
   ```kotlin
   val ids = rows.mapNotNull { it.id }
   val lastMap = taskExecutionService.lastExecutedAtByBatchConfigIds(ids)
   return rows.map { toView(it, lastMap[it.id]) }
   ```
   （I-4：单次聚合）
5. `get(id)`（`:40-44`）改为传入 `taskExecutionService.lastExecutedAtByBatchConfigIds(listOf(id))[id]`
6. `create()` / `update()` / `setEnabled()` 内的 `toView(saved)` 调用：
   - `create()` 保持 `toView(saved)`（新建必然无执行历史，`lastExecutedAt` 为 null）
   - `update()` / `setEnabled()` 传入单条查询结果（配置可能已有执行历史）
7. 新增公共方法（I-1、I-3）：
   ```kotlin
   /** cron 预览：只读校验 + 最近 N 次触发时间。非法表达式返回 valid=false，不抛异常。 */
   fun previewCron(cron: String, count: Int = 5): CronPreviewResult {
       val trimmed = cron.trim()
       if (trimmed.isEmpty()) return CronPreviewResult(false, "cron 表达式不能为空", emptyList())
       val expr = runCatching { CronExpression.parse(trimmed) }.getOrElse { e ->
           return CronPreviewResult(false, "不是合法的 Spring cron 表达式（6 段，秒 分 时 日 月 周）：${e.message}", emptyList())
       }
       val times = mutableListOf<LocalDateTime>()
       var cursor = LocalDateTime.now()
       repeat(count.coerceIn(1, 20)) {
           val next = expr.next(cursor) ?: return@repeat
           times.add(next)
           cursor = next
       }
       return if (times.isEmpty()) {
           CronPreviewResult(false, "该表达式在可预见的未来没有触发时间", emptyList())
       } else {
           CronPreviewResult(true, null, times)
       }
   }
   ```
8. 新增 `data class CronPreviewResult(val valid: Boolean, val message: String?, val nextFireTimes: List<LocalDateTime>)`

### A-5 `BatchSendConfigController.kt`：新增预览端点（I-3）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt`

在 `DELETE /configs/{id}`（`:76`）之后、`POST /configs/{id}/execute`（`:82`）之前新增：

```kotlin
@PostMapping("/cron/preview")
fun previewCron(@RequestBody request: CronPreviewRequest): ResponseEntity<CronPreviewResult> =
    ResponseEntity.ok(batchSendTaskConfigService.previewCron(request.cron, request.count ?: 5))
```

同文件末尾（或 domain 包）新增 `data class CronPreviewRequest(val cron: String, val count: Int? = null)`。

> **必须返回 200 而非 4xx**（I-3）：预览是编辑器里的即时反馈，非法表达式是常态而非异常，用 4xx 会让前端把它当网络错误处理。

### A-6 测试

**`BatchSendTaskConfigServiceTest.kt`** — +7 用例：
- `autoEnabled = true`、`cron = "0 0 9 * * ?"` → `nextFireTime` 非 null 且晚于当前时刻（I-1）
- `autoEnabled = false` → `nextFireTime == null`（I-2）
- `cron = "这不是cron"` 的实体 → `toView` 不抛异常，`nextFireTime == null`（I-3、X-4）
- `list()` 在 3 条配置下，`taskExecutionService.lastExecutedAtByBatchConfigIds` 被调用**恰好 1 次**，实参为 3 个 id（I-4，用 Mockito verify + captor）
- `list()` 在 0 条配置下不调用该方法或以空集合调用且不抛异常（A-1 的判空）
- `previewCron("0 0 9 * * ?")` → `valid = true`、`nextFireTimes.size == 5`、5 个时间**严格递增**
- `previewCron("bogus")` → `valid = false`、`message` 非空、`nextFireTimes` 为空，**不抛异常**

**`BatchSendConfigControllerTest.kt`** — +3 用例：
- `POST /cron/preview` 合法表达式 → 200，响应 `valid = true` 且 `nextFireTimes` 长度 5
- `POST /cron/preview` 非法表达式 → **200**（不是 400/422），`valid = false`
- `GET /configs` 响应 JSON 含 `nextFireTime` 与 `lastExecutedAt` 两个键

## 变更文件清单

| # | 文件 | 类型 | 改动摘要 |
|---|---|---|---|
| 1 | `src/main/kotlin/.../task/repository/TaskExecutionRepository.kt` | 修改 | +`findLastStartedAtByBatchConfigIds` +`BatchConfigLastExecution` 投影 |
| 2 | `src/main/kotlin/.../task/service/TaskExecutionService.kt` | 修改 | +`lastExecutedAtByBatchConfigIds`（含空集合判空） |
| 3 | `src/main/kotlin/.../campaign/domain/BatchSendTaskConfig.kt` | 修改 | View +`nextFireTime` +`lastExecutedAt` |
| 4 | `src/main/kotlin/.../campaign/service/BatchSendTaskConfigService.kt` | 修改 | +`TaskExecutionService` 依赖、`toView` 加参与两字段、`list`/`get`/`update`/`setEnabled` 接入、+`previewCron` +`CronPreviewResult` |
| 5 | `src/main/kotlin/.../mail/controller/BatchSendConfigController.kt` | 修改 | +`POST /cron/preview` +`CronPreviewRequest` |
| 6 | `src/test/kotlin/.../campaign/service/BatchSendTaskConfigServiceTest.kt` | 修改 | +7 用例 |
| 7 | `src/test/kotlin/.../mail/controller/BatchSendConfigControllerTest.kt` | 修改 | +3 用例 |

**文件数 7 ≤ 10 ✅　独立子系统 2（批量发送配置 / 任务执行记录）≤ 2 ✅　新增字段 0（无新增持久化列）✅**

> **不得**修改：`BatchSendScheduler.kt`、`TaskExecution.kt`、任何迁移文件、`app.js`、`index.html`。
>
> ⚠ 本计划为 `BatchSendTaskConfigService` 新增了构造函数依赖，其**全部手工实例化点须同步**。grep 实测全集（**共 2 处，都在测试里**，生产侧由 Spring 注入）：
> ```
> src/test/kotlin/.../campaign/service/BatchSendTaskConfigServiceTest.kt:37   private fun service() = BatchSendTaskConfigService(
> src/test/kotlin/.../mail/controller/BatchSendConfigControllerTest.kt:33     private val taskConfigService = BatchSendTaskConfigService(
> ```
> 两处**均已在本计划的变更文件清单内**（第 6、7 项），无需扩范围。

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（Surefire 逗号分隔）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=BatchSendTaskConfigServiceTest,BatchSendConfigControllerTest

# 单个测试方法
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigServiceTest#methodName

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

**通过判据**：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。`git diff --check` 无输出。
**来源**：`CLAUDE.md` 的「Commands」章节 + 项目元信息。

> 本计划不改 `.js` / `.html`，无需 `node --test` 门禁。

## 验收标准

- **I-1**：grep `CronExpression.parse` 在 `BatchSendTaskConfigService.kt` 命中 ≥2 处（既有校验 + 新增计算）；grep 全仓无新增的第三方 cron 依赖（`git diff -- pom.xml` 为空）。
- **I-2**：`autoEnabled = false → nextFireTime == null` 用例通过。
- **I-3**：非法 cron 的 `toView` 用例与 `previewCron` 用例通过；grep 确认 `computeNextFireTime` 与 `previewCron` 中均出现 `runCatching`；控制器用例断言非法表达式返回 **200**。
- **I-4**：`list()` 的 Mockito `verify(times(1))` 用例通过；grep 确认 `toView` 方法体内无任何 repository / service 调用。
- **I-5**：新增查询的 SQL 中 grep `trigger_type` 结果为空。
- **框架假设已消解**：A-1 的 spike 已完成，且本节记录了实际采用的方案（DTO 投影 / 降级甲 / 降级乙）。若采用降级方案，「变更文件清单」与 A-1 正文已同步更新为实际实现。
- **回归**：执行「验证命令」节的全量测试命令通过；构建命令通过。

## 人工验收清单

### A-1：cron 预览返回最近 5 次执行时间
- 前置条件：应用已启动。
- 操作步骤：
  1. `curl -X POST http://localhost:8080/api/mail/batch-send/cron/preview -H 'Content-Type: application/json' -d '{"cron":"0 0 9 * * ?"}'`
  2. 换成 `{"cron":"0 */30 * * * ?"}` 重试
- 预期结果：两次均返回 **200**；`valid` 为 `true`；`nextFireTimes` 长度为 **5**，时间严格递增。第 1 次的 5 个时间均为当天或后续日期的 **09:00:00**；第 2 次的 5 个时间间隔均为 **30 分钟**。
- 覆盖：Observable outcome 1；I-1

### A-2：非法 cron 返回明确原因且不报错
- 前置条件：应用已启动。
- 操作步骤：
  1. `curl -X POST .../cron/preview -d '{"cron":"每天九点"}'`
  2. 换成 `{"cron":"0 0 9 * *"}`（只有 5 段）
  3. 换成 `{"cron":""}`
- 预期结果：三次均返回 HTTP **200**（不是 400/422/500）；`valid` 为 `false`；`message` 分别提示「不是合法的 Spring cron 表达式（6 段，秒 分 时 日 月 周）」或「cron 表达式不能为空」；`nextFireTimes` 为空数组。
- 覆盖：Observable outcome 1；I-3

### A-3：列表返回下次执行与最近执行时间
- 前置条件：至少一条配置 `auto_enabled = 1`、`cron = '0 0 9 * * ?'` 且已手动执行过一次；另一条配置 `auto_enabled = 0`；再一条配置从未执行过。
- 操作步骤：调用 `GET /api/mail/batch-send/configs`，逐条查看三个配置对象。
- 预期结果：
  - 第 1 条：`nextFireTime` 为下一个 09:00:00，`lastExecutedAt` 等于 `SELECT MAX(started_at) FROM task_execution WHERE batch_config_id = <id>` 的值
  - 第 2 条：`nextFireTime` 为 **null**，`lastExecutedAt` 按其实际执行历史
  - 第 3 条：`lastExecutedAt` 为 **null**
- 覆盖：Observable outcome 2；I-2、I-5

### A-4：手动执行也计入最近执行时间
- 前置条件：一条配置 `auto_enabled = 0`，且从未执行过。
- 操作步骤：
  1. 调用 `GET /configs/{id}`，记录 `lastExecutedAt`（应为 null）
  2. 在控制台点击该配置的「手动」并执行
  3. 再次调用 `GET /configs/{id}`
- 预期结果：第 3 步 `lastExecutedAt` 非 null，且等于刚才那次执行的 `started_at`；`nextFireTime` 仍为 null（配置未启用）。
- 覆盖：I-5；I-2

### A-5：脏 cron 不打挂列表接口
- 前置条件：用 SQL 直接注入一条非法 cron：`UPDATE batch_send_task_config SET cron = '这不是cron' WHERE id = <某条>;`（绕过服务层校验模拟历史脏数据）。
- 操作步骤：
  1. 调用 `GET /api/mail/batch-send/configs`
  2. 打开前端「批量邮件任务控制台」→ 定时任务 tab
  3. 测试完成后把 cron 改回合法值
- 预期结果：接口返回 **200**，包含全部配置；那条脏数据的 `nextFireTime` 为 `null`，其余配置的 `nextFireTime` 正常；前端列表正常渲染，不出现空白页或报错弹窗。
- 覆盖：I-3；交互点 X-4

### A-6【回归】列表查询次数不随配置数增长
- 前置条件：应用开启 SQL 日志（`logging.level.org.springframework.jdbc.core = DEBUG` 或等效）；库中有 ≥ 5 条有效配置。
- 操作步骤：清空日志 → 调用一次 `GET /api/mail/batch-send/configs` → 统计日志中命中 `task_execution` 的 SELECT 语句条数。
- 预期结果：恰好 **1 条** `task_execution` 查询（含 `GROUP BY batch_config_id`），不是 5 条。
- 覆盖：Observable outcome 3；I-4

### A-7【回归】现有字段与调度行为不变
- 前置条件：一条 `auto_enabled = 1`、`cron = '0 */2 * * * ?'` 的配置。
- 操作步骤：
  1. 对比 `GET /configs` 响应与本计划上线前的响应，逐字段核对现有字段
  2. 等待两次 cron 触发，查看应用日志
  3. 在编辑器中把 cron 改为 `'0 */3 * * * ?'` 并保存，观察日志
- 预期结果：现有字段名称、类型、取值全部不变，只多出 `nextFireTime` / `lastExecutedAt`；第 2 步日志正常出现两次 `Scheduled batch send trigger firing`；第 3 步日志出现 `Scheduled batch send for configId=..., cron=0 */3 * * * ?`（重排正常，主计划 G-5）。
- 覆盖：must-NOT-change 第 1、3 条

## 修正记录

（暂无）

---

## 全局约束（主计划 00 共享，本批所有子计划必须复述并各自验证）

### G-1 地区常量是领域值，不可中文化
`CountryContinentMapping` 的 9 个大区英文串（`China` / `Asia (Japan & Korea)` / `Asia (Other)` / `Europe` / `North America` / `South America` / `Africa` / `Oceania` / `Other`）是领域常量，参与 ES term 查询构造（`countriesForRegion` → `esTermVariants`）。需求 4 的「改为中文」只能作用于显示标签；API 传值、DB 存值、ES 查询值必须保持英文原串。

### G-2 服务端始终存在至少一道单次调度发送量硬闸门
从 01 提交开始到 02 提交完成，`ManualInitialOutreachService` 的轮次循环必须始终受一个服务端配置字段约束（先是 `dailyCap`，01 后新增 `roundsPerRun`，02 后仅剩 `roundsPerRun` + 账号容量）。

### G-3 UNCLASSIFIED 学科的过滤实现必须同源
`ExpertSearchService.disciplineFilter()` 已正确实现 `UNCLASSIFIED` = `must_not exists disciplineCategory`，且 `ALLOWED_DISCIPLINES` 已含该值。已知缺陷点：#1 `ManualInitialOutreachService.buildEsFiltersForLevel()` else 分支（:1219）直接写 `term disciplineCategory = it`（活跃旁路）；#2 `RecipientScope.matchesExpert()`（BatchExecutionModels.kt:54）直接写 `profile.disciplineCategory != discipline`（活跃缺陷）；#3 `BatchSendTaskConfigService.ALLOWED_DISCIPLINES`（:473）= `setOf("STEM","HUMANITIES")`（白名单缺项）；#4 `BatchSendSettingService.ALLOWED_DISCIPLINES`（:236）有意不改；#5 `buildMaterialReminderEsFilters()`（:1088）是死代码；#6 前端 `index.html:1199-1201`、`:1336-1338` 缺 option。

### G-4 运行中只消费启动快照
任何新增配置字段（`roundsPerRun`、`regions`）都必须经 `BatchExecutionSnapshot` 传入执行循环，禁止在循环内重新读 `batch_send_task_config`。

### G-5 调度重排的触发条件是 cron ∪ autoEnabled
`BatchSendScheduler.reload()` 目前仅在 `scheduledCrons[configId] != cron` 时重排；04 引入自定义 cron 后必须确认「沿用原 cron、仅把 autoEnabled 由 false 改 true」的场景仍会重排。

### 全批约束
- 迁移文件禁止包含 `${...}`（生产 application.yml 未关 Flyway placeholder-replacement）。
- 新建迁移前必须先跑 `ls src/main/resources/db/migration/ | sort -V | tail -3` 与 `grep -rn "V9[0-9]__" docs/plans/` 确认版本号未被占用；本批计划编号 V91/V92/V93，若实际落地顺序不同则按实际重编号并同步本计划与主计划引用。已应用的迁移一律不得编辑。
- `BatchSendTaskConfig` 等 data class 的新增字段必须带默认值（全仓 11 个构造点，10 个在测试里）。
- 不在本批范围：账号侧 `dailySendLimit` / warmup ramp 语义与配置入口、`AccountRateLimiter` 动态间隔算法、`oneRoundOnly` 手动单轮语义、`batch_send_setting` KV 兼容表迁移、跨执行自然日发送量统计替代品（`TaskExecutionService.sumSuccessCountTodayByBatchConfigId()` 保留方法与其测试）。

## 执行契约（fast-p 实施者）
- 使用 execute-p 技能；本 brief 是完整批准的契约。
- 只修改「变更文件清单」列出的授权文件；不引入新文件（除计划明示的迁移/测试文件）。
- 保留全部关键不变量与下游接口；data class 新增字段带默认值。
- 运行「验证命令」中全部命令；记录命令与退出码。
- 禁止修改 docs/plans/fast/ 下的任何 fast-p 工件；实现提交不得包含它们。
- 实现提交信息：`feat(fast-p): implement 04a`；只提交授权文件。

