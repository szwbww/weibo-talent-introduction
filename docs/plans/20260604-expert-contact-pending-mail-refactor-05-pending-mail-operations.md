# Phase 5：待处理邮件操作面板后端能力

> 目标：待处理邮件详情页支持查看内容、变更专家状态/层级、发送 QA 邮件、富文本人工回复、标记已处理，并记录所有操作日志。

## 1. 前置依赖

必须先完成：

- Phase 1 操作日志。
- Phase 2 状态/层级统一服务。
- Phase 4 文件浏览可并行，但不是本阶段强依赖。

执行前检查：

```bash
sed -n '1,260p' src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt
sed -n '1,260p' src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt
sed -n '1,260p' src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt
sed -n '1,220p' src/main/kotlin/com/weibo/talentintroduction/mail/service/MailDeliveryService.kt
```

## 2. 命名说明

现有 API 叫 `unmatched-inbound`，但页面实际已经是人工处理队列，包含已匹配专家但需要人工处理的邮件。

本阶段不强制改 URL，避免前端大量断裂。继续复用：

```http
/api/mail/unmatched-inbound
```

页面文案可以叫「待处理邮件」。

## 3. 待处理邮件详情增强

修改：

```text
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt
```

`GET /api/mail/unmatched-inbound/{id}` 响应新增：

- `contact`：已关联专家摘要，可为空。
- `logs`：该 inboundProcessingId 对应操作日志，按时间倒序，取最近 50 条。
- `mailSendOptions` 可不放详情响应，前端可单独调 `/api/expert-contacts/mail-send-options`。

建议响应：

```kotlin
data class UnmatchedDetailResponse(
    val record: InboundMailProcessingResponse,
    val candidates: List<CandidateResponse>,
    val contact: PendingMailContactResponse?,
    val logs: List<OperatorActionLogResponse>
)
```

`PendingMailContactResponse`：

```kotlin
data class PendingMailContactResponse(
    val contactId: Long,
    val expertName: String?,
    val expertEmail: String,
    val orcidId: String,
    val currentIndexLevel: String,
    val operatorStatus: String,
    val currentStatus: String,
    val autoReplyEnabled: Boolean
)
```

## 4. PendingMailOperationService

新增：

```text
src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt
```

注入：

- `InboundMailProcessingRepository`
- `ExpertContactRepository`
- `ExpertOperatorStatusService`
- `ExpertIndexLevelOperationService`
- `ManualExpertMailService`
- `MailSenderAccountService`
- `MailDeliveryService`
- `MailRecordRepository`
- `OperatorActionLogService`
- `QaRuleRepository`

职责：

- 先通过 `recordId` 找 `InboundMailProcessing`。
- 对需要专家的操作，要求 `expertContactId != null`。
- 每个操作成功后写 `operator_action_log`，`inboundProcessingId` 必须填。

### 4.1 变更专家状态

方法：

```kotlin
fun changeOperatorStatus(
    inboundProcessingId: Long,
    operatorStatus: String,
    operatorName: String?,
    note: String?
): ExpertContact
```

行为：

- 找待处理记录。
- 要求已关联专家。
- 调用 `ExpertOperatorStatusService.changeStatus(...)`，但要让日志关联 `inboundProcessingId`。
- 如果 `ExpertOperatorStatusService` 不能传 inbound id，可以在本服务再写一条日志；避免重复日志时要统一设计。

建议改 `ExpertOperatorStatusService.changeStatus` 增加可选 `inboundProcessingId` 参数。

### 4.2 变更专家层级

方法：

```kotlin
fun changeIndexLevel(
    inboundProcessingId: Long,
    targetLevel: String,
    operatorName: String?,
    note: String?
): ExpertContact
```

行为：

- 要求已关联专家。
- 调用 `ExpertIndexLevelOperationService.changeLevel(...)`。
- 日志关联 inbound id。

### 4.3 发送 QA 邮件

新增接口：

```http
POST /api/mail/unmatched-inbound/{id}/qa-reply
```

请求：

```kotlin
data class PendingQaReplyRequest(
    val qaRuleId: Long,
    val senderAccountCode: String?,
    val operatorName: String?
)
```

行为：

- 要求待处理记录已关联专家。
- 使用 QA rule 生成邮件：
  - `subject = rule.replySubject ?: "Re: ${record.subject.orEmpty()}"`
  - `body = rule.replyBody`
- 发给专家主邮箱 `ExpertContact.expertEmail`。
- 保存 `MailRecord`：
  - `direction = "OUTBOUND"`
  - `mailType = "MANUAL_QA_REPLY"`
  - `triggeredBy = TriggeredBy.OPERATOR`
  - `matchedQaRuleId = qaRuleId`
  - `sourceInboundId` 当前模型是 `mail_record.id`，而此处只有 `inbound_mail_processing.id`。第一版建议新增 `MailRecord.sourceInboundProcessingId` 需要迁移，或者把 `sourceInboundId` 置空，并在操作日志中关联 `inboundProcessingId`。不要误填不同表的 id。
