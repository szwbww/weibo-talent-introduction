# 批量邮件配置执行与配置级日志：fix-1

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-14/batch-send-task-execution-and-logs.md`
- 复验对象：批量邮件配置执行与配置级日志（2/3）
- 既有 fix 文档：未找到。

## 约束摘录

- I-1：执行只消费启动快照，日志保存 `sourceConfigId`、`sourceUpdatedAt` 与完整参数。
- I-2：future 按 `configId` 管理；自动和手动继续共用全局互斥与单线程执行器。
- I-3：ES 新目标与 MySQL 重试路径使用同一收件范围。
- I-4：自动和手动启动前重新校验模板；失败时不得提交执行器。
- I-5：配置日限额跨自动和来源于配置的手动执行累积，成功后立即持久化。
- I-6：数量守恒，失败/跳过原因以稳定原因码聚合。
- I-7：执行记录以 `batch_config_id` 归属；配置级日志不得串读。
- 兼容约束：旧 `/types/{sendType}` 路由仅映射 `legacy_code` 配置，不能继续形成 KV 与实体双写事实源。

## 修正记录表

| ID | P1 | 触发频率 | 证据 |
|---|---|---|---|
| P1-1 | 旧配置兼容路由仍直接读写 `BatchSendSettingService` 的 KV；配置化 scheduler、启动快照却读取 `batch_send_task_config` 实体。经旧 `/config` 或 `/types/{sendType}/config` 保存的 cron、限额、模板和范围不会更新实体，随后定时/手动执行继续使用旧实体值，形成两个可变事实源，违反兼容约束。 | 高：现有旧页面、脚本或集成方仍调用任一旧配置 GET/PUT 时触发；每次写后下一次执行均可能使用过期配置。 | `BatchSendConfigController.kt:118-140` 直接调用 `batchSendSettingService.getConfig/updateConfig`；`BatchSendScheduler.kt:50`、`BatchSendControlService.kt:55-65` 只读取实体；原计划“完成切换后”要求兼容路由映射 `legacy_code` 配置。 |

## 修复规格

### P1-1：兼容路由改为实体配置适配器

1. 修改 `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt`：`GET/PUT /config` 与 `GET/PUT /types/{sendType}/config` 均定位对应 `legacy_code` 的未删除 `batch_send_task_config`，并把旧 `BatchSendConfig` 请求/响应转换为实体配置的读写契约；不得再调用 `BatchSendSettingService.getConfig/updateConfig` 作为这些路由的读写源。
2. 复用 `BatchSendTaskConfigService` 的完整校验、单次保存和 reload 事件。若现有公开接口不能完成适配，才在 `BatchSendTaskConfigService` 增加窄的 `legacy_code` 读取/更新入口；不得恢复 KV 双写、不得新建状态机或迁移。
3. 保留旧路由的 URL、`BatchSendConfig` 字段形状、INTRODUCTION 的 `/config` 默认含义及模板类型校验；当对应 legacy 实体不存在/已删除时返回明确 404/422，不能静默退回 KV。
4. 在 `src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigControllerTest.kt`（或现有等价 MVC 测试）覆盖 INTRODUCTION `/config` 与两种 typed 路由：写入后实体行更新、后续配置化启动读取更新值、KV service 不被调用。触发频率高，测试必须直接断言该边界。

预期：配置化实体表成为唯一运行时配置事实源；旧客户端继续使用原 URL，但其读写立即影响 configId 调度与执行快照。

## 当前状态

- 编译：PASS（`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 完成；Kotlin 编译及 Surefire 已执行）。
- 测试：PASS — Surefire 1,577 passed、0 failed、0 errors、0 skipped；`BatchSendTaskRuntimeIntegrationTest`：21 passed。
- 迁移集成测试：默认 `migrationIt=false`，未执行 Docker/MySQL profile。

## 合规审计

- I-1 启动快照：✅ `BatchSendControlService.kt:55-82` 与 `86-109` 在提交执行器前构造/传递 snapshot；`TaskExecutionService.kt:84-96` 保存完整 request 与 `batchConfigId`；`ManualInitialOutreachService.kt:127-138` 只按传入 snapshot 分流。
- I-2 configId 调度与全局互斥：✅ `BatchSendScheduler.kt:34-76` 用 `Long` map、差量 `cancel(false)`；`BatchSendControlService.kt:325-350` 仍用 `tryStartWithToken()` 与 `manualOutreachExecutor`；`BatchSendScheduler.kt:82-88` 触发前重新确认配置仍启用。
- I-3 双路径筛选：✅ `BatchExecutionModels.kt:67-80` 将空漏斗展开为 CANDIDATE+APPLICATION；`ManualInitialOutreachService.kt:866-896` 的重试联系人按同一 `RecipientScope` 查询/匹配；`1147-1159` 的 ES filter 复用 tags/provider/discipline。
- I-4 启动模板校验：✅ `BatchSendControlService.kt:63-64`、`87-88` 在 launch 前校验；`426-447` 拒绝不存在、禁用和类型不匹配模板；测试 `BatchSendTaskRuntimeIntegrationTest.kt:275-292` 断言 422 且未提交执行器。
- I-5 跨执行日限额：✅ `TaskExecutionRepository.kt:46-56` 按 config/day 聚合；`TaskExecutionService.kt:46-54` 以 Asia/Shanghai 日界线调用并提供进度更新；`BatchSendControlService.kt:65-68`、`90-98` 注入累计量；`ManualInitialOutreachService.kt:631-635` 每次成功立即持久化。
- I-6 守恒与原因：✅ `OutcomeAccumulator` 的分类/聚合见 `BatchExecutionModels.kt:121-168`；`ManualInitialOutreachService.kt:1202-1223` 终止时归因余量并构造结果；`1291-1310` 将执行状态归一为 `RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELLED` 集合中的终态。
- I-7 归属与日志隔离：✅ `V73__add_batch_config_id_to_task_execution.sql:1-9` 增加 nullable FK/索引；`TaskExecutionService.kt:76-96` 写入归属；`BatchSendConfigController.kt:89-113` 按 config 查询且详情核对 path id。
- 兼容路由单一事实源：❌ P1-1。`BatchSendConfigController.kt:118-140` 仍直连 KV，和实体读取路径不一致。
- Deleted code：❌ 旧 KV 配置写入口仍被兼容配置路由调用（P1-1）。
- No extras：❌ 工作区还新增 `campaign/domain/BatchExecutionModels.kt`、修改 `BatchSendTaskConfigRepository.kt` 及若干既有测试构造器；前两项是本实现 DTO/查询所必需，测试改动为构造器签名连带调整，均记为范围观察，不单独升格 P1。

### 语义完整性检查

- Accumulation check：✅ `dailySentTotal` 以 `alreadySentToday` 初始化（`ManualInitialOutreachService.kt:193`、`463`），而该值来自持久化 `SUM(success_count)`；不是跨调用重置为 0。
- State machine check：✅ N/A。本计划仅规定 execution 终态枚举，不引入可恢复的运行状态机；旧 typed runtime 状态机不属于此计划的新行为。
- Cross-plan check：❌ 配置 CRUD（计划 1）到执行计划（计划 2）的 configId/软删除/快照合同成立（`BatchSendTaskConfigService.kt:105-142`、`BatchSendControlService.kt:53-82`），但旧兼容路由绕开该合同并继续写 KV（P1-1）。计划 3 尚未按既定顺序实施；其 `manual-executions` payload 名称与本计划 DTO 一致，留待计划 3 复验。
