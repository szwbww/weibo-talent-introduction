# 01 · 定时任务新增「执行轮次数」(roundsPerRun) — 执行报告

## Execution Result: READY_FOR_VERIFICATION

- **Plan**: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-rhythm-and-filter/docs/plans/fast/batch-send-rhythm-and-filter/children/01/brief.md
- **Plan SHA-256**: a4db6717ea577054eb77e7a32ce1c5e38f141d65c46462977471d726c148d8ec
- **Execution ID**: <path>@a4db6717ea577054eb77e7a32ce1c5e38f141d65c46462977471d726c148d8ec
- **Execution epoch**: NEW（执行前报告文件为空）
- **Executor**: Implementer01
- **Target worktree**: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-rhythm-and-filter
- **Target branch**: fast/batch-send-rhythm-and-filter
- **Worktree ID**: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-rhythm-and-filter@fast/batch-send-rhythm-and-filter@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/batch-send-rhythm-and-filter
- **Pre-execution HEAD**: a6c27bbbca02a3b018d8a16aeb11822abd905e19（= child_base_sha）
- **Post-execution code SHA**: 见本报告末尾「提交」一节（实现提交后补充）

## 实现摘要

按 brief 阶段 A/B/C 全部落地：

- **A-1** 新增 `src/main/resources/db/migration/V91__add_rounds_per_run_to_batch_send_task_config.sql`：`ADD COLUMN rounds_per_run INT NOT NULL DEFAULT 1 AFTER round_size` + 全表回填 `UPDATE ... SET rounds_per_run = GREATEST(1, CEIL(daily_cap / round_size))`；无 `${...}`；未触碰 V72。
- **A-2** `BatchSendTaskConfig.kt`：4 个 data class（实体 / View / CreateCommand / UpdateCommand）在 `roundSize` 后各加 `val roundsPerRun: Int = 1`（均带默认值）。
- **A-3** `BatchSendTaskConfigService.kt`：`ConfigFields`/`NormalizedConfig` 加字段；`normalizeAndValidate()` 在 `roundSize > 0` 后加 `require(fields.roundsPerRun >= 1) { "roundsPerRun must be >= 1" }`；`create()`/`update()` 实体构造透传；`toView()` 透传；**`updateLegacyConfig()` 加 `roundsPerRun = existing.roundsPerRun`（X-4 保留实体现值）**；3 处 `*Fields()` 提取函数同步加字段。未改 `toLegacyConfig`（:181 返回映射）与 `BatchSendConfig`。
- **A-4** `BatchExecutionModels.kt`：`BatchExecutionSnapshot` 加 `val roundsPerRun: Int = 1`（默认 1，非 0）；`toExecutionSnapshot()` 透传 `roundsPerRun = roundsPerRun`。
- **B-1** `BatchSendControlService.kt`：`validateSnapshotFields()` 加 `require(snapshot.roundsPerRun >= 1)`；`toLegacySnapshot()` 加 `roundsPerRun = maxOf(1, ceil(dailyCap.toDouble() / roundSize).toInt())`；`BatchSendStatusView` 加 `val roundsPerRun: Int = 0`；`getStatus()` 加 `roundsPerRun = details?.asInt("roundsPerRun") ?: 0`。`applyResultToRuntimeStatus()` / `idleSafeOneRoundStopReasons` 未改（COMPLETED 走既有 else → IDLE）。
- **B-2** `ManualInitialOutreachService.kt`（双循环对称 4 项改动）：
  1. 轮首预算检查（取消检查之后、`roundNumber++` 之前）：`if (roundNumber >= snapshot.roundsPerRun) { stopReason = "ROUNDS_PER_RUN_REACHED"; finalStatus = "COMPLETED"; break }` —— 读 `snapshot.roundsPerRun`（I-3）。
  2. 轮间 sleep 守卫：`&& roundNumber < snapshot.roundsPerRun`（两处）。
  3. 进度详情：`updateProgress()` 与 `updateProgressWithAccumulator()` 各加 `roundsPerRun: Int = 0` 形参 + `details["roundsPerRun"]`；**全部 14 个调用点（1×updateProgress + 13×updateProgressWithAccumulator）显式传 `snapshot.roundsPerRun`**。
  4. `stopReasonMessage()` 在 `ONE_ROUND_DONE` 前加 `"ROUNDS_PER_RUN_REACHED" -> "本次调度轮次已用完"`。
  另：`toSnapshot()` 加 `roundsPerRun = maxOf(1, ceil(dailyCap.toDouble() / roundSize).toInt())`（I-6 同构）；`toBatchSendConfig()` 未改（I-3）。
