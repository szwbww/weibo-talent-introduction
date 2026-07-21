---
id: K-grounded-answerbody-no-legacy-fallback
domain: llm
created: 2026-07-17
last_used: 2026-07-21
hit_count: 11
source: fix-v:qa-refactor-04-grounded-engine:fix-1
last_source: fix-v:ai-reply-failure-trust-closure-master-plan:blocked-after-fix-1
severity: P1
---
经验：grounded prompt、fallback 或高风险校验从 answerBody 回退到 replyBody，会把废弃邮件表达重新作为可外发事实。
正确做法：grounded 全链路只消费非空 answerBody；来源缺失即拒绝引用或转人工，绝不回退旧正文。
