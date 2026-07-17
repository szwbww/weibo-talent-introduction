---
id: K-ai-reply-json-integral-identifiers
domain: llm
created: 2026-07-15
last_used: 2026-07-17
hit_count: 6
source: fix-v:ai-reply-07-p1-intent-output-and-claim-validation:fix-3
severity: P1
---
经验：JSON 严格 schema 若只用 Jackson `canConvertToInt()` 验证 request index，小数 `1.5` 会被 `asInt()` 截断成 `1`，从而冒充 matrix 中的合法 request。
正确做法：所有协议标识字段先要求 JSON integral number，再读取数值并校验 matrix 成员；不能用范围转换或截断作为类型验证。
反例：`AiReplyGroundedDraftMaterializer.kt:83-86`。
