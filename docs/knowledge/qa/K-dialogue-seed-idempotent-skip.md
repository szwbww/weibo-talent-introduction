---
id: K-dialogue-seed-idempotent-skip
domain: qa
created: 2026-07-12
last_used: 2026-07-12
hit_count: 2
source: create-p:ai-training-dialogue-style-curation
---
经验：`AiTrainingDialogueSeeder` 对已存在 `sourceRef` 的 `ai_training_dialogue` 行执行存在即跳过；只修改 `ai-training/dialogue-seed.json` 仅影响全新数据库，存量环境会继续使用旧 turns/keywords/enabled。
正确做法：对话范例内容治理必须同时提供新 Flyway migration，对存量 sourceRef 做幂等 upsert/disable；JSON 与 migration 必须由测试约束为同一启用集合和语义。
