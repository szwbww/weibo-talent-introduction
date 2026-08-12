# 02a · 移除日限额闸门（语义变更）

> 主计划：`batch-send-rhythm-and-filter-00-master.md`
> 覆盖需求：第 1 条（前半：闸门下线）
> 依赖：**01 必须已完成**（G-2：拆掉 `dailyCap` 前必须已有 `roundsPerRun` 闸门）
> 后继：02b（删除字段与列）

## 需求描述

### Observable outcome

1. 批量发送任务不再有「本任务今天最多发 N 封」的服务端限制。单次调度的量由「执行轮次 × 每轮数量」决定，跨调度的量只由**发件账号自身的每日限额与预热 ramp** 兜底。
2. 定时调度不再因为「今日发送额度已达配置上限」而被拒绝启动；同一天内可被 cron 触发任意多次，直到账号容量耗尽。
3. 账号容量耗尽时，本次执行以 `DAILY_LIMIT_REACHED` / `WARMUP_LIMIT_REACHED` 结束，`autoEnabled` 保持不变，次日账号计数重置后自动恢复（需求方 2026-08-12 决策：仅结束本次执行）。

### What must NOT change

- `roundsPerRun` 闸门（01 引入）行为不变：轮次用尽 → `ROUNDS_PER_RUN_REACHED` + `COMPLETED`。
- 账号侧闸门语义不变：`roundQuota` 仍取 `remainingAccountCapacity`；`runRoundGate()` 返回空时仍走 `classifyNoSendableOutcome()`；`DAILY_LIMIT_REACHED` / `WARMUP_LIMIT_REACHED` / `NO_AVAILABLE_ACCOUNT` 三个 stopReason 的取值、文案与 runtime status 映射逐字不变。
- `ignoreWarmup = (mode == ExecutionMode.MANUAL)` 不变——手动执行仍绕过预热压制，定时执行仍受预热压制。
- `checkRemainingAccountCapacity()`（`BatchSendControlService.kt:491-497`）**保留**：它查的是账号池剩余容量（`mailSenderAccountService.remainingDailyCapacity(ignoreWarmup = true)`），不是 `dailyCap`，是本计划之后唯一的启动前预检。
- `oneRoundOnly` 手动单轮语义不变。
- 模板校验 `validateTemplateAtLaunch()` 不变。
- **字段与列本计划不删**（留给 02b）：`batch_send_task_config.daily_cap`、`BatchSendTaskConfig.dailyCap`、`BatchExecutionSnapshot.dailyCap` 在本计划结束时仍存在，只是无任何代码读取其值做判定。
- 前端不改。

### Out of scope

- 删除 `dailyCap` 字段与 `daily_cap` 列 → 02b
- 前端删除日限额输入框 → 04
- `TaskExecutionService.sumSuccessCountTodayByBatchConfigId()` 与 `TaskExecutionRepository.sumSuccessCountByBatchConfigIdBetween()` **不删**（主计划已明确保留，留给后续独立清理）；本计划只删除它们在 `BatchSendControlService` 中的 5 个调用点

## 关键不变量

### Invariant I-1: 拆除 dailyCap 后，服务端仍有且仅有三道发送量闸门
- Rule: 本计划完成后，一次执行能发多少封，由且仅由三个因素决定：① `roundsPerRun`（单次执行轮次上界，01 引入）；② `roundSize`（单轮上界）；③ `Σ senderWarmupService.remainingCapacity(account, ignoreWarmup)`（账号侧剩余容量）。`roundQuota = minOf(roundSize, estimatedRemaining, remainingAccountCapacity)`——`dailyCapRemaining` 从该表达式中移除，不得用任何等价物替换。
- Applies to: `ManualInitialOutreachService.runIntroductionFromSnapshot()` 的 `roundQuota` 计算（`:520-543`）、`runMaterialFromSnapshot()` 的 `roundQuota` 计算（`:230-249`）。
- Violation consequence: 若保留任何形式的日额度（例如改从 `mail_record` 统计当日量），需求方「限额只用账号的限额」的目标落空，且会与账号侧闸门产生双重截断，运营无法归因。
- 来源: original（需求方 2026-08-12 决策）

