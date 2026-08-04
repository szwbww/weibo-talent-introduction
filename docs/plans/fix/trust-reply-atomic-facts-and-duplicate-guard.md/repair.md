# Repair Plan: 可信回复原子事实与重复 Claim 防线开发计划

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-08-04/trust-reply-atomic-facts-and-duplicate-guard.md`
Verification report: `review-p current review, 2026-08-04`
Implementation boundary: current working-tree diff against `c154b54`

## Objective

受控原子 coverage 只能写入对应 V82 规范正文；所有非空 coverage 都只能支撑相交 intent，同时保留 legacy blank-coverage 兼容路径。

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-2 | P1 | I-1/I-5：四个受控 coverage 必须精确绑定规范 `answerBody`；create、update 与 enable 在写前复验且拒绝不保存。 | `QaCoverageKeyCatalog` 没有受控集合→规范正文校验；`QaRuleManagementService` 只做通用正文校验，`setRuleEnabled` 直接保存。 |
| V-3 | P1 | I-2：任一非空 `coverage_keys` 必须与当前 intent 的 required/alternative coverage 相交；仅 legacy blank coverage 保留兼容。 | `isCoverageEligible` 对所有非高风险 intent 直接返回 true，忽略非空但不相交的 coverage。 |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | 已解决：catalog 已登记 `finance.compensation_structure`、`publication.authorship`、`confidentiality.research`。 |
| V-4 | P2：计划清单外的知识文档/修复目录改动不影响运行时；未获单独清理授权。 |
| A-1～A-7 | 人工验收，仍为 PENDING；不属于本次代码修复。 |

## Unchanged Contract

- 不改 `QaRequestExtractor`、request→intent→factRuleIds→groundingStatus 矩阵、关键词候选召回、composer 逐字拼接、版本/claim 重物化或发送权限。
- legacy intent 的 blank coverage 继续兼容；不得用正文、displayName、LLM 或模糊匹配推断 coverage。
- 不改 V82、历史迁移、前端 UI、自动删除或模糊去重行为。

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt` | 定义四组受控 coverage 与其 V82 规范正文，并提供确定性校验。 |
| `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt` | 在 create/update/enable 的 repository save 前执行受控正文复验。 |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` | 对任意非空 coverage 实施 intent 相交过滤，blank 仅按高风险集合拒绝。 |
| `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt` | 证明受控正文错配/混合 coverage/错误 enable 均拒绝且不保存。 |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | 证明非高风险 intent 的非空错配 coverage 不得成为 evidence，legacy blank 仍兼容。 |

## Repair Tasks

### R-1: 受控 coverage 原子正文门禁

- Resolves: V-2
- Root cause: 管理写路径只有通用正文和 coverage key 校验，缺少受控 coverage→规范正文映射与 enable 复验。
- Files: `QaCoverageKeyCatalog.kt`, `QaRuleManagementService.kt`, `QaRuleManagementServiceTest.kt`
- Change: 为 `[confidentiality.materials]`、`[fees.policy]`、`[contract.party,contract.terms]`、`[ip.arrangements]` 定义 V82 完整规范正文映射。显式写入任一受控 key 时，coverage 必须恰为一组且正文完全一致；已存 coverage 恰为一组的 update（含 `coverageKeys=null`）及 enable 也须复验。任何失败发生在 `repository.save` 前。
- Regression test: 覆盖 create/update/null/enable 的规范正文接受、正文错配、混合受控 coverage 及错误已存规则 enable；每个拒绝断言 `save` 未调用。保留非受控 Program overview 行为。
- Existing verification: `QaRuleManagementServiceTest`，随后完整回归。
- Must not change: coverage null/empty 序列化语义、`answerBody == replyBody`、variants/category/reply-policy 校验。
- Prohibited: 对非受控 legacy coverage 做正文分类、回写或拒绝；修改 V82 或 UI。

### R-2: 非空 coverage 的全 intent 授权过滤

- Resolves: V-3
- Root cause: coverage eligibility 将“高风险 blank 必拒绝”错误实现成“仅高风险检查任何 coverage”。
- Files: `AiReplyIntentCatalog.kt`, `QaFactSelectionServiceTest.kt`
- Change: 若 rule 的 stored coverage 非空，先要求与当前 intent 的 required/alternative coverage 相交；若为空，仅 `COVERAGE_REQUIRED_INTENT_KEYS` 中 intent 拒绝，其余 legacy intent 保持既有分配。
- Regression test: keyword 命中的非高风险 intent + 非空错配 coverage 为 MISSING；同一 legacy intent + blank coverage 维持既有 evidence；高风险的 blank/错配继续 MISSING。
- Existing verification: `QaFactSelectionServiceTest`，随后完整回归。
- Must not change: alias 集合、唯一 intent score、request 顺序及 `factRuleIds`/`sendQaRuleIds` 派生顺序。
- Prohibited: 基于正文或名称猜测 coverage；扩大 blank coverage 的拒绝范围；改动迁移或 composer。

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -Dtest=QaRuleManagementServiceTest,QaFactSelectionServiceTest test`
2. `git diff --check`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`

## Completion Criteria

- 四组受控 coverage 只有对应完整 V82 正文可写入或启用；每个拒绝路径均无 repository save。
- 任一非空 coverage 仅支撑相交 intent；high-risk blank coverage 仍为 MISSING，legacy blank coverage 保持原行为。
- 聚焦测试、`git diff --check` 与完整回归通过；产品改动仅在 Authorized Files 内。

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
