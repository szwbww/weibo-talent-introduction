# Phase 3：自动收信流转和有效层晋级规则

> 目标：专家回复次数超过 2 次自动进有效层；专家发送材料附件自动进有效层；同步维护运营状态。

## 1. 前置依赖

必须先完成 Phase 1 和 Phase 2：

- `expert_contact.operator_status` 已存在。
- `ExpertOperatorStatusService` 已存在。
- `ExpertIndexLevelOperationService` 已存在，或至少已有可复用的 application promotion 服务。

执行前检查：

```bash
sed -n '1,760p' src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt
sed -n '1,220p' src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt
sed -n '1,220p' src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt
```

## 2. Repository 增加回复计数

文件：

```text
src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt
```

新增方法：

```kotlin
fun countByExpertContactIdAndDirectionAndMailType(
    expertContactId: Long,
    direction: String,
    mailType: String
): Long
```

如果 Spring Data JDBC 方法名不兼容当前字段，使用 `@Query`：

```kotlin
@Query(
    """
    SELECT COUNT(*) FROM mail_record
    WHERE expert_contact_id = :expertContactId
      AND direction = 'INBOUND'
      AND mail_type = 'REPLY'
    """
)
fun countInboundReplies(expertContactId: Long): Long
```

## 3. 抽取自动晋级方法

在 `AutoMailReplyService` 中新增私有方法，或新增独立服务 `AutomaticApplicationPromotionService`。

推荐新增服务，避免 `AutoMailReplyService` 继续膨胀：

```text
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionService.kt
```

建议签名：

```kotlin
fun promoteByReplyCountIfNeeded(
    contact: ExpertContact,
    receivedAt: LocalDateTime,
    sourceInboundId: Long
): ExpertContact

fun promoteByMaterialIfNeeded(
    contact: ExpertContact,
    receivedAt: LocalDateTime,
    sourceInboundId: Long,
    savedDocumentCount: Int
): ExpertContact
```

规则：

### 回复次数规则

- 保存本次 inbound `mail_record` 后再统计。
- `countInboundReplies(contactId) > 2` 才触发。
- 如果 `contact.applicationIndexed=true`，不重复 promotion，只返回 contact。
- promotion 触发原因记录为 `REPLY_COUNT_GT_2`。
- 如果 promotion 成功，保存：
  - `applicationIndexed=true`
  - `currentIndexLevel='APPLICATION'`
  - `firstReplyAt` 保留原值，没有则用当前 `receivedAt`
  - `operatorStatus` 至少为 `REPLIED`，但如果已经是 `MATERIALS_RECEIVED`、`INVITED`、`COMPLETED` 不要降级。

### 材料规则

- `savedDocumentCount > 0` 触发。
- 如果 `contact.applicationIndexed=true`，不重复 promotion。
- promotion 触发原因记录为 `MATERIAL_ATTACHED`。
- 保存 `operatorStatus='MATERIALS_RECEIVED'`，除非已是 `COMPLETED`。

## 4. 调整 `AutoMailReplyService.processSingle`

文件：

```text
src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt
```

找到两处 inbound 保存逻辑：

1. 自动回复暂停或 MANUAL_HANDOFF 分支中：
   - 当前会 `saveMailRecord(...)`
   - `mailAttachmentService.saveInboundAttachments(...)`
   - classify intent
2. 正常自动流程中：
   - 保存 inbound `MailRecord`
   - 保存附件
   - classify intent

两处都要调用自动晋级规则。

推荐顺序：

```text
保存 inbound mail_record
保存附件，拿到 savedDocuments
按附件调用 promoteByMaterialIfNeeded
如果没有附件 promotion，再按回复次数调用 promoteByReplyCountIfNeeded
后续 intent/QA/meeting/manual review 使用最新 contact
```

注意事项：

- 发送材料优先级高于回复次数，因为页面状态应为 `MATERIALS_RECEIVED`。
- 如果材料已触发 promotion，不需要再用回复次数触发第二次 promotion。
- `NOT_INTERESTED` 如果带附件非常罕见，仍按附件规则进入有效层还是不进入，需要产品口径。建议第一版：附件优先，进入有效层并待人工判断。
- 已经 `COMPLETED` 的运营状态不要被自动流程改掉。

## 5. 会议和 QA 状态维护

在已有自动逻辑中补 `operatorStatus`：

| 触发 | operatorStatus |
| --- | --- |
| 普通 inbound 已保存且没有附件 | `REPLIED` |
| QA 自动回复成功 | `REPLIED` |
| 会议邀约发送成功 | `INVITED` |
| 附件/材料 | `MATERIALS_RECEIVED` |

建议通过 `ExpertOperatorStatusService.updateAutomatically(...)` 做，避免重复校验。

不要把 `COMPLETED` 覆盖掉。

## 6. Promotion audit

当前已有 `expert_application_promotion`。如果 `ExpertIndexWriterService.promoteToApplication(...)` 已经写 audit，沿用。

如果 audit 不支持原因字段，至少在 `triggeredBy` 或现有 error/note 机制中保留：

- `REPLY_COUNT_GT_2`
- `MATERIAL_ATTACHED`

如果需要新增字段，单独迁移完整 SQL；不要把局部 SQL 写在说明里。

## 7. 测试

重点修改：

```text
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt
```

或新增：

```text
src/test/kotlin/com/weibo/talentintroduction/mail/service/AutomaticApplicationPromotionServiceTest.kt
```

最低覆盖：

1. 第 1 封 inbound reply：
   - 不 promotion。
   - `operatorStatus` 从 `CONTACTED` 变为 `REPLIED`。
2. 第 2 封 inbound reply：
   - 不 promotion。
3. 第 3 封 inbound reply：
   - promotion 到 `APPLICATION`。
   - `applicationIndexed=true`。
   - `currentIndexLevel='APPLICATION'`。
4. 附件来信：
   - 即使是第 1 封也 promotion。
   - `operatorStatus='MATERIALS_RECEIVED'`。
5. 已在 `APPLICATION`：
   - 不重复调用 `promoteToApplication`。
6. 已 `COMPLETED`：
   - 自动流程不把 `operatorStatus` 改回 `REPLIED`。
7. 自动回复暂停分支：
   - 仍保存附件。
   - 附件/回复次数规则仍生效。
   - 不发送自动邮件。

## 8. 验证命令

```bash
node --check src/main/resources/static/app.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

## 9. 验收标准

- 专家第 3 次回复后自动进入有效层。
- 专家发送附件材料后自动进入有效层。
- 附件进入有效层后，待处理邮件仍可进入人工队列，不被吞掉。
- 自动回复暂停时，不自动发邮件，但仍保存附件、计数、必要时晋级。
- 运营状态自动更新正确。

## 10. 禁止事项

- 不要把所有首次回复都自动进有效层；新规则是回复次数超过 2 次或发送材料。
- 不要在 promotion 失败时吞掉错误并假装已经进有效层；至少记录 warn，并确保 MySQL 状态不错误标记成 `APPLICATION`。
- 不要覆盖人工标记的 `COMPLETED`。