- 写操作日志 `SEND_QA_REPLY`。

如果选择新增 `mail_record.source_inbound_processing_id`，必须另建完整迁移 SQL。

### 4.4 人工富文本回复

新增接口：

```http
POST /api/mail/unmatched-inbound/{id}/manual-rich-reply
```

请求：

```kotlin
data class PendingManualRichReplyRequest(
    val senderAccountCode: String?,
    val subject: String,
    val htmlBody: String,
    val textBody: String?,
    val operatorName: String?
)
```

行为：

- 要求已关联专家。
- `subject` 非空。
- `htmlBody` 非空。
- 发 HTML 邮件。
- 保存 `MailRecord`：
  - `mailType = "MANUAL_RICH_REPLY"`
  - `body = htmlBody`
  - `triggeredBy = TriggeredBy.OPERATOR`
- 写操作日志 `SEND_MANUAL_RICH_REPLY`。

### 4.5 HTML 邮件支持

检查 `ComposedMail` 和 `MailDeliveryService` 当前是否支持 HTML。

如果不支持，扩展：

```kotlin
data class ComposedMail(
    val to: String,
    val subject: String,
    val body: String,
    val html: Boolean = false
)
```

`SmtpMailDeliveryService` 中：

- `html=false` 使用原文本发送。
- `html=true` 设置 HTML 内容。

必须保证现有文本邮件不受影响。

### 4.6 标记已处理增强

现有：

```http
POST /api/mail/unmatched-inbound/{id}/mark-resolved
```

增强：

- `MarkResolvedRequest` 字段从 `resolvedBy` 兼容扩展为：

```kotlin
data class MarkResolvedRequest(
    val resolvedBy: String?,
    val operatorName: String? = null,
    val note: String?
)
```

- 实际操作人取 `operatorName ?: resolvedBy`。
- 处理后：
  - `processStatus='PROCESSED'`
  - `processReason='MANUAL_RESOLVED'`
  - `reasonType='MANUAL_RESOLVED'`
  - `resolvedBy=operator`
  - `resolvedAt=now`
- 写日志 `MARK_INBOUND_RESOLVED`。
- 由于列表只查 `MANUAL_REVIEW`，处理后自然不再显示。

## 5. Controller 新增接口

文件：

```text
src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt
```

新增：

```http
POST /api/mail/unmatched-inbound/{id}/operator-status
POST /api/mail/unmatched-inbound/{id}/index-level
POST /api/mail/unmatched-inbound/{id}/qa-reply
POST /api/mail/unmatched-inbound/{id}/manual-rich-reply
```

所有接口都返回足够前端刷新用的数据：

- 状态/层级接口返回 `ExpertContactResponse` 或简化 contact 响应。
- 邮件发送接口返回 `ManualMailSendResult` 类似结构。
- 标记已处理返回 `InboundMailProcessingResponse`。

## 6. 绑定专家日志

修改：

```text
src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt
```

`bindToContact(...)` 成功后写 `BIND_INBOUND_MAIL` 日志：

- targetType = `INBOUND_MAIL_PROCESSING`
- targetId = recordId
- expertContactId = contactId
- inboundProcessingId = recordId
- before = record 的关键字段：`expertContactId`, `processStatus`, `processReason`
- after = 更新后的关键字段
- operatorName = resolvedBy

## 7. 测试

新增：

```text
src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt
```

最低覆盖：

- 未关联专家时，变更状态抛错。
- 未关联专家时，变更层级抛错。
- 已关联专家时，变更状态调用 `ExpertOperatorStatusService` 并写日志。
- 已关联专家时，变更层级调用 `ExpertIndexLevelOperationService` 并写日志。
- 发送 QA 邮件：
  - 读取 QA rule。
  - 调用邮件发送。
  - 保存 `MailRecord`。
  - 写 `SEND_QA_REPLY` 日志。
- 人工富文本回复：
  - HTML 正文非空。
  - 保存 `MANUAL_RICH_REPLY`。
  - 写日志。
- 标记已处理后 `processStatus=PROCESSED`。
- 标记已处理后写 `MARK_INBOUND_RESOLVED` 日志。
- 绑定专家后写 `BIND_INBOUND_MAIL` 日志。

## 8. 验证命令

```bash
node --check src/main/resources/static/app.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

## 9. 验收标准

- 待处理邮件详情能返回邮件正文、清洗正文、关联专家、日志。
- 待处理邮件可变更专家状态。
- 待处理邮件可变更专家层级。
- 待处理邮件可发送 QA 邮件。
- 待处理邮件可发送富文本人工回复。
- 标记已处理后不再出现在待处理列表。
- 所有操作能在日志查询接口查到。

## 10. 禁止事项

- 不要把 `inbound_mail_processing.id` 填进 `mail_record.source_inbound_id`，除非已经明确迁移字段语义。
- 不要发送邮件成功前先写成功日志。
- 不要发送手动邮件后自动标记已处理。
