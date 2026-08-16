# B5：任务审计 90 天保留清理

主计划：`task-records-refactor-main.md`　全链顺序：`00-execution-order.md`
编号：**B5**（全链第 8 份，也可在 B1 之后任意位置插入）
前置计划：**B1 必须已合并**（清理依赖 `idx_te_started`；`V100` 已占用）
子系统数：1（task）　文件数：9（见清单末尾的拆分处置）
迁移版本：**V102**　缓存键：**不适用**

---

## 需求描述

### Observable outcome

新增定时任务 `TASK_AUDIT_RETENTION`，每日清理 `task_execution` 与 `task_progress_log` 中超过保留窗口（默认 **90 天**，可配置）的行。清理本身经 `TaskExecutionService.runAndRecord` 记审计，在「任务记录」页可见，成功数为删除总行数。

### What must NOT change

- **N3-1** 保留窗口内的行**一行不删**。
- **N3-2** `TaskProgressStore` 的任何写入行为不变；`bindExecutionId` / `rebindPendingExecutionId` 不改。
- **N3-3** `task_progress_log` 的两个既有索引（`idx_tpl_task_type` / `idx_tpl_execution_id`）不删不改。
- **N3-4** `TaskExecutionRepository` / `TaskProgressLogRepository` 的既有方法全部不改，只新增。
- **N3-5** 调度未启用（`talent-introduction.task-retention.enabled=false`，默认）时，本计划**零行为变化**。
- **N3-6** 不使用 `manualOutreachExecutor`（该执行器 core=max=1、queue=0，被批量外发 / CHECK_REPLIES / BatchSendScheduler 三处共用，长任务提交进去会饿死发信）。（来源: K-manual-outreach-executor-shared）

### Out of scope

- 不做归档到历史表（直接删除；需求方已定 90 天）。
- 不治理 `task_progress_log` 的写放大（K-progress-log-per-mail-write-amplification 的方案①），本计划只做方案②保留窗口。
- 不清理 `mail_record` / `mail_send_attempt` / `inbound_mail_processing`。
- 不做 RUNNING 行的启动收敛。
- 不清理 `operator_action_log`。

---

## 关键不变量

### Invariant I3-1: 按 `created_at` 删，不按关联（M-6 落地）

- Rule：`task_progress_log` 的清理条件是 `created_at < :cutoff`。**禁止**写成 `WHERE task_execution_id NOT IN (SELECT id FROM task_execution)` 或任何 JOIN / EXISTS 形式。
- Applies to：`TaskProgressLogRepository` 新增的删除查询。
- Violation consequence：`TaskProgressStore.tryStartWithToken()` 落的初始化行持久化时 `task_execution_id = -System.nanoTime()`（负值），`bindExecutionId()` 只改内存槽；若 `rebindPendingExecutionId` 回写失败，这些行成为永久孤儿。按关联删除漏掉它们，按 `created_at` 删除才清得掉。
- 来源：M-6 / K-progress-log-pending-token-orphan

### Invariant I3-2: 分批删除，单批有上限

- Rule：每次删除必须带 `LIMIT :batchSize`（默认 2000），循环执行直到单批返回 0 或达到单次运行的总上限（默认 200000 行）。
- Applies to：`TaskAuditRetentionService`。
- Violation consequence：`task_progress_log` 因每发一封邮件写一行且 `details_json` 内嵌完整账号数组（日限额 1000 封 × 十余账号 ≈ 每天数 MB），首次清理可能面对数十万行；单条 `DELETE` 会长时间持锁，阻塞正在运行的批量发送任务的进度写入。
- 来源：K-progress-log-per-mail-write-amplification

### Invariant I3-3: 两张表的 cutoff 独立计算但取同一配置

