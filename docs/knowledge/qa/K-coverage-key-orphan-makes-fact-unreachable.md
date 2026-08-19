---
id: K-coverage-key-orphan-makes-fact-unreachable
domain: qa
created: 2026-08-19
last_used: 2026-08-19
hit_count: 0
source: create-p:01-fact-and-catalog
severity: P1
---

经验：`qa_rule.coverage_keys` 非空、但其中没有任何键被某条 intent 的 `requiredCoverageKeys` / `alternativeCoverageKeys` 引用时，该规则在 grounded 链路里**结构性不可达**——不是"命中率低"，是永远为 0。

链路：`isCoverageEligible`（`AiReplyIntentCatalog.kt:475-481`）对非空 coverage 要求与 intent 的 required+alternative 有交集，无交集对所有 intent 恒 `false` → `selectIntentKeyForRule`（:483-500）的 `scored` 为空且 `blankCoverage` 为 false → 返回 `null` → 规则被丢弃。补 alias、补 keyword 都救不回来。

2026-08-19 实测的孤儿键（脚本比对 `QaCoverageKeyCatalog` 全键 vs 全 intent 的 required+alternative）：
`company.verification_evidence`（`Agency credentials and government cooperation` 的**唯一**覆盖键，由 `V76:17-20` 赋值）、`application.required_materials`、`work.remote_arrangement`、`work.travel_arrangement`、`work.relocation`。
反向失配（intent 要、目录没有，`normalizeAndValidate` 会 require 失败，任何规则都存不进这两个键）：`work.time_commitment`、`work.advisory_duration`。

正确做法：新增 coverage key 与新增 intent 必须成对提交，并加一条**双向**守卫测试——intent 引用的键都合法，目录里的键都被引用（例外走显式豁免常量并注释指向延后计划，使后续删豁免时测试立刻变红）。

关联：[[K-qa-coverage-keys-management-write-boundary]]（coverage_keys 只能由迁移落库）、[[K-intent-keyword-two-sided-normalization]]。