### Invariant I-2: 启动前预检只剩账号容量一项
- Rule: `BatchSendControlService` 的 5 个启动入口中，`alreadySent >= snapshot.dailyCap` 预检全部删除；`checkRemainingAccountCapacity()` 保留且行为不变。删除后，5 个入口不得再调用 `taskExecutionService.sumSuccessCountTodayByBatchConfigId()`。
- Applies to: `startScheduled()`（`:65-69`）、`startManual(request)`（`:90-98`）、`startAuto()`（`:139-142`）、`startManual(sendType)`（`:174-177`）、`runManualOnce()`（`:283-286`）——**共 5 处，grep `alreadySent` 实测全集**。
- Violation consequence: 漏删任一处，该入口仍会返回 409「今日发送额度已达配置上限」，表现为「某些按钮能发、某些不能」，运营无法理解。
- 来源: original

### Invariant I-3: dailySentTotal 与 alreadySentToday 是 dailyCap 的专属载体，必须一并移除
- Rule: `alreadySentToday` 参数（`run()` / `runIntroductionFromSnapshot()` / `runMaterialFromSnapshot()` 三个签名）与由它初始化的局部变量 `dailySentTotal`（`:204` / `:493`）的**唯一**用途是计算 `dailyCapRemaining`；`run()` 的 `alreadySentToday` 移除后，`runMaterialReminderBatch()` 中用于计算它的 `mailRecordRepository.countSentByMailTypeSince()` 调用（`:162-164`）也成为死代码，一并删除。
- Applies to: `ManualInitialOutreachService.run()`（`:132-143`）、`runIntroductionFromSnapshot()`（`:448-454`）、`runMaterialFromSnapshot()`（`:168-174`）、`runMaterialReminderBatch()`（`:154-166`）、`runBulkOutreach()`（`:123-127`）、`runScheduledBatch()`（`:433-446`）、`BatchSendControlService.launchFromSnapshot()` 的 `alreadySentToday` 形参（`:344`）与其 5 个调用点。
- Violation consequence: 留下永不被读的参数与计数器，后续维护者会误以为仍有日额度语义；`countSentByMailTypeSince` 的残留调用还会在每次材料提醒执行时白查一次 DB。
- 来源: original

### Invariant I-4: DAILY_CAP_REACHED 停止原因彻底退役
- Rule: `stopReason = "DAILY_CAP_REACHED"` 的两个产生点（`:238-241` / `:530-535`）删除；`stopReasonMessage()` 的 `"DAILY_CAP_REACHED" -> "已达到本批次每日上限"` 分支（`:871`）删除；`BatchSendControlService.idleSafeOneRoundStopReasons`（`:694-700`）中的 `"DAILY_CAP_REACHED"` 条目删除。`BatchOutcomeReasonCodes.DAILY_CAP_EXCEEDED`（`BatchExecutionModels.kt:92`）**保留不动**——它是 outcome 明细的原因码，与 stopReason 是两套枚举，且当前无产生点。
- Applies to: 上述 4 处。
- Violation consequence: 保留 `stopReasonMessage` 分支而删除产生点 → 死分支；保留 `idleSafeOneRoundStopReasons` 条目 → 无害但误导。删错 `BatchOutcomeReasonCodes.DAILY_CAP_EXCEEDED` → `BatchOutcomeReasonCodes.LABELS` map 与历史执行日志的原因码反查失效。
- 来源: original

### Invariant I-5: 账号容量耗尽的分类逻辑一字不改
- Rule: `classifyNoSendableOutcome()`（`:837-848`）与 `classifyLimitReachedOutcome()`（`:850-861`）的实现、`roundQuota <= 0` 时的 `when` 分支中 `remainingAccountCapacity <= 0 -> ...` 那一支、以及 `stopReasonMessage()` 中 `WARMUP_LIMIT_REACHED` / `DAILY_LIMIT_REACHED` / `NO_AVAILABLE_ACCOUNT` 三个分支，逐字保持不变。仅删除 `dailyCapRemaining <= 0 -> ...` 那一支。
- Applies to: `ManualInitialOutreachService.kt:529-541`（介绍邮件）、`:237-247`（材料提醒）、`:837-880`。
- Violation consequence: 本计划把账号限额提升为唯一兜底，若同时改动其分类逻辑，故障时无法区分「是本次改动引入的」还是「账号本身的问题」。
- 来源: original

## 现状审计

### 闸门一：启动前预检（`BatchSendControlService`）

