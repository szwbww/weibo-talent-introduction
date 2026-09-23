# 批量邮件发件账号筛选与已绑定专家跳过：后端计划（1/2）

> create-p 子计划；受 [MAIN 总计划](00-batch-sender-filter-main.md) 的顺序、接口与发布门槛约束。只创建计划，不授权执行。执行前读 [前端计划](02-batch-sender-filter-frontend.md) 和共享收件箱 [配置计划](01-shared-inbox-configuration.md)、[路由计划](02-shared-inbox-routing.md)。本计划限 10 个代码/测试文件。迁移号以开跑时实际最高版本为准：基线最高为 V133，本计划使用下一个空号 V134（人工批准的改写见 `docs/plans/fast/2026-09-23-batch-sender-filter-main/ledger.md` 的 `## Amendments` A2）。实施前重新核对最高版本与工作树，若版本冲突先修订文件名和本文，不能抢号。

**目标**：批量任务可保存多选逻辑发件账号；执行时仅从这些账号中选号。INTRODUCTION 与 MATERIAL_REMINDER 都跳过已有 `bound_sender_account_code` 的专家，不因筛选结果改绑或从其他账号发送。

**技术栈**：Kotlin、Spring Data JDBC、MySQL/Flyway、Mockito/JUnit。

## 需求描述

- 可观察结果：选 LuKai 时批量任务的 SMTP 发件账号只会是 LuKai；选 LuKai 和另一个账号时只会是这两者；不选账号沿用存量任务的“全部可发送账号”。
- 可观察结果：专家只要已绑定任意逻辑发件账号，本次批量首发和材料提醒都不发送。材料提醒当前只面向已有联系人，因此若目标都已绑定，本次预估与执行目标可以是 0。
- 必须保持：发件账号的 `enabled`、暂停、预热、额度、自检门禁；已有独立账号与共享收件箱的 SMTP 身份；单封人工发信、收信、回信和退信的现有路径；配置编辑期间运行任务仍用启动快照。
- 范围外：更改专家绑定、自动回信与 To/Cc 路由、物理 IMAP owner、历史邮件/联系人回填、让材料提醒重发给已绑定专家、重新设计 ES 收件人数估算。

## 关键不变量

### I-1：筛选身份与空值
- 规则：新字段 `sender_account_codes_json` 持久化逻辑 `mail_sender_account.account_code` 的 JSON 数组；`[]` 表示不限制（旧配置和未传字段同义）。非空数组只允许列出的账号；绝不按 `inbound_mailbox_code` 合并/扩展兄弟别名。解析损坏 JSON 必须拒绝启动，不得降级成 `[]`。`BatchExecutionSnapshot.senderAccountCodes` 是本次执行唯一范围快照。
- 适用写路径：V134、配置 create/update/旧 typed 更新、手动快照；适用读路径：toView、toExecutionSnapshot、两发送循环。
- 违反后果：旧任务停发或筛选被意外放宽、共享收件箱别名串号。
- 来源：`BatchSendTaskConfig.kt:7-111`、`BatchExecutionModels.kt:279-335`；K-batch-task-config-snapshot-log-identity、K-batch-config-legacy-adapter-field-preservation。

### I-2：只有选中账号能外发
- 规则：配置和手动快照中的非空 code 列表先 trim/去重，并验证每个 code 对应存在且不是 `SIMULATOR_NOOP`；运行时每轮 `listSendableAccounts`、自检、额度合计、`SenderAccountAssignmentService.selectAccount` 候选都限制在快照集合内。账号后来禁用/暂停/额度不足时停发或跳过，绝不回退未选中账号。空列表沿用旧选号逻辑。
- 适用写路径：配置保存、手动启动；适用发送路径：INTRODUCTION、MATERIAL_REMINDER 两循环和共同选号服务。
- 违反后果：筛选看似生效但实际错发。
- 来源：`ManualInitialOutreachService.kt:167-335,499-730,907-995`、`SenderAccountAssignmentService.kt:16-38`；K-sender-account-enabled-scope。

### I-3：已绑定即跳过
- 规则：任何 `expert_contact.bound_sender_account_code != NULL` 的目标均不得在本次批量任务中发送、重选或改绑；与绑定值是否在选中集合无关。INTRODUCTION 的 MySQL NEW 重试和 ES 新目标都检查；MATERIAL_REMINDER 的目标构造和发送前都检查。空白字符串不作为合法绑定数据（V85 注释明确禁止），如现场脏值另行审计，不在此计划修复。
- 适用路径：`buildRetryableTargets`、ES 目标发送前、`buildMaterialReminderSnapshotFromScope`、材料发送前；绑定写路径保持原样。
- 违反后果：同一专家跨账号重复发送或材料提醒越过规则。
- 来源：`V85__add_expert_contact_sender_binding.sql:1-11`、`ExpertContactRepository.kt:9-19,68-100`、`ManualInitialOutreachService.kt:634-686,1201-1258`；K-batch-send-filter-retry-parity。

