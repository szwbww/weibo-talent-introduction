# 02b · 删除 dailyCap 字段与 daily_cap 列（纯机械删除）

> 主计划：`batch-send-rhythm-and-filter-00-master.md`
> 覆盖需求：第 1 条（后半：字段与列下线）
> 依赖：**02a 必须已完成**（所有语义已在 02a 移除，本计划零语义变更）
> 并行约束：与 04（前端）**不可同时进行**，见 X-1

## Phase 2 范围检查：已记录的偏离

本计划变更 **12 个文件**，超出 `create-p` 的 10 文件硬约束。这是一次**有意识、有补偿控制的偏离**，理由如下：

1. **零交互面**。10 文件限制的目的是约束「交互面」。02a 已移除 `dailyCap` 的全部语义（闸门、配额项、停止原因、参数流），本计划只删除一个已无任何读取方的字段声明与赋值。改动全部由 Kotlin 编译器强制发现，不存在「漏改一处但能编译通过」的静默失效。
2. **不可再拆**。删除 data class 字段会同时打断其全部具名参数构造点。`BatchSendTaskConfig(` 全仓 11 个构造点（grep 实测：`BatchSendConfigControllerTest.kt:52,73`、`BatchSendSchedulerTest.kt:26`、`BatchSendTaskRuntimeIntegrationTest.kt:630`、`BatchSendControlServiceTest.kt:78,204`、`BatchSendTaskConfigServiceTest.kt:117,428,467`、`ManualInitialOutreachServiceTest.kt:2240`、`BatchSendTaskConfigService.kt:51`），再拆会留下无法编译的中间提交。
3. **补偿控制**：① 本计划**不新增任何行为**，验收以「全量测试通过 + diff 只含删除」为主；② `## 验收标准` 增加一条 diff 形态断言（除迁移文件外，`git diff` 中不得出现新增逻辑行）；③ 12 个文件全部逐一列名并给出精确改动点。

## 需求描述

### Observable outcome

1. `batch_send_task_config` 表不再有 `daily_cap` 列；`GET /api/mail/batch-send/configs` 返回的配置对象不再有 `dailyCap` 字段。
2. 创建/更新定时任务时不再需要提供 `dailyCap`；提供了也会被忽略（Jackson 默认 `FAIL_ON_UNKNOWN_PROPERTIES = false`）。

### What must NOT change

- 任何运行时发送行为。本计划完成前后，同一配置在同一账号状态下发出的邮件数量、顺序、间隔逐字相同。
- 02a 建立的三道闸门（`roundsPerRun` / `roundSize` / 账号容量）。
- **KV 兼容层保留 `dailyCap`**：`BatchSendSettingService.kt` 的 `BatchSendConfig`（`:240-252`）、`BatchSendConfigUpdateRequest`（`:254-265`）、KV key `batchSend.dailyCap` / `batchSend.materialReminder.dailyCap`、旧 typed API `GET|PUT /api/mail/batch-send/config` 与 `/types/{sendType}/config` 的请求响应形态**全部不动**。理由：K-batch-send-setting-kv —— 该表是旧 typed API 的 KV 兼容层，不是配置 SSOT，删它属独立清理任务。
- `BatchOutcomeReasonCodes.DAILY_CAP_EXCEEDED` 与 `LABELS` 中的对应条目（`BatchExecutionModels.kt:92`、`:103`）保留。
- `TaskExecutionService.sumSuccessCountTodayByBatchConfigId()` 与 `TaskExecutionRepository.sumSuccessCountByBatchConfigIdBetween()` 保留（主计划已声明）。
- `task_execution` 表结构不动。

### Out of scope

- 前端删除日限额输入框与 diff 字段表 → 04
- KV 兼容层清理
- 任何行为变更

## 关键不变量

