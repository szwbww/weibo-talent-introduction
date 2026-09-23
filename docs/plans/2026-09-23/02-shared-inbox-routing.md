# 共享收件箱单次抓取与逻辑账号路由开发计划（2/4）

> 上位约束：[MAIN 总计划](00-shared-inbox-main.md)。依赖 01；本阶段可以部署但所有新归属配置保持 NULL，直到 03 完成。先补失败测试，再修改生产代码。
>
> 修订（A1，2026-09-23，人工批准）：本阶段把 `listAutoReceiveAccounts()` 收紧为 owner-only 后，计划 01 在 `MailSenderAccountServiceTest.kt` 写的「除模拟器外全部参与收信」断言必然失效（该断言原文只承诺「不因阶段 01 改变」）。依据 M-5「每阶段最多改自己清单文件，超出先修计划」，本计划变更文件清单新增第 11 个文件 `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt`，仅用于退役该条被取代的断言。

**目标**：共享同一物理 INBOX 的多个发件账号只抓取一次，按原始收件人路由到各自逻辑账号；旧独立账号、附件源、已处理去重、游标语义不变。当前两个账号的配置与历史修复由 04 执行。

**架构**：轮询列表只含 `inbound_mailbox_code=NULL` 的主账号；主账号 IMAP/游标取信；邮件 `To/Cc` 和必要时 `In-Reply-To` 决定逻辑账号。`sender_account_code` 仍是业务/发信归属；`mailbox_owner_code` + UIDVALIDITY + UID 是物理去重身份。无法唯一判定时只入人工待核，不发自动邮件。

**技术栈**：Kotlin、Spring Data JDBC、JavaMail、MySQL。

## 需求描述

- 可观察结果：同一封寄往别名的来信只出现一次，界面显示对应独立账号，且之后新别名可沿用同一机制；手动“检查回复”、定时检查和指定联系人检查均只抓一次物理邮箱。
- 不得改变：独立账号收信、SMTP 发信身份、物理附件定位、UIDVALIDITY/游标连续性、未判明邮件不自动发信。
- 不在范围：退信归属由 03 处理；生产 QF 切换和历史行清理由 04 处理；不新增按域名自动推断规则。

## 关键不变量

### I-1：物理抓取与业务归属分离
- 规则：对每个物理 owner，每次检查只调用一次其 IMAP 凭据、一次其 `mail_inbox_cursor`；业务 `sender_account_code` 为唯一判定的收件人逻辑账号，附件 `ImapAttachmentSource.accountCode`、后续取附件账号和 `markSeen` 仍为 owner。`enabled` 不参加收信过滤。
- 适用写路径：`receiveAndAutoReply`、`processByUids`、`processSingle`、`BatchAutoMailReplyService` 两入口、附件登记现有流程。
- 违反后果：重复待处理、游标错位、附件取不到或从错账号发信。
- 来源：`K-process-single-all-callers`、`K-sender-account-selection-sites`、`K-mail-attachment-write-paths`。

### I-2：收件人来源与拒绝猜测
- 规则：本组只有 owner 一个成员时直接沿用独立账号旧路由。共享组先解析原始顶层 `To`/`Cc` 邮箱地址，大小写无关地对本组 `sender_email` 精确匹配；恰好命中一个逻辑账号则使用它。零个或多个命中时，仅当 `In-Reply-To` 唯一命中本组的一条 `direction='OUTBOUND'` 记录时按该记录的 `sender_account_code` 路由。仍不能唯一判定（含 BCC、缺头、错误头、重复地址冲突）则以 owner 归属新建 `MANUAL_REVIEW/RECIPIENT_UNRESOLVED`，绝不自动发信、改专家状态或假装属于 QF。
- 适用写路径：IMAP 转换、单信路由、人工确认路径、回信关联读取。
- 违反后果：误发信、错误账号展示。线上已验证一封直接测试邮件保留 `To: lukai@qingfeitalent.com`，但这不证明所有历史邮件或 BCC 都保留原收件人。
- 来源：线上只读 IMAP 测试和 `ImapMailReceiveService.kt:291-345` 缺 To/Cc 的代码事实。