grep `alreadySent` 实测，**5 处结构相同的预检**：

| # | 方法 | 行号 | 代码 | 拒绝响应 |
|---|---|---|---|---|
| 1 | `startScheduled(configId)` | `:65-69` | `val alreadySent = taskExecutionService.sumSuccessCountTodayByBatchConfigId(configId)` + `if (alreadySent >= snapshot.dailyCap)` | 409「今日发送额度已达配置上限 ($alreadySent/${snapshot.dailyCap})」 |
| 2 | `startManual(request)` | `:90-98` | 同上（`batchConfigId` 为 null 时 `alreadySent = 0`） | 409「今日发送额度已达上限 (…)」 |
| 3 | `startAuto(sendType)` | `:139-142` | 同上（legacy 实体路径） | 409「今日发送额度已达配置上限 (…)」 |
| 4 | `startManual(sendType)` | `:174-177` | 同上 | 409「今日发送额度已达配置上限 (…)」 |
| 5 | `runManualOnce(sendType)` | `:283-286` | 同上 | 409「今日发送额度已达配置上限 (…)」 |

**保留的另一道预检**：`checkRemainingAccountCapacity()`（`:491-497`）→ `mailSenderAccountService.remainingDailyCapacity(ignoreWarmup = true) <= 0` → 409「今日发送额度已用尽（含预热限制），暂不可手动发送」。被 2（`:99-100`）、4（经 `checkRemainingDailyCapacity()`，`:165-166`）、5（`:274-275`）调用。**不动**。

**`alreadySentToday` 的流向**：5 处预检算出的 `alreadySent` → `launchFromSnapshot(alreadySentToday = alreadySent)`（`:344` 形参）→ `manualInitialOutreachService.run(alreadySentToday = alreadySentToday)`（`:396`）。legacy KV 兜底路径 `launchLegacyKv()`（`:577` `alreadySentToday = 0`）。

### 闸门二：轮次配额（`ManualInitialOutreachService`）

两个循环各有一处，结构逐字同构（K-batch-send-round-loop-symmetry）：

**介绍邮件 `:519-543`**
```
val dailyCapRemaining = config.dailyCap - dailySentTotal            // ← 删
val estimatedRemaining = maxOf(0, totalEstimate - processedTotal)
val remainingAccountCapacity = sendable.sumOf { senderWarmupService.remainingCapacity(it, ignoreWarmup) }
val roundQuota = minOf(config.roundSize, dailyCapRemaining, estimatedRemaining, remainingAccountCapacity)  // ← 去掉第 2 项
if (roundQuota <= 0) {
    when {
        dailyCapRemaining <= 0 -> { stopReason = "DAILY_CAP_REACHED"; if (oneRoundOnly) finalStatus = "PAUSED" }   // ← 删整支
        remainingAccountCapacity <= 0 -> { ... }                                                                   // ← 保留
    }
    break
}
```

**材料提醒 `:229-249`** —— 同上，`estimatedRemaining` 用 `totalEstimate - targetIndex`。

**`dailySentTotal` 的 8 处命中（grep 实测，逐行）**：

```
$ grep -n "dailySentTotal" ManualInitialOutreachService.kt
203:        var dailySentTotal = alreadySentToday              ← 变量定义（材料提醒）
230:            val dailyCapRemaining = config.dailyCap - dailySentTotal   ← 变量读
328:                        dailySentTotal++                   ← 变量自增
493:        var dailySentTotal = alreadySentToday              ← 变量定义（介绍邮件）
520:            val dailyCapRemaining = config.dailyCap - dailySentTotal   ← 变量读
698:                        dailySentTotal++                   ← 变量自增
1050:            "dailySentTotal" to sent,                     ← ⚠ 字符串 key，不是变量
1319:            "dailySentTotal" to breakdown.success,         ← ⚠ 字符串 key，不是变量
```

**变量**共 6 处（定义 2 / 读 2 / 自增 2），读点**全部**在 `dailyCapRemaining` 行 —— 故 `dailyCapRemaining` 一删，变量即成死变量，可一并删除。

> ⚠ **`:1050` 与 `:1319` 是进度 `details` map 的字符串 key，与被删的局部变量同名但完全无关**：其值分别取自形参 `sent` 和 `breakdown.success`，代表「本次执行已成功发送数」，是操作端展示字段。**本计划不得删除这两处**——全局搜索替换 `dailySentTotal` 会误伤，必须逐点确认。

