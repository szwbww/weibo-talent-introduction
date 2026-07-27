# 子计划 01：批量发送配置持久化（batch_send_setting + 服务 + API）

> 主计划：`2026-06-18-scheduled-batch-send-00-master.md`。共享不变量/审计见主计划，本文件只补子计划专属内容。

## 需求描述
- 可观察结果：新增一张 key-value 配置表 `batch_send_setting`，并提供 `GET/PUT /api/mail/batch-send/config` 读写定时与定量参数（cron、每日上限、每轮数量、每封间隔、每轮间隔、自动开关、自检 TTL）。同时预留**运行时状态机字段**（status/mode/pauseReason，供子计划 03 持久化 I-9 状态）。
- 不可改变：现有 `eligibility_filter_setting` 及其 API 不动。
- 不做：UI（子计划 04）；调度器与状态机写入逻辑（子计划 03，本计划只建表与读写服务，写入由 03 调用）。

## 关键不变量（引用 + 专属）
- 引用 I-6（定量约束的参数来源即本表）、I-9（运行时状态持久化于本表）。
- Invariant L1-1：配置读取**容错**。任一配置项缺失/非法时，服务返回**带默认值**的 `BatchSendConfig`（默认值见下），绝不抛出导致调度器/编排器启动失败。
  - 适用于：`BatchSendSettingService.getConfig()`。
  - 违反后果：坏配置导致定时流程整体瘫痪。
- Invariant L1-2：写入校验。`PUT` 时校验：`roundSize>=1`、`dailyCap>=roundSize`、`perMailIntervalMs>=0`、`perRoundIntervalMs>=0`、`selfCheckTtlMinutes>=1`、cron 可被 `CronExpression.parse` 解析。校验失败返回 400，不落库。
  - 适用于：`BatchSendConfigController.updateConfig` / service 校验。
  - 违反后果：非法 cron/定量导致运行期异常。

## 现状审计（专属）
- `eligibility_filter_setting`（V26）：`id / setting_key UNIQUE / setting_value / updated_at`，INSERT 种子。本表照搬该结构。
- 最大迁移版本号当前为 V26 → 本表用 **V27**。
- settings 读取模式参考：`config/CandidateFilterProperties`+对应 settings 服务（key-value→typed DTO）。本计划自建独立服务，不复用 eligibility 服务。

## 实现方案

### 任务 1：迁移 V27 建表 + 种子（遵循 L1-1 默认值）
文件：`src/main/resources/db/migration/V27__create_batch_send_setting.sql`
- 建表 `batch_send_setting(id PK AI, setting_key VARCHAR(64) UNIQUE NOT NULL, setting_value VARCHAR(255) NOT NULL, updated_at DATETIME default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP)`。
- INSERT 种子（即默认值）：
  - `batchSend.autoEnabled` = `false`（默认不自动跑，需显式开启）
  - `batchSend.cron` = `0 0 0 * * ?`（每天 00:00）
  - `batchSend.dailyCap` = `1000`
  - `batchSend.roundSize` = `50`
  - `batchSend.perMailIntervalMs` = `1000`
  - `batchSend.perRoundIntervalMs` = `60000`
  - `batchSend.selfCheckTtlMinutes` = `30`
  - 运行时状态（子计划 03 写、本计划仅建键）：`batchSend.runtimeStatus` = `IDLE`、`batchSend.runtimeMode` = `NONE`、`batchSend.pauseReason` = ``（空串）

### 任务 2：领域 + 仓储（key-value）
文件：
- `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendSetting.kt`
  - `@Table("batch_send_setting") data class BatchSendSetting(@Id id, settingKey, settingValue, updatedAt)`（不可变 data class，Spring Data JDBC）。
- `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendSettingRepository.kt`
  - `CrudRepository<BatchSendSetting, Long>` + `findBySettingKey(key): BatchSendSetting?` + `findAll()`。

### 任务 3：配置服务（typed，遵循 L1-1/L1-2）
文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt`
- `getConfig(): BatchSendConfig`：读全表→Map，按 key 取值并 fallback 默认；解析失败回退默认（L1-1）。
- `updateConfig(cmd): BatchSendConfig`：校验（L1-2）→ 对每个 key `findBySettingKey` 存在则 copy(value) 否则 insert → 返回最新 config。
- 运行时状态访问（供子计划 03）：`getRuntimeStatus(): RuntimeState`（status/mode/pauseReason）、`setRuntimeStatus(status, mode, pauseReason)`。本计划提供方法与读写，但**不**在本计划内调用（调用在 03）。
- DTO：在同文件内
  - `data class BatchSendConfig(autoEnabled, cron, dailyCap, roundSize, perMailIntervalMs, perRoundIntervalMs, selfCheckTtlMinutes)`
  - `data class BatchSendRuntimeState(status, mode, pauseReason)`

### 任务 4：REST API
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt`
- `@RestController @RequestMapping("/api/mail/batch-send")`
- `GET /config` → `BatchSendConfig`
- `PUT /config`（`@RequestBody BatchSendConfigUpdateRequest`）→ 校验（L1-2）→ `service.updateConfig` → `BatchSendConfig`
- 请求体 data class 同文件内定义。
- 注意：`/api/mail/batch-send/status`（运行时状态查询，I-5/I-9）放在子计划 03 的控制器，本计划不建 status 端点。

## 变更文件清单（5）
| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V27__create_batch_send_setting.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendSetting.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchSendSettingRepository.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendSettingService.kt` | 新增 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` | 新增 |

测试（可选随任务加，不计入上限）：`BatchSendSettingServiceTest`（默认回退、校验、读写往返）。

## 验收标准
- L1-1：删除/置非法某配置行后 `getConfig()` 返回默认值不抛异常。
- L1-2：`PUT` 非法 cron / `dailyCap<roundSize` / 负间隔 → 400，库内值不变。
- I-6：`GET /config` 返回的定量参数与种子/最近 PUT 一致。
- I-9：`getRuntimeStatus/setRuntimeStatus` 往返正确，重启后从表恢复（值持久）。
- Flyway：V27 在 `FlywayMigrationIntegrationTest` 通过；不修改任何已应用迁移。

## 自检清单
- [x] 新存储 `batch_send_setting` 有不变量（L1-1/L1-2 + 引用 I-6/I-9）。
- [x] 文件数 5 ≤10；单子系统。
- [x] 任务引用不变量编号。
- [x] 每不变量有验收。
- [x] 文件清单无「等」。
