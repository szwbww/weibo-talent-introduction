# 已发信专家自由回信开发计划

> 状态：待人工批准；本文件只定义实施范围，不包含业务代码修改。

## 需求描述

邮件聊天页中，只要专家满足以下任一条件，就显示并允许使用现有“人工回复”富文本编辑器：

1. 存在已绑定来信，不论该来信的 `processStatus` 是 `MANUAL_REVIEW`、`PROCESSED` 或其他状态；
2. 不存在来信，但至少存在一封成功发出的邮件（`mail_record.direction='OUTBOUND' AND send_status='SENT'`）。

无来信的回信由服务端在点击发送时选择该专家最近一封成功发件作为真实锚点，使用锚点的发件账号发送；不能伪造 `inbound_mail_processing.id`。无成功发件、仅有失败发件的专家不开放自由回信，继续保留模板跟进入口。

范围结论：最多修改 10 个既有文件（6 个生产文件、4 个测试文件），仅 2 个相互配合的子系统（后端发送链、邮件聊天前端），新增 1 个 HTTP 接口、1 个仓库查询；不新增表、字段、迁移、页面、邮件类型或操作类型。

### 明确不做

- 不改变待处理列表、`pendingCount`、`waitingReply`、`MANUAL_REVIEW` 状态流转或“标记已处理”逻辑。
- 不改可信回复工作台；无来信时仍不可生成可信回复。
- 不把自由正文塞进 `ManualExpertMailService` 的模板命令，也不扩展 `ManualMailOptionType`。
- 不允许前端指定或伪造线程锚点、发件账号、`processingId`。
- 不补发历史邮件，不修改历史 `mail_record`，不新增数据库迁移。
- 不改现有来信回复接口 `/api/mail/unmatched-inbound/{id}/manual-rich-reply` 的请求/响应契约。

## 关键不变量

### Invariant I-1: 资格只认真实成功发件
- Rule: 无来信路径必须由服务端查询真实 `SENT` 出站记录；`FAILED`、空状态、空账号、模拟器账号均不能成为锚点；聊天页有账号筛选时锚点必须位于该账号范围。
- Applies to: `MailRecordRepository.findLatestSentOutboundAnchor`、`PendingMailOperationService.sendConversationManualRichReply`、前端三态展示。
- Violation consequence: 仅失败或模拟发送的专家会被错误开放真实外发。
- 来源: original

### Invariant I-2: 待处理状态不是回信门禁
- Rule: 已绑定来信只要求存在真实 `processingId`；不得要求 `processStatus == MANUAL_REVIEW`。`PROCESSED` 来信必须继续可回。
- Applies to: 既有 `sendManualRichReply`、`renderManualSectionInto`、回归测试。
- Violation consequence: 已处理会话无法继续沟通，直接违背需求。
- 来源: original

### Invariant I-3: 线程锚点必须真实
- Rule: 来信路径使用真实 inbound；无来信路径使用服务端查得的真实 `mail_record.id` 做线程/审计锚点，禁止伪造 inbound ID。
- Applies to: source context、SMTP 线程头、`mail_record.in_reply_to`、operator audit。
- Violation consequence: 幂等、线程、审计会指向不存在或错误的来信。
- 来源: K-manual-rich-reply-anchor-must-be-real

### Invariant I-4: 会话请求键与线程锚点分离
- Rule: outbound DTO 必须携带前端生成并随草稿保存的 `requestId`；attempt 短键由该 requestId 派生，真实 `MAIL_RECORD:<id>` 锚点仍进入完整 payload。service 在重查“最新成功发件”前先按 requestId 查已完成 attempt；已 `SENT` 时直接返回原结果，不能因“刚发出的信成为最新锚点”再次投递；用户编辑正文后必须清除旧 requestId。
- Applies to: outbound 草稿、controller DTO、`SendPayload`、`computeFingerprint/prepareAndClaim`、发送成功/失败处理。
- Violation consequence: HTTP 成功响应丢失后重试会产生重复邮件，或编辑后的新正文被旧 attempt 锁死。
- 来源: K-manual-send-unknown-must-converge

### Invariant I-5: 现有来信指纹兼容
- Rule: 未提供 outbound requestId 时，fingerprint 的首段继续是原始 `inboundProcessingId.toString()`，字段顺序和短键算法保持不变。
- Applies to: `ManualReplySendAttemptService.computeFingerprint`、所有现有 inbound 调用与测试。
- Violation consequence: 部署前后的同一来信/正文生成不同 attempt，可能重复发送。
- 来源: K-manual-send-fingerprint-complete-identity

### Invariant I-6: 发件账号沿用锚点
- Rule: 无来信路径固定使用最近成功发件的 `sender_account_code`，再经过 `getManualSendAccount`。DTO 只允许可空 `accountScope` 约束锚点查询，不能用它直接选一个没有成功发件的账号，也不接收 `senderAccountCode` 覆盖值。
- Applies to: anchor query、service 账号解析、controller DTO、前端 payload。
- Violation consequence: 回信从错误账号发出，线程和收件人预期不一致。
- 来源: K-manual-send-explicit-account-must-match-binding

### Invariant I-7: Message-ID 缺失不取消资格
- Rule: 锚点 Message-ID 只有在 trim 后非空且长度 `<=255` 时进入新记录及 SMTP `In-Reply-To/References`；为空或非法长度时线程头为空但仍发送。
- Applies to: source context、`ComposedMail`、`SendPayload.inReplyTo`。
- Violation consequence: 历史合法成功发件因旧数据缺头而无法继续沟通，或非法头污染 MIME。
- 来源: K-outbound-thread-headers-single-seam

