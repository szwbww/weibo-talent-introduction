# 02：定时配置与手动快照开关贯通

状态：待开发批准。前置：01通过验证。此步独立部署后，已有配置API可设置开关，定时/手动共享验证引擎；界面由03补齐。

## 需求描述

1. 介绍邮件任务配置可保存、读取 `emailVerificationEnabled`；定时与从配置手动执行均复制同一值到执行快照。
2. 手动临时覆盖只影响本次；旧配置、旧调用和历史执行仍可读取。

必须不变：已有自动开关/cron/模板/目标范围/发送轮次与账号名单；旧API修改其它参数不清空验证开关；历史执行独立于配置后续修改和删除。

不做：新调度器、定时与手动暖机策略统一、默认批量开启、密钥管理页面、自动扣费预算、旧快照回填。

## 关键不变量

### Invariant I-1: 一个布尔字段
- Rule: batch_send_task_config 只新增 email_verification_enabled BOOLEAN NOT NULL DEFAULT FALSE；Entity/View/Create均Boolean=false。Update使用Boolean?=null：缺省或null保留现值，显式false关闭。配置→执行快照传递权威bool；旧执行JSON仍由01默认false读取。
- Applies to: 迁移、所有配置映射、手动/定时入口
- Violation consequence: 老配置意外开启或旧客户端擦掉开启状态。
- 来源: original

### Invariant I-2: 所有写路径保值
- Rule: create/update、ConfigFields/NormalizedConfig、三组toFields、toView、updateLegacyConfig、setEnabled、softDelete全部覆盖。updateLegacyConfig显式带existing值；setEnabled/softDelete copy不重置。控制服务旧入口若已有实体按实体取值，纯KV兼容快照无字段则false。
- Applies to: 配置服务、旧控制API、toExecutionSnapshot
- Violation consequence: 从不同入口操作同一任务结果不一致。
- 来源: K-batch-config-legacy-adapter-field-preservation；K-batch-send-legacy-routes-entity-ssot

### Invariant I-3: 直接快照不能绕过类型边界
- Rule: normalizeAndValidate解析模板mailType后，仅INTRODUCTION允许true；validateSnapshotFields对直接手动snapshot同样校验。材料提醒+true返回400；01运行入口守卫继续兜底。人数预估无验证调用、不要求Emailable额度。
- Applies to: 配置保存、启用、手动请求、定时触发、预估
- Violation consequence: 只校验配置却允许手动绕过，或预估收费。
- 来源: K-batch-snapshot-two-write-entrances

### Invariant I-4: 执行快照不可被配置变更追改
- Rule: 启动时固定开关到task_execution.request_payload；手动覆盖不update配置；运行中编辑、停用、软删配置不改本次验证开关与历史显示。调度器保留原调度机制，下一次触发才读取新配置。
- Applies to: BatchSendControlService→TaskExecutionService；BatchSendScheduler
- Violation consequence: 半轮开半轮关，历史日志按当前配置编造。
- 来源: K-batch-task-config-snapshot-log-identity

## 现状审计

- **配置表 schema**：V72建表与模板FK/soft-delete唯一名；后续V74、91、92、93、95、97、98、99、103、108、110、128、135增改字段。V129仅提及表名注释，不是写入。详见 [证据E-2](emailable-evidence.md)。
- **全部业务写入口**：`BatchSendTaskConfigService.kt:66 create`，101 update，137 setEnabled，161 softDelete，187 updateLegacyConfig，583 saveConfig；内部 ConfigFields/NormalizedConfig 与653/675/697三组toFields。旧KV没有这个新字段，不能拿旧请求的缺省覆盖实体。
- **全部业务读方**：同服务 list/get/toView/toLegacyConfig；BatchSendControlService 查实体再 toExecutionSnapshot；BatchSendScheduler:46/80 枚举启用/触发再读。BatchSendTaskConfigRepository 的SELECT*与SpringData映射自动读新列，无需新增SQL文件。模板服务只涉及现有template外键约束。
- **快照写/读**：BatchExecutionModels:290 toExecutionSnapshot；ControlService:57 startScheduled、83 startManual、100 startManualFromConfig、111/142旧入口、246 runManualOnce；310 launchFromSnapshot存JSON；ManualInitialOutreachService:135按mailType派发。BatchSendConfigController:264读取执行requestSnapshot。
- **交互点 X1**：保存→列表回显→toExecutionSnapshot→定时/手动引擎；X2：旧接口写→新接口读；X3：配置被修改/软删→运行快照/历史只读；X4：直接snapshot/配置校验的双入口。
- **知识校正**：K-batch-snapshot-two-write-entrances 的“快照不落库”过时；当前实际持久化，禁止用此旧说法省略历史兼容。来源同时包含 K-batch-config-legacy-adapter-field-preservation。

## 实现方案

