---
id: K-outbound-message-id-single-factory
domain: mail
created: 2026-08-06
last_used: 2026-08-06
hit_count: 0
source: create-p:outbound-message-id-01-fill-missing
---

出站 Message-ID 的唯一生成入口是 `OutboundMessageIdFactory.newId(kind, discriminator, senderEmail)`（写侧专用；读侧归一化/匹配见 [[K-vendor-message-id-prefix.md]]，由 `inbound-message-id-vendor-prefix` 计划创建并链接回来）。同一事实的写侧与读侧各记一处。

规则：
- **唯一入口**：任何外发邮件路径都必须在 `ComposedMail` 上显式设置 `messageId = OutboundMessageIdFactory.newId(...)`，禁止留空依赖 JavaMail 默认生成（默认格式会把服务器主机名暴露到公网邮件头，可被 ESP 指纹识别）。
- **域名取自本次投递账号**：`domain = account.senderEmail.substringAfter("@")`，其中 `account` 是本次投递实际使用的 `MailSenderAccount`；禁止域名字面量、hostname、InetAddress 或配置注入。`senderEmail` 不含 `@` 或域名为空白 → `IllegalArgumentException`（fail-fast）。
- **唯一性只依赖 UUID**：格式 `<{kind}-{discriminator}-{uuid}@{domain}>`，`uuid` 为 `UUID.randomUUID().toString()`；`kind` / `discriminator` 仅供人工排查，禁止任何代码解析、匹配或作为查询条件（`MailRecordRepository.findByMessageId()` 为精确相等、格式无关）。
- **同类邮件同 kind**：同一邮件类型的多条产出路径必须使用同一 `kind`，防止同类邮件出现两种前缀（前车之鉴：`intro-` / `manual-outreach-` 分裂）。例：MEETING_INVITATION 的 `MeetingInvitationMailComposer` 与 `AutoMailReplyService:958` 均为 `meeting-invitation`。

任何新增外发邮件路径都应继承以上规则。
