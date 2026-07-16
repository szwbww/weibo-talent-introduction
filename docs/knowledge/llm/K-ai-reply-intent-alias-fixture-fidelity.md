---
id: K-ai-reply-intent-alias-fixture-fidelity
domain: llm
created: 2026-07-16
last_used: 2026-07-16
hit_count: 4
source: fix-v:ai-reply-06-p1-intent-coverage-matrix:stop-after-fix-3
severity: P1
---
经验：intent catalog 只覆盖测试改写后的问法，会让真实验收邮件退化为 `general.answer` 或漏掉复合子意图；英美拼写、连字符及词序差异都可能静默改变 readiness。
正确做法：以原始验收 fixture 逐 group 断言精确 intent 列表；在 URL 屏蔽后统一规范化 programme/program、常见连字符和空白，再做边界安全 alias 匹配。
反例：`purpose and structure of the programme` 未命中 purpose/structure；`intellectual-property arrangements` 未命中 IP intent。
