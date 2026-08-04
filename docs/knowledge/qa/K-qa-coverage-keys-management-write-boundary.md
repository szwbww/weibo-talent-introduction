---
id: K-qa-coverage-keys-management-write-boundary
domain: qa
created: 2026-07-19
last_used: 2026-08-04
hit_count: 5
source: create-p:ai-reply-05-trust-boundary-readiness-plan
last_source: fix-v:ai-reply-failure-trust-closure-master-plan:blocked-after-fix-1
severity: P1
---
经验：`qa_rule.coverage_keys` 当前主要由 Flyway 回填；`QaRuleManagementService.createRule()` 强制写空，`updateRule()` 不更新该字段，即使 command 带 `coverageKeys` 也不会落库。
正确做法：任何依赖 coverage key 的生成/门禁都必须把“请求存在但无法分类或无 evidence”视为不可自动放行，不能用 displayName、keywords 或 replyBody 推断事实覆盖；若要支持后台维护 coverageKeys，必须单独审计并改造 create/update 两条写路径。

补充：启用 coverage 硬门禁前必须统计线上空 coverage 规则。若存量覆盖率不足，先对合同、IP、薪酬结构、保密、费用等高风险 intent 强制，其他 legacy intent 暂兼容；完成全量语义回填后才能取消兼容，禁止一次切换导致大量既有规则突然 MISSING。
