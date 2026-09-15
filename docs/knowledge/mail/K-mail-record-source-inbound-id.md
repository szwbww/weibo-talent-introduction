---
id: K-mail-record-source-inbound-id
domain: mail
created: 2026-09-08
last_used: 2026-09-09
hit_count: 11
source: create-p:inbound-summary-redesign
last_source: fix-v:ai-reply-07-final-send-integrity-plan:stop-after-fix-3
---
经验：不能把 `mail_record.source_inbound_id` 当作 `inbound_mail_processing.id`。V15迁移注释明确其设计语义是“触发OUTBOUND的INBOUND mail_record.id”；当前AutoMailReplyService创建INBOUND时写null，创建QA OUTBOUND时写inboundMailRecordId，confirmProcessed另建processing且不建立直接桥接。ManualExpertMailService透传命令值，MeetingScheduleService用sourceMailRecordId。跨表关联必须审计实际写入；不能仅靠字段名或两表数值相同推断。

2026-09-08重新核验：旧条目声称它是processing外键，与V15及当前写入代码不符，已撤销。证据：docs/plans/2026-09-07/audit.md E3。