### Invariant I-1: 本计划零行为变更，diff 只含删除
- Rule: 除新增迁移文件 `V92` 外，本计划的 `git diff` 在 `src/main` 下**不得出现任何新增逻辑行**（新增行仅允许出现在：因删除参数导致的函数签名换行重排、以及 `toLegacyConfig()` 中的 `dailyCap` 常量占位一处，见 I-2）。
- Applies to: 全部 6 个 `src/main` 变更文件。
- Violation consequence: 本计划的验收完全依赖「无行为变更」这一前提；一旦掺入逻辑，12 文件的宽 diff 将无法有效评审。
- 来源: original

### Invariant I-2: KV 兼容层的 dailyCap 由常量占位，不再有真实来源
- Rule: `BatchSendTaskConfigService.toLegacyConfig()`（`:203-216`）与 `updateLegacyConfig()` 的返回构造（`:177-189`）需要给 `BatchSendConfig.dailyCap` 一个值，但实体已无该列。二者必须使用同一个具名常量 `LEGACY_DAILY_CAP_UNUSED = 0`（定义在 `BatchSendTaskConfigService` 的 companion object），并带注释说明「日限额已下线，此值仅为旧 typed API 保持字段形态，不参与任何判定」。**禁止**使用魔法数字、`Int.MAX_VALUE` 或从其他字段推导。
- Applies to: `BatchSendTaskConfigService.kt:181`、`:208`。
- Violation consequence: 用 `Int.MAX_VALUE` 或推导值会让旧 API 的消费者误以为该值仍有意义；用不同的字面量则两处不一致。
- 来源: original

### Invariant I-3: 迁移只能是新增文件，且不含 Flyway 占位符
- Rule: 新增 `V92__drop_daily_cap_from_batch_send_task_config.sql`，内容仅 `ALTER TABLE batch_send_task_config DROP COLUMN daily_cap;`。不得编辑 `V72` 或 `V91`。文件中不得出现 `${...}`（K-flyway-placeholder-replacement）。
- Applies to: `src/main/resources/db/migration/`。
- Violation consequence: 编辑已应用迁移会导致 Flyway checksum 校验失败，生产启动即挂。
- 来源: K-flyway-placeholder-replacement + 项目 CLAUDE.md「never edit an applied migration」

### Invariant I-4: 前端在 04 落地前仍会发送 dailyCap，服务端必须容忍
- Rule: `app.js:13481`（配置编辑器）与 `:13705`（手动 tab）当前仍在 payload 中发送 `dailyCap`。本计划删除 `BatchSendTaskConfigCreateCommand` / `UpdateCommand` / `BatchExecutionSnapshot` 的该字段后，这些请求必须**照常成功**（未知字段被忽略），不得返回 400/422。
- Applies to: `BatchSendConfigController` 的 `POST /configs`、`PUT /configs/{id}`、`POST /manual-executions`。
- Violation consequence: 若 Spring Boot 的 `spring.jackson.deserialization.fail-on-unknown-properties` 被显式开启，本计划一上线前端所有保存操作即 400。**执行前必须先确认该配置未被开启**（见 A-1 的前置检查）。
- 来源: original

## 现状审计

### `dailyCap` 在 02a 之后的残留点（grep 实测全集）

> 以下行号为 **02a 完成后**的预期位置；执行前须重新 grep 复核（02a 的删除会使行号上移）。

**`src/main` 侧 —— 6 个文件**

