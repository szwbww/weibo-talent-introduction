---
id: K-openalex-institution-two-sources
domain: es-index
created: 2026-10-19
last_used: 2026-10-19
hit_count: 2
source: create-p:05a-institution-type-collection
severity: P1
---

本仓库的「专家机构」信息来自 OpenAlex 的**两条路径，指向两个不同的机构**。
任何基于机构做判定的需求（企业识别、医疗机构排除、地区归属）都必须先分清这一点。

## 两条路径

| | works 搜索路径 | authors 补全路径 |
|---|---|---|
| 取数点 | `OpenAlexDataSource.parseResponse` 的 `soleInstitution(authorship)` | 作者补全的 `last_known_institutions`（具体解析按当前实现复核） |
| 语义 | **该专家被发现的那篇论文上的署名机构** | **该作者当前已知机构** |
| 写入点 | `ExpertDiscoveryService.buildProfile/toIndexMap` → `institution`（employment=null） | `ExpertDiscoveryService.updateExpertAcademicFields` |
| 时机 | 发现入库时，一次 | 每次 enrichment |

**2026-10-19重新验证**：updateExpertAcademicFields不写institution、country，且明确禁止enrichment.institutionType覆盖原类型（约3254行）。因此旧描述“当前已将另一机构类型写回”不能再用作现状依据。不能把作者最新机构地址直接补给论文署名机构。works路径仅唯一机构对象可用于结构化字段，多机构保留未知；当前buildProfile不把署名复制为employment。

## 实证（2026-08-25）

`api.openalex.org/authors?filter=orcid:0000-0003-1613-5981`：
- `last_known_institutions` 条数 2，首项 `OpenAlex`（`type=nonprofit`, CA）
- `affiliations` 条数 12，其中含 `National Institutes of Health`（`type=government`, US, years=[2008]）

同一个人，两个机构，两个 type，两个国家。

## 机构对象结构（两条路径一致）

```json
{ "id": "...", "ror": "...", "display_name": "...", "country_code": "..", "type": "...", "lineage": [...] }
```

`type` 取值（2026-08-25历史works样本662条曾观测填充率100%，不能当作今天覆盖率）：
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
