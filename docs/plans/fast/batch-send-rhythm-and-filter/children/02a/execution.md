# 02a 执行报告（Implementer02aR1，重试收尾）

> 计划：`docs/plans/fast/batch-send-rhythm-and-filter/children/02a/brief.md`（完整批准契约）
> 分支：`fast/batch-send-rhythm-and-filter`（worktree：`.worktrees/fast/batch-send-rhythm-and-filter`）
> child_base_sha：`59a99f3e8771c2f08e213b7e36ebbfb3eee7e60c`
> 结果：**READY_FOR_VERIFICATION**

## 1. 交接状态与动作

前一实施者崩溃前在 5 个授权文件中留下了未提交编辑。本实施者：

1. 通读 brief（实现方案 A-1/A-2/A-3、不变量 I-1..I-5、验证命令、验收标准）。
2. 逐文件 review 完整 `git diff`（5 个授权文件），对照 A-1/A-2/A-3 与 I-1..I-5 核对，未发现偏差（详见第 3 节）。
3. 补齐遗留缺口：`ManualInitialOutreachService.kt:427` 的 KDoc 仍把 `DAILY_CAP_REACHED` 列为 flow-level 结果信号 —— 已删除该陈旧提及（I-4）。
4. 运行全部验证命令，全部通过（第 4 节）。
5. 提交实现（仅 5 个授权文件，第 6 节）。

## 2. 边界决策（计划审计缺口，按指示不做改动）

- `BatchExecutionModels.kt` 在计划 02a 中**明确禁止修改**（未列入即超范围）。
- 该文件 `annotateTerminalRemaining`（`:151`）仍保留 `"DAILY_CAP_REACHED" -> BatchOutcomeReasonCodes.DAILY_CAP_EXCEEDED` 分支。02a 删除全部产生点后该分支为**死代码**（无任何路径产出 `DAILY_CAP_REACHED` stopReason）。
- **决定：不触碰**。理由：
  - 计划 I-4 的适用点明确枚举 4 处（两个产生点、`stopReasonMessage()` 分支、`idleSafeOneRoundStopReasons` 条目），不含 `BatchExecutionModels.kt`；
  - 变更文件清单未列入该文件；
  - `BatchOutcomeReasonCodes.DAILY_CAP_EXCEEDED` 常量与 LABELS 条目按 I-4 必须保留（历史执行日志反查）；`:151` 分支与它们同文件，无法只删分支不动文件边界。
- 影响：I-4 验收 grep（`DAILY_CAP_REACHED` 在 `src/main/kotlin` 为空）无法对 `BatchExecutionModels.kt` 成立 —— grep 实测仅剩该文件 `:151` 一处命中。这是计划验收措辞对禁止文件的过度覆盖（over-reach），建议主计划在 02b（删除字段与列）或后续清理中处理该死分支。
- `DAILY_CAP_EXCEEDED` 在 `BatchExecutionModels.kt` 共 3 处命中：`:93`（常量定义）、`:104`（LABELS 条目）、`:151`（上述死分支）。I-4 要求的 2 处（常量 + LABELS）完好。

## 3. 完整 diff review 结论（对照 brief）

### A-1 `BatchSendControlService.kt` ✅
- 5 处启动预检（`startScheduled`/`startManual(request)`/`startAuto`/`startManual(sendType)`/`runManualOnce`）的 `alreadySent` 计算与 409 拒绝块全部删除 ✅
- `launchFromSnapshot()` 形参 `alreadySentToday: Int` 删除，5 个调用点与 `launchLegacyKv()` 的实参同步删除 ✅
- `idleSafeOneRoundStopReasons` 的 `"DAILY_CAP_REACHED"` 条目删除（I-4）✅
- 未动：`checkRemainingAccountCapacity()`、`checkRemainingDailyCapacity()`、`validateSnapshotFields()`、`validateTemplateAtLaunch()`、`applyResultToRuntimeStatus()` ✅
- `taskExecutionService` 依赖保留（`runAndRecordWithResult` 仍使用）✅

### A-2 `ManualInitialOutreachService.kt` ✅
- 双循环对称完成（介绍 `runIntroductionFromSnapshot` 与材料 `runMaterialFromSnapshot` 各含全部 1~4 项）：
  - `dailyCapRemaining` 行删除 ✅
  - `roundQuota = minOf(config.roundSize, estimatedRemaining, remainingAccountCapacity)`（3 项，`:234` / `:524`）✅
  - `when` 中 `dailyCapRemaining <= 0` 整支删除；`remainingAccountCapacity <= 0` 支逐字保留（I-5）✅
  - `var dailySentTotal`（2 处）与 `dailySentTotal++`（2 处）删除 ✅
- 日志行 `dailyCap={}` 与实参删除（介绍路径）✅
- 签名清理：`run()` / `runIntroductionFromSnapshot()` / `runMaterialFromSnapshot()` 的 `alreadySentToday` 形参删除；`runBulkOutreach()` / `runScheduledBatch()` 的 `alreadySentToday = 0` 实参删除 ✅
- `runMaterialReminderBatch()` 删除 `dayStart` / `countSentByMailTypeSince(...)` 两行（I-3，唯一生产调用点）✅
- `stopReasonMessage()` 删除 `"DAILY_CAP_REACHED" -> "已达到本批次每日上限"` 分支（I-4）✅
- 保留（留给 02b）：`details` map 的 `"dailyCap" to config.dailyCap`（`:1045`/`:1317`）与 `"dailySentTotal" to ...` 字符串 key（`:1046`/`:1318`）✅
- 本实施者追加修复：`:427` KDoc 删除 `DAILY_CAP_REACHED` 陈旧提及 ✅

