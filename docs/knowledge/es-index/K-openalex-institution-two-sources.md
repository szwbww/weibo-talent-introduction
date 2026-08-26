---
id: K-openalex-institution-two-sources
domain: es-index
created: 2026-08-25
last_used: 2026-08-25
hit_count: 0
source: create-p:05a-institution-type-collection
severity: P1
---

本仓库的「专家机构」信息来自 OpenAlex 的**两条路径，指向两个不同的机构**。
任何基于机构做判定的需求（企业识别、医疗机构排除、地区归属）都必须先分清这一点。

## 两条路径

| | works 搜索路径 | authors 补全路径 |
|---|---|---|
| 取数点 | `OpenAlexDataSource.parseResponse:92` `authorship.institutions[0]` | `parseAuthorEnrichmentFromNode:276` `node.last_known_institutions[0]` |
| 语义 | **该专家被发现的那篇论文上的署名机构** | **该作者当前已知机构** |
| 写入点 | `ExpertDiscoveryService.toIndexMap:752-767` → `institution` / `employment` | `updateExpertAcademicFields:1085-1096` |
| 时机 | 发现入库时，一次 | 每次 enrichment |

**关键：`updateExpertAcademicFields` 写的 10 个字段里没有 `institution`**
（实测：hIndex / citationCount / updatedAt / enrichedAt / enrichmentSource / worksCount /
researchFields / recentWorkTitles / patentTitles / disciplineCategory）。
所以**机构名永远停留在发现时的论文署名机构**，而任何由 enrichment 写入的机构衍生字段
反映的是"当前机构"。两者可能完全无关。

## 实证（2026-08-25）

`api.openalex.org/authors?filter=orcid:0000-0003-1613-5981`：
- `last_known_institutions` 条数 2，首项 `OpenAlex`（`type=nonprofit`, CA）
- `affiliations` 条数 12，其中含 `National Institutes of Health`（`type=government`, US, years=[2008]）

同一个人，两个机构，两个 type，两个国家。

## 机构对象结构（两条路径一致）

```json
{ "id": "...", "ror": "...", "display_name": "...", "country_code": "..", "type": "...", "lineage": [...] }
```

`type` 取值（works 端点实测 662 条目填充率 **100%**）：
`education` / `company` / `healthcare` / `facility` / `nonprofit` / `government` / `other` / `archive` / `funder`。

## 三条使用纪律

1. **不要假设机构名与机构衍生字段同源**——写规则前先确认这个字段是哪条路径写的。
2. **不要用 `affiliations[]` 代替 `last_known_institutions`**——前者是带 `years` 的历史列表，
   选取需要额外的"取哪一年"判断，不确定且难审计。
3. **不要为了消除差异而在 enrichment 里补写 `institution`**——那会覆盖发现时的论文署名机构，
   改变既有字段语义，波及邮件模板的 `${institution}` 占位符
   （见 [[K-mail-placeholder-labels-are-semantic-contracts]]）。

两条路径均**不带 `select=`**，返回完整对象，扩展机构衍生字段无需新增 API 请求
（见 [[K-openalex-author-full-object]]）。
