# AI 回复第 7 步：最终内容复验、幂等发送与结果审计

## 需求描述

- 可观察结果：运营点击人工发送后，服务端对最终变量渲染后的纯文本、HTML 可见文本及链接执行当前态复验；有无依据的高风险事实、信任替代话术、身份误述、敏感材料或未授权动作时，在 SMTP 前确定性阻断。
- 可观察结果：相同最终内容发生双击、并发请求、网络重试或页面重放时，最多调用一次 SMTP；已发送请求返回原发送结果，不生成第二封邮件、第二条发送记录或第二次 QA 采用审计。
- 可观察结果：SMTP 明确可安全重试时可用同一请求再次尝试；SMTP 是否已接收无法确认时进入 `DELIVERY_UNKNOWN`，服务端和页面都明确提示“不要重复发送”，不得盲重试。
- 可观察结果：成功发送后，最终纯文本、HTML、Message-ID、发送账号、QA 事实顺序、发送结果和操作人审计可一致追踪；失败/未知尝试也有持久化结果，不伪造成已发送。

必须保持不变：

1. 第 4 步的 Grounded JSON、自然段和统一组装契约不变；第 5 步的 trust/action/readiness 计算不变；第 6 步的草稿审计与编辑期 preflight 仍是写作辅助。
2. 人工发送不得读取历史草稿 `READY / NEEDS_REVIEW / BLOCKED`、draftHash、Prompt 版本或前端 preflight 状态作为权限；只依据当前服务端事实、最终正文和当前发送上下文。（来源：K-ai-generation-observability-not-send-gate、K-ai-adopt-direct-send-no-residual-gates）
3. 不恢复人工审核 modal、确认勾选或 draft identity gate。纯人工正文只要当前最终内容未触发确定性安全规则，仍可直接发送。（来源：K-ai-draft-edit-not-review-confirmation）
4. QA `answerBody` 仍是唯一事实正文；客户端 `qaRuleIds` 只能触发服务端 canonicalization，不能成为发送 authority。（来源：K-answerbody-source-exclusive、K-ai-review-server-authoritative-snapshot）
5. 人工发送继续使用现有账号选择、变量渲染、multipart text/html 和 `MANUAL_RICH_REPLY` 邮件类型；允许已停用但仍可人工选择的真实账号，不计入自动发送配额，不允许 simulator。（来源：K-manual-rich-render-before-send、K-operator-send-quota-paths、K-sender-account-enabled-scope）
6. 不修改数据库 schema/Flyway，不新增前端输入、按钮、modal 或 CSS；不改变自动回复、项目介绍邮件、会议邮件和 INTRODUCTION 的发送/重试语义。

范围说明：

- 本计划是第 7 步可发布主链：最终内容安全、幂等投递、结果审计。
- 流程图中的“专家后续是否继续交流、是否主动约谈、重复质疑或沉默”反馈学习拆为第 7b 步；本计划不自动修改 Prompt、QA 事实或风格样本。
- 不在本计划内：`DELIVERY_UNKNOWN` 自动对账、人工强制重发 UI、新版发送监控台、历史 QA 版本库、`mail_record.source_inbound_id` 数据补链、第三方 SMTP webhook。

## 关键不变量

### Invariant I-1：最终发送只信任当前服务端状态
- Rule：发送时重新加载 inbound、contact、研究充分性、发送账号和 QA 规则；客户端只提供候选事实与正文。历史 generationState/readiness、expectedEvidenceSetVersion、draftHash 和前端 PASS/WARNING 不得参与允许/拒绝判断。
- Applies to：`sendManualRichReply()`、最终复验、发送尝试创建。
- Violation consequence：旧草稿状态变成人工审批权，或已禁用事实仍可授权发送。
- 来源：K-ai-generation-observability-not-send-gate、K-readiness-evidence-revalidation。

### Invariant I-2：复验对象必须是最终可发送内容
- Rule：先完成 raw template、subject、text、HTML 的变量渲染，再构造最终检查文本：最终纯文本 + 最终 HTML 的可见纯文本 + 最终 HTML 中全部 href；不得只检查编辑器 raw text、不得截断后校验。最终检查文本非空且最多 20,000 字符，超限在 SMTP 前拒绝。
- Applies to：变量渲染、claim/trust/action 校验、SMTP 入参。
- Violation consequence：变量或 HTML 链接可绕过第 6 步 preflight，形成“检查的是 A，实际发的是 B”。
- 来源：K-renderText-all-callers、K-manual-rich-render-before-send；original。

