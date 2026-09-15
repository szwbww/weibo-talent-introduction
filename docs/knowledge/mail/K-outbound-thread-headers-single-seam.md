---
id: K-outbound-thread-headers-single-seam
domain: mail
created: 2026-09-09
last_used: 2026-09-14
hit_count: 4
source: create-p:material-reminder-01-threading
---

2026-09-09 重新读代码：旧“全库未设置线程头”结论已失效。`ComposedMail`（IntroductionMailComposer.kt:73）已有可空默认 `inReplyTo` / `references`；`SmtpMailDeliveryService.send` 的唯一MIME写入点已将非空值设置到真实邮件头。AutoMailReplyService、ManualExpertMailService 的部分构造点已传入。

审计仍必须检查**调用方实际传参**：PendingMailOperationService.sendManualRichReply:383 当前只传messageId，没有传这两个头；其SendPayload.inReplyTo和mail_record.in_reply_to仍记真实来信messageId。不能把数据库记账等同于已写入MIME，也不能把这一处缺参推广成所有出站路径缺失。

可复用规则：修改线程行为时，逐项追踪真实INBOUND messageId→ComposedMail→SMTP headers→mail_record.in_reply_to；没有真实messageId时不伪造。对已有调用新增可选载体字段必须带默认值；是否改变旧调用的线程行为需要明确范围，不能借新附件功能顺带修改全站发件。

本次证据：docs/plans/2026-09-09/meeting-confirmation-audit.md D3；原始构造点见同目录meeting-confirmation-evidence/constructors.txt。本条描述现有代码，不意味着会议日历功能已实现。
