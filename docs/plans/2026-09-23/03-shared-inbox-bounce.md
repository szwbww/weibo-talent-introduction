# 共享收件箱机器邮件过滤与退信归属开发计划（3/4）

> 上位约束：[MAIN 总计划](00-shared-inbox-main.md)。依赖 01、02；必须在生产设置任何非空 `inbound_mailbox_code` 前完成。范围限于退信归属与 `[self-check]` 内部探针过滤，不改退信判定器或探针发送格式。

**目标**：同物理邮箱收到的退信按原始 OUTBOUND 的逻辑发件账号统计；同组内账号发出的自检探针不再进入待处理；两类机器邮件均不得被误认成专家回复。前两阶段通过后，04 才能迁移现有 QF。

**技术栈**：Kotlin、Spring Data JDBC、MySQL、现有 BounceCollector。

## 需求描述

- 可观察结果：Alias 发出的信退到 Owner 收件箱后，退信列表和硬退信率显示 Alias；Owner 自己发出的信仍显示 Owner。`[self-check] LuKai 1788498000276` 同类新探针被确认但不产生待处理或业务邮件；旧待处理由 04 安全清理。
- 不得改变：退信去重、原始专家匹配、硬退信禁发逻辑、独立账号退信；外部真实邮件即使主题包含 `[self-check]` 也不能误吞。
- 不在范围：重算所有历史退信；扩展 DSN 解析格式；改发信限制算法或自检 TTL/发送格式；本阶段不直接修改历史 processing 行。(来源: `K-self-check-ttl-type-scope`)

## 关键不变量

### I-1：退信归属只凭唯一原始发信
- 规则：`signal.originalMessageId` 经现有标准化候选查 `direction='OUTBOUND'`；仅当唯一候选且其 `sender_account_code` 属于当前物理组，才用它写 `bounce_record.sender_account_code`。否则保持传入归属（共享邮箱自动抓取时为 owner），记录可检索警告，不推断 Alias；绝不把 `failedRecipient` 专家邮箱当成发件账号。
- 适用写路径：`BounceCollectionService.collectBounces/ingest`，`AutoMailReplyService` 收信中退信分支，`BounceBackfillService` 现有 ingest 入口。
- 违反后果：退信计数、暂停和报表落错账号。
- 来源：`BounceCollectionService.kt:33-105,145-155`；`K-sender-account-selection-sites`。

### I-2：退信来源仍是物理邮箱
- 规则：`collectBounces` 继续用 owner 登录抓 unseen；`bounce_message_id` 唯一键与解析专家逻辑不变；只改变确认后的逻辑账号代码。完成一次共享邮箱抓取后，对组内各逻辑账号执行既有硬退信率检测，不能只检测 owner。
- 适用写路径：`BounceCollectionService`、`AutoMailReplyService.kt:807,876-885`。
- 违反后果：重复记录或 Alias 的高退信率不报警。
- 来源：`V29__create_bounce_record.sql`、`BounceRateMonitorService.kt:21`。

### I-3：自检探针只按同组发件身份识别
- 规则：探针必须同时满足：From 精确匹配当前物理组某个账号的 `sender_email`（地址大小写无关）、Subject 是该账号代码对应的完整生成格式 `[self-check] {accountCode} {十进制时间戳}`（保留现有 `[ self - check ]` 标签空格兼容）；只看前缀、只看 From、或匹配其他物理组均不成立。`Re:` 和不同代码、非数字尾巴均不成立。不能因可伪造主题单独丢弃专家邮件。
- 适用路径：`SelfCheckProbeDetector.isSelfCheckProbe`、`AutoMailReplyService.processSingle`，包含自动检查、队列、手动检查和 `processByUids`。
- 违反后果：内部探针进入待处理；或误吞真实专家回信。
- 来源：`SenderAccountSelfCheckService.kt:22-27` 生成格式、`SelfCheckProbeDetector.kt:6-14` 当前只比轮询账号邮箱、线上行 383 证据；`K-process-single-all-callers`。

