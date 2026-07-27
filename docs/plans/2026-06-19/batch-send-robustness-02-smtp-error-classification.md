# 子计划 02：SMTP 错误分级处理

> 主计划：`2026-06-19-batch-send-robustness-00-master.md`。共享不变量见主计划。

## 需求描述

- 可观察结果：SMTP 发送失败时，系统区分暂时性错误（4xx：限流、邮箱满）和永久性错误（5xx：收件人不存在、域名无效），并做出不同响应。永久性错误标记专家邮箱为无效（不再重试），暂时性错误暂停当前账号的发送。`mail_record.error_summary` 和 `mail_send_attempt.error_summary` 中记录结构化错误码。
- 不可改变：`MailDeliveryService` 接口签名不变（返回 `DeliveredMail`）。成功路径不变（R-2）。
- 不做：基于错误码的自动切换备用 SMTP 服务器。

## 关键不变量（引用 + 专属）

- 引用 R-2（成功路径不变）。
- Invariant L2-1：错误分类保守原则。无法确定错误码时归为 `TRANSIENT`（暂时性），不误标为永久性。宁可多重试，不可误弃。
- Invariant L2-2：永久性错误标记不影响已有联系人状态机。标记仅写入 `mail_send_attempt` 和 `ExpertContact` 的 `operatorStatus`（设为 `EMAIL_INVALID`），不触发 `ConversationStateService.transition()`。

## 现状审计

- `SmtpMailDeliveryService.send()`：SMTP 异常直接向上抛出。调用方 `ManualInitialOutreachService` 在 catch 中统一记 `failedCount++`，不区分错误类型。
- `javax.mail.SendFailedException` 包含 `getInvalidAddresses()` 和 `getValidUnsentAddresses()`，可用于区分收件人级别错误。
- `javax.mail.MessagingException` 的 message 通常包含 SMTP 响应码（如 "550 5.1.1 User unknown"、"421 4.7.0 Try again later"）。
- `mail_send_attempt.error_summary` 已有字段（VARCHAR(1000)），可用于存储结构化错误信息。

## 实现方案

### 任务 1：定义错误分类枚举

新文件：`src/main/kotlin/com/weibo/talentintroduction/mail/domain/SmtpErrorCategory.kt`

```kotlin
enum class SmtpErrorCategory {
    /** 发送成功 */
    SUCCESS,
    /** 暂时性错误（4xx）：限流、邮箱满、服务器暂不可用。建议暂停账号稍后重试。 */
    TRANSIENT,
    /** 永久性错误（5xx）：收件人不存在、域名无效、被拒。标记邮箱无效，不再重试。 */
    PERMANENT,
    /** 网络/认证等基础设施错误。与收件人无关，暂停账号。 */
    INFRASTRUCTURE
}
```

### 任务 2：扩展 `DeliveredMail`

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailDeliveryService.kt`

```kotlin
data class DeliveredMail(
    val messageId: String?,
    val status: String,
    val errorCategory: SmtpErrorCategory = SmtpErrorCategory.SUCCESS,
    val smtpResponseCode: Int? = null,       // 新增
    val errorDetail: String? = null           // 新增：原始异常 message
)
```

新增字段带默认值，不影响已有调用点。

### 任务 3：改造 `SmtpMailDeliveryService`

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt`

将 `send()` 从"成功或抛异常"改为"始终返回 `DeliveredMail`"：

