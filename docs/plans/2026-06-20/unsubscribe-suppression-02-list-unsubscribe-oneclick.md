# 子计划 02：List-Unsubscribe 头 + RFC 8058 一键退订端点

> 主计划：`2026-06-20-unsubscribe-suppression-00-master.md`。共享不变量 G-1..G-4 见主计划。
> **依赖子计划 01**：复用 `EmailSuppressionService` 与 `SuppressionSource`。

## 需求描述

- 可观察结果：
  1. 每封**外发**邮件头部带 `List-Unsubscribe: <https://<base>/u/unsubscribe?token=...>, <mailto:<sender>?subject=unsubscribe>` 与 `List-Unsubscribe-Post: List=One-Click`。
  2. 邮件客户端发起一键退订（向 https 链接 POST）后，该收件邮箱被加入抑制名单（来源 `ONE_CLICK`）。
  3. 用户在浏览器打开该链接（GET）看到一个极简确认页，确认后同样加入抑制名单。
- 必须不变：邮件正文、主题、`Message-ID`、既有 From/To/Content 设置不变；发送成功/失败判定不变。鉴权配置（`/api/**` 拦截）不变。
- 不做：抑制名单管理前端；退订原因收集；多语言确认页（一个英文极简页即可）。

## 关键不变量（引用 + 专属）

- 引用 G-1（归一化）、G-2（幂等）、G-4（端点免鉴权且只增）。
- Invariant L2-1：token 自包含且防篡改。token = `base64url(email)` + "." + `base64url(HMAC_SHA256(secret, email))`；服务端用同一 `secret` 校验签名，校验失败一律拒绝（400），**不**根据未签名的明文邮箱执行退订。token 不含过期（退订链接长期有效，符合预期）。
- Invariant L2-2：头部仅对外发追加，且不破坏既有头。在 `SmtpMailDeliveryService.send` 构造 `MimeMessage` 后、`sender.send` 前用 `message.addHeader(...)` 追加两行头；不触碰 `Message-ID`/From/To/Subject/Content 既有逻辑。
- Invariant L2-3：端点路径在 `/u/**`，不在 `/api/**`，因此不经 `AuthInterceptor`（G-4）；POST 与 GET 均只调用 `EmailSuppressionService.suppress`，无任何读取/删除/枚举。
- Invariant L2-4：base URL 与 secret 来自配置，缺省安全。未配置 `secret` 时启动校验失败或禁用头注入（择一，见任务 4），绝不使用空/硬编码密钥签发。

## 现状审计

### 头部注入点
- `mail/service/SmtpMailDeliveryService.kt`：`send` 内已构造 `MimeMessage`，设 From/Recipients/Subject/Content，再 `sender.send(message)`。可用 `mail.to`（收件邮箱，用于签 token）、`account.senderEmail`（用于 mailto）。在 `sender.send` 前 `message.addHeader("List-Unsubscribe", ...)` 与 `message.addHeader("List-Unsubscribe-Post", "List=One-Click")`。

### 鉴权与路由
- `auth/config/AuthWebConfig.kt`：拦截 `/api/**`。`/u/**` 不被拦截（G-4 / L2-3），无需改该文件。
- 需确认 `common/controller/FrontendController` 的静态/SPA 兜底不会把 `/u/unsubscribe` 转发到前端页面而越过 `@RestController`。Spring 中具体的 `@RequestMapping("/u/...")` 控制器优先级高于通配兜底；验收时实测一次。

### 配置
- `application.yml` 现有大量 `talent-introduction.*` 与 `@ConfigurationProperties` 类（如 `config/` 下）。新增 `talent-introduction.unsubscribe.base-url` 与 `.secret`，对应一个 `UnsubscribeProperties`。

### 抑制服务（来自 01）
- `EmailSuppressionService.suppress(email, SuppressionSource.ONE_CLICK, reason)`、`normalize`、`isSuppressed` 已可用。

## 实现方案

### 任务 1：配置属性（L2-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/config/UnsubscribeProperties.kt`

```kotlin
@ConfigurationProperties(prefix = "talent-introduction.unsubscribe")
data class UnsubscribeProperties(
    val baseUrl: String = "",   // 如 https://outreach.qftechtalent.com
    val secret: String = ""     // HMAC 密钥，必须由环境变量注入
)
```

文件：`src/main/resources/application.yml`（在 `talent-introduction:` 下新增）

```yaml
  unsubscribe:
    base-url: ${UNSUBSCRIBE_BASE_URL:}
    secret: ${UNSUBSCRIBE_SECRET:}
```

