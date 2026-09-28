# 01：新发现机构来源提取修复

## 需求描述

新论文发现只把同一作者的单一、明确机构写成 `institution`，其国家来自同一机构的结构化国家；论文署名不写为当前任职。OpenAlex 补全不再拿作者“最近机构”类型覆盖论文机构类型。不得改变邮箱—作者绑定规则、ORCID/SBIR 来源记录、非发现专家。范围外：历史 ES 修正、发送策略、猜测多机构的“主机构”。

## 关键不变量

### Invariant I-1: 作者—机构同源
- Rule: JATS 只接受该 `<contrib>` 直接 `<aff>` 或唯一 `xref rid` 指向的 `<aff>` 中，`content-type=university|edu` 的唯一非空 `<institution>` 作为组织名；其他 `content-type` 和无类型标签只保留在原始 `affiliation`，不得猜组织级含义。OpenAlex 只接受同一 `authorship` 中唯一非空 `institutions` 对象。多个不同机构、无上述结构节点、无法唯一绑定作者，一律不选第一家。
- Applies to: JatsXmlEmailParser、OpenAlexDataSource、SourceAuthorEmailResolver、ExpertDiscoveryService.buildProfile。
- Violation consequence: 选错作者或从多人署名块提取假机构。
- 来源: original；`JatsXmlEmailParser.kt:44-52`、`OpenAlexDataSource.kt:244-251`。

### Invariant I-2: 字段语义
- Rule: `affiliation` 保留现有原文作提取线索；内部 `institutionSource` 仅由实际成功的 JATS 或 OpenAlex 结构解析写 `JATS`/`OPENALEX`，不得由 `dataSource` 猜测。`institution` 只放该结构机构名，`country` 只放同一机构明确且能被 `CountryContinentMapping` 识别的国家/代码；无值则 null。`employment` 对论文发现设 null，不能按逗号末段、邮箱域名或机构地址猜国籍/现任职位。
- Applies to: AuthorEmail、PaperAuthor、JATS/OpenAlex 提取、ExpertDiscoveryService.buildProfile。
- Violation consequence: 地址、邮编、ROR/邮箱成为机构或国家。
- 来源: original；`ExpertDiscoveryService.kt:1918-1929,2922-2926`。

### Invariant I-3: 机构类型绑定
- Rule: `institutionType` 只能与所显示 `institution` 的同一个来源机构对象配对；作者 `last_known_institutions[0].type` 不得覆盖论文 `authorship.institutions` 的类型。类型未知则 null。
- Applies to: OpenAlexDataSource、ExpertDiscoveryService.updateExpertAcademicFields。
- Violation consequence: 大学名被标为 company。
- 来源: original；`OpenAlexDataSource.kt:535`、`ExpertDiscoveryService.kt:2669`。

## 现状审计

### 发现数据写入 RAW/候选 ES
- Schema/mapping: 三个 `src/main/resources/es/orcid_info_*.json` 已含 `institution` text、`country` keyword、`employment`、`institutionType` keyword；本计划不新增 ES 字段。
- Write paths: `JatsXmlEmailParser.parse` 把 `<aff>.textContent` 作为整段机构；`EuropePmcDataSource.parseAffiliation` 多段用 `; ` 合并；`OpenAlexDataSource.parseResponse` 取第一机构对象；`SourceAuthorEmailResolver.results` 将 PaperAuthor 字段传播到邮箱作者；`ExpertDiscoveryService.buildProfile/toIndexMap` 写 RAW，再由 `promoteDiscoveredToCandidate` 复制；`buildOrcidProfile` 独立写 ORCID；`updateExpertAcademicFields` 后续覆盖类型。`ExpertIndexWriterService` 晋升透传 `_source`，不需改。（来源: K-enrichment-write-three-layers、K-promotion-source-passthrough）
- Read paths: `ExpertSearchService.toExpertProfile`、`ExpertClassificationService`、`CandidateEligibilityService`、`MailVariableService`、发送查询读取这些现有字段。
- Interaction points: JATS/OpenAlex→AuthorEmail→ES→模板/分类；作者补全→机构类型→分类。`OpenAlexDataSource.extractAuthorEmails` 优先取 Europe PMC/JATS 全文，仅以 ORCID 唯一等值补 OpenAlex 作者 ID，当前不把 OpenAlex 机构接入这个分支；PDF/HTML 回退才经 `SourceAuthorEmailResolver` 使用 `PaperAuthor` 机构。新字段必须分别覆盖两条分支，跨来源机构只准在同一作者 ORCID/作者 ID 唯一等值且无冲突时使用。已存 1,081 篇 PMC XML 的 2,480 个 `<institution>` 中，2,262 个无 `content-type`，125 个标 `department`、48 个标 `dept`、25 个标 `university`、15 个标 `edu`、5 个标 `org-division`。`PMC13138287` 同文 `aff1` 为无类型的 `Mayo Clinic Alix School of Medicine`，`aff2` 为无类型的 `Department of Neurologic Surgery`；两者标签相同，代码无法仅凭标签证明后者是组织。因此仅准已观察到的显式 `university|edu` 组织类型；其余暂空，不用名称词典猜测。