### Invariant I-8: 现有来信语义不漂移
- Rule: inbound 的 QA/RAG/可信 assembly、unsupported-answer 归档、审计字段、fingerprint 和现有 SMTP 头行为逐字保持；本需求不顺带修复它们。
- Applies to: 共同私有发送实现、旧 controller、旧 JS adapter、既有测试。
- Violation consequence: 新增 outbound 能力时破坏已验证的来信回复链。
- 来源: original

### Invariant I-9: 安全与副作用顺序不可旁路
- Rule: 两条路径保持“占位符校验 → 最终渲染 → 最终文本安全检查 → suppression → claim → SMTP → 持久化 → after-commit audit”。outbound 执行通用纯文本风险检查，不运行依赖来信语义的 QA/意图检查。
- Applies to: `PendingMailOperationService` 共同实现、前端二级确认、审计。
- Violation consequence: 未渲染文本或高风险正文可能绕过校验，或失败请求提前占用 attempt。
- 来源: K-manual-rich-render-before-send, K-manual-send-safety-chain-reads-qa-rule

### Invariant I-10: 发送状态必须收敛且响应不泄密
- Rule: 新路径复用既有 SMTP 分类；claim 后的不确定异常必须尽力落为 `DELIVERY_UNKNOWN` 并 fail closed，HTTP 只返回稳定状态/Message-ID，不回显 SMTP 诊断。
- Applies to: 共同 delivery 分支、`finalizeFailure`、controller response。
- Violation consequence: 重试可能重复投递，或服务端认证/SMTP 信息泄露。
- 来源: K-manual-send-unknown-must-converge, K-manual-send-error-response-opaque

### Invariant I-11: 落库与审计关系准确
- Rule: 成功/失败仍写 `MANUAL_RICH_REPLY/OUTBOUND/OPERATOR/source_inbound_id=NULL` 并唯一关联 attempt；outbound audit 使用 `EXPERT_CONTACT`、`inbound_processing_id=NULL` 和真实 `anchorMailRecordId`。
- Applies to: `finalizeSuccess/finalizeFailure`、`recordConversationSendAudit`、聊天日志读取。
- Violation consequence: 会话统计、追责和来源关系失真。
- 来源: K-mail-record-source-inbound-id

### Invariant I-12: 草稿只在确认成功后删除
- Rule: 返回 `true` 才删除当前 draft 和 requestId；确认取消、422/409/503、reject、UNKNOWN 都保留；编辑 outbound 草稿会清除旧 requestId，下一次发送生成新值。
- Applies to: `saveDraftFromInputs`、`onInput`、`sendManualReply`、`afterSuccessfulSend`。
- Violation consequence: 用户正文丢失，或修改后的正文错误复用旧幂等操作。
- 来源: K-frontend-cache-key-triad

## 样式契约

### S-1: 人工回复编辑器
- 复用：`.mc-section/.mc-section-content`（`mailbox-chat.css:53-56,123-130`）、`.mc-compose`（`60`）、`.mc-editor-tools`（`63`）、`.mc-editor`（`64,130`）、`.mc-compose-footer`（`65`）、`.button/.button.primary`（`66-68,147-148`）。禁止新增 class、inline style 或修改这些规则。
- 新增 CSS：无。
- DOM 结构：inbound/outbound 都必须使用下列骨架；outbound 只额外插入已存在的模板按钮，不能新增包装 class。

```html
<details class="mc-section" data-section="manual" open>
  <summary>人工回复</summary>
  <div class="mc-section-content">
    <div class="mc-compose" data-role="manual-compose" data-target-key="...">
      <label>主题<input aria-label="回复主题" value="..."></label>
      <div class="mc-editor-tools">
        <button class="button" type="button" data-action="mc-rich-command" data-command="bold">B</button>
        <button class="button" type="button" data-action="mc-rich-command" data-command="italic">I</button>
        <button class="button" type="button" data-action="mc-rich-command" data-command="insertUnorderedList">列表</button>
        <button class="button" type="button" data-action="mc-rich-command" data-command="createLink">链接</button>
      </div>
      <div class="mc-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="人工回复正文" data-role="mc-editor"></div>
      <div class="mc-compose-footer">
        <span data-role="target-info">回复账号与目标来信信息：...</span>
        <!-- 仅 outbound：复用当前模板入口 -->
        <button class="button" type="button" data-action="mc-template-follow" data-contact-id="...">选择模板发送跟进邮件</button>
        <button class="button primary" type="button" data-action="mc-send-manual">发送人工回复</button>
      </div>
    </div>
  </div>
</details>
```

- 实值基准：section 白底 `#fff`、边框 `#dce4ef`、圆角 `10px`；输入/编辑器白底 `#fff`、文字 `#334155`、边框 `#dce4ef`、圆角 `7px`；编辑器高度 `100..240px`、字号 `12px`、行高 `1.8`；按钮高 `32px`、字号 `12px`、圆角 `7px`；focus outline `2px solid #3b82f6`；disabled opacity `.45`。
- 全部使用位置：本计划不就地修改任何 class；只在 `mailbox-chat.js:2094-2099,2148-2153,2163-2191` 的既有 workbench/manual DOM 中复用。其他使用点不受影响。

