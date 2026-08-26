---
id: K-coverage-key-orphan-makes-fact-unreachable
domain: qa
created: 2026-08-26
last_used: 2026-08-26
hit_count: 2
source: create-p:01-fact-and-catalog
last_source: create-p:02-unrecognized-asks-and-orphan-keys
severity: P1
---

经验：`qa_rule.coverage_keys` 非空、但其中没有任何键被某条 intent 的 `requiredCoverageKeys` / `alternativeCoverageKeys` 引用时，该规则在 grounded 链路里**结构性不可达**——不是"命中率低"，是永远为 0。

链路：`isCoverageEligible`（`AiReplyIntentCatalog.kt:654-661`）对非空 coverage 要求与 intent 的 required+alternative 有交集，无交集对所有 intent 恒 `false` → `selectIntentKeyForRule`（`:663-684`）的 `scored` 为空且 `blankCoverage` 为 false → 返回 `null` → 规则被丢弃。补 alias、补 keyword 都救不回来。

## 2026-08-26 重测（main @f293507）——2026-08-19 的名单已过时，勿再引用

脚本：`QaCoverageKeyCatalog.kt` 的全部 `Entry("<key>"` vs `AiReplyIntentCatalog.kt` 的全部
`(required|alternative)CoverageKeys = listOf(...)` 字面量，双向差集。

```
catalog keys: 31 / intent-referenced keys: 30
ORPHANS: general.answer, application.required_materials, work.relocation
REVERSE MISMATCH: work.time_commitment, work.advisory_duration
```

与 2026-08-19 记录的差异：`company.verification_evidence`（今由 `AiReplyIntentCatalog.kt:329` 的
alternative 引用）、`work.remote_arrangement`、`work.travel_arrangement`（今由 `:339` 的 alternative 引用）
**已不再是孤儿**。**任何计划都必须重跑该脚本，不得直接引用本条的名单。**

三个孤儿的定性：
- `general.answer`（`QaCoverageKeyCatalog.kt:65`）——**设计如此**，是零具名意图命中时合成的兜底 intent 的 key
  （`AiReplyIntentCatalog.kt:406-414`，`requiredCoverageKeys = emptyList()`），
  `selectIntentKeyForRule:679` 有专门的 `blankCoverage && "general.answer" in intentKeys` 分支。
- `application.required_materials`（`:92`）——**真缺陷**。`V76__add_qa_rule_coverage_keys.sql:57-60`
  把它作为**唯一**覆盖键赋给 `Getting started materials`，该规则因此结构性不可达。
- `work.relocation`（`:98`）——无实害。`V76:77-80` 把它与 `work.travel_arrangement` 一起赋给
  `Workplace arrangement`，后者已被引用，故规则仍可达。

正确做法：新增 coverage key 与新增 intent 必须成对提交，并加一条**双向**守卫测试——intent 引用的键都合法，目录里的键都被引用（例外走显式豁免常量并注释理由，使后续删豁免时测试立刻变红）。

关联：[[K-qa-coverage-keys-management-write-boundary]]、[[K-intent-keyword-two-sided-normalization]]、[[K-coverage-catalog-append-only]]
