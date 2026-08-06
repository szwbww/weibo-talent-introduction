---
id: K-manual-expert-mail-sender-only-variables
domain: mail
created: 2026-08-06
last_used: 2026-08-06
hit_count: 0
source: create-p:material-reminder-01-threading
severity: P1
---

经验：`ManualExpertMailService.mailTemplateVariables()`（`:196-204`）**只注入 6 个 sender 变量**（`senderEmail` / `senderName` / `senderTitle` / `teamName` / `countryName` / `senderDisplayName`），**零个专家变量**。因此在 compose 模板正文里写 `${expertName}` / `${institution}` 等专家占位符，经手动发送与批量材料提醒路径外发时**不会渲染**——只会输出字面量或走 `${key|fallback}` 回退。

这与 `IntroductionMailComposer.compose()` 形成不对称：后者经 `MailVariableService.buildVariables()` 注入全量 sender + expert + unsubscribe 变量。同一个模板在两条路径下渲染结果不同。

正确做法：任何"提醒/手动邮件正文个性化"需求都是**代码改动**，不是后台编辑模板正文。必须先在 `mailTemplateVariables()` 补注入点，且该函数需要 `ExpertContact` 参数（当前签名只收 `MailSenderAccount`）。

注意 `ExpertRecipientNamePolicy`（`MailVariableService.kt:13`，`internal object`，同 module 可直接引用）的两个函数语义不同：
- `resolveRecipientName(contact, expertProfile)` — `expertProfile` 传 null 时仍能从 `contact.expertName` 取值，**可用于无 ES 的 contact-only 路径**。
- `resolveFamilyName(expertProfile, contact)` — 只读 `expertProfile.familyNames`，`expertProfile == null` 时**恒返回空串**。contact-only 路径注入它等同于永远走 fallback，属误导性接线。

关联：K-renderText-all-callers（本函数是 5 个变量注入入口中的第 3 个）、K-recipient-name-no-technical-identifier（称呼必须过滤技术 ID）。
