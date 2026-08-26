---
id: K-openalex-field-domain-ids
domain: es-index
created: 2026-08-25
last_used: 2026-08-25
hit_count: 0
source: create-p:04-discovery-subject-scope
---

OpenAlex 学科过滤的实测参数（2026-08-25 取数，来源 `api.openalex.org/domains` 与 `/fields`）。
**不要凭 ASJC 印象猜 id**，以本表为准；如需扩充先重跑一次 `/fields`。

## domain（4 个）

| id | display_name |
|---|---|
| `1` | Life Sciences |
| `2` | Social Sciences |
| `3` | Physical Sciences |
| `4` | Health Sciences |

## 工程/理工目标 field（全部隶属 domain `3` Physical Sciences）

| id | display_name |
|---|---|
| `15` | Chemical Engineering |
| `17` | Computer Science |
| `21` | Energy |
| `22` | Engineering |
| `25` | Materials Science |
| `31` | Physics and Astronomy |

## 语法与量级（均实测通过）

- 正向多值锁定：`primary_topic.field.id:22|31|17|25|21|15` —— `|` 语法可用，
  2024 单年 + `is_oa:true` 下 count = 1,473,809。
- 反向排除：`primary_topic.domain.id:!4` 可用，同条件 count = 5,263,473。
- 与既有查询叠加（`is_oa:true,publication_year:2020-2026,authorships.institutions.country_code:!CN` + 六 field）
  count = 8,972,684。

## 两条推论

1. **正向锁定六 field 已隐含排除 Health Sciences**（field 与 domain 是一对多的从属关系，
   六者全在 domain `3`）。再叠 `domain.id:!4` 是冗余条件，只会拉长查询串。
2. **反过来说，锁定这六个 field 也排除了 Life Sciences 与 Health Sciences 的全部内容**——
   包括 Pharmacology、Biochemistry、Immunology 这些**制药研发的主阵地**。
   若业务需求含「制药 / 生物医药研发」，六 field 方案会静默漏掉它们，
   而分类器里的 `PHARMA_WHITELIST_TERMS` 也就永远不会被触发（论文根本没抓进来）。
   做学科范围需求时必须显式确认这一点。

**请求务必带 `mailto=`**（仓库既有配置 `application.yml:165` 的
`OPENALEX_POLITE_EMAIL`），否则极易 429；把 429 误记为「数据为空」会得出完全相反的结论。
