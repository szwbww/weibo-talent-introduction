---
id: K-workbench-lock-replay-needs-dedicated-state-store
domain: llm
created: 2026-08-04
last_used: 2026-08-05
hit_count: 1
source: create-p:trust-reply-durable-locks-and-assembly-generation
severity: P1
---

逐项工作台若要求刷新后恢复锁定回答，不能依赖浏览器 closure、生成审计或 versionId 单独重放：LLM 正文不可由 hash/versionId 重新推导，通用操作日志也不应保存完整敏感正文。

应使用独立、受限的 mutable state store，只保存服务端重新验证过的 locked snapshot；bootstrap 恢复时必须重算 source/evidence version 并重物化 versionId。保存使用乐观版本，冲突不得静默覆盖；active-only 草稿不属于 durable state。