- Rule：`task_execution` 按 `started_at < cutoff` 删（`started_at` 有 `idx_te_started`，`created_at` 无索引）；`task_progress_log` 按 `created_at < cutoff` 删（V102 新建索引）。两者的 `cutoff` 由同一个 `retentionDays` 配置算出，时区取 `Asia/Shanghai`（与 `TaskExecutionService.SHANGHAI` 一致）。
- Applies to：`TaskAuditRetentionService`。
- Violation consequence：`task_execution` 若按 `created_at` 删会走全表扫（该列无索引），把清理本身变成慢查询。
- 来源：original（X-2 实测索引现状）

### Invariant I3-4: 先删子表再删主表

- Rule：删除顺序必须是 `task_progress_log` → `task_execution`。
- Applies to：`TaskAuditRetentionService.run()`。
- Violation consequence：虽然 `task_progress_log.task_execution_id` **无外键约束**（`V22` 只建了普通索引），顺序颠倒不会报错，但会在两次删除之间留下「主表已删、子表尚在」的窗口，期间 `TaskProgressController.getProgressLogs` 可能返回归属不明的行。
- 来源：original（V22 全文核对：无 FK）

### Invariant I3-5: 清理任务本身可被自己清理，且不得自杀

- Rule：`TASK_AUDIT_RETENTION` 的审计行同样受保留策略约束（90 天后被清理）。但**单次运行内**不得删除自己刚创建的 RUNNING 行——由 `started_at < cutoff` 天然保证（cutoff 是 90 天前，当前行的 `started_at` 是现在）。**禁止**在删除条件里加 `task_type != 'TASK_AUDIT_RETENTION'` 之类的排除。
- Applies to：`TaskAuditRetentionService`。
- Violation consequence：加排除会让清理任务的审计行无限累积。
- 来源：original

### Invariant I3-6: 删除失败不得让整个调度失败

- Rule：两张表的删除各自 try/catch，一张失败记 WARN 并继续另一张；最终把「失败的表数」计入 `failureCount`，使执行状态落到 `PARTIAL_SUCCESS` 或 `FAILED`。**不得**让异常穿透到 `runAndRecord` 之外。
- Applies to：`TaskAuditRetentionService`。
- Violation consequence：`runAndRecord` 捕获异常后会 rethrow（`runAndRecordWithResult`）或吞掉（`runAndRecord`）；穿透到 `@Scheduled` 方法外会由 Spring 的默认 error handler 记录，但状态语义会退化为笼统的 FAILED，看不出哪张表失败。
- 来源：K-circuit-breaker-terminal-status（熔断/失败分支必须有显式终态，三者语义一致）

---

## 样式契约

不适用 —— 本计划无前端文件改动。

> ✅ 因此本计划**不需要** bump 缓存键三连。（来源: K-frontend-cache-key-triad）

---

## 现状审计

### `task_execution` 索引现状

见主计划 X-2。要点：`started_at` 在 P0 的 `V100` 后有 `idx_te_started`；`created_at` **无索引**。故 I3-3 要求按 `started_at` 删。

### `task_progress_log` 表结构（V22 全文）

```sql
CREATE TABLE task_progress_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_type VARCHAR(64) NOT NULL,
    task_execution_id BIGINT,
    batch_number INT NOT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'RUNNING, COMPLETED, FAILED, CANCELLED',
    processed_count BIGINT NOT NULL DEFAULT 0,
    total_count BIGINT NOT NULL DEFAULT 0,
    batch_processed INT NOT NULL DEFAULT 0 COMMENT '本批次处理数量',
    batch_passed INT NOT NULL DEFAULT 0 COMMENT '本批次通过/成功数量',
    batch_rejected INT NOT NULL DEFAULT 0 COMMENT '本批次拒绝/降级数量',
    message TEXT,
    details_json TEXT COMMENT 'JSON 格式的详细统计',
    errors_json TEXT COMMENT 'JSON 格式的错误列表',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tpl_task_type ON task_progress_log(task_type);
CREATE INDEX idx_tpl_execution_id ON task_progress_log(task_execution_id);
```