| 文件 | 残留点 | 性质 |
|---|---|---|
| `campaign/domain/BatchSendTaskConfig.kt` | `:15` 实体、`:37` View、`:55` CreateCommand、`:71` UpdateCommand | 字段声明 ×4 |
| `campaign/domain/BatchExecutionModels.kt` | `:10` Snapshot 字段、`:197` `toExecutionSnapshot()` 赋值 | 字段声明 + 赋值 |
| `campaign/service/BatchSendTaskConfigService.kt` | `:56` create 构造、`:87` update 构造、`:165` updateLegacyConfig 命令构造、`:181` 返回 BatchSendConfig、`:208` toLegacyConfig、`:228` `require(fields.dailyCap > 0)`、`:258` NormalizedConfig 构造、`:338` toView、`:390` ConfigFields 声明、`:407` NormalizedConfig 声明、`:423`/`:439`/`:455` 三处 `*Fields()` | 13 处 |
| `campaign/service/BatchSendControlService.kt` | `:323` `getStatus()` 的 `dailyCap = details?.asInt("dailyCap")`、`:450` `require(snapshot.dailyCap > 0)`、`:596` `toLegacySnapshot()` 赋值、`:710` `BatchSendStatusView.dailyCap` 声明 | 4 处 |
| `campaign/service/ManualInitialOutreachService.kt` | `:1049` `updateProgress` details、`:1231` `toSnapshot()` 赋值、`:1249` `toBatchSendConfig()` 赋值、`:1318` `updateProgressWithAccumulator` details | 4 处 |
| **`campaign/service/BatchSendSettingService.kt`** | `:69`/`:91`/`:146`/`:244`/`:257` | **不动**（KV 兼容层，must-NOT-change） |

> ⚠ `BatchSendControlService.kt:596` `toLegacySnapshot()` 与 `ManualInitialOutreachService.kt:1231` `toSnapshot()` 在 01 中被改为从 `dailyCap` 推导 `roundsPerRun`（`maxOf(1, ceil(dailyCap/roundSize))`）。本计划删除 `dailyCap` 后，这两处必须改为直接使用 `BatchSendConfig` 的某个来源。**由于 `BatchSendConfig`（KV 兼容层）保留 `dailyCap`，这两处的推导公式可原样保留** —— 它们的输入是 `BatchSendConfig.dailyCap`（KV 层，未删），不是 `BatchSendTaskConfig.dailyCap`（实体，已删）。执行时须逐字确认这一点，不要误删。

**`src/test` 侧 —— 6 个文件**

| 文件 | 残留点 | 改动性质 |
|---|---|---|
| `BatchSendTaskConfigServiceTest.kt` | `:48`/`:62`（createCmd helper）、`:78`/`:92`（updateCmd helper）、`:117`（entity helper）、`:304`（`dailyCap = 0` 校验用例）、`:352`/`:359`、`:428`、`:467`、`:505`/`:510` | 删 helper 形参与实参；**删除 `:304` 整个用例**（`dailyCap > 0` 校验已不存在）；`:359`/`:505`/`:510` 的 dailyCap 断言删除 |
| `ManualInitialOutreachServiceTest.kt` | `:116`/`:121`（snapshot helper）、`:713`（details 断言）、`:2240`（entity 构造）；`:1943-2000` 的 KV 用例**保留不动** | 删 helper 形参、删 `:713` 断言、删 `:2240` 实参 |
| `BatchSendControlServiceTest.kt` | `:46`、`:84`、`:123`、`:210`、`:482`（details map）、`:510`（`assertEquals(1000, status.dailyCap)`） | 删实参；**删除 `:482` 的 `"dailyCap" to 1000` 与 `:510` 断言** |
| `BatchSendTaskRuntimeIntegrationTest.kt` | `:152`/`:162`、`:169`/`:179`、`:185`/`:193`、`:199`/`:203`/`:213`、`:624`/`:632`（entity helper）、`:639`/`:646`（snapshot helper） | 删 helper 形参与全部 dailyCap 断言 |
| `BatchSendSchedulerTest.kt` | `:32` `dailyCap = 1000` | 删 1 行 |
| `BatchSendConfigControllerTest.kt` | `:52`、`:73` 两个 `BatchSendTaskConfig(` helper 中的 dailyCap | 删 2 行；该类中针对**旧 typed API** 的 dailyCap 断言保留（KV 层未改） |

### 交互点

