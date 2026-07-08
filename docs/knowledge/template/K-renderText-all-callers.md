---
id: K-renderText-all-callers
domain: template
created: 2026-07-06
last_used: 2026-07-08
hit_count: 8
source: create-p:template-expert-variables-and-fallback
---

`MailComposeTemplateService.renderText()` 是所有模板变量替换的唯一实现点。任何对其行为的修改影响以下全部调用链：

**内部调用点（MailComposeTemplateService 内）:**
1. `renderTemplate()` line 115 — subject 替换
2. `resolveBlocks()` line 248 — QA_RULE block body
3. `resolveBlocks()` line 279 — REPLY_SNIPPET block content
4. `resolveBlocks()` line 294 — CUSTOM_TEXT block customText

**外部 variables 注入入口:**
1. `IntroductionMailComposer.compose()` — sender + expert 变量
2. `AutoMailReplyService.mailTemplateVariables()` — sender 变量
3. `ManualExpertMailService.mailTemplateVariables()` — sender 变量
4. `AutoReplyPreviewService.mailTemplateVariables()` — sender 变量
5. `MeetingInvitationMailComposer` — sender 变量

修改 renderText 时必须确保所有 5 个入口的现有行为不变。