## 实现方案

1. 在 `PaperAuthor`/`AuthorEmail` 增加内部可空 `institutionName`、`institutionCountry`、`institutionSource`，保留原 `affiliation`。JATS 只读取同一作者关联 `<aff>` 中唯一 `content-type=university|edu` 的 `<institution>` 文本，并标记来源 `JATS`；同一 `<aff>` 唯一 `<country>` 且地区表可识别才取国家。脚注、`<institution-id>`、`<email>`、`<ext-link>`、无类型/院系类型 `<institution>` 均不参与展示值；缺结构则 null，不升级为全文正则。用 `PMC13138287` 两个真实 `aff` 作反例测试。（I-1、I-2）
2. OpenAlex 从同一 `authorship` 唯一机构对象读取 `display_name/type/country_code` 并标记来源 `OPENALEX`；国家仅在实际响应存在且地区表可识别时写入。多机构不任取第一项。PDF/HTML 分支的 `SourceAuthorEmailResolver` 只有唯一邮箱作者证据时传播字段；PMC/JATS 分支以 JATS 机构为准，不能仅凭相似姓名拿 OpenAlex 机构覆盖，只有已存在的唯一 ORCID 作者绑定且 JATS 未取得机构时才可取该同一作者的 OpenAlex 单机构。无绑定或两源矛盾时保持 null。（I-1、I-2、I-3）
3. `ExpertDiscoveryService.buildProfile` 改用新字段，论文 `employment=null`，删用 `inferCountryFromAffiliation`；`updateExpertAcademicFields` 不再写异源 `institutionType`。ORCID 路径维持来源 `institution-name`，但不把它声称为已验证当前任职；原有邮箱身份门禁不变。（I-1、I-2、I-3）

## 变更文件清单

| 文件 | 改动 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/AuthorEmail.kt` | 内部来源字段 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperAuthor.kt` | 内部来源字段 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParser.kt` | JATS 精确提取 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | 同一 authorship 唯一机构 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` | 强绑定字段传播 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 建档与异源覆盖修正 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/JatsXmlEmailParserTest.kt` | JATS 反例 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 多机构/类型/国家 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` | 作者绑定传播 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | ES 写值/补全不覆盖 |

## 验收标准

- I-1：JATS 同作者唯一 `content-type=university|edu` 标签和 OpenAlex 唯一机构得到明确名；无类型/只有院系标签、多人引用、多个不同机构均 null；PMC/JATS→OpenAlex 仅唯一 ORCID 绑定才补机构，冲突不补；现有身份测试通过。
- I-2：含脚注、ROR/GRID URL、邮箱、邮编、换行、整段院系地址的测试不进入 `institution`/`country`；有效结构的内部 `institutionSource` 是真实分支，缺机构时为 null；论文 `employment=null`；国家不在映射表则 null。
- I-3：同一 authorship 类型与机构配对；后续 `last_known_institutions.type=company` 不覆盖已显示大学机构的类型。针对旧测试中原先“覆盖”的断言同步改为“不覆盖”。
- 集成：写出的 RAW 与晋升候选字段一致；分类仍由最终机构重新计算；ORCID/SBIR 与非发现样本回归通过。

## 人工验收清单

### A-1: 结构化单机构
- 前置条件: 测试源准备一篇作者邮箱唯一对应且 `<aff><institution content-type="university">Seoul National University</institution><country>Republic of Korea</country>` 的 JATS 论文。
- 操作步骤: 运行发现并打开 RAW 与候选详情。
- 预期结果: 两层 `institution=Seoul National University`、`country=Republic of Korea`、`employment` 为空。
- 覆盖: I-1、I-2、JATS→ES→详情。

### A-2: 异常与歧义
- 前置条件: 测试源准备含脚注/ROR/邮箱但无机构节点的 JATS 样本，以及有两个不同 `institutions` 的 OpenAlex authorship。
- 操作步骤: 运行发现并打开详情。
- 预期结果: 两名专家的机构和国家均为空；页面不显示邮箱、ROR URL 或任选的第一家机构。
- 覆盖: I-1、I-2。

### A-3: 类型与来源回归
- 前置条件: OpenAlex 论文机构为大学、作者最近机构类型为 company；另备一名 ORCID 和一名非新发现专家。
- 操作步骤: 运行发现和补全，检查上述三人的详情。
- 预期结果: 大学机构类型维持论文同对象值；ORCID 机构来源值保留；非新发现资料值不变。
- 覆盖: I-3、不得改变来源/非发现行为。