**核对结论**：① 无 `created_at` 索引 → V102 必须建；② **无外键约束** → I3-4 的顺序要求是语义性的而非约束性的；③ 无 `batch_rejected_reasons_json` 列（实体有 `batchRejectReasonsJson`，须在后续迁移中核对——**本计划不动该问题，仅记录**）。

⚠️ 第 ③ 点须在执行时用 `grep -rn "batch_reject_reasons_json" src/main/resources/db/migration/` 复核；若确无该列而实体有该属性，是既有缺陷，**不在本计划范围内，发现即记入观察不建任务**。

### 写路径

`TaskProgressStore.persistProgressLog()`（唯一写入 `task_progress_log` 的点）、`TaskProgressStore.rebindPendingExecutionId`（列级 UPDATE）。**本计划一处不改**（N3-2）。

### 读路径

`TaskProgressLogRepository` 的 4 个派生查询 + 1 个 `@Modifying`（见 N3-4，全部不改）。清理只新增删除方法。

### 既有调度器范式（T3-3 依据）

`BounceCollectionScheduler.kt:32`：

```kotlin
taskExecutionService.runAndRecord("BOUNCE_COLLECTION", "SCHEDULED", "bounce-collection") { ... }
```

`DailyCountResetScheduler.kt:28`：

```kotlin
taskExecutionService.runAndRecord("DAILY_COUNT_RESET", "SCHEDULED", "daily-count-reset") { ... }
```

两者均为「cron + `runAndRecord` + 简单字符串 request」的最小范式，本计划逐字沿用。**不使用任何 executor**（N3-6）——`@Scheduled` 自带的调度线程即可，清理是每日一次的低频任务。

### 配置现状

`MailSchedulingProperties`（`config/MailSchedulingProperties.kt`）是 `talent-introduction.scheduling` 前缀的 `@ConstructorBinding` data class，当前 12 个字段。`application.yml:64-75` 为对应段落。

**本计划新建独立配置类**而非往 `MailSchedulingProperties` 加字段：保留策略与「邮件调度」是不同关注点，且独立前缀便于运维单独开关。

### 交互点

| # | 写 | 读 | 处理 |
|---|---|---|---|
| IP3-1 | `persistProgressLog` 持续写入 | 清理按 `created_at` 删 | I3-1 / I3-2 分批 |
| IP3-2 | 清理删 `task_execution` 行 | P2b 的 `mail_record.task_execution_id` 成悬垂 | P2b 的 I2b-4 已兜底 |
| IP3-3 | 清理自身写审计行 | 下次清理读它 | I3-5 |
| IP3-4 | 正在运行的批量任务写 progress_log | 清理并发删除 | I3-2 分批 + cutoff 为 90 天前，运行中的行不可能落入 |

---

## 实现方案

### T3-1 迁移 V102（I3-1 / I3-3）

新建 `src/main/resources/db/migration/V102__add_task_progress_log_created_at_index.sql`：

```sql
-- 保留清理按 created_at 删除（不按 task_execution_id 关联，见计划 I3-1：
-- tryStartWithToken 落的初始化行 task_execution_id 为负值 pendingToken，
-- 关联删除会漏掉这些孤儿行）。V22 建表时无此索引。
CREATE INDEX idx_tpl_created_at ON task_progress_log (created_at);
```

不含 `${...}`。

### T3-2 配置类（N3-5）

新建 `src/main/kotlin/.../config/TaskRetentionProperties.kt`：

```kotlin
@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.task-retention")
data class TaskRetentionProperties(
    val enabled: Boolean = false,
    val cron: String = "0 30 4 * * *",
    val retentionDays: Long = 90,
    val batchSize: Int = 2000,
    val maxRowsPerRun: Int = 200000
)
```

`application.yml` 在 `talent-introduction` 下加：

