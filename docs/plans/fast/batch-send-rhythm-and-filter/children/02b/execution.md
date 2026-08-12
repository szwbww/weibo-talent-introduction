# 02b 执行报告 · 删除 dailyCap 字段与 daily_cap 列

- **执行结果**: READY_FOR_VERIFICATION（附 1 条已记录偏离，见下）
- **执行者**: Impl02b（fast-p 子计划实施代理）
- **计划**: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-rhythm-and-filter/docs/plans/fast/batch-send-rhythm-and-filter/children/02b/brief.md`
- **计划 SHA-256**: `7910084bce16e899268fc55167d19b7531e30ff9c27b1f12021ec68247f8402f`（执行前与执行后复算一致）
- **Execution ID**: `<canonical_path>@7910084bce16e899268fc55167d19b7531e30ff9c27b1f12021ec68247f8402f`
- **Execution epoch**: NEW
- **目标 worktree**: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-rhythm-and-filter`
- **目标分支**: `fast/batch-send-rhythm-and-filter`（git-dir `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/batch-send-rhythm-and-filter`）
- **Pre-execution code SHA（产品基线）**: `d5370c6`（02a 提交；worktree HEAD 原为 d61b52e，docs-only evidence 提交）
- **Post-execution code SHA**: `919a0d6`（本计划实现提交）
- **实现提交**: `919a0d6 feat(fast-p): implement 02b`（13 个文件，39 insertions / 92 deletions）

## 任务状态

| 需求 | 状态 | 文件 | 证据 |
|---|---|---|---|
| I-1 零行为变更，diff 只含删除 | IMPLEMENTED | 5 个 src/main 文件 | diff 形态断言通过：src/main 新增行仅 LEGACY_DAILY_CAP_UNUSED 常量块（KDoc 2 行 + 声明 1 行 + 空行）、2 处常量使用、A-3#4 授权的 `dailyCap = 0` |
| I-2 KV 兼容层常量占位 | IMPLEMENTED | BatchSendTaskConfigService.kt | grep LEGACY_DAILY_CAP_UNUSED = 3（1 声明 + 2 使用）；grep Int.MAX_VALUE = 0 |
| I-3 新增 V92 迁移，无占位符 | IMPLEMENTED | V92__drop_daily_cap_from_batch_send_task_config.sql | 文件存在；`grep -c '\${'` = 0；V72/V91 未修改（git status 无输出） |
| I-4 服务端容忍前端 dailyCap | IMPLEMENTED（环境确认） | — | A-1 前置检查无 `fail-on-unknown-properties` 命中（grep exit 1） |
| KV 层未动 | IMPLEMENTED | BatchSendSettingService.kt | `git diff --stat` 输出为空 |
| 保留项 | IMPLEMENTED | BatchExecutionModels.kt / TaskExecutionService.kt / 两个 service | DAILY_CAP_EXCEEDED 命中 3（常量 + LABELS + annotateTerminalRemaining 映射，保留）；sumSuccessCountTodayByBatchConfigId 定义 1；`roundsPerRun = maxOf(1, ceil(` 两个 service 各 1 |
| 回归门禁 | IMPLEMENTED | 全部 13 个文件 | 全量测试 2347 通过；定向测试 136 通过；mvn clean package 通过；Flyway IT 环境阻塞（基线同样失败，见命令 4） |