### Invariant I-3：最终 QA 事实必须重新 canonicalize
- Rule：非空 QA 候选必须按当前 inbound 与 researchProfileSufficient 重新执行 fact selection；只使用当前 enabled、非 NEVER、非空 `answerBody` 的 canonical IDs，并对最终内容执行 claim/source 校验。候选全部失效、无依据高风险 claim 或当前 trust/action 硬违规均阻断；纯人工空 facts 不因“无 QA 证据”本身阻断。
- Applies to：最终复验、QA 关联、幂等指纹。
- Violation consequence：客户端伪造事实 ID，或把无来源自由文本误判为 QA 支持。
- 来源：K-answerbody-source-exclusive、K-validation-exhaustion-must-block-readiness、K-ai-review-server-authoritative-snapshot。

### Invariant I-4：发送阻断只来自当前确定性风险
- Rule：阻断集合固定为：无来源数字/URL/高风险承诺、信任/保密替代、角色隐藏、企业确定性误述、敏感材料、CV 缺用途或自愿性、未授权下一步、变量未解析、最终内容/主题边界失败。不得因非关键 PARTIAL、历史 readiness、审计写失败或 preflight 服务暂不可用阻断人工发送。
- Applies to：最终复验结果和 controller 错误映射。
- Violation consequence：人工发送重新被历史 AI 状态绑死，或实际危险内容被软提示放行。
- 来源：K-ai-adopt-direct-send-no-residual-gates、K-ai-preflight-error-stale-exact-text、K-action-sanitizer-preserve-layout。

### Invariant I-5：幂等身份由最终 canonical payload 服务端计算
- Rule：使用长度前缀编码后计算 SHA-256，字段固定为 schemaVersion、inboundProcessingId、contactId、accountCode、normalized recipient、exact subject、finalText、finalHtml、inReplyTo、ordered canonicalQaRuleIds。客户端不得提供或覆盖 idempotency key；Message-ID 在首次 reservation 时生成 UUID-based 值并持久化，安全重试/重复请求复用该值，不得从短 hash 直接构造可预测 Message-ID。
- Applies to：人工富文本发送尝试、Message-ID、重复请求判断。
- Violation consequence：分隔符碰撞、客户端复用 key 丢信，或双击产生不同身份。
- 来源：K-message-id-fingerprint；original。

### Invariant I-6：SMTP 前必须先提交发送占位
- Rule：`mail_send_attempt` 的 `PREPARED -> DELIVERY_IN_PROGRESS` 必须在独立事务中成功提交后才能调用 SMTP；创建使用数据库唯一键和原子 insert-if-absent，claim 使用 compare-and-set。进程在 SMTP 前后崩溃时，占位不得随外层事务回滚。
- Applies to：`ManualReplySendAttemptService.prepareAndClaim()`、delivery coordinator。
- Violation consequence：SMTP 已接收但数据库占位回滚，恢复后重复发送。
- 来源：original。

### Invariant I-7：重复请求按持久化状态 fail closed
- Rule：同一指纹：`SENT` 返回原 mail record；`DELIVERY_IN_PROGRESS / DELIVERY_UNKNOWN` 不调用 SMTP；`FAILED_SAFE_TO_RETRY` 仅一个 CAS winner 重试；`FAILED` 不自动重试。若短 key 命中但完整 hash/metadata 不一致，按碰撞阻断。
- Applies to：重复 HTTP、双击、超时重试、并发请求。
- Violation consequence：未知投递被二次发送，或 hash 前缀碰撞错认成功。
- 来源：original。

### Invariant I-8：SMTP 结果采用保守分类
- Rule：成功仅接受 delivery status=`SENT` 且 category=`SUCCESS`；明确 4xx 或明确未进入投递的认证失败可记 `FAILED_SAFE_TO_RETRY`；明确 5xx 记 `FAILED`；无 SMTP code、非认证 infrastructure、未分类 MessagingException、unchecked exception、成功后 DB finalize 失败均视为 `DELIVERY_UNKNOWN`。未知状态永不自动重发。
- Applies to：`SmtpMailDeliveryService.DeliveredMail` 解释和异常处理。
- Violation consequence：可能已接收的邮件被当作普通失败重发。
- 来源：original。

### Invariant I-9：一个尝试只对应一条结果记录
- Rule：每个 `mail_send_attempt` 最多关联一条 `mail_record`；成功为 `sendStatus=SENT`，其他结果统一为 `sendStatus=FAILED`，详细状态以 attempt.status 和有界 `errorSummary` 前缀表示。安全重试更新同一记录，不创建新记录；sentAt 只在成功时写。
- Applies to：attempt finalize、`mail_record` 唯一 FK、监控读取。
- Violation consequence：发件箱、失败监控和投递事实彼此矛盾。
- 来源：K-mail-record-source-inbound-id；original。

