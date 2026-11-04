# 人工回复延迟发送开发总计划

状态为待执行。创建日期 2026-11-04。源码基线 `a224623c8dc17bdad1ce80e1c4391637bf7ee5ca`，同时核对当前工作树。用户已确认前端预览，并明确包含「发送已审核回复」。本文件与子计划描述拟实施行为，代码现状单独列在审计中。

## 需求描述

O-1 人工回复提交后等待 60 秒，由服务端到期发送。支持普通来信回复、人工编辑区内的跟进/无来信回信，以及管理员发送已审核回复。刷新、切换会话、关闭浏览器不改变期限。

O-2 邮件卡片底部「翻译」所在操作行显示「撤回」或「重新发送」。点击后在同一行二次确认，取消不执行操作，确认撤回才取消待投递任务。重新发送重新获得完整 60 秒。

O-3 异步失败后，收发列表对应专家和邮件卡片显示红色感叹号，鼠标悬停、键盘聚焦可查看公开失败原因。安全可重发的失败展示「重新发送」。SMTP 结果未知显示待确认原因并禁止重复投递。

O-4 普通回复撤回后恢复原草稿与附件。已审核回复撤回后保留 `APPROVED` 冻结内容和审核历史，可以再次发送。后者沿用已审核内容不可直接编辑的业务规则，不把审核稿复制为可绕过审核的普通草稿。

必须保持的行为（N）

- N-1 批量发送、自动回复、专家模板直接发送、旧会议排期发信沿用现有路径。
- N-2 普通与审核权限、原件归属、当前账号/真实线程锚点校验有效。审核内容变更和新来信导致的上下文过期仍要求确认。
- N-3 HTML/text 规范化、安全确认、QA/RAG 顺序与归档、附件原字节和 ICS、人工发送配额语义保留。
- N-4 `SENT` 后才产生成功记录、关闭对应版本普通草稿、创建来源邮件唯一排期。HTTP 超时不触发重复发送。
- N-5 既有筛选、收发计数、分页、账号隔离、只读展示和移动布局保持可用。

范围外包括任意自定义预约时间、自动重试策略、SMTP 投递后邮件召回、历史失败批量补发、批量发送撤回、新建消息推送基础设施。旧历史失败只有状态记录而缺少最终 HTML/审核快照，不能伪造可重发载荷。

## 关键不变量

### Invariant I-1 — 服务端 60 秒期限
- Rule — 首次受理事务写入 `WAITING` 与 `dueAt = serverNow + 60000ms`。工作线程只能在数据库权威时间达到 dueAt 后领取。重复提交返回同一任务及原 dueAt，不刷新期限。明确确认的重新发送/撤回后新提交才开启新一轮期限。
- Applies to — 原人工回复入口、审核发送入口、enqueue、retry、claim、cancel、倒计时读取。
- Violation consequence — 页面关闭后不发、提前发、重试网络请求无限延后。
- 来源 — original。

### Invariant I-2 — 取消与领取互斥
- Rule — 相同 job/revision 的 `WAITING→CANCELLED` 和 `WAITING→SENDING` 在短事务中条件更新，只能一个成功。撤回要求数据库 `now < dueAt`，到期即停止撤回。二次确认不暂停时间。领取与 attempt `PREPARED/FAILED_SAFE_TO_RETRY→DELIVERY_IN_PROGRESS` 同事务提交后才能进入 SMTP。
- Applies to — cancel、claim、worker、二次确认请求。
- Violation consequence — UI 已撤回但仍发出，或并发线程重复投递。
- 来源 — K-smtp-idempotency-reservation-before-delivery。

### Invariant I-3 — 幂等身份与代次
- Rule — 复用当前 `computeFingerprint` 和 `mail_send_attempt` 的唯一键，保持旧指纹字节、`MANUAL_RICH:` 与 `MANUAL_RICH_REQUEST:` 规则。新表每个 attempt 最多一行。首次已存在 `SENT/UNKNOWN/IN_PROGRESS` 必须收敛，不重新排队。任务 `revision` 每次操作推进，重发要求 expectedRevision，CANCELLED经原send重新排队要求delayExpectedRevision匹配当前代次；旧点击与旧网络包不能撤回/重发新一轮。撤回后重新提交使用同任务的新 revision，保留原 Message-ID，记录最新 dueAt。不同操作者或不同源草稿遇到同指纹已绑定任务，返回 409，不能劫持任务。
- Applies to — reservation、enqueue、retry、cancel、sync compatibility guard、finalize、重复 API。
- Violation consequence — 重发多个 Message-ID、旧请求取消新发送、内容指纹碰撞。
- 来源 — K-manual-send-fingerprint-complete-identity、K-manual-send-success-transaction-and-dedup。

