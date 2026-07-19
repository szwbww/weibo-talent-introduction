---
id: K-message-id-fingerprint
domain: mail
created: 2026-07-06
last_used: 2026-07-20
hit_count: 3
source: create-p:mail-personalization-anti-spam
---

`ManualInitialOutreachService` 已生成 UUID-based Message-ID（`<manual-outreach-{orcid}-{uuid}@weibo.com>`），但 `InitialOutreachService` 路径下 `IntroductionMailComposer.compose()` 不设置 messageId，导致 JavaMail 使用默认格式 `<hash.JavaMail.user@hostname>`，可被 ESP 指纹识别为批量工具。

任何新增的外发邮件路径都必须在 ComposedMail 上显式设置 UUID-based messageId。