### Invariant I-10：QA 关联和操作审计只在首次成功后形成
- Rule：首次 `SENT` finalize 按 canonical 顺序写 `mail_record_qa_rule`；随后 best-effort 写一次既有 `SEND_MANUAL_COMPOSED_REPLY` 或 `SEND_MANUAL_RICH_REPLY`。重复成功、失败和安全重试失败不得重复关联或重复发送审计；审计失败不能回滚已发送状态。
- Applies to：QA audit、operator action log、重复请求。
- Violation consequence：采用率被重复计算，或日志失败把已投递邮件伪装成未发送。
- 来源：K-qa-outbound-render-seams、K-rich-reply-qa-audit-reuse、K-audit-selected-source、K-review-event-audit-payload-bounds。

### Invariant I-11：现有人工账号和渲染语义不变
- Rule：继续使用 `getManualSendAccount()`，拒绝 simulator，允许人工选择的 disabled 账号，不增加 todaySentCount；raw template 仍只在未编辑时参与变量渲染，最终 SMTP 始终发送 multipart text/html；首次 reservation 生成 UUID-based Message-ID，后续相同 attempt 始终复用。
- Applies to：人工发送账号、配额、变量、MIME。
- Violation consequence：第 7 步意外改变运营账号能力、配额或富文本格式。
- 来源：K-ai-preview-raw-adoption-boundary、K-manual-rich-render-before-send、K-message-id-fingerprint、K-operator-send-quota-paths、K-sender-account-enabled-scope。

### Invariant I-12：新状态不得改变其他发送路径
- Rule：给 `MailSendAttemptStatus` 增加状态和 repository 原子方法必须是 additive；现有 INTRODUCTION 的 PREPARED/SENT/FAILED、唯一键、quota/account 计数和 `ManualOutreachTxHelper` 行为原样保留。
- Applies to：共享 attempt domain/repository、migration schema。
- Violation consequence：人工回复改造破坏初次触达发送。
- 来源：original。

### Invariant I-13：输入、错误与审计均有稳定边界
- Rule：subject trim 后非空且最多 255；final validation text 最多 20,000；HTML、错误摘要、账号、recipient、hash metadata 按现有列上限拒绝或有界保存，禁止静默截断参与指纹/校验的内容。controller 对 blocked、safe retry、unknown/in-progress、permanent failure 返回稳定 HTTP 状态和中文信息，不回显凭据或异常堆栈。
- Applies to：service DTO、attempt/mail record、controller。
- Violation consequence：库写失败发生在 SMTP 后，或用户把未知状态误认成普通失败继续点击。
- 来源：K-review-event-audit-payload-bounds；original。

## 现状审计

### `mail_send_attempt`
- Schema：V23 创建；V24 增加 recipient、subject、body、contentType、quotaCounted、accountCountedAt，并让 `mail_record.mail_send_attempt_id` 唯一关联。现有唯一键是 `(orcid_id, mail_type)`，`mail_type` 上限 50。
- Write paths（全部）：`ManualInitialOutreachService` 创建/恢复 INTRODUCTION PREPARED；`ManualOutreachTxHelper` 写 SENT/FAILED；V23/V24 migration seed/backfill。当前人工富文本回复不使用该表。
- Read paths（全部）：上述两个 INTRODUCTION service；`MailRecordRepository.findByMailSendAttemptId()` 读取关联结果。
- 改造 seam：人工回复使用 `mailType=MANUAL_RICH:<hash前32位>`，完整 64 位 hash 放入 attempt.body 并标记内部 contentType；命中后必须比较完整 hash、recipient、subject、account，不能只信短 key。原 INTRODUCTION 路径不改。

### `mail_record`
- Schema：V1 建表；V15 增加 sender/triggered/source；V23 增加 errorSummary/attempt FK；V24 将 attempt FK 设唯一；V31 增加检索索引。
- Write paths（全部）：`AutoMailReplyService` 写 inbound、QA 自动回复和会议邮件；`PendingMailOperationService` 写当前人工富文本回复；`ManualExpertMailService` 写人工专家邮件；`MeetingScheduleService` 写确认邮件；`ManualOutreachTxHelper` 写初次触达成功/失败；V24 backfill。
- Read paths（全部）：`MailboxService`；`MailMonitoringService`；`BounceCollectionService`/`BounceRateMonitorService`；`AutoMailReplyService`/`AutoReplyPreviewService`/`AutomaticApplicationPromotionService`；`ManualInitialOutreachService`；`ExpertContactManagementService`；`AiTrainingController`/`AiQaExtractionService`；`InboundMailSummaryController`/`UnmatchedInboundMailController`/`UnmatchedInboundMailService`；`DocumentTextExtractor`/`ExpertDocumentBrowseService`。
- 改造 seam：仅把 `PendingMailOperationService` 的人工富文本写入移至新 finalize service；继续写 `mailType=MANUAL_RICH_REPLY`、`triggeredBy=OPERATOR`、最终 text body、SENT/FAILED 和 errorSummary，其他 caller/reader 不改。