- **C-1/C-2/C-3** 测试：+4 / +5 / +2，共 11 个新用例，全部通过。

## 变更文件清单（与 brief 完全一致，共 9 个）

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V91__add_rounds_per_run_to_batch_send_task_config.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | 修改 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 修改 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | 修改 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 修改 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 修改 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` | 修改 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt` | 修改 |

## 验证命令（逐条记录）

> 全部命令在 worktree 根目录执行，`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。

### 1. 全量测试（回归门禁）
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```
- **退出码**: 0
- **结果**: `Tests run: 2344, Failures: 0, Errors: 0, Skipped: 4`；`BUILD SUCCESS`。
- 4 个 Skipped 为既有集成测试（`FlywayMigrationIntegrationTest` / `AuthFlowIntegrationTest` / `OperatorActionLogRepositoryTest` / `EuropePmcDataSourceTest`），均因 Docker/外部依赖未启用而 skip，属允许范围。
- 新用例在本次运行中全部执行并通过（含嵌套类 `ReminderBatchTests` 内新增的材料提醒轮次用例，全量运行时嵌套测试随父类执行，`ManualInitialOutreachServiceTest` 计 76 个用例）。

### 2. 本计划相关测试类（三合一）
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigServiceTest,ManualInitialOutreachServiceTest,BatchSendControlServiceTest
```
- **退出码**: 0
- **结果**: `Tests run: 102, Failures: 0, Errors: 0, Skipped: 0`（配置 25 / 控制 30 / 外发 47）；`BUILD SUCCESS`。
- 注：`-Dtest` 指定类名时 surefire 只运行外层类用例（本项目既有行为），嵌套类 `ReminderBatchTests` 单独验证见下第 3.5 条。

### 3. 单个测试类
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest
```
- **退出码**: 0
- **结果**: `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS`。

### 3.5. 嵌套类 ReminderBatchTests（材料提醒路径用例，含新增 C-2 第 4 条）
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='ManualInitialOutreachServiceTest$ReminderBatchTests' -Dsurefire.failIfNoSpecifiedTests=false
```
- **退出码**: 0
- **结果**: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS`。

### 4. 单个测试方法
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='ManualInitialOutreachServiceTest#roundsPerRun bounds a single execution at rounds times round size'
```
- **退出码**: 0
- **结果**: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS`。
- 注：surefire 2.22.2 对含空格的 Kotlin backtick 方法名，`+` 转义形式（`#roundsPerRun+bounds+...`）报 "No tests were executed!"（exit 1，工具链限制，非实现问题）；按空格原样写法可正常选中执行。

### 5. Flyway 迁移集成测试（V91 应用验证；需本地 Docker）
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
```
- **退出码**: 1
- **结果**: `Tests run: 1, Failures: 0, Errors: 1, Skipped: 0` — `IllegalStateException: Docker is required...`。
- **原因**: 本机 Docker daemon 未运行（`docker info` exit 1；`/Users/lukai/.orbstack/run/docker.sock` 不存在）。brief 已注明该测试「需本地 Docker，默认跳过」，属环境基础设施阻塞，非实现缺陷。V91 迁移未能在 MySQL 上实际应用验证；SQL 为简单 `ALTER TABLE ... ADD COLUMN ... AFTER round_size` + 全表 `UPDATE ... GREATEST(1, CEIL(daily_cap/round_size))`，语法与回填公式经静态核对。

### 6. 构建
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
```
- **退出码**: 0
- **结果**: `Tests run: 2344, Failures: 0, Errors: 0, Skipped: 4`；`BUILD SUCCESS`；产出 jar/war 正常。

### 7. 空白/换行卫生
```bash
git diff --check
```
- **退出码**: 0
- **结果**: 无输出。

## 验收标准核查（grep 证据）

