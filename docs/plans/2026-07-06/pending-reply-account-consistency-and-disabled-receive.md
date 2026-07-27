# 待处理回复账号一致性与禁用账号接收语义修复计划

## 需求描述

人工处理待回复邮件时，系统默认必须沿用该入站邮件所属的发信账号，不得因为账号余量策略静默切换到另一个账号。`mail_sender_account.enabled=false` 的语义调整为“禁止自动外发”，不再阻止 IMAP 接收、退信收集、人工发送和收发信箱可见性。

必须不改变：
- 自动批量外联、自动会议邀请、自动 QA 回复的可发送账号筛选仍不得使用 `enabled=false` 账号。
- `SIMULATOR_NOOP` 仍不能参与真实接收、真实发送、退信收集、收发信箱统计。
- 人工回复的显式账号选择仍允许覆盖默认账号。
- 现有数据库表结构不变，不新增字段、不写迁移。

本计划不包含：
- 修改账号管理页文案，将“启用/禁用”改成“自动发送启用/停用”。
- 修复 SMTP 邮件头 `In-Reply-To` / `References`。
- 回填历史错误账号的 `mail_record.sender_account_code`。
- 调整 daily quota、warmup、Postmaster reputation 自动暂停策略。

## 关键不变量

### Invariant I-1: pending 回复默认沿用入站账号
- Rule: `PendingMailOperationService` 的待处理来信回复路径在 `senderAccountCode` 为空时，必须使用 `InboundMailProcessing.senderAccountCode`，不能调用 `selectAccountForManualSending()` 重新选号。
- Applies to: `sendQaReply`、`sendManualRichReply`、`sendManualComposedReply`。
- Violation consequence: 同一专家线程从最初发件账号切到余量最高账号，导致 Gmail 收件/线程混乱和运营身份不一致。
- 来源: original

### Invariant I-2: 显式账号选择优先
- Rule: 如果请求体传入非空 `senderAccountCode`，待处理回复应使用该账号；该账号只需满足人工发送可用规则，不要求 `enabled=true`。
- Applies to: `PendingMailOperationService` 三条待处理回复路径。
- Violation consequence: 前端未来添加账号选择器后无法覆盖默认账号，或禁用账号无法人工补发。
- 来源: original

### Invariant I-3: enabled=false 只禁止自动外发
- Rule: `enabled=false` 账号仍可 IMAP 接收、退信收集、收发信箱展示、待处理队列统计、人工发送；但任何 `triggered_by=SYSTEM` 的真实外发必须在发送前被拦截并转人工/跳过。
- Applies to: `MailSenderAccountService`、`AutoMailReplyService`、`MailboxService`、`UnmatchedInboundMailService`、`AutoReplyPreviewService`。
- Violation consequence: 账号禁用后漏收专家回复，或反过来 disabled 账号继续自动回复专家。
- 来源: original + K-operator-send-quota-paths

### Invariant I-4: 自动发送筛选仍保守
- Rule: `selectAccountForSending()`、`listSendableAccounts()`、`isSendable()`、批量初始外联和自动会议邀请入口仍必须排除 `enabled=false`、`auto_send_paused=true`、超额、模拟器账号。
- Applies to: `MailSenderAccountService`、`ManualInitialOutreachService` 间接读路径、`MeetingScheduleService` 间接读路径、`SenderAccountAssignmentService` 既有路径。
- Violation consequence: 禁用账号被批量外联或系统任务继续自动发信。
- 来源: K-operator-send-quota-paths

### Invariant I-5: 模拟器账号永远隔离真实收发
- Rule: `SIMULATOR_NOOP` 继续被 `getReceiveAccount`、`getManualSendAccount`、`listAutoReceiveAccounts`、`selectAccountForManualSending`、`selectAccountForSending` 排除或拒绝。
- Applies to: 所有账号解析/列表方法。
- Violation consequence: 模拟器账号进入真实 SMTP/IMAP 路径。
- 来源: original

## 现状审计

### Store: `mail_sender_account`
- Schema/mapping:
  - `MailSenderAccount` 字段包括 `enabled`、`autoSendPaused`、`strategyWeight`、`dailySendLimit`、`todaySentCount`、SMTP/IMAP 凭据。
  - `V1__create_business_tables.sql` 定义 `enabled TINYINT(1) NOT NULL DEFAULT 1`。
  - `V28__add_sender_account_auto_pause.sql` 增加 `auto_send_paused`，该字段比 `enabled` 更接近“临时禁止自动发送”语义。
