---
id: K-training-keyword-bare-verb-collision
domain: qa
created: 2026-07-12
last_used: 2026-07-12
hit_count: 0
source: create-p:ai-reply-content-boundary-curation
severity: P1
---
经验：`ai_training_qa` 关键词用于 substring 召回时，裸通用动词会跨意图碰撞；`MATERIALS_LIGHT` 的裸 `provide` 会被尽调句 `provide further information` 命中，向无材料意图的 prompt 注入 CV 答案。
正确做法：动作/材料类知识只用完整意图短语；检索无命中必须返回空，不得回退全量。seed 存在即跳过，关键词修订必须同时有存量 Flyway migration。
