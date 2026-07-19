---
id: K-ai-generation-observability-not-send-gate
domain: mail
created: 2026-07-16
last_used: 2026-07-20
hit_count: 15
source: create-p:ai-adopt-direct-manual-send
severity: P1
---
经验：当产品明确将 AI 草稿定位为人工写作辅助、而非发送审批时，初稿 quality/audit 写入只能用于观测；不得让历史 READY/NEEDS_REVIEW/BLOCKED、draft identity 或日志写失败影响人工富文本外发。采用操作只负责把对应草稿复制进编辑器并保留 raw/rendered 边界，最终外发仍走统一变量渲染与 QA 关联链。