### S-2: 不可用说明与工作台回归
- 复用：`.mc-note`（`mailbox-chat.css:57`）和现有 `.button.primary`；禁止新增 class、CSS 或 inline style。
- DOM 结构：`sentCount == 0` 保留当前 `manualFollowUpHtml`；无来信工作台保留以下逐字文案。

```html
<div class="mc-note">暂无专家来信，暂不能生成回复</div>
<div data-role="manual-followup">
  <div class="mc-note">该专家暂无来信。请使用既有模板发送跟进邮件；系统不会在没有真实来信时伪造可生成的人工富文本回复。</div>
  <button class="button primary" type="button" data-action="mc-template-follow" data-contact-id="...">选择模板发送跟进邮件</button>
</div>
```

- 响应式：继续使用 `mailbox-chat.css:69-70,150-151`；桌面/窄屏都不新增断点。

## 现状审计

### 前端门禁证据

- `mailbox-chat.js:2126-2147` 只按 `latestInbound.processingId` 分成 `inbound` / `outboundOnly`；前者调用 `manualComposeHtml`，后者调用 `manualFollowUpHtml`。
- `mailbox-chat.js:2185-2191` 的 `manualFollowUpHtml` 只有说明和模板跟进按钮，因此缺失的是“只有成功发件、没有来信”的自由编辑器。
- `mailbox-chat.js:2672-2675` 的草稿 key 只允许 `mode === 'inbound'`；`2753-2789` 也只调用 `mcHostSendRichReply(processingId, body)`。
- `app.js:14492-14558` 的宿主适配固定 POST `/api/mail/unmatched-inbound/{processingId}/manual-rich-reply`，并已包含普通/强风险二次确认。
- `app.js:14561-14568` 明确把无来信专家送进模板流程；当前没有自由 subject/body 的会话级接口。
- `mailboxChatBehavior.test.js:1874-1889` 反向固定“无来信不显示 `.mc-compose`”，该测试必须按新需求改写。

### “不需要待处理状态”证据

- `MailboxConversationService.kt:152-156` 分别输出 `sentCount`、`pendingCount`；两者不是同一门禁。`waitingReply` 也仅由 `receivedCount == 0 && sentCount > 0` 派生。
- `MailboxConversationRepository.kt:464-467` 只有 `send_status='SENT'` 计入 `sent_flag`；`490` 才按 `process_status='MANUAL_REVIEW'` 计算独立的 `pending_flag`。
- `PendingMailOperationService.kt:143-180` 的 `sendManualRichReply` 读取来信、联系人后直接校验正文，未读取或判断 `record.processStatus`。真正带 `MANUAL_REVIEW` 门禁的是无关的 `markResolved`（`1062`）。
- 因此，已处理来信当前已能自由回信；实现不能误改状态机，只需补齐“仅有成功发件”的链路，并增加回归测试防止以后重新绑定到 pending。

### 数据结构与完整读写路径

#### `mail_record`

- Schema：`V1__create_business_tables.sql:97-115` 创建基础表；`V15__add_mail_monitoring_columns_and_promotion_audit.sql:2-14` 增加可空 `sender_account_code` / `source_inbound_id`；`V23`/`V24` 增加并唯一约束 `mail_send_attempt_id`。现有字段足够，无迁移。
- 新读路径：`MailRecordRepository` 增加按 `expert_contact_id + OUTBOUND + SENT` 读取最近可发送锚点，排序固定为 `COALESCE(sent_at, created_at) DESC, id DESC`，排除空账号和模拟器账号。
- 既有写路径：`ManualReplySendAttemptService.finalizeSuccess/finalizeFailure`（当前 `204-335`）统一写成功/失败 `mail_record`；新路径继续走这里。
- 既有读路径：`MailboxConversationRepository.rangeUnionSql`（`432-473`）读取出站记录形成 summary/timeline；`MailboxConversationService:135-177` 投影 `sentCount/latestMessage`；旧 mailbox、审计和发送结果仍按现有仓库读取。
- 新出站记录 `source_inbound_id` 保持 `NULL`；锚点只进入 `in_reply_to` 和审计 before，不滥用来源字段。
- 全部直接写路径（repository 调用与生产 SQL 双检）：`ManualOutreachTxHelper`（成功/失败人工触达）、`MeetingScheduleService`（会议邮件）、`AutoMailReplyService`（入站归档、自动回信及失败）、`ManualExpertMailService`（模板人工发件）、`ManualReplySendAttemptService`（人工富文本成功/失败）；历史迁移 `V24` 只回填 attempt 关联。没有其他生产直接 SQL 写 mail_record。本计划只改人工富文本路径；其余写入格式不变。
- 全部直接读路径按职责分组：联系人/初始触达上下文（`ExpertContactManagementService`、`ManualInitialOutreachService`、`OperatorStatusReconcileService`）；材料附件（`ExpertDocumentBrowseService`、`ExpertMaterialService`、`AttachmentTransferService`）；AI/训练/RAG（`AiTrainingController`、`AiQaExtractionService`、`TrustReplyWorkbenchService`、`GroundedAutoReplyDecisionService`、`RagReplyController`）；收发判断（`AutoMailReplyService`、`AutoReplyPreviewService`、`AutomaticApplicationPromotionService`、`BounceCollectionService`、`UnmatchedInboundMailService`）；邮箱/监控（`MailboxService`、`MailboxConversationRepository`、`MailMonitoringService`、`BounceRateMonitorService`）；人工发件（`ManualExpertMailService`、`ManualReplySendAttemptService`、`PendingMailOperationService`）。这些读取者只消费既有字段；新行沿用既有 `MANUAL_RICH_REPLY` 口径，无读方字段调整。
- Interaction point：`ManualReplySendAttemptService` 写 `SENT/FAILED mail_record` → `MailboxConversationRepository` 将下一次 summary/timeline 读到；新 anchor query 只读这些既有成功行。`source_inbound_id=NULL` 防止其他来源读取者把 outbound follow-up 误配成 inbound 来源。（来源: K-mail-record-source-inbound-id）