### Invariant I-4 — 持久化完整冻结载荷
- Rule — 服务端完成现有最终正文、安全、会议、附件校验后冻结载荷。快照包含 final subject/text/html、真实联系人与账号、收件人、落库及 SMTP 两套线程字段、有序 QA/RAG、ICS、结构化排期、通用附件 ID/元数据/摘要、原始普通草稿或已审核稿引用、审计与归档输入。禁止序列化 `MailSenderAccount`、SMTP 密码、运行时 service、原件字节/绝对路径。worker 从元数据重读校验文件，SMTP 使用冻结正文与 ICS，不重渲染成另一封邮件。
- Applies to — snapshot codec、enqueue、worker、retry、消息 DTO。
- Violation consequence — 重启后正文/附件丢失，或排队内容与实际发出不同。
- 来源 — K-manual-rich-final-body-single-seam、K-calendar-filename-snapshot-chain。

### Invariant I-5 — 成功事务与失败分类
- Rule — `mail_record + QA/RAG 事实 + 来源排期 + attempt SENT + 普通草稿 closeSent/审核发送结果 + job SENT` 在同一最终短事务内收敛。保留审计/ES 无依据回答归档的发送后处理语义，归档失败不能把已提交 SENT 改回 UNKNOWN。明确未投递的错误才可进入 FAILED；现有分类允许的 `FAILED_SAFE_TO_RETRY` 才可重发原内容。SMTP 超时/断链及无法确定发送结果进入 UNKNOWN，无自动重发。SMTP 5xx 永久失败仍要求修改/重新走原审核流程，原内容重发按钮禁用并解释原因。
- Applies to — deliverPrepared、finalizeSuccess、finalizeFailure、recovery、retry eligibility。
- Violation consequence — 已发重复、失败计入成功、排期提前产生、RAG 存证丢失。
- 来源 — K-manual-send-unknown-must-converge、K-manual-send-success-transaction-and-dedup、K-failed-mail-record-has-null-sent-at。

### Invariant I-6 — 草稿版本与恢复
- Rule — WAITING/SENDING 时对应普通草稿 ACTIVE 内容保留但不可编辑/放弃/重绑，所有写路径在服务端守卫。撤回只释放相同 draft id/version/attempt 绑定，不清正文和 context。无 draftRef 的旧入口也必须持久化可恢复的原始输入，撤回后通过该快照恢复原操作者的旧详情编辑器，不把legacy QA/assembly强塞进普通草稿context；已有更新输入时明确提示冲突且保留两份内容，不覆盖。审核稿始终 APPROVED，撤回释放发送锁但保留审核冻结内容和事件。
- Applies to — draft save/discard/reopen/bind/closeSent、enqueue、cancel、review bind/read/send。
- Violation consequence — 倒计时内被自动保存改写、撤回丢附件、已审核内容绕审。
- 来源 — K-reply-draft-single-target-is-not-review-history、K-mailbox-draft-cache-owner-capture。

### Invariant I-7 — 权限与在途变化
- Rule — GET 按当前联系人可读权限过滤。操作按当前权限校验，普通任务限原操作者或 admin，user1 必须 FULL；审核任务限 admin，审核稿作者仍须具有原有可读授权。worker 使用持久化 actor 身份再校验，不能以 null 身份冒充管理员。检查当前 contact/account/recipient/source identity、抑制名单、附件完整性与审核上下文。变化导致投递前失败，禁止静默换账号/收件人/正文。SMTP 已调用后，权限变化不能阻止实际结果落库。
- Applies to — HTTP handlers、service actions、claim preflight、finalize、reader DTO。
- Violation consequence — 降权后继续发、跨专家操作、凭缓存权限泄露邮件。
- 来源 — K-explicit-outbound-anchor-server-revalidation、K-mailbox-readonly-needs-lifecycle-audit。