### I-3：物理 UID 只处理一次，历史不伪造
- 规则：新 processing 行写 `mailbox_owner_code=owner.accountCode`、真实正数 UIDVALIDITY；数据库唯一键 `(mailbox_owner_code, uid_validity, imap_uid)` 是并发兜底。入业务事务前先查该物理键，再检查组内历史空 owner 行；仅对同 UID 且非空 Message-ID、From、秒级 receivedAt 全吻合认定旧行已处理。历史 0 代际来源不明时维持 `LEGACY_UID_UNVERIFIABLE` 人工路径，不能回填当前代际。唯一键异常只在查到确切同物理键后视为重复，其余异常向外抛。
- 适用写路径：`AutoMailReplyService` 所有 processing 新建点、`InboundMailProcessingRepository` finder、UID 回填。
- 违反后果：同信重复/重发，或 UID 重用后吞新信。
- 来源：`K-inbound-seen-not-processed-marker`、`K-inbound-processing-write-paths`。

### I-4：确认与游标连续性
- 规则：业务事务成功后才 `markSeen(owner,uid)`；只有已确认/可验证重复的 UID 进入 owner 游标成功集。未解析收件人若人工行与附件登记成功，视为已确认；失败不标已读、不推进游标。指定 alias 的 UID 回填先解析到 owner IMAP，不能使用 alias 自己旧游标。
- 适用写路径：`AutoMailReplyService.kt:87-109,767-907`、`MailInboxCursorService.kt:20-75` 的既有调用。
- 违反后果：未处理邮件消失或反复重放。
- 来源：`K-process-single-all-callers`。

## 现状审计

### `mail_sender_account` / `mail_inbox_cursor`
- Schema：`V1__create_business_tables.sql:1-25` 账号独立；01 新增 nullable owner；`V49__create_mail_inbox_cursor.sql:1-7` 游标按 `sender_account_code` 唯一。
- 写路径：账号创建/更新/启停在 `MailSenderAccountService.kt:74-174`，日计数与自动暂停在 `MailSenderAccountRepository.kt:23-90`；游标仅 `MailInboxCursorService.kt:60` repository.save。计划 04 手工设置当前关系。
- 读路径：`MailSenderAccountService.kt:43-60` 当前除模拟器全部参与收信，`getReceiveAccount` 也忽略 enabled；`BatchAutoMailReplyService.kt:24,57-79` 自动/联系人检查；`AutoMailReplyService.kt:775-783,892-897` 按传入代码拿账号与游标；队列消费者和控制器最终调用同一收信入口。(来源: `K-sender-account-enabled-scope`、`K-sender-account-selection-sites`)
- 交互点：账号归属配置→批处理账号清单/单账号解析→owner IMAP 与 owner 游标。子账号旧 cursor 原样留存、不再推进。

### `inbound_mail_processing` / `mail_record`
- Schema：V120 逻辑账号+代际+UID 唯一；01 添加 nullable 物理 owner 唯一键。`mail_record` 在 V1 建，V15 加 `sender_account_code/source_inbound_id`；`MailRecord.kt:14-16` 映射两列。
- 写路径：`AutoMailReplyService.kt:204-375,971,1169,1231,1284` 保存 INBOUND/OUTBOUND/processing；`mail_record` 其他新建/更新还包括 `ManualReplySendAttemptService.kt:397,496`、`ManualExpertMailService.kt:70`、`ManualOutreachTxHelper.kt:59,110`、`MeetingScheduleService.kt:144`；processing 其他 copy/save 在 `UnmatchedInboundMailService.kt:193,238`、`PendingMailOperationService.kt:1522` 和重开 SQL `InboundMailProcessingRepository.kt:35-47`。V14/V15 曾 UPDATE processing 原因、V24 曾 UPDATE 历史 mail_record 发送关联，均已应用且本计划不重跑。(来源: `K-inbound-processing-write-paths`、`K-mail-record-source-inbound-id`)
- 读路径：`AutoMailReplyService.kt:123-140` 旧逻辑账号 UID 判重；`MailRecordRepository.kt:144-146` 通用 Message-ID 查找并不限制 OUTBOUND，不能直接作路由；`MailboxConversationRepository.kt:433-495` 以 processing 为收件展示权威，`MailboxConversationService.kt:151-193` 展示账号/待处理数。(来源: `K-mailbox-inbound-source-authority`)
- 交互点：IMAP 收件人/回信头→账号选择→新 processing/mail_record 的逻辑代码；物理 owner 唯一键→重复分支；processing 写入→聊天列表正确展示；旧行空 owner→严格指纹兼容判重。