## 命令记录

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `grep -rn "fail-on-unknown-properties\|FAIL_ON_UNKNOWN_PROPERTIES" src/main/resources/application.yml src/main/kotlin/com/weibo/talentintroduction/config/` | 1（无命中） | PASS（I-4 前置检查：无 true 命中） |
| 2 | `JAVA_HOME=zulu-11 mvn test` | 0 | PASS — `Tests run: 2347, Failures: 0, Errors: 0, Skipped: 4`，BUILD SUCCESS |
| 3 | `JAVA_HOME=zulu-11 mvn test -Dtest=BatchSendTaskConfigServiceTest,ManualInitialOutreachServiceTest,BatchSendControlServiceTest,BatchSendTaskRuntimeIntegrationTest,BatchSendSchedulerTest,BatchSendConfigControllerTest` | 0 | PASS — `Tests run: 136, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS |
| 4 | `JAVA_HOME=zulu-11 mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true`（需本地 Docker） | 1 | **环境阻塞（非实现缺陷）** — 首次因 Docker daemon 未运行失败；启动 OrbStack 并设 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`、`-Dapi.version=1.40`（testcontainers/docker-java 默认 API 1.32 被 OrbStack 拒绝）后，容器正常启动、迁移实际执行，但 9 个用例中 8 个在 **V82** 处失败：`V82 baseline drift: audited legacy QA rules changed; stop deployment and merge manually`。**基线对照**：在 base SHA `d5370c6` 的临时 worktree 上以相同命令复跑，结果逐字一致（`Tests run: 9, Errors: 8`，同一 V82 错误）。V82 早于本批 V91/V92，与本计划改动无关。**V92 独立验证**：在 scratch `mysql:8.0.36` 容器中按版本序应用 V1–V81（全部成功，跳过 V82–V90 门禁）+ V91 + V92，`SHOW COLUMNS FROM batch_send_task_config` 确认 `daily_cap` 列已删除、`rounds_per_run` 保留。 |
| 5 | `JAVA_HOME=zulu-11 mvn clean package` | 0 | PASS — `Tests run: 2347, Failures: 0, Errors: 0, Skipped: 4`，BUILD SUCCESS，产出 jar |
| 6 | `git diff --stat -- src/main` + `git diff -- src/main \| grep '^+' \| grep -v '^+++'` | 0 | PASS（I-1 diff 形态断言）— 注：本机 BSD grep 将 `^+` 误作空原子量词匹配所有行，故改用 `grep -E '^\+'` 复核；新增行仅：`LEGACY_DAILY_CAP_UNUSED` 常量 KDoc 2 行 + 声明 1 行 + 空行 1 行、2 处 `dailyCap = LEGACY_DAILY_CAP_UNUSED,`、`dailyCap = 0,`（A-3#4 授权）。stat：`5 files changed, 8 insertions(+), 27 deletions(-)` |
| 7 | `git diff --check` | 0 | PASS — 无输出（工作区与暂存区均干净） |

## 变更文件

实现提交 `919a0d6` 包含 13 个文件（brief 的 12 个授权文件 + 1 个偏离文件）：

| # | 文件 | 类型 | 改动摘要 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V92__drop_daily_cap_from_batch_send_task_config.sql` | 新增 | `ALTER TABLE batch_send_task_config DROP COLUMN daily_cap;` |
| 2 | `src/main/kotlin/.../campaign/domain/BatchSendTaskConfig.kt` | 修改 | 删 4 处 `val dailyCap: Int`（实体/View/CreateCommand/UpdateCommand） |
| 3 | `src/main/kotlin/.../campaign/domain/BatchExecutionModels.kt` | 修改 | 删 Snapshot 字段声明 + `toExecutionSnapshot()` 赋值 |
| 4 | `src/main/kotlin/.../campaign/service/BatchSendTaskConfigService.kt` | 修改 | 删 13 处（create/update 构造、updateLegacyConfig 命令、require、NormalizedConfig/ConfigFields、toFields×3、toView）；`updateLegacyConfig` 返回与 `toLegacyConfig()` 两处 `dailyCap = LEGACY_DAILY_CAP_UNUSED`；companion object 新增常量（含 KDoc） |
| 5 | `src/main/kotlin/.../campaign/service/BatchSendControlService.kt` | 修改 | 删 4 处（getStatus、validateSnapshotFields require、toLegacySnapshot 赋值、BatchSendStatusView 字段）；保留 `roundsPerRun = maxOf(1, ceil(dailyCap/roundSize))` 推导 |
| 6 | `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` | 修改 | 删 4 处（updateProgress details、toSnapshot 赋值、toBatchSendConfig 改 `dailyCap = 0`、updateProgressWithAccumulator details）；保留 roundsPerRun 推导 |
| 7–12 | 6 个授权测试文件（BatchSendTaskConfigServiceTest / ManualInitialOutreachServiceTest / BatchSendControlServiceTest / BatchSendTaskRuntimeIntegrationTest / BatchSendSchedulerTest / BatchSendConfigControllerTest） | 修改 | 删 helper 形参/实参、dailyCap 断言；删 `dailyCap = 0` 校验用例；KV 用例（ManualInitialOutreachServiceTest 2140–2211 行区域）一行未动 |
| 13（偏离） | `src/test/kotlin/.../mail/controller/MailAutomationControllerTest.kt` | 修改 | 删 3 处 `BatchSendStatusView` 构造/断言中的 dailyCap |

