---
id: K-message-id-fingerprint
domain: mail
created: 2026-08-06
last_used: 2026-08-06
hit_count: 8
source: create-p:mail-personalization-anti-spam
last_source: create-p:ai-reply-final-send-identity-scope-repair
---

`ManualInitialOutreachService` 已生成 UUID-based Message-ID（`<manual-outreach-{orcid}-{uuid}@weibo.com>`），但 `InitialOutreachService` 路径下 `IntroductionMailComposer.compose()` 不设置 messageId，导致 JavaMail 使用默认格式 `<hash.JavaMail.user@hostname>`，可被 ESP 指纹识别为批量工具。

任何新增的外发邮件路径都必须在 ComposedMail 上显式设置 UUID-based messageId。

---

**2026-08-06 复验修正**：本条此前只覆盖 `InitialOutreachService`，实际审计发现 `ComposedMail` 的 8 个构造点中**有 5 个不设 messageId**，全部落到 JavaMail 默认格式：

| 构造点 | 邮件类型 | messageId |
|---|---|---|
| `IntroductionMailComposer.kt:28` | INTRODUCTION | ✅ `<intro-{orcid}-{uuid}@{domain}>` |
| `ManualInitialOutreachService.kt:587` | INTRODUCTION | ✅ `<manual-outreach-{orcid}-{uuid}@weibo.com>` |
| `MeetingInvitationMailComposer.kt:22` | MEETING_INVITATION | ❌ |
| `MeetingScheduleService.kt:125` | MEETING_CONFIRMATION | ❌ |
| `AutoMailReplyService.kt:567` | QA 自动回复 | ❌ |
| `AutoMailReplyService.kt:958` | 自动回复（另一分支） | ❌ |
| `PendingMailOperationService.kt:258` | 人工富文本回复 | ❌ |
| `ManualExpertMailService.kt:175` | COMPOSE_TEMPLATE / MATERIAL_REMINDER | ❌ |

`docs/plans/2026-08-06/material-reminder-01-threading.md`（I-3）只修最后一行。其余 4 处仍是既有缺陷，任何触及这些路径的计划都应顺手收口。

注意 `SmtpMailDeliveryService.kt:20-28` 的实现细节：只有 `mail.messageId != null` 时才用匿名 `MimeMessage` 子类覆写 `updateMessageID()`；为 null 时走 `sender.createMimeMessage()`，由 JavaMail 生成。落库的 `mail_record.message_id` 取 `message.messageID`，两种情况下都与实际发出值一致。

**2026-08-06 二次复验修正（p3-outbound-message-id-01 回写）**：上表两处失准，缺失数由「5 处」更正为「4 处」：
- `PendingMailOperationService.kt:258` 实际**已设置** `messageId`（`:264` 取 `claim.messageId`），问题不是缺失而是**域名硬编码**（`<manual-rich-{uuid}@weibo.com>`），属第二批 `outbound-message-id-02-domain-alignment.md`。
- `ManualExpertMailService.kt` 已由 `3bff469`（material-reminder-01-threading）修复，产出 `<reminder-{contactId}-{uuid}@{senderDomain}>`。

因此真正缺失的 4 处为：`MeetingInvitationMailComposer.kt:22`、`MeetingScheduleService.kt:125`、`AutoMailReplyService.kt:567`、`AutoMailReplyService.kt:958` —— 已由 `outbound-message-id-01-fill-missing.md` 收口，统一经 `OutboundMessageIdFactory` 生成（见 [[K-outbound-message-id-single-factory]]）。

**2026-08-06 三次复验修正（p2-inbound-message-id-vendor-prefix 回写）**：上文「落库的 `mail_record.message_id` 取 `message.messageID`，两种情况下都与实际发出值一致」被本次审计**证伪**。`message.messageID` 是交给中继**之前**的值；腾讯企业邮中继会在投递时改写 Message-ID，给 local-part 加 `[0-9A-F]{16}+` 前缀（16 位大写十六进制 + `+`），因此**库内值 ≠ 实际投递值**：库内值 = 交给中继前值。入站 `in_reply_to` / 退信 `originalMessageId` 引用的是投递后值，与库内 `message_id` 精确匹配必然落空 —— 读侧须经 `MessageIdNormalizer` 构造候选（原值 → 规范化 → 有界剥离前缀）逐个精确查询（见 [[K-vendor-message-id-prefix.md]]）。