### Invariant I-8 — 消息身份与统计
- Rule — 任务邮件 source 固定 `DELAYED_MANUAL_REPLY`，id 为 job.id，source_rank=3，时间轴 eventAt 固定任务首次 createdAt。只要 job 存在，其关联 mail_record 在 UI union 内被排除，job 行一直显示到 SENT，不在成功时更换 UI 主键。成功后原 mail_record 仍供统计、附件、审计使用。待发/撤回不计成功或失败，FAILED/UNKNOWN 计一条异常，重排 WAITING 清除异常计数，SENT 计一条成功。审核 context token 使用原始真实邮件 union，任务 UI 行不参与摘要，避免排队行为使自身审核失效。
- Applies to — conversation union、membership、latest、timeline/cursor、attachment dispatch、frontend message key。
- Violation consequence — 重复卡片、计数翻倍、分页丢项、自身审核上下文改变。
- 来源 — K-mail-record-source-inbound-id，当前 `reviewContext`/`rangeUnionSql` 交叉审计。

### Invariant I-9 — UI 服务器状态权威
- Rule — HTTP 受理仅提示「已加入发送队列，60 秒内可撤回」。倒计时仅显示，0 秒显示「等待发送」直到服务端回报。确认状态按 user/account/contact/job/revision 隔离，切换、失权、unmount 清理；重复点击同操作只发一个请求。异步成功不清另一个专家或新版本的草稿。列表在无选中会话时也刷新异常状态。
- Applies to — app 两个同名 submit helper、mailbox-chat、reader host、review send、轮询。
- Violation consequence — 尚未发送却提示成功、旧回包清空新稿、列表长期不报错。
- 来源 — K-mailbox-draft-cache-owner-capture。

### Invariant I-10 — 状态与错误可解释
- Rule — WAITING/SENDING/CANCELLED/SENT/FAILED/UNKNOWN 为新表生命周期，attempt 仍使用现有枚举字符串。job 状态与 attempt 不一致时 fail closed 并通过恢复过程修正。`publicErrorCode/publicErrorMessage` 来自固定映射，SMTP 原始 errorDetail 仅服务端保存。UNKNOWN 同样提示异常但明确「发送结果待确认，请勿重复发送」。历史记录缺快照不提供重发能力。
- Applies to — models、query、worker exception handling、tooltip、能力字段。
- Violation consequence — 泄露 SMTP 凭据/内部路径，或以未知投递冒充可安全失败。
- 来源 — K-manual-send-error-response-opaque。

### Invariant I-11 — 发布开关与恢复
- Rule — 新开关只控制新请求入队，默认 false 供逐步部署。已有任务的处理/读取/撤回不受开关关闭影响。开启入口必须晚于 worker、读投影、UI 全部可用。worker 使用独立有界线程，不在共享调度线程睡眠 60 秒。进程重启继续 WAITING，超时 SENDING 标 UNKNOWN，绝不重新发送。
- Applies to — properties、worker、rollout、HTTP routing。
- Violation consequence — 发布后遗留任务永远不发或回滚造成重复发送。
- 来源 — original，现有独立 executor 先例见审计。

## 现状审计

以下结论来自工作树代码。`manual-reply-delay-evidence/source-manifest.json` 保存路径、SHA-256、行数及 HEAD；完整 grep 带命令与 exit code，禁止把旧知识行号当当前证据。迁移原文在 `schemas.md`，关键 Kotlin 原文带行号在 `source-excerpts.md`，前端逐字基线在 `frontend-baseline.md`。

### 入口与共用 seam

- `PendingMailOperationService.kt:162–386` 为来信人工回复，`:397–519` 为会话人工回复，两者进入 `:605 executeManualRichSend`。`:648–809` 生成最终正文、会议和附件、SendPayload，`:819–836` 处理抑制与身份确认，`:838` 立即 claim，`:868` 同步 SMTP。
- `MailReviewSendService.kt:10–23` 从 `APPROVED` 服务端快照调用上述两个入口。已审核发送也必须走延迟分支，不能遗漏。
- HTTP `UnmatchedInboundMailController.kt:268–310` 与 `MailboxConversationController.kt:425–447` 提交并返回 `PendingMailSendResult`。原 response 的 `sendStatus` 为字符串，可以新增 WAITING 响应，但所有消费者必须同步。
- `app.js:18434–18478` 的局部 helper 与 `:22038–22061` 的全局 helper 都把非空响应当发送成功。`mailbox-chat.js:9506–9556` 则回读草稿决定是否关闭。两者均纳入计划。
- `ManualExpertMailService.kt:72–207` 是模板直接发件路径。批量、自动回复和旧会议路径的写入口见 grep 02，明确不接入本功能。

### mail_send_attempt

