---
id: K-ai-research-profile-authority-parity
domain: llm
created: 2026-07-16
last_used: 2026-07-16
hit_count: 2
source: fix-v:ai-reply-p0-p2-master-plan:fix-1
severity: P1
---
经验：intent catalog 与 context service 各维护研究短语时，新 alias 可能命中 `requiresProfile` intent，却不产生画像不足 warning，随后被误判为画像充分。
正确做法：画像需求、coverage 标记和 warning 必须从同一 matched-intent authority 派生；画像充分性使用实际查询结果，不以“warning 缺席”反推。
反例：`research fit` 命中 `expertise.programme_fit`，但旧 `RESEARCH_PHRASES` 不识别，缺画像仍可被标为 supported。
