---
id: K-recipient-name-no-technical-identifier
domain: mail
created: 2026-07-21
last_used: 2026-07-21
hit_count: 0
source: create-p:ai-reply-10-history-context-recipient-identity
severity: P1
---
经验：索引层 `displayName` 可以合理 fallback 到 ORCID/EMAIL 主键，但邮件收件人称呼不能复用该语义，否则会渲染 `Dear EMAIL-*`。
正确做法：邮件变量 `expertName/expertFamilyName` 只取真人 given/family 字段，并过滤 EMAIL-*、ORCID、邮箱和等于技术 ID 的值；非法时返回空串，让 `${expertName|Professor}` 生效。preview、plain、HTML 与 AI profile 必须共享同一过滤策略。
写路径边界：`expert_contact` 可被 conversation/contact/index/status/outreach/auto/pending/unmatched 等多条状态 writer 更新；称呼修复应在消费边界过滤，不得为了邮件显示反写或清洗联系人/ES 主键。
反例：`MailVariableService.buildVariables()` 直接把 `ExpertProfile.displayName` 写入 expertName。
