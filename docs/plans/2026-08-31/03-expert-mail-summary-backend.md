# 专家邮件收发统计与精确聚合过滤（后端）

> 执行顺序：本计划先于 `04-expert-list-ui-refinement.md`。本计划只做向后兼容的收发件箱聚合接口扩展，可独立部署；前端计划随后消费新增字段与 `expertContactId` 参数。

## 需求描述

扩展既有 `GET /api/mail/mailbox/by-expert`：允许按 `expertContactId` 精确限定一位专家，并在每个专家聚合结果中返回“收到、成功发出、发送失败”三个计数。计数必须与当前收发件箱的邮件来源、账号范围及其他过滤条件完全一致，供专家详情摘要和精确跳转复用。

必须不变：

1. 不传 `expertContactId` 时，现有按专家聚合、过滤、排序、分页、待处理和标签行为不变。
2. `mailCount`、`pendingCount`、`mails` 继续保留；现有收发件箱调用方不需要改请求。
3. 账号范围继续使用 `SenderAccountRepository.findAllByAccountCodeNot(SIMULATOR_NOOP)` 的当前结果，不在本计划中擅自增加 `enabled=true` 条件。
4. 专家聚合仍须先在数据库按专家分组、排序、分页，再查询本页子邮件；不得改为前端分组或先分页邮件。
5. 不修改 `mail_record`、`inbound_mail_processing`、`expert_contact` 表结构，不增加写路径，不改变任何邮件发送、收取、状态流转或自动回复行为。

范围外：删除专家详情接口中的 `mails` 字段、清理历史 `mail_record` 入站副本、重做收发件箱 API、改变发送状态枚举、增加缓存或新表。

## 关键不变量

### Invariant I-1: 三类计数只取收发件箱权威来源
- Rule: `receivedCount` 只统计 `inbound_mail_processing` 中已关联该专家的行；`sentCount` 只统计 `mail_record.direction='OUTBOUND' AND send_status='SENT'`；`failedCount` 只统计 `mail_record.direction='OUTBOUND' AND send_status='FAILED'`。不得把 `mail_record.direction='INBOUND'` 再计入收到数量，否则同一来信会与 `inbound_mail_processing` 重复。
- Applies to: `MailRecordRepository.listMailboxExpertSummaries` 的 UNION 与 `MailboxExpertSummaryRow`/`MailboxExpertGroupResponse` 映射。
- Violation consequence: 专家详情显示的收发数量与收发件箱列表不一致，入站邮件重复计数或失败邮件被算作成功。
- 来源: original（源码证据：`MailRecordRepository.kt:543-581` 当前聚合 UNION 仅取 OUTBOUND `mail_record` 与 `inbound_mail_processing`；写路径证据见现状审计）。

### Invariant I-2: 精确专家过滤三段一致
- Rule: 可空参数 `expertContactId` 必须同时进入专家总数查询和专家摘要查询的 OUTBOUND/INBOUND 两个 UNION 分支；子邮件查询只能使用摘要返回的 `expertContactIds`，从而继承同一精确范围。禁止用 `recipientEmail LIKE` 近似替代 contact id。
- Applies to: `MailboxController.listByExpert`、`MailboxService.listByExpert`、`MailRecordRepository.countMailboxExperts`、`MailRecordRepository.listMailboxExpertSummaries`、现有 `listMailboxByExpertContactIds` 调用。
- Violation consequence: 同邮箱子串、邮箱变更或分页时可能跳到错误专家，或摘要是一位专家但子邮件混入其他专家。
- 来源: original（源码证据：`MailboxController.kt:118-141` 当前无 contact id 参数；`MailRecordRepository.kt:555/577` 当前邮箱条件是 `LIKE`）。

### Invariant I-3: 先分组再分页
- Rule: `expertContactId` 只是聚合 SQL 的附加 WHERE 条件，不得改变 `GROUP BY expert_contact_id`、`MAX(event_at)` 排序、`LIMIT/OFFSET` 后批量查子邮件的两段式结构；空页继续短路子邮件查询。
- Applies to: `MailboxService.listByExpert` 与三条 repository 查询。
- Violation consequence: 同一专家跨页、专家总数与组内邮件数失真，或空页生成非法 `IN ()`。
- 来源: K-group-before-pagination。