| # | 写路径 | 读路径 | 处理 |
|---|---|---|---|
| X-1 | `app.js:13481` / `:13705` 仍发送 `dailyCap` | `BatchSendConfigController` 的 `@RequestBody` 反序列化 | I-4：依赖 Jackson 忽略未知字段。**因此 02b 与 04 不可同时进行**——若 04 先改前端而 02b 未上线，前端不发 `dailyCap` 会命中当时仍存在的 `require(fields.dailyCap > 0)` 而 422 |
| X-2 | `app.js:13571` `dailyCap: c.dailyCap \|\| 1000` 从列表响应读取 | 响应中该字段消失 → 回退为 `1000` | 无害（该值只进前端 draft，02b 后服务端忽略）；04 会彻底删除 |
| X-3 | V92 `DROP COLUMN` | Spring Data JDBC 的 `BatchSendTaskConfig` 映射 | 二者必须同一次发布；先删列后发代码会导致 `Unknown column` 查询异常 |

## 实现方案

### A-1 前置检查（执行前必做，非代码改动）

确认 `spring.jackson.deserialization.fail-on-unknown-properties` 未被显式开启（I-4）：

```bash
grep -rn "fail-on-unknown-properties\|FAIL_ON_UNKNOWN_PROPERTIES" \
  src/main/resources/application.yml src/main/kotlin/com/weibo/talentintroduction/config/
```

若有命中且值为 `true`，**停止执行本计划**，回到 create-p 增补处理方案。

### A-2 新增迁移（I-3）

`src/main/resources/db/migration/V92__drop_daily_cap_from_batch_send_task_config.sql`：

```sql
ALTER TABLE batch_send_task_config DROP COLUMN daily_cap;
```

### A-3 删除 `src/main` 侧字段（I-1、I-2）

按现状审计表逐点删除。四个特别注意点：

1. `BatchSendTaskConfigService.kt:228` 删除 `require(fields.dailyCap > 0) { "dailyCap must be > 0" }` 整行；**保留** `require(fields.roundSize > 0)` 与 01 引入的 `require(fields.roundsPerRun >= 1)`。
2. `BatchSendTaskConfigService.kt:181` / `:208`：`BatchSendConfig(...)` 构造中 `dailyCap = row.dailyCap` 改为 `dailyCap = LEGACY_DAILY_CAP_UNUSED`；在 companion object 中新增该常量（I-2）。**这是本计划允许的唯一新增逻辑行**。
3. `BatchSendControlService.kt:450` 删除 `require(snapshot.dailyCap > 0)`；`:596` `toLegacySnapshot()` 中删除 `dailyCap = dailyCap` 一行，但**保留** 01 引入的 `roundsPerRun = maxOf(1, ceil(dailyCap.toDouble() / roundSize).toInt())`——其 `dailyCap` 来自 `BatchSendConfig`（KV 层，未删）。
4. `ManualInitialOutreachService.kt:1231` `toSnapshot()` 同上；`:1249` `toBatchSendConfig()` 中 `dailyCap = dailyCap` 的右侧来自已删的 `BatchExecutionSnapshot.dailyCap`，改为 `dailyCap = 0`（该 `BatchSendConfig` 实例只在执行循环内当参数容器用，其 `dailyCap` 在 02a 后已无读点）。

### A-4 删除 `src/test` 侧残留

按现状审计表逐点删除。三个特别注意点：

1. `BatchSendTaskConfigServiceTest.kt:304` 的 `service().create(createCmd(name = "c", dailyCap = 0))` 用例**整体删除**（校验规则已不存在）——K-ui-removal-retires-obsolete-contract-tests。
2. `BatchSendControlServiceTest.kt:482`/`:510` 的 details map 条目与断言**整体删除**（`BatchSendStatusView.dailyCap` 已删）。
3. `ManualInitialOutreachServiceTest.kt:1943-2000` 的 6 个 `BatchSendSettingService` KV 用例**一行不改**——它们断言的是 KV 层，本计划未触及。执行时若发现它们编译失败，说明误删了 KV 层，属实现错误。

## 变更文件清单

