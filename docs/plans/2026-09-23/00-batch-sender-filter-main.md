# 批量邮件发件账号筛选与已绑定专家跳过：MAIN 总计划

> 状态：待人工审阅；不授权实施、部署或线上发信。本文件只约束两份子计划的顺序、接口、跨计划验收与发布。实现细节和允许改动的产品文件，以各子计划的 `## 变更文件清单` 为准；MAIN 不额外授权代码改动。发现代码与计划不符，先修订 MAIN 和受影响子计划，再继续。

| 顺序 | 权威子计划 | 交付 | 进入下一阶段门槛 |
|---|---|---|---|
| 01 | [后端：配置、快照、严格选号与绑定跳过](01-batch-sender-filter-backend.md) | V135、`senderAccountCodes` API/快照、两类发送路径门禁 | 后端 I-1～I-5、Flyway 与黑盒 A-1～A-6 通过；不单独发布 |
| 02 | [前端：定时与手动多选](02-batch-sender-filter-frontend.md) | 两个 picker、配置/预估/手动快照、差异提示 | 前端 I-1～I-3/S-1～S-2 与黑盒 A-1～A-4 通过；再做联合验收 |

## 需求描述

- 可观察结果：运营在定时任务或独立手动执行中选定逻辑发件账号后，本次批量邮件仅由这些账号发送；已绑定任何发件账号的专家不参与本次批量发送。未选账号的旧任务维持原有全池选号。
- 不得改变：专家既有绑定；共享收件箱的 IMAP owner 与逻辑 SMTP 发件身份；账号 enabled/暂停/预热/额度/自检门禁；单封人工邮件、自动回信和退信路径；已有任务的其他筛选与执行快照语义。
- 不在范围：共享收件箱的收信路由与现网迁移、全局回复账号策略、历史联系人/邮件修复、新的账号管理页或发信策略重构。

## 关键不变量

### M-1：两份子计划严格串行，联合发布
- 规则：先实现并独立验证 01，再以 01 的实际 API/快照结果实施 02；不能并行从旧代码基线各自实现。01 完成后标记 `IMPLEMENTED_NOT_RELEASABLE`，直到 02 与联合验收通过；两份作为一个功能发布/回滚单元。子计划文件清单分别为 10、3 个产品/测试文件，MAIN 不合并成超限计划。
- 适用：执行、验证、发布、回滚。
- 违反后果：页面显示已筛选但服务器仍从全池发件，或服务器已换语义而页面传旧字段。
- 来源：`K-master-plan-shared-file-sequential-gates`；`BatchSendConfigController.kt:98-106` 预估直接接收快照，`BatchSendControlService.kt:418-441` 独立校验启动快照。

### M-2：跨层字段与账号身份只有一份语义
- 规则：数据库 `sender_account_codes_json` ↔ API/启动快照 `senderAccountCodes` ↔ 前端两个 picker 的 value，均为 `mail_sender_account.account_code` 列表；`[]` 为不限制，非空为严格白名单。不得存 `senderEmail`、`inboundMailboxCode` 或物理 IMAP owner code，也不得把共享 IMAP 的 LuKai 与 LuKai_QF 合为一个选择。坏 JSON、非法账号不允许悄悄转成 `[]`。
- 适用：01 的持久化/启动/选号，02 的选项/回显/保存/预估/执行。
- 违反后果：配置跨层漂移、别名串号、意外扩大外发范围。
- 来源：`BatchSendTaskConfig.kt:7-111`、`BatchExecutionModels.kt:12-36,279-335`、`MailSenderAccountController.kt:21-31,96-126`；共享收件箱 [MAIN](00-shared-inbox-main.md) M-1。