- Schema — V23 唯一 `(orcid_id,mail_type)`、唯一 message_id；V24 加 recipient/subject/body/content_type 与配额字段。人工 `body` 存 full SHA，不存完整邮件，见 `ManualReplySendAttemptService.kt:326–336`。不能单靠当前 attempt 恢复异步邮件。
- Write paths — `ManualReplySendAttemptService.prepareAndClaim:326/364/388` 与 `finalizeSuccess:533`、`finalizeFailure:617`；`ManualInitialOutreachService.kt:1551` save；`ManualOutreachTxHelper.kt:98/153` save；V24 数据回填。完整调用回执见 01/02。
- Read paths — attempt service 的指纹/绑定/提前完成读取；初始外联重试与配额链；`DiscoveryPersonOutreachGuardService.kt:751` 仅按 INTRODUCTION 读发送历史；`MailReplyDraftRepository.kt:27–29`、`MailReviewDraftRepository.kt:123–130` 联表；`MailRecordRepository` 的 attempt 关联读取。
- Interaction X-1 — 入队保留 PREPARED 与现有同步 claim/草稿状态 reader 的交叉。必须增加 delayed-job guard，禁止旧入口直接领取已排队任务。

### mailbox_reply_draft

- Schema — V152 目标唯一键 owner/contact/kind/processing/scope，version，ACTIVE/SENT/DISCARDED（应用 enum），send_attempt_id/send_version。两绑定列无 DB FK。
- Write paths — `MailReplyDraftRepository.insert/save/discard/bindAttempt/closeSent/releaseCompletedBinding`；调用方 `MailReplyDraftService.save:37–63`、`discard:66–77`、`bind:155`、`closeSent:158`、`releaseBinding:150`，以及 attempt service claim/finalize 的调用。save 当前是单条 CAS，但不禁止 PREPARED 绑定下编辑。
- Read paths — target/detail/list/summaries；send validator/completedBinding/lockForClaim；`MailboxConversationRepository.ownDraftRows:840`；前端 server draft 回读与本地 Map。
- Interaction X-2 — 任务锁定↔自动保存/放弃/重开↔成功清稿/撤回恢复。必须给每条写路径相同锁序与 CAS 条件。

### mailbox_review_draft / mailbox_review_draft_event

- Schema — V169 状态 DRAFT/SUBMITTED/RETURNED/APPROVED，approved 字段有 CHECK，owner/client_request_id 唯一；event.draft_id FK RESTRICT，事件内容 LONGTEXT。
- Write paths — `MailReviewDraftRepository.insert:49`、`update:59`、`bindAttempt:115`、`appendEvent:173`、`appendSendResult:137–149`。服务 create/save/submit/withdraw/delete/approve/return/comment/confirmContext/change/snapshot 与发送 bind/result 调用这些写点，原文已归档。
- Read paths — list/detail/events/createSnapshot/context/validTarget/sendProjection；审核徽标 `MailboxSuspensionService`、会话 pending/list；`MailReviewSendService`；管理员与 reader 工作台。
- Interaction X-3 — 入队/撤回/结果必须投影到审核详情；现 `PREPARED` 被前端视为可发送 (`mailbox-chat.js:5853`)。`reviewContext` (`MailboxConversationRepository.kt:33–76`) 哈希 `rangeUnionSql`，新增 UI union 不能直接进入该哈希。

### mail_record 与成功附属表

- Schema — V1 mail_record；V6 cleaned body/intent；V15 triggered_by/source_inbound_id；V23 error/attempt；V24 attempt 唯一及 FK；V101 task_execution_id；V123 calendar JSON；V127 outbound JSON；V141 open tracking。QA 表 V42 唯一(mail_record_id,qa_rule_id)，RAG V113 唯一(mail_record_id,ordinal)，会议 V125 唯一 source_mail_record_id。
- mail_record Write paths — `AutoMailReplyService.kt:519/860/1207/1406`、`ManualExpertMailService.kt:134`、`ManualReplySendAttemptService.kt:509/614`、`ManualOutreachTxHelper.kt:69/127`、`MeetingScheduleService.kt:145` 的 save，以及 V24 等历史迁移。执行本计划仅修改 attempt service 的人工完成事务。
- QA Write paths — AutoMailReplyService、ManualExpertMailService、ManualReplySendAttemptService 的 save。RAG Write path — `PendingMailOperationService.kt:922` 的 saveAll，位于 finalizeSuccess 的 REQUIRES_NEW 提交之后，异步化应移入同一个最终事务，避免 SENT 后存证异常导致错误回报。QA/RAG readers 包括 QaRuleAuditService 与各自有序 repository 方法，grep 07。
- meeting Write paths — `MeetingCalendarService.createManual:33`、`createFromSentMail:64`、`update:170`、`cancel:200`，调用 repository.insert/updateMutable/cancel。列表、摘要、event、发送成功去重均读该表。
- mail_record Read paths — mailbox/会话、监控、退信归因、专家管理/材料/下载、AI/RAG/训练/自动回复上下文、外联/维护准入、状态 reconcile；具体文件和方法调用逐行列在 02 与 07，离线 `scripts/reclassify-auto-responses.py:84/94/104/114` 为读取。不得扩大这些消费者的记录状态枚举。
- Interaction X-4 — 最终事务→时间线/计数/草稿/审核/排期。等待阶段不写伪 SENT mail_record，既有跨模块读取保持原状态空间。来源关系必须按 source 处理（来源 K-mail-record-source-inbound-id）。

