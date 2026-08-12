---
id: K-unsubscribe-token-plaintext-email
domain: mail
created: 2026-08-12
last_used: 2026-08-12
hit_count: 0
source: create-p:unsubscribe-07-opaque-token
severity: P1
---

经验：退订 token 的自签格式把**收件人邮箱明文编码进了 URL**。

`UnsubscribeTokenService.kt:17-21`：

```kotlin
fun sign(email: String): String {
    val n = email.trim().lowercase(Locale.ROOT)
    val mac = hmac(n)
    return enc(n) + "." + enc(mac)     // enc = Base64.getUrlEncoder().withoutPadding() (:45-47)
}
```

第一段是**可直接 base64url 解码**的邮箱明文，任何拿到该 URL 的人（邮件转发链、企业邮件网关、访问日志、浏览器历史、Referer）都能还原收件人地址。同时 token 总长把退订 URL 推到 150+ 字符，在纯文本正文里被 Gmail 折行显示，也是垃圾邮件特征。

**改造时的文件数陷阱**：`grep -rn "UnsubscribeTokenService(" src/` 有 **9 处构造，全部在测试**（`UnsubscribeTokenServiceTest:17/65/66`、`SmtpMailDeliveryServiceTest:29/35`、`UnsubscribeControllerIllegalTokenTest:60`、`ManualExpertMailServiceGateTest:51`、`MailVariableServiceTest:42/604`）。给构造函数加**必填**参数会一次性打爆 6 个测试文件。

**收敛范式**：新依赖用**可空 + 默认 null** 的尾部参数（与 `MailVariableService.kt:112` 的 `unsubscribeTokenService: UnsubscribeTokenService? = null` 同一手法），把测试改动收敛到 1 个文件。代价是引入 test-only fallback 分支 —— 必须配套要求"每条断言都要有一份非空依赖装配的用例"，否则会重蹈 [[K-compose-template-html-after-render]] 记录过的"只测 fallback 分支、漏掉线上独有路径"。

**注意 `enabled()` 的语义会分裂**：`:15` 现在是 `baseUrl.isNotBlank() && secret.isNotBlank()`；改成表存储后 secret 只在旧 token 兜底校验时需要。分裂后必须保证 secret 为空时旧通道**直接返回 null**，不能用空 key 做 HMAC（否则任意人构造的 token 在未配密钥的环境里会通过校验）。

关联：[[K-unsubscribe-variable-injection-sites]]、[[K-flyway-placeholder-replacement]]。
