---
id: K-cleanedbody-inbound-only
domain: mail
created: 2026-06-29
last_used: 2026-06-29
hit_count: 2
source: create-p:translate-source-cleaned-body
severity: P2
---
经验：`MailRecord.cleanedBody`（=`MailBodyCleaner.clean(body)`，去引用后的纯回复）**仅 INBOUND 邮件有值**，写入点集中在 `AutoMailReplyService`（:77/:113/:172/:241/:274/:306/:330 等）。OUTBOUND 记录与历史旧数据 `cleanedBody` 为 null/空。该字段已透出到联系详情 DTO `ExpertContactManagementController.MailRecordResponse.cleanedBody`（`MailRecord.toResponse()` 映射），前端 `mail.cleanedBody` 可直接用。
正确做法：任何想「只处理专家回复正文」的功能（翻译、摘要、QA 匹配预览等）取 `cleanedBody` 时，必须 `cleanedBody?.takeIf{isNotBlank} ?: body` 回退全文，否则 OUTBOUND/旧数据为空。后端已有此回退范式：`UnmatchedInboundMailController:215/229`。
关联：K-mail-body-display-sites（正文展示点全集）、K-plaintext-reply-client-reflow。