### M-3：绑定排除高于账号筛选
- 规则：专家任一 `expert_contact` 行的 `bound_sender_account_code != NULL` 时，两种批量邮件都跳过；即使绑定账号恰在选中集合内也不发送，不换绑、不以筛选账号接管。INTRODUCTION 覆盖 NEW 重试和 ES 候选；MATERIAL_REMINDER 覆盖目标构造和发送前重读。材料提醒目标全绑定时允许 0 个可发送目标。
- 适用：01 的目标读取、发送循环、预估和跳过统计；02 的说明文案。
- 违反后果：同一专家跨账号重复外发，或 UI 误导运营。
- 来源：`V1__create_business_tables.sql:79-95` 的 `(campaign_id,orcid_id)` 唯一键、`V85__add_expert_contact_sender_binding.sql:1-11` 的 NULL 语义；`ManualInitialOutreachService.kt:1002-1038,1201-1258` 当前两类目标构造。

### M-4：所有发信门禁只在选中集合内运作
- 规则：非空选择下，每轮可用账号、自检、容量求和和最终 `selectAccount` 均限制在选中逻辑 code；选中账号后来禁用/暂停/额度不足时停发或跳过，不回退到未选中账号。空选择保留旧门禁与选号。预估沿用现有口径：NEW 重试与材料目标过滤绑定；INTRODUCTION 的 ES `countExperts` 是候选估算，不伪称扣除 MySQL 绑定后的精确数。
- 适用：01 的 `ManualInitialOutreachService`、`SenderAccountAssignmentService`；02 的预估提示与快照。
- 违反后果：预览与实际外发身份不一致，或未选账号在额度兜底时发信。
- 来源：`ManualInitialOutreachService.kt:167-335,451-469,499-730,907-995`、`SenderAccountAssignmentService.kt:16-38`；K-recipient-count-preview-parity。

### M-5：迁移号与共享文件的落地顺序
- 规则：本组是批量发件账号筛选功能的唯一发布单元。V134 已由并行 run（`fast/2026-09-23-shared-inbox-master` 的 `V134__shared_inbox_owner.sql`，已实施并轻量验证）占用，故本组 01 使用其后的下一个空号 **V135**（`V135__add_batch_sender_account_codes.sql`），保持「共享收件箱 V134 → 本组 V135」的原顺序；本组不 rebase 到该分支。本组 02 仍以当前 `index.html`/`app.js` 为基线修改，共享收件箱的 owner UI 在并行分支上，两分支合入时共用文件按「方法区分开改、不整文件覆盖」人工解冲突，只增自己的字段/控件，不覆盖对方的 owner 配置或本组 picker、缓存键与测试断言。生产 Flyway 当前关闭，生产 DDL 按各自发布审批和现场核验处理，本 MAIN 不授权执行。若实际版本或文件基线变了，先改计划，不自行猜迁移号或机械套旧行号。
- 适用：实施前检查、合并、迁移、静态资源构建。
- 违反后果：迁移重号（同库两个 V134 会使 Flyway 启动即失败）、共用 UI 文件覆盖、线上旧 JS 缓存。
- 来源：人工批准的计划改写（2026-09-23，`docs/plans/fast/2026-09-23-batch-sender-filter-main/ledger.md` 的 `## Amendments` A1、A4）；当前本组子计划的 V135 与静态资源审计。

## 现状审计