### `mail_record_qa_rule`
- Schema：V42 建表，唯一键 `(mail_record_id, qa_rule_id)`，带 ordinal。
- Write paths（全部）：`AutoMailReplyService`、`ManualExpertMailService`、`PendingMailOperationService`。
- Read paths（全部）：`QaRuleAuditService.findByMailRecordIdOrderByOrdinalAsc()`，用于建议/采用/发送审计和 selected-source fallback。
- 改造 seam：人工富文本改由成功 finalize 写 canonical IDs；利用唯一键和 attempt 状态保证一次写入。失败/未知不写，以免被统计成已采用发送。

### `operator_action_log`
- Schema/mapping：V19 建表；`OperatorActionLog` 映射 target/expert/inbound/action/before/after/operator/note/createdAt，before/after 为 TEXT，无 payload schema version 或唯一业务键。
- Write paths（全部）：`OperatorActionLogService.record()` 是唯一 repository save；caller 为 `ExpertContactManagementService`、`ExpertIndexLevelOperationService`、`ExpertOperatorStatusService`、`BounceController`、`PendingMailOperationService`、`UnmatchedInboundMailService`、`AiReplyReviewAuditService`。
- Read paths（全部）：`OperatorActionLogService.search()`/后台 controller；`UnmatchedInboundMailController.getUnmatchedDetail()`；`QaRuleAuditService` 的发送采用指标与 selected-source fallback；repository 的 latest AI draft 遗留 seam。
- 改造 seam：沿用 `SEND_MANUAL_COMPOSED_REPLY / SEND_MANUAL_RICH_REPLY`；只在首次成功 finalize 后 best-effort 写一次。新增 attemptId、短 fingerprint、messageId、mailRecordId 和 deduplicated=false 等有界元数据，不保存完整正文/hash metadata 或 SMTP 凭据。

### `qa_rule` 与第 6 步 preflight
- Schema/mapping：`QaRule` 以 id 为主键；本计划读取 enabled、replyPolicy、answerBody、coverageKeys、displayName、updatedAt。`answerBody` 是事实正文，库内没有 immutable revision 表。
- Write paths（全部）：`QaRuleManagementService.createRule/updateRule/setRuleEnabled/deleteRule()`；Flyway QA seed/repair/backfill migrations。本计划不新增写入。
- Read paths（全部类别）：`QaFactSelectionService`/`QaMatchService`；`AiReplyDraftService`/`AiReplyPointByPointComposer`/`AiReplyHighRiskClaimValidator`；`GroundedAutoReplyDecisionService`；`PendingMailOperationService`/`ManualExpertMailService`/`AutoMailReplyService`；`InboundMailTagService`/`MailMonitoringService`/`QaRuleAuditService`/`QaRuleManagementService`；`MailComposeTemplateService`。
- 第 6 步 `preflightEditedAiReply()` 已能按当前 inbound/contact/facts 复验编辑器纯文本，但它只读、只提示，且尚未覆盖变量渲染后的 text/html/href。
- 改造 seam：管理写入后的当前 enabled/answerBody 必须立即被最终发送读取；提取/复用同源当前态校验，不从 preflight response 或审计日志恢复 authority；发送端增加 final phase 并将阻断码限定在 I-4。

### 当前人工富文本发送链
- `PendingMailOperationService.sendManualRichReply()` 当前在一个 `@Transactional` 中完成读取、canonicalize、raw/变量渲染、SMTP、mail record、QA 关联；进程崩溃或事务回滚时不能提供可靠投递幂等性。
- 当前成功/失败都会创建 mail record，`mailSendAttemptId/sourceInboundId` 为空；QA claim 仅校验 raw text，未完整校验最终 HTML/link。
- 当前 UI 主题/正文校验后直接 POST，并把普通失败显示为 alert；没有 idempotency key。第 7 步保持直接提交，不要求前端生成 key；controller 必须把 unknown/in-progress 映射成明确的“不要重复发送”。

## 实现方案