### I-4：预估与执行使用同一目标过滤
- 规则：INTRODUCTION 的 NEW 重试、MATERIAL_REMINDER 的既有联系人目标，在现有共用构造函数中过滤绑定，以使 `countBySnapshot` 和执行同源；INTRODUCTION 的 ES `countExperts` 仍是候选估算，ES 页中的已绑定专家在发送前以 `BOUND_SENDER_ALREADY_SET` 跳过并写入跳过数。不能声称 ES 预估是扣除 MySQL 绑定后的精确人数。
- 适用路径：`countBySnapshot`、两目标构造、`OutcomeAccumulator`。
- 违反后果：页面预估与可发送人数的差异无解释，或绑定专家进入材料任务。
- 来源：`ManualInitialOutreachService.kt:451-469,1002-1038,1201-1258,1260-1266`；K-recipient-count-preview-parity。

### I-5：并行计划的账号语义
- 规则：共享收件箱的 `mailbox_owner_code` / `inbound_mailbox_code` 仅用于 IMAP 抓取和物理去重；本计划所有发件候选、绑定、日志均使用逻辑 `accountCode`。LuKai_QF 即使与 LuKai 共享 IMAP，禁用时也不是可发送候选。
- 适用路径：配置校验、运行时选号、发送统计。
- 违反后果：筛选 LuKai 意外从 QF 发件，或绑定判断按 owner 误放行。
- 来源：`docs/plans/2026-09-23/01-shared-inbox-configuration.md:I-1/I-3`、`02-shared-inbox-routing.md:I-1/I-2`；`MailSenderAccountService.kt:40-56`。

## 现状审计

### `batch_send_task_config`
- Schema：`V72__create_batch_send_task_config.sql:1-31` 建表；V91/V93/V97/V98/V99/V128 后的 `BatchSendTaskConfig.kt:7-34` 映射当前 JSON 范围列和 `researchDirectionFilter`，无发件账号筛选列。V97 证明 MySQL TEXT 字段用迁移写入 `[]`，不依赖 TEXT DEFAULT。
- 写路径：`BatchSendTaskConfigService.create/update/setEnabled/softDelete`（`:59-150`）；`updateLegacyConfig`（`:170-202`）把旧 typed 请求全量映射到新实体，新增字段必须从 existing 显式保留；V72 种子、既有 V91/V93/V97/V98/V99/V128 已应用，不重跑。`BatchSendTaskConfigRepository` 只声明 CRUD/SELECT，未见自定义 UPDATE。
- 读路径：`BatchSendTaskConfigService.list/get/toView`（`:39-56,474-505`）；`BatchSendControlService.startScheduled/startManualFromConfig` 取 row 后 `toExecutionSnapshot`；旧 typed `getLegacyConfig`；调度器查询 `autoEnabled` 配置。字段跨配置写入→启动快照→发送。

### `BatchExecutionSnapshot` 与账号池
- 快照映射：`BatchExecutionModels.kt:12-36,279-335`；`BatchSendConfigController.kt:98-106` 的预估直接接收快照；`BatchSendControlService.validateSnapshotFields:418-441` 是手动/配置启动门禁，两入口不互经配置服务（来源：K-batch-snapshot-two-write-entrances）。本计划不新增 task_execution 列。
- 账号 Schema：`V1__create_business_tables.sql:1-25` 的 `account_code` 唯一，`MailSenderAccount.kt:7-39` 有 `enabled/autoSendPaused`；`MailSenderAccountService.listAccounts/listEnabledAccounts/listSendableAccounts` 读取逻辑账号，`SenderAccountAssignmentService.selectAccount:16-38` 当前从所有 enabled 账号选号。共享收件箱计划 01 的 `inbound_mailbox_code` 不改变 SMTP/发件门禁。
- 账号写路径：`MailSenderAccountService.createAccount/updateAccount/setEnabled/deleteAccount`；`MailSenderAccountRepository` 的发件计数/暂停更新。本计划不改账号表或这些写路径。筛选配置只保存 code，不复制账号状态；运行时重新读取状态。