#### `mail_send_attempt`

- Schema：`V23__create_mail_send_attempt_and_add_mail_record_error.sql:1-17` 创建尝试表；`V24__extend_mail_send_attempt_state_and_link_mail_record.sql:18-40` 扩展状态并建立一对一 mail record 关联。
- 写/读路径全部集中在 `ManualReplySendAttemptService.prepareAndClaim/finalizeSuccess/finalizeFailure`；新路径只扩展 `SendPayload` 的可选 requestId，不增加第二套 attempt。
- 现有来信指纹首段仍使用原始数字 `inboundProcessingId.toString()`，保证部署前后同一来信/正文的幂等键不变化；无来信路径用 requestId 固定 attempt 短键，并把真实 mail-record 锚点保留在完整 hash/线程/审计中。
- 全部写路径：`ManualReplySendAttemptService.prepareAndClaim` 的 `insertIgnore/claimStatus`，以及 `finalizeSuccess/finalizeFailure` 的 `updateStatusAndError`；没有其他生产服务直接写 attempt。
- 全部读路径：同服务的 `findByOrcidIdAndMailType/findByOrcidIdAndMailTypeForUpdate/findById`，以及 `MailRecordRepository.findByMailSendAttemptId` 读取唯一结果行。Interaction point：前端 draft requestId → controller DTO → attempt 短键 → 成功后 `mail_record.mail_send_attempt_id`；响应丢失后的相同 requestId 必须在重选 anchor 前读回同一结果。（来源: K-manual-send-fingerprint-complete-identity, K-manual-send-unknown-must-converge）

#### `operator_action_log`

- Schema：`V19__add_operator_status_and_action_log.sql:32-52` 已允许 `expert_contact_id` 和 `inbound_processing_id` 可空组合，无迁移。
- 写路径：`ManualReplySendAttemptService.recordSendAudit` 当前只支持 `INBOUND_MAIL_PROCESSING`；新增并列的会话审计方法，仍复用 after-commit、best-effort 调度。
- 读路径：聊天页 `mailbox-chat.js:2204-2215` 按 `expertContactId` 加载操作日志，所以 `EXPERT_CONTACT` 审计会自然出现在同一会话。
- 全部写路径：`ExpertContactManagementService`、`ExpertIndexLevelOperationService`、`ExpertOperatorStatusService`、`AiReplyReviewAuditService`、`AiTrainingEvaluationService`、`BounceController`、`ManualReplySendAttemptService`、`PendingMailOperationService`、`SenderAccountBindingService`、`UnmatchedInboundMailService` 均调用 `OperatorActionLogService.record`；`RagPromptConfigService` 是唯一绕过该 service 的生产直接 SQL insert。本计划只在 `ManualReplySendAttemptService` 增加同类动作，不改共享 schema、RAG 直写或其他 action。
- 全部读路径统一经 `OperatorActionLogService.search` / repository 分页查询，由操作日志 API 和聊天日志区消费。Interaction point：after-commit audit 写 `expert_contact_id` → 聊天页按同 contactId 读取；`inbound_processing_id=NULL` 不会污染待处理来信日志关系。

#### QA/RAG 关联表

- `mail_record_qa_rule`（V42）与 `mail_record_rag_fact`（V113）只服务现有证据型来信回复。
- 无来信接口 DTO 不接收 `qaRuleIds`、`ragFactCodes`、`trustReplyAssembly`；内部 canonical ids 固定为空，不写两张关联表，不触发 unsupported-answer 归档。

#### 前端草稿缓存

- `mailbox-chat.js:75` 的 `sessionStore` 是模块内 `Map`；会话记录的 `drafts` 也是 `Map`（`252,390-424`）。
- 写路径：`saveDraftFromInputs:2702-2713`、采用工作台草稿 `2724-2745`、切目标和 unmount 的既有保存流程；成功发送 `2790-2797` 删除。
- 读路径：`getDraft:410-413`，选专家/重挂载通过会话缓存恢复。
- 新无来信 key 固定为 `contactId:OUTBOUND:<accountScope>`，不依赖可能变化的 latest message id；不承诺浏览器硬刷新后保留。

### 线程头与账号证据

- `ComposedMail` 已有 `inReplyTo` / `references`（`IntroductionMailComposer.kt:73-83`），SMTP 在 `SmtpMailDeliveryService.kt:47-48` 唯一写邮件头。
- `ManualExpertMailService.kt:241-250,259-267` 已有经过验证的做法：清理/限长 Message-ID，并用“原 `inReplyTo` + 当前锚点 Message-ID”构造 `References`。新路径复用该规则，不另造线程算法。
- `MailSenderAccountService.kt:65-71` 的 `getManualSendAccount` 是人工发送账号入口，并禁止模拟器账号。

### 前端样式盘点

