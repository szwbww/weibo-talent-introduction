---
id: K-outbound-thread-headers-single-seam
domain: mail
created: 2026-09-25
last_used: 2026-09-25
hit_count: 5
source: create-p:mail-open-tracking-00-master
---

2026-09-25重新审计：ComposedMail已有inReplyTo/references，SmtpMailDeliveryService:43–44统一写入非空线程头；但不是所有真实回复调用方均传入。

- AutoMailReplyService:748–754 QA回复和:1229–1234来信触发邀约未传SMTP回复头，保存mail_record时却写sourceInboundId/inReplyTo。
- PendingMailOperationService:322–324普通回复来源smtp头为null，:470–472会话回复来源带真实锚点头；:722–740统一交给SMTP。
- ManualExpertMailService:244–279材料提醒可读真实来信锚点，有messageId才设置头；command.sourceInboundId目前另存记录。
- MeetingScheduleService:135–159不传SMTP回复头，记录可带schedule.sourceMailRecordId。

可复用规则：回复判定须审计实际业务上下文、DTO参数与MIME头，不能把记录字段存在等同于头已发送，也不能以头缺失证明新会话。修改某一能力不得顺带补全站线程头。
证据：docs/plans/2026-09-25/mail-open-tracking-audit.md §1。
