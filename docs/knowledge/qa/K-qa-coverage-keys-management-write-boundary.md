---
id: K-qa-coverage-keys-management-write-boundary
domain: qa
created: 2026-08-19
last_used: 2026-08-19
hit_count: 6
source: create-p:ai-reply-05-trust-boundary-readiness-plan
last_source: create-p:01-fact-and-catalog
severity: P1
---

> **2026-08-19 复验更正（create-p:01-fact-and-catalog）**：本条原文写的
> 「`QaRuleManagementService.createRule()` 强制写空 coverage_keys；`updateRule()` 不更新该字段，
> 即使 command 带 `coverageKeys` 也不会落库」**已经不成立**，代码后来补齐了这两条写路径。
> 实测回执：
> ```
> $ grep -n "coverageKeys" src/main/kotlin/.../qa/service/QaRuleManagementService.kt
> 74:  val normalizedCoverage = QaCoverageKeyCatalog.normalizeAndValidate(command.coverageKeys)
> 76:  val coverageKeys = QaCoverageKeyCatalog.serialize(normalizedCoverage)
> 83:  coverageKeys = coverageKeys                      # createRule 写入
> 101-103: updateRule 的 effectiveCoverage：null -> parseStored(existing) / else -> normalizeAndValidate(command)
> 106-107: coverageKeys：null -> existing.coverageKeys（保留）
> 120: coverageKeys = coverageKeys                      # 传值时更新
> ```
> 即：**`createRule` 会写；`updateRule` 传值则更新、传 null 则保留原值。**

经验：`qa_rule.coverage_keys` 现在有**两条**写路径 —— Flyway 迁移回填，以及
`QaRuleManagementService.createRule()` / `updateRule()` 的运营 UI 写入。二者会互相覆盖。

正确做法：
- 任何依赖 coverage key 的生成/门禁，仍必须把「请求存在但无法分类或无 evidence」视为不可自动放行，
  不能用 displayName、keywords 或 replyBody 推断事实覆盖。
- 新增事实的 coverage_keys **必须写在迁移的 INSERT 列里**，不能指望后台补录；
  同时要意识到运营后续在 UI 上编辑该规则时**可以**改掉它，因此依赖某个 coverage key 的不变量
  需要配一条"该规则 coverage_keys 未被清空"的验收项。
- `setRuleEnabled()` 仍不经正文/coverage 校验（见 [[K-qa-rule-enable-must-revalidate-facts]]）。

补充：启用 coverage 硬门禁前必须统计线上空 coverage 规则。若存量覆盖率不足，先对合同、IP、薪酬结构、
保密、费用等高风险 intent 强制，其他 legacy intent 暂兼容；完成全量语义回填后才能取消兼容，
禁止一次切换导致大量既有规则突然 MISSING。

关联：[[K-coverage-key-orphan-makes-fact-unreachable]]（非空但无人引用的键 = 事实永久不可达）、
[[K-qa-rule-runtime-vs-migration-writes]]（迁移不得覆盖运营运行时改动）。