- 可复用 class：完整集合与行号见 S-1/S-2；没有新增或就地修改 class。
- 设计 token：`#fff/#334155/#dce4ef/#3b82f6`，字号 `12px`，圆角 `7/10px`，按钮高 `32px`，编辑器高度 `100..240px`，disabled opacity `.45`。
- 当前 DOM 基线：`mailbox-chat.js:2148-2153` 为 manual details/section-content；`2168-2181` 为 compose/label/tools/editor/footer；`2185-2191` 为 unavailable note/template。目标 DOM 逐字骨架见 S-1/S-2。
- Interaction point：`renderManualSectionInto` 写入 DOM → `onInput` 写 draft Map → `sendManualReply` 读取同 DOM/draft → app adapter 发 HTTP；成功后 `afterSuccessfulSend` 读取服务端 timeline。outbound requestId 必须跟随这条链保存/清除。（来源: K-frontend-cache-key-triad, K-mailbox-draft-cache-owner-capture）

## 实现方案

### T1：增加真实成功发件锚点和幂等身份

文件：`MailRecordRepository.kt`、`ManualReplySendAttemptService.kt`。

遵守：I-1、I-3～I-5、I-10、I-11。

1. 在 `MailRecordRepository` 新增 `findLatestSentOutboundAnchor(contactId, accountScope, excludedAccountCode)`：
   - 条件：指定联系人、`direction='OUTBOUND'`、`send_status='SENT'`、`sender_account_code` 非空且不等于模拟器；`accountScope` 非空时再要求账号相等；
   - 排序：`COALESCE(sent_at, created_at) DESC, id DESC LIMIT 1`；
   - 返回完整 `MailRecord`，供账号、主题、Message-ID 和引用链使用。
2. 将 `ManualReplySendAttemptService.SendPayload.inboundProcessingId` 改为可空，并新增默认可空 `sourceAnchor` 与 `idempotencyRequestId`：
   - 来信路径未传新字段时仍把原数字 ID 写入 fingerprint，完整 hash 和短键算法不变（I-5）；
   - 发件路径传 `inboundProcessingId=null`、`sourceAnchor="MAIL_RECORD:<id>"`、可被 `UUID.fromString` 解析的 `idempotencyRequestId`；先用 `UUID.fromString(value.trim()).toString()` 规范化，完整 hash 的原首段位置写 sourceAnchor，并继续包含 account/recipient/正文；attempt 短键固定为 `MANUAL_RICH_REQUEST:` + `sha256(canonicalRequestId UTF-8).take(32)`（I-3/I-4）；
   - inbound ID 与 sourceAnchor 必须恰有一个；outbound 缺 requestId 时也在 `prepareAndClaim` 前立即失败。
3. 新增只读 `findCompletedByRequestId(orcidId, requestId)`：由 requestId 计算同一短键，读取 attempt 与其唯一 `mail_record`；仅当状态为 `SENT` 且结果行存在时返回。其他状态返回空并继续既有 claim/碰撞/fail-closed 逻辑（I-4/I-10）。
4. 新增 `recordConversationSendAudit`：动作类型固定 `SEND_MANUAL_RICH_REPLY`，目标为联系人，before 记录锚点 mail record；after 字段沿用纯人工分支的 `mailRecordId/sendStatus/subject/bodyPreviewText`（I-11）。
5. 抽取现有 after-commit 调度为私有小函数，让两种审计共享事务提交后执行和异常吞吐；不改现有来信审计 payload（I-8/I-11）。

### T2：在现有富文本服务中增加发件锚点入口

文件：`PendingMailOperationService.kt`。

遵守：I-1～I-11。

1. 新增公开方法 `sendConversationManualRichReply(contactId, requestId, accountScope, subject, htmlBody, textBody, operatorName, safetyWarningConfirmed, strongConfirmationText)`。
2. 方法先查联系人并校验 requestId，再调用 `findCompletedByRequestId`；命中时直接用原 `mail_record` 组装 `PendingMailSendResult`。未命中才调用 T1 锚点查询；无成功发件时在任何新 attempt/SMTP/写库之前返回 `422 CONVERSATION_SENT_ANCHOR_NOT_FOUND`（I-1/I-4/I-10）。
3. 由锚点确定账号；不接收覆盖账号。锚点 Message-ID 仅在 trim 后非空且长度 `<=255` 时使用，否则线程头为空但继续发送。
4. 为避免复制整套发送生命周期，把当前 `sendManualRichReply` 的主体下沉到一个私有共同实现，并传入一个小型 source context：联系人 ID、可空 inbound ID、可空 anchor mail record ID、账号 code、落库 `inReplyTo`、SMTP `inReplyTo/references`。
5. 现有来信入口构造与当前等价的 context：真实 inbound ID、原 `record.senderAccountCode`、原 `record.messageId` 进入 payload，但 SMTP 线程头保持当前值，不借本需求改变现有行为。
6. 新发件入口传空 QA/RAG/assembly/template 字段，跳过 inbound-only 的可信 assembly、QA canonicalization、RAG 存证和 unsupported-answer 归档。
7. 给 `collectSafetyFindings` 增加默认开启的 `runInboundSemanticChecks` 参数：两条路径都先跑数字/URL、高风险声明、信任话术检查；新无来信路径设为 false，在这些通用检查后返回；现有来信路径默认 true，行为不变。
8. 共同发送实现必须继续按既有顺序执行 suppression、claim、SMTP、分类、finalize；构造 `ComposedMail` 时只为新发件路径传 T1 计算出的线程头。
9. 成功后：来信路径继续调用旧审计和归档；发件路径调用 `recordConversationSendAudit` 且返回默认 `NOT_APPLICABLE/0/0` 归档状态。所有失败分类与 HTTP 状态沿用现有实现。