### stopReason 消费点（重申 01 的审计）

1. `ManualInitialOutreachService.stopReasonMessage()`（`:863-880`）—— `"DAILY_CAP_REACHED" -> "已达到本批次每日上限"` 在 `:871`
2. `BatchSendControlService.applyResultToRuntimeStatus()`（`:499-532`）—— 按 `finalStatus` 分派，不枚举 stopReason，**不改**
3. `BatchSendControlService.idleSafeOneRoundStopReasons`（`:694-700`）—— 含 `"DAILY_CAP_REACHED"`
4. `details["stopReason"]`（`:1058-1060` / `updateProgressWithAccumulator` 内）—— 透传，**不改**

### 交互点

| # | 写路径 | 读路径 | 处理 |
|---|---|---|---|
| X-1 | `TaskExecutionService.updateProgressCounts()`（每封成功后写 `task_execution.success_count`） | `sumSuccessCountTodayByBatchConfigId()` | 本计划删除后者的全部 5 个调用点，但**保留方法本身与 `updateProgressCounts` 的写入**——`success_count` 仍是执行日志的展示字段（`BatchSendConfigController` 的 `/configs/{id}/executions`），不可停写 |
| X-2 | `mailSenderAccountRepository.incrementTodaySentCount()`（`:326` / `txHelper.recordSuccess`） | `senderWarmupService.remainingCapacity()` → `roundQuota` | 本计划后这是唯一的跨执行量控通道，A-2 / A-3 必须覆盖 |
| X-3 | 前端手动 tab 仍在 payload 里发 `dailyCap`（`app.js:13705`） | `validateSnapshotFields()` 的 `require(snapshot.dailyCap > 0)`（`:450`） | 本计划**保留**该 require（字段还在），前端无需同步改；02b 删字段时才需与 04 协调 |

## 实现方案

### A-1 `BatchSendControlService.kt`：删除 5 处启动预检（I-2、I-3）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`

- 删除 `:65-69`、`:90-98`、`:139-142`、`:174-177`、`:283-286` 五处的 `val alreadySent = ...` 与紧随的 `if (alreadySent >= ... ) return conflict/409` 块
- `launchFromSnapshot()` 删除形参 `alreadySentToday: Int`（`:344`）及其向 `manualInitialOutreachService.run()` 的传参（`:396`）；5 个调用点与 `launchLegacyKv()`（`:577`）同步去掉该实参
- `idleSafeOneRoundStopReasons`（`:694-700`）删除 `"DAILY_CAP_REACHED"` 条目（I-4）
- **不动**：`checkRemainingAccountCapacity()`、`checkRemainingDailyCapacity()`、`validateSnapshotFields()`、`validateTemplateAtLaunch()`、`applyResultToRuntimeStatus()`
- 若 `taskExecutionService` 在删除后不再被本类使用，从构造函数移除该依赖；否则保留（执行前先 grep `taskExecutionService` 在本文件的残留用点——`runAndRecordWithResult` 在 `:383` 仍在用，故**依赖保留**）

### A-2 `ManualInitialOutreachService.kt`：双循环去 dailyCap 配额（I-1、I-3、I-4、I-5）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

对**两个**循环对称执行：

1. 删除 `val dailyCapRemaining = config.dailyCap - dailySentTotal`（`:520` / `:230`）
2. `roundQuota` 改为 `minOf(config.roundSize, estimatedRemaining, remainingAccountCapacity)`（`:523` / `:233`）
3. 删除 `when` 中 `dailyCapRemaining <= 0 -> { ... }` 整支（`:530-535` / `:238-241`）；**保留** `remainingAccountCapacity <= 0 -> { ... }` 支逐字不变（I-5）
4. 删除 `var dailySentTotal = ...`（`:493` / `:204`）及其两处 `dailySentTotal++`（`:698` / `:329`）
5. 日志行 `:464-466` 去掉 `dailyCap={}` 与对应实参

签名与死代码清理（I-3）：

