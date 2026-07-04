---
id: K-expert-document-ownership-chain
domain: document
created: 2026-07-04
last_used: 2026-07-04
hit_count: 2
source: create-p:expert-document-ai-analysis
---
经验：验证某 attachment 属于某 expertContact 的链路为：`expert_document.expert_contact_id = contactId AND expert_document.mail_attachment_id = attachmentId`。不能跳过 expert_document 直接用 mail_attachment.mail_record_id → mail_record.expert_contact_id，因为存在 inbound_processing_id 非 null 的附件未必关联到 expert_document。