- Write paths:
  1. `MailSenderAccountService.createAccount` — 新账号默认保存为 `enabled=false`，当前语义下会连接收都不可用。
  2. `MailSenderAccountService.updateAccount` — 管理页更新 `enabled`、SMTP/IMAP、额度等。
  3. `MailSenderAccountService.setEnabled` — 启用时要求连通性检查，停用时驱逐 SMTP sender cache。
  4. `MailSenderAccountService.pauseAutoSend/resumeAutoSend` — 写 `auto_send_paused`，只应影响自动发送。
  5. `MailSenderAccountRepository.incrementTodaySentCount` — 自动/批量发送成功后可能自增额度计数。
  6. `resetDailyCountsBeforeDate/resumeDailyLimitPausedAccounts` — 重置计数/解除每日额度暂停，仅处理 `enabled=1` 行。
- Read paths:
  1. `MailSenderAccountService.getEnabledAccount` — 当前显式账号发送/模板渲染要求 `enabled=true`。
  2. `listAutoReceiveAccounts` / `getAutoReceiveAccount` — 当前接收也要求 `enabled=true`。
  3. `selectAccountForSending` / `listSendableAccounts` — 自动发送保守筛选，要求 `enabled=true`。
  4. `selectAccountForManualSending` — 当前只从 enabled 账号选，并排除 `autoSendPaused`，导致手动发送不符合新语义。
  5. `MailboxService.listMailbox` — 当前只展示 enabled 账号的收发信。
  6. `UnmatchedInboundMailService.listManualReviewQueue` — 当前角标统计只统计 enabled 账号。
  7. `AutoReplyPreviewService.preview` — 会议邀请预览用 `getEnabledAccount(record.senderAccountCode)`，disabled 账号会预览失败。
- Interaction points:
  - `enabled` 写入后影响接收任务、待处理角标、收发信箱、人工回复、自动回复预览和自动发送；本计划必须分清“自动发送筛选”和“接收/人工路径”。

### Store: `inbound_mail_processing`
- Schema/mapping:
  - `InboundMailProcessing.senderAccountCode` 非空，表示这封入站邮件是从哪个账号 IMAP 拉取的。
  - `V5__create_inbound_mail_processing.sql` 对 `(sender_account_code, imap_uid)` 建唯一键，是入站处理去重真相源。
- Write paths:
  1. `AutoMailReplyService.confirmProcessed` — 写入 PROCESSED/MANUAL_REVIEW 入站处理记录。
  2. `AutoMailReplyService.confirmManualReviewWithBody` — 手工审核入站写入点，委托入站处理表保存。
- Read paths:
  1. `PendingMailOperationService` 三条发送方法读取 `inboundProcessingId`，当前没有使用 `record.senderAccountCode` 解析发信账号。
  2. `UnmatchedInboundMailController` 详情返回 `record.senderAccountCode`，前端已能显示“邮箱账号”。
  3. `UnmatchedInboundMailService` 按账号集合统计角标。
- Interaction points:
  - `InboundMailProcessing.senderAccountCode` 是 pending 回复默认发件账号的权威来源；不得从 `mail_record` message-id 字符串反查。

### Store: `mail_record`
- Schema/mapping:
  - `mail_record.sender_account_code` 记录 INBOUND/OUTBOUND 所属账号。
  - `mail_record.source_inbound_id` 可把 OUTBOUND 回复指回入站处理记录，但当前 pending 人工回复路径仍写 `null`。
- Write paths:
  1. `AutoMailReplyService.saveMailRecord` — 入站回复记录，`senderAccountCode=account.accountCode`。
  2. `AutoMailReplyService` QA 自动回复 — OUTBOUND `QA_REPLY`，`triggeredBy=SYSTEM`。
  3. `AutoMailReplyService.sendMeetingInvitation` — OUTBOUND `MEETING_INVITATION`，`triggeredBy=SYSTEM`。
  4. `PendingMailOperationService.sendQaReply` — OUTBOUND `MANUAL_QA_REPLY`，`triggeredBy=OPERATOR`。
  5. `PendingMailOperationService.sendManualRichReply` — OUTBOUND `MANUAL_RICH_REPLY`，`triggeredBy=OPERATOR`。
  6. `PendingMailOperationService.sendManualComposedReply` — OUTBOUND `MANUAL_COMPOSED_REPLY`，`triggeredBy=OPERATOR`。
  7. `ManualExpertMailService.sendManualMail` — 专家详情页手动模板发送，`triggeredBy=OPERATOR`。
  8. `ManualInitialOutreachService` / `ManualOutreachTxHelper` — 批量初始外联。
  9. `MeetingScheduleService.confirmSchedule` — 系统会议流程发送。
