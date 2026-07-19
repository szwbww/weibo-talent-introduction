---
id: K-sensitive-cta-compound-material-coverage
domain: llm
created: 2026-07-19
last_used: 2026-07-19
hit_count: 2
source: fix-v:ai-reply-05a-action-policy-negation-plan:fix-3
severity: P1
---
经验：敏感材料 CTA 按连接词切 clause 时，`Please send your passport and bank statement.` 的动词可只绑定前一 clause，sanitizer 删除护照请求后遗留银行证明索取，绕过最终 gate。
正确做法：并列材料共享一个正向 CTA 动词时，检测和删除 span 必须覆盖整个请求；测试须断言完整 sanitize 输出，不能只断言首个材料词消失。
反例：AiReplyActionPolicy.kt:255,272-297 将 `and` 切为独立 clause，后半 `bank statement` 无 CTA prefix 而未被删除。
