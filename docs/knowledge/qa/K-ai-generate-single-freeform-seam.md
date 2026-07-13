---
id: K-ai-generate-single-freeform-seam
domain: qa
created: 2026-07-08
last_used: 2026-07-13
hit_count: 15
source: create-p:ai-training-dialogue-fewshot
---
经验：`AiReplyDraftService.generate()` 全库仅两个调用方——`UnmatchedInboundMailController.aiReplyTurn`（人工工作台 AI 草稿）与 `AiTrainingController.simulate`（AI 训练模拟）。任何要"对所有 FREE_FORM 生成生效"的 prompt 能力（知识注入、few-shot、约束），改 `buildFreeFormMessages` 这一个 seam 即可全覆盖，不需要也不应该在 controller 层各改一份。
注意：QA_MATCHED 模式（`buildMatchedMessages`）有 verbatim 拼接契约，prompt 增强类改动默认不得触碰；fallback / `composeSimulateDeterministicDraft` 是 LLM 之外的独立文本源，同样不注入。