### Invariant I-4: 新参数和新响应字段向后兼容
- Rule: `expertContactId` 默认 `null`；新增的 `receivedCount`、`sentCount`、`failedCount` 为附加 JSON 字段，现有 `mailCount`、`pendingCount`、`mails` 字段名称和语义不变。Kotlin DTO/投影新增参数放在带默认值的位置，避免迫使无关测试和调用方重写。
- Applies to: Controller 请求签名、Service 请求签名、`MailboxExpertSummaryRow`、`MailboxExpertGroupResponse`。
- Violation consequence: 现有前端和测试编译失败，或普通收发件箱请求发生行为变化。
- 来源: original。

### Invariant I-5: 统计查询保持只读
- Rule: 本计划不得修改任何表或任何 `save`/`UPDATE` 路径；统计结果由现有事实行实时聚合，不回填冗余计数字段。
- Applies to: 本计划全部生产文件。
- Violation consequence: 计数与事实数据漂移，并把一个展示需求扩成跨写路径迁移。
- 来源: original；`inbound_mail_processing` 写路径复核来源 K-inbound-processing-write-paths。

## 现状审计

### `mail_record`
- Schema/mapping:
  - `V1__create_business_tables.sql:97-115`：`expert_contact_id` 外键非空，`direction` 非空，`send_status` 可空，时间字段为 `received_at`/`sent_at`/`created_at`。
  - `V15__add_mail_monitoring_columns_and_promotion_audit.sql:2-15`：补充 `sender_account_code`、`triggered_by` 及监控索引。
  - `MailRecord.kt:7-30`：Spring Data JDBC 映射；本计划只读取 `expertContactId`、`direction`、`sendStatus`、账号和时间/筛选字段。
- Write paths（`rg 'mailRecordRepository.(save|saveAll)|MailRecord(' src/main/kotlin` 全量复核）：
  1. `ManualOutreachTxHelper.recordSuccess/recordFailure`（`:59-79`, `:98-131`）写首发 OUTBOUND，状态明确为 `SENT`/`FAILED`。
  2. `MeetingScheduleService`（`:144-163`）写会议邮件 OUTBOUND，状态来自投递结果。
  3. `ManualExpertMailService`（`:69-88`）写人工邮件 OUTBOUND，状态来自投递结果。
  4. `ManualReplySendAttemptService`（`:215-249`, `:293-328`）以发送尝试幂等写人工回复 OUTBOUND，状态为 `SENT`/`FAILED`。
  5. `AutoMailReplyService`（`:267-286`, `:614-633`, `:799-823`, `:1002-1021`）写入站副本及自动 QA/会议 OUTBOUND；本计划的 received 计数明确不读取其中的 INBOUND 副本。
  6. 历史迁移 `V24__extend_mail_send_attempt_state_and_link_mail_record.sql:26-52` 只做既有数据回填；运行时代码无自定义 UPDATE。
- Read paths（`rg 'mailRecordRepository.' src/main/kotlin` 全量复核并按模块归并）：
  1. 专家/批量：`ExpertContactManagementService`、`ManualInitialOutreachService`、`OperatorStatusReconcileService` 读取专家邮件历史、是否已成功首发/提醒及状态对账。
  2. 文档：`DocumentTextExtractor`、`ExpertDocumentBrowseService` 按 mail id 查附件归属。
  3. AI：`AiTrainingController`、`AiQaExtractionService`、`TrustReplyWorkbenchService` 读取入站样本与会话历史。
  4. 邮件业务：`AutoMailReplyService`、`AutoReplyPreviewService`、`AutomaticApplicationPromotionService`、`BounceCollectionService`、`BounceRateMonitorService`、`GroundedAutoReplyDecisionService`、`ManualExpertMailService`、`ManualReplySendAttemptService`、`PendingMailOperationService`、`UnmatchedInboundMailService`。
  5. 收发件箱：`MailboxService.kt:53-79,84-173,185-188,259-346` 读取平铺列表、专家摘要、专家子邮件、任务执行邮件和详情。
  6. 监控：`MailMonitoringService` 的当日统计、首发/回复列表、账号/地区/国家聚合。
- Interaction points:
  - 上述 5 类 OUTBOUND 写路径 → `listMailboxExpertSummaries` 的成功/失败计数。
  - `AutoMailReplyService` 的 INBOUND `mail_record` 副本不得进入 received；权威 received 来自下一节的 `inbound_mail_processing`。（I-1）