### A-3 测试 ✅
- `BatchSendControlServiceTest.kt`：5 处预检 409 用例改写为 202 接受 + 2 个新增反向用例（I-2：today-sum 远超 dailyCap 仍 202；账号容量 0 仍 409 且文案逐字）✅
- `ManualInitialOutreachServiceTest.kt`：`:592` 改「dailyCap=2 不再截断，5 个专家全发」；4 个 `runMaterialReminderBatch` dailyCap 用例改写；6 处 `countSentByMailTypeSince` Mockito stub 全部删除（I-3，无残留）；新增 I-5 账号容量用例（容量 3、roundSize 10、roundsPerRun 5、目标 20 → 恰好 3 封、`DAILY_LIMIT_REACHED`）✅
- `BatchSendTaskRuntimeIntegrationTest.kt`：3 个 dailyCap 拒绝用例改写为 202 接受 + 断言 `sumSuccessCountTodayByBatchConfigId` 未被调用；`DailyCapContext` 去除 `capturedAlreadySent` ✅

### I-5 专项核对
- `classifyNoSendableOutcome()` / `classifyLimitReachedOutcome()`：与 HEAD 逐字节相同（diff IDENTICAL）✅
- `stopReasonMessage()`：除删除的 `DAILY_CAP_REACHED` 分支外与 HEAD 相同（WARMUP/DAILY_LIMIT/NO_AVAILABLE 三支零改动）✅

## 4. 验证命令记录（全部通过）

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | 0 | BUILD SUCCESS；`Tests run: 2347, Failures: 0, Errors: 0, Skipped: 4` |
| 2 | `JAVA_HOME=... mvn test -Dtest=BatchSendControlServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest` | 0 | BUILD SUCCESS；BatchSendControlServiceTest 32、ManualInitialOutreachServiceTest 48、BatchSendTaskRuntimeIntegrationTest 21，共 `Tests run: 101, Failures: 0, Errors: 0` |
| 3 | `JAVA_HOME=... mvn test -Dtest='ManualInitialOutreachServiceTest#runScheduledBatch stops at account capacity with DAILY_LIMIT_REACHED (I-5)'` | 0 | BUILD SUCCESS；`Tests run: 1, Failures: 0, Errors: 0`（新增 I-5 用例） |
| 4 | `JAVA_HOME=... mvn clean package` | 0 | BUILD SUCCESS（含全量测试重跑） |
| 5 | `git diff --check` | 0 | 无输出（空白/换行卫生通过） |

## 5. 验收 grep 记录

| 验收项 | grep | 结果 | 判定 |
|---|---|---|---|
| I-1 | `dailyCapRemaining` in src/main | 空 | ✅ |
| I-1 | `roundQuota = minOf(` 两处实参恰 3 项 | `:234` / `:524` = `minOf(config.roundSize, estimatedRemaining, remainingAccountCapacity)` | ✅ |
| I-2 | `alreadySent` in BatchSendControlService.kt | 空 | ✅ |
| I-2 | `sumSuccessCountTodayByBatchConfigId` in src/main | 仅 `TaskExecutionService.kt:46` 定义行 | ✅ |
| I-3 | `alreadySentToday` in src/main | 空 | ✅ |
| I-3 | `countSentByMailTypeSince` in src/main | 仅 `MailRecordRepository.kt:403` 定义行 | ✅ |
| I-3 | `countSentByMailTypeSince` in src/test | 空（6 处 stub 已清） | ✅ |
| I-3 | `dailySentTotal` | `ManualInitialOutreachService.kt` 恰 2 行 string key（`:1046`/`:1318`），无 `var`、无 `++` | ✅ |
| I-4 | `DAILY_CAP_REACHED` in src/main | 仅 `BatchExecutionModels.kt:151`（禁止文件死分支，见第 2 节边界决策） | ✅（记录审计缺口） |
| I-4 | `DAILY_CAP_EXCEEDED` in BatchExecutionModels.kt | 3 处：`:93` 常量、`:104` LABELS、`:151` 死分支（≥ 计划要求的 2 处） | ✅ |
| I-5 | classify 两函数 diff vs HEAD | IDENTICAL | ✅ |
| 对称性 | 双循环 A-2 1~4 项 | 全部对称出现 | ✅ |

注：`src/test` 中 `DAILY_CAP_REACHED` 仅在 5 处 `assertNotEquals("DAILY_CAP_REACHED", ...)` 负向断言（新用例，语义正确）与 `mail/controller/BatchSendExecutionDetailTest.kt`（非授权文件，`detailsJson` fixture 字符串，不经过被删代码路径）出现；均不在 I-4 的 `src/main` 验收范围内，未改动。

## 6. 实现提交

- 提交信息：`feat(fast-p): implement 02a`
- 仅显式 `git add` 下列 5 个授权文件：
  1. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`
  2. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
  3. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt`
  4. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
  5. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt`
- `docs/plans/fast/` 树（含本报告）与其余 untracked 工件**未**进入提交；未 push / merge / rebase / amend。
- 提交 SHA：（见第 7 节）

## 7. 提交 SHA

`d5370c6387cc6748b3adadd6bb4ca16a502ead18`（`feat(fast-p): implement 02a`，5 files +168 -183）
