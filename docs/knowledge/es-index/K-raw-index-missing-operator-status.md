---
id: K-raw-index-missing-operator-status
domain: es-index
created: 2026-08-16
last_used: 2026-08-16
hit_count: 0
source: create-p:expert-reachability-02-classifier-and-mapping
severity: P2
---

`checkOperatorStatusMapping()` 断言三层皆有 `operatorStatus` 的 keyword mapping，
但 `orcid_info_raw.json` **从未声明该字段**，且该层 `dynamic: false`：

```bash
grep -n "fun checkOperatorStatusMapping" -A4 src/main/kotlin/.../ExpertIndexService.kt
```
```
170:    fun checkOperatorStatusMapping(): Boolean {
173:        for (level in listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)) {
```
```bash
for f in raw candidate application; do grep -n '"dynamic"\|"operatorStatus"' src/main/resources/es/orcid_info_$f.json; done
```
```
--- raw:         7:"dynamic": false,        （无 operatorStatus）
--- candidate:   7:"dynamic": false,   38:"operatorStatus": { "type": "keyword" },
--- application: 7:"dynamic": false,   48:"operatorStatus": { "type": "keyword" },
```

后果：在按仓库声明重建的纯净索引上，`/api/experts/backfill-operator-status`
会因该断言失败而返回 400。线上未暴露是因为 RAW 索引建于早期、settings 与仓库 JSON 漂移
（见团队记录 `es-mapping-live-vs-repo-drift`：实测线上 RAW 的 `dynamic` 键不存在，即默认 true）。

**通用规则（比这条具体缺陷更重要）**：
新增任何「MySQL 事实 → ES 冗余字段」的同步能力时，
**mapping 前置断言遍历的层级集合必须与实际写入的层级集合逐一相等**。
断言层级 ⊃ 写入层级 → 结构上永远无法通过；断言层级 ⊂ 写入层级 → 未断言层写入即静默失效。

关联：[[K-es-mapping-single-declaration-source]]、[[K-es-dynamic-false]]