### `inbound_mail_processing`
- Schema/mapping:
  - `V5__create_inbound_mail_processing.sql:1-20`：账号+IMAP UID 唯一；`expert_contact_id` 可空并外键关联专家；`process_status`/`received_at` 非空。
  - `V10__create_expert_email_alias_and_extend_unmatched_mail.sql:97-120`：补充正文、清洗正文和人工处理字段。
  - `V14__contact_index_level_and_reason_type_and_qa_display_name.sql:36-50`、`V15...sql:18-25`：补充 `reason_type` 及历史回填。
  - `InboundMailProcessing.kt:7-30`：本计划只读取关联专家、账号、时间、处理状态和现有筛选字段。
- Write paths（来源 K-inbound-processing-write-paths，已用 grep 复核）：
  1. 新建 sink：`AutoMailReplyService.confirmManualReviewWithBody`（`:1046-1077`）和 `confirmProcessed`（`:1093-1125`）。
  2. 绑定/处理更新：`UnmatchedInboundMailService.bindToContact/markResolved`（`:141-207`, `:210-233`）。
  3. 待办处理更新：`PendingMailOperationService.markResolved`（`:956-978`）。
  4. 取消处理的原子 UPDATE：`InboundMailProcessingRepository.reopenManualResolved`（`:22-36`）。
  5. 历史迁移：`V14...sql:43-50`、`V15...sql:23-25`。
- Read paths（`rg 'inboundMailProcessingRepository.' src/main/kotlin` 全量复核并归并）：
  1. `AiTrainingController`、`TrustReplyWorkbenchService` 读取专家入站上下文。
  2. `InboundMailSummaryController`、`UnmatchedInboundMailController` 读取来信摘要/详情。
  3. `AutoMailReplyService` 做 UID 幂等；`AutoReplyPreviewService`、`InboundMailTagService` 读取详情。
  4. `BounceBackfillService` 分页扫描；`PendingMailOperationService`、`UnmatchedInboundMailService` 读取/计数人工队列。
  5. `MailboxService` 读取收发件箱详情；聚合列表通过 `MailRecordRepository` 的 UNION 原生 SQL读取本表。
  6. `MailMonitoringService` 读取入站活动、待处理统计和账号最近收信时间。
- Interaction points: 两个新建 sink及三个状态更新路径 → 聚合 UNION；所有已关联行计入 received，`MANUAL_REVIEW` 同时影响 pending，精确专家过滤不得改变该关系。（I-1/I-2）

### `expert_contact`
- Schema/mapping:
  - `V1__create_business_tables.sql:79-95`：主键、唯一 `(campaign_id, orcid_id)`、邮箱和姓名。
  - `V14...sql:2-8`：`current_index_level` 非空，枚举注释 `RAW/CANDIDATE/APPLICATION`。
  - `V19...sql:1-20`：`operator_status` 非空默认 `NOT_CONTACTED`。
  - `ExpertContact.kt:7-34`：聚合响应读取 `id/name/email/orcidId/operatorStatus/currentIndexLevel`。
- Write paths（`rg 'expertContactRepository.(save|updateCountryById|...Binding...)'` 全量复核）：
  1. 联系人创建/会话：`InitialOutreachService`、`ManualInitialOutreachService`、`ConversationStateService`、`ExpertContactManagementService`。
  2. 漏斗/运营状态：`ExpertIndexLevelOperationService`、`ExpertIndexController`、`ExpertOperatorStatusService`、`AutomaticApplicationPromotionService`、`OperatorStatusReconcileService`。
  3. 人工待办：`AutoMailReplyService`、`PendingMailOperationService`、`UnmatchedInboundMailService`。
  4. 绑定/回填：`SenderAccountBindingService` 的 4 个定向 UPDATE、`ContactCountryBackfillService.updateCountryById`。
- Read paths（`rg 'expertContactRepository.' src/main/kotlin` 全量复核并归并）：campaign 联系人管理/批量发送/漏斗；expert ES 写入与状态同步；AI 训练与信任工作台；mail 自动回复、人工回复、收发件箱、退信、绑定与待办；monitoring 活动/晋级；QA 和模板预览。
- Interaction points: 聚合 SQL 只以 `expert_contact.id` 精确连接并投影 5 个展示字段；本计划不得写回任何计数或状态。（I-2/I-5）

