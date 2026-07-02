---
id: K-mail-record-source-inbound-id
domain: mail
created: 2026-07-02
last_used: 2026-07-02
hit_count: 2
source: create-p:inbound-summary-redesign
---
经验：`mail_record.source_inbound_id` 是 INBOUND 类型 MailRecord 直接链回 `inbound_mail_processing.id` 的外键。在需要将线程消息（MailRecord）关联回来信处理记录（获取标签、处理状态等）时，优先使用此字段，而非通过 `message_id` 字符串匹配。OUTBOUND 类型的 MailRecord 此字段也可能有值（指向其回复的来信记录），使用时注意区分方向。
