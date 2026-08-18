---
id: K-auto-reply-decide-context-parity
domain: mail
created: 2026-08-18
last_used: 2026-08-18
hit_count: 0
source: create-p:01-decide-context-closure
severity: P1
---

经验：`GroundedAutoReplyDecisionService.decide()` 是自动预览与自动实发的共享决策点，但它
**从不调 `aiReplyContextService.build()`**，直接
`aiReplyDraftService.generate(inboundText, operatorTurns = emptyList())`，其余 8 个具名参数全走默认值。

后果链（2026-08-18 实测，main @4583525）：
`contextWarnings = emptyList()` ⇒ `generate()` 的默认表达式
`researchProfileSufficient = !contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")` 恒 `true`
⇒ `AiReplyIntentCatalog.resolveIntentEvidence()` 的
`if (intent.requiresProfile && !profileSufficient) → MISSING` 永不触发
⇒ 同一封研究匹配类来信，工作台判 UNSUPPORTED 要人工、自动路判 SUPPORTED 可直发。
**方向是反的：自动路比人工工具更宽松。** 同时 `expertProfile = null`、训练知识零注入。

`aiReplyContextService.build()` 的生产调用点共 4 处：
`AiTrainingController:220`、`TrustReplyWorkbenchService:1367`、`UnmatchedInboundMailController:347`、
`PendingMailOperationService:535`。自动路一处都没有。

正确做法：任何「自动管线与人工工具必须同口径」的需求，先核对**上下文是否同源**再看生成逻辑。
上下文四元组（`expertProfile` / `mailHistory` / `contextWarnings` / `researchProfileSufficient`）
必须整体来自一次 `build()`，不得部分为真部分走默认——半一致比全不一致更难排查。

排除的误报：`PendingMailOperationService:535` 传 `trainingKnowledge = ""` 看着像漏注入，
但它在 `resolveResearchProfileSufficient()` 里只读回 `researchProfileSufficient`，`profileText` 直接丢弃，
不是缺陷。

同样确认**无分叉、不要动**的一项：prompt 配置（`AiPromptConfigService`）在
`AiReplyDraftService:2114/2414` 内部读取，三条入口都经 `generate()`，本来就一致。

关联：[[K-ai-research-profile-authority-parity]]、[[K-research-fit-dual-evidence]]、
[[K-training-knowledge-injection-points]]、[[K-ai-generate-single-freeform-seam]]。
