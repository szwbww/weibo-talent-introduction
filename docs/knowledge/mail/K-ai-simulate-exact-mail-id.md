---
id: K-ai-simulate-exact-mail-id
domain: mail
created: 2026-07-12
last_used: 2026-07-12
hit_count: 6
source: create-p:ai-reply-grounded-parity-backend
severity: P1
---
经验：历史邮件模拟若前端只传 `expertContactId`，后端再查 latest inbound，列表加载后新邮件到达会让“用户点击的邮件”与“实际生成使用的邮件”不一致。
正确做法：列表项返回并提交 `mailRecordId`；后端优先按 id 读取并验证 INBOUND/contact。contactId 只能作为短期兼容 fallback，不能覆盖精确 id。
