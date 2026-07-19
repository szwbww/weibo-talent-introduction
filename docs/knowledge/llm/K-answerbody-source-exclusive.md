---
id: K-answerbody-source-exclusive
domain: llm
created: 2026-07-19
last_used: 2026-07-20
hit_count: 5
source: fix-v:ai-reply-05-trust-boundary-readiness-plan:fix-2
severity: P1
---
经验：Grounded claim 校验若把规则 displayName 混入批准事实，会让标题中的数字、URL 或高风险措辞替 answerBody 背书，破坏事实正文单一来源。
正确做法：任何 claim/source 真实性校验只能读取 enabled 且非空的 answerBody；displayName 只能作 UI/prompt 标签，不能参与事实匹配。
反例：AiReplyHighRiskClaimValidator.kt:168-172 将 displayName 与 answerBody 拼接为 sourceText。
