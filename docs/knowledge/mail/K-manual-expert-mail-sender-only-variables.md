---
id: K-manual-expert-mail-sender-only-variables
domain: mail
created: 2026-08-11
last_used: 2026-08-11
hit_count: 1
source: create-p:material-reminder-01-threading
revalidated_by: create-p:unsubscribe-01-body-link
severity: P2
---

> **2026-08-11 更正（原条目已过期）**：原文称 `ManualExpertMailService.mailTemplateVariables()`（`:196-204`）只注入 6 个 sender 变量、零个专家变量，因此 compose 模板里的 `${expertName}` 等占位符经手动/材料提醒路径外发时不会渲染。**该结论对当前代码不再成立。** 保留下方"仍然成立的部分"，删除已失效的结论。

## 当前事实（已重新 grep 验证）

`ManualExpertMailService.composeComposeTemplate()` `:196-200` 走的是**全量** `buildVariables`：

```kotlin
val variables = if (variableService != null) {
    val expert = variableService.resolveExpertProfileFor(contact)
    variableService.buildVariables(
        account, expert, contact.expertEmail, previewFallbacks = false, contact = contact
    )
} else { /* test-only fallback, 见下 */ }
```

因此手动单发与材料提醒批量路径**能**渲染 sender + expert + `unsubscribeUrl` 全量变量，与 `IntroductionMailComposer.compose()` 已对称。"提醒邮件正文个性化必须改代码"这一结论**作废** —— 现在是后台编辑模板正文即可。

## 仍然成立的部分

1. **测试兜底分支仍是旧行为**：`:203-205` 在 `variableService == null`（legacy 9-arg 构造器，仅测试使用）时返回 `senderVariables(account) + MailVariableService.EXPERT_KEYS.associateWith { "" }` —— **不含** `unsubscribeUrl`。该分支下模板里的 `${unsubscribeUrl}` 会泄漏字面量（见 [[K-unsubscribe-variable-injection-sites]]）。
2. `senderVariables()` `:283-290` 本身确实只有 5 个 sender 变量（注意**不含** `senderDisplayName`，与 `AutoMailReplyService.mailTemplateVariables()` `:990-998` 的 6 个不同）。
3. `ExpertRecipientNamePolicy`（`MailVariableService.kt:13`，`internal object`）两个函数语义不同，这一点未变：
   - `resolveRecipientName(contact, expertProfile)` — `expertProfile` 传 null 时仍能从 `contact.expertName` 取值，**可用于无 ES 的 contact-only 路径**。
   - `resolveFamilyName(expertProfile, contact)` — 只读 `expertProfile.familyNames`，`expertProfile == null` 时**恒返回空串**；contact-only 路径注入它等于永远走 fallback，属误导性接线。

## 教训（元层面）

这条过期是"知识 seeds research, does not replace it"的实例：条目写于 2026-08-06，一次重构在 5 天内让它失效。**任何 K 条目在进 plan 前必须重新 grep 验证**，尤其是带具体行号的结论。

关联：[[K-renderText-all-callers]]（本函数是 5 个变量注入入口中的第 3 个）、[[K-recipient-name-no-technical-identifier]]、[[K-material-reminder-single-compose-seam]]、[[K-unsubscribe-variable-injection-sites]]。
