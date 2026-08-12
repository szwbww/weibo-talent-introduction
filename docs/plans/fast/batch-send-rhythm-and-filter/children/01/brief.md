# 01 · 定时任务新增「执行轮次数」(roundsPerRun)

> 主计划：`batch-send-rhythm-and-filter-00-master.md`
> 覆盖需求：第 2 条
> 依赖：无。**必须先于 02（拆日限额）执行**（主计划 G-2）

## 需求描述

### Observable outcome

1. `batch_send_task_config` 新增「执行轮次数」配置项。一次定时调度最多执行 N 轮，N 由该配置决定；轮次用尽即结束本次执行，剩余目标留给下一个 cron 周期。
2. 单次调度的发送量上界变为可直接口算：`执行轮次数 × 每轮数量`。例：轮次 2、每轮 20 → 本次调度最多 40 封。
3. 执行进度中可读到当前轮次与轮次上限（`roundNumber` / `roundsPerRun`），供后续控制台展示「第 2/2 轮」。

### What must NOT change

- `dailyCap` 的现有闸门行为（4 处 `startXxx` 预检 + `roundQuota` 参与项）在本计划内**原样保留**，由 02 移除。
- 账号侧限额与预热压制（`SenderWarmupService.remainingCapacity` / `dailyState`）语义不变；`ignoreWarmup = (mode == MANUAL)` 不变。
- 手动单轮语义不变：`oneRoundOnly = true` 时仍恰好执行 1 轮并以 `stopReason = "ONE_ROUND_DONE"` / `finalStatus = "PAUSED"` 返回。
- 现有 6 个 `stopReason`（`CANCELLED` / `DAILY_CAP_REACHED` / `DAILY_LIMIT_REACHED` / `WARMUP_LIMIT_REACHED` / `NO_AVAILABLE_ACCOUNT` / `ONE_ROUND_DONE`）的取值、消息文案与 runtime status 映射不变。
- 迁移执行后，两条 seeded 配置（`默认介绍邮件任务` / `材料提醒任务`）的实际单次发送量不变。
- 前端不做任何改动，`app.js` / `index.html` 不在本计划范围。

### Out of scope

- 前端「执行轮次」输入框 → 04
- 移除 `dailyCap` → 02
- 地区多选、地区中文、学科未分类、cron 自定义、列表执行时间列 → 03/04/05
- 不改 `BatchSendSettingService.kt`（本计划刻意通过直接消费 `snapshot.roundsPerRun` 规避该文件，见 I-3）
- 不改 `SenderWarmupService` / `AccountRateLimiter`
- 不新增 API 端点

## 关键不变量

### Invariant I-1: roundsPerRun 是「单次执行」上界，不跨执行累计
- Rule: `roundsPerRun` 约束的是**一次 `ManualInitialOutreachService.run()` 调用内已开始的轮次数**，计数器随执行开始归零，不持久化、不跨 cron 周期累计。与 `dailyCap`（自然日累计，跨执行）语义正交。
- Applies to: `ManualInitialOutreachService.runIntroductionFromSnapshot()` 的轮次循环、`ManualInitialOutreachService.runMaterialFromSnapshot()` 的轮次循环。二者是仅有的两个轮次循环。
- Violation consequence: 若误按自然日累计实现，会与 02 移除 `dailyCap` 的目标冲突，重新引入一个跨执行日额度，需求方明确不要。
- 来源: original

### Invariant I-2: 轮次预算耗尽是正常完成，不是暂停
- Rule: 因 `roundsPerRun` 用尽而结束时，`stopReason = "ROUNDS_PER_RUN_REACHED"`，`finalStatus = "COMPLETED"`。经 `BatchSendControlService.applyResultToRuntimeStatus()` 的 `else` 分支落到 runtime status `IDLE`，`autoEnabled` 不变，下个 cron 周期正常触发。
- Applies to: `ManualInitialOutreachService` 两个轮次循环的 break 分支、`stopReasonMessage()`、`BatchSendControlService.applyResultToRuntimeStatus()`。
- Violation consequence: 若映射为 `PAUSED`，定时任务会在第一次跑满轮次后停摆，需人工恢复——与需求方「仅结束本次执行」的决策直接冲突。
- 来源: original（需求方 2026-08-12 决策：仅结束本次执行）

### Invariant I-3: 执行循环只读启动快照的 roundsPerRun
- Rule: 轮次上界从 `BatchExecutionSnapshot.roundsPerRun` 读取，**不得**在循环内查 `batch_send_task_config`，也**不得**经由 `BatchSendConfig`（`BatchSendSettingService.kt:240` 的 KV 兼容 data class）中转。
- Applies to: `runIntroductionFromSnapshot()`（`snapshot` 已在作用域内）、`runMaterialFromSnapshot()`（同）。
- Violation consequence: 配置编辑污染在途任务；且会把 `BatchSendSettingService.kt` 拖进变更范围，突破本计划文件预算。
- 来源: K-batch-task-config-snapshot-log-identity