- Read paths:
  1. `MailboxService.listMailbox` 查询 `sender_account_code IN (:accountCodes)`。
  2. `BatchAutoMailReplyService.receiveAndAutoReplyForContacts` 通过 `findDistinctSenderAccountCodesByExpertContactIds` 找联系人关联账号。
  3. `MailMonitoringService` 汇总外发/回复/账号健康。
- Interaction points:
  - pending 回复错误选择账号会直接污染 OUTBOUND `sender_account_code`，后续监控和专家线程都显示不一致。
  - disabled 账号若继续被 `MailboxService` 过滤，已接收的 `mail_record` 和 `inbound_mail_processing` 会不可见。

## 实现方案

### Phase 1: 拆分账号用途 API
- 修改文件:
  - `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailSenderAccountRepository.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt`
- 任务:
  1. 在 repository 增加 `findAllByAccountCodeNot(accountCode: String): List<MailSenderAccount>`，保留既有 `findAllByEnabledTrue*` 给自动发送路径用。
  2. 在 service 新增 `getReceiveAccount(accountCode)`：读取任意账号，拒绝不存在和 `SIMULATOR_NOOP`，不检查 `enabled`。
  3. 保留 `getAutoReceiveAccount(accountCode)` 作为兼容别名或内部委托，但语义改成接收账号；若重命名调用点，旧方法可暂留以降低改动面。
  4. 新增 `getManualSendAccount(accountCode)`：读取任意账号，拒绝不存在和 `SIMULATOR_NOOP`，不检查 `enabled`、`autoSendPaused`、额度、预热。
  5. 修改 `listAutoReceiveAccounts()` 使用 `findAllByAccountCodeNot(SIMULATOR_NOOP)`，使 disabled 账号继续被 IMAP 轮询。
  6. 修改 `selectAccountForManualSending()` 使用非模拟器账号集合并调用人工发送判定；人工判定只排除 `SIMULATOR_NOOP`，不排除 `enabled=false` 或 `autoSendPaused=true`。
  7. 保持 `selectAccountForSending()` / `listSendableAccounts()` / `isSendable()` 不变，继续只从 enabled 且可自动发送账号中选。
- 覆盖不变量: I-3, I-4, I-5

### Phase 2: pending 回复沿用入站账号
- 修改文件:
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- 任务:
  1. 新增私有方法 `resolvePendingReplyAccount(requestedAccountCode, inboundSenderAccountCode)`。
  2. 解析规则为 `requestedAccountCode.takeIf { notBlank } ?: inboundSenderAccountCode`。
  3. 用 `mailSenderAccountService.getManualSendAccount(resolvedCode)` 取账号。
  4. 替换 `sendQaReply`、`sendManualRichReply`、`sendManualComposedReply` 中 `getEnabledAccount(...) ?: selectAccountForManualSending()` 的旧逻辑。
  5. 不修改前端 `senderAccountCode: null` 也能正确工作；空值自动映射入站账号。
- 覆盖不变量: I-1, I-2, I-3, I-5

### Phase 3: disabled 账号只收不自动发
- 修改文件:
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
- 任务:
  1. 允许 `receiveAndAutoReply()` / `processByUids()` 获取 disabled 接收账号。
  2. 在 `processSingle` 已保存入站 `mail_record`、附件、intent、unsubscribe 后，在任何 `mailDeliveryService.send` 之前检查 `!account.enabled`。
  3. 若账号 disabled，调用现有 `markManualReview` / `confirmManualReviewWithBody` 路径转人工，reason 建议使用 `ACCOUNT_AUTO_SEND_DISABLED`，outcome 可复用 `MANUAL_REVIEW_BY_INTENT`，避免新增枚举扩散。
  4. 该拦截必须早于 `SEND_MEETING_INVITATION` 和 QA 自动回复实际发送点。
  5. 退信、DMARC、自检探针处理不属于自动外发，仍允许 disabled 账号执行。
- 覆盖不变量: I-3, I-4, I-5