### T1：扩展发送尝试状态与原子存储操作
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/MailSendAttemptStatus.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MailSendAttemptRepository.kt`
- 增加 `DELIVERY_IN_PROGRESS`、`DELIVERY_UNKNOWN`、`FAILED_SAFE_TO_RETRY`；保留 PREPARED/SENT/FAILED 原值。
- repository 增加 MySQL 原子 `INSERT IGNORE` reservation 和 CAS claim/update 方法；人工 key 固定 `MANUAL_RICH:` + fingerprint 前 32 位，长度不超过 50。
- insert 字段显式覆盖 schema 的非空/长度约束；短 key 命中后由 service 比对 full hash/metadata，碰撞不得复用。
- 现有 `findByOrcidIdAndMailType()`、CrudRepository save 和 INTRODUCTION caller 不改签名。
- 遵守：I-5、I-6、I-7、I-12、I-13。

### T2：新增独立事务的人工回复投递协调器
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt`
- 新增 canonical payload/fingerprint builder，使用 UTF-8 长度前缀编码；首次 reservation 生成 `<manual-rich-{UUID}@weibo.com>` 并写入 attempt，重试与 dedup 读取同一持久化值。
- `prepareAndClaim()` 使用 `REQUIRES_NEW`：insert-if-absent 后读取、校验 full hash/metadata，并按 I-7 CAS claim。返回 `CLAIMED / DEDUP_SENT / SAFE_RETRY_CLAIMED / IN_PROGRESS / UNKNOWN / PERMANENT_FAILED`，不在未 claim 时调用 delivery。
- `finalizeSuccess()` 使用独立事务：只允许 DELIVERY_IN_PROGRESS；创建或更新唯一关联 mail record、写 SENT、sentAt、最终正文/subject/account/messageId，再按 ordinal 写 canonical QA associations，最后 attempt=SENT。
- `finalizeFailure()` 使用独立事务：将详细状态写 attempt；创建/更新同一 mail record 为 FAILED，errorSummary 使用稳定前缀且有界；不写 QA association/sentAt。
- SMTP 成功后 finalize 异常不得再调用 SMTP；尽力将 attempt 标 UNKNOWN，标记失败时仍失败则返回 UNKNOWN 并保留已提交的 IN_PROGRESS 占位供人工核查。
- `recordSuccessfulSendAudit()` 只由本次 claim winner 在成功事务提交后 best-effort 调现有 action type；DEDUP_SENT 路径不得调用，audit 失败不改变 SENT。
- 新写入仍由既有 reader 消费：`MailboxService` 展示 SENT/FAILED 最终正文，`MailMonitoringService` 统计失败，bounce service 按稳定 Message-ID 关联，`QaRuleAuditService` 读取成功 association/action；不要求 reader 识别 attempt 详细状态。
- 单测覆盖两请求并发只有一个 claim、所有状态分支、full hash 碰撞、成功一次落库、失败更新同一 record、安全重试、unknown fail closed、finalize/audit 异常和 INTRODUCTION 状态不受影响。
- 遵守：I-5 至 I-10、I-12、I-13。

### T3：把最终当前态复验接入 SMTP 前主链
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`
- 去除 `sendManualRichReply()` 外层长事务；保留现有 inbound/contact/account/raw selection/canonical fact selection/变量渲染顺序。
- subject、finalText、finalHtml 完成后，构造 I-2 的 exact final validation input；复用第 6 步当前态事实、claim、trust、role/enterprise、action 校验器。新增内部 final result，区分 blocking codes 与 observability-only codes；不得调用第 6 步 endpoint 或信任前端 warning。
- QA 候选非空但 canonical facts 全失效、最终高风险 claim 无来源时阻断；候选为空的纯人工回复仍执行通用 trust/action/placeholder 校验，但不因 NO_EVIDENCE 阻断。
- 复验通过后构造 canonical payload，调用 T2 claim；仅 CLAIMED/SAFE_RETRY_CLAIMED 调 `SmtpMailDeliveryService.send()`，使用稳定 Message-ID 和现有 multipart final text/html。
- 按 I-8 映射 delivery/exception，再调用 T2 finalize。重复 SENT 返回原 `PendingMailSendResult`；IN_PROGRESS/UNKNOWN/FAILED 返回稳定状态，不进入 SMTP。
- 保持 manual account、disabled account、simulator、todaySentCount、raw/template、HTML 和 QA canonical 顺序现有语义。
- 单测覆盖：最终变量新增数字/URL、HTML-only 文案/href、未解析变量、QA 失效、纯人工无事实、历史 BLOCKED/preflight error 不参与、长度边界、claim 前无 SMTP、dedup、4xx/5xx/auth/无 code/unchecked/finalize failure、multipart 与账号/配额回归。
- 遵守：I-1 至 I-8、I-11、I-13。

### T4：公开稳定发送结果，不新增前端 gate
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt`
- 现有 `send-manual-rich-reply` endpoint 继续接收同一 request；不增加客户端 idempotencyKey、draftHash、readiness 或 confirmation 字段。
- SENT/重复 SENT 返回现有成功 body；最终校验失败返回 422 + 稳定阻断码摘要；FAILED_SAFE_TO_RETRY 返回 503 并说明可再次点击；DELIVERY_IN_PROGRESS/UNKNOWN 返回 409，中文固定包含“发送状态未知，请勿重复发送”和 Message-ID；FAILED 返回 422/502，说明需修改内容或处理配置后形成新请求。
- controller 不把异常堆栈、SMTP 凭据、完整 body/hash 放入 response。`app.js` 继续使用现有 catch alert 展示服务端信息，不增 modal、不读历史状态、不改样式。
- controller 测试覆盖所有状态与 HTTP/中文合同、重复 SENT 成功、request DTO 无 idempotency 字段、历史 readiness 不参与。
- 遵守：I-1、I-4、I-7、I-13。

