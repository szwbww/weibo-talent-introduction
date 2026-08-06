---
id: K-outbound-thread-headers-single-seam
domain: mail
created: 2026-08-06
last_used: 2026-08-06
hit_count: 0
source: create-p:material-reminder-01-threading
---

现状（2026-08-06 审计）：出站邮件的 `In-Reply-To` / `References` 头在全代码库**从未被设置**。grep `In-Reply-To` 只在读取侧命中两处：`ImapMailReceiveService.kt:135`、`BounceDetector.kt:193`。`mail_record.in_reply_to` 列（`V1__create_business_tables.sql:103`，`VARCHAR(255)`）虽被自动回复与人工回复路径填充，但那只是**库内记账**，从未落到实际发出的 MIME 头上。

后果：所有外发邮件（含对已回信专家的跟进）在收件人客户端里都是独立新会话，无法继承原线程的分类与信誉信号。

写路径边界（改线程头时的完整改动面）：
- **唯一 MIME 写入点**：`SmtpMailDeliveryService.send()` —— 全部 7 个投递调用点共用（`PendingMailOperationService:270`、`AutoMailReplyService:574/:963`、`ManualExpertMailService:57`、`ManualInitialOutreachService:626`、`MeetingScheduleService:130`、`InitialOutreachService:66`）。
- **唯一载体**：`ComposedMail`（`IntroductionMailComposer.kt:59`）—— 全部 8 个构造点。新增字段必须带默认值，否则 8 处全要改。
- **锚点数据源**：`MailRecordRepository.findLatestInboundByExpertContactId(contactId)` 已存在，无需新增查询。

规则：线程头只能取自真实 INBOUND 记录的 `messageId`；缺失时**不写头**（fail-open 照常发送），禁止伪造或写空串。库内 `in_reply_to` 必须与实际发出的头同源同值，两侧不一致会让 `UnmatchedInboundMailService.suggestCandidates()` 的 `IN_REPLY_TO`（confidence 90）审计失真。`in_reply_to` 与 `subject` 均为 `VARCHAR(255)`，需长度守卫（先例：`PendingMailOperationService.kt:245-249`）。