（需确认主配置类是否用 `@ConfigurationPropertiesScan` 或显式 `@EnableConfigurationProperties`；按既有 `config/` 类的注册方式登记 `UnsubscribeProperties`。）

### 任务 2：token 服务（L2-1, L2-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenService.kt`

```kotlin
@Service
class UnsubscribeTokenService(private val properties: UnsubscribeProperties) {

    fun enabled(): Boolean = properties.baseUrl.isNotBlank() && properties.secret.isNotBlank()

    fun sign(email: String): String {
        val n = email.trim().lowercase(Locale.ROOT)            // 与 G-1 一致
        val mac = hmac(n)
        return enc(n) + "." + enc(mac)
    }

    /** 校验并返回归一化邮箱；失败返回 null（L2-1）。 */
    fun verify(token: String): String? {
        val parts = token.split(".")
        if (parts.size != 2) return null
        val email = String(dec(parts[0]), Charsets.UTF_8)
        val expected = enc(hmac(email))
        // 常量时间比较
        if (!MessageDigest.isEqual(expected.toByteArray(), parts[1].toByteArray())) return null
        return email
    }

    fun unsubscribeUrl(email: String): String =
        "${properties.baseUrl.trimEnd('/')}/u/unsubscribe?token=${sign(email)}"

    private fun hmac(data: String): ByteArray {
        val key = SecretKeySpec(properties.secret.toByteArray(), "HmacSHA256")
        return Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(data.toByteArray())
    }
    private fun enc(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun enc(s: String) = enc(s.toByteArray())
    private fun dec(s: String) = Base64.getUrlDecoder().decode(s)
}
```

### 任务 3：一键退订控制器（G-4, L2-3）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnsubscribeController.kt`

```kotlin
@RestController
@RequestMapping("/u")
class UnsubscribeController(
    private val tokenService: UnsubscribeTokenService,
    private val suppressionService: EmailSuppressionService
) {
    /** RFC 8058 一键退订：邮件客户端 POST。 */
    @PostMapping("/unsubscribe")
    fun oneClick(@RequestParam token: String): ResponseEntity<String> {
        val email = tokenService.verify(token) ?: return ResponseEntity.badRequest().body("invalid")
        suppressionService.suppress(email, SuppressionSource.ONE_CLICK, "one-click unsubscribe")
        return ResponseEntity.ok("unsubscribed")
    }

    /** 浏览器打开链接：极简确认页（GET 不直接退订，避免预取误触发）。 */
    @GetMapping("/unsubscribe")
    fun page(@RequestParam token: String): ResponseEntity<String> {
        if (tokenService.verify(token) == null) return ResponseEntity.badRequest().body("invalid link")
        // 返回一个含 POST 表单的极简 HTML，提交到本端点 POST
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(confirmHtml(token))
    }

    @PostMapping("/unsubscribe/confirm")
    fun confirm(@RequestParam token: String): ResponseEntity<String> {
        val email = tokenService.verify(token) ?: return ResponseEntity.badRequest().body("invalid")
        suppressionService.suppress(email, SuppressionSource.ONE_CLICK, "web confirm unsubscribe")
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body("<p>You have been unsubscribed.</p>")
    }
}
```

（`confirmHtml(token)` 返回内联的最简表单，POST 到 `/u/unsubscribe/confirm`。）

### 任务 4：邮件头注入（L2-2, L2-4）

文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt`

- 注入 `UnsubscribeTokenService`。
- 在 `sender.send(message)` 之前：

```kotlin
if (tokenService.enabled()) {
    val httpsUrl = tokenService.unsubscribeUrl(mail.to)
    val mailto = "mailto:${account.senderEmail}?subject=unsubscribe"
    message.addHeader("List-Unsubscribe", "<$httpsUrl>, <$mailto>")
    message.addHeader("List-Unsubscribe-Post", "List=One-Click")
}
```

- 未配置（`enabled()==false`）时不追加头（L2-4），其余发送逻辑完全不变（L2-2）。

### 任务 5：测试

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenServiceTest.kt`
- `sign` 后 `verify` 回得归一化邮箱（L2-1）。
- 篡改签名段 / 篡改邮箱段 → `verify` 返回 null（L2-1）。
- 大小写不同邮箱 `sign` 出的邮箱归一一致（G-1）。

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnsubscribeControllerTest.kt`（MockMvc）
- POST 合法 token → 200 且 `EmailSuppressionService.suppress(ONE_CLICK)` 被调用。
- POST 非法 token → 400 且不调用 suppress（L2-3）。
- GET 合法 token → 200 HTML；GET 非法 → 400。

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/config/UnsubscribeProperties.kt` | 新增 |
| 2 | `src/main/resources/application.yml` | 修改 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenService.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnsubscribeController.kt` | 新增 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt` | 修改 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeTokenServiceTest.kt` | 新增 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnsubscribeControllerTest.kt` | 新增 |