### `expert_contact` 与目标
- Schema：`V1__create_business_tables.sql:79-95` 以 `(campaign_id,orcid_id)` 唯一；`V85__add_expert_contact_sender_binding.sql:1-11` 增 nullable 绑定 code，NULL=未绑定。一个 ORCID 可在不同 campaign 有多行，因此查“专家已绑定”不能只看当前 campaign 行。
- 绑定写路径：`InitialOutreachService` 与 `ManualInitialOutreachService` 建行时写绑定；`SenderAccountBindingService.bindIfAbsent/rebind/migrateAccount` 经 `ExpertContactRepository.updateBindingById/rebindSenderAccountById/migrateBindingByAccount` 写绑定；V85 只执行过一次历史回填。本计划不改这些写路径。
- 读路径：`ManualInitialOutreachService.buildRetryableTargets:1002-1038` 读 NEW 联系人，目前不排绑定；`buildMaterialReminderSnapshotFromScope:1201-1258` 批量读 `findByOrcidIdIn`，目前不排绑定；两发送循环目前优先使用绑定账号。`ExpertContactRepository.findByOrcidIdIn` 可核查同 ORCID 的所有行，无须新增表/索引/Repository 方法。
- 交互点：绑定写入→重试/材料/ES 执行读；账号 enabled/paused 写入→每轮只在选中集合内读取。

## 实现方案

### 阶段 1：字段与配置链（I-1、I-2、I-5）
- 文件：`src/main/resources/db/migration/V134__add_batch_sender_account_codes.sql`、`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`、`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`、`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`。
- 测试先行：新建/编辑/列表/详情/旧 typed 更新均往返 `["LuKai"]`；旧 payload、旧行回显 `[]`；模拟坏 JSON 时 `toExecutionSnapshot` 抛错，不能变为 unrestricted；LuKai 与 LuKai_QF 同物理 owner 仍保持两个不同 code。
- V134 用 `ADD COLUMN sender_account_codes_json TEXT NULL` → `UPDATE ... SET '[]' WHERE ... IS NULL` → `MODIFY ... TEXT NOT NULL`；实体/命令/view/snapshot 字段默认 `emptyList()` 或 `"[]"`。配置服务统一 trim/去重/验证账号 code；`updateLegacyConfig` 显式传 `parseSenderAccountCodes(existing.senderAccountCodesJson)`；`toView` 和 `toExecutionSnapshot` 传真实值。坏 JSON 拒绝读取/启动，不用其他旧范围字段的“错误按不限”模式。Flyway 测试中普通最新版本断言现仍钉 `131`（`:62,136,178` 等），实施前按工作树实际最高版本重查并改到 `134`；定点 target 版本断言（如 `:435` 的 `130`）保持原值。增加旧配置 `[]` 与新增列存在的断言。

### 阶段 2：启动校验与限定选号（I-1、I-2、I-5）
- 文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`、`src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentService.kt`。
- 手动快照在 `validateSnapshotFields` 校验 code 集合（非空/去重后每项在 `mailSenderAccountService.listAccounts()` 且非模拟器）；配置校验与此同口径。该服务构造器已有 `mailSenderAccountService`（`:30-39`），不新增依赖。选中账号后被停用仍允许保存配置，但运行时不发送。`selectAccount` 新增末位可选 `allowedAccountCodes: Set<String> = emptySet()`，在现有 enabled/预热/额度/暂停谓词之上增加集合限制；未选时旧四参调用保持原行为。手动非法 code 的 422 由 A-6 黑盒验证，同时运行未修改的现有 `BatchSendControlServiceTest` 回归。

### 阶段 3：两发送循环及绑定跳过（I-2～I-5）
- 文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`、`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`、`src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`。
- `runRoundGate` 两重载、`classifyNoSendableOutcome`、`hasWarmupLimitedAccounts` 和限额提示只看快照选中集合；自检也只查该集合。调用 `selectAccount` 时非空集合传第五参，空集合保留旧四参路径。每轮 quota 只累计选中账号容量。
- `buildRetryableTargets` 对这批 NEW contact 的 ORCID 用现有 `findByOrcidIdIn` 批量读取，任一 campaign 行有绑定即排除；`buildMaterialReminderSnapshotFromScope` 利用已读的 `findByOrcidIdIn` 结果按 ORCID 分组，任一行有绑定即排除。INTRODUCTION 的 ES 页和 MATERIAL_REMINDER 的实际发送前都用 `findByOrcidIdIn(listOf(normOrcid))` 重查任一绑定。跳过发生在选号、bindIfAbsent、建新 contact 与 SMTP 之前；新增 `BatchOutcomeReasonCodes.BOUND_SENDER_ALREADY_SET` 及“专家已绑定发件账号”标签。跳过推进 processed/roundProcessed/roundRejected，**不增加 roundSent**，让后续未绑定目标继续占用本轮发件名额；目标迭代器或列表索引照常前进，避免死循环。不把它记录为 SMTP 失败。
- 测试覆盖两个类型、NEW 重试/ES 页、绑定账号在选中集合内外、一个 ORCID 不同 campaign 只一行已绑定、禁用/暂停/无额度的选中账号、旧空集合、材料目标全绑定为 0。发送测试同时断言所有实际 `senderAccountCode` 属于快照集合，未选中账号零 SMTP；预估路径零绑定写入/零 SMTP。