```kotlin
override fun send(account: MailSenderAccount, mail: ComposedMail): DeliveredMail {
    val sender = smtpSenderFactory.getSender(account)
    // ... 构建 message（不变）
    return try {
        sender.send(message)
        DeliveredMail(messageId = message.messageID ?: mail.messageId, status = "SENT")
    } catch (e: SendFailedException) {
        val code = extractSmtpCode(e)
        val category = classifySmtpCode(code, e)
        DeliveredMail(
            messageId = mail.messageId, status = "FAILED",
            errorCategory = category, smtpResponseCode = code,
            errorDetail = e.message?.take(500)
        )
    } catch (e: AuthenticationFailedException) {
        DeliveredMail(
            messageId = mail.messageId, status = "FAILED",
            errorCategory = SmtpErrorCategory.INFRASTRUCTURE,
            errorDetail = "AUTH_FAILED:${e.message?.take(500)}"
        )
    } catch (e: MessagingException) {
        val code = extractSmtpCode(e)
        val category = classifySmtpCode(code, e)
        DeliveredMail(
            messageId = mail.messageId, status = "FAILED",
            errorCategory = category, smtpResponseCode = code,
            errorDetail = e.message?.take(500)
        )
    }
}

private fun extractSmtpCode(e: MessagingException): Int? {
    // 正则提取 SMTP 响应码（3 位数字开头）
    val match = Regex("^(\\d{3})\\b").find(e.message ?: "")
    return match?.groupValues?.get(1)?.toIntOrNull()
}

private fun classifySmtpCode(code: Int?, e: MessagingException): SmtpErrorCategory {
    if (code == null) return SmtpErrorCategory.TRANSIENT  // L2-1 保守原则
    return when {
        code in 200..299 -> SmtpErrorCategory.SUCCESS
        code in 400..499 -> SmtpErrorCategory.TRANSIENT
        code in 500..599 -> SmtpErrorCategory.PERMANENT
        else -> SmtpErrorCategory.TRANSIENT  // L2-1
    }
}
```

### 任务 4：编排器按错误类型处理

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

在发送结果处理（约 L255-283）中，增加按 `errorCategory` 的分支：

```kotlin
if (delivered.status == "SENT") {
    // 不变
} else {
    when (delivered.errorCategory) {
        SmtpErrorCategory.PERMANENT -> {
            // 永久性错误：标记邮箱无效 + 记录失败
            txHelper.recordFailure(...)
            expertIndexWriterService.syncCandidateOperatorStatus(normOrcid, "EMAIL_INVALID")
            failedCount++
            stat.failed++
        }
        SmtpErrorCategory.TRANSIENT -> {
            // 暂时性错误：暂停当前账号 + 跳出当前轮
            txHelper.recordFailure(...)
            mailSenderAccountService.pauseAutoSend(account.accountCode,
                "SMTP_TRANSIENT:${delivered.smtpResponseCode}:${delivered.errorDetail?.take(200)}")
            failedCount++
            stat.failed++
            midRoundStop = true
        }
        SmtpErrorCategory.INFRASTRUCTURE -> {
            // 基础设施错误：暂停账号
            txHelper.recordFailure(...)
            mailSenderAccountService.pauseAutoSend(account.accountCode,
                "SMTP_INFRA:${delivered.errorDetail?.take(200)}")
            failedCount++
            stat.failed++
            midRoundStop = true
        }
        else -> {
            // 兜底
            txHelper.recordFailure(...)
            failedCount++
            stat.failed++
        }
    }
}
```

### 任务 5：ES `operatorStatus=EMAIL_INVALID` 支持

文件：`src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt`

- 确认 `syncCandidateOperatorStatus(orcidId, "EMAIL_INVALID")` 已有通用实现，不需要额外改动。
- `ExpertSearchService.notContactedWithEmailFilters()` 中的 ES 查询条件应排除 `operatorStatus=EMAIL_INVALID`，确保标记无效的专家不再出现在快照中。

### 任务 6：测试

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt`

- 模拟 `SendFailedException`（550）→ 返回 `PERMANENT`。
- 模拟 `MessagingException`（421）→ 返回 `TRANSIENT`。
- 模拟 `AuthenticationFailedException` → 返回 `INFRASTRUCTURE`。
- 模拟无法解析错误码 → 返回 `TRANSIENT`（L2-1）。

文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`（补充）

- `PERMANENT` 错误后 `operatorStatus=EMAIL_INVALID`，该专家不再出现在下次快照。
- `TRANSIENT` 错误后账号被暂停，轮中断。