### T3：增加会话级 HTTP 接口

文件：`MailboxConversationController.kt`。

遵守：I-1、I-4、I-6、I-8～I-10。

1. 给现有 controller 注入 `PendingMailOperationService`，新增：
   - `POST /api/mail/mailbox/conversations/{contactId}/manual-rich-reply`
   - body：`requestId`、可空 `accountScope`、`subject`、`htmlBody`、可空 `textBody/operatorName`、`safetyWarningConfirmed=false`、可空 `strongConfirmationText`；
   - response：复用 `PendingMailSendResult`。
2. DTO 的 `accountScope` 只用于约束“从哪个当前会话账号查真实 SENT 锚点”；它不直接决定发件账号。DTO 不含 `senderAccountCode`、`qaRuleIds`、RAG 或 assembly 字段（I-4/I-6/I-8）。
3. controller 仅做参数转发；联系人、锚点、账号和安全校验全部留在 service，避免前端成为权限边界。

### T4：开放编辑器并按模式发送

文件：`mailbox-chat.js`、`app.js`。

遵守：I-1、I-2、I-4、I-8、I-9、I-12；S-1、S-2。

1. `renderManualSectionInto` 改成三态：
   - `inbound`：`latestInbound.processingId != null`；
   - `outbound`：无 latest inbound 且 `Number(summary.sentCount) > 0`；
   - `unavailable`：其余情况。
2. `inbound` 继续使用原 target key；`outbound` 使用 `contactId:OUTBOUND:<accountScope>`，QA context 固定 null；两者均调用现有 `manualComposeHtml`。`unavailable` 保留 `manualFollowUpHtml`（I-1/I-2/S-1/S-2）。
3. `manualComposeHtml` 接收模式/目标文案：outbound 显示“回复最近成功发件线程”，并在 footer 保留模板跟进按钮；不新增 CSS。
4. outbound 默认主题仅在 `latestMessage.direction === 'OUTBOUND' && sendStatus === 'SENT'` 时由该主题生成 `Re:`；否则使用 `Re:`，不把失败消息假装成锚点。
5. `currentTargetKey` 同时接受 inbound/outbound；`adoptAssembly` 仍只接受 inbound。
6. outbound draft 增加 `requestId`：第一次点击发送时用 `globalThis.crypto.randomUUID()` 生成；无该 API 时复用 `app.js:342-349` 的 RFC 4122 v4 fallback，生成后先写入 draft；安全确认/失败/网络重试复用它；用户再次编辑主题或正文时清空它；成功时随 draft 一起删除（I-4/I-12）。
7. `sendManualReply` 按模式分发：
   - inbound：继续调用 `mcHostSendRichReply(processingId, body)`，保留 QA/RAG payload；
   - outbound：调用 `mcHostSendConversationRichReply(contactId, body)`，body 只含 requestId、当前 accountScope、自由正文和确认字段。
8. `app.js` 从当前 `mcHostSendRichReply` 提取按 URL 提交和两级安全确认的共同函数；旧适配器传旧 URL，新适配器传会话 URL。成功/失败提示、归档状态提示保持同一口径（I-8/I-9/I-10）。
9. 只有返回 `true` 时删除对应草稿和 requestId 并刷新 timeline；返回 false/reject 继续保留（I-12）。

### T5：自动化验证

文件：`mailboxChatBehavior.test.js`、`PendingMailOperationServiceTest.kt`、`ManualReplySendAttemptServiceTest.kt`、`MailboxConversationControllerTest.kt`。

遵守：I-1～I-12；S-1、S-2。

1. `mailboxChatBehavior.test.js`：
   - 改写当前“无来信不显示 compose”用例：`sentCount=2` 时显示 compose，工作台仍不可生成，发送调用新 adapter/contactId，模板按钮仍可用；
   - 增加 `pendingCount=0` 且有真实 latest inbound 的用例，断言仍显示编辑器并走旧 processingId adapter；
   - 增加仅失败发件（`sentCount=0`）仍不显示 compose；
   - 覆盖 outbound 草稿/requestId 跨切换恢复、失败/取消复用、编辑后换新 requestId、发送成功删除；
   - 现有采用 QA/RAG 草稿并走 inbound adapter 的断言保持。
2. `PendingMailOperationServiceTest.kt`：
   - 最近 `SENT` 锚点决定账号、`mail_record.inReplyTo` 和 SMTP 线程头；更晚 FAILED 不得成为锚点；
   - 非空 accountScope 只选择该账号的 SENT 锚点；该账号无成功发件时 422，不能退回其他账号；
   - `messageId=null/空/超长` 仍发送但线程头为空；
   - 无成功锚点在 claim/SMTP 前返回 422；
   - 已完成 requestId 在锚点重查前返回原结果，模拟原发送后新增记录已成为 latest，也不再次 SMTP；
   - suppression、安全普通确认、强确认、SMTP 各类失败继续走既有状态；
   - 新路径 canonical QA/RAG 均为空、无归档、调用联系人审计；
   - 现有 `PROCESSED` inbound 回信成功，明确证明不依赖 `MANUAL_REVIEW`。
3. `ManualReplySendAttemptServiceTest.kt`：
   - 原 inbound payload 的 fingerprint 输入保持兼容；
   - outbound requestId 派生固定短键；`findCompletedByRequestId` 对 `SENT` 返回唯一 mail record；不同 requestId 产生不同 attempt；缺失 inbound/sourceAnchor/requestId 立即失败；
   - 成功/失败记录仍保存 `inReplyTo`；
   - 会话审计 target/inbound/before/after 精确断言，且 after-commit 行为与旧路径相同。
