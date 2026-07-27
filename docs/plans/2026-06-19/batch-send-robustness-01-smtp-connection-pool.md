# 子计划 01：SMTP 连接复用

> 主计划：`2026-06-19-batch-send-robustness-00-master.md`。共享不变量见主计划。

## 需求描述

- 可观察结果：批量发送时，同一发送账号的多封邮件复用同一个 `JavaMailSenderImpl` 实例（底层复用 SMTP Session/Transport），减少连接建立/销毁开销，降低被邮件服务商因连接频率触发风控的风险。
- 不可改变：`MailDeliveryService` 接口签名不变；`send()` 的行为语义不变。
- 不做：连接池的健康检查/空闲回收（后续可优化）。

## 关键不变量（引用 + 专属）

- 引用 R-1（连接池语义安全）。
- Invariant L1-1：配置变更感知。账号的 SMTP 配置（host/port/username/password）变更时，旧缓存条目必须被清除。检测方式：缓存条目记录配置摘要（`smtpHost:smtpPort:smtpUsername`），取用时比对，不匹配则重建。
- Invariant L1-2：异常不泄漏连接。`send()` 抛异常时不影响缓存条目的可用性。`JavaMailSenderImpl.send()` 内部已处理 Transport 的关闭，缓存的是 `JavaMailSenderImpl` 对象（无状态工厂），非 Transport 本身，天然安全。

## 现状审计

- `SmtpMailDeliveryService.send()`（L11-45）：每次 `new JavaMailSenderImpl()`，设置 host/port/username/password/properties，发送后丢弃。
- `DefaultSelfCheckProbeSender.sendProbe()`（L21-34）：同样每次新建。
- `JavaMailSenderImpl` 是线程安全的——内部用 `synchronized` 保护 Session 创建，每次 `send()` 独立获取/释放 Transport。缓存实例是安全的。

## 实现方案

### 任务 1：提取 `SmtpSenderFactory`（缓存工厂）

新文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpSenderFactory.kt`

```kotlin
@Service
class SmtpSenderFactory {
    private data class CacheKey(val accountCode: String)
    private data class CacheEntry(
        val sender: JavaMailSenderImpl,
        val configFingerprint: String
    )

    private val cache = ConcurrentHashMap<CacheKey, CacheEntry>()

    fun getSender(account: MailSenderAccount): JavaMailSenderImpl {
        val key = CacheKey(account.accountCode)
        val fingerprint = configFingerprint(account)
        val existing = cache[key]
        if (existing != null && existing.configFingerprint == fingerprint) {
            return existing.sender
        }
        val sender = buildSender(account)
        cache[key] = CacheEntry(sender, fingerprint)
        return sender
    }

    fun evict(accountCode: String) {
        cache.remove(CacheKey(accountCode))
    }

    fun evictAll() {
        cache.clear()
    }

    private fun configFingerprint(account: MailSenderAccount): String =
        "${account.smtpHost}:${account.smtpPort}:${account.smtpUsername}:${account.smtpPassword}"

    private fun buildSender(account: MailSenderAccount): JavaMailSenderImpl =
        JavaMailSenderImpl().apply {
            host = account.smtpHost
            port = account.smtpPort
            username = account.smtpUsername
            password = account.smtpPassword
            javaMailProperties = smtpProperties(account.smtpPort)
        }

    private fun smtpProperties(port: Int): Properties =
        Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.auth.mechanisms", "LOGIN")
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
            if (port == 465) {
                put("mail.smtp.ssl.enable", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
            }
        }
}
```

### 任务 2：改造 `SmtpMailDeliveryService`

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt`

- 注入 `SmtpSenderFactory`，`send()` 内改为 `smtpSenderFactory.getSender(account)` 替代 `new JavaMailSenderImpl()`。
- 删除 `smtpProperties()` 私有方法（已迁移到 factory）。

### 任务 3：改造 `DefaultSelfCheckProbeSender`

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/SenderAccountSelfCheckService.kt`

- 注入 `SmtpSenderFactory`，`sendProbe()` 内改为 `smtpSenderFactory.getSender(account)`。
- 删除 `smtpProperties()` 私有方法。

### 任务 4：账号配置变更时清除缓存

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt`

- 注入 `SmtpSenderFactory`。
- `updateAccount()` 末尾追加 `smtpSenderFactory.evict(accountCode)`。
- `setEnabled(accountCode, false)` 末尾追加 `smtpSenderFactory.evict(accountCode)`。

### 任务 5：测试

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpSenderFactoryTest.kt`

- 同一 account 连续两次 `getSender()` 返回同一实例。
- 修改 smtpHost 后 `getSender()` 返回新实例。
- `evict()` 后返回新实例。