### 当前聚合实现
- `MailboxController.kt:118-141` 暴露 direction/account/keyword/email/date/pending/tag/page/size，无 `expertContactId`。
- `MailboxService.kt:84-173`：先 `countMailboxExperts`，再 `listMailboxExpertSummaries`，最后用该页 id 调 `listMailboxByExpertContactIds`，符合 K-group-before-pagination。
- `MailRecordRepository.kt:531-602` 摘要 SQL 当前只返回 `mail_count/pending_count/latest_event_at`；`:604-658` 计专家；`:660-751` 取该页子邮件。
- 当前 recipientEmail 是 `LIKE '%...%'`（`:555`, `:577`, `:619`, `:640`），不能作为精确跳转键。
- `MailboxExpertSummaryRow`（`:764-774`）与 `MailboxExpertGroupResponse`（`MailboxController.kt:40-50`）没有方向/发送状态分项计数。

## 实现方案

### 阶段 1：扩展只读接口契约

1. 修改 `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxController.kt`：
   - `listByExpert` 增加可空 `@RequestParam expertContactId: Long?`，原参数和 `size.coerceIn(1,100)` 不动。
   - 原样向 `MailboxService.listByExpert` 传递。
   - `MailboxExpertGroupResponse` 增加带默认值的 `receivedCount`、`sentCount`、`failedCount`；保留所有原字段。（I-1/I-2/I-4/I-5）
2. 修改 `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt`：
   - `listByExpert` 增加默认 `null` 的 `expertContactId`。
   - 同时传入 count 与 summary repository；子邮件仍只取 summaries 的 id。
   - DTO 映射新增三个字段；总数为 0、摘要空页、模拟账号排除等分支不改。（I-1-I-5）

### 阶段 2：扩展聚合 SQL

1. 修改 `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt`：
   - `countMailboxExperts` 和 `listMailboxExpertSummaries` 各增加默认 `null` 的 `expertContactId` 参数。
   - 两条 SQL 的 OUTBOUND 分支加入 `(:expertContactId IS NULL OR mr.expert_contact_id = :expertContactId)`；INBOUND 分支加入对应 `imp.expert_contact_id` 条件。（I-2/I-3）
   - summary UNION 为每行投影 `received_flag/sent_flag/failed_flag`：INBOUND 分支分别为 `1/0/0`；OUTBOUND 按 `send_status='SENT'`、`='FAILED'` 产生 0/1 标志；外层 `SUM` 并别名为 DTO 字段。（I-1）
   - `mail_count`、`pending_count`、latest 排序、GROUP BY、LIMIT/OFFSET 及子邮件 SQL保持不变。（I-3/I-4）
   - `MailboxExpertSummaryRow` 末尾增加三个默认值字段。（I-4）

### 阶段 3：自动化验证

1. 修改 `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxControllerTest.kt`：请求带 `expertContactId=100`，验证 service 收到 100，并验证 JSON 新字段；保留 size 上限用例。（I-2/I-4）
2. 修改 `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt`：
   - 验证 contact id 同时转发给 count/summary；子查询 id 仍来自 summaries。
   - summary 的 2/3/1 三项映射到 group DTO；原 `mailCount/pendingCount/mails` 不变。
   - 不传 contact id 的现有测试继续证明兼容。（I-1-I-4）
3. 修改 `src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt`：在两个专家、2 条 processing 入站、2 条 SENT、1 条 FAILED、另含一条 `mail_record.direction='INBOUND'` 副本的 MySQL fixture 上验证：
   - contact 1 返回 `received=2/sent=2/failed=1`，INBOUND mail_record 不重复；
   - contact 2 不进入 count/summary；
   - `expertContactId=null` 保持原聚合；
   - mailCount/pending/order/子邮件仍符合既有契约。（I-1-I-4）

## 变更文件清单

| # | 文件 | 类型 | 用途 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxController.kt` | 修改 | 请求参数与响应 DTO |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt` | 修改 | 参数转发与计数装配 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt` | 修改 | 精确过滤与分项统计 SQL |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxControllerTest.kt` | 修改 | MVC 契约测试 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt` | 修改 | Service 转发/装配测试 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt` | 修改 | MySQL 聚合语义测试 |

共 6 个文件；1 个子系统（收发件箱后端）；0 个共享存储新字段。

## 验收标准