### I-4：忽略探针不是业务持久化
- 规则：真实正数 UIDVALIDITY 的探针在 `processSingle` 业务事务前返回 `SELF_CHECK_IGNORED`（`recorded=false`，不属于 `MANUAL_REVIEW_OUTCOMES`）；不写 `inbound_mail_processing`、`mail_record`、意图、标签、附件或专家状态；仅在 `skipImapAck=false` 时 `markSeen(owner,uid)`，批量入口将 UID 纳入已处理集合以推进 owner 游标。UIDVALIDITY 缺失、IMAP 确认失败时不得推进游标；UID 回填同样不能落人工行。
- 适用路径：`AutoMailReplyService.processSingle/receiveAndAutoReply/processByUids`。
- 违反后果：旧待处理重现、漏信或错标已读。
- 来源：`AutoMailReplyService.kt:87-109,795-805,896-907,1378-1438`、`K-inbound-seen-not-processed-marker`、`K-imap-source-vs-business-account`。

## 现状审计

### `bounce_record`
- Schema：`V29__create_bounce_record.sql:1-16` 非空 `sender_account_code`、唯一 `bounce_message_id`、原始 Message-ID/联系人及退信类型；无需新字段。
- 写路径：`BounceCollectionService.kt:33-105` 拉 unseen 并调用 `ingest` 保存；`AutoMailReplyService.kt:804-818` 在 UID 轮询中直接调用同一 ingest；`BounceBackfillService.kt:30-44` 按已有 processing 再调用 ingest。三条路须共用同一归属解析。
- 读路径：`BounceController.kt:19-34` 列表；`BounceRateMonitorService.kt:21` 按账号统计；`MailMonitoringService.kt:270-303` 报表；`OperatorStatusReconcileService.kt:57` 状态重算；`MailRecordRepository.kt:440` 关联查询。
- 交互点：OUTBOUND mail_record→退信解析→bounce_record→列表/监控。`AutoMailReplyService.kt:876-885` 目前只对轮询账号做退信率检查。

### `mail_record` 与物理组
- Schema：`V15__add_mail_monitoring_columns_and_promotion_audit.sql:3-15` 给 mail_record 加 `sender_account_code`；02 已增加限制 OUTBOUND 的只读查询。
- 写路径：`AutoMailReplyService.kt:696` 自动发信，`ManualReplySendAttemptService.kt:397,496` 手动回复，`ManualExpertMailService.kt:70` 专家邮件，`ManualOutreachTxHelper.kt:59,110` 外联，`MeetingScheduleService.kt:144` 会议邮件；均产生现有发件归属，具体 `direction` 由各自写入对象决定，退信查询只取 OUTBOUND。
- 读路径：`BounceCollectionService.kt:145-155` 当前 `findByMessageId` 没有方向限制；02 新查询返回 OUTBOUND 候选列表。`MailSenderAccountService.listAccounts` 提供当前物理组成员。
- 交互点：严格 OUTBOUND 归属→退信行；多候选/无候选→传入 owner，不使用非 OUTBOUND 行。

