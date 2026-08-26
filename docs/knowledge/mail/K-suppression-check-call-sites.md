---
id: K-suppression-check-call-sites
domain: mail
created: 2026-08-11
last_used: 2026-08-21
hit_count: 1
source: create-p:unsubscribe-02-suppression-gate
severity: P1
---

经验：抑制名单（退订）检查**分散在调用方**，`mailDeliveryService.send` 有 7 个调用点而 `isSuppressed` 只覆盖 4 条，漏 3 条。这就是分散式安全检查的必然结果。

`mailDeliveryService.send` 的 7 个调用点与覆盖情况（截至 2026-08-11）：

| # | 调用点 | 组装来源 | 前置 `isSuppressed` |
|---|---|---|---|
| 1 | `InitialOutreachService.kt:73` | `IntroductionMailComposer` | ✅ `:46` |
| 2 | `ManualInitialOutreachService.kt:688` | `IntroductionMailComposer`（`:297`） | ✅ `:266` |
| 3 | `AutoMailReplyService.kt:575` | QA/AI 自动回复 | ✅ `:825` |
| 4 | `AutoMailReplyService.kt:965` | `sendMeetingInvitation` | ✅ 同 `:825` |
| 5 | `MeetingScheduleService.kt:141` | MEETING_CONFIRMATION | ❌ 无 |
| 6 | `ManualExpertMailService.kt:63` | `composeComposeTemplate` | ❌ 无 |
| 7 | `PendingMailOperationService.kt:270` | 待办工作台外发 | ❌ 无 |

`isSuppressed` 全部调用点：`InitialOutreachService:46`、`ManualInitialOutreachService:266/556/1171`、`AutoReplyPreviewService:149`、`AutoMailReplyService:825`。

**关键约束：抑制拦截不能表达为投递失败。** `ManualInitialOutreachService.kt:704-730` 按 `delivered.errorCategory` 分支 —— `PERMANENT` 会把联系人写成 `operatorStatus = "EMAIL_INVALID"` 并同步 ES（`:713-715`），`TRANSIENT` + SMTP 421/452 会 `accountRateLimiter.recordThrottled()`（`:729`）。用 `DeliveredMail(status="SUPPRESSED"/"FAILED")` 表达"已退订"会误标邮箱无效、误限流发件账号。正确做法是**抛异常**，且异常须继承 `IllegalStateException` 才能被 `GlobalExceptionHandler.kt:18-20` 映射为 400（见 [[K-custom-exception-http-status-mapping]]）。

**为什么 fail-closed 应放在 `SmtpMailDeliveryService.send()`**：已覆盖的 4 条有前置检查，永不触达异常，既有跳过统计口径（`BatchOutcomeReasonCodes.SUPPRESSED`）不受影响。检查必须在 `smtpSenderFactory.getSender(account)` **之前**，否则会先建立 SMTP 连接。

**但投递层兜底不足以覆盖全部三条漏网路径 —— 异常传播形态不一致，必须逐条验证 `send` 是否被 try/catch 包裹：**

| 路径 | send 行 | try/catch | 抛异常的实际后果 |
|---|---|---|---|
| `ManualExpertMailService.sendManualMail()` `:51` | `:63` | 无 | `@Transactional` 回滚 → `GlobalExceptionHandler` → 400 ✅ |
| `MeetingScheduleService.confirmMeetingAndEmail()` `:89` | `:141` | 无（唯一 catch 在 `:114`，只捕 `SenderAccountNotBoundException`） | 直达 handler → 400 ✅ |
| `PendingMailOperationService.sendManualRichReply()` `:128` | `:270` | **有**，`:359 catch (deliveryEx: Exception)` | 落 `else` → `finalizeFailure(DELIVERY_UNKNOWN)` `:365-371` → 409「发送状态未知，请勿重复发送」`:376-379` ❌ |

第三条必须在 `manualReplySendAttemptService.prepareAndClaim(payload)`（`:253`）**之前**单独前置拦截。否则：运营看到的文案暗示邮件可能已发出（实际一封没发）；attempt 被烧成 `DELIVERY_UNKNOWN`，按 [[K-smtp-idempotency-reservation-before-delivery]] 的 fail-closed 设计后续重试被阻断，该待办变成发不出也解不掉。

**通用教训**：给一个共享 seam 加"抛异常"型闸门时，必须逐一验证每个调用点是否有 catch 会改写该异常的语义 —— 尤其是带幂等占位/claim 的路径，占位在异常之前已经写入。

**不要改 `MailDeliveryService.send()` 的签名**：9 个测试文件引用该接口或其 mock（`BatchSendTaskRuntimeIntegrationTest`、`InitialOutreachServiceTest`、`ManualInitialOutreachServiceTest`、`MeetingScheduleServiceTest`、`AutoMailReplyServiceTest`、`ManualExpertMailServiceGateTest`、`ManualExpertMailServiceTest`、`PendingMailOperationServiceTrustWorkbenchTest`、`SmtpMailDeliveryServiceTest`）。需要传发送期策略（如 override）时，给 `ComposedMail`（`IntroductionMailComposer.kt:69-78`，8 个构造点）加**带默认值**的字段，对 Mockito `any()` 桩零影响。

另：`SuppressionSource.MAILTO`（`EmailSuppressionService.kt:10`）曾是**零写入方的死枚举值** —— `List-Unsubscribe` 头里承诺的 `mailto:` 退订通道不生效，因为 `looksLikeUnsubscribe()` `:74-77` 只看正文，而 mailto 退订邮件的典型形态是**主题 unsubscribe、正文为空**。主题判定必须用**归一化后精确相等**（匹配 `SmtpMailDeliveryService.kt:59` 生成的 `?subject=unsubscribe` 契约），用 `contains` 会把"标题里提到退订"的正常来信永久误加入抑制名单。

关联：[[K-preview-runtime-gates-visible]]（预览须只读标记 `RECIPIENT_UNSUBSCRIBED`，不得调用写入型 suppress）、[[K-sender-account-selection-sites]]（同类"多决策点须逐点核对"的形态）。