- I-1: Repository IT 构造明确的 INBOUND processing、INBOUND mail_record 副本、SENT/FAILED OUTBOUND，断言 `2/2/1`，并断言 `mailCount` 未被新分项逻辑改变。
- I-2: Controller/Service 测试断言 `expertContactId=100` 贯穿 MVC→Service→count/summary；MySQL 测试断言另一专家完全不返回。
- I-3: 既有“同专家不跨页、按 latest_event_at 排序、空页不查子邮件”测试继续通过；新增过滤未改 GROUP BY/LIMIT/第二段 IN 结构。
- I-4: 不带新参数的原 Controller/Service/Repository 用例全部通过，JSON 仍含 `mailCount/pendingCount/mails`；新增字段为附加字段。
- I-5: `git diff --name-only` 仅命中上述 6 文件；无 migration、domain entity、写服务变更；`git diff` 无 `save(`/`UPDATE` 新增。
- 目标测试：`mvn -Dtest=MailboxControllerTest,MailboxServiceTest,MailRecordRepositoryMonitoringIT test`。
- 全量门禁：`mvn test`（其中 `MailRecordRepositoryMonitoringIT` 需要 Docker/Testcontainers）。
- 静态检查：`git diff --check`。

## 人工验收清单

### A-1: 精确专家三类计数
- 前置条件: 测试库存在专家 A(id=100) 与专家 B(id=101)；A 在非模拟账号下有 2 条已关联 `inbound_mail_processing`、2 条 OUTBOUND/SENT、1 条 OUTBOUND/FAILED，并另有 1 条 `mail_record.direction='INBOUND'` 副本；B 至少 1 封邮件。
- 操作步骤: 1. 登录后台；2. 浏览器或 API 客户端请求 `GET /api/mail/mailbox/by-expert?expertContactId=100&page=0&size=20`；3. 查看 JSON 第一组。
- 预期结果: `totalCount=1`；唯一 group 的 `expertContactId=100`、`receivedCount=2`、`sentCount=2`、`failedCount=1`；专家 B 不出现；INBOUND mail_record 未让 received 变成 3。
- 覆盖: I-1/I-2；observable outcome 1。

### A-2: 普通按专家聚合回归
- 前置条件: A-1 数据保留。
- 操作步骤: 1. 请求不带 `expertContactId` 的 `GET /api/mail/mailbox/by-expert?page=0&size=20`；2. 对比实施前同一数据的专家数量、顺序和每组 `mailCount/pendingCount/mails`。
- 预期结果: A 与 B 均按最近事件降序出现；`mailCount/pendingCount/mails` 与实施前一致；响应仅额外增加三个计数字段。
- 覆盖: I-3/I-4；must-NOT-change 1/2/4。

### A-3: 收信写入后统计可见
- 前置条件: 选定非模拟邮箱账号和专家 A，记录 A 当前 `receivedCount=N`；允许在测试环境插入一条符合 `V5` 约束、`expert_contact_id=100` 的 `inbound_mail_processing`，`process_status='PROCESSED'`。
- 操作步骤: 1. 插入该行；2. 再次请求 A-1 接口。
- 预期结果: `receivedCount=N+1`；`sentCount/failedCount` 不变；无需任何回填任务。
- 覆盖: I-1/I-5；interaction point `AutoMailReplyService` 新建 sink → 聚合读取。

### A-4: 发送成功与失败写入后统计可见
- 前置条件: 专家 A 当前 `sentCount=S`、`failedCount=F`；在测试环境分别通过既有发送记录路径或等价 SQL新增一条 OUTBOUND/SENT 和一条 OUTBOUND/FAILED。
- 操作步骤: 1. 完成两条事实行写入；2. 再次请求 A-1 接口。
- 预期结果: `sentCount=S+1`、`failedCount=F+1`、`receivedCount` 不变；两条记录均出现在 group.mails。
- 覆盖: I-1/I-5；interaction point OUTBOUND 写路径 → 聚合读取。

### A-5: 账号范围保持不变
- 前置条件: 专家 A 在 `SIMULATOR_NOOP` 与一个普通账号下各有邮件。
- 操作步骤: 请求 `GET /api/mail/mailbox/by-expert?expertContactId=100`。
- 预期结果: 三项计数和 `mails` 只包含普通账号数据，`SIMULATOR_NOOP` 数据不计入；行为与实施前邮箱聚合账号范围一致。
- 覆盖: I-4；must-NOT-change 3。

