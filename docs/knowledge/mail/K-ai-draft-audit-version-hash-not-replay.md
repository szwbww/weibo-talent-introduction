---
id: K-ai-draft-audit-version-hash-not-replay
domain: mail
created: 2026-07-19
last_used: 2026-07-27
hit_count: 2
source: create-p:ai-reply-06-draft-audit-evidence-preflight
severity: P1
---
经验：事实表没有 immutable revision 时，`updatedAt` 不能单独证明 AI 草稿用了哪版正文；但把完整事实/草稿复制进通用操作日志又会形成泄露和无界 payload。
正确做法：审计保存事实 ID、`updatedAt`、exact `answerBody` SHA-256 和有序集合 hash，同时明确它只支持版本追踪与变更检测，不支持历史正文重放。需要逐字复现时另建有权限、加密和保留期的 immutable snapshot store。
关联：[[K-review-event-audit-payload-bounds]]、[[K-answerbody-source-exclusive]]。