6. `run()`（`:132-143`）删除形参 `alreadySentToday: Int`
7. `runIntroductionFromSnapshot()`（`:448-454`）、`runMaterialFromSnapshot()`（`:168-174`）删除同名形参
8. `runBulkOutreach()`（`:123-127`）、`runScheduledBatch()`（`:433-446`）去掉 `alreadySentToday = 0` 实参
9. `runMaterialReminderBatch()`（`:154-166`）删除 `val dayStart` / `val alreadySentToday = mailRecordRepository.countSentByMailTypeSince(...)` 两行及其实参。

   **`countSentByMailTypeSince` 的全部命中（grep 实测，非推断）**：
   ```
   src/main/kotlin/.../mail/repository/MailRecordRepository.kt:403          ← 方法定义
   src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt:163 ← 唯一生产调用点（本计划删除）
   src/test/kotlin/.../ManualInitialOutreachServiceTest.kt:1314,1454,1753,1811,1870,1906  ← 6 处 Mockito stub
   ```
   - **保留** `MailRecordRepository.kt:403` 的方法定义（仓储层公共 API，删除超出本计划范围）。
   - ⚠ **必须同步清理测试中的 6 处 stub**。删掉唯一生产调用点后这些打桩即成无用；若项目启用了 Mockito strict stubbing，会抛 `UnnecessaryStubbingException` 让这 6 个用例失败——**这是本计划最可能让 CI 变红的一处**，且报错信息与 `dailyCap` 毫无关联，容易被误判为无关回归。执行时逐处删除该 stub 行（保留用例其余部分）
10. `stopReasonMessage()`（`:871`）删除 `"DAILY_CAP_REACHED" -> "已达到本批次每日上限"` 分支（I-4）

**不动**：`details` map 中的 `"dailyCap" to config.dailyCap`（`:1049` / `:1318`）与 `"dailySentTotal"`（其值取自 `breakdown.success`，与被删的局部变量同名但不同源）——留给 02b。

### A-3 测试同步

**`BatchSendControlServiceTest.kt`**
- 删除/改写断言 5 处启动预检返回 409 的用例；新增反向用例：`sumSuccessCountTodayByBatchConfigId` 返回一个远大于 `dailyCap` 的值时，`startScheduled()` 仍返回 202 ACCEPTED（I-2）
- 新增用例：`mailSenderAccountService.remainingDailyCapacity(ignoreWarmup = true)` 返回 0 时 `startManual(request)` 仍返回 409「今日发送额度已用尽（含预热限制），暂不可手动发送」（must-NOT-change 第 4 条）
- `:510` `assertEquals(1000, status.dailyCap)` 保留（字段未删）

**`ManualInitialOutreachServiceTest.kt`**
- 改写 `:592` `runScheduledBatch respects dailyCap and stops full run with COMPLETED` → 断言 `dailyCap = 2` 时**不再**截断，5 个专家全部发出（I-1）
- 改写 `:1682` / `:1734` / `:1792` / `:1849` 四个 `runMaterialReminderBatch ... dailyCap` 用例：删除 `assertEquals("DAILY_CAP_REACHED", result.stopReason)` 断言（`:1787` / `:1845` / `:1923`），改为断言按账号容量或轮次预算停止
- `:663` `3 experts, roundSize=2, dailyCap=10 → 2 rounds` 用例保留（不依赖 dailyCap 截断）
- `:713` `assertEquals(1000, details["dailyCap"])` 保留（details 未改）
- `:1943-2000` 的 `BatchSendSettingService` KV 用例**全部保留不动**（KV 层未改）
- 新增用例：账号 `remainingCapacity = 3`、`roundSize = 10`、`roundsPerRun = 5`、目标 20 → 恰好发 3 封，`stopReason = "DAILY_LIMIT_REACHED"`（I-5）

**`BatchSendTaskRuntimeIntegrationTest.kt`**
- `:301-330` 三个 dailyCap 相关用例（`dailyCapContextWithTodaySum(5L, 7)` / `(5L, 10)` / `dailyCapContext()`）改写为断言**不再拒绝**：`sum >= dailyCap` 时 `startScheduled` 仍 202
- `:432` `dailyCapContextWithTodaySum` / `:564` `dailyCapContext` 两个 helper 保留（仍用于构造上下文），但其 stub 的 `sumSuccessCountTodayByBatchConfigId` 应改为断言**未被调用**（I-2）

## 变更文件清单

