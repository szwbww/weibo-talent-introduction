---
id: K-qa-coverage-keys-management-write-boundary
domain: qa
created: 2026-07-19
last_used: 2026-07-21
hit_count: 1
source: create-p:ai-reply-05-trust-boundary-readiness-plan
severity: P1
---
经验：`qa_rule.coverage_keys` 当前主要由 Flyway 回填；`QaRuleManagementService.createRule()` 强制写空，`updateRule()` 不更新该字段，即使 command 带 `coverageKeys` 也不会落库。
正确做法：任何依赖 coverage key 的生成/门禁都必须把“请求存在但无法分类或无 evidence”视为不可自动放行，不能用 displayName、keywords 或 replyBody 推断事实覆盖；若要支持后台维护 coverageKeys，必须单独审计并改造 create/update 两条写路径。