```yaml
  task-retention:
    enabled: ${TASK_RETENTION_ENABLED:false}
    cron: ${TASK_RETENTION_CRON:0 30 4 * * *}
    retention-days: ${TASK_RETENTION_DAYS:90}
    batch-size: ${TASK_RETENTION_BATCH_SIZE:2000}
    max-rows-per-run: ${TASK_RETENTION_MAX_ROWS_PER_RUN:200000}
```

⚠️ 该 yml 片段**不含** `${...}` 形式的 Flyway 占位符风险（这是 Spring 的属性占位符，不经 Flyway）。但须确认 `application.yml:8-13` 的 `spring.flyway.placeholder-replacement: false` **仍然存在**——它是必须维持的约束，任何人清理 yml 时删掉会导致「部署即挂」。（来源: K-flyway-placeholder-replacement）

须确认配置类被 `@ConfigurationPropertiesScan` 或 `@EnableConfigurationProperties` 纳入（执行时照 `MailSchedulingProperties` 的注册方式复制）。

### T3-3 Repository 删除方法（I3-1 / I3-2 / I3-3 / N3-4）

`TaskExecutionRepository` 新增：

```kotlin
@Modifying
@Query("DELETE FROM task_execution WHERE started_at < :cutoff ORDER BY started_at LIMIT :batchSize")
fun deleteOlderThan(cutoff: LocalDateTime, batchSize: Int): Int
```

`TaskProgressLogRepository` 新增：

```kotlin
@Modifying
@Query("DELETE FROM task_progress_log WHERE created_at < :cutoff ORDER BY created_at LIMIT :batchSize")
fun deleteOlderThan(cutoff: LocalDateTime, batchSize: Int): Int
```

⚠️ MySQL 的 `DELETE ... ORDER BY ... LIMIT` 是合法语法（单表删除）。`ORDER BY` 使删除沿索引顺序进行，减少锁范围。执行前须确认本仓库 `@Modifying` + `@Query` 的既有先例（`TaskExecutionRepository.updateProgressCounts`、`TaskProgressLogRepository.rebindPendingExecutionId` 均是 UPDATE；**DELETE 形式在本仓库无先例**，属框架能力假设——须先 spike 一条最小用例确认 Spring Data JDBC 的 `@Modifying` 支持 DELETE 并返回受影响行数；若不支持，降级方案为注入 `NamedParameterJdbcTemplate` 直接执行，本仓库 ES 侧已有 `RestTemplate` 直用先例，风格上可接受）。（来源: K-plan-quantified-claims-need-grep-receipts 陷阱 #3）

### T3-4 清理 Service（I3-2 / I3-4 / I3-5 / I3-6）

新建 `src/main/kotlin/.../task/service/TaskAuditRetentionService.kt`：

```kotlin
fun purge(): RetentionResult {
    val cutoff = LocalDateTime.now(TaskExecutionService.SHANGHAI).minusDays(props.retentionDays)
    var progressDeleted = 0
    var executionDeleted = 0
    var failedTables = 0

    // I3-4：先子表后主表
    try { progressDeleted = purgeLoop { progressLogRepository.deleteOlderThan(cutoff, props.batchSize) } }
    catch (e: Exception) { failedTables++; log.warn("purge task_progress_log failed: {}", e.message) }

    try { executionDeleted = purgeLoop { executionRepository.deleteOlderThan(cutoff, props.batchSize) } }
    catch (e: Exception) { failedTables++; log.warn("purge task_execution failed: {}", e.message) }

    return RetentionResult(progressDeleted, executionDeleted, failedTables)
}
```

`purgeLoop` 循环调用直到单批返回 0 或累计达 `maxRowsPerRun`（I3-2）。

`RetentionResult` 实现 `TaskExecutionSummaryProvider`（I3-6）：

```kotlin
override val taskSuccessCount: Int get() = progressLogDeleted + executionDeleted
override val taskFailureCount: Int get() = failedTables
override val taskFinalStatus: String? get() = when {
    failedTables == 2 -> "FAILED"
    failedTables == 1 -> "PARTIAL_SUCCESS"
    else -> "SUCCESS"
}
```