### 开发前研究检查点

1. R-1：在真实 MySQL 测试库验证 repository 的 `INSERT IGNORE` affected rows 和 CAS 返回值；若 H2 与 MySQL 行为不同，不得用 H2 通过替代真实门禁。
2. R-2：用本地 SMTP stub 分别模拟 250、明确 451、明确 550、认证失败、连接中断、DATA 后断链；先记录 `DeliveredMail` 实际字段，再锁定 I-8 映射，禁止只按 `errorCategory` 名称猜测。
3. R-3：用调用图和单测确认只有 CLAIMED/SAFE_RETRY_CLAIMED 的成功请求触发 audit，DEDUP_SENT/失败/未知均绕过；不得为此新增 operator log schema、action type 或未列入清单的 repository 文件。
4. R-4：构造两个线程同一 payload 的 transaction integration test；若 Spring proxy 自调用导致 `REQUIRES_NEW` 不生效，必须保持 T2 为独立 bean，不得退回同类私有方法。
5. R-5：确认现有 mail record 失败监控只识别 `sendStatus=FAILED`；详细状态只放 attempt/errorSummary，避免修改发件箱/监控 reader。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/MailSendAttemptStatus.kt` | 增加投递中、未知、可安全重试状态 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MailSendAttemptRepository.kt` | 原子 reservation、CAS claim/finalize seam |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt` | 新增指纹、独立事务、结果落库、成功审计协调器 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 最终渲染复验、幂等投递编排、结果分类 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt` | 稳定 HTTP/中文发送结果合同 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt` | 状态机、并发、事务、一次落库/审计测试 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | 最终内容门禁、SMTP 分类、现有语义回归 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt` | HTTP 状态、文案、重复成功合同测试 |

边界：8 个实现/测试文件，2 个子系统（发送尝试持久化协调、人工富文本发送入口），0 个数据库字段，0 个前端/CSS 文件。需要新增 migration、前端 UI、自动对账或反馈学习时必须停下另立计划，不得静默扩展。

## 验收标准

### 自动化

- I-1：service 测试以历史 BLOCKED、错误 expectedEvidenceSetVersion 和伪造客户端 IDs 调用发送；断言重新加载当前 inbound/contact/facts，历史字段零读取、零 gate。
- I-2：分别把风险只放在最终变量、HTML 可见文本和 href；断言均在 SMTP 前阻断。20,000 通过、20,001 拒绝且无截断校验。
- I-3：候选包含 disabled/NEVER/空/不匹配规则；断言只使用服务端 canonical IDs。QA 候选全失效阻断，空候选安全人工正文通过。
- I-4：逐个断言固定 blocking code；非关键 PARTIAL、历史 readiness、审计/preflight 失败不阻断。
- I-5：相同 exact payload 指纹相同、任一字段改变指纹改变；长度前缀无分隔符碰撞；客户端 request 无 key 字段；首次 Message-ID 为 UUID-based，重复/重试值相同。
- I-6：transaction test 证明 reservation/claim 已提交后才调用 delivery；模拟 delivery 后 finalize 异常，另一个事务仍能读取 IN_PROGRESS/UNKNOWN 占位。
- I-7：并发相同 payload 只有一个 claim winner；SENT dedup，IN_PROGRESS/UNKNOWN/FAILED 零 delivery，SAFE_RETRY 只有一个 CAS winner；短 hash/full hash 不一致阻断。
- I-8：250+SUCCESS、451、550、auth、无 code、infrastructure、unchecked 和 finalize failure 分别断言为 SENT/SAFE_RETRY/FAILED/SAFE_RETRY/UNKNOWN/UNKNOWN/UNKNOWN/UNKNOWN。
- I-9：每个 attempt 只关联一个 mail record；安全重试更新同一 ID；详细 attempt 状态与 mail record SENT/FAILED 双表示一致，sentAt 只在 SENT。
- I-10：首次 SENT 按 canonical ordinal 写 associations 和一条 SEND action；重复、失败、unknown 零新增；audit exception 不改变 SENT。
- I-11：现有测试断言 disabled manual account 可用、simulator 拒绝、todaySentCount 不变、raw/rendered 变量和 multipart text/html 不变。
- I-12：`ManualInitialOutreachServiceTest`、`ManualOutreachTxHelperTest` 全部通过；新 repository 方法不改变旧 find/save 及 PREPARED/SENT/FAILED。
- I-13：subject 255/256、正文 20,000/20,001、errorSummary/response 边界与 422/503/409 合同测试；反向断言无 stack、凭据或正文泄露。
- 交互集成：`ManualReplySendAttemptServiceTest` 覆盖 attempt → mail record → QA association → operator audit；`UnmatchedInboundTrustWorkbenchTest` 覆盖 HTTP → service 状态；真实 MySQL + SMTP stub 按 R-1/R-2 验证原子 SQL 和投递分类。
- 运行：
   ```bash
   JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
     -Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTrustWorkbenchTest,UnmatchedInboundTrustWorkbenchTest,PendingMailOperationServiceTest,ManualInitialOutreachServiceTest,ManualOutreachTxHelperTest
   JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean test
   npm test
   git diff --check
   ```

