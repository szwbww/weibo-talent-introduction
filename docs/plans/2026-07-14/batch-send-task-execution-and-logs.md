# 批量邮件配置执行与配置级日志

> 顺序：2/3。依赖 `batch-send-task-config-crud.md`；完成后执行 `batch-send-task-console-frontend.md`。

## 需求描述

- 每份启用配置独立调度；列表行“手动”可携带该配置快照发起一次执行。
- 手动也允许不选择配置，提交一份独立参数快照。
- 收件范围按配置执行，漏斗不再硬编码，并支持标签筛选。
- 日志按配置查询；每次执行展示目标、成功、失败、跳过、剩余、耗时、状态，以及失败/跳过原因和数量。
- 保留现有单线程执行器和全局互斥，不扩大并发发送能力。

## 关键不变量

### I-1：每次执行只消费启动快照

- 自动执行在触发时读取一次配置；手动执行使用请求中的完整快照。
- 运行开始后修改/删除配置不改变当前批次；请求与执行记录保存 `sourceConfigId`、`sourceUpdatedAt` 和完整参数。
- 选择配置后的手动修改只影响本次，不回写定时配置。
- 未选择配置时 `sourceConfigId=null`；不做“与配置差异”校验，但仍执行所有字段、模板、cron 以外运行参数校验。

### I-2：调度按 configId 隔离，执行仍全局互斥

- `BatchSendScheduler` future map 从 `BatchSendType` 改为 `Long configId`。
- 创建、删除、cron 或 enabled 变化均触发重新加载；旧 future 使用 `cancel(false)`，不打断运行中的发送。
- 所有自动/手动执行继续共用 `manualOutreachExecutor` 和 `TaskProgressStore.tryStartWithToken()`；同时只能有一个外联批次。
- 冲突时返回明确“已有批量任务执行中”，不能重复创建 RUNNING 记录。

### I-3：收件筛选双路径一致

- ES 新目标和 MySQL/历史失败重试路径都应用同一份范围：漏斗、标签、邮箱服务商、学科。
- `funnelLevel=null` 等价于 `CANDIDATE OR APPLICATION`，不包含 `RAW`。
- 标签多选在单字段内 OR，和其他筛选维度 AND。
- INTRODUCTION 沿用介绍信 compose/dedup；MATERIAL_REMINDER 沿用已有联系人和材料提醒 compose 规则。

### I-4：模板在启动边界重新校验

- 创建/编辑时通过不代表启动时仍合法；自动和手动启动前重新读取模板并校验存在、启用、`mailType` 一致。
- 校验失败时不提交执行器、不扫描目标、不发送邮件；API 返回明确错误。
- 一旦启动，同一批始终使用快照中的同一个模板 ID。

### I-5：自然日限额按配置跨执行累计

- `sourceConfigId!=null` 时，已发送量取该配置当天所有执行记录的 `SUM(success_count)`；自动和“来源于配置”的手动共同占用上限。
- 独立手动执行 `sourceConfigId=null`，仅以本次 `dailyCap` 为上限，不与任何配置共享。
- 调度/手动多次触发不能绕过上限；取消和失败不回退已成功数量。
- 每次发送成功后立即持久化当前 execution 的累计 `success_count`，不能只在任务结束时落库；进程崩溃/重启后已发送数量仍计入当天上限。

### I-6：数量守恒、原因结构化

- 每次执行固定满足：`target = success + failure + skipped + remaining`。
- 结束后通常 `remaining=0`；取消、互斥终止或中途异常允许 `remaining>0`。
- `failureReasons`、`skippedReasons` 为稳定原因码到 `{label,count}` 的映射；同类原因聚合，不以错误字符串作为 key。
- 至少覆盖：发送异常、模板渲染失败、邮箱账号不可用、退订/抑制、无联系人账号、去重、超日限额、被取消。
- `failure=sum(failureReasons.count)`、`skipped=sum(skippedReasons.count)`；详情可额外保留最多 20 条样例错误，不影响聚合数。

### I-7：日志归属不可漂移

- `task_execution.batch_config_id` 是执行时来源配置 ID；配置软删除后仍可读取日志。
- 配置行“日志”只返回该 `batch_config_id` 的记录；独立手动记录不出现在任何配置行日志中，仍保留在通用任务记录。
- 状态值统一为 `RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELLED`。

## 现状审计