- **I-1**：`roundsPerRun` 在 `ManualInitialOutreachService.kt` 的全部出现点均为 `snapshot.roundsPerRun`（20 处）；`config.roundsPerRun` 全仓 0 处；该文件内 `roundsPerRun` 与 repository / `sumSuccessCountToday*` 无任何关联出现。
- **I-2**：C-2 第 1 个用例断言 `stopReason == "ROUNDS_PER_RUN_REACHED" && finalStatus == "COMPLETED"` 通过；`applyResultToRuntimeStatus()` 未新增任何 `PAUSED` 分支（未编辑该方法）。
- **I-3**：`ManualInitialOutreachService.kt` 中无 `config.roundsPerRun`；`BatchSendSettingService.kt` `git diff` 为空（0 行）。
- **I-4**：C-2 第 3 个用例（`oneRoundOnly = true, roundsPerRun = 5` → 恰好 1 轮、`ONE_ROUND_DONE`/`PAUSED`）通过。
- **I-5**：`BatchExecutionModels.kt:12` 为 `val roundsPerRun: Int = 1`（非 0）；C-1 前两用例 + C-3 第 1 用例通过。
- **I-6**：三处公式逐字同构 —— V91 `GREATEST(1, CEIL(daily_cap / round_size))`；`BatchSendControlService.kt:601` 与 `ManualInitialOutreachService.kt:1254` 均为 `maxOf(1, ceil(dailyCap.toDouble() / roundSize).toInt())`；C-3 第 2 用例（legacy KV 兜底推导 `ceil(100/10)=10`）通过。
- **X-2**：C-1 第 4 用例（仅改 `roundsPerRun` 时 reload 事件 oldCron == newCron，scheduler 按 cron 去重不重排）通过。
- **X-4**：C-1 第 3 用例（`updateLegacyConfig` 请求体不含 `roundsPerRun` 时保留实体现值 7）通过。
- **双循环对称性**：`runIntroductionFromSnapshot` 与 `runMaterialFromSnapshot` 均含轮首预算检查 + sleep 守卫 + 进度传值；C-2 第 4 用例覆盖材料提醒路径，通过。

## 偏差与说明

1. **既有测试断言更新（2 处，均在授权文件 `ManualInitialOutreachServiceTest.kt` 内）**：
   - `runMaterialReminderBatch seeds dailyCap from persisted SENT count (R1)`：`stopReason` 由 `DAILY_CAP_REACHED` 改为 `ROUNDS_PER_RUN_REACHED`。
   - `runMaterialReminderBatch does not count FAILED toward dailyCap seed (R1)`：同上。
   - 原因：这两条用例经 `toSnapshot()` 走 I-6 推导路径（dailyCap=3、roundSize=10 → `roundsPerRun = ceil(3/10) = 1`；dailyCap=2、roundSize=10 → 1）。按 brief B-2 规定的轮首检查位置（取消检查之后、`roundNumber++` 之前），轮次预算在「预算耗尽与 dailyCap 恰好同时耗尽」的迭代上先于轮内配额检查命中，终止原因变为 `ROUNDS_PER_RUN_REACHED`。发送量、`finalStatus = COMPLETED` 均不变；两种原因都走 `COMPLETED → IDLE`，runtime 行为一致。此即为 I-6 回填公式的语义后果，brief A-4（roundsPerRun=10 > 推导值 2）刻意使用非推导配置验证 `DAILY_CAP_REACHED` 消息，不受影响。
2. **`-Dtest` 指定类名时 surefire 不运行嵌套类用例**：本项目既有行为（基线上同样如此），非本计划引入；材料提醒新用例已通过显式嵌套类运行（第 3.5 条）与全量测试（第 1 条，76 用例）双重验证。
3. **Flyway 迁移集成测试**：Docker 不可用（环境阻塞），见命令 5。
4. 未修改任何计划文档；`docs/plans/fast/` 与 `docs/plans/2026-08-12/` 均未纳入实现提交。
5. 前端 `app.js` / `index.html` / `BatchSendSettingService.kt` / `BatchSendScheduler.kt` / `BatchSendTaskRuntimeIntegrationTest.kt` 均未改动。

## 提交

- **提交信息**: `feat(fast-p): implement 01`
- **提交内容**: 仅上述 9 个授权文件（8 个修改 + 1 个新增迁移），按路径显式 `git add`；不含 `docs/plans/` 任何内容。
- **提交 SHA**: `59a99f3e8771c2f08e213b7e36ebbfb3eee7e60c`（9 files changed, 426 insertions(+), 21 deletions(-)）
- **Post-execution code SHA**: `59a99f3e8771c2f08e213b7e36ebbfb3eee7e60c`（= 提交 SHA，HEAD 即实现提交，且为 `refs/heads/fast/batch-send-rhythm-and-filter` 所指）
- **提交可达性**: `git merge-base --is-ancestor HEAD refs/heads/fast/batch-send-rhythm-and-filter` → HEAD 是分支祖先 ✓
- **工作树残留**: 仅 `?? docs/plans/fast/batch-send-rhythm-and-filter/`（计划工件，按契约不提交）
