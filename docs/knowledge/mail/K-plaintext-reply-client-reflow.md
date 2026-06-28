---
id: K-plaintext-reply-client-reflow
domain: mail
created: 2026-06-27
last_used: 2026-06-28
hit_count: 2
source: create-p:qa-reply-paragraph-formatting
severity: P2
---
经验：外发邮件以 `ComposedMail(html=false)` 走 `setText` 纯文本时，Gmail/Outlook 网页版会重排纯文本，`\n\n` 段落塌成一堵墙；段落格式诉求不能只靠在正文里加换行解决。
正确做法：需要保留段落的回复用 `MailContentService.plainTextToHtml(plain)` 生成 HTML，`ComposedMail(body=html, html=true, text=plain)` 走 `SmtpMailDeliveryService` 既有 multipart/alternative 分支（:33-43）；`mail_record.body` 仍持久化纯文本，因为前端 `.pre`（white-space:pre-wrap, styles.css:1506）用 `escapeHtml` 渲染（app.js:5022）、审计 `bodyPreviewText` 也读纯文本。
反例：QA 三个发送点 `AutoMailReplyService:469`、`PendingMailOperationService:102/291` 原先全 `html=false`，正文到达客户端后段落丢失。
关联：内部段落还需数据侧补 `\n\n`（单条 reply_body 本身无换行时 HTML 化也无段落）。