### 调度与执行

- `BatchSendScheduler` 目前固定调度两个 `BatchSendType`，不能支持 N 份配置。
- `BatchSendControlService` 启动时按类型重新读 KV；必须改为接收配置快照。
- `ManualInitialOutreachService` 当前分别硬编码 `CANDIDATE` 与 `APPLICATION + 承诺回复材料`，且部分 TTL 重载只覆盖材料提醒。
- 当前全局互斥和共享执行器是安全边界，必须保留。

### 执行记录写路径

- `task_execution` 唯一生产写入口为 `TaskExecutionService.runAndRecord*()`；新增归属字段必须在两个重载统一写入。
- 读取入口为 `TaskExecutionService`、`TaskProgressController`；新配置日志 API可通过 service/repository读取。
- 当前 `request_payload`、`result_summary` 为 JSON TEXT，可直接保存配置快照和结构化统计，无需为每个统计项加列。

### 进度日志写路径

- `task_progress_log` 唯一写入口是 `TaskProgressStore.persistProgressLog()`；`details_json`、`errors_json` 已可承载结构化信息。
- 本计划不改表结构；最终 `result_summary.outcome` 作为汇总权威，progress log 用于批次时间线。

### 当前统计缺口

- `ManualOutreachResult` 有 `total/sent/failed/skippedNoAccount/remaining`，但原因未结构化、部分跳过混入 rejected。
- 当前页面记录只按 task type 查，不知道配置 ID；失败仅有错误样例，不能稳定聚合原因。
- 因此新增一列 `batch_config_id`，其余统计写现有 JSON，避免重复事实源。

## 实现方案

### Phase 1：执行记录归属

#### Task 1.1：V73 增加配置外键

文件：`src/main/resources/db/migration/V73__add_batch_config_id_to_task_execution.sql`

- 新增 `task_execution.batch_config_id BIGINT NULL`。
- 建索引 `(batch_config_id, started_at)`。
- 外键引用 `batch_send_task_config(id)`，不级联删除。
- 旧记录保持 null。

#### Task 1.2：贯通 TaskExecution 写入

文件：

- `src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskExecution.kt`
- `src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt`

变更：

- domain 增加 `batchConfigId: Long? = null`，默认值保证其他调用方源码兼容。
- 两个 `runAndRecord*()` 增加可选 `batchConfigId`，创建 RUNNING 记录时写入。
- repository 增加按 configId 分页/限量倒序查询、`SUM(success_count)` 的当日查询，以及按 executionId 原子更新累计成功/失败数的方法。
- `TaskExecutionService` 暴露进度计数更新；发送成功后立即更新累计值，最终完成时再以结果汇总校正。
- 统计日界线由服务层按 `Asia/Shanghai` 生成 `[dayStart,nextDayStart)`，避免数据库时区含糊。

### Phase 2：调度与启动 API

#### Task 2.1：Scheduler 改为配置驱动

文件：`src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt`

- 启动时加载全部未删除且 enabled 的配置，按 id 创建 cron future。
- 收到现有配置变更事件时执行 diff：取消消失/关闭/cron 变化的 future，新增需要的 future。
- 回调仅传 `configId`；执行前再获取一次快照，已关闭/删除则退出。
- future 取消统一 `cancel(false)`；异常只记录该配置，不影响其他 future。

#### Task 2.2：控制服务接收快照

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`

- 新增 `startScheduled(configId)`、`startManual(ManualBatchExecutionRequest)`。
- 请求包含 `sourceConfigId?`、`sourceUpdatedAt?` 和完整 `BatchExecutionSnapshot`。
- 选配置的手动请求不要求 `sourceUpdatedAt` 与当前值一致；它是审计基线，不是乐观锁。
- 启动前完成字段、模板、类型校验和自然日剩余额度计算，再尝试全局互斥。
- 将 configId 和完整请求传入 `TaskExecutionService`；运行中不再调用旧 `getConfig(sendType)`。
- 返回 `202 + executionId`；校验失败 422；全局忙 409。

#### Task 2.3：扩展执行与日志 API

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt`

新增：

