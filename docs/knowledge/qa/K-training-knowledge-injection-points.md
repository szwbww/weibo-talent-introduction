---
id: K-training-knowledge-injection-points
domain: qa
created: 2026-07-11
last_used: 2026-07-12
hit_count: 6
source: create-p:ai-training-real-reply-integration
---
经验：`ai_training_qa` 训练知识进入 LLM prompt 的通道只有一条——`AiTrainingQaService.buildKnowledgeContext()` → `AiReplyContextBuilder.appendKnowledgeToProfile()` 拼入 expertProfile，且 expertProfile 只被 FREE_FORM 模式的 `buildFreeFormUserContent` 消费；QA_MATCHED（verbatim 拼接）完全不读 expertProfile/mailHistory/few-shot。
含义：① 任何"训练知识对某路径生效"的需求 = 让该路径的 expertProfile 走 appendKnowledgeToProfile，别开第二通道（重复注入挤占 12000 字符预算）；② 对 QA_MATCHED 注入训练知识违反 verbatim 契约，属明确不做项。消费方全集见 [[K-ai-generate-single-freeform-seam]]。