### outbound_mail_attachment 与文件

- Schema — V126 UUID 主键，expert FK RESTRICT，created_by/file_name/content_type/byte_length/sha256。快照 V127 存 mail_record。
- Write paths — `OutboundAttachmentService.upload:49–122` 流式临时文件→原子 move→repository.insert，失败删本次文件；repository 的显式 insert 为元数据写点。现移除草稿附件仅解除引用，没有删除原件 API，不能沿用历史 `mail_attachment` 的收信转存模型。
- Read paths — resolveForSend/loadSnapshots/resolveDraftDownload/resolveForMessageDownload，草稿、发送、消息下载与权限守卫。`resolveForSend:148–162` 校验上传者并重读 size/SHA，不能把原件仅保留为队列内存 byte[]。
- Interaction X-5 — 入队快照→重启后文件重读→冻结附件一致→成功归档下载。会议 ICS 在 payload 内保存，通用原件继续引用现有文件。

### 前端样式盘点

- `mailbox-chat.css:109–112` `.mc-text-button` 完整普通/hover/active/disabled，字号 11px、line-height 1.5、主色 #6482b2、hover #244ca9/#edf3ff，active #dbeafe，disabled opacity .45。
- `mailbox-chat.css:43–48` 基础邮件卡片被 `:113–116` 覆盖，最终卡片 padding 14px 17px、radius 11px、width min(92%,820px)、阴影 0 2px 6px #334b7210；正文 13px/1.55 #465974；footer gap 12px、margin-top 12px、padding-top 10px、border-top #e7edf5、左对齐。
- `.mc-person-heading` 在 `mailbox-chat.css:157–158` flex/gap6px，strong flex:1；`styles.css:12698` 另有右侧状态区域 padding。添加图标不改既有 heading 规则。
- `styles.css:12905–12907` 已有 inline-secondary/primary 与 process-confirm，含 focus-visible/disabled；移动 `:12918` 最小高度44px。新增邮件操作复用按钮，派生独立包裹 class，不修改旧待处理确认。
- DOM 基线为 `mailbox-chat.js:5118–5168` 的 `article.mc-message > header/h3/.mc-body/footer` 与既有 footerButtons；列表管理员 `:2231–2233` 和 reader `mailbox-readonly.js:828–830`。
- 本次审计实际基线、完整规则和 DOM 原文在 `frontend-baseline.md`。class 全部使用位置在 05-style-sites.txt。正式逐字样式在子计划 06。

### 其他交互点

- X-6 权限双闸 — `ExpertMailPermissionGuard.kt:34–78` handler/verb→资源表映射；`MailReadOnlyPolicy.kt:60–99` 白名单；`app.js:21180–21230` reader API 白名单。新 action/details 必须同时覆盖。
- X-7 HTTP→UI — 两个 app submit helper、review send、manual draft 回读、reader 的 onChanged 与 refresh/focus/unmount 生命周期。
- X-8 worker→重启 — `TalentIntroductionApplication.kt:9` 开启 scheduling；`ExpertAcademicEnrichmentWorker.kt:19–46` 记录共享 @Scheduled 线程不能被长任务占用，并通过独立 executor 执行。本计划使用同类型有界执行方式，不假设 RabbitMQ 已开启。
- X-9 审核来源上下文变化 — 待发送期间新的真实来信使 review context 过期，worker 必须投递前失败；用户在原审核面板重新确认上下文后才能重排，错误不能通过冻结旧 token 绕过。

## 实现方案