4. `MailboxConversationControllerTest.kt`：增加 `PendingMailOperationService` mock，断言会话 POST 的路径、JSON 字段、服务调用和 `PendingMailSendResult` JSON；保留 Auth 拦截。

验证命令：

```bash
node --check src/main/resources/static/mailbox-chat.js
node --check src/main/resources/static/app.js
node --test src/test/js/mailboxChatBehavior.test.js
mvn -Dtest=PendingMailOperationServiceTest,ManualReplySendAttemptServiceTest test
mvn -DmysqlIt=true -Dtest=MailboxConversationControllerTest test
mvn test
git diff --check
```

MySQL 集成测试依赖项目既有 Docker/Testcontainers 条件；环境不可用时必须记录 `NOT_RUN`，不能写成通过。测试不得连接或清理业务数据库。

## 变更文件清单

| # | 文件 | 变更范围 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt` | 最近成功出站锚点查询 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt` | outbound requestId 幂等键、会话审计、共享 after-commit 调度 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 会话自由回信入口与现有发送链复用 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` | 会话级 POST 与窄 DTO |
| 5 | `src/main/resources/static/mailbox-chat.js` | 三态人工区、outbound 草稿和分发 |
| 6 | `src/main/resources/static/app.js` | 会话发送适配器与安全确认复用 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt` | 指纹/落库/审计测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 新服务路径与状态无关回归测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` | 新接口契约/Auth 测试 |
| 10 | `src/test/js/mailboxChatBehavior.test.js` | 编辑器、草稿、适配器行为测试 |

硬范围：上述清单不含 migration、CSS、HTML 资源注册、`ManualExpertMailService`、可信工作台或待处理状态相关文件。若实施时必须触碰第 11 个业务/测试文件，先停止并修订计划，由人工重新批准。

## 验收标准

- I-1：仓库/service 测试覆盖 `SENT`、更晚 `FAILED`、仅 FAILED、空账号、模拟器；只有真实可发送成功记录返回锚点。
- I-2：JS 与 service 测试构造 `pendingCount=0/processStatus=PROCESSED`，断言编辑器存在且旧 inbound endpoint 成功。
- I-3：测试断言 outbound 无 `inboundProcessingId`，线程/审计使用仓库返回的 mail record；全局 grep 不出现伪 ID 常量。
- I-4：测试模拟“首次已 finalize 成功但 HTTP 响应丢失”：同 requestId 再请求返回原 messageId、SMTP 仍只调用一次；编辑正文后 requestId 改变。
- I-5：固定 inbound payload 的 `fullHex/shortKey` 回归值不变；现有 inbound attempt 测试全部通过。
- I-6：controller JSON 只接收 accountScope 而非 senderAccountCode；service 测试断言 scope 参与锚点查询，delivery 账号等于查询结果，scope 下无 SENT 不回退其他账号。
- I-7：合法 Message-ID 同时断言 payload、SMTP 两个头和落库；null/blank/>255 三组都发送且三个位置均为空。
- I-8：既有 inbound QA、RAG、assembly、archive、audit、错误分类测试零改预期并通过；旧 endpoint JSON 不变。
- I-9：mock invocation order 证明校验/渲染/安全/suppression 在 claim 前，SMTP 在 claim 后，audit 在 commit 后；outbound 不调用 QA selection。
- I-10：TRANSIENT/PERMANENT/INFRA/未知异常状态与原链一致；UNKNOWN 重试不调用 SMTP；HTTP body 不含 mock 的 SMTP errorDetail。
- I-11：成功/失败 `mail_record` 字段逐项断言；outbound audit 的 target/inbound/before/after 逐项断言；聊天日志 API 按 contactId 可读。
- I-12：JS 测试断言安全取消、false、reject 均保留正文/requestId，编辑换 key，true 才删除并刷新。
- S-1：`git diff -- mailbox-chat.css` 为空；DOM 测试逐项断言 S-1 的 class/层级/按钮，样式测试继续断言列出的实值和 disabled/focus 规则。
- S-2：无来信+无 SENT 与无来信 workbench 的 DOM/逐字文案保持 S-2；窄屏既有测试无回归。
- 集成：定向 JS/Kotlin、MySQL 接口测试（环境可用时）、全量 `mvn test`、`git diff --check` 全通过；MySQL 不可用只能记 `NOT_RUN`。

## 人工验收清单

### A-1: 已处理来信仍可回信
- 前置条件: 准备一位已绑定来信且后台状态为 `PROCESSED`、待处理数为 0 的专家。
- 操作步骤: 1. 打开“收发邮件”聊天页；2. 选择该专家；3. 输入主题和正文；4. 点击“发送人工回复”。
- 预期结果: 显示主题/正文编辑器；发送成功提示为“人工回复邮件发送成功”；timeline 新增 1 封出站邮件，待处理数仍为 0。
- 覆盖: I-2、I-8；需求第 1 条；回归旧 endpoint/状态机。

