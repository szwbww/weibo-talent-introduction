---
id: K-raw-index-scoring-fields-sparse
domain: es-index
created: 2026-08-25
last_used: 2026-08-25
hit_count: 0
source: create-p:03-promotion-classification-gate
severity: P1
---

RAW 层文档只有**三个字段**能参与 `ExpertClassificationService` 打分，因此
**RAW 层的 `UNKNOWN` 含义是「还没取数」，不是「不合格」**。任何在 RAW 层
按分类结果做排除的门禁，都必须按这个语义设计。

## 证据

`ExpertDiscoveryService.kt:740-748` 构造入库 profile：
`keyword = null`、`employment = authorEmail.affiliation`、`institution = authorEmail.affiliation`
（两者同一个串）、`lastPublicationYear = paper.pubYear`。

`toIndexMap:752-767` 实际写入 ES 的键里，**没有** `researchFields`、`recentWorkTitles`、
`patentTitles`、`hIndex`、`worksCount`、`citationCount`。它们只由
`ExpertDiscoveryService.kt:1093-1096` 的 enrichment 写入，而 enrichment 只跑 CANDIDATE
（见 [[K-enrichment-excludes-email-id-experts]]）。

## 代入打分（阈值均为 50）

| affiliation 形态 | 生产分 | 科研分 | 类型 |
|---|---|---|---|
| `"Robert Bosch GmbH"` | 15（COMPANY_TERMS） | 35（RECENT_PUBLICATION） | **UNKNOWN** |
| `"Siemens Healthineers GmbH"` | 15 | 35 | **OUT_OF_SCOPE**（命中医学域无白名单） |
| `"Dept. of Mechanical Engineering, MIT"` | 0 | 35+20=55 | ACADEMIC_RND |
| `"Department of Cardiology, X Hospital"` | 0 | 35 | OUT_OF_SCOPE |

结论：在 RAW 层按 `sendable=true` 收紧，实际效果是**只留 affiliation 里带
university/institute/lab 的人**，把企业研发整类误杀。正确分层是
「晋升端宽档（只拒 SERVICE_ONLY / OUT_OF_SCOPE）+ 发信端严档（sendable=true）」。

关联：[[K-enrichment-excludes-email-id-experts]]、[[K-expert-classification-one-object-three-layers]]
