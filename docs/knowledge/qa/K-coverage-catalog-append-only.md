---
id: K-coverage-catalog-append-only
domain: qa
created: 2026-08-26
last_used: 2026-08-26
hit_count: 0
source: create-p:02-unrecognized-asks-and-orphan-keys
severity: P1
---

# `QaCoverageKeyCatalog` 的新 Entry 只能追加在列表末尾

`normalizeAndValidate`（`QaCoverageKeyCatalog.kt:118-134`）的返回值是
`all().map { it.key }.filter { it in trimmed }`——**按目录声明顺序**，不是按输入顺序。

因此在 `catalog` 的 `listOf(...)` 中插一条 Entry，会让既有规则**下次保存时**
`coverage_keys` 字符串的顺序改变，进而让一切按字符串逐字比对的迁移守卫失配，
例如 `V107__strip_controlled_keys_from_program_overview.sql:10` 的
`AND coverage_keys = 'programme.purpose,...,work.relocation,fees.policy,confidentiality.materials'`。

`QaCoverageKeyCatalog.kt:105-107` 已有 V105 P1 留下的同款注释，追加新键时照抄该注释风格。

关联：[[K-qa-coverage-keys-management-write-boundary]]、[[K-coverage-key-orphan-makes-fact-unreachable]]