文件数 = 7 ≤ 10。子系统：HTTP 退订端点（属性+token+控制器）+ 邮件头注入 = 2。

## 验收标准

- L2-1：token 往返正确；任一段被改 → 拒绝。
- L2-2：发送一封邮件，抓取 `MimeMessage` 头，断言含 `List-Unsubscribe`（同时有 https 与 mailto）与 `List-Unsubscribe-Post: List=One-Click`；`Message-ID`/From/To/Subject/Content 与改造前一致。
- L2-3 / G-4：用 MockMvc 直接打 `/u/unsubscribe`（不带任何鉴权头）能命中控制器并按 token 合法性返回 200/400；确认不经 `AuthInterceptor`。
- L2-4：清空 `secret` 配置时，`tokenService.enabled()==false`，发送不追加退订头且不抛异常。
- 端到端：合法 token POST 后，`email_suppression` 出现该邮箱（来源 `ONE_CLICK`）；随后该邮箱进入 `InitialOutreachService.sendInitialBatch` 被跳过（依赖 01 的 G-3，跨子计划集成验证）。
- 路由：实测 `GET /u/unsubscribe?token=...` 返回控制器 HTML 而非前端 SPA 页面（确认 `FrontendController` 兜底未拦截）。

---

## 修正记录

### 修正 1（2026-08-06）：`List-Unsubscribe-Post` 的值不合 RFC 8058，本计划原文即为错误源

- **原文**：本计划 `:9`、`:26`、任务 4（`:152`）、验收标准 L2-2（`:187`）四处均写死
  `List-Unsubscribe-Post: List=One-Click`。代码 `SmtpMailDeliveryService.kt:54` 是对本计划的**忠实实现**，
  因此这不是代码缺陷，而是**计划缺陷传导至代码**。
- **修正为**：值必须逐字为 `List-Unsubscribe=One-Click`。
- **依据**：RFC 8058 §3.1「The List-Unsubscribe-Post header MUST contain the single key/value pair
  `List-Unsubscribe=One-Click`」；§5 ABNF `postarg = "List-Unsubscribe=One-Click"`。
  值不合法时 Gmail 不渲染一键退订按钮。
- **生产实证**（两封外发原文，2026-07-05 与 2026-08-06，跨两个腾讯企业邮中继集群）：
  两封均携带 `List-Unsubscribe`（配置正常、token 正常签发）、SPF/DKIM/DMARC 全部 pass，
  但 Gmail 界面无退订按钮。
- **另一并存阻断点（本计划范围外，代码不可解）**：RFC 8058 §4 要求 `List-Unsubscribe` 与
  `List-Unsubscribe-Post` 必须被 DKIM 签名覆盖并出现在 `h=` 中，否则
  「the mail receiver SHOULD NOT offer a one-click unsubscribe」。实际签名由腾讯企业邮
  （`bizesmtp.qq.com`，`s=card2607`）完成，其 `h=Date:From:To:Message-ID:Subject:MIME-Version`
  **不含**这两个头，且 `h=` 由中继 MTA 决定，JavaMail 侧无法控制。
  **因此仅修正本条的 header 值，Gmail 按钮仍不会出现** —— 两者是与关系。
  出路：① 要求腾讯企业邮把 List-* 加入 `h=`；② 更换支持 RFC 8058 的发送服务商；
  ③ 发信前自签一份覆盖 List-* 的 DKIM（多重 DKIM-Signature 合法）——
  但**不得签 `Message-ID`**，因为该中继会给 Message-ID 加 `[0-9A-F]{16}+` 前缀，会破坏签名。
- **代码修正的归属**：并入 `docs/plans/2026-08-06/material-reminder-02-headers-personalization.md`
  —— 该计划已持有 `SmtpMailDeliveryService` 退订头逻辑的所有权（Invariant J-1）
  及任务 7 的修正记录机制。**不另起新计划**，避免多个计划同时改同一文件。
  注意其执行前决策点：若任务 1/2（对 `MATERIAL_REMINDER` 抑制退订头）被放弃，
  **本条 header 值修正仍必须执行** —— 它作用于其余所有邮件类型。
- **发现来源**：`docs/plans/2026-08-06/expert-profile-absence-not-error.md` 的关联缺陷移交第 1 项。
