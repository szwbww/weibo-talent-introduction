---
id: K-outbound-message-id-single-factory
domain: mail
created: 2026-09-25
last_used: 2026-09-25
hit_count: 1
source: create-p:mail-open-tracking-00-master
---

2026-09-25重新审计：OutboundMessageIdFactory存在，但“全部外发均已使用单工厂”不是当前代码事实。

- IntroductionMailComposer:35仍内联UUID构造intro Message-ID。
- ManualInitialOutreachService:816–819覆盖为manual-outreach前缀及固定weibo.com域名。
- ManualExpertMailService也存在内联构造；其它部分路径调用工厂。完整grep：docs/plans/2026-09-25/mail-open-tracking-evidence/message-id-factory.txt。
- mail_record.message_id无数据库唯一约束；findByMessageId不限定OUTBOUND。禁止用前缀判断邮件类型/回复属性，禁止仅按Message-ID关联需要精确身份的新记录。

新增路径优先复用OutboundMessageIdFactory、按实际senderEmail生成域名；旧路径统一化应独立审计立项，不能借别的发信改动顺手修复。格式前缀只用于人工诊断，不是业务身份。