### Invariant I-4: oneRoundOnly 优先于 roundsPerRun
- Rule: `snapshot.oneRoundOnly == true` 时，有效轮次上界恒为 1，与 `roundsPerRun` 取值无关（即使 `roundsPerRun = 5`）。实现上 `oneRoundOnly` 的 break 在轮末，`roundsPerRun` 的 break 在轮首，天然满足；但必须有测试锁定。
- Applies to: 两个轮次循环；`BatchSendControlService.runManualOnce()` / `startManual()` 构造的 `oneRoundOnly = true` 快照。
- Violation consequence: 手动单轮按钮变成「跑 N 轮」，操作端失去逐轮观察能力。
- 来源: original

### Invariant I-5: 每条 batch_send_task_config 行的 rounds_per_run 恒 ≥ 1
- Rule: 列定义 `NOT NULL`，服务端 `normalizeAndValidate()` 强制 `require(roundsPerRun >= 1)`。迁移对存量行回填 `GREATEST(1, CEIL(daily_cap / round_size))`。
- Applies to: `V91` 迁移、`BatchSendTaskConfigService.normalizeAndValidate()`、`BatchSendControlService.validateSnapshotFields()`。
- Violation consequence: `roundsPerRun = 0` 会让轮首检查在第 0 轮即 break，任务永远发不出任何邮件且无报错，属静默失效。
- 来源: original

### Invariant I-6: 迁移回填必须保持存量配置的实际发送量不变
- Rule: 回填值 `CEIL(daily_cap / round_size)` 恰好是「在 `dailyCap` 闸门下最多能开始的轮次数」，故迁移后两条 seeded 配置的单次执行发送量与迁移前逐字相同（`默认介绍邮件任务` 1000/50 → 20 轮；`材料提醒任务` 60/30 → 2 轮）。
- Applies to: `V91` 迁移的 UPDATE 语句；`BatchSendControlService.toLegacySnapshot()` 与 `ManualInitialOutreachService.toSnapshot()` 两个 legacy KV 快照构造点必须用同一公式。
- Violation consequence: 上线即改变生产发送量，运营无感知。
- 来源: original

## 现状审计

### 存储：MySQL `batch_send_task_config`

**Schema**（`src/main/resources/db/migration/V72__create_batch_send_task_config.sql:1-31`）

关键列：`daily_cap INT NOT NULL`、`round_size INT NOT NULL`、`per_mail_interval_ms BIGINT NOT NULL`、`per_round_interval_ms BIGINT NOT NULL`、`self_check_ttl_minutes INT NOT NULL`、`cron VARCHAR(64) NOT NULL`、`legacy_code VARCHAR(64) NULL UNIQUE`、`deleted_at DATETIME NULL`。
另有生成列 `active_config_name GENERATED ALWAYS AS (IF(deleted_at IS NULL, config_name, NULL)) STORED` + `UNIQUE KEY uk_batch_send_task_config_active_name`（K-batch-send-config-active-name-unique）。

存量行：`V72:34-140` seed 了 2 条（`legacy_code = 'INTRODUCTION'` / `'MATERIAL_REMINDER'`），均带 `WHERE NOT EXISTS` 幂等保护。

> ⚠ 迁移占位符：`V72` 不含 `${...}`，本计划新增的 `V91` 也不得含 `${...}`。生产 `application.yml` 未关 `placeholder-replacement`（K-flyway-placeholder-replacement）。

**Write paths（grep `batch_send_task_config` + `BatchSendTaskConfigRepository`，实测全集）**

1. `V72__create_batch_send_task_config.sql:34,87` — 建表 + seed 2 条 legacy 行
2. `BatchSendTaskConfigService.create()`（`:56` 附近）— 写入 `dailyCap` 等全部字段，经 `normalizeAndValidate()`
3. `BatchSendTaskConfigService.update()`（`:87` 附近）— 同上
4. `BatchSendTaskConfigService.updateLegacyConfig()`（`:155-190`）— 旧 typed API 适配器，复用 `update()`（K-batch-send-legacy-routes-entity-ssot：禁止退回 KV）
5. `BatchSendTaskConfigService.setEnabled()` / `softDelete()` — 只改 `autoEnabled` / `deletedAt`，不涉本计划字段

→ 本计划需改动 2、3、4（新字段透传），1 由新迁移 V91 补列。

**Read paths（实测全集）**

1. `BatchSendControlService.startScheduled()`（`:55-83`）— `findByIdAndDeletedAtIsNull` → `toExecutionSnapshot()`
2. `BatchSendControlService.startManualFromConfig()`（`:113-122`）— 同上
3. `BatchSendControlService.startAuto()`（`:129-157`）— `findByLegacyCode` → `toExecutionSnapshot()`
4. `BatchSendControlService.startManual(sendType)`（`:167-192`）— 同上
5. `BatchSendControlService.runManualOnce()`（`:276-303`）— 同上，`oneRoundOnly = true`
6. `BatchSendScheduler.triggerBatchSend()`（`:81-95`）— 只读 `autoEnabled` / `cron`
7. `BatchSendScheduler.reload()`（`:60-77`）— 只读 `id` / `cron` / `autoEnabled`
8. `BatchSendTaskConfigService.list()` / `getById()` — 转 `BatchSendTaskConfigView` 供前端