| # | 文件 | 类型 | 改动摘要 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 修改 | 删 5 处启动预检、`launchFromSnapshot` 去形参、`idleSafeOneRoundStopReasons` 去条目 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 修改 | 双循环去 `dailyCapRemaining`、去 `dailySentTotal`、6 个签名去 `alreadySentToday`、去 `countSentByMailTypeSince` 调用、`stopReasonMessage` 去分支 |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt` | 修改 | 改写 5 处预检用例 + 新增 2 个反向用例 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 | 改写 5 个 dailyCap 截断用例 + 新增 1 个账号容量用例 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | 修改 | 改写 3 个 dailyCap 拒绝用例 |

**文件数 5 ≤ 10 ✅　独立子系统 1（发送执行链路）≤ 2 ✅　新增字段 0 ✅**

> 未列入即超范围。特别地：`BatchSendTaskConfig.kt`、`BatchExecutionModels.kt`、`BatchSendTaskConfigService.kt`、`BatchSendSettingService.kt`、`app.js`、`index.html`、任何迁移文件在本计划中**不得**修改。

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（Surefire 逗号分隔）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=BatchSendControlServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest

# 单个测试方法
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest#methodName

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

**通过判据**：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。`git diff --check` 无输出。
**来源**：`CLAUDE.md` 的「Commands」章节 + 项目元信息 `test_command` / `build_command`。

> 本计划不改 `.js` / `.html`，无需 `node --test` 门禁。

## 验收标准

- **I-1**：grep `ManualInitialOutreachService.kt` 中 `roundQuota = minOf(` 的两处，实参恰为 3 项且不含任何日额度项；grep 全文 `dailyCapRemaining` 结果为空。
- **I-2**：grep `BatchSendControlService.kt` 中 `alreadySent` 结果为空；grep `sumSuccessCountTodayByBatchConfigId` 在 `src/main/kotlin` 中仅剩 `TaskExecutionService.kt` 的定义行。C-3 的两个新增用例通过。
- **I-3**：grep `alreadySentToday` 在 `src/main/kotlin` 中结果为空；grep `countSentByMailTypeSince` 在 `src/main/kotlin` 中仅剩 `MailRecordRepository.kt:403` 的定义行；grep `countSentByMailTypeSince` 在 `src/test` 中结果为空（6 处 stub 已清）。
  ⚠ **`dailySentTotal` 不能断言「结果为空」**——`ManualInitialOutreachService.kt` 中应**仍剩 2 处**：`updateProgress()` 与 `updateProgressWithAccumulator()` 的 `details` map 字符串 key（原 `:1050` / `:1319`）。正确断言是：grep 结果恰为 2 行且均为 `"dailySentTotal" to ...` 形式，无 `var dailySentTotal` 与 `dailySentTotal++`。
- **I-4**：grep `DAILY_CAP_REACHED` 在 `src/main/kotlin` 中结果为空；grep `DAILY_CAP_EXCEEDED` 仍能在 `BatchExecutionModels.kt` 命中 2 处（常量定义 + LABELS 条目）。
- **I-5**：`git diff` 确认 `classifyNoSendableOutcome()` / `classifyLimitReachedOutcome()` / `stopReasonMessage()` 中 `WARMUP_LIMIT_REACHED`、`DAILY_LIMIT_REACHED`、`NO_AVAILABLE_ACCOUNT` 三支**零改动**；`ManualInitialOutreachServiceTest` 新增的账号容量用例通过。
- **对称性**：`git diff` 确认 `runIntroductionFromSnapshot` 与 `runMaterialFromSnapshot` 各出现了 A-2 第 1~4 项全部改动。
- **回归**：执行「验证命令」节的全量测试命令通过；执行「验证命令」节的构建命令通过。

## 人工验收清单

### A-1：同一天内可被定时触发多次，不再被日额度拒绝
- 前置条件：一条配置 `rounds_per_run = 1`、`round_size = 5`、`daily_cap = 5`（**故意设成很小**）、`auto_enabled = 1`、`cron = '0 */2 * * * ?'`（每 2 分钟）；单个发件账号 `dailySendLimit = 100`、`today_sent_count = 0`、`warmup_enabled = 0`；ES 目标 ≥ 50。
- 操作步骤：
  1. 启动应用，等待 3 次 cron 触发（约 6 分钟）
  2. 打开该配置的「日志」，查看 3 次执行记录
  3. 查 `SELECT SUM(success_count) FROM task_execution WHERE batch_config_id = <id> AND DATE(started_at) = CURDATE();`