采用 MySQL 持久化延迟任务，复用现有 attempt 身份、最终 SMTP 投递与落库流程。前端 setTimeout 仅用于倒计时显示。新表承载等待/取消/失败可见状态，既有共享表不增加字段。

以下严格顺序执行，每个子计划单独编译、测试和核验，前序不引用未完成的后序类。中间基础设施版本通过 `talent-introduction.manual-reply-delay.enabled=false` 独立部署；这不是完整功能交付，最后开启阶段才满足 O-1～O-4。

| 顺序 | 子计划 | 修改文件数 | 前置 |
|---|---|---:|---|
| 01 | [持久模型与迁移](manual-reply-delay-01-storage.md) | 7 | 无 |
| 02 | [冻结与投递分离](manual-reply-delay-02-preparation.md) | 7 | 01 |
| 03 | [事务状态机与草稿](manual-reply-delay-03-lifecycle.md) | 10 | 02 |
| 04 | [后台执行与操作 API](manual-reply-delay-04-worker-api.md) | 7 | 03 |
| 05 | [会话读模型](manual-reply-delay-05-query.md) | 7 | 04 |
| 06 | [前端操作与状态](manual-reply-delay-06-frontend.md) | 9 | 05 |
| 07 | [入口接通与集成验收](manual-reply-delay-07-activation.md) | 8 | 06 |

技术契约与每步具体任务在子计划正文中。共同门禁为 I-1～I-11，子计划不得用测试 mock 覆盖掉真实事务边界。

## 变更文件清单

总计划为编排文档，代码修改清单为零。执行代码授权以各子计划表为准，每份≤10个文件、≤2个子系统。新增表为独立存储，既有 mail_send_attempt/mail_record/草稿/审核表新增字段均为零，满足共享表每计划≤1新字段限制。同一文件跨阶段修改必须顺序进行，禁止并行编辑。

## 验收标准

- I-1 — 可控时钟 59,999ms SMTP=0，60,000ms 才可 claim；重复提交 dueAt 不变；重发后重新60秒。
- I-2 — 真实 MySQL 双线程取消/领取竞争，只能一个事务赢。59秒打开确认、61秒确认，返回409且 UI 回读权威状态。
- I-3 — 重复/并发提交、重发、撤回及过期 revision 请求不重复投递。指纹字节回归覆盖现有旧稿、会话 UUID、附件顺序、审核稿版本。
- I-4 — 持久快照往返及进程重启后正文/ICS/附件摘要相同。snapshot 中密码与绝对路径均不存在，未知 schemaVersion fail closed。
- I-5 — 真实事务注入失败，SENT/事实/排期/关闭/审核/job 一起提交或回滚；SMTP结果未知不重发，发送后归档失败仍为 SENT。
- I-6 — WAITING/SENDING 状态的 save/discard/reopen 被拒；撤回恢复 context 原值；review APPROVED 与版本保留；旧入口恢复冲突不覆盖新稿。
- I-7 — user1 NONE/READ/FULL、非owner、admin，入队到投递间撤权/删除目标/换收件人/退订/文件失效均覆盖。finalize 不因事后撤权丢结果。
- I-8 — 真实 SQL summary/count/timeline/membership/日期账号方向筛选、同秒游标、失败后重试成功仅一行，reviewContext 自身排队不变/新来信改变。
- I-9 — 浏览器验证列表单独刷新、断网恢复、页面隐藏/显示、切专家、退出后旧回包不更新。同步兼容入口与WAITING提示均核验。
- I-10 — SMTP认证错误不透传原文；失败图标 tooltip+aria；UNKNOWN 禁重发；历史失败缺快照禁重发。
- I-11 — 独立线程执行、重启领取、超时SENDING只收敛UNKNOWN、关闭入口开关仍处理旧任务、依赖关闭RabbitMQ也能执行。
- S-1～S-4 — 见06逐字CSS/DOM合同与截图验收，不能以预览注入脚本冒充生产实现。
- 自动测试执行记录必须标明跳过项。MySQL IT 需要 `-DmysqlIt=true`，Flyway IT 需要 `-DmigrationIt=true`，普通 `mvn test` 绿不能替代这两类证据。

## 人工验收清单

本节是全功能人工验收权威版，各子计划另有针对本阶段的黑盒清单。开始人工验收时才导出 `manual-reply-delay-master-acceptance.md`，包含勾选框、验收人、日期、结果/备注。