→ 1~5 全部经 `BatchSendTaskConfig.toExecutionSnapshot()`（`BatchExecutionModels.kt:183-209`）这一**单一转换点**，新字段只需在此处加一行即可覆盖 5 条读路径。8 需在 View 加字段。6、7 无关。

### 存储：`BatchExecutionSnapshot`（进程内启动快照，非持久化）

定义 `BatchExecutionModels.kt:8-21`。

**构造点（实测全集，共 4 处）**

1. `BatchSendTaskConfig.toExecutionSnapshot()`（`BatchExecutionModels.kt:183-209`）— 实体路径，覆盖上述 5 条读路径
2. `BatchSendControlService.toLegacySnapshot()`（`BatchSendControlService.kt:593-607`）— legacy KV 兜底路径（`legacy_code` 行缺失时）
3. `ManualInitialOutreachService.toSnapshot()`（`ManualInitialOutreachService.kt:1228-1242`）— `runBulkOutreach()` / `runScheduledBatch()` / `runMaterialReminderBatch()` 三个旧入口共用
4. 前端手动 tab 直接 POST 的 `ManualBatchExecutionRequest.snapshot`（反序列化构造）— 由 Jackson 按 data class 默认值填充

→ 2、3 是 `BatchSendConfig` → snapshot 的转换，`BatchSendConfig` 无 `roundsPerRun`，须用 I-6 公式推导。4 依赖 data class 默认值（见 I-5，默认值须 ≥ 1）。

**消费点（实测全集，共 3 处）**

1. `BatchSendControlService.validateSnapshotFields()`（`:448-465`）— 参数校验
2. `ManualInitialOutreachService.run()`（`:132-143`）— 按 `mailType` 分发到两个循环
3. `BatchSendControlService.launchFromSnapshot()`（`:338-446`）— 只读 `mailType` / `oneRoundOnly` / `templateId`

### 执行循环（本计划的核心改动点）

**两个轮次循环，结构同构：**

| | INTRODUCTION | MATERIAL_REMINDER |
|---|---|---|
| 方法 | `runIntroductionFromSnapshot()` | `runMaterialFromSnapshot()` |
| 循环头 | `ManualInitialOutreachService.kt:499` `while (targetIterator.hasNext())` | `:209` `while (targetIndex < targets.size)` |
| 取消检查 | `:501-506` | `:211-216` |
| `roundNumber++` | `:509` | `:219` |
| 轮次门 | `:510-517` `runRoundGate()` | `:220-227` |
| 配额计算 | `:520-543` `minOf(roundSize, dailyCapRemaining, estimatedRemaining, remainingAccountCapacity)` | `:230-249` 同 |
| 内层发送 | `:551-798` | `:258-373` |
| `oneRoundOnly` break | `:808-813` | `:382-387` |
| 轮间 sleep | `:816-818` | `:389-391` |

→ 新增的轮首预算检查须**对称落到两处**；轮间 sleep 也须对称加守卫（否则跑满预算后仍白等一个 `perRoundIntervalMs`，介绍邮件默认 60s、材料提醒默认 120s）。

**`stopReason` 消费点（实测全集）**

1. `ManualInitialOutreachService.stopReasonMessage()`（`:863-880`）— when 分支，缺失 case 落 `else`
2. `BatchSendControlService.applyResultToRuntimeStatus()`（`:499-532`）— 按 `finalStatus` 映射 runtime status
3. `BatchSendControlService.idleSafeOneRoundStopReasons`（`:694-700`）— 仅对 `oneRoundOnly` 生效的 IDLE 白名单
4. `ManualInitialOutreachService.updateProgress()` 的 `details["stopReason"]`（`:1058-1060`）— 落进度详情

→ 新 `stopReason` 须在 1 加分支（否则文案退化为「发送任务已完成」这类泛化 else）；2 无需改（`COMPLETED` 已有 else 分支）；3 无需改（`ROUNDS_PER_RUN_REACHED` 不会与 `oneRoundOnly` 同时出现，见 I-4）。

### 交互点

| # | 写路径 | 读路径 | 本计划的处理 |
|---|---|---|---|
| X-1 | `V91` 回填 `rounds_per_run` | `toExecutionSnapshot()` → 两个轮次循环 | 回填公式与 legacy 快照公式必须一致（I-6） |
| X-2 | `BatchSendTaskConfigService.update()` | `BatchSendScheduler.reload()` | 新字段不参与调度键，reload 逻辑不受影响；须有测试确认改 `roundsPerRun` 不触发无谓重排（K-batch-send-scheduler-reschedule-on-enable 的反向确认） |
| X-3 | 前端 POST 的手动 snapshot（本计划前端未改，字段缺失） | `validateSnapshotFields()` | data class 默认值兜底，且默认值须 ≥ 1（I-5），否则手动 tab 在 04 上线前直接 422 |
| X-4 | `updateLegacyConfig()`（旧 typed API） | `toExecutionSnapshot()` | 旧 API 请求体无 `roundsPerRun`，须保留实体现值而非覆盖为默认值 |

