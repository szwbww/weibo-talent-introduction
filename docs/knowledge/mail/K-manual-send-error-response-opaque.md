---
id: K-manual-send-error-response-opaque
domain: mail
created: 2026-07-20
last_used: 2026-07-20
hit_count: 2
source: fix-v:ai-reply-07-final-send-integrity-plan:fix-3
last_source: fix-v:ai-reply-07-final-send-integrity-plan:stop-after-fix-3
severity: P1
---
经验：SMTP 分类的 `errorDetail` 是持久化诊断字段，不可直接拼入人工发送 HTTP 响应；异常内容可能含服务端细节或认证信息。
正确做法：响应仅返回稳定中文结果与允许公开的状态/Message-ID；有界诊断只保存服务端审计记录。
反例：PendingMailOperationService.kt:325、330 将 `classification.errorSummary` 回显给客户端。
