---
id: K-cold-outreach-html-asymmetry
domain: mail
created: 2026-09-25
last_used: 2026-09-25
hit_count: 1
source: create-p:mail-open-tracking-00-master
severity: P1
---

2026-09-25重新审计：旧结论“INTRODUCTION纯文本、MATERIAL_REMINDER为HTML”已失效。

- IntroductionMailComposer.compose:37–45 已同时提供 html=true、body=HTML、text=plain。
- ManualExpertMailService.composeComposeTemplate:265–279 同样提供HTML与text。
- InitialOutreachService及ManualInitialOutreachService将介绍邮件正文经 `mail.text ?: mail.body` 交给成功/失败记录链；修改MIME不能误将HTML或随机像素回写到这些审计字段。
- SmtpMailDeliveryService:46–62与:64–98是无附件/带附件两条MIME路径；改HTML能力必须核对两条及originalBodyPart。
- MailContentService.htmlToPlainText:25起删除标签，不保留href URL。原HTML外发仍应显式提供text，不能假定HTML回退能保存链接。

可复用规则：先审计真实html/text与持久化body的三者关系；不得把历史计划中的形态当当前事实。原始证据：docs/plans/2026-09-25/mail-open-tracking-evidence/source-excerpts.txt。
关联：K-plaintext-reply-client-reflow、K-dual-outreach-paths。