> X-4 是本计划最易漏的一处：`updateLegacyConfig()`（`BatchSendTaskConfigService.kt:156-190`）已有「保留实体 `configName` / `funnelLevel` / `tags`」的先例，`roundsPerRun` 必须加入同一保留集合。

## 实现方案

### 阶段 A：持久化与配置服务

**A-1 新增迁移 `V91__add_rounds_per_run_to_batch_send_task_config.sql`**（遵守 I-5、I-6）

文件：`src/main/resources/db/migration/V91__add_rounds_per_run_to_batch_send_task_config.sql`

内容要求（逐条）：
- `ALTER TABLE batch_send_task_config ADD COLUMN rounds_per_run INT NOT NULL DEFAULT 1;`，列位置置于 `round_size` 之后
- 紧随一条 `UPDATE batch_send_task_config SET rounds_per_run = GREATEST(1, CEIL(daily_cap / round_size));`，对**全部行**（含已软删除行，保持数据一致）回填
- 不含任何 `${...}`（K-flyway-placeholder-replacement）
- 不修改 `V72`（已应用迁移禁止编辑）

**A-2 `BatchSendTaskConfig.kt` 加字段**（遵守 I-5）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`

4 个 data class 各加一个字段，位置紧随 `roundSize`，**四个都必须带默认值 `= 1`**：
- `BatchSendTaskConfig`：`val roundsPerRun: Int = 1`
- `BatchSendTaskConfigView`：`val roundsPerRun: Int = 1`
- `BatchSendTaskConfigCreateCommand`：`val roundsPerRun: Int = 1`
- `BatchSendTaskConfigUpdateCommand`：`val roundsPerRun: Int = 1`

> **为什么 `BatchSendTaskConfig` 也必须带默认值**（grep 实证）：`BatchSendTaskConfig(` 全仓有 **11 个构造点**，其中 10 个在测试里：
> `BatchSendConfigControllerTest.kt:52,73`、`BatchSendSchedulerTest.kt:26`、`BatchSendTaskRuntimeIntegrationTest.kt:630`、`BatchSendControlServiceTest.kt:78,204`、`BatchSendTaskConfigServiceTest.kt:117,428,467`、`ManualInitialOutreachServiceTest.kt:2240`，生产侧仅 `BatchSendTaskConfigService.kt:51`。
> 若不带默认值，这 10 处全部编译失败，会把 `BatchSendConfigControllerTest` / `BatchSendSchedulerTest` / `BatchSendTaskRuntimeIntegrationTest` 三个本计划范围外的测试类拖进来，文件数从 9 涨到 12，突破硬约束。
> 默认值 `1` 是安全侧（少发不多发），且实际取值一律由 `BatchSendTaskConfigService.create/update` 显式写入或由 V91 回填，默认值只在测试构造与 Jackson 反序列化缺字段时生效。
> `BatchExecutionSnapshot(` 同理有 3 个测试构造点（`BatchSendControlServiceTest.kt:45,122`、`BatchSendTaskRuntimeIntegrationTest.kt:645`），A-4 的 `= 1` 默认值同样是必需的而非可选。

**A-3 `BatchSendTaskConfigService.kt` 透传与校验**（遵守 I-5、X-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`

- `ConfigFields`（`:390` 附近）与 `NormalizedConfig`（`:407` 附近）各加 `val roundsPerRun: Int`
- `normalizeAndValidate()`（`:218-269`）：在 `require(fields.roundSize > 0)` 之后加 `require(fields.roundsPerRun >= 1) { "roundsPerRun must be >= 1" }`；`NormalizedConfig(...)` 构造加 `roundsPerRun = fields.roundsPerRun`
- `create()` / `update()` 的实体构造（`:56` / `:87`）加 `roundsPerRun = normalized.roundsPerRun`
- `toView()`（`:338` 的 `dailyCap = row.dailyCap` 处，**这是唯一的 row→View 映射**）加 `roundsPerRun = row.roundsPerRun`
- **`updateLegacyConfig()`（`:156-190`）**：`BatchSendTaskConfigUpdateCommand(...)` 中（`:165` 的 `dailyCap = request.dailyCap` 附近）加 `roundsPerRun = existing.roundsPerRun`，与既有的 `configName = existing.configName` 同列。**不得**使用命令默认值——旧 typed API 请求体无此字段，用默认值会把存量配置静默改为 1 轮（X-4）
- 各 `*Fields()` 提取函数（`:423` / `:439` / `:455` 三处 `dailyCap = dailyCap`）同步加 `roundsPerRun = roundsPerRun`

> **不得改动** `:181`（`updateLegacyConfig` 的返回映射）与 `:208`（`toLegacyConfig`）——这两处目标类型是 `BatchSendConfig`，该 data class 刻意不承载 `roundsPerRun`（I-3）。

**A-4 `BatchExecutionModels.kt` 快照字段**（遵守 I-3、I-5、I-6）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`

- `BatchExecutionSnapshot`（`:8-21`）加 `val roundsPerRun: Int = 1`，位置紧随 `roundSize`。**默认值必须是 1 而非 0**（I-5、X-3）
- `BatchSendTaskConfig.toExecutionSnapshot()`（`:183-209`）加 `roundsPerRun = roundsPerRun`

### 阶段 B：执行循环与控制服务

**B-1 `BatchSendControlService.kt`**（遵守 I-2、I-5、I-6）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`

- `validateSnapshotFields()`（`:448-465`）：在 `require(snapshot.roundSize > 0)` 之后加 `require(snapshot.roundsPerRun >= 1) { "roundsPerRun must be >= 1" }`
- `toLegacySnapshot()`（`:593-607`）：加 `roundsPerRun = maxOf(1, ceil(dailyCap.toDouble() / roundSize).toInt())`，与 A-1 回填公式同构（I-6）
- `BatchSendStatusView`（`:704-722`）加 `val roundsPerRun: Int = 0`；`getStatus()`（`:317-335`）加 `roundsPerRun = details?.asInt("roundsPerRun") ?: 0`
- `applyResultToRuntimeStatus()` / `idleSafeOneRoundStopReasons` **不改**（`COMPLETED` 已有 else 分支落 IDLE，见 I-2 与现状审计）

**B-2 `ManualInitialOutreachService.kt` 双循环对称改造**（遵守 I-1、I-2、I-3、I-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

对 `runIntroductionFromSnapshot()` 与 `runMaterialFromSnapshot()` **两处**做完全对称的 4 项改动：

1. **轮首预算检查** —— 插在取消检查之后、`roundNumber++` 之前（介绍邮件 `:507`、材料提醒 `:217`）：
   ```
   if (roundNumber >= snapshot.roundsPerRun) {
       stopReason = "ROUNDS_PER_RUN_REACHED"
       finalStatus = "COMPLETED"
       break
   }
   ```
   读 `snapshot.roundsPerRun` 而非 `config.roundsPerRun`（I-3：`config` 是 `snapshot.toBatchSendConfig()` 的产物，不承载该字段）。
2. **轮间 sleep 守卫** —— 介绍邮件 `:816`、材料提醒 `:389` 的 `if (config.perRoundIntervalMs > 0 && ...)` 条件追加 `&& roundNumber < snapshot.roundsPerRun`，避免跑满预算后白等一个轮间隔。
3. **进度详情** —— 存在**两个**独立的进度写入方法，各自维护自己的 `details` map，必须都改：
   - `updateProgress()`（`:1030-1072`，`details` 中 `"dailyCap" to config.dailyCap` 在 `:1049`）
   - `updateProgressWithAccumulator()`（`:1292-1311`，`details` 中 `"dailyCap" to config.dailyCap` 在 `:1318`）——**这才是两个轮次循环实际调用的那个**，`updateProgress()` 仅被材料提醒的空快照分支（`:190`）调用

   两处各加 `"roundsPerRun" to roundsPerRun`。两个方法签名当前均无 `snapshot`，各新增一个 `roundsPerRun: Int = 0` 形参，并由**全部 13 个调用点**显式传入 `snapshot.roundsPerRun`。

   **调用点全集（grep 实测，逐行列出）**：
   ```
   $ grep -n "updateProgressWithAccumulator(" ManualInitialOutreachService.kt
   ```
   | 归属 | 行号 | 数量 |
   |---|---|---|
   | `runMaterialFromSnapshot()`（`:168-405`） | 269, 298, 364, 377, 400 | **5** |
   | `runIntroductionFromSnapshot()`（`:448-833`） | 472（空快照分支，在循环外）, 562, 580, 649, 661, 789, 803, 828 | **8** |
   | 方法定义 | 1292 | — |

   `updateProgress()`（`:1030`）另有 1 个调用点：`:190`（材料提醒空快照分支），需同样加形参并传值。

   > ⚠ 形参默认值给 `0` 而非 `1`：这里是**展示用**的进度字段，`0` 表示「未提供」比伪造成 `1` 更诚实；与 I-5 要求的**配置字段**默认值 `1` 是两回事，不要混淆。
4. **停止文案** —— `stopReasonMessage()`（`:863-880`）在 `"ONE_ROUND_DONE"` 分支前加：
   ```
   "ROUNDS_PER_RUN_REACHED" -> "本次调度轮次已用完"
   ```

`toSnapshot()`（`:1228-1242`）加 `roundsPerRun = maxOf(1, ceil(dailyCap.toDouble() / roundSize).toInt())`（I-6，与 B-1 同公式）。
`toBatchSendConfig()`（`:1244-1257`）**不改**——`BatchSendConfig` 刻意不承载 `roundsPerRun`（I-3）。

### 阶段 C：测试

**C-1 `BatchSendTaskConfigServiceTest.kt`** — 新增 4 个用例：
- `roundsPerRun = 0` 与负值创建 → `IllegalArgumentException`，消息含 `roundsPerRun must be >= 1`（I-5）
- `roundsPerRun = 3` 创建后 `getById()` 返回的 View 携带 3（A-3 三处映射全覆盖）
- `updateLegacyConfig()` 在请求体不含 `roundsPerRun` 时保留实体现值（X-4）——构造实体 `roundsPerRun = 7`，调用旧 typed 更新，断言仍为 7 而非 1
- `update()` 仅改 `roundsPerRun` 时不触发调度重排事件（X-2）

**C-2 `ManualInitialOutreachServiceTest.kt`** — 新增 5 个用例（介绍邮件 3 个 + 材料提醒 2 个）：
- `roundsPerRun = 2`、`roundSize = 20`、ES 目标 100 → 恰好发出 40 封，`stopReason = "ROUNDS_PER_RUN_REACHED"`，`finalStatus = "COMPLETED"`（Observable outcome 2、I-1、I-2）
- `roundsPerRun = 5` 但 ES 目标仅 10、`roundSize = 20` → 1 轮结束，`stopReason` **不是** `ROUNDS_PER_RUN_REACHED`（预算未用尽不得误报）
- `oneRoundOnly = true` 且 `roundsPerRun = 5` → 恰好 1 轮，`stopReason = "ONE_ROUND_DONE"`、`finalStatus = "PAUSED"`（I-4）
- 材料提醒路径同第 1 条（循环对称性，B-2）
- 跑满预算后**不**执行轮间 sleep：以 `perRoundIntervalMs = 120000` 构造，断言方法整体耗时 < 5s（B-2 第 2 项）

**C-3 `BatchSendControlServiceTest.kt`** — 新增 2 个用例：
- `roundsPerRun = 0` 的快照经 `startManual()` → HTTP 422，消息含 `roundsPerRun must be >= 1`（I-5、B-1）
- legacy KV 兜底路径（`findByLegacyCode` 返回 null）构造的快照 `roundsPerRun == ceil(dailyCap / roundSize)`（I-6、X-1）

## 变更文件清单

| # | 文件 | 类型 | 改动摘要 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V91__add_rounds_per_run_to_batch_send_task_config.sql` | 新增 | ADD COLUMN + 全表回填 `GREATEST(1, CEIL(daily_cap/round_size))` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | 修改 | 4 个 data class 各加 `roundsPerRun` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 修改 | Snapshot 加字段（默认 1）+ `toExecutionSnapshot()` 透传 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | 修改 | ConfigFields/NormalizedConfig 加字段、`require >= 1`、create/update/3 处 toView/`updateLegacyConfig` 保留现值/3 处 `*Fields()` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 修改 | `validateSnapshotFields` 加校验、`toLegacySnapshot` 推导、`BatchSendStatusView` + `getStatus` 加字段 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 修改 | 双循环轮首预算检查 + sleep 守卫 + 进度详情 + `stopReasonMessage` 分支 + `toSnapshot()` 推导 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` | 修改 | +4 用例（C-1） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 | +5 用例（C-2） |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt` | 修改 | +2 用例（C-3） |

**文件数 9 ≤ 10 ✅　独立子系统 2（批量发送配置 / 发送执行循环）≤ 2 ✅　新增字段 1（`rounds_per_run`）✅**

> 未列入清单即为超范围。特别地：`BatchSendSettingService.kt`、`app.js`、`index.html`、`BatchSendScheduler.kt`、`BatchSendTaskRuntimeIntegrationTest.kt` 均**不得**在本计划中修改。若执行中发现必须改，说明本计划审计有误，应回到 create-p 修订而非就地扩范围。

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。以下命令可原样复制到终端执行。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（快速迭代用，三个类一次跑完；Surefire 用逗号分隔）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=BatchSendTaskConfigServiceTest,ManualInitialOutreachServiceTest,BatchSendControlServiceTest

# 单个测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest

# 单个测试方法
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest#methodName

# Flyway 迁移集成测试（验证 V91 可应用；需本地 Docker，默认跳过）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

**通过判据**：退出码 0，且 `mvn test` 输出末尾出现 `Tests run: N, Failures: 0, Errors: 0`（`Skipped` 允许非零——`FlywayMigrationIntegrationTest` 默认被 `@EnabledIfSystemProperty(named = "migrationIt")` 跳过）。`git diff --check` 无输出。

**来源**：`CLAUDE.md` 的「Commands」章节 + 项目元信息 `test_command` / `build_command`。

> 本计划**不**改动任何 `.js` / `.html`，故无需前端 `node --test` 门禁（K-js-test-invocation-surface）。若执行中发现前端必须改，即为范围突破，见变更文件清单末尾说明。

## 验收标准

- **I-1**：`ManualInitialOutreachServiceTest` 中断言轮次计数器为方法局部变量、随 `run()` 调用归零；grep 确认 `roundsPerRun` 在 `ManualInitialOutreachService.kt` 中无任何 repository / `sumSuccessCountToday*` 关联调用。
- **I-2**：C-2 第 1 个用例断言 `result.stopReason == "ROUNDS_PER_RUN_REACHED" && result.finalStatus == "COMPLETED"`；grep 确认 `BatchSendControlService.applyResultToRuntimeStatus()` 的 `when` 未新增 `PAUSED` 分支。
- **I-3**：grep `ManualInitialOutreachService.kt` 中 `roundsPerRun` 的全部出现点，必须全部形如 `snapshot.roundsPerRun`，不得出现 `config.roundsPerRun`；`BatchSendSettingService.kt` 的 `git diff` 为空。
- **I-4**：C-2 第 3 个用例断言 `oneRoundOnly = true, roundsPerRun = 5` → 恰好 1 轮。
- **I-5**：C-1 前两个用例 + C-3 第 1 个用例；grep `BatchExecutionModels.kt` 确认 `roundsPerRun: Int = 1`（非 `= 0`）。
- **I-6**：C-3 第 2 个用例；diff 比对 `V91` 的 UPDATE 公式、`BatchSendControlService.toLegacySnapshot()`、`ManualInitialOutreachService.toSnapshot()` 三处的取整表达式逐字同构（均为 `GREATEST(1, CEIL(...))` / `maxOf(1, ceil(...).toInt())`）。
- **X-2**：C-1 第 4 个用例。
- **X-4**：C-1 第 3 个用例。
- **双循环对称性（B-2）**：diff 确认 `runIntroductionFromSnapshot` 与 `runMaterialFromSnapshot` 各自都出现了 4 项改动；C-2 第 4 个用例覆盖材料提醒路径。
- **回归**：执行「验证命令」节的全量测试命令通过；执行「验证命令」节的构建命令通过；执行「验证命令」节的 Flyway 迁移集成测试命令通过。

## 人工验收清单

### A-1：执行轮次生效，单次调度发送量 = 轮次 × 每轮数量
- 前置条件：ES `orcid_info_candidate` 中存在 ≥ 100 位未联系、有邮箱的专家；至少 1 个启用且未预热压制的发件账号，其 `dailySendLimit` ≥ 100 且 `todaySentCount = 0`；通过 SQL 建一条配置 `INSERT INTO batch_send_task_config (... rounds_per_run, round_size, daily_cap, per_mail_interval_ms, per_round_interval_ms ...) VALUES (... 2, 20, 1000, 0, 1000 ...)`，`auto_enabled = 0`。
- 操作步骤：
  1. 打开「批量邮件任务控制台」→ 定时任务 tab，找到该配置行
  2. 点击「手动」，进入手动执行 tab，确认来源已带入
  3. 点击执行，等待任务结束
  4. 点击该配置行的「日志」，打开本次执行记录
- 预期结果：本次执行成功计数**恰好为 40**；执行日志的结束消息为「**本次调度轮次已用完**」；执行状态为 `COMPLETED`；`task_execution` 中该条记录 `status = 'SUCCESS'`。
- 覆盖：需求描述 Observable outcome 1、2；I-1、I-2

### A-2：轮次未用尽时不误报
- 前置条件：同 A-1，但把配置改为 `rounds_per_run = 5`、`round_size = 20`，且 ES 匹配目标仅 10 位（用 `discipline` 或 `email_domain` 收窄到 10 人以内）。
- 操作步骤：同 A-1 第 1~4 步。
- 预期结果：成功计数 = 10；结束消息**不是**「本次调度轮次已用完」，而是「发送任务已完成」；执行状态 `COMPLETED`。
- 覆盖：I-2 的边界（预算未用尽不得误报）

### A-3【回归】手动单轮按钮仍恰好执行一轮
- 前置条件：配置 `rounds_per_run = 5`、`round_size = 20`；ES 目标 ≥ 100。
- 操作步骤：
  1. 控制台切到旧「批量发送」面板（`INTRODUCTION` legacy 入口）
  2. 点击「手动执行一次」
  3. 等待结束，查看状态与消息
- 预期结果：成功计数**恰好为 20**（1 轮）；结束消息为「手动单轮发送已完成」；流程状态变为 `PAUSED`（或按发起前状态回到 `IDLE`），**不是** `COMPLETED`。
- 覆盖：must-NOT-change 第 3 条；I-4

### A-4【回归】日限额闸门在本计划内未失效
- 前置条件：配置 `rounds_per_run = 10`、`round_size = 20`、`daily_cap = 30`；ES 目标 ≥ 100；当日该配置尚未发送。
- 操作步骤：同 A-1 第 1~4 步；结束后**再次**点击「手动」执行同一配置。
- 预期结果：第一次执行成功计数为 **30**（受 `daily_cap` 截断，非 200），结束消息为「已达到本批次每日上限」；第二次点击执行时页面提示「**今日发送额度已达上限 (30/30)**」且不启动任务。
- 覆盖：must-NOT-change 第 1 条

### A-5【回归】seeded 配置的实际发送量不因迁移改变
- 前置条件：在迁移**前**记录 `SELECT config_name, daily_cap, round_size FROM batch_send_task_config WHERE legacy_code IS NOT NULL;` 的结果（预期 `默认介绍邮件任务` 1000/50、`材料提醒任务` 60/30）。
- 操作步骤：
  1. 应用 V91 迁移（启动应用）
  2. 执行 `SELECT config_name, daily_cap, round_size, rounds_per_run FROM batch_send_task_config;`
- 预期结果：`默认介绍邮件任务` 的 `rounds_per_run = 20`；`材料提醒任务` 的 `rounds_per_run = 2`；两行的 `daily_cap` / `round_size` 与迁移前逐字相同；**无任何行的 `rounds_per_run` 为 0 或 NULL**。
- 覆盖：must-NOT-change 第 5 条；I-5、I-6

### A-6【回归】旧 typed API 更新不会把轮次静默改为 1
- 前置条件：`UPDATE batch_send_task_config SET rounds_per_run = 7 WHERE legacy_code = 'INTRODUCTION';`
- 操作步骤：
  1. 用旧 typed 接口更新该配置的任意其他字段（例如把 `perRoundIntervalMs` 从 60000 改为 90000）：`PUT /api/mail/batch-send/types/INTRODUCTION/config`，请求体沿用旧字段集合（**不含** `roundsPerRun`）
  2. 执行 `SELECT rounds_per_run, per_round_interval_ms FROM batch_send_task_config WHERE legacy_code = 'INTRODUCTION';`
- 预期结果：`per_round_interval_ms` 已变为 `90000`；`rounds_per_run` **仍为 7**，不是 1。
- 覆盖：交互点 X-4

### A-7【回归】跑满轮次后不空等轮间隔
- 前置条件：配置 `rounds_per_run = 1`、`round_size = 5`、`per_round_interval_ms = 120000`（2 分钟）、`per_mail_interval_ms = 0`；ES 目标 ≥ 50。
- 操作步骤：
  1. 记录点击执行的时刻
  2. 手动执行该配置
  3. 记录执行状态变为终态的时刻
- 预期结果：从点击到终态的耗时**小于 30 秒**（不含 2 分钟轮间隔空等）；成功计数 = 5。
- 覆盖：实现方案 B-2 第 2 项

### A-8【回归】改动执行轮次不影响定时调度注册
- 前置条件：一条 `auto_enabled = 1`、`cron = '0 0 * * * ?'` 的配置。
- 操作步骤：
  1. 通过配置编辑器保存一次（**只**改执行轮次相关的 DB 值，或用 API 只改 `roundsPerRun`）
  2. 观察应用日志中是否出现 `Scheduled batch send for configId=..., cron=...`
  3. 等待下一个整点，确认任务按 cron 正常触发
- 预期结果：不出现无谓的重排日志（cron 未变）；下一个整点该配置正常触发，日志出现 `Scheduled batch send trigger firing: configId=...`。
- 覆盖：交互点 X-2；主计划 G-5

### A-9【回归】账号限额与预热压制仍先于轮次预算生效
- 前置条件：仅保留 1 个启用发件账号，设 `dailySendLimit = 10`、`today_sent_count = 0`、`warmup_enabled = 0`、`auto_send_paused = 0`；配置 `rounds_per_run = 5`、`round_size = 20`、`daily_cap = 1000`；ES 目标 ≥ 100。
- 操作步骤：
  1. 手动执行该配置（手动路径 `ignoreWarmup = true`）
  2. 记录成功计数与结束消息
  3. 把该账号改为 `warmup_enabled = 1`、`warmup_started_at = NOW()`、`today_sent_count = 0`，使预热 ramp 限额低于 `dailySendLimit`
  4. 把配置 `auto_enabled = 1`、`cron` 设为 1 分钟后触发，等待**定时**执行（`ignoreWarmup = false`）
- 预期结果：第 1 步成功计数为 **10**（受账号 `dailySendLimit` 截断，非 100），结束消息为「已达到今日发送上限」；第 4 步的定时执行成功计数不超过当前预热 ramp 值，结束消息为「已达到预热上限，今日暂停发送」。两次都**不是**「本次调度轮次已用完」。
- 覆盖：must-NOT-change 第 2 条、第 4 条；需求方决策「限额只用账号的限额」的行为基线

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
- 实现提交信息：`feat(fast-p): implement 01`；只提交授权文件。

