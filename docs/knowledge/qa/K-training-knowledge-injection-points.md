---
id: K-training-knowledge-injection-points
domain: qa
created: 2026-07-12
last_used: 2026-07-21
hit_count: 12
source: create-p:ai-training-real-reply-integration
---
经验：`ai_training_qa` 训练知识进入 LLM prompt 的通道只有一条——`AiTrainingQaService.buildKnowledgeContext()` → `AiReplyContextBuilder.appendKnowledgeToProfile()` 拼入 expertProfile；当前 expertProfile 被 QA_GROUNDED 与 FREE_FORM 消费，QA_MATCHED（verbatim 拼接）完全不读 expertProfile/mailHistory/few-shot。
含义：① 任何“训练知识对 AI 综合草稿生效”的需求应继续复用 appendKnowledgeToProfile，别开第二通道；② knowledge 必须在进入 profile 前按当前 inbound 定向筛选，禁止全量注入；③ 对 QA_MATCHED 注入训练知识违反 verbatim 契约。消费方全集见 [[K-ai-generate-single-freeform-seam]]。