- 预期结果：3 次执行**全部成功启动**，每次成功计数为 5，当日累计 15（> `daily_cap` 的 5）；应用日志中**不出现** `Auto batch send start rejected` 或「今日发送额度已达配置上限」。
- 覆盖：Observable outcome 1、2；I-1、I-2

### A-2：账号限额成为唯一兜底
- 前置条件：仅 1 个启用账号，`dailySendLimit = 12`、`today_sent_count = 0`、`warmup_enabled = 0`、`auto_send_paused = 0`；配置 `rounds_per_run = 10`、`round_size = 5`、`daily_cap = 1000`；ES 目标 ≥ 50。
- 操作步骤：
  1. 手动执行该配置，等待结束
  2. 记录成功计数与结束消息
  3. 查 `SELECT today_sent_count FROM mail_sender_account WHERE account_code = '<code>';`
  4. **再次**手动执行同一配置
- 预期结果：第 1 次成功计数为 **12**（2 轮满 5 + 第 3 轮受账号容量截断为 2），结束消息「已达到今日发送上限」；`today_sent_count = 12`；第 2 次执行被拒，提示「今日发送额度已用尽（含预热限制），暂不可手动发送」。
- 覆盖：Observable outcome 3；I-5；交互点 X-2

### A-3【回归】预热压制在定时路径仍生效
- 前置条件：单账号 `dailySendLimit = 100`、`warmup_enabled = 1`、`warmup_started_at = NOW()`（使 ramp 限额远低于 100）、`today_sent_count = 0`；配置 `auto_enabled = 1`、`rounds_per_run = 10`、`round_size = 50`。
- 操作步骤：等待一次 cron 触发，查看执行日志的结束消息与成功计数。
- 预期结果：成功计数不超过当前 ramp 限额（**不是** 100，也不是 500）；结束消息为「已达到预热上限，今日暂停发送」。
- 覆盖：must-NOT-change 第 2、3 条；I-5

### A-4【回归】手动单轮按钮仍恰好一轮
- 前置条件：配置 `rounds_per_run = 5`、`round_size = 20`、`daily_cap = 1000`；账号容量充足；ES 目标 ≥ 100。
- 操作步骤：点击旧「批量发送」面板的「手动执行一次」，等待结束。
- 预期结果：成功计数恰好 20；结束消息「手动单轮发送已完成」；状态 `PAUSED`。
- 覆盖：must-NOT-change 第 5 条

### A-5【回归】轮次预算仍是硬闸门
- 前置条件：配置 `rounds_per_run = 2`、`round_size = 20`、`daily_cap = 1000`；账号 `dailySendLimit = 500`、`today_sent_count = 0`；ES 目标 ≥ 200。
- 操作步骤：手动执行该配置，等待结束。
- 预期结果：成功计数恰好 **40**（不是 200，也不是 500）；结束消息「本次调度轮次已用完」。
- 覆盖：must-NOT-change 第 1 条；主计划 G-2

### A-6【回归】材料提醒路径同步生效
- 前置条件：`材料提醒任务` 配置 `rounds_per_run = 1`、`round_size = 3`、`daily_cap = 2`（**故意小于 round_size**）；APPLICATION 层带 `承诺回复材料` 标签且未发过材料提醒的联系人 ≥ 10；账号容量充足。
- 操作步骤：手动执行材料提醒任务，等待结束。
- 预期结果：成功计数为 **3**（受 `round_size` 而非 `daily_cap = 2` 约束）；结束消息为「本次调度轮次已用完」，**不是**「已达到本批次每日上限」。
- 覆盖：I-1 的材料提醒侧；双循环对称性

### A-7【回归】字段与列本计划尚未删除
- 前置条件：无。
- 操作步骤：
  1. 执行 `SHOW COLUMNS FROM batch_send_task_config LIKE 'daily_cap';`
  2. 调用 `GET /api/mail/batch-send/configs`，查看返回 JSON
- 预期结果：第 1 步返回 1 行（列仍存在）；第 2 步每个配置对象仍含 `dailyCap` 字段。前端配置编辑器的「日限额」输入框仍可正常保存与回显（值被存储但不再生效）。
- 覆盖：must-NOT-change 第 7 条（确认 02a 与 02b 的边界正确）

## 修正记录

（暂无）