### `mail_attachment` / `mail_attachment_transfer`
- Schema：`V7__create_mail_attachment_and_expert_document.sql:1-12` mail_record FK；`V36__add_mail_attachment_inbound_processing_link.sql:12` processing FK；`V119__create_mail_attachment_transfer.sql:25-70` 源唯一键是 `(account_code, folder, uid_validity, imap_uid, part_path)`。
- 写路径：`ImapMailReceiveService.kt:301-306` 将抓取账号写进附件 source，`AutoMailReplyService.kt:1231-1305` processing 确认后桥接；`MailAttachmentService` / `AttachmentTransferService` 保存实体与 transfer。(来源: `K-mail-attachment-write-paths`)
- 读路径：附件下载/传输以 `source.accountCode` 或 transfer `account_code` 重新登录 IMAP；对话材料从 processing/mail_record 关联读。路由只改变逻辑账号，不可改 source。
- 交互点：owner 抓取→附件源持久化→之后按 owner 下载；别名逻辑归属→界面展示账号但不覆盖附件源。

## 实现方案

### 阶段 1：头解析与组成员测试（I-1、I-2）
- 文件：`MailReceiveService.kt`、`ImapMailReceiveService.kt`、`ImapMailReceiveServiceTest.kt`（完整路径见清单）。先写 To/Cc 缺失、单个、多个、大小写、带显示名测试。`ReceivedMail` 增加只读解析结果，默认空列表保证旧测试调用兼容；只拉顶层头字段，不拉完整 MIME。
- 文件：`MailSenderAccountService.kt`。`listAutoReceiveAccounts()` 仅返回 owner，`getAutoReceiveAccount(accountCode)` 把 alias 解析为 owner，`getReceiveAccount` 仍保留原始逻辑账号读取；`getAutoReceiveAccountOrNull` 跟随 owner 解析。不得改发信账号的 `enabled` 读取。
- 文件：`MailSenderAccountServiceTest.kt`（A1 授权）。删除计划 01 写的断言 `listAutoReceiveAccounts still returns disabled and shared inbox accounts`：它把「除模拟器外全部参与收信」的历史语义连同 `findAllByAccountCodeNot("SIMULATOR_NOOP")` 调用一起钉死，与本阶段 owner-only 收信列表直接冲突；只退役该条，同文件其余断言（含 I-1/I-3 相关用例）保留。

### 阶段 2：路由、去重、单次抓取（I-1～I-4）
- 文件：`AutoMailReplyService.kt`、`BatchAutoMailReplyService.kt`、`InboundMailProcessingRepository.kt`、`MailRecordRepository.kt`、`AutoMailReplyServiceTest.kt`、`OperatorStatusWriteSeamGuardTest.kt`。
- `MailRecordRepository` 增加只读 OUTBOUND Message-ID 候选查询，返回列表以拒绝歧义。组成员从现有账号列表按 `inbound_mailbox_code` 分组；按 I-2 决定逻辑账号。新建 processing 两处都写物理 owner；现有 INBOUND mail_record 写逻辑账号，不动 `source_inbound_id` 语义。`processSingle` 接收逻辑账号时内部解析物理 owner，仅用 owner 做 markSeen；所有非退信/非 DMARC 来信（包括收件人未解析者）必须先进入 `processSingle`，不得在其调用前直接建人工行。人工不可判明路由必须在任何自动回复或专家状态写入之前。此入口次序供 03 的内部探针过滤复用。
- `BatchAutoMailReplyService.receiveAndAutoReplyAll` 仅传 owner；联系人范围从历史 mail_record 提取逻辑代码后映射为 owner，再 `distinctBy(accountCode)`；单账号控制器/队列/UID 回填使用 `AutoMailReplyService` 的统一解析，不能各自另写分支。批量去重的新用例放进 `AutoMailReplyServiceTest.kt`（可在该测试类构造 Batch service），同时运行现有 `BatchAutoMailReplyServiceTest` 回归，不修改后者。
- `AutoMailReplyService.receiveAndAutoReply/processByUids` 用解析后的 `owner.accountCode` 调 `MailInboxCursorService.get/advance`，不能继续用入参 `accountCode`（它可能是 alias）；抓取、`markSeen`、自检 probe、DMARC transfer 的源账号也用 owner。仅在处理普通专家来信时选择逻辑账号。
- `MailRecordRepository.kt` 插入新查询会移动 `OperatorStatusWriteSeamGuardTest.kt:91,94` 的精确行号排除位；仅按实际新行号机械修正这两项，不更改排除文本/规则。该测试纳入定向回归（来源: `CLAUDE.md:70`）。
- 查物理键及严格历史指纹；插入冲突只对已存在同物理键返回 duplicate。不得使用“同一 Message-ID”单独判重，也不得按原邮件 `From` 猜路由。回信匹配 OUTBOUND 的查询只读取已有写路径产生的 `direction='OUTBOUND'` 行。