### Phase 4: 可见性和预览一致
- 修改文件:
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt`
- 任务:
  1. `MailboxService.listMailbox` 改用非模拟器账号集合，disabled 账号历史和新入站邮件仍可见。
  2. `UnmatchedInboundMailService.listManualReviewQueue` 的角标统计改用非模拟器账号集合，disabled 账号的待处理邮件不消失。
  3. `AutoReplyPreviewService` 会议邀请模板渲染改用 `getReceiveAccount` 或 `getManualSendAccount`，disabled 账号可预览。
  4. `AutoReplyPreviewService.buildWouldBeBlockedBy` 增加账号级阻断：当 `record.senderAccountCode` 对应账号 `enabled=false` 时追加 `ACCOUNT_AUTO_SEND_DISABLED`。预览仍可展示“如果允许自动发会发什么”，但会明确标记实际会被阻断。
- 覆盖不变量: I-3, I-5

### Phase 5: 核心测试
- 修改文件:
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`
- 任务:
  1. `MailSenderAccountServiceTest`:
     - `listAutoReceiveAccounts` 包含 disabled 非模拟器账号。
     - `getReceiveAccount` / `getManualSendAccount` 允许 disabled 非模拟器账号。
     - `selectAccountForSending` 仍排除 disabled。
     - `selectAccountForManualSending` 允许 disabled/auto-paused，但排除模拟器。
  2. `PendingMailOperationServiceTest`:
     - 入站 `senderAccountCode=LiLei`，`senderAccountCode=null` 时人工富文本回复保存/发送都用 `LiLei`。
     - 显式请求 `senderAccountCode=LuKai` 时才覆盖。
     - `sendQaReply` 和 `sendManualComposedReply` 同样沿用入站账号。
     - disabled 入站账号仍可人工发送。
  3. `AutoMailReplyServiceTest`:
     - disabled 账号能拉取并保存入站记录。
     - QA 可匹配时不调用 `mailDeliveryService.send`，而是写 `MANUAL_REVIEW`，reason 为 `ACCOUNT_AUTO_SEND_DISABLED`。
     - meeting invitation 可触发时同样不发送，转人工。
- 覆盖不变量: I-1, I-2, I-3, I-4, I-5

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailSenderAccountRepository.kt` | 修改 | 增加非模拟器账号列表查询 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt` | 修改 | 拆分接收、人工发送、自动发送账号语义 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 修改 | pending 回复默认沿用入站账号 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 修改 | disabled 账号接收后阻断自动外发并转人工 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt` | 修改 | 收发信箱包含 disabled 非模拟器账号 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt` | 修改 | 待处理角标统计包含 disabled 非模拟器账号 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt` | 修改 | disabled 账号预览可渲染且显示阻断原因 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt` | 修改 | 账号用途 API 语义测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 修改 | pending 回复账号一致性测试 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | 修改 | disabled 账号只收不自动发测试 |

## 验收标准

- I-1: 构造 `InboundMailProcessing(senderAccountCode="LiLei")`，调用三条 pending 回复且请求账号为空，断言 `mailDeliveryService.send` 使用 `LiLei`，保存的 `mail_record.sender_account_code="LiLei"`。
- I-2: 构造同一入站记录，请求显式传 `LuKai`，断言发送和保存均使用 `LuKai`。
- I-3: 构造 disabled 账号轮询入站，断言入站 `mail_record` 和 `inbound_mail_processing` 写入成功；QA/会议自动回复路径不调用 SMTP，入站进入 `MANUAL_REVIEW`，reason 为 `ACCOUNT_AUTO_SEND_DISABLED`。
- I-4: 构造 disabled + enabled 两账号，断言 `selectAccountForSending()` / `listSendableAccounts()` 不返回 disabled；批量自动外联间接依赖此结果。
- I-5: 构造 `SIMULATOR_NOOP`，断言接收账号、人工发送账号、自动发送选择都拒绝或排除它。
- 收发信箱集成: disabled 账号下已有 `mail_record` / `inbound_mail_processing`，列表查询应返回；显式筛选该账号不返回空。
- 待处理队列集成: disabled 账号下 `MANUAL_REVIEW` 记录应计入角标和 reason 分组。
- 预览集成: disabled 账号下 QA/会议预览仍展示候选正文，同时 `wouldBeBlockedBy` 包含 `ACCOUNT_AUTO_SEND_DISABLED`。

建议验证命令：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailSenderAccountServiceTest,PendingMailOperationServiceTest,AutoMailReplyServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
```

## 修正记录

| 日期 | 修正计划 | 原因 | 状态 |
|---|---|---|---|
| 2026-07-06 | `docs/plans/fix/pending-reply-account-consistency-and-disabled-receive/fix-1.md` | 复验发现专家详情手动发送入口 `ManualExpertMailService` 仍使用 `getEnabledAccount`，不满足 disabled 账号可手动发送 | 待执行 |

## 自审清单

- [x] 关键不变量 section exists and has >=1 invariant per new state/semantic.
- [x] 现状审计 lists write/read paths for touched stores.
- [x] No task introduces a write path that is not covered by an invariant.
- [x] File count <= 10.
- [x] Subsystem count <= 2: mail account semantics + pending/auto reply send paths.
- [x] Each task references governing invariants.
- [x] 验收标准 has at least one check per invariant.
- [x] No unbounded "related files" in change list.
- [x] Out-of-scope section defers UI wording, SMTP thread headers, and historical backfill.
- [x] Knowledge entries K-operator-send-quota-paths and K-mail-record-source-inbound-id were used in audit.
