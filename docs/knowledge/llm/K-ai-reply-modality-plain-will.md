---
id: K-ai-reply-modality-plain-will
domain: llm
created: 2026-07-16
last_used: 2026-07-16
hit_count: 3
source: fix-v:ai-reply-07-p1-intent-output-and-claim-validation:stop-after-fix-3
severity: P1
---
经验：条件性 QA 写 `may/can/depends` 时，只拦截 `will definitely/guaranteed` 仍会放过普通 `will receive`，从而把可能性强化为确定承诺。
正确做法：modality 校验比较来源与回答的强度层级；来源存在条件措辞时，普通 `will/shall/is entitled` 也必须视为强化，除非同一引用正文明确支持确定语气。
