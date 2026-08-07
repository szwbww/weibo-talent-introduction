---
id: K-vendor-message-id-prefix
domain: mail
created: 2026-08-06
last_used: 2026-08-06
hit_count: 0
source: create-p:inbound-message-id-vendor-prefix
---

腾讯企业邮中继会在投递时改写外发 Message-ID，给 local-part 加 16 位**大写**十六进制 + `+` 前缀（`[0-9A-F]{16}+`）。该改写行为**无腾讯官方文档**，属生产观测所得。`mail_record.message_id` 落库的是**交给中继前**的值（`SmtpMailDeliveryService` 取 `DeliveredMail.messageId`），因此库内值与**实际投递值**不一致（详见 [[K-message-id-fingerprint.md]] 三次复验修正）。

观测样本（跨一个月、跨两个 QQ 中继集群，格式一致）：

| 样本 | 投递日期 | 中继 | 实际投递 Message-ID | 我方生成 |
|---|---|---|---|---|
| 提醒邮件 | 2026-08-06 | `smtpbgeu2.qq.com` | `<6136051B41AACA62+reminder-2088-710aba50-77fa-4936-a8d3-72ecffaba836@talents.szwebotech.cn>` | `<reminder-2088-710aba50-...@talents.szwebotech.cn>` |
| 介绍邮件 | 2026-07-05 | `smtpbgjp3.qq.com` | `<ED4DEF51D75D746B+1387390957.0.1783265426131@VM-4-16-centos>` | JavaMail 默认（早于 `87eb186`） |

受影响的两个读路径（入站引用的是投递后值，与库内无前缀值精确匹配必然落空）：

1. `UnmatchedInboundMailService.suggestCandidates()` —— 用入站 `in_reply_to` 查出站 `message_id`（人工队列 IN_REPLY_TO 候选，confidence 90）。
2. `BounceCollectionService.resolveOriginalContact()` —— 用退信解析出的 `originalMessageId` 查原始外发记录。

读侧归一化规则（`MessageIdNormalizer`，只读、无状态、写侧零改动）：

- **只做有界剥离**：仅剥离 local-part 开头恰好匹配 `^[0-9A-F]{16}\+` 的部分（16 位大写 hex + `+`），剥离后**不做任何格式假设**，不匹配则原样返回。小写 hex、长度非 16、local-part 含 `+` 等一律不剥离。
- **有限候选 + 精确相等**：候选顺序 ①原值(trim) ②尖括号规范化 ③剥离前缀，过滤空白、保序去重，逐个走 `MailRecordRepository.findByMessageId(exactValue)` **精确相等**查询，首个命中即返回。禁止 `LIKE` / `Containing` / `EndingWith` / `StartingWith` 等模糊匹配。
- **写侧零改动**：`mail_record.message_id` / `in_reply_to` 落库值逐字不变；`MessageIdNormalizer` 禁止在写侧（`SmtpMailDeliveryService` / `OutboundMessageIdFactory`）调用。

出站 Message-ID 的唯一生成入口见 [[K-outbound-message-id-single-factory.md]]。任何未来涉及 Message-ID 匹配的计划都应继承本规则。
