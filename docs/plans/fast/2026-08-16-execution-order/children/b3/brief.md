# Fast-P Child Brief — b3

- Child: b3
- Plan: docs/plans/2026-08-16/b3-mail-record-execution-link-backend.md
- Plan identity: commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3
- Depends on: b2
- Base: 7ca26a1129399fa5f0431fb7830dcecbaf4f9f3f  (b2 terminal Code head)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast
- Knowledge files (K-*) referenced by plans live in the MAIN worktree (uncommitted): /Users/lukai/IdeaProjects/weibo-talent-introduction/docs/knowledge/
- Family main plan (MUST read first for shared invariants M-1..M-7, audits X-1..X-7 incl. X-4/X-5, authoritative verification commands): docs/plans/2026-08-16/task-records-refactor-main.md

## Global constraints (binding, from master plan docs/plans/2026-08-16/00-execution-order.md)

1. JDK 11 mandatory. Use JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home for every mvn command; bare mvn fails to build.
2. Cache key triad: NOT APPLICABLE — this child is pure backend, does not touch index.html/app.js/styles.css/batchSendTaskConsoleVisualFix.test.js.
3. Migration chain: current max V100__add_task_execution_indexes.sql; this child creates V101__add_task_execution_id_to_mail_record.sql (ADD COLUMN task_execution_id BIGINT NULL + CREATE INDEX idx_mail_record_task_execution (task_execution_id, id); NO FOREIGN KEY; no ${). Do not use any other V-number.
4. Next child b4 depends on this child's terminal code head (needs the column + data written).
5. Git: commit locally only, exactly one implementation commit `feat(fast-p): implement b3`. Never push, merge, rebase, amend, rewrite. Exclude docs/plans/fast/ from the commit.

## Authorized files (from the plan 变更文件清单 — 10 files, modify nothing else)

1. src/main/resources/db/migration/V101__add_task_execution_id_to_mail_record.sql   (NEW)
2. src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecord.kt   (one nullable property WITH default = null)
3. src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt   (both methods + 1 trailing optional param each; body otherwise untouched)
4. src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt   (5 call sites pass taskExecutionId = executionId; NO new fields)
5. src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt   (sendInitialBatch signature + 2 call sites)
6. src/main/kotlin/com/weibo/talentintroduction/task/service/MailAutomationScheduler.kt   (only scheduleInitialOutreach onStarted)
7. src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelperTest.kt   (4 args + 2 new cases)
8. src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt   (5 args + 1 new case)
9. src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt   (3 args)
10. src/test/kotlin/com/weibo/talentintroduction/campaign/service/MailRecordTaskExecutionLinkTest.kt   (NEW: default-null + V101 text assertions)

## Required commands (run all; from plan 验证命令 + family main plan)

- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualOutreachTxHelperTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=InitialOutreachServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailRecordTaskExecutionLinkTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true   (requires local Docker; if Docker is unavailable, record as unexecuted with rationale — acceptance says 'on machines with Docker')
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test   (full regression; Tests run: N, Failures: 0, Errors: 0, exit 0; includes node --test exec; verify.sh NOT a gate)
- git diff --check   (clean)

## Verify-first items (⚠️ — MUST resolve BEFORE editing)

1. T2a-4: confirm the `executionId` variable is visible in scope at each of the 5 ManualInitialOutreachService txHelper call sites (:741/:754/:768/:792/:808 per plan — re-grep). If ANY site lacks it, STOP and report PLAN_CONFLICT — do NOT add fields or reverse-lookup from progressStore (I2a-5). The two round loops must be verified symmetrically (N2a-6 / K-batch-send-round-loop-symmetry).
2. Re-verify by grep the X-4 facts: 14 MailRecord(...) construction sites, 7 txHelper production calls, 11 test dependencies (K-plan-quantified-claims-need-grep-receipts). The plan states these; re-grep before relying on them.
3. Queue branch (T2a-5): publisher != null path must NOT pass taskExecutionId; code comment must record the behavior (P2b shows '该执行经队列派发，邮件未直接关联').
4. runAndRecord / onStarted semantics unchanged (N2a-4): the callback already exists (onStarted?.invoke(running.id!!)).

## Downstream interfaces

- b4 (next child) queries WHERE task_execution_id = ? ORDER BY id — the V101 index second column is id; the column must be written for MANUAL_INITIAL_OUTREACH / INITIAL_OUTREACH sync-path mails only (queue path stays null, displayed as 队列派发).
- N-6/N-7 from family main plan: mail_record existing columns and read paths unchanged; the 12 other construction sites stay untouched (null).
- M-5: task_execution_id written ONLY at ManualOutreachTxHelper.recordSuccess/recordFailure.

## Plan text (exact approved content; authoritative)

# B3：`mail_record.task_execution_id` 写入链路（后端）

主计划：`task-records-refactor-main.md`　全链顺序：`00-execution-order.md`
编号：**B3**（全链第 6 份）
前置计划：**B2 必须已合并**（`TaskTypeCatalog.Drilldown` 已存在；`V100` 已占用）
子系统数：2（campaign / mail）　文件数：10
迁移版本：**V101**　缓存键：**不适用**

---

## 需求描述

### Observable outcome

`mail_record` 新增可空列 `task_execution_id`。批量首发（`ManualInitialOutreachService`）与定时首发（`InitialOutreachService`）两条路径发出的每一封邮件——无论成功还是失败——都记录其所属的 `task_execution.id`。本计划**不产生任何 UI 变化**，是 P2b 的数据地基。

### What must NOT change

- **N2a-1** `mail_record` 既有 17 个列的语义与写入不变；`task_execution_id` 为 null 时**所有既有读取路径逐字相同**。
- **N2a-2** 除 `ManualOutreachTxHelper.kt:59` / `:108` 外，其余 12 处 `MailRecord(...)` 构造点一行不改（见主计划 X-4 的 grep 回执）。特别地：`AutoMailReplyService`（4 处）、`ManualReplySendAttemptService`（2 处）、`ManualExpertMailService`（1 处）、`MeetingScheduleService`（1 处）全部不动。
- **N2a-3** `ManualOutreachTxHelper.recordSuccess` 内的 `conversationStateService.transition(...)` 与 `expertOperatorStatusService.updateAutomatically(...)` 两步的**顺序与参数**不变（I-3 不变量：operator_status 收敛到唯一自动写入口，须在 transition 之后调用并用其返回值）。
- **N2a-4** `TaskExecutionService.runAndRecord` / `runAndRecordWithResult` 的签名不变；`onStarted` 回调的既有语义不变。
- **N2a-5** `IntroductionMailComposer.compose()` 与 `SenderAccountAssignmentService` 一行不改（两条路径共用，改动会双向波及）。（来源: K-dual-outreach-paths）
- **N2a-6** `ManualInitialOutreachService` 的两个轮次循环（`runIntroductionFromSnapshot` / `runMaterialFromSnapshot`）的发送节奏、break 条件、轮尾 sleep 一行不改。（来源: K-batch-send-round-loop-symmetry）

### Out of scope

- 不做读取路径与 UI（P2b）。
- 不给其余 12 处构造点补写 `task_execution_id`（M-5）。
- 不加外键约束（见 I2a-4）。
- 不回填历史数据。历史邮件的 `task_execution_id` 恒为 null，P2b 的跳转对历史执行显示「该执行早于本功能上线，无法关联」。
- 不改 `mail_send_attempt`。

---

## 关键不变量

### Invariant I2a-1: 写入点封闭（M-5 落地）

- Rule：`task_execution_id` 只在 `ManualOutreachTxHelper.recordSuccess` / `recordFailure` 内被赋非 null 值。其余 12 处 `MailRecord(...)` 构造点**不传该参数**，走 Kotlin 默认值 null。
- Applies to：主计划 X-4 列出的 14 处构造点全集。
- Violation consequence：`AutoMailReplyService` 的 INBOUND 记录若被误赋值，会把收信错算进发信批次，P2b 的「本次发出的邮件」条数与任务的「已发送」数对不上。
- 来源：M-5

### Invariant I2a-2: 成功与失败都要写

- Rule：`recordFailure` 与 `recordSuccess` 都必须写入 `taskExecutionId`。
- Applies to：`ManualOutreachTxHelper` 两个方法。
- Violation consequence：只写成功侧时，P2b 的邮件列表会漏掉发送失败的记录，运营无法从任务记录页排查「这批为什么失败了 3 封」——而这恰是跳转功能最主要的使用场景。
- 来源：original

### Invariant I2a-3: `InitialOutreachService` 的 executionId 必须来自 `onStarted`，不得新开事务或二次查询

- Rule：`MailAutomationScheduler.scheduleInitialOutreach` 通过 `runAndRecord` 已有的 `onStarted: (executionId) -> Unit` 回调把 id 传给 `InitialOutreachService.sendInitialBatch(...)`（新增可空参数）。**禁止**在 service 内用 `findRecentByTaskType("INITIAL_OUTREACH", 1)` 之类的「查最近一条」反推 executionId。
- Applies to：`MailAutomationScheduler.kt`、`InitialOutreachService.kt`。
- Violation consequence：「查最近一条」在并发或重叠调度下会关联到别的执行；且 `runAndRecord` 的起始 save 与 block 执行在同一事务边界内，反查存在可见性风险。
- 来源：original

### Invariant I2a-4: 不加外键约束（IP-5 落地）

- Rule：`V101` 只加列与索引，**不加** `FOREIGN KEY`。
- Applies to：`V101__add_task_execution_id_to_mail_record.sql`。
- Violation consequence：P3 的 90 天保留清理会硬删 `task_execution` 行；有 FK 时删除被阻塞（或需 `ON DELETE SET NULL` 额外维护成本）。注意这与 `V73` 给 `batch_config_id` 加了 FK 的做法**刻意不同**——`batch_send_task_config` 是软删除永不物理删除，`task_execution` 是硬删除。
- 来源：主计划 IP-5

### Invariant I2a-5: `ManualInitialOutreachService` 复用已有的 executionId，不新增传参链路

- Rule：该 service 作用域内**已持有** `executionId`（用于 `progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)`，见 `:207` / `:549`）。txHelper 调用处直接传入该变量，**不得**新增字段、不得挂到 `TaskProgressStore` 上取。
- Applies to：`ManualInitialOutreachService.kt` 的 5 处 txHelper 调用（`:741` recordSuccess、`:754` / `:768` / `:792` / `:808` recordFailure）。
- Violation consequence：新增传参链路会穿过两个结构同构但独立的轮次循环，极易只改一个（K-batch-send-round-loop-symmetry）。
- 来源：original（本轮 grep 取证）

---

## 样式契约

不适用 —— 本计划无前端文件改动（`app.js` / `index.html` / `styles.css` 均不在变更清单内）。

> ✅ 因此本计划**不需要** bump 缓存键三连，也不需要改 `batchSendTaskConsoleVisualFix.test.js`。
> 若执行中发现必须动前端，**停止并回报** —— 那说明范围判断错了，须先补缓存键契约（参照 B1 的 S0-3）。（来源: K-frontend-cache-key-triad）

---

## 现状审计

### `mail_record` 表结构

`V1__create_business_tables.sql:97-115` 建表；后续 `ALTER TABLE mail_record` 的迁移全集（grep 回执）：

```
$ grep -rn "ALTER TABLE mail_record" src/main/resources/db/migration/*.sql
V6__add_inbound_cleaning_and_intent.sql:1
V15__add_mail_monitoring_columns_and_promotion_audit.sql:2 / :10
V23__create_mail_send_attempt_and_add_mail_record_error.sql:15   error_summary VARCHAR(1024)
V23__create_mail_send_attempt_and_add_mail_record_error.sql:16   mail_send_attempt_id BIGINT
V24__extend_mail_send_attempt_state_and_link_mail_record.sql:37
V31__add_mail_record_created_at_index.sql:1                       （已有 created_at 索引）
```

`MailRecord.kt` 当前 17 个属性（改动前基线）：`id` / `expertContactId` / `direction` / `mailType` / `senderAccountCode` / `triggeredBy` / `sourceInboundId` / `messageId` / `inReplyTo` / `subject` / `body` / `cleanedBody` / `matchedQaRuleId` / `sendStatus` / `receivedAt` / `sentAt` / `errorSummary` / `mailSendAttemptId` / `createdAt`。

⚠️ `triggeredBy` 的值域（grep 回执）：`grep -rhn 'triggeredBy = "' src/main/kotlin | grep -o '"[A-Z_]*"' | sort -u` → 仅 `"MANUAL"` 一个字面量。它是**粗粒度触发来源标记**，不足以定位到具体某次执行，无法替代 `task_execution_id`。

### 构造点与调用方全集

见主计划 X-4（14 处构造点 + 7 处 txHelper 生产调用 + 11 处测试依赖）。**不重复列出，执行前须重新 grep 复核**（来源: K-plan-quantified-claims-need-grep-receipts —— 知识可以播种研究，不能替代研究）。

### `ManualOutreachTxHelper` 现签名

见主计划 X-5（逐字）。

### `Spring Data JDBC` 新增可空列的注意事项

`MailRecord` 是 `data class` + `CrudRepository`。新增 `val taskExecutionId: Long? = null` 后，Spring Data JDBC 会把该属性加入 INSERT 并在值为 null 时绑定 SQL NULL —— 这对可空列是**正确行为**，无 `K-spring-data-jdbc-null-default` 描述的 `NOT NULL DEFAULT` 冲突（该条目针对的是 `created_at` 这类有默认值的非空列）。

⚠️ 但 `K-entity-field-default-for-test-constructors` 的要点适用：新属性**必须带默认值 `= null`**，否则全仓所有 `MailRecord(...)` 的测试构造点都要改。

### 交互点

| # | 写 | 读 | 处理 |
|---|---|---|---|
| IP2a-1 | `ManualOutreachTxHelper` 写列 | P2b 的 `WHERE task_execution_id = ?` | 本计划只保证写入正确 |
| IP2a-2 | `runAndRecord.onStarted` | `InitialOutreachService` 接收 | I2a-3 |
| IP2a-3 | P3 硬删 `task_execution` | `mail_record.task_execution_id` 成悬垂值 | I2a-4：不加 FK，悬垂值由 P2b 显示为「该执行记录已过保留期」 |

---

## 实现方案

### T2a-1 迁移 V101（I2a-4）

新建 `src/main/resources/db/migration/V101__add_task_execution_id_to_mail_record.sql`：

```sql
-- 关联邮件到产生它的任务执行。刻意不加外键：task_execution 有 90 天硬删除保留策略
-- （见 V102 / TaskAuditRetentionScheduler），加 FK 会阻塞清理。悬垂值由读取侧兜底。
ALTER TABLE mail_record
    ADD COLUMN task_execution_id BIGINT NULL;

CREATE INDEX idx_mail_record_task_execution
    ON mail_record (task_execution_id, id);
```

不含 `${...}`。索引带 `id` 做第二列，服务 P2b 的 `WHERE task_execution_id = ? ORDER BY id`。

### T2a-2 实体加属性（I2a-1）

`MailRecord.kt` 末尾加：

```kotlin
    /** 产生该邮件的任务执行 id；只由 ManualOutreachTxHelper 写入（见计划 I2a-1），其余构造点恒为 null。 */
    val taskExecutionId: Long? = null
```

**必须带默认值**（见现状审计末段）。

### T2a-3 `ManualOutreachTxHelper` 加参数（I2a-1 / I2a-2 / N2a-3）

两个方法各在**参数列表末尾**新增 `taskExecutionId: Long? = null`，并在各自的 `MailRecord(...)` 中传入。

- `recordSuccess`：`attemptId: Long` 之后加。
- `recordFailure`：`attemptId: Long?` 之后加。
- 方法体其余部分（transition → updateAutomatically 的顺序、mailRecordRepository.save 的位置）**一行不改**（N2a-3）。

### T2a-4 `ManualInitialOutreachService` 传参（I2a-5 / N2a-6）

在 5 处 txHelper 调用（`:741` / `:754` / `:768` / `:792` / `:808`）末尾加 `taskExecutionId = executionId`。

⚠️ 执行前须确认这 5 处**各自作用域内 `executionId` 变量确实可见**。若某处不可见，**停止并回报**，不要新建字段或从 `progressStore` 反查（I2a-5）。两个轮次循环须对称核对（N2a-6 / K-batch-send-round-loop-symmetry）。

### T2a-5 `InitialOutreachService` 接收 executionId（I2a-3）

1. `InitialOutreachService.sendInitialBatch(campaignId, size)` 签名末尾加 `taskExecutionId: Long? = null`。
2. 两处 txHelper 调用（`:90` recordSuccess、`:99` recordFailure）传入。
3. `MailAutomationScheduler.scheduleInitialOutreach`（`:52`）改为：

```kotlin
var executionId: Long? = null
taskExecutionService.runAndRecord(
    "INITIAL_OUTREACH", "SCHEDULED", request,
    onStarted = { executionId = it }
) {
    val publisher = mailQueuePublisherProvider.getIfAvailable()
    if (publisher != null) {
        publisher.publishInitialOutreach(
            campaignId = properties.initialOutreachCampaignId,
            size = properties.initialOutreachBatchSize
        )
    } else {
        initialOutreachService.sendInitialBatch(
            campaignId = properties.initialOutreachCampaignId,
            size = properties.initialOutreachBatchSize,
            taskExecutionId = executionId
        )
    }
}
```

⚠️ **队列分支（`publisher != null`）不传 executionId**：`MailQueueConsumer` 在另一个 `runAndRecord` 上下文里跑，会有自己的 execution 行；跨进程传递超出本计划范围。队列模式下 `task_execution_id` 保持 null，P2b 显示「该执行经队列派发，邮件未直接关联」。**该行为必须写进代码注释与 P2b 的兜底文案。**

`runAndRecord` 的签名与 `onStarted` 语义均未改（N2a-4）—— 该回调本就存在（`onStarted?.invoke(running.id!!)`）。

### T2a-6 测试适配（主计划 X-4 的 11 处依赖）

**三个既有测试文件必须改**，不得以「Kotlin 默认参数向后兼容」为由略过：Kotlin 默认参数在生产侧传入非 null 后，`Mockito.verify(txHelper).recordSuccess(6 个实参)` 会 argument mismatch。

- `ManualOutreachTxHelperTest.kt`（`:81` / `:159` / `:180` / `:207`）：直接调用处补第 7/8 个实参；新增 2 条用例断言 `MailRecord.taskExecutionId` 被写入（成功侧 + 失败侧，I2a-2）。
- `InitialOutreachServiceTest.kt`（`:93` / `:101` / `:132` / `:165` / `:173`）：`verify` 的实参补齐；新增 1 条用例断言 `sendInitialBatch(taskExecutionId = 42L)` 时 txHelper 收到 42L。
- `ManualInitialOutreachServiceTest.kt`（`:371` / `:1116` / `:1862`）：`verify` 实参补齐。

新增 `src/test/kotlin/.../campaign/service/MailRecordTaskExecutionLinkTest.kt`：
- 断言 `MailRecord` 的 `taskExecutionId` 默认值为 null（I2a-1 的反向保证）。
- `V101` 文本断言（`Files.readString`，沿用 `QaSeedEncodingRepairMigrationTest` 范式）：含 `ADD COLUMN task_execution_id BIGINT NULL`、含 `CREATE INDEX idx_mail_record_task_execution`、**不含** `FOREIGN KEY`（I2a-4）、不含 `${`。

---

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V101__add_task_execution_id_to_mail_record.sql` | 新增 | 列 + 索引，无 FK |
| 2 | `src/main/kotlin/.../mail/domain/MailRecord.kt` | 修改 | 加 1 个可空属性（带默认值） |
| 3 | `src/main/kotlin/.../campaign/service/ManualOutreachTxHelper.kt` | 修改 | 两方法各加 1 个末位可选参数 |
| 4 | `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` | 修改 | 5 处调用补传 |
| 5 | `src/main/kotlin/.../campaign/service/InitialOutreachService.kt` | 修改 | 签名加参数 + 2 处调用补传 |
| 6 | `src/main/kotlin/.../task/service/MailAutomationScheduler.kt` | 修改 | 只改 `scheduleInitialOutreach` 的 `onStarted` |
| 7 | `src/test/kotlin/.../campaign/service/ManualOutreachTxHelperTest.kt` | 修改 | 4 处实参 + 2 条新用例 |
| 8 | `src/test/kotlin/.../campaign/service/InitialOutreachServiceTest.kt` | 修改 | 5 处实参 + 1 条新用例 |
| 9 | `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 | 3 处实参 |
| 10 | `src/test/kotlin/.../campaign/service/MailRecordTaskExecutionLinkTest.kt` | 新增 | 默认值 + 迁移文本断言 |

文件数 10 ≤ 10。子系统 2（campaign / mail；`MailAutomationScheduler` 虽在 task 包，但本改动只是把 campaign 的参数接上，不属独立子系统）。

---

## 验证命令

见主计划「验证命令」节。本计划相关：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualOutreachTxHelperTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=InitialOutreachServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailRecordTaskExecutionLinkTest

# 空库全量迁移（需本地 Docker）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
```

---

## 验收标准

- **I2a-1**：`grep -rn "taskExecutionId" src/main/kotlin --include=*.kt | grep -B2 "MailRecord("` 或人工核对——`MailRecord(...)` 中传 `taskExecutionId` 的构造点**恰好 2 处**，且都在 `ManualOutreachTxHelper.kt`。
- **I2a-2**：`ManualOutreachTxHelperTest` 的 2 条新用例（成功 + 失败）均断言写入值。
- **I2a-3**：`grep -n "findRecentByTaskType" src/main/kotlin/.../InitialOutreachService.kt` **无命中**；`InitialOutreachServiceTest` 新用例断言参数透传。
- **I2a-4**：`V101` 文本断言不含 `FOREIGN KEY`。
- **I2a-5**：`grep -c "taskExecutionId = executionId" src/main/kotlin/.../ManualInitialOutreachService.kt` 为 **5**；且该文件**未新增字段**（`git diff` 中无新的 `private val` / `private var`）。
- 回归：执行主计划「验证命令」节的全量测试命令通过；`FlywayMigrationIntegrationTest` 在有 Docker 的机器上通过。

---

## 人工验收清单

### A2a-1: 批量发送写入关联（Observable outcome）

- 前置条件：配置一个批量发送任务，额度设 3 封，确保有可发送的目标专家。
- 操作步骤：
  1. 发起批量发送，等待完成。
  2. 在「任务记录」页记下这次 `MANUAL_INITIAL_OUTREACH` 的审计 ID（设为 `X`）。
  3. 执行 SQL：`SELECT id, expert_contact_id, send_status, task_execution_id FROM mail_record WHERE task_execution_id = X;`
- 预期结果：返回 3 行（与任务的「已发送 + 失败」总数一致），每行 `task_execution_id` 均为 `X`。
- 覆盖：Observable outcome / I2a-1

### A2a-2: 失败的邮件也被关联（I2a-2）

- 前置条件：构造一次必然部分失败的批量发送（例如把某个发件账号的 SMTP 口令临时改错，或选中一个已知无效邮箱的专家）。
- 操作步骤：发起发送 → 记下审计 ID `Y` → `SELECT send_status, COUNT(*) FROM mail_record WHERE task_execution_id = Y GROUP BY send_status;`
- 预期结果：结果同时包含成功与失败两种 `send_status`，且总数等于任务记录页该行的「已发送 + 失败」之和。**失败行的 `task_execution_id` 不为 null。**
- 覆盖：I2a-2

### A2a-3: 定时首发写入关联（I2a-3）

- 前置条件：`talent-introduction.scheduling.enabled=true`、`initial-outreach-cron` 设为近期可触发的表达式、`initial-outreach-campaign-id > 0`、**未启用** RabbitMQ（`mail-queue.enabled=false`，走同步分支）。
- 操作步骤：等待 cron 触发一次 → 在任务记录页找到该 `INITIAL_OUTREACH` 行记下审计 ID `Z` → `SELECT COUNT(*) FROM mail_record WHERE task_execution_id = Z;`
- 预期结果：计数 > 0 且等于该行的成功+失败数。
- 覆盖：I2a-3

### A2a-4: 队列模式下保持 null（T2a-5 的显式行为）

- 前置条件：启用 RabbitMQ（`talent-introduction.mail-queue.enabled=true`），触发一次定时首发。
- 操作步骤：记下 `INITIAL_OUTREACH` 的审计 ID `W` → `SELECT COUNT(*) FROM mail_record WHERE task_execution_id = W;`
- 预期结果：计数为 **0**（预期行为，非缺陷）。队列消费产生的 execution 行是另一条记录。
- 覆盖：T2a-5 的注释约定

### A2a-5: 回归 —— 其余邮件路径不受影响（N2a-2）

- 前置条件：任意已联系专家。
- 操作步骤：
  1. 在专家详情页手工发一封邮件（`ManualExpertMailService` 路径）。
  2. 触发一次自动收信回复（`AutoMailReplyService` 路径）。
  3. 发一封会议邀请（`MeetingScheduleService` 路径）。
  4. `SELECT id, mail_type, task_execution_id FROM mail_record ORDER BY id DESC LIMIT 10;`
- 预期结果：三条路径产生的记录 `task_execution_id` 均为 **NULL**；邮件本身正常发出，收发件箱正常显示。
- 覆盖：N2a-2 / I2a-1

### A2a-6: 回归 —— 状态流转与运营状态（N2a-3）

- 前置条件：一位 `currentStatus = NEW`、`operatorStatus = NOT_CONTACTED` 的专家。
- 操作步骤：把该专家纳入一次批量发送 → 发送成功后查看专家详情。
- 预期结果：会话状态变为 `INTRO_SENT`，运营状态变为「已联系」，状态历史新增一条 `MANUAL_BULK_OUTREACH` 记录 —— 与改动前完全一致。
- 覆盖：N2a-3

### A2a-7: 回归 —— 批量发送节奏（N2a-6）

- 前置条件：批量任务配置了 `perMailIntervalMs` 与 `perRoundIntervalMs`。
- 操作步骤：发起一次多轮次的批量发送，观察进度弹窗的时间戳间隔。
- 预期结果：每封间隔与轮次间隔与改动前一致，无提前退出、无节奏变化。
- 覆盖：N2a-6

---

## 知识回写（Phase 6）

- **新增** `docs/knowledge/mail/K-mail-record-task-execution-link.md`：`mail_record.task_execution_id` 的写入点封闭在 `ManualOutreachTxHelper` 两处；其余 12 处构造点恒 null；刻意不加 FK 的理由（`task_execution` 90 天硬删除 vs `batch_send_task_config` 软删除）；队列模式下恒 null 的已知边界。
- **更正/补充** `docs/knowledge/campaign/K-expert-contact-two-write-sites.md`：补一句「`ManualOutreachTxHelper.recordSuccess/recordFailure` 是介绍邮件 `mail_record` 的唯一写入 seam，两条外发路径共用；给发信记录加『执行期固化』字段只需改这两处 + 两个调用方传参」。
- **命中续期**：`K-dual-outreach-paths`、`K-batch-send-round-loop-symmetry`、`K-entity-field-default-for-test-constructors`、`K-spring-data-jdbc-null-default`、`K-plan-quantified-claims-need-grep-receipts`。
