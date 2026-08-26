---
id: K-qa-fact-body-required-no-legacy-fallback
domain: qa
created: 2026-07-17
last_used: 2026-08-21
hit_count: 9
source: fix-v:qa-refactor-02-fact-card-foundation:fix-1
severity: P1
---
经验：expand 阶段的新权威字段若可从 deprecated 写字段回退，旧客户端会绕过新语义边界，双写无法证明来源正确。
正确做法：新写请求必须强制提交新字段；旧字段仅保留响应/读取兼容，绝不能作为新字段的写入回退。
反例：`QaRuleManagementService.kt:166-179` 用 `replyBody` 推导缺失的 `answerBody`。
