# 批量邮件任务配置 CRUD：fix-1

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-14/batch-send-task-config-crud.md`
- 复验对象：批量邮件任务配置 CRUD（1/3）
- 既有 fix 文档：未找到；知识条目 `K-batch-task-config-implementation-evidence` 显示此前缺失实现已补齐，本轮按现有文件重新审计。

## 约束摘录

- I-1：未删除配置的名称必须唯一；删除为软删除，删除后名称可复用。
- I-4：更新必须完整校验后单次保存，不产生部分写入。
- I-5：新配置必须写规范化表；旧 KV 仅保留兼容接口。
- API：重名返回 409。
- 范围：仅配置 CRUD；不提前接入计划 2 调度、执行或日志。

## 修正记录表

| ID | P1 | 触发频率 | 证据 |
|---|---|---|---|
| P1-1 | 活动配置名称没有数据库唯一约束；两个并发 POST/PUT 可同时通过 service 预查并插入同名未删除行，破坏 I-1。唯一键冲突即使后续补上，当前全局异常处理也会返回 500 而非 409。 | 低频；两个操作者或重试请求同时提交同名配置时触发。 | `V72__create_batch_send_task_config.sql:1-27` 只有 `legacy_code` 唯一键；`BatchSendTaskConfigService.kt:145-148` 为 TOCTOU 预查；`GlobalExceptionHandler.kt:38-40` 将未转换的约束异常映射为 500。 |

## 修复规格

### P1-1：持久化活动名称唯一性，并保持 409 契约

1. 在 V72 尚未部署的前提下，修改 `V72__create_batch_send_task_config.sql`：增加仅对未删除记录生效的可空生成列（值为活动记录的 `config_name`，已删除为 `NULL`）及其唯一键。不得改为对 `config_name` 全表唯一，软删除后名称必须可复用。
2. 在 `BatchSendTaskConfigService.kt` 保留现有预查以改善常规报错；同时把底层唯一键冲突转换为 `ResponseStatusException(CONFLICT)`，保证竞争窗口中的重复请求仍返回 409。不得新增 KV 双写、状态机或重试机制。
3. 在 `BatchSendTaskConfigServiceTest.kt` 覆盖持久化层唯一键冲突映射为 409；保留并验证删除后同名新建的语义。
4. 若 V72 已在任一共享环境执行，不得改写已应用迁移；先由负责人分配不与计划 2 的 V73 冲突的新迁移版本，再以等价生成列/唯一键修复并复验。

预期：任意时刻至多一条 `deleted_at IS NULL` 的同名配置；删除后可创建同名配置；所有重名路径返回 409。

## 当前状态

- 编译：PASS（`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q test`）。默认 JDK 25 与 Kotlin 1.9.25 不兼容，属于环境问题；JDK 11 为项目已配置运行时。
- 测试：PASS — Surefire 1,550 passed、0 failed、0 errors、3 skipped；Node 241 passed、0 failed。目标 `BatchSendTaskConfigServiceTest`：15 passed。
- 迁移集成测试：默认 `migrationIt=false`，未执行 Docker/MySQL profile。

## 合规审计

- I-1 配置实体/软删除/排序：❌ 名称并发唯一性不成立。实体稳定 ID 与软删除见 `BatchSendTaskConfig.kt:8-28`；列表排序及已删除过滤见 `BatchSendTaskConfigRepository.kt:11-28`；缺少活动名称唯一键见 `V72__create_batch_send_task_config.sql:1-27`。
- I-2 模板派生与类型门禁：✅ 创建/更新命令不含 `mailType`，见 `BatchSendTaskConfig.kt:51-80`；服务由启用模板派生类型、null 仅为 INTRODUCTION，见 `BatchSendTaskConfigService.kt:193-218`。
- I-3 范围规范化：✅ 漏斗仅允许 CANDIDATE/APPLICATION，ALL/空为 null；标签 trim、去重、排序，见 `BatchSendTaskConfigService.kt:220-239`。
- I-4 完整校验、单次保存：✅ 数值和 Spring cron 校验见 `BatchSendTaskConfigService.kt:150-190`；create/update 均在校验后一次 `save`，见 `45-69`、`73-98`；启用会重验完整配置，见 `102-123`。
- I-5 新表与旧 KV 兼容：✅ 新表和幂等 legacy seed 见 `V72__create_batch_send_task_config.sql:1-136`；新 CRUD 路由见 `BatchSendConfigController.kt:40-70`；旧 typed 路由保留见 `74-97`。
- 删除/启停/重载事件：✅ 删除同时关闭并写 `deletedAt`，见 `BatchSendTaskConfigService.kt:125-138`；四个变更路径均发布 reload，见 `68`、`97`、`121`、`137`。
- No extras：✅ 业务提交 `274caf86` 只改原计划列出的 6 个文件。

### 语义完整性检查

- Accumulation check：✅ N/A；跨执行自然日累计属于计划 2，未在本计划实现。
- State machine check：✅ N/A；本计划只有配置 CRUD，不定义运行状态机。
- Cross-plan check：✅ 静态合同一致：计划 1 已提供稳定 `id`、软删除、`mailType`、范围与启用查询（`BatchSendTaskConfig.kt:8-28`、`BatchSendTaskConfigRepository.kt:40`），与计划 2 的 `sourceConfigId`/按 configId 调度契约一致；计划 2/3 尚未实施，运行时场景留待其自身复验。
