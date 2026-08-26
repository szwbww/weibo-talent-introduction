---
id: K-qa-rule-enable-must-revalidate-facts
domain: qa
created: 2026-08-04
last_used: 2026-08-21
hit_count: 1
source: create-p:trust-reply-atomic-facts-and-duplicate-guard
---
经验：`QaRuleManagementService.setRuleEnabled()` 直接持久化 `enabled`，不会经过 create/update 的正文或 coverage 校验；禁用记录因此不能被视为已验证安全。
正确做法：只要某条 QA 事实不变量依赖 `answerBody`、`replyBody` 或 `coverageKeys`，任何 enable 转换都必须在 repository save 前以已存完整状态重验；拒绝时不得写库。disable 不应借机重分类或回写 legacy 规则。
