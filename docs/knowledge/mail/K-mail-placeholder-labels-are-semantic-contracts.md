---
id: K-mail-placeholder-labels-are-semantic-contracts
domain: mail
created: 2026-08-25
last_used: 2026-08-25
hit_count: 0
source: create-p:00-rnd-gate-master
severity: P1
---

`MailPlaceholderService.VARIABLE_LABELS`（`:118-138`）里的中文标签是**对外承诺**，
不只是 UI 文案。往对应 ES 字段写入语义不符的数据 = 对真实收件人说假话。

`ES_FIELD_BY_KEY`（`:141-161`）给出占位符 → ES 字段的映射，
`MailVariableService.kt:130-150` 把它们注入邮件正文。关键的语义绑定：

| 占位符 | 标签（`:118-138`） | ES 字段 |
|---|---|---|
| `recentWorkTitle` | **近期论文标题** | `recentWorkTitles` |
| `patentTitle` | 专利标题 | `patentTitles` |
| `lastPublicationYear` | **最近发表年份** | `lastPublicationYear` |
| `employment` | **职位** | `employment` |
| `institution` | 所属机构 | `institution` |
| `keyword` | 关键词 | `keyword` |
| `worksCount` | 论文数 | `worksCount` |

## 直接后果

新增数据源时，不能为了"凑够分类分数"把非论文数据塞进这些字段。
典型反例（已在 2026-08-25 的 SBIR 评估中被否决）：把政府资助 award 的标题写进
`recentWorkTitles`，介绍邮件就会对收件人声称一篇**不存在的论文**；
把 award year 写进 `lastPublicationYear`，邮件里的"最近发表年份"同样是假的。

这是 [[intro-mail-fallback-renders-as-title]] 那类缺陷的同源风险：
兜底值/借用字段一路直达对外发信，且没有任何门禁会拦。

新增字段承载新语义时，正确做法是新增 ES 字段 + 新增占位符 + 新增标签，
而不是复用一个标签已经写死的旧字段。