⚠️ 实现 `TaskExecutionSummaryProvider` 而非依赖 `TaskResultSummary.from()` 的反射——后者的成功侧名单是 `sent`/`replied`/`accepted`/`fetched`/`dispatched`，本结果类一个都不命中，会得到 `0/0`（正是 P1 要消灭的问题）。

### T3-5 调度器（N3-5 / N3-6）

新建 `src/main/kotlin/.../task/service/TaskAuditRetentionScheduler.kt`，照 `BounceCollectionScheduler` 范式：

```kotlin
@Scheduled(cron = "\${talent-introduction.task-retention.cron:-}")
fun scheduleRetention() {
    if (!props.enabled) return
    taskExecutionService.runAndRecordWithResult(
        "TASK_AUDIT_RETENTION", "SCHEDULED", "task-audit-retention"
    ) {
        retentionService.purge()
    }
}
```

用 `runAndRecordWithResult`（而非 `runAndRecord`）以便 `RetentionResult` 落入 `result_summary`（I3-6 的终态语义依赖 `TaskExecutionSummaryProvider` 分支，两个方法都走该分支，但 `WithResult` 让摘要可读）。

### T3-6 catalog 登记（依赖 P1）

`TaskTypeCatalog` 加一条：

```
TASK_AUDIT_RETENTION → label "任务审计清理", group "SCHEDULED",
    metricLabel "删除行数/失败表数", summaryRule "TASK_AUDIT_RETENTION",
    hasProgressUi = false, drilldown = null
```

`TaskExecutionSummaryExtractor` 加对应分支：`totalPassed = root.progressLogDeleted + root.executionDeleted`、`totalRejected = root.failedTables`。

### T3-7 测试

新建 `src/test/kotlin/.../task/service/TaskAuditRetentionServiceTest.kt`：
- cutoff 计算：`retentionDays = 90` 时 cutoff 为 90 天前（Asia/Shanghai）（I3-3）。
- 分批：mock 删除方法依次返回 `2000, 2000, 137, 0`，断言共调 4 次、累计 4137（I3-2）。
- `maxRowsPerRun` 达上限时提前停止（I3-2）。
- 顺序：Mockito `InOrder` 断言 progressLog 先于 execution（I3-4）。
- 一张表抛异常时另一张仍执行，`failedTables = 1`，`taskFinalStatus = "PARTIAL_SUCCESS"`（I3-6）。
- 两张都抛时 `FAILED`。
- **不含**任何 `task_type != 'TASK_AUDIT_RETENTION'` 的排除（I3-5，用 Mockito 参数捕获断言 cutoff 是唯一条件）。

新建 `src/test/kotlin/.../task/service/TaskRetentionMigrationTest.kt`（文本断言，不需 Docker，沿用 `QaSeedEncodingRepairMigrationTest` 范式）：
- `V102` 含 `CREATE INDEX idx_tpl_created_at`，不含 `${`。
- **两个 Repository 的删除 SQL 文本断言**（I3-1 / M-6）：读取 `TaskProgressLogRepository.kt` 源文件，断言其 `deleteOlderThan` 的 `@Query` 字符串含 `created_at <`，且**不含** `JOIN` / `EXISTS` / `task_execution_id`。
- `application.yml` 仍含 `placeholder-replacement: false`（K-flyway-placeholder-replacement 的回归断言，与既有 `UnsubscribeBodyLinkMigrationTest.kt:46` 同类）。