## 变更文件清单

| # | 文件 | 作用 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReceiveService.kt` | ReceivedMail 原始收件人字段 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt` | 只读解析 To/Cc |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 统一路由/物理去重/owner 游标与 ack |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyService.kt` | 两入口物理 owner 去重 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt` | 物理/严格历史 finder |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt` | OUTBOUND 回信候选查询 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt` | 收信 owner 列表/解析，不碰发信筛选 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt` | 头解析测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 路由/判重/游标/附件源测试 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 仓库查询插入后精确行号排除位修正 |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt` | A1 授权：仅退役被阶段 2 owner-only 收信列表取代的计划 01 断言 |

## 验收标准

- I-1：两个逻辑账号共享 owner，自动检查/联系人检查分别只有一次 owner IMAP 和一次 owner 游标推进；逻辑账号仍进入 INBOUND/processing，附件 source 和 `markSeen` 始终 owner；独立账号依旧按自己账号抓取。
- I-2：测试覆盖直寄 updates、直寄 QF、大小写/显示名、BCC 无 To、To/Cc 同时两个组成员、无法关联/唯一关联 In-Reply-To、重复 OUTBOUND Message-ID；无法唯一判定时只有一条 `MANUAL_REVIEW/RECIPIENT_UNRESOLVED`，零 SMTP、零专家状态迁移。
- I-3：相同 owner/UIDVALIDITY/UID 重放不新增记录；组内旧空 owner 行仅三要素严格吻合才跳过；同 UID 不同 UIDVALIDITY 可新增；并发唯一冲突不会吞无关异常；0 代际维持人工分支。
- I-4：处理或附件登记失败时 IMAP 未标已读且游标不推进；成功/确认重复后 owner 游标前进；alias 旧 cursor 不变。运行 `ImapMailReceiveServiceTest`、`AutoMailReplyServiceTest`、现有 `BatchAutoMailReplyServiceTest`、`OperatorStatusWriteSeamGuardTest`；新增批量场景位于 Auto 测试文件。
- 跨路径：收信入 processing → `MailboxConversationRepository` 读到逻辑账号且计数为 1；附件 transfer → 后续用 owner 登录抓取。

## 人工验收清单

### A-1：直寄别名只出现一次
- 前置条件：测试环境有 `Owner` 和 `Alias`，后者归属前者；自动回复关闭；保存两者发送地址。
- 操作步骤：给 Alias 发一封新邮件→点“检查回复”两次→打开该专家邮箱会话。
- 预期结果：会话只出现 1 条新收件，账号为 `Alias`；第二次检查新增数为 0；`Owner` 与 `Alias` 的发信地址不互换。
- 覆盖：I-1、I-2、I-3、收件→会话跨路径。

### A-2：不能判定收件人
- 前置条件：测试邮箱中有一封 BCC 到该物理邮箱、To/Cc 不含本组账号、In-Reply-To 不匹配既有发信记录的邮件。
- 操作步骤：点“检查回复”→打开待处理列表。
- 预期结果：仅 1 条 `MANUAL_REVIEW`，原因 `RECIPIENT_UNRESOLVED`、显示 owner 账号；没有任何自动回复，专家状态不变。
- 覆盖：I-2、I-4。

### A-3：回信头与附件
- 前置条件：测试环境有从 Alias 发出的 OUTBOUND Message-ID；准备回信的 To/Cc 不含组成员、In-Reply-To 唯一引用它，并带 1 个附件。
- 操作步骤：收信→打开会话→在材料页触发附件下载。
- 预期结果：会话账号为 Alias、收件数增加 1；附件有 1 条，不发生“源邮箱找不到”错误；未新建 Owner 的专家回复。
- 覆盖：I-1、I-2、附件跨路径。

### A-4：独立账号与批量入口回归
- 前置条件：测试环境另有不关联 owner 的 `Independent` 账号，三个账号各有一封新信。
- 操作步骤：执行“全部检查”→再针对同时关联 Owner/Alias 的专家执行“指定联系人检查”。
- 预期结果：第一次物理收件箱轮询数为 2（Owner、Independent），非 3；第二次 Owner 只被轮询 1 次；Independent 的来信继续标记为 Independent，原 SMTP 身份不变。
- 覆盖：I-1、I-4、独立账号回归。