### 自检发送、过滤与 `inbound_mail_processing`
- Schema：`V5__create_inbound_mail_processing.sql:1-20` 有 `process_status/process_reason`；V120 以逻辑账号+UIDVALIDITY+UID 去重；01 再加物理 owner 唯一键。普通来信可能写此表，探针不应写。
- 写路径：`SenderAccountSelfCheckService.DefaultSelfCheckProbeSender.sendProbe` 以账号自己的 From/To 发送固定正文，Subject 为 `[self-check] {accountCode} {System.currentTimeMillis()}`；`AutoMailReplyService.kt:1231,1284` 新建 processing，`UnmatchedInboundMailService.kt:193,238` 与 `PendingMailOperationService.kt:1522` copy/save，repository `reopenManualResolved` 条件 UPDATE；探针须在两个新建 sink 前截断。(来源: `K-inbound-processing-write-paths`)
- 读路径：`AutoMailReplyService.kt:795-805` 当前在批量入口先用 `SelfCheckProbeDetector` 过滤，只传当前轮询账号邮箱；`processByUids:896-907` 直接调 `processSingle`，所以这一路会绕过现有探针过滤；待处理列表与 `MailboxConversationRepository.kt:433-495` 从 processing 读取。(来源: `K-process-single-all-callers`、`K-mailbox-inbound-source-authority`)
- 交互点：探针生成 From/Subject→同组识别→不落 processing→待处理/会话均无新行；确认成功→owner 游标前进。
- 线上只读证明：`inbound_mail_processing.id=383` 为 `sender_account_code=LuKai_QF`、`uid_validity=1782107786`、`imap_uid=186`、From=`lukai@updates.szwebotech.cn`、Subject=`[self-check] LuKai 1788498000276`、`MANUAL_REVIEW/CONTACT_NOT_FOUND`、无专家。当前探针过滤比较的是 QF 邮箱，故失败。只读 SQL `SELECT sender_account_code,process_status,COUNT(*) FROM inbound_mail_processing WHERE subject LIKE '[self-check]%' GROUP BY sender_account_code,process_status ORDER BY sender_account_code,process_status` 的 QF 两行输出是 `MANUAL_REVIEW 23`、`PROCESSED 2`；对 23 条待处理按 ID 关联查询专家/标签/附件/transfer/操作，汇总均为 0。运行前必须重新核验，不能仅按主题批删。

## 实现方案

### 阶段 1：失败测试（I-1、I-2）
- 文件：`BounceCollectionServiceTest.kt`、`AutoMailReplyServiceTest.kt`（完整路径见清单）。构造 Owner、Alias 同组及各自 OUTBOUND；同一 DSN 经 `collectBounces` 与轮询直接 ingest 两条入口，断言 Alias/Owner 正确；构造无 Message-ID、多候选、INBOUND 同 ID，断言不猜 Alias。断言仍只落一条 `bounce_message_id`、专家匹配/硬退信副作用不变。轮询后监测两账号各一次。

### 阶段 2：共用归属解析（I-1、I-2）
- 文件：`BounceCollectionService.kt`、`AutoMailReplyService.kt`。
- 将唯一 OUTBOUND→本组成员校验放入 `BounceCollectionService.ingest`，因此三类调用者自动使用；传入代码为已知逻辑账号的历史回填时保留该代码，物理抓取时传 owner。改原始专家关联读取为 OUTBOUND-only 候选，保持 `failedRecipient` 查专家的旧后备，但不拿它猜发信账号。`AutoMailReplyService` 轮询后按组成员执行 `BounceRateMonitorService.checkAndWarn`，不增加 IMAP 请求。
- 新退信行由 `BounceController`、`BounceRateMonitorService`、`MailMonitoringService` 现有按账号读取；不改这些读取代码。

### 阶段 3：同组 self-check 过滤（I-3、I-4）
- 文件：`SelfCheckProbeDetector.kt`、`SelfCheckProbeDetectorTest.kt`、`AutoMailReplyService.kt`、`AutoMailReplyServiceTest.kt`。
- 先写失败用例：Owner 自检从 Alias 旧轮询视角进入、Alias 自检由 Owner 物理收件箱抓取、UID 回填直调 `processSingle`、外部同主题/错误账号代码/`Re:` 不误吞、`skipImapAck=true` 不标已读、UIDVALIDITY=0 不确认。再将识别移至 `processSingle` 唯一业务入口，位于路由/正文与附件处理之前；批量入口不再保留一份只比当前账号的独立分支。组成员读取 02 已有的 `MailSenderAccountService.listAccounts` 与 `inbound_mailbox_code`，不新增持久化字段。
- 匹配成功返回新的非业务结果 `SELF_CHECK_IGNORED` 并按 I-4 确认；正常批量分支仍把该 UID 加入 handled 集合。对外部真实信继续沿 02 路由/人工逻辑，绝不凭主题前缀跳过。

## 变更文件清单

| # | 文件 | 作用 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt` | 共用 OUTBOUND 归属解析 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 全组退信率检查和探针统一入口 |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionServiceTest.kt` | 三类归属与去重测试 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 轮询/监测/探针两入口覆盖 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SelfCheckProbeDetector.kt` | 同组账号严格匹配 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/SelfCheckProbeDetectorTest.kt` | 真探针/伪装邮件边界测试 |