---

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V102__add_task_progress_log_created_at_index.sql` | 新增 | 1 个索引 |
| 2 | `src/main/kotlin/.../config/TaskRetentionProperties.kt` | 新增 | 5 个配置项 |
| 3 | `src/main/resources/application.yml` | 修改 | 新增 `task-retention` 段 |
| 4 | `src/main/kotlin/.../task/repository/TaskExecutionRepository.kt` | 修改 | 加 1 个删除方法 |
| 5 | `src/main/kotlin/.../task/repository/TaskProgressLogRepository.kt` | 修改 | 加 1 个删除方法 |
| 6 | `src/main/kotlin/.../task/service/TaskAuditRetentionService.kt` | 新增 | purge + RetentionResult |
| 7 | `src/main/kotlin/.../task/service/TaskAuditRetentionScheduler.kt` | 新增 | cron + runAndRecordWithResult |
| 8 | `src/main/kotlin/.../task/domain/TaskTypeCatalog.kt` | 修改 | 加 1 条登记 |
| 9 | `src/main/kotlin/.../task/service/TaskExecutionSummaryExtractor.kt` | 修改 | 加 1 个分支 |
| 10 | `src/test/kotlin/.../task/service/TaskAuditRetentionServiceTest.kt` | 新增 | — |
| 11 | `src/test/kotlin/.../task/service/TaskRetentionMigrationTest.kt` | 新增 | — |

文件数 **11**（fast-p 修正 A5：经人工批准采用 11 文件完整方案，超出 10 的上限仅此一次；拆分方案的前提——P1 在合并时预留 `TASK_AUDIT_RETENTION` 条目——未成立，b2 合并后的 catalog 为 16 条、无该条目，拆分会使任务记录页无法正确显示审计清理行，A3-1 可见性验收不满足）。

子系统 1（task）。

---

## 验证命令

见主计划「验证命令」节。本计划相关：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskAuditRetentionServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskRetentionMigrationTest

# 空库全量迁移（需本地 Docker）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
```

---

## 验收标准

- **I3-1**：`TaskRetentionMigrationTest` 的源文件文本断言通过——`deleteOlderThan` 的 `@Query` 含 `created_at <`，不含 `JOIN` / `EXISTS` / `task_execution_id`。
- **I3-2**：分批用例（`2000, 2000, 137, 0` → 调 4 次、累计 4137）与 `maxRowsPerRun` 上限用例通过。
- **I3-3**：cutoff 用例断言时区为 `Asia/Shanghai`；`TaskExecutionRepository.deleteOlderThan` 的 `@Query` 含 `started_at <`（**不是** `created_at`）。
- **I3-4**：`InOrder` 用例通过。
- **I3-5**：参数捕获断言删除条件中不含任何 `task_type` 过滤。
- **I3-6**：三条终态用例（`SUCCESS` / `PARTIAL_SUCCESS` / `FAILED`）通过；`RetentionResult` 实现了 `TaskExecutionSummaryProvider`（编译期保证 + 断言 `taskFinalStatus`）。
- **N3-5**：`enabled = false` 时 `scheduleRetention()` 直接 return，`runAndRecordWithResult` 未被调用（Mockito verify never）。
- **N3-6**：`grep -n "manualOutreachExecutor\|Executor" src/main/kotlin/.../TaskAuditRetentionScheduler.kt` 无命中。
- 回归：执行主计划「验证命令」节的全量测试命令通过；`application.yml` 的 `placeholder-replacement: false` 回归断言通过。

---

## 人工验收清单

### A3-1: 过期行被删除（Observable outcome）

- 前置条件：
  - `TASK_RETENTION_ENABLED=true`，`TASK_RETENTION_CRON` 临时设为近期可触发的表达式。
  - 手工插入：一行 `task_execution`（`started_at` = 91 天前、`created_at` = 91 天前）、一行 `task_progress_log`（`created_at` = 91 天前）。
  - 记录两张表的当前总行数。
- 操作步骤：等待 cron 触发 → 在「任务记录」页找到 `任务审计清理` 行 → 查询两张表。
- 预期结果：手工插入的两行**已被删除**；两张表总行数各减少对应数量；任务记录页该行状态为「执行成功」，计数列显示形如 `2/0 删除行数/失败表数`。
- 覆盖：Observable outcome / I3-1

### A3-2: 保留窗口内一行不删（N3-1）

