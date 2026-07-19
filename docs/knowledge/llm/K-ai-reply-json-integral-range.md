---
id: K-ai-reply-json-integral-range
domain: llm
created: 2026-07-15
last_used: 2026-07-19
hit_count: 7
source: fix-v:ai-reply-07-p1-intent-output-and-claim-validation:stop-after-fix-3
severity: P1
---
经验：仅校验 Jackson 节点为 `isIntegralNumber` 仍不足以保证协议标识可安全映射；超出 `Long` 范围的 JSON 整数经 `asLong()` 会发生截断，可伪装成 matrix 中的合法 rule ID。
正确做法：协议整数标识必须同时校验 JSON integral 类型、明确的数值范围及 matrix 成员关系，再转换为 Kotlin `Long`/`Int`。
反例：`AiReplyGroundedDraftMaterializer.kt:150-155` — `sourceRuleIds:[18446744073709551617]` 可在 `asLong()` 后成为 `1`。
