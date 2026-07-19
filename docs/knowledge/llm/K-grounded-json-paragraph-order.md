---
id: K-grounded-json-paragraph-order
domain: llm
created: 2026-07-19
last_used: 2026-07-19
hit_count: 4
source: fix-v:ai-reply-04-grounded-trust-content-plan:fix-2
severity: P1
---
经验：Grounded JSON 用 set/map 校验 paragraph 时会接受对象数组重排，虽然后续 composer 可能按服务端 plan 输出，仍违反严格协议并掩盖模型未遵守的计划。
正确做法：claims、paragraphs、paragraph claimKeys 和 missingFacts 都逐项按 array 顺序、值和次数与 immutable plan 比较；任意重排或重复一律 invalid/fallback。
反例：AiReplyGroundedDraftMaterializer.kt:368-380 仅按 paragraphIndex 映射比较，接受 `[p2,p1]`。
