# B3 Execution Report — `mail_record.task_execution_id` 写入链路（后端）

- **Result**: READY_FOR_VERIFICATION
- **Plan**: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast/docs/plans/2026-08-16/b3-mail-record-execution-link-backend.md
- **Plan SHA-256**: 667254404a74e3995625251f9f66894125527960e5870dbef402e7d20a58c05c（与批准 commit `65b8de8` 中的文件逐字节一致，重跑 `plan_identity.py` 复核相同）
- **Execution ID**: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast/docs/plans/2026-08-16/b3-mail-record-execution-link-backend.md@667254404a74e3995625251f9f66894125527960e5870dbef402e7d20a58c05c`
- **Execution epoch**: NEW
- **Executor**: ImplementB3（task 子代理，execute-p skill）
- **Target worktree**: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast
- **Target branch**: fast/2026-08-16-execution-order
- **Worktree ID**: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast@fast/2026-08-16-execution-order@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast`
- **Pre-execution code SHA**: 7ca26a1129399fa5f0431fb7830dcecbaf4f9f3f（b2 终端代码头；HEAD 4e2c76f = 代码头 + 两份 docs 证据 commit）
- **Post-execution code SHA**: eb27b8d84a4286ce3ef92ca40acf98d761168121
- **Evidence HEAD**: N/A（evidence 由控制器另行 commit）
- **Implementation boundary**: 7ca26a1..eb27b8d（仅 10 个授权文件；docs/plans/fast/ 未纳入实现 commit）

---

## Verify-first 解析（编辑前完成，带证据）

### VF-1: T2a-4 — `executionId` 在 5 处 txHelper 调用点的作用域可见性 ✅

grep 证据（`grep -rn "executionId" src/main/kotlin/.../ManualInitialOutreachService.kt`）：
- 5 处调用（`:741` recordSuccess / `:754` / `:768` / `:792` / `:808` recordFailure）全部位于 `runIntroductionFromSnapshot(snapshot, executionId, mode, oneRoundOnly)`（签名 `:499-503`，参数 `executionId: Long` 于 `:500` 声明）函数体内，`executionId` 为该函数形参，**5 处全部可见**。未新增字段、未从 progressStore 反查（I2a-5 合规）。
- 材料提醒轮次循环 `runMaterialFromSnapshot`（`:166-403`）**不调用 txHelper**（该路径经 `manualExpertMailService.sendManualMail` 走 `ManualExpertMailService` 构造点，本就恒 null）——与主计划 X-4 一致，N2a-6 对称性核对通过（两轮循环的发送节奏/break/sleep 零改动）。
- 修改后 `grep -c "taskExecutionId = executionId"` = **5**（验收标准 I2a-5 通过）。

### VF-2: X-4 事实重 grep 复核

| 事实 | 计划声明 | 实测 | 结论 |
|---|---|---|---|
| `MailRecord(` 构造点 | 14 处（主计划 X-4） | **10 处实际构造点**：AutoMailReplyService 265/579/769/967（4）、ManualReplySendAttemptService 229/307（2）、ManualExpertMailService 70（1）、MeetingScheduleService 145（1）、ManualOutreachTxHelper 59/108（2） | 计划数字含 6 处「函数名子串」命中（`saveMailRecord(` ×4、`toDetailFromMailRecord(` ×2，非构造）。行为无差异——除 txHelper 2 处外其余构造点一行未改（N2a-2） |
| txHelper 生产调用 | 7 处 | 7 处：ManualInitialOutreachService 741/754/768/792/808（5）+ InitialOutreachService 90/99（2） | 一致 |
| 测试侧依赖 | 3 文件 11 处 | **12 处**：InitialOutreachServiceTest 93/101/132/165/173（5）、ManualOutreachTxHelperTest 81/159/180/207（4）、ManualInitialOutreachServiceTest 371/1116/1862（3） | 计划数字 11 低估 1（1862 的 `verify(never()).recordSuccess` 亦需补参）；12 处全部更新 |
| `task_execution_id` 写入点 | 仅 txHelper 2 处 | `grep -rn "taskExecutionId = taskExecutionId"` → 仅 ManualOutreachTxHelper.kt:77 / :129（MailRecord 构造内） | I2a-1 通过 |

### VF-3: T2a-5 队列分支 ✅
`MailAutomationScheduler.scheduleInitialOutreach`：`publisher != null` 分支不传 executionId，代码注释已写明「队列模式下 mail_record.task_execution_id 保持 null（该执行经队列派发，邮件未直接关联）」。