### 配置→快照→发送链
- Schema：`V72__create_batch_send_task_config.sql:1-31` 当前没有发件账号列表；`BatchSendTaskConfig.kt:7-111`/`BatchExecutionModels.kt:12-36` 当前无对应字段。`V85__add_expert_contact_sender_binding.sql:1-11` 的 NULL 代表未绑定。
- 写路径：`BatchSendTaskConfigService.create/update/updateLegacyConfig` 写配置；前端 `saveBatchConfigEditor` 发 POST/PUT；手动面板直接 POST `BatchExecutionSnapshot`，不经过配置服务；绑定由 `SenderAccountBindingService.bindIfAbsent/rebind/migrateAccount` 等既有路径写入。
- 读路径：`toView/toExecutionSnapshot` 读配置；`BatchSendControlService` 启动读取快照；`ManualInitialOutreachService` 两发送循环与 `countBySnapshot` 读快照/联系人；`SenderAccountAssignmentService.selectAccount` 当前从所有 enabled 账号中打分。详细逐路径审计在 01，不在 MAIN 重复定义实现。
- 跨计划交互点：01 的字段/API → 02 的回显/保存；02 的预估/手动快照 → 01 的校验/发送；共享收件箱逻辑账号 → 本组白名单；绑定写入 → 两类目标排除。
- 外部边界：`AutoMailReplyService.kt:143,204-206` 在识别联系人后，继续以传入的逻辑账号保存来信；共享收件箱 [02 路由计划](02-shared-inbox-routing.md) 第 26 行按 To/Cc、In-Reply-To 定归属，未规定 `bound_sender_account_code` 冲突处理。因此本 MAIN 只能验收批量发送的绑定排除，不能据此宣称“已绑定专家的全局收发只走绑定账号”已经完成；该全局规则需要在收信/回复计划中单独核验与补充。

### 共享文件清单

| 文件 | 共享收件箱 run | 本组子计划 | 落地顺序 |
|---|---|---|---|
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | V134 断言（并行分支 `fast/2026-09-23-shared-inbox-master` 已实施） | 后端 01 的 V135/旧行断言 | 本组先按 V133→V135 改「最新版本」断言（并行分支留 V134 缺口）；定点 target 断言不动；两分支合入时该测试文件人工解冲突 |
| `src/main/resources/static/index.html` | owner 选择器、启用文案、资源键（在并行分支上） | 前端 02 的批量任务 picker、资源键 | 本组按当前 main 基线改 picker 并按当前实际键统一 bump；合入时按方法区分开改，不覆盖对方 owner UI |
| `src/main/resources/static/app.js` | owner 选择/保存/回显（在并行分支上） | 前端 02 的账号多选/快照 | 本组按当前 main 基线加账号多选/快照方法；合入时方法区分开改，不得整文件覆盖 |

## 实现方案

### G-0：迁移号与基线门槛（M-5）
- 开跑前核对实际最高迁移版本与工作树：基线 `main` @ `9237d6f` 最高为 `V133__create_discovery_paper_queue.sql`；V134 已被并行 run 的 `V134__shared_inbox_owner.sql` 占用（该分支已实施并轻量验证），本组 01 使用下一个空号 V135，不等待共享收件箱 run 收尾。生产 DDL 是否已执行须到发布前现场核验，不能由本门槛推定。

### G-1：后端 01（M-1～M-5）
- 仅按 [后端子计划](01-batch-sender-filter-backend.md) 的 10 文件清单实施；先跑其定向/迁移验证，再做 API 黑盒 A-1～A-6。A-5 用测试数据把 A/B 配成同一物理 IMAP 收件箱的两个逻辑发件账号（不依赖共享收件箱 01 的配置 UI），不要求生产 alias 切换。记录实际字段 JSON、启动快照、两类型发信 code、绑定跳过结果与失败项。通过后状态 `IMPLEMENTED_NOT_RELEASABLE`，以此结果作为前端唯一接口基线。

### G-2：前端 02（M-1、M-2、M-3、M-5）
- 仅按 [前端子计划](02-batch-sender-filter-frontend.md) 的 3 文件清单实施；真实账号 API 决定选项，不写死线上账号。执行前重查 11 项资源键和固定字面量测试；后端字段接口或共享收件箱 DOM 有变化时先修计划。通过本阶段 JS/DOM/视觉与黑盒 A-1～A-4 后进入联合验证。