### A-2: 只有成功发件也可自由回信
- 前置条件: 准备一位从未收信、至少有 1 封 `SENT` 发件且最近成功发件账号可人工发送的专家。
- 操作步骤: 1. 选择该专家；2. 检查人工回复区；3. 输入“Re: test”和“follow up”；4. 在浏览器网络面板观察 POST；5. 点击发送。
- 预期结果: 显示 S-1 编辑器和“回复最近成功发件线程”；请求路径为 `/api/mail/mailbox/conversations/{contactId}/manual-rich-reply`，body 有 requestId/accountScope 且没有 processingId/senderAccountCode/QA/RAG；成功后 timeline 增加 1 封邮件，发件账号与当前 scope 内最近成功发件一致。
- 覆盖: I-1、I-3、I-4、I-6；需求第 2 条。

### A-3: 仅失败发件不开放
- 前置条件: 准备一位无来信、`sentCount=0`、至少有 1 封 `FAILED` 发件的专家。
- 操作步骤: 1. 选择该专家；2. 展开人工回复区。
- 预期结果: 不存在主题输入框和正文编辑器；逐字显示 S-2 的说明；只存在“选择模板发送跟进邮件”。
- 覆盖: I-1、S-2；资格边界。

### A-4: 模板跟进链保持
- 前置条件: 使用 A-2 或 A-3 专家，并保证存在可用邮件模板。
- 操作步骤: 1. 点击“选择模板发送跟进邮件”；2. 选择模板；3. 按原流程预览，不实际发送也可。
- 预期结果: 打开既有专家模板发件流程；可选择模板，页面没有新增自由正文塞入模板命令的选项。
- 覆盖: I-8、S-1/S-2；“不改模板发送链”。

### A-5: 无来信工作台与待处理功能保持
- 前置条件: 一位无来信但有成功发件专家；另准备一条 `MANUAL_REVIEW` 来信。
- 操作步骤: 1. 对无来信专家展开“可信回复工作台”；2. 回到待处理筛选；3. 对准备的来信执行原“标记已处理”。
- 预期结果: 工作台逐字显示“暂无专家来信，暂不能生成回复”；待处理筛选只列 `MANUAL_REVIEW`；标记后该条从待处理消失。本次自由回信不改变这些结果。
- 覆盖: I-2、I-8、S-2；“不改工作台/状态机”。

### A-6: 草稿、失败与幂等重试
- 前置条件: 使用 A-2 专家；测试环境可模拟一次响应丢失或服务端返回失败。
- 操作步骤: 1. 输入主题/正文；2. 切换专家再切回；3. 点击发送并制造响应丢失；4. 不编辑正文再次点击；5. 再修改正文后点击。
- 预期结果: 第 2 步原文恢复；第 3 步草稿保留；第 4 步 SMTP 实际总投递数仍为 1 且返回原结果；第 5 步使用新 requestId 并形成新的发送尝试。
- 覆盖: I-4、I-10、I-12；draft→HTTP→attempt→mail_record interaction。

### A-7: 安全确认不回退
- 前置条件: 准备会命中普通风险和强风险的两份测试正文。
- 操作步骤: 1. 发送普通风险正文并取消；2. 再发送并确认；3. 发送强风险正文；4. 不输入“确认发送”取消；5. 重新发送并逐字输入“确认发送”。
- 预期结果: 步骤 1/4 均无邮件且草稿保留；步骤 2 成功；步骤 3/5 出现两级确认，只有步骤 5 投递成功。
- 覆盖: I-9、I-12；安全链回归。

### A-8: 发件账号、线程头与落库可见结果
- 前置条件: A-2 专家的最近成功发件具有合法 Message-ID，并可查看测试收件箱邮件原文和后台邮件记录。
- 操作步骤: 1. 完成一次发送；2. 打开收件邮件原文；3. 在后台邮件明细查看新记录。
- 预期结果: From 等于锚点账号；原文含 `In-Reply-To: <锚点Message-ID>`，`References` 末项相同；后台为 `MANUAL_RICH_REPLY/OUTBOUND/SENT`，`sourceInboundId` 为空，`inReplyTo` 等于锚点 Message-ID。
- 覆盖: I-3、I-6、I-7、I-11；mail_record 写→timeline/后台读 interaction。

### A-9: 无 Message-ID 仍可发送
- 前置条件: 测试库准备一位只有成功发件但锚点 `message_id=NULL` 的专家。
- 操作步骤: 1. 在聊天页输入普通正文；2. 点击发送；3. 查看邮件原文和新记录。
- 预期结果: 发送成功；原文无 `In-Reply-To/References`；新记录 `inReplyTo` 为空；没有伪造 Message-ID 作为引用头。
- 覆盖: I-3、I-7。

### A-10: 操作日志归属准确
- 前置条件: 完成 A-2 的发送。
- 操作步骤: 1. 展开该专家的“操作日志”；2. 打开该次 `SEND_MANUAL_RICH_REPLY` 详情。
- 预期结果: 日志归属当前专家；目标类型为 `EXPERT_CONTACT`；没有 inbound processing 关联；before 中 `anchorMailRecordId` 等于发送前的成功发件记录 ID。
- 覆盖: I-3、I-11；audit 写→聊天日志读 interaction。

### A-11: 视觉与响应式
- 前置条件: 使用 A-2 专家；浏览器分别设为 1200px 和 720px 宽。
- 操作步骤: 1. 两种宽度打开人工回复区；2. 聚焦主题/正文；3. 点击发送使按钮进入 disabled。
- 预期结果: section 白底、10px 圆角；输入/编辑器 7px 圆角；编辑器高度在 100..240px；按钮高 32px；focus 为 2px 蓝色 outline；disabled opacity 为 .45；720px 下无横向溢出。
- 覆盖: S-1、S-2；UI 目测回归。