### 行为

1. 安全的 QA 或纯人工最终内容只发送一次，并在发件箱显示最终正文、Message-ID、账号和 SENT。
2. 最终变量/HTML 新增无来源事实或敏感动作时，SMTP、attempt、mail record、QA association、send audit 均不发生。
3. 同 payload 双击/并发/网络重试最多一封；重复成功返回同 Message-ID/mail record。
4. 451/认证未投递失败可安全重试同 key；550 不自动重试；无 code/断链/数据库 finalize 失败提示 UNKNOWN 并禁止重复发送。
5. 成功 QA 邮件的 `mail_record_qa_rule` 顺序等于服务端 canonical 顺序，审计仅一条；纯人工邮件没有 QA association。
6. 历史 `BLOCKED / NEEDS_REVIEW`、草稿审计失败或第 6 步 preflight 500 不阻断当前安全人工正文。
7. disabled 人工账号仍按现状可发送、simulator 仍拒绝、todaySentCount 不增加；text/html multipart 与变量渲染不变。
8. INTRODUCTION、自动回复、会议邮件及其他 mail record writer/readers 行为不变。

## 人工验收清单

准备：测试环境使用可查询消息数/Message-ID 的 SMTP stub；准备一封已绑定专家的入站邮件、一个 enabled QA 事实、一个 disabled 人工账号；打开浏览器 Network 和 MySQL 只读窗口。

### A-1：安全最终内容正常发送
- 前置条件：SMTP stub 返回 250；入站邮件已绑定专家；草稿包含一个现有联系人变量和一个安全 HTTPS 链接。
- 操作步骤：1. 采用草稿；2. 填写主题；3. 点击发送一次；4. 刷新发件箱并查询 SMTP stub。
- 预期结果：HTTP 2xx；SMTP 恰有 1 封 multipart 邮件，text/html 变量均已替换，Message-ID 格式为 `<manual-rich-{UUID}@weibo.com>`；发件箱恰有 1 条 SENT；无未解析变量、第二条 mail record 或配额增加。
- 覆盖：I-2、I-5、I-9、I-11；需求描述第 1、4 条。

### A-2：最终内容风险在 SMTP 前阻断
- 前置条件：SMTP stub 消息数已记录；准备可渲染金额变量以及可编辑 HTML 链接。
- 操作步骤：1. 分别在变量或 HTML-only 区域加入无来源金额、guarantee、外部 href、passport 请求、未授权会议；2. 每种情况点击发送；3. 查询 SMTP 与数据库。
- 预期结果：每次均 HTTP 422 并显示对应中文风险；SMTP 消息数不变；`mail_send_attempt`、`mail_record`、QA association、SEND action 均无新增。
- 覆盖：I-1 至 I-4、I-13；需求描述第 1 条。

### A-3：并发双击只发送一次
- 前置条件：SMTP stub 返回 250；复制页面实际 request JSON，保持所有字段逐字一致。
- 操作步骤：1. 两个终端同时 curl 提交 exact same request；2. 再用页面快速双击同一发送按钮；3. 查询 SMTP、attempt、mail record、QA association 和 action log。
- 预期结果：所有成功结果指向同一 UUID-based Message-ID；SMTP 消息数只增加 1；同一 attempt、mail record、SEND action 各 1 条，QA ordinal 无重复。
- 覆盖：I-5 至 I-7、I-9、I-10；需求描述第 2 条。

### A-4：明确可安全重试
- 前置条件：SMTP stub 配置为第一次明确返回 451，第二次返回 250；准备一份安全 exact request。
- 操作步骤：1. 发送一次；2. 不改正文/主题再次发送；3. 查询两次响应与数据库。
- 预期结果：第一次 HTTP 503，attempt=`FAILED_SAFE_TO_RETRY`、mail record=FAILED；第二次同 attempt= SENT、同 mail record 更新为 SENT、Message-ID 不变；SMTP 有 2 次明确尝试；失败阶段无 QA association/SEND action。
- 覆盖：I-7 至 I-10；需求描述第 3、4 条。

