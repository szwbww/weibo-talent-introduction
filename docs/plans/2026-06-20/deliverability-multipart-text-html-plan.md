# 开发计划：HTML 邮件补 text/plain 多部分（multipart/alternative）

> 用 create-p skill 编写。独立计划，无前置依赖。

## 需求描述

- 可观察结果：当一封外发邮件为 HTML（`ComposedMail.html == true`）时，实际发出的是 `multipart/alternative`，同时包含 text/plain 与 text/html 两部分；纯文本邮件（`html == false`）行为完全不变。
- 必须不变：纯文本邮件（当前 `IntroductionMailComposer` 即此类）的发送、`Message-ID`、From/To/Subject 不变；`DeliveredMail` 返回结构不变；错误分类逻辑不变。
- 不做：富文本编辑器、模板系统改造、为每个模板单独维护一份纯文本版（本期纯文本部分由 HTML 自动降级或可选传入）。

## 关键不变量

### Invariant I-1：纯文本路径零回归
- 规则：`ComposedMail.html == false` 时，`SmtpMailDeliveryService.send` 走与现状**完全相同**的 `message.setText(body, UTF-8)` 分支，不构造 multipart。
- 适用于：`SmtpMailDeliveryService.send`。
- 违反后果：现有纯文本首封外联编码/格式变化，引入回归。

### Invariant I-2：HTML 邮件必含两部分且顺序正确
- 规则：`html == true` 时构造 `MimeMultipart("alternative")`，**先加 text/plain，再加 text/html**（RFC 2046 要求最优表示放最后）。两部分均 UTF-8。
- 适用于：`SmtpMailDeliveryService.send` 的 HTML 分支。
- 违反后果：客户端优先显示纯文本或显示乱序，反垃圾评分不升反降。

### Invariant I-3：纯文本内容来源确定
- 规则：text/plain 部分内容 = `ComposedMail.text`（若非空）否则由 `body`（HTML）经统一降级函数 `htmlToPlainText` 生成（去标签、解实体、规整空白）。降级函数无副作用、可单测。
- 适用于：`SmtpMailDeliveryService` 或一个 `MailContentService`。
- 违反后果：纯文本部分为空或含原始标签，触发垃圾判定。

## 现状审计

### `SmtpMailDeliveryService.send`（写路径）
- 现状：`if (mail.html) message.setContent(body, "text/html; charset=UTF-8") else message.setText(body, UTF-8)`，随后 `sender.send`。本计划只改 HTML 分支为 multipart。
- `ComposedMail(to, subject, body, html=false, messageId=null)`：新增可空字段 `text: String? = null`（向后兼容，默认 null）。

### 各 Composer（读 `ComposedMail` 的写入方）
- `IntroductionMailComposer`：构造 `ComposedMail(html 默认 false)` → 纯文本，走 I-1，不受影响。
- 其余产生 `html=true` 的来源（QA 自动回复模板、会议邀请、人工发送 `ManualExpertMailService`）：执行时 grep `html = true` / `.copy(html` 确认清单；它们无需改动即自动获得 text/plain 降级（I-3）。可选地为这些来源显式传 `text=` 以获得更优纯文本。

## 实现方案

### 任务 1：`ComposedMail` 增加可空 `text` 字段（I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt`（`ComposedMail` 定义处）
- `data class ComposedMail(..., val html: Boolean = false, val text: String? = null, val messageId: String? = null)`。默认 null 保证现有构造点不变。

### 任务 2：HTML→纯文本降级函数（I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/MailContentService.kt`（新增）
```kotlin
@Service
class MailContentService {
    fun htmlToPlainText(html: String): String =
        html.replace(Regex("(?is)<(script|style).*?>.*?</\\1>"), "")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .let { org.apache.commons.text.StringEscapeUtils.unescapeHtml4(it) } // 若无依赖则手写常见实体表
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
}
```
（执行时确认是否已有 commons-text 依赖；无则用最小手写实体解码，不新增依赖。）

### 任务 3：`SmtpMailDeliveryService` 构造 multipart（I-1, I-2, I-3）
文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt`
- 注入 `MailContentService`。替换内容设置段：
```kotlin
if (mail.html) {
    val plain = mail.text?.takeIf { it.isNotBlank() } ?: mailContentService.htmlToPlainText(mail.body)
    val mp = javax.mail.internet.MimeMultipart("alternative")
    mp.addBodyPart(javax.mail.internet.MimeBodyPart().apply { setText(plain, "UTF-8") })          // 先 text
    mp.addBodyPart(javax.mail.internet.MimeBodyPart().apply { setContent(mail.body, "text/html; charset=UTF-8") }) // 后 html
    message.setContent(mp)
} else {
    message.setText(mail.body, Charsets.UTF_8.name())   // I-1 原样
}
```

### 任务 4：测试
文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/MailContentServiceTest.kt`
- `<p>` / `<br>` 转换、标签剥离、实体解码、空白规整（I-3）。
文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt`（新增/补充，用伪 Session/Transport 或断言构造的 MimeMessage）
- `html=false` → `message.content` 为字符串、非 multipart（I-1）。
- `html=true` → content 为 `MimeMultipart`，count==2，第 0 部分 `text/plain`、第 1 部分 `text/html`（I-2）。
- `html=true` 且传入 `text=` → text/plain 用传入值（I-3）。

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `mail/service/IntroductionMailComposer.kt`（`ComposedMail`） | 修改 |
| 2 | `mail/service/MailContentService.kt` | 新增 |
| 3 | `mail/service/SmtpMailDeliveryService.kt` | 修改 |
| 4 | `test/.../MailContentServiceTest.kt` | 新增 |
| 5 | `test/.../SmtpMailDeliveryServiceTest.kt` | 新增/修改 |

文件数 = 5 ≤ 10。子系统：发送内容构造（单一）。

## 验收标准
- I-1：纯文本邮件 content 类型与现状一致（字符串），无 multipart。
- I-2：HTML 邮件为 multipart/alternative，部分顺序 text→html。
- I-3：降级函数单测覆盖标签/实体/空白；HTML 邮件 text/plain 非空且无残留标签。
- 集成：用 `IntroductionMailComposer` 产出的纯文本邮件端到端发送，断言不受影响。