| 方法 | 路径 | 行为 |
|---|---|---|
| POST | `/api/mail/batch-send/configs/{id}/execute` | 使用当前配置快照手动执行 |
| POST | `/api/mail/batch-send/manual-executions` | 可带/不带 `sourceConfigId` 的完整快照执行 |
| GET | `/api/mail/batch-send/configs/{id}/executions?limit=50` | 配置级执行摘要 |
| GET | `/api/mail/batch-send/configs/{id}/executions/{executionId}` | 执行详情、原因聚合、批次日志 |

- 详情必须同时校验 path configId 与 execution.batchConfigId，防越权串读。
- 列表摘要返回：executionId、triggerType、status、target/success/failure/skipped/remaining、startedAt、finishedAt、durationMs。
- 详情额外返回 request snapshot、failureReasons、skippedReasons、errorSamples、progress batches。
- 完成切换后，旧 `/types/{sendType}` 路由仅作为兼容适配器映射到对应 `legacy_code` 配置，不再形成 KV 与实体双写事实源。

### Phase 3：目标筛选与统计

#### Task 3.1：统一 ManualInitialOutreachService 输入

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

- 将入口统一为 `run(snapshot, alreadySentToday, progressCallback)`；邮件类型仅选择 compose 策略，不选择硬编码范围。
- 构造一个不可变 `RecipientScope`，ES 搜索和 MySQL 重试候选都调用同一 matcher/query builder。
- 漏斗为空展开为两层；标签使用 terms/集合交集；provider/discipline 保持现有规范化。
- 自查 TTL 始终从 snapshot 传入，禁止回读某一类型 KV。
- 引入稳定原因码累加器；每个候选最终只能落入 success/failure/skipped/remaining 一类。
- 每封发送成功后通过 execution progress 回调持久化累计 `success_count`，确保异常重启仍能恢复自然日消耗。
- 返回 `ManualOutreachResult` 内嵌 `OutcomeBreakdown`，并实现 `TaskExecutionSummaryProvider`。
- progress details 写当前计数和原因聚合；样例错误仍上限 20。

### Phase 4：自动化测试

#### Task 4.1：运行与日志集成测试

文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt`

- 多配置 scheduler 注册/修改/关闭/删除，验证按 id reschedule 和 `cancel(false)`。
- 自动、选配置手动、独立手动三种 request payload 与 `batch_config_id`。
- 运行中修改配置不改变已启动 snapshot。
- ES 和 retry 两路径对漏斗/标签/provider/discipline结果一致。
- 模板在启动前被禁用时 422 且零发送。
- 同配置多次执行日限额累计；独立手动不共享。
- 各原因计数和守恒公式；取消时 remaining 正确。
- 配置日志只返回对应记录；软删除配置后日志仍可查询。

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V73__add_batch_config_id_to_task_execution.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskExecution.kt` | 修改 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt` | 修改 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt` | 修改 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/task/service/BatchSendScheduler.kt` | 修改 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 修改 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 修改 |
| 8 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` | 修改 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | 新增 |

共 9 文件，2 个子系统（批量发送运行时、任务执行日志），满足 create-p 限制。

## 验收标准

- N 个启用配置产生 N 个按 configId 管理的调度项；关闭/删除只取消对应项。
- 自动和手动均使用启动快照；执行中编辑配置不改变本次收件范围、模板、限额。
- 漏斗为空覆盖 CANDIDATE+APPLICATION 且排除 RAW；标签/provider/discipline在新目标和重试目标一致。
- 选择配置的手动执行计入该配置当日上限；独立手动不串账。
- 每条记录均满足数量守恒；失败和跳过原因数量之和与总数一致。
- 配置日志不会出现其他配置或独立手动记录；软删除后历史日志仍可查。
- 模板失效、全局忙、字段非法均在发送前失败，零邮件副作用。

## 人工验收清单

- [ ] 启用两份不同 cron 配置，分别观察下一次触发；关闭其中一份不影响另一份。
- [ ] 用配置 A 手动执行，再编辑 A，已启动执行仍显示原快照。
- [ ] 不选漏斗，确认目标来自 CANDIDATE 和 APPLICATION，且无 RAW。
- [ ] 选择两个标签，确认命中任一标签即可，其他维度仍同时生效。
- [ ] 连续执行配置 A，成功总数达到日限额后不再新增发送。
- [ ] 打开 A 的日志，不出现 B 或独立手动记录。
- [ ] 检查成功、部分成功、失败、取消四种记录的数量均守恒。
- [ ] 失败详情能按原因展示数量；跳过详情至少能区分退订、无账号、去重、超限。
