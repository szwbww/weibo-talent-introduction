---
id: K-ai-training-seed-idempotent-skip
domain: qa
created: 2026-07-02
last_used: 2026-07-12
hit_count: 5
source: create-p:qa-material-tiering-and-trust
---
经验：`AiTrainingQaSeeder` 启动播种 `ai_training_qa` 时按 `(source=MANUAL_IMPORT, sourceRef)` **存在即跳过**（`AiTrainingQaSeeder.kt:29` `findBySourceAndSourceRef`），只对全新库生效。仅编辑 `ai-training/qa-seed.json` 的 `answer/keywords` 不会更新已播种库的既有行。
影响：`ai_training_qa` 经 `AiTrainingQaService.buildKnowledgeContext()` → `AiReplyContextBuilder.appendKnowledgeToProfile()` 注入 free-form LLM prompt（`AiTrainingController.kt:157`），旧文案会继续影响线上 AI 草稿，绕过基于新 qa_rule 文案的意图（如「重资料不入自动回复」）。
正确做法：任何要在既有环境生效的 qa-seed.json 文案变更，必须配一条 `V<n>` 迁移 `UPDATE ai_training_qa ... WHERE source='MANUAL_IMPORT' AND source_ref=...`；新增条目用幂等 INSERT（存在性守卫/ON DUPLICATE）避免与 seeder 双插。
