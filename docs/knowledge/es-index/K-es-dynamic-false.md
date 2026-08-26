---
id: K-es-dynamic-false
domain: es-index
created: 2026-08-13
last_used: 2026-08-25
hit_count: 9
source: create-p:expert-enrichment-backend
last_source: create-p:batch-send-status-consistency (03 P-B re-validated)
---

**只有 APPLICATION 层是 `dynamic: false`；CANDIDATE / RAW 是 ES 默认 `dynamic: true`。**（2026-08-13 实测证伪了旧版「三层均为 false」的假设，就地修正。）

实测命令与输出（ES 生产环境）：

```
GET /orcid_info_candidate/_mapping?filter_path=**.dynamic   → {}
GET /orcid_info/_mapping?filter_path=**.dynamic             → {}
GET /orcid_info_application/_mapping?filter_path=**.dynamic → {"orcid_info_application":{"mappings":{"dynamic":"false"}}}
```

APPLICATION 有返回值证明查询语法有效 → 前两者的空结果表示键确实不存在 → ES 默认 true。

推论（按索引区分，不得一概而论）：

- **APPLICATION**（`dynamic: false`）：新增字段必须在 `orcid_info_application.json` 显式声明后才可搜索/聚合；
  且对既有索引 `PUT _mapping` 声明新字段后，存量文档还需 `_update_by_query` 重建倒排（见
  `K-es-mapping-single-declaration-source` 与 `docs/runbooks/es-mapping-reindex.md`）。
- **CANDIDATE / RAW**（`dynamic: true`）：未声明字段会动态映射进索引（线上 CANDIDATE 的
  `_class`/`beDeleted`/`countryZh` 等 10 个野字段即铁证），`dynamic:true` 下不可能出现那些字段——
  故「补 JSON 声明」不是 CANDIDATE 可查询的必要条件，但 JSON 声明仍是**类型对齐**的唯一权威
  （动态模板产物与显式声明可能不同，如 `givenNames`/`familyNames`/`employment`/`keyword` 曾为
  `text` vs 线上 `keyword`）。
- 独立业务索引不适用「三层同改」：它必须使用独立 index-name 配置和自己的显式 mapping，
  并证明不进入 `ExpertIndexLevel`、三层 writer/search/promotion 路径。新索引可按自身合同选择更严格的 `dynamic: strict`。
