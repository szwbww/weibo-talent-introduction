---
id: K-ai-reply-prompt-vs-send-rule-ids
domain: qa
created: 2026-06-29
last_used: 2026-06-29
hit_count: 1
source: fix-v:ai-deepseek-manual-reply:fix-1
severity: P1
---
经验：AI 草稿生成里“给 LLM 的 QA 知识范围”和“发送审计用的 QA 匹配子集”不是同一个概念。无匹配时可以给 prompt 回退全集帮助生成参考草稿，但不能把全集返回成发送用 `qaRuleIds`。
正确做法：服务层同时保留 `promptQaRuleIds` 与 `sendQaRuleIds` 语义；只有 `QaMatchService.suggestComposition(...).suggestedRuleIds` 的真实匹配子集能进入 `mail_record_qa_rule`，空子集采用草稿必须走人工富文本回复。
反例：`AiReplyDraftService.kt:62-67` 在 `suggestedRuleIds` 为空时返回 `findAllEnabledOrdered()` 全集，`app.js:5849-5861` 因 `qaRuleIds` 非空走组装台发送，导致 `mail_record_qa_rule` 关联无关全集规则。