### A-1 普通回复倒计时与撤回
- 前置条件 — 隔离验收环境连接可记录收件的 SMTP 测试服务，账号 admin，一位有真实来信的测试专家，保存正文与一个txt附件。
- 操作步骤 — 1. 点击发送。2. 刷新页面并切换专家再返回。3. 40秒时点击撤回，再点取消。4. 50秒时重新点击撤回并确认。
- 预期结果 — WAITING保持原截止时间；取消确认后继续倒计时；确认后显示已撤回，SMTP收件0封，正文/附件仍可编辑，超过原截止时间仍0封。
- 覆盖 — O-1/O-2/O-4、I-1/I-2/I-4/I-6/I-9、X-1/X-2/X-5/X-7。

### A-2 到期成功与会议附件
- 前置条件 — 同 A-1，生成有效会议预览，并加两个顺序不同的通用附件。
- 操作步骤 — 1. 提交并关闭浏览器。2. 60秒后重新登录。3. 下载已发附件并查看排期。
- 预期结果 — 60秒前0封，正常空闲 worker 在期限后一个轮询周期内开始投递；SMTP共1封；时间线1张卡片已发送；排期1条；ICS与预览摘要相同，通用附件顺序和字节一致；普通草稿关闭。
- 覆盖 — O-1、N-3/N-4、I-1/I-4/I-5/I-8/I-11、X-4/X-5/X-8。

### A-3 可重试失败
- 前置条件 — SMTP测试服务配置第一次返回451，后续成功。测试专家列表当前页可见。
- 操作步骤 — 1. 提交等待失败。2. 选择另一专家。3. 查看失败专家列表图标。4. 回到失败卡片点重新发送并取消。5. 再点并确认。
- 预期结果 — 列表与卡片出现红色感叹号，公开提示「发送暂时失败，可安全重试」；取消无新投递；确认后重新60秒；成功后异常图标消失且无重复卡片/排期。
- 覆盖 — O-2/O-3、I-3/I-5/I-8/I-9/I-10、X-4/X-7。

### A-4 未知结果与永久失败
- 前置条件 — SMTP测试服务可模拟 DATA后断链及550拒绝，分别准备两个独立测试专家。
- 操作步骤 — 1. 分别提交。2. 查看卡片原因与按钮。3. 刷新、重复提交旧请求。
- 预期结果 — 断链显示「发送结果待确认，请勿重复发送」，无可执行重发；550显示固定永久失败原因，重发禁用。任何旧请求都不再次调用SMTP。
- 覆盖 — O-3、N-4、I-3/I-5/I-10、X-1/X-4。

### A-5 二次确认跨过截止时间
- 前置条件 — 一封WAITING任务。
- 操作步骤 — 1. 在59秒打开撤回确认。2. 超过61秒再确认。3. 观察回读。
- 预期结果 — 返回409「已超过撤回期限」，显示实际等待投递/发送中/已发送状态，不出现撤回成功提示。
- 覆盖 — O-2、I-1/I-2/I-9。

### A-6 已审核回复
- 前置条件 — user1有测试专家READ授权，提交草稿，由admin审核通过。
- 操作步骤 — 1. admin发送已审核回复。2. 二次确认撤回。3. 再次发送。4. 另一轮等待期间加入真实新来信，再观察投递并在审核面板重新确认上下文。
- 预期结果 — 首次撤回SMTP=0，审核稿仍APPROVED且正文不可改，事件有SEND_QUEUED/SEND_CANCELLED；再次提交可发；新来信一轮显示上下文变化失败，重新确认后才可排队。user1不能调用审核撤回/重发。
- 覆盖 — O-4、N-2、I-3/I-6/I-7/I-8、X-3/X-6/X-9。

### A-7 权限变化
- 前置条件 — user1对测试专家FULL，admin可改授权。
- 操作步骤 — 1. user1提交普通回复。2. admin在等待期降为READ或撤权。3. 等待期限，尝试重发并刷新。
- 预期结果 — SMTP=0；READ仅见脱敏状态和正文，无操作/附件下载能力；撤权后详情403且清除当前显示；直接POST重发403。
- 覆盖 — N-2/N-5、I-7/I-9、X-6。

### A-8 重启和结果收敛
- 前置条件 — 隔离测试环境，有WAITING任务及可在SMTP前后暂停的测试投递服务。
- 操作步骤 — 1. WAITING时重启应用。2. 另一封在SMTP调用后终止进程并重启。3. 等待恢复阈值。4. 关闭新入口开关观察已排队任务。
- 预期结果 — 第一封按原dueAt处理；第二封收敛UNKNOWN且不自动重投；关闭开关后原WAITING仍被处理。
- 覆盖 — N-4、I-2/I-5/I-11、X-8。