- 前置条件：记录 89 天内某一天的 `task_execution` 行数（`SELECT COUNT(*) FROM task_execution WHERE started_at >= NOW() - INTERVAL 89 DAY;`）。
- 操作步骤：触发一次清理 → 重新查询同一条件。
- 预期结果：计数**完全不变**。
- 覆盖：N3-1

### A3-3: 孤儿行被清掉（I3-1 / M-6）

- 前置条件：手工插入一行 `task_progress_log`，`task_execution_id` 设为一个**负数**（如 `-1234567890`），`created_at` 设为 91 天前。
- 操作步骤：触发一次清理 → `SELECT COUNT(*) FROM task_progress_log WHERE task_execution_id < 0;`
- 预期结果：该行已被删除（计数减 1）。若清理误用了关联条件，该行会残留。
- 覆盖：I3-1 / K-progress-log-pending-token-orphan

### A3-4: 关闭时零行为（N3-5）

- 前置条件：`TASK_RETENTION_ENABLED=false`（默认）。
- 操作步骤：重启应用，等待超过 cron 周期 → 查询两张表行数 → 查看任务记录页。
- 预期结果：行数不变；任务记录页**不出现** `任务审计清理` 记录。
- 覆盖：N3-5

### A3-5: 单表失败时的部分成功（I3-6）

- 前置条件：临时把 `task_progress_log` 重命名（或收回该表的 DELETE 权限）以制造一张表失败。
- 操作步骤：触发一次清理 → 查看任务记录页该行。
- 预期结果：状态为「部分成功」（不是「执行成功」也不是「执行失败」）；`task_execution` 的过期行**仍被正常删除**；日志中有对应 WARN。
- 覆盖：I3-6 / K-circuit-breaker-terminal-status

### A3-6: 清理不阻塞发信（I3-2 / N3-6）

- 前置条件：`task_progress_log` 中有大量（> 5 万行）过期数据；同时配置一个批量发送任务。
- 操作步骤：先启动批量发送任务，运行中手工触发一次保留清理，观察发送进度弹窗。
- 预期结果：批量发送的进度**持续推进**，无长时间卡顿；两个任务在任务记录页各自独立记录。
- 覆盖：I3-2 / N3-6 / K-manual-outreach-executor-shared

### A3-7: 清理任务自身的审计行受同一策略约束（I3-5）

- 前置条件：手工插入一行 `task_execution`，`task_type = 'TASK_AUDIT_RETENTION'`、`started_at` = 91 天前。
- 操作步骤：触发一次清理 → 查询该行。
- 预期结果：该行**已被删除**（清理任务不豁免自己）。
- 覆盖：I3-5

---

## 知识回写（Phase 6）

- **新增** `docs/knowledge/task/K-task-audit-retention-by-created-at.md`：两张任务审计表的保留清理必须按时间列删（`task_execution` 用 `started_at`——`created_at` 无索引；`task_progress_log` 用 `created_at`），禁止关联删除（孤儿行）；分批 + 单次上限的理由；先子表后主表。
- **更正** `docs/knowledge/task/K-progress-log-per-mail-write-amplification.md`：其「正确做法」的方案②（保留窗口 + `created_at` 索引）已由本计划落地（`V102` + `TaskAuditRetentionScheduler`），方案①（降低写放大）仍未做，条目改为只保留方案①为待办。
- **新增/补充** `docs/knowledge/task/K-modifying-delete-precedent.md`：本仓库 `@Modifying` + `@Query` 此前只有 UPDATE 先例（`updateProgressCounts` / `rebindPendingExecutionId`），DELETE 是首次；记录 spike 结论（支持与否、返回值语义），供后续计划直接引用而不必重新试探。
- **命中续期**：`K-progress-log-pending-token-orphan`、`K-progress-log-per-mail-write-amplification`、`K-manual-outreach-executor-shared`、`K-circuit-breaker-terminal-status`、`K-flyway-placeholder-replacement`、`K-plan-quantified-claims-need-grep-receipts`。