## 变更文件清单

| # | 文件 | 用途 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V134__add_batch_sender_account_codes.sql` | JSON 列及旧行 `[]` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | 配置/命令/view 字段 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 快照映射、跳过原因 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | 保存/回显/旧 API 保留 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 手动启动校验 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountAssignmentService.kt` | 限定选号候选 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 目标排除、两循环门禁 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` | 配置往返/旧 API |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | DDL、旧行及最新版本断言 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 两类型实际发送、预估 |

## 验收标准

- I-1：V134 迁移后旧配置为 `[]`；JSON 损坏阻断执行；创建/修改/旧 typed 更新/详情/启动快照值一致。
- I-2：两个类型在 `[LuKai]` 时实际外发账号仅 LuKai；LuKai 不可用时不从 QF 或其他账号兜底；空集合旧行为仍可用；手动快照伪造未知 code 返回 422。
- I-3：已有任何绑定的专家无 SMTP、无重新选号/改绑；未绑定专家仍可发送且只绑定实际选中账号。材料提醒“全部已绑定”目标数为 0。
- I-4：NEW 重试与材料提醒预估/执行同源；ES 已绑定目标至少在发送前被跳过，`skippedReasons.BOUND_SENDER_ALREADY_SET` 可见；ES 候选数仍以现有 `countExperts` 为估算口径。
- I-5：共享 IMAP 的 LuKai/QF 分别作为逻辑 code 处理，QF 禁用时不会借 owner LuKai 的 enabled 状态外发。
- 运行：`mvn -Dtest=BatchSendTaskConfigServiceTest,BatchSendControlServiceTest,ManualInitialOutreachServiceTest test`；Flyway 集成环境运行 `FlywayMigrationIntegrationTest` 并核对新增列/旧行值；如本地无数据库，记录未运行而不宣称通过。

## 人工验收清单

### A-1：限定单账号
- 前置条件：测试环境有两个 enabled、可发送的逻辑账号 A/B，各有未绑定且未抑制的专家；用 API 建立 `senderAccountCodes:["A"]` 的介绍邮件任务。
- 操作步骤：执行任务，查看发件记录及执行日志。
- 预期结果：成功邮件的 `senderAccountCode` 全为 A；B 发送数增加 0；A 不可用时任务停发，B 仍增加 0。
- 覆盖：I-1、I-2、I-5。

### A-2：已绑定跨账号跳过
- 前置条件：专家 X 已绑定 B，专家 Y 未绑定；任务只选 A，两者均满足介绍邮件收件条件。
- 操作步骤：执行一次，并查看两名专家邮件记录与绑定信息。
- 预期结果：X 本次新增外发 0、绑定仍 B；Y 若通过其余门禁，本次仅从 A 外发并绑定 A。
- 覆盖：I-2、I-3、I-4。

### A-3：材料提醒回归
- 前置条件：建一个材料提醒任务，只选 A；目标专家已有联系人且均已绑定 B，未发过材料提醒。
- 操作步骤：预估人数，再执行一次。
- 预期结果：预估可发送目标 0；执行新增材料提醒邮件 0；绑定与收信、人工回复记录不变。
- 覆盖：I-3、I-4、must-not-change。

### A-4：旧配置与旧 typed API
- 前置条件：迁移前已有批量任务，迁移后其账号列表为 `[]`；另建一个只选 A 的旧 typed 对应配置。
- 操作步骤：先执行空列表任务；再通过旧 `/types/{sendType}/config` 修改时间并重新读取配置。
- 预期结果：空列表仍按既有所有可发送账号策略选号；只选 A 的任务修改时间后仍回显 `["A"]`。
- 覆盖：I-1、I-2、配置写→读→快照。

### A-5：共享收件箱别名
- 前置条件：测试数据将 A/B 配为同一物理 IMAP 收件箱的两个逻辑发件账号，B 为 disabled；不依赖共享收件箱 01 的配置 UI，不要求生产 alias 切换。
- 操作步骤：任务仅选 A 执行一次，再改为仅选 B 执行一次。
- 预期结果：首轮仅 A 外发；次轮外发 0；不会用物理 owner 代替 B 发件。
- 覆盖：I-2、I-5、账号状态写→执行读。

### A-6：手动请求的非法账号
- 前置条件：准备一个合法的独立手动执行快照，将 `senderAccountCodes` 设为 `["DOES_NOT_EXIST"]`。
- 操作步骤：POST `/api/mail/batch-send/manual-executions`；查看响应与任务/邮件记录。
- 预期结果：HTTP 422；无新执行、无 SMTP 外发；请求中的未知 code 不被当作 `[]`。
- 覆盖：I-1、I-2、手动快照写入口。