## 验收标准

- I-1：唯一 Alias/Owner OUTBOUND 分别写对应账号；无匹配、多匹配、仅 INBOUND 命中均保持传入 owner；`BounceBackfillService` 已知逻辑账号不被改错。
- I-2：`bounce_message_id` 第二次仍去重；owner 只登录一次；组内各账号调用一次硬退信率检查；原始专家关联和 EMAIL_INVALID 现有测试通过。
- I-3：Owner/任意 Alias 的自检 From+完整 Subject 均被识别；跨物理组、错代码、`Re:`、非数字尾巴或仅主题前缀不识别。新旧测试通过。
- I-4：批量、队列转入的统一收信入口以及 UID 回填均返回 `SELF_CHECK_IGNORED`、`recorded=false`；`inbound_mail_processing`、`mail_record`、`inbound_intent`、`inbound_mail_tag`、`mail_attachment` 写入次数均为 0；仅 owner `markSeen` 与连续游标前进；`skipImapAck`/无正数 UIDVALIDITY 不确认。现有专家真实信回归通过。
- 跨路径：模拟真实 DSN→`BounceController` 按 Alias 列出 1 条→`BounceRateMonitorService` Alias 硬退信数增加 1、Owner 不增加。

## 人工验收清单

### A-1：Alias 退信归属
- 前置条件：测试环境 Owner/Alias 共用收件箱，Alias 已发一封可追溯 Message-ID 的邮件；准备一封引用该 ID 的 DSN。
- 操作步骤：投递 DSN→执行“检查回复”→在退信列表分别筛 Alias 和 Owner。
- 预期结果：Alias 列表增加 1 条 HARD/SOFT（以 DSN 类型为准），Owner 增加 0 条；再次检查均不再增加。
- 覆盖：I-1、I-2、OUTBOUND→退信→列表跨路径。

### A-2：无法证明原始发件账号
- 前置条件：测试环境有一封缺原始 Message-ID 的 DSN，且未有相同 `bounce_message_id` 记录。
- 操作步骤：执行检查→分别筛 Owner/Alias 退信列表。
- 预期结果：Owner 增加 1 条，Alias 增加 0 条；日志出现“归属未解析”警告，不出现自动发送。
- 覆盖：I-1。

### A-3：独立账号和硬退信副作用回归
- 前置条件：另有独立账号 Independent，准备一封带其 OUTBOUND ID 的 HARD DSN，并有一名可匹配的专家。
- 操作步骤：检查 Independent 收件箱→查看退信列表、专家邮箱状态和硬退信监控。
- 预期结果：Independent 增加 1 条 HARD 记录、该专家标为 EMAIL_INVALID、Owner/Alias 各增加 0 条；共享组检查后 Owner 与 Alias 的退信监控都刷新。
- 覆盖：I-2、既有行为回归。

### A-4：同组探针不进待处理
- 前置条件：测试环境 Owner/Alias 共用 INBOX，自动回复关闭，两个账号各能发送自检探针；记录待处理数。
- 操作步骤：分别触发两个账号的“自检”→执行“检查回复”→用对应 UID 执行一次回填→刷新待处理和会话列表。
- 预期结果：两封探针都不新增待处理、会话收件、INBOUND/OUTBOUND 记录；待处理数较操作前增加 0，第二次检查新增数为 0。
- 覆盖：I-3、I-4、探针发送→收信/回填→会话跨路径。

### A-5：外部同主题邮件不误吞
- 前置条件：从外部测试邮箱给 Alias 发一封主题为 `[self-check] LuKai 1788498000276` 的邮件，From 不是本组任何发件邮箱。
- 操作步骤：执行“检查回复”→在专家会话或未匹配待处理查找该主题。
- 预期结果：该邮件产生 1 条正常来信或未匹配人工行，不显示 `SELF_CHECK_IGNORED`，没有因主题前缀被丢弃。
- 覆盖：I-3、真实来信回归。