### T1 迁移与数据结构（I-1）
文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`、`src/main/resources/db/migration/V139__add_batch_email_verification_enabled.sql`、`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`。V139增加上述一列，不回填开启，不改V138或已应用版本。字段名按SpringData snake_case映射。Create缺省false、Update nullable保值、View始终返回boolean。迁移升级前至少种一条旧任务，升级后0值且其它字段逐一不变；更新latest-target断言为139。

### T2 覆盖配置写读路径（I-1/I-2/I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt`。create和normalize传字段；update先取existing，再把 `cmd.emailVerificationEnabled ?: existing.emailVerificationEnabled` 传入规范化；UpdateCommand.toFields增加existing值参数或在调用处明确合并，不用通用“空值清空”逻辑。三组toFields、NormalizedConfig、save映射、toView、legacy适配全部点名检查。模板解析为MATERIAL_REMINDER且合并后true时拒绝保存，用户必须显式false，不暗中关闭。

### T3 贯通共享快照（I-1/I-3/I-4）
文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`、`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt`。toExecutionSnapshot复制字段；validateSnapshotFields新增类型守卫。手动原始request保持sourceConfigId/sourceUpdatedAt审计身份，开关取request.snapshot，不回写来源配置。startScheduled沿现有路径复制字段；不修改BatchSendScheduler。缺key检查沿01执行入口报告清晰原因，保存未启用配置不强依赖密钥。

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | 实体/View/Create/Update 的新开关 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | 所有读写/规范化/旧接口保留 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 配置→执行快照映射 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 直接手动快照启动校验 |
| 5 | `src/main/resources/db/migration/V139__add_batch_email_verification_enabled.sql` | 现有配置表增加一个布尔列 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` | 读写/旧接口/缺省兼容 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt` | 手动/定时/不合法快照 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 旧配置迁移及 latest 版本断言 |

合计8文件；子系统为任务配置与执行入口；现有共享配置表新增1列。

## 验收标准

- I-1：旧行迁移false；Create省略false；Update省略/null保留true；false显式关闭；JSON/实体round-trip，旧requestPayload读取不报错。
- I-2：逐一测试create/update/setEnabled/softDelete/legacy adapter；比对开关及其它配置字段。E-2中的读写入口无遗漏。
- I-3：配置创建/更新/启用、直接手动分别拒绝材料提醒+true；INTRODUCTION允许；预估provider调用0。
- I-4：捕获定时与从配置手动启动snapshot均true；临时false覆盖不更改实体；启动后修改/删除配置，当前及历史snapshot仍true。

实施后执行：
```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigServiceTest,BatchSendControlServiceTest,BatchSendTaskRuntimeIntegrationTest,BatchSendSchedulerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
```

## 人工验收清单

本子计划通过现有配置REST接口黑盒验收；用隔离环境已有“介绍邮件”任务详情JSON作基准，不需要猜必填字段。

### A-1: 保存→定时/手动
- 前置条件：隔离环境配置列表API `/api/mail/batch-send/configs` 中有INTRODUCTION任务；发信仅到SMTP捕获器。准备01的单个deliverable候选。
- 操作步骤：
  1. GET任务详情，原样保留其它字段，PUT同URL增加emailVerificationEnabled=true。
  2. GET确认；启用任务等待一次原cron触发，读取执行requestPayload。
  3. 从配置手动执行一次，读取对应requestPayload及验证明细。
- 预期结果：两次快照值均true、均有对应验证行；cron/模板/发件账号/轮次原值保留。
- 覆盖：I-1/I-2/I-4；需求1；X1

### A-2: 旧更新保值
- 前置条件：A-1任务开关true，复制其更新请求。
- 操作步骤：
  1. 移除emailVerificationEnabled，仅修改任务名后PUT。
  2. 用旧版配置更新接口修改每封间隔；再GET新接口详情。
  3. PATCH enabled=false，再GET；最后显式PUT emailVerificationEnabled=false。
- 预期结果：前两类更新与停用后验证开关仍true，最后显式关闭后false，其它未改字段保持基准。
- 覆盖：I-1/I-2；必须不变旧API项；X2

### A-3: 临时覆盖与历史身份
- 前置条件：配置保持true；01的独立未发候选；保存执行前配置JSON。
- 操作步骤：
  1. POST manual-executions，sourceConfigId取该任务，snapshot.emailVerificationEnabled=false。
  2. 执行后GET配置、执行详情。
  3. 再开启验证启动一次，运行中更名后软删来源任务，查看这次执行详情。
- 预期结果：临时false调用Emailable为0且配置仍true；更名/删除不改变第二次快照true；历史仍可按执行ID访问。
- 覆盖：I-1/I-4；需求2；X3

### A-4: 旧配置与双入口校验
- 前置条件：隔离库由137升级到139，升级前保存旧任务JSON；另有材料提醒模板。
- 操作步骤：
  1. GET旧任务确认开关默认false。
  2. 创建/更新材料提醒任务传true；向manual-executions直接提交材料提醒+true。
  3. 发送介绍邮件人数预估请求，开关true，观察HTTP捕获。
- 预期结果：旧任务其它值不变；两种非法请求均400且无SMTP/验证调用；预估请求不消耗验证调用。
- 覆盖：I-1/I-3；需求2；X4