### A-9 历史分页与筛选
- 前置条件 — 测试库准备两账号、同秒的旧入站/出站以及WAITING/FAILED/SENT/CANCELLED任务，单专家数量超过50封。
- 操作步骤 — 1. 分账号和日期筛选。2. 连续加载更早页直到结束。3. 将一条失败任务重发成功再刷新。
- 预期结果 — 各消息source/id无重复；失败变成功仍1张卡；待发不增加发件成功数；账号筛选只出现该账号邮件；历史失败无快照不出现可执行重发。
- 覆盖 — N-5、I-8/I-10、X-4。

### A-10 既有发送回归
- 前置条件 — 测试SMTP；准备批量模板、自动回复规则、专家模板发件、普通和RAG人工草稿。
- 操作步骤 — 1. 各执行一笔非人工回复发信。2. 人工回复加入安全确认触发内容并取消确认。3. 发带RAG的有效人工回复。
- 预期结果 — 非目标路径未创建delay job；安全确认取消不入队；RAG成功后按原序保存fact_code且没有QA事实混写；人工发送不扣自动每日额度。
- 覆盖 — N-1/N-3、I-4/I-5、X-1/X-4。

### A-11 位置、图标与无障碍
- 前置条件 — WAITING和安全失败各一封，桌面1440px及手机390px。
- 操作步骤 — 1. 查看邮件底部。2. 点击处理按钮再取消。3. 用Tab聚焦失败图标与确认按钮。4. 缩窄屏幕。
- 预期结果 — 操作与翻译同footer，确认行可换行；图标17px、#c53e32，聚焦显示公开原因；手机按钮命中区至少44px，无横向溢出；确认交互无额外红色错误大卡。
- 覆盖 — O-2/O-3、N-5、S-1～S-4、X-7。

### A-12 旧入口与新草稿冲突
- 前置条件 — admin打开旧来信详情页人工编辑器，另一个浏览器打开同专家普通草稿。
- 操作步骤 — 1. 旧入口提交。2. 另一浏览器编辑/保存同目标草稿。3. 撤回旧任务并按冲突提示查看恢复内容。
- 预期结果 — 提交提示已排队；任何更高版本草稿不被覆盖；冲突时旧任务仍取消且可从取消卡片读取原始正文/附件，显式恢复前提示保留当前稿或另存处理。
- 覆盖 — O-4、N-2、I-6/I-9、X-2/X-7。

## 计划自检与边界

审计已覆盖 schema、生产/测试/脚本 grep、前端覆盖链与旧入口。没有运行开发后测试，也未声明功能已实现。新表/worker/API为本计划设计，所有代码现状结论附当前文件位置。

自检回执见[文件与源码基线校验](manual-reply-delay-evidence/plan-structure-check.json)：7份子计划文件数为7/7/10/7/7/9/8，章节顺序与相对链接有效，205份源码哈希复核无漂移。这是计划静态自检，不是实现测试。前端合同位于06，总人工清单12项尚未执行。

知识消费：本轮载入21条相关知识并更新命中信息。除不变量已标注来源外，K-manual-send-final-validation-complete用于02准备顺序；K-mail-record-save-sites用于全写路径审计；K-operator-send-quota-paths用于N-3/A-10；K-mailbox-chat-css-byte-contract和K-frontend-cache-key-triad用于06逐字CSS/资源合同；K-plan-quantified-claims-need-grep-receipts用于证据目录。K-mail-attachment-write-paths经复核限定为收信存储，另明确outbound_mail_attachment独立边界，未照搬旧表模型。

知识回写：补充RAG提交后写点、出站附件边界，新增reviewContext共用UI union与双submit helper事实；Flyway版本断言条目命中达到10，已向CLAUDE.md提升一行规则。选中条目均已本日复核，无需衰减归档；这些条目各管指纹、投递分类、草稿、附件、样式等不同约束，没有5条相同主题可无损合并。仓库没有agents/*.md或templates/project-CLAUDE.md角色文件，未凭空新建角色配置。现存用户修改保留。

拟采用的设计选择包括数据库队列、服务器截止时间、UNKNOWN禁重发、已审核撤回后仍APPROVED、原内容永久失败禁重发。生产开关在07完整验收后由发布环境显式启用，仓库默认false；实际上线仍按项目发布流程。以上设计不冒充当前已实现行为。
