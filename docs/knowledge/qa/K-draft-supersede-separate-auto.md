---
id: K-draft-supersede-separate-auto
domain: qa
created: 2026-07-12
last_used: 2026-07-12
hit_count: 4
source: create-p:ai-reply-grounded-parity-backend
severity: P1
---
经验：`QaMatchService.match()` 服务自动外发，`suggestComposition()` 服务人工组装和 AI 草稿；复合 overview 的 `supersedesChildren` 不能在两者无条件共用。自动回复可保守压成总览，但多问题人工草稿必须保留覆盖前详细命中，否则公司、职责、合同/IP、流程等明确问题会被总览静默删除。
正确做法：自动路径保持既有 supersede；suggestion 先提取请求单元，单请求才 supersede，多请求返回详细命中并逐请求报告 grounded/unsupported。