### G-3：联合验证与发布（M-1～M-5）
- 在同一测试版本验证：定时配置保存→启动快照→INTRODUCTION 实际发件；手动从配置带入并修改→预估/启动快照→实际发件；MATERIAL_REMINDER 已绑定全跳过；同物理 IMAP 收件箱的两个逻辑账号分开选择；旧配置 `[]` 保持全池；账号失效时不跨白名单兜底。核对真实 `index.html` DOM、JS payload、API 返回、数据库列和 `mail_record.sender_account_code`，不能只看单元测试。
- 任何联合失败归回拥有该代码文件的子计划修复并重跑该计划门禁与联合场景；需要新增代码文件或改跨层语义时先修订 MAIN/子计划。联合机器与人工验收通过后，才提交本功能发布审批。回滚按一个功能单元处理，不允许只回滚后端或前端使二者字段语义分裂。

## 变更文件清单

| # | 文件 | 作用 |
|---|---|---|
| 1 | `docs/plans/2026-09-23/00-batch-sender-filter-main.md` | 两子计划的跨阶段治理；不修改产品代码 |

## 验收标准

- M-1：01、02 各自定向验证记录存在且时间顺序为 01→02；联合验证在两者后；无单独发布 01/02 的操作记录。
- M-2：DB/API/前端/实际 SMTP 日志均以相同逻辑 `accountCode` 表示选择；`[]` 旧任务不被限制；非空选择没有未选账号外发。
- M-3：两个邮件类型的已绑定专家均新增 0 封、绑定值不变；未绑定目标仍可从选中账号发送；材料目标全绑定时预估与执行均为 0。
- M-4：选中账号禁用/暂停/满额时未选账号发件 0；INTRODUCTION ES 预估明确仍是候选估算，发送跳过数有原因。
- M-5：本组迁移号为 V134 之后的下一个空号 V135，与并行共享收件箱分支的 V134 不重号；生产 DDL 有独立现场核验与审批；11 个资源键统一；三个共享文件在两分支合入时按方法区分开改、人工解冲突。各子计划文件数仍 ≤10。

## 人工验收清单

### A-1：配置到外发闭环
- 前置条件：测试环境有两个可发送的逻辑账号 A/B，两名未绑定且满足介绍邮件筛选的专家；用 UI 建立只选 A 的任务。
- 操作步骤：保存任务→重新打开核对 chip→执行一次→查看执行快照和外发记录。
- 预期结果：chip、配置 API、启动快照均为 `["A"]`；所有新增成功外发的 `sender_account_code` 为 A，B 新增外发 0。
- 覆盖：M-1、M-2、G-1→G-2→G-3。

### A-2：绑定排除与材料提醒
- 前置条件：专家 X 已绑定 B，专家 Y 未绑定；配置只选 A；另有只包含已绑定联系人的材料提醒任务。
- 操作步骤：执行介绍邮件任务和材料提醒任务，查看预估、跳过原因、绑定与外发记录。
- 预期结果：X 本次外发 0、仍绑定 B；Y 满足其余门禁时仅 A 外发并绑定 A；材料提醒预估 0、外发 0。
- 覆盖：M-3、M-4。

### A-3：同物理收件箱的两个逻辑账号与失效账号
- 前置条件：A/B 是同一物理 IMAP 收件箱的两个逻辑发件账号（如现网 LuKai/LuKai_QF），分别有不同 SMTP 发件地址；B 已停用且一份历史配置选了 B。
- 操作步骤：打开该配置；执行仅选 B 的任务，再将任务改为仅选 A 执行。
- 预期结果：批量选择器把 A/B 分成两项，B 原选值可见并标停用；仅选 B 时外发 0，仅选 A 时只 A 外发。
- 覆盖：M-2、M-4。

### A-4：旧任务和手动覆盖
- 前置条件：迁移前任务的 `senderAccountCodes` 为 `[]`；另有只选 A 的定时任务。
- 操作步骤：执行旧任务；在手动页带入只选 A 的任务，改选 B 后查看差异、预估与执行。
- 预期结果：旧任务仍从原全池可发送账号选号；手动页显示“已修改”，预估与执行快照均为 `["B"]`，原定时配置仍为 `["A"]`。
- 覆盖：M-1、M-2、M-4、旧行为回归。