### VF-4: N2a-4 — runAndRecord/onStarted 语义未变 ✅
`TaskExecutionService.runAndRecord` 签名 `onStarted: ((executionId: Long) -> Unit)? = null`（`:91`）与 `onStarted?.invoke(running.id!!)`（`:110`）改动前已存在，**零修改**；仅 `MailAutomationScheduler` 新增 `var executionId: Long? = null` + `onStarted = { executionId = it }`。

### 其他预检
- 迁移链最大值确认为 `V100__add_task_execution_indexes.sql`，本 child 占用 **V101**，无跳号。
- 缓存键三连：不适用（纯后端，无前端文件改动，未动 `batchSendTaskConsoleVisualFix.test.js`）。
- 基线 grep：改动前 `src/main/kotlin` 无 `MailRecord.taskExecutionId`（既有 `taskExecutionId` 命中全部属 `TaskProgressLog` 机制，与本计划无关）。

---

## 变更明细（10 个授权文件）

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V101__add_task_execution_id_to_mail_record.sql`（新增） | 逐字按计划：`ALTER TABLE mail_record ADD COLUMN task_execution_id BIGINT NULL;` + `CREATE INDEX idx_mail_record_task_execution ON mail_record (task_execution_id, id);` 无 FK、无 `${` |
| 2 | `mail/domain/MailRecord.kt` | 末尾加 `val taskExecutionId: Long? = null`（**带默认值**，K-entity-field-default-for-test-constructors）+ 计划原文注释 |
| 3 | `campaign/service/ManualOutreachTxHelper.kt` | `recordSuccess` 末位加 `taskExecutionId: Long? = null`（attemptId 之后）；`recordFailure` 末位加 `taskExecutionId: Long? = null`（attemptId 之后）；两方法 MailRecord 构造各传 `taskExecutionId = taskExecutionId`。**方法体其余部分一行未改**（N2a-3：transition → updateAutomatically 顺序、save 位置不变） |
| 4 | `campaign/service/ManualInitialOutreachService.kt` | 5 处调用末尾补 `taskExecutionId = executionId`（741 success + 754/768/792/808 failure）。无新字段（`git diff` 无新增 `private val/var`），两个轮次循环节奏零改动 |
| 5 | `campaign/service/InitialOutreachService.kt` | `sendInitialBatch(campaignId, size, taskExecutionId: Long? = null)`；90/99 两处调用补传 |
| 6 | `task/service/MailAutomationScheduler.kt` | 仅 `scheduleInitialOutreach`：`var executionId` + `onStarted = { executionId = it }`；同步分支传 `taskExecutionId = executionId`；队列分支不传 + 注释 |
| 7 | `test/.../ManualOutreachTxHelperTest.kt` | 4 处直接调用补第 7/8 实参（1000L/2000L/1000L/3000L）；**新增 2 条用例**断言成功侧与失败侧 `MailRecord.taskExecutionId` 写入 4242L（I2a-2） |
| 8 | `test/.../InitialOutreachServiceTest.kt` | 5 处 verify 补参（成功侧 `isNull()`、失败侧 `isNull()`/`any()`）；**新增 1 条用例** `sendInitialBatch(..., taskExecutionId = 42L)` 断言 txHelper 收到 42L（I2a-3 参数透传） |
| 9 | `test/.../ManualInitialOutreachServiceTest.kt` | 3 处 verify 补参：371/1116 断言 `Mockito.eq(12345L)`（runBulkOutreach/runScheduledBatch 传入的 executionId），1862 `never()` 补 `Mockito.any()` |
| 10 | `test/.../MailRecordTaskExecutionLinkTest.kt`（新增） | 2 条用例：`taskExecutionId` 默认 null（I2a-1 反向保证）；V101 文本断言（含 `ADD COLUMN task_execution_id BIGINT NULL`、含 `CREATE INDEX idx_mail_record_task_execution`、不含 `FOREIGN KEY`、不含 `${`，沿用 QaSeedEncodingRepairMigrationTest 的 `Files.readString` 范式） |

---

## 命令执行记录（全部为最终实现状态后的新鲜运行）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `JAVA_HOME=...zulu-11... mvn test -Dtest=ManualOutreachTxHelperTest` | 0 | Tests run: **6**, Failures: 0, Errors: 0（4 旧 + 2 新）；含 JS 616 pass |
| `JAVA_HOME=...zulu-11... mvn test -Dtest=InitialOutreachServiceTest` | 0 | Tests run: **11**, Failures: 0, Errors: 0（10 旧 + 1 新） |
| `JAVA_HOME=...zulu-11... mvn test -Dtest=ManualInitialOutreachServiceTest` | 0 | Tests run: **86**, Failures: 0, Errors: 0 |
| `JAVA_HOME=...zulu-11... mvn test -Dtest=MailRecordTaskExecutionLinkTest` | 0 | Tests run: **2**, Failures: 0, Errors: 0 |
| `JAVA_HOME=...zulu-11... mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | **未执行** | **Docker 不可用**：`docker info` 失败（daemon 未运行），该测试用 Testcontainers（`DockerClientFactory` + `MySQLContainer`）需要本地 Docker。验收标准注明「在有 Docker 的机器上通过」。全量回归中该测试被 `@EnabledIfSystemProperty` 跳过（计入 Skipped: 4 之一） |
| `JAVA_HOME=...zulu-11... mvn test`（全量回归） | 0 | **Tests run: 2493, Failures: 0, Errors: 0, Skipped: 4**（4 个跳过均为既有门控测试：FlywayMigrationIntegrationTest 1、OperatorActionLogRepositoryTest 1、AuthFlowIntegrationTest 1、EuropePmcDataSourceTest 1，与本次改动无关）；JS `node --test` fail 0（616 pass）；BUILD SUCCESS |
| `git diff --check` | 0 | 无输出（DIFF_CHECK_CLEAN） |

surefire 报告：`target/surefire-reports/TEST-com.weibo.talentintroduction.campaign.service.{ManualOutreachTxHelperTest,InitialOutreachServiceTest,ManualInitialOutreachServiceTest,MailRecordTaskExecutionLinkTest}.xml`

---

## 验收标准核对（本 child）

- **I2a-1** ✅ `grep -rn "taskExecutionId = taskExecutionId"` → 仅 txHelper :77/:129 两处 MailRecord 构造点（2 处，均在 ManualOutreachTxHelper.kt）。
- **I2a-2** ✅ ManualOutreachTxHelperTest 新增成功/失败 2 条用例断言写入 4242L。
- **I2a-3** ✅ `grep -n "findRecentByTaskType" InitialOutreachService.kt` 无命中；新用例断言 42L 透传。
- **I2a-4** ✅ V101 文本断言不含 `FOREIGN KEY`（MailRecordTaskExecutionLinkTest 第 2 条用例）。
- **I2a-5** ✅ `grep -c "taskExecutionId = executionId"` = 5；`git diff` 无新 `private val/var`。
- **回归** ✅ 全量 2493 通过；Flyway 集成测试因 Docker 不可用记录为未执行（见上）。
- **M-5 / N-6 / N-7 / N2a-1..N2a-6 / X-4 / X-5**：均维持（详见 verify-first 节与变更明细；12 处非 txHelper 构造点零改动；runAndRecord/onStarted 签名未动；compose/assignment 一行未改）。

---

## 偏差与备注

1. **计划计数与实测不符（无行为影响）**：
   - 「14 处 MailRecord 构造点」实为 10 处实际构造 + 6 处函数名子串（`saveMailRecord(` ×4、`toDetailFromMailRecord(` ×2）。I2a-1 核心断言（恰 2 处传参、均在 txHelper）不受影响。
   - 「11 处测试依赖」实为 12 处（主计划 X-4 漏计 `ManualInitialOutreachServiceTest:1862` 的 `verify(never())`）。12 处全部更新。
2. **FlywayMigrationIntegrationTest 未执行**：本地无 Docker daemon（`docker info` 失败）。这是唯一未运行的必要命令，按 brief 允许项记录为 unexecuted + rationale。
3. 队列分支注释按 T2a-5 要求写入 `MailAutomationScheduler.kt`（中文，含「该执行经队列派发，邮件未直接关联」）。
4. 无前端文件、无其他迁移文件、未改 `mail_send_attempt`、未回填历史数据。
5. 未 push/merge/rebase/amend/rewrite；实现 commit 单条 `feat(fast-p): implement b3`（eb27b8d），docs/plans/fast/ 未纳入（证据由控制器另行 commit）。

---

## Freshness

- Plan identity rechecked: YES（前后 SHA 均 66725440...）
- Worktree identity rechecked: YES（--expect-root/--expect-branch/--expect-git-dir 前后均通过）
- Reported commit reachable from target branch: YES（`merge-base --is-ancestor eb27b8d fast/2026-08-16-execution-order` 通过；commit 为 worktree HEAD）
- Required commands run this invocation: YES（除 Flyway/Docker 项记录为未执行）
- Historical evidence used only as baseline: YES

## 下一步

- READY_FOR_VERIFICATION → 由控制器转交 `verify-p`（b3 独立验证）。
- 后续 child b4 依赖本 commit 代码头 eb27b8d。