## 偏离

1. **新增第 13 个文件 `MailAutomationControllerTest.kt`**（brief 变更文件清单为 12 个）。
   - 原因：brief 的「全仓 11 个构造点」grep 实测未覆盖 `BatchSendStatusView(...)` 的 3 个构造/断言点（`MailAutomationControllerTest.kt` 原 :413/:431/:443）。删除 `BatchSendStatusView.dailyCap` 后该文件无法编译，`mvn test` 在 test-compile 阶段失败（Kotlin 编译器强制发现，恰为 brief 论证「不存在漏改一处但能编译通过」的机制）。
   - 授权依据：**主计划** `docs/plans/2026-08-12/batch-send-rhythm-and-filter-00-master.md` 第 32 行将 `MailAutomationControllerTest`（3 处）显式列入本批测试文件集；改动性质与其余 5 个测试文件完全一致（纯机械删除，无行为变更）。已在提交中一并包含。
2. **`BatchSendTaskConfigServiceTest` 的 `assertEquals(55, config.dailyCap)` 改为 `assertEquals(0, ...)`**：`getLegacyConfig` 测试断言旧 typed API 返回的 `BatchSendConfig.dailyCap`。I-2 将两处 legacy 构造改为 `LEGACY_DAILY_CAP_UNUSED = 0` 后原断言必然失败；改为断言 0 以锁定 I-2 契约（对应人工验收 A-5「GET 响应始终含 dailyCap 键（值为 0）」）。`BatchSendConfigControllerTest` 的 6 处旧 typed API dailyCap 断言同理 77/333/11/22/60/80 → 0（brief 要求「断言保留」，保留即需更新期望值）；实体行断言 `captor.value.dailyCap`（:135）因实体字段已删而删除。
3. **Flyway IT（命令 4）退出码 1**：V82 基线门禁在本环境 fresh MySQL 8.0.36 上失败，**基线上逐字复现**（d5370c6 临时 worktree 同命令同结果 8 errors）。属既有环境/数据门禁问题，非本计划缺陷；V92 已通过 scratch MySQL 容器 V1–V81 + V91 + V92 全链应用验证（`daily_cap` 列删除成功，`rounds_per_run` 保留）。

## 未改动确认

- `BatchSendSettingService.kt`（KV 兼容层）：`git diff --stat` 为空。
- `app.js` / `index.html`：无改动（I-4 依赖 Jackson 忽略未知字段，A-1 已确认 `fail-on-unknown-properties` 未开启）。
- `V72` / `V91`：`git status` 无输出（未修改）。
- `docs/plans/fast/` 与 `docs/plans/2026-08-12/`：未纳入实现提交（本报告文件亦不提交，由控制器单独提交证据）。

## 新鲜度

- Plan identity 复算: YES（SHA 未变）
- Worktree identity 复算: YES（含 --expect-root/--expect-branch/--expect-git-dir）
- 实现提交可达目标分支: YES（`git merge-base --is-ancestor 919a0d6 refs/heads/fast/batch-send-rhythm-and-filter`）
- 必需命令本次调用内新鲜执行: YES（命令 1–7 全部在本轮执行）
- 历史输出仅作基线: YES（Flyway IT 基线对照在 d5370c6 临时 worktree 上现跑复现）

## 剩余阻塞

- 无实现侧阻塞。Flyway IT 的 V82 基线门禁失败为本环境既有问题（基线复现一致），如需该测试绿灯需先解决 V82 gate 与当前 QA 规则数据的漂移，超出本计划范围。

## 下一步

- READY_FOR_VERIFICATION → 运行 `verify-p`（02b）