| # | 文件 | 类型 | 改动摘要 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V92__drop_daily_cap_from_batch_send_task_config.sql` | 新增 | `DROP COLUMN daily_cap` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | 修改 | 删 4 处字段声明 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 修改 | 删 Snapshot 字段 + `toExecutionSnapshot` 赋值 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | 修改 | 删 13 处；新增 `LEGACY_DAILY_CAP_UNUSED` 常量 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 修改 | 删 4 处；保留 `roundsPerRun` 推导 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 修改 | 删 4 处；`toBatchSendConfig` 改常量 0 |
| 7 | `src/test/kotlin/.../campaign/service/BatchSendTaskConfigServiceTest.kt` | 修改 | 删 helper 形参与断言；删 `:304` 用例 |
| 8 | `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 | 删 helper 形参、`:713` 断言、`:2240` 实参 |
| 9 | `src/test/kotlin/.../campaign/service/BatchSendControlServiceTest.kt` | 修改 | 删实参；删 `:482` / `:510` |
| 10 | `src/test/kotlin/.../campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | 修改 | 删 helper 形参与断言 |
| 11 | `src/test/kotlin/.../task/service/BatchSendSchedulerTest.kt` | 修改 | 删 `:32` 一行 |
| 12 | `src/test/kotlin/.../mail/controller/BatchSendConfigControllerTest.kt` | 修改 | 删 `:52` / `:73` 两行 |

**文件数 12 —— 见本文档开头「Phase 2 范围检查：已记录的偏离」。独立子系统 1（批量发送配置）✅　新增字段 0 ✅**

> **不得**修改：`BatchSendSettingService.kt`、`app.js`、`index.html`、`V72`、`V91`。

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。

```bash
# 前置检查（A-1，必须先跑且应无 true 命中）
grep -rn "fail-on-unknown-properties\|FAIL_ON_UNKNOWN_PROPERTIES" \
  src/main/resources/application.yml src/main/kotlin/com/weibo/talentintroduction/config/

# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（Surefire 逗号分隔）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=BatchSendTaskConfigServiceTest,ManualInitialOutreachServiceTest,BatchSendControlServiceTest,BatchSendTaskRuntimeIntegrationTest,BatchSendSchedulerTest,BatchSendConfigControllerTest

# Flyway 迁移集成测试（验证 V92 可应用；需本地 Docker）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# diff 形态断言（I-1）：src/main 下新增行应仅有 LEGACY_DAILY_CAP_UNUSED 常量及其注释
git diff --stat -- src/main
git diff -- src/main | grep '^+' | grep -v '^+++'

# 空白/换行卫生
git diff --check
```

**通过判据**：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。`git diff --check` 无输出。
**来源**：`CLAUDE.md` 的「Commands」章节 + 项目元信息。

> 本计划不改 `.js` / `.html`，无需 `node --test` 门禁。

## 验收标准

- **I-1**：执行「验证命令」节的 diff 形态断言命令，`src/main` 新增行仅包含 `LEGACY_DAILY_CAP_UNUSED` 常量声明、其 KDoc 注释、以及因删参导致的签名换行；无其他逻辑行。
- **I-2**：grep `LEGACY_DAILY_CAP_UNUSED` 在 `BatchSendTaskConfigService.kt` 命中 3 次（1 处声明 + 2 处使用）；grep `Int.MAX_VALUE` 在该文件结果为空。
- **I-3**：`ls src/main/resources/db/migration/V92*` 存在；`git status` 显示 `V72` / `V91` 未修改；`grep -c '\${' V92__*.sql` 为 0。
- **I-4**：A-1 前置检查命令无 `true` 命中；人工验收 A-2 通过。
- **KV 层未动**：`git diff --stat -- src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt` 输出为空。
- **保留项**：grep `DAILY_CAP_EXCEEDED` 在 `BatchExecutionModels.kt` 仍命中 2 处；grep `sumSuccessCountTodayByBatchConfigId` 在 `TaskExecutionService.kt` 仍命中定义；grep `roundsPerRun = maxOf(1, ceil(` 在 `BatchSendControlService.kt` 与 `ManualInitialOutreachService.kt` 各命中 1 处。
- **回归**：执行「验证命令」节的全量测试命令通过；构建命令通过；Flyway 迁移集成测试命令通过。

