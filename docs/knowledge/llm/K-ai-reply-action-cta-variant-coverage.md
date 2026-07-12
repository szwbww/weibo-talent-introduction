---
id: K-ai-reply-action-cta-variant-coverage
domain: llm
created: 2026-07-12
last_used: 2026-07-12
hit_count: 1
source: fix-v:ai-reply-action-policy-runtime:fix-1
severity: P1
---
经验：AI 回复的确定性 CTA 拦截若只覆盖祈使句，会让常见疑问式英文索要材料请求穿透最终 sanitize。
正确做法：直接动作 regex 与测试必须同时覆盖祈使句、疑问式请求和材料同义词；所有变体必须经过 `findViolations`、`sanitize` 和运行时最终 gate 验证。
反例：`AiReplyActionPolicy.kt:32-37` 未匹配 `Could you share your CV?` 或 `Would you mind forwarding your résumé?`。