### A-5：投递未知禁止盲重发
- 前置条件：SMTP stub 配置为接收 DATA 后断链；记录当前消息/调用计数。
- 操作步骤：1. 提交安全 request；2. 用 exact same request 重试两次；3. 查询响应、SMTP 调用和数据库。
- 预期结果：首次及后续均 HTTP 409，文案逐字包含“发送状态未知，请勿重复发送”并显示同一 Message-ID；attempt 为 UNKNOWN 或保守保留 IN_PROGRESS，失败监控 1 条；后续 SMTP 调用为 0、无第二条 mail record/成功审计。
- 覆盖：I-6 至 I-10、I-13；需求描述第 3 条。

### A-6：明确永久失败
- 前置条件：SMTP stub 固定返回明确 550；准备一份安全 request。
- 操作步骤：1. 发送；2. 原样重试；3. 修改正文一个可见字符后再发送；4. 查询 attempt/mail record/SMTP。
- 预期结果：首次 attempt=FAILED、mail record=FAILED；原样重试不调用 SMTP；修改后产生新 fingerprint/new attempt；任一步都不伪造 SENT。
- 覆盖：I-5、I-7 至 I-9；需求描述第 3 条。

### A-7：QA 采用链一致
- 前置条件：两条 enabled AUTO/REVIEW QA 事实可匹配当前来信，另准备一个 disabled/无效 ID；SMTP 返回 250。
- 操作步骤：1. 以乱序 IDs 加无效 ID 发送；2. 原样重复；3. 查询 QA association 和 action log。
- 预期结果：`mail_record_qa_rule` 恰有服务端 canonical 的 2 行，ordinal 为 0/1；`SEND_MANUAL_COMPOSED_REPLY` 恰有 1 条并关联同 mail record/attempt；无无效 ID、重复 association 或重复审计。
- 覆盖：I-1、I-3、I-9、I-10；需求描述第 4 条及 qa_rule → mail_record_qa_rule → audit interaction。

### A-8：纯人工直接发送
- 前置条件：选择一封历史 AI 状态任意的已绑定邮件；不采用 AI、不选择 QA；SMTP 返回 250。
- 操作步骤：1. 输入安全人工主题/正文；2. 点击发送；3. 查询发件箱和 action log。
- 预期结果：HTTP 2xx、mail record=SENT、action 为现有 manual rich 类型；无 QA association；不因 NO_EVIDENCE、历史 readiness 或缺 draftHash 阻断。
- 覆盖：I-1、I-3、I-4、I-10；必须保持不变第 1 至 4 项。

### A-9：账号和配额回归
- 前置条件：存在一个 disabled 但当前人工列表可选的真实账号和一个 SIMULATOR_NOOP 账号；记录真实账号 todaySentCount。
- 操作步骤：1. 用 disabled 真实账号发送安全正文；2. 用 simulator 提交另一封；3. 查询计数、SMTP 和 mail record。
- 预期结果：真实账号 HTTP 2xx 且 todaySentCount 精确不变；simulator 在 SMTP 前拒绝，SMTP/mail record 无对应新增。
- 覆盖：I-11；必须保持不变第 5 项。

### A-10：历史状态不是发送 gate
- 前置条件：准备历史 BLOCKED 或 NEEDS_REVIEW 草稿；让第 6 步 preflight endpoint 返回 500；SMTP 返回 250。
- 操作步骤：1. 将编辑器改为当前安全纯人工文本；2. 点击发送；3. 观察 Network/UI。
- 预期结果：最终服务端复验通过后 HTTP 2xx 且 SMTP 增加 1；页面无审核 modal/确认勾选，发送 request 不含 draft identity/readiness authority 字段。
- 覆盖：I-1、I-4；必须保持不变第 2、3 项。

### A-11：共享发送表回归
- 前置条件：准备可执行现有 INTRODUCTION 的测试联系人、账号和 SMTP 250 配置，并记录配额。
- 操作步骤：1. 走现有初次触达入口发送；2. 查询 attempt、mail record、账号计数；3. 再查人工回复 attempt。
- 预期结果：初次触达仍使用 `mailType=INTRODUCTION` 与 PREPARED/SENT/FAILED 旧状态，原 quota/account 计数语义不变；没有 MANUAL_RICH 状态覆盖该记录。
- 覆盖：I-12；必须保持不变第 6 项及共享 attempt write/read interaction。

### A-12：边界与泄露
- 前置条件：准备 255/256 字符主题、20,000/20,001 字符最终检查文本，并让 SMTP stub 返回包含伪密码的异常消息。
- 操作步骤：1. 逐组提交边界 request；2. 查看 HTTP body、应用日志和数据库错误摘要；3. 查询 SMTP 调用数。
- 预期结果：255 与 20,000 进入正常校验；256 与 20,001 在 SMTP 前返回稳定 4xx；response/log/store 均不含堆栈、伪密码、完整正文或完整内部 hash metadata，无 SQL 静默截断。
- 覆盖：I-2、I-13；需求描述第 1、4 条。