## 人工验收清单

### A-1：列已删除，API 不再返回该字段
- 前置条件：应用已启动并应用 V92。
- 操作步骤：
  1. 执行 `SHOW COLUMNS FROM batch_send_task_config LIKE 'daily_cap';`
  2. 调用 `GET /api/mail/batch-send/configs`，查看任一配置对象
  3. 执行 `SELECT * FROM flyway_schema_history WHERE version = '92';`
- 预期结果：第 1 步返回 **0 行**；第 2 步返回的 JSON **不含** `dailyCap` 键，且仍含 `roundsPerRun` / `roundSize` / `cron`；第 3 步 `success = 1`。
- 覆盖：Observable outcome 1；I-3

### A-2：04 未上线前，前端保存仍成功
- 前置条件：前端为 **04 之前的版本**（配置编辑器仍有「日限额」输入框，`app.js:13481` 仍在 payload 中发 `dailyCap`）。
- 操作步骤：
  1. 打开「批量邮件任务控制台」→ 定时任务 → 编辑任一任务
  2. 修改「每轮数量」为 25，日限额输入框保持原值
  3. 点击「保存任务」
  4. 刷新列表，重新打开该任务
- 预期结果：保存成功，**不出现** 400 / 422 错误提示；重新打开后「每轮数量」为 25。
- 覆盖：Observable outcome 2；I-4；交互点 X-1

### A-3【回归】发送行为与 02a 完成时逐字一致
- 前置条件：与 02a 的 A-2 完全相同的环境（单账号 `dailySendLimit = 12`、`today_sent_count = 0`、`warmup_enabled = 0`；配置 `rounds_per_run = 10`、`round_size = 5`；ES 目标 ≥ 50）。
- 操作步骤：手动执行该配置，等待结束；再次手动执行。
- 预期结果：与 02a 的 A-2 **逐字相同**——第 1 次成功计数 12、结束消息「已达到今日发送上限」、`today_sent_count = 12`；第 2 次被拒并提示「今日发送额度已用尽（含预热限制），暂不可手动发送」。
- 覆盖：must-NOT-change 第 1、2 条

### A-4【回归】轮次预算仍生效
- 前置条件：配置 `rounds_per_run = 2`、`round_size = 20`；账号容量充足；ES 目标 ≥ 200。
- 操作步骤：手动执行，等待结束。
- 预期结果：成功计数恰好 40；结束消息「本次调度轮次已用完」。
- 覆盖：must-NOT-change 第 2 条

### A-5【回归】旧 typed API 的 dailyCap 形态未变
- 前置条件：无。
- 操作步骤：
  1. 调用 `GET /api/mail/batch-send/types/INTRODUCTION/config`
  2. 用返回体（含 `dailyCap`）原样 `PUT /api/mail/batch-send/types/INTRODUCTION/config`
  3. 再次 GET
- 预期结果：三步全部 200；GET 响应始终含 `dailyCap` 键（值为 `0`）；PUT 不报错；`roundSize` / `cron` / `roundsPerRun` 等其他字段在往返后不变。
- 覆盖：must-NOT-change 第 3 条；I-2

### A-6【回归】材料提醒任务可正常保存与执行
- 前置条件：`材料提醒任务` 配置存在且已指定模板。
- 操作步骤：
  1. 在配置编辑器中打开该任务，修改「每轮间隔」为 90 秒并保存
  2. 手动执行一次
- 预期结果：保存成功；执行正常启动并结束，无 `Unknown column 'daily_cap'` 类异常出现在应用日志中。
- 覆盖：交互点 X-3

## 修正记录

（暂无）
