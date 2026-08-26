# 子计划 05A：采集 OpenAlex 机构类型（institutionType）

> **本计划只采集字段，不改变任何行为。** 分类规则改动、`VERSION` 升版与两阶段迁移在
> 05B（尚未编写），必须等本计划上线并观察真实数据分布之后再定稿评分数值。
>
> **为什么拆两份**：05A 零行为变化、无版本迁移，可安全上线；05B 要动分类器与发信门禁，
> 代价大且其评分数值应当由 05A 产出的真实分布来定，而不是由一份 50 篇论文的抽样来定。

## 需求描述

可观察结果：专家文档新增 `institutionType` 字段，取值为 OpenAlex 的机构类型
（`education` / `company` / `healthcare` / `facility` / `nonprofit` / `government` / `other` / `archive` / `funder`）。
新发现的专家在入库时写入（取论文署名机构的类型）；存量专家在学术数据补全（OpenAlex enrichment）时写入（取作者当前已知机构的类型）。**两者可能是不同机构，见 I5a-7。**
该字段可被 `ExpertProfile` 读取，为 05B 的分类规则改造提供数据基础。

必须保持不变：

- **分类结果零变化**。`ExpertClassificationService` 一行不改，`VERSION` 保持 `rnd-v2-2026`，
  `sourceFingerprint` 的输入集合不变（本字段**不进**分类器）。
- **发信行为零变化**。`expertSendableFilter`（`ExpertSearchService.kt:55-63`）与
  `RecipientScope.matchesExpert`（`BatchExecutionModels.kt:62-121`）一行不改。
- 既有 `institution`（机构名文本）字段的值、类型、写入时机不变；本计划**新增并列字段**，不替换它。
- 邮箱抽取链路、作者去重、入库资格判定、晋升链路零改动。
- `AuthorEnrichment` 既有 7 个字段的名称与语义不变。

范围外：

- 修改 `ExpertClassificationService` 的任何词表、分数、阈值、`VERSION`（属 05B）。
- 修改 `expertSendableFilter` 或 `matchesExpert` 的版本判定（属 05B）。
- 前端展示与筛选 `institutionType`（属子计划 01 的 `ExpertContactView`，加一个字段即可，本计划不动前端）。
- 用 `institutionType` 替换或废弃 `COMPANY_TERMS` / `MEDICAL_DOMAIN_TERMS` / `RESEARCH_INSTITUTION_TERMS`（属 05B）。
- 对存量 11.6 万 CANDIDATE 做一次性回填（本计划只让 enrichment 链路顺带写入；是否单独回填由 05B 决定）。
- 采集 `institution.ror` / `country_code` / `lineage` 等其他机构字段（一次一个字段，见范围检查）。

## 关键不变量

### Invariant I5a-1: 只采集，不消费
- Rule: `institutionType` 在本计划中**只有写入方和一个读出点**（`ExpertProfile`）。禁止任何代码依据它做判定——不得进入 `ExpertClassificationService`、不得进入任何 ES filter、不得影响资格判定或晋升。
- Applies to: 全部变更文件。
- Violation consequence: 一旦进入分类器，`sourceFingerprint` 输入集合改变，按 2026-08-24 计划 01 的不变量 I1-4 必须升 `VERSION`；而 `expertSendableFilter`（`ExpertSearchService.kt:60`）校验 `version == VERSION`，升版当天存量分类全部失效、可发送池归零。这是本计划刻意规避的代价。
- 来源: original

### Invariant I5a-2: 单值，取数组第一项
- Rule: OpenAlex 的机构在两条路径下都是**数组**，一名作者可能挂多个机构。本字段一律取**第一项**的 `type`。禁止取 union、禁止拼接、禁止另立优先级规则（如"选 years 最大的"）。
- Applies to: `OpenAlexDataSource` 的两处解析点。
  - works 路径：`authorship.path("institutions").firstOrNull()` —— 与既有 `affiliation` 取自**同一个**机构对象（`OpenAlexDataSource.kt:92-95`）。
  - authors 路径：`last_known_institutions[0]` —— 见 I5a-7，与 `institution` **不同源**。
- Violation consequence: 取法不确定 → 同一份数据重跑得到不同结果，无法审计。
- 来源: original

### Invariant I5a-7: 两条路径写的是**不同机构**的类型，不得假设同源
- Rule: `institutionType` 的语义随写入路径而不同，**必须**在 `ExpertProfile.institutionType` 的属性注释中逐字写明这一点：
  - **works 路径（发现时）**：该专家被发现的那篇论文上的**署名机构**的类型，与同时写入的 `institution` / `employment` **同源**。
  - **authors 路径（enrichment 时）**：该作者的**当前已知机构**（`last_known_institutions[0]`）的类型，**与 `institution` 很可能不是同一个机构** —— 因为 `updateExpertAcademicFields`（`ExpertDiscoveryService.kt:1085-1096`）写入的 10 个字段中**不含 `institution`**，机构名永远停留在发现时的论文署名机构。
  - 实证（2026-08-25，`api.openalex.org/authors?filter=orcid:0000-0003-1613-5981`）：该作者 `last_known_institutions[0]` 为 `OpenAlex`（`type=nonprofit`），而 `affiliations` 中含 `National Institutes of Health`（`type=government`，years=[2008]）。同一人，两个机构，两个类型。
- Applies to: `ExpertProfile.kt` 的属性注释、`OpenAlexDataSource` 两处解析点、05B 的规则设计。
- Violation consequence: 05B 若假设"机构名与类型同源"，会写出诸如"institution 含 university 且 type=company 则如何"的组合规则，而这两个信号根本来自不同机构，规则语义不成立。
- **不得**为消除该差异而在 `updateExpertAcademicFields` 中一并写入 `institution` —— 那会覆盖发现时的论文署名机构，改变既有字段语义，属"必须保持不变"第 3 条。
- 来源: original（2026-08-25 实测发现）

### Invariant I5a-8: enrichment 的值覆盖发现时的值
- Rule: 两条路径都可能写入同一文档的 `institutionType`。约定：**enrichment（authors 路径）的值覆盖发现（works 路径）的值**，因为它反映作者的当前机构而非历史某篇论文的署名。实现上这是自然结果（enrichment 在发现之后运行，`_update` + `doc` 局部覆盖），本不变量的作用是**禁止**执行方为"保护首次写入"而加任何条件判断（如 `if (doc has no institutionType)`）。
- Applies to: `ExpertDiscoveryService.updateExpertAcademicFields`。
- Violation consequence: 加了保护逻辑，则专家跳槽后类型永远停在旧机构；且两条路径的优先级变成隐式，无法解释。
- 来源: original

### Invariant I5a-3: 空值即缺失，不造默认值
- Rule: OpenAlex 未返回 `type`、机构数组为空、或字段为空串时，`institutionType` 一律为 `null`，**不写入该键**。禁止写 `"unknown"` / `""` / `"other"` 之类的占位值。
- Applies to: `ExpertDiscoveryService.toIndexMap`、`updateExpertAcademicFields`、`toExpertProfile`。
- Violation consequence: 05B 无法区分「没取到」与「OpenAlex 判定为 other」，且占位值会污染将来的 terms 聚合。
- 来源: original

### Invariant I5a-4: ES 字段声明四处同步
- Rule: 新增 ES 字段必须同步四处，缺一则字段存在但不可用：① 三份 mapping JSON ② `ExpertProfile.kt` 属性 ③ `ExpertSearchService.sourceFields()` ④ `ExpertSearchService.toExpertProfile()`。
- Applies to: `orcid_info_{raw,candidate,application}.json`、`ExpertProfile.kt`、`ExpertSearchService.kt`。
- Violation consequence: ES 写入成功但读不到，或读到 null，缺陷隐蔽无报错。
- 来源: K-expert-profile-source-sync

### Invariant I5a-5: mapping 类型为 keyword，且新字段不追溯存量
- Rule: 三份 mapping 中 `institutionType` 一律声明为 `{ "type": "keyword" }`（枚举值，需精确匹配与聚合，不分词）。三份声明**逐字相同**。
- Applies to: 三份 mapping JSON。
- Violation consequence: 声明为 `text` 则无法做 `term` 过滤与 `terms` 聚合，05B 的筛选与统计全部失效。
- 附加约束: `PUT _mapping` 新增字段**不会**让存量文档 `_source` 里的旧值进入倒排索引；本字段是全新字段，存量文档本就没有值，因此**无需** `_update_by_query`。但执行方不得据此推广到其他字段。
- 来源: K-es-mapping-single-declaration-source

### Invariant I5a-6: 尾部可空默认参数
- Rule: `ExpertProfile` 与 `AuthorEnrichment` 的新字段必须加在**参数列表末尾**且带默认值 `null`。
- Applies to: `ExpertProfile.kt`、`OpenAlexDataSource.AuthorEnrichment`。
- Violation consequence: `ExpertProfile` 在仓库中有大量位置参数构造点（含未授权的测试文件）；插入中间位置会全线编译失败。`AuthorEnrichment` 的 `baseEnrichment` 等既有构造点同理。
- 来源: K-openalex-author-full-object

## 现状审计

### 三层 ES 索引
- Schema/mapping: `src/main/resources/es/orcid_info_raw.json`、`orcid_info_candidate.json`、`orcid_info_application.json`，均 `dynamic:false`（来源: K-es-dynamic-false，本轮复核成立）。既有机构字段声明为 `"institution": { "type": "text" }`（`orcid_info_candidate.json:27`）——**是 `text` 不是 `keyword`**，因此它本身不能做 term 过滤，这也是不能靠它区分机构类型的额外理由。
- Mapping 推送: `ExpertIndexService.bootstrapMappings:37` → `updateMappingIfNeeded:75` 整批 `PUT _mapping`；4xx 时降级为 `pushFieldsIndividually:117` 逐字段推送。`loadMappingProperties:149` 从 JSON 读取，**Kotlin 侧无字段白名单**（历史上的 `phase5NewFields` 白名单已移除，来源: K-es-mapping-single-declaration-source）。
- Write paths（全部，grep 复核）:
  1. `ExpertDiscoveryService.toIndexMap:752-767` —— 新发现专家写 RAW 的字段全集。**本计划在此新增一个键。**
  2. `ExpertDiscoveryService.updateExpertAcademicFields:1085-1110` —— enrichment 的三层局部更新（`:1098-1107` 循环 RAW/CANDIDATE/APPLICATION，`_update` + `doc`）。**本计划在此新增一个键。**
  3. `ExpertDiscoveryService.promoteDiscoveredToCandidate:770-786` —— 发现时直接晋升，`rawDoc.toMutableMap()` 全量复制，新字段自动透传，**零改动**。
  4. `ExpertIndexWriterService.promoteToCandidate` / `promoteToApplication` —— `_source` 全量逐字段复制，新字段自动透传，**零改动**（来源: K-promotion-source-passthrough，本轮复核成立）。
  5. `ExpertRevalidationService.promoteRawToCandidate:241-261` —— `rawDoc.toMutableMap()` 复制，自动透传，**零改动**。
  6. `ExpertIndexWriterService.bulkUpdateExpertClassifications` —— 只写 `expertClassification`，与本字段无关。
  7. `ExpertIndexWriterService.syncOperatorStatus` / `addTag` / `removeTag` 等局部更新 —— 只写各自字段，与本字段无关。
- Read paths:
  1. `ExpertSearchService.sourceFields():585-596` —— `_source` 白名单，当前 **不含** `institutionType`。**本计划新增。**
  2. `ExpertSearchService.toExpertProfile():~484-510` —— 反序列化，`institution` 在 `:488`。**本计划在其后新增一行。**
- Interaction points:
  - 写路径 1（新发现）× 读路径 1+2：新专家入库即带类型。
  - 写路径 2（enrichment）× 读路径 1+2：**存量专家补齐的唯一通道**。注意 `buildEnrichmentFilters:800-826` 带 `must_not: prefix orcidId "EMAIL-"`（`:820-822`），因此**无 ORCID 的专家永远补不到本字段**（来源: K-enrichment-excludes-email-id-experts）。这批人只能靠写路径 1（若其发现时的 works 响应带了 type）。
  - 写路径 3/4/5（晋升透传）× 读路径：字段随文档在三层间流动，无需额外处理，但需验收确认未丢失。

### OpenAlex 数据源
- 两条解析路径，**结构不同，必须分别处理**:
  1. **works 搜索路径** —— `OpenAlexDataSource.parseResponse:88-96`，`:92` 取 `authorship.path("institutions").firstOrNull()`，`:95` 只取了 `display_name` 作为 `affiliation`。产出 `PaperAuthor`，最终经 `ExpertDiscoveryService.kt:744` 写入 `employment` 与 `institution`。
  2. **authors 补全路径** —— `parseAuthorEnrichmentFromNode:276-294`，被单专家路径（`:286` 一带，`fetchWorksAndPatents=true`）与批量路径（`batchEnrichByOrcids:243`，`fetchWorksAndPatents=false`）**共用**；产出 `AuthorEnrichment`（`:320-328`，7 个字段）。
- 两条路径均**不带 `select=` 参数**，返回完整对象，扩展字段无需新增 API 请求（来源: K-openalex-author-full-object，本轮 grep 复核 `:204` 与 `:35` 的 URL 拼接确认无 `select`；**但 works 路径的 `:36` 确实无 select，authors 路径 `:204` 也无 select**）。
- **2026-08-25 实测（works 路径，50 篇论文 / 662 个机构条目 / 153 个去重机构）**：
  - `type` 字段**填充率 100%**，无缺失
  - 取值分布：`education` 35% / `company` 24% / `facility` 15% / `healthcare` 12% / `nonprofit` 8% / `government` 2% / 其余 <1%
  - 机构对象实际结构：`{ id, display_name, ror, country_code, type, lineage }`
  - **仅通讯作者子集**（真正能拿到邮箱的那批，85 条目 / 59 个去重机构）分布不同：`education` 37% / `facility` 22% / `healthcare` 16% / `nonprofit` 10% / `company` 9%
- **⚠ 未验证项（阻塞 Task 2，见 Task 5）**：上述实测取自 **works** 端点响应。**authors 端点（enrichment 路径）的响应中是否存在 `last_known_institutions` 且其元素是否带 `type`，尚未实测。** 本计划**不得**假设它存在。

### 分类器（本计划不改，仅记录以证明 I5a-1 的代价）
- `ExpertClassificationService.kt:220` `VERSION = "rnd-v2-2026"`；`:222-223` 双阈值 50。
- `NormalizedInputs`（`:73-85`）包含 6 个文本字段 + 3 个数值字段；`fingerprint:178-190` 对这 9 项做 SHA-256。**新增分类输入必然改变指纹语义。**
- `classify:32-64` 的判定优先级：clinical → medicalDomain && !whitelist → 双阈值 → 生产 → 科研 → serviceRole → UNKNOWN。
- 版本门禁有**两处独立实现**，本计划均不改动，但 05B 必须同时改：
  1. `ExpertSearchService.expertSendableFilter:55-63` —— ES 侧，`term version == VERSION`。**3 个生产调用点**：`ManualInitialOutreachService.kt:1324`（批量首发）、`ExpertSearchService.kt:376`（`searchSendableExpertsWithEmail`，被 `InitialOutreachService.kt:34` 的旧定时/队列首发调用）、以及该函数自身。
  2. `BatchExecutionModels.kt:65-69` —— 内存侧，`classification.version != ExpertClassificationService.VERSION` 即拒。**不是调用上述函数，是独立复刻**。
  两者若不同步改，会复现 ES 路径与重试路径口径分裂（来源: K-batch-send-filter-retry-parity）。

## 实现方案

### Task 1：ES 字段声明（I5a-4、I5a-5）

修改文件：`src/main/resources/es/orcid_info_raw.json`、`orcid_info_candidate.json`、`orcid_info_application.json`

三份**逐字相同**，在既有 `"institution"` 声明之后插入：

```json
      "institutionType": { "type": "keyword" },
```

不得使用 `text`、不得加 `fields.keyword` 子字段、不得加 `null_value`。

### Task 2：works 路径采集（I5a-2、I5a-3）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt`

- `PaperAuthor`（`discovery/domain/PaperAuthor.kt`）**不改** —— 见下方说明。
- 在 `parseResponse` 的 `:92-96` 处，`institution` 已取到，追加读取 `institution?.path("type")?.asText(null)?.takeIf { it.isNotBlank() }`。

> **注意**：`PaperAuthor` 是 `AcademicDataSource` 接口的公共产物，7 个数据源共用
> （`discovery/domain/PaperAuthor.kt`）。给它加字段会波及全部数据源的解析与测试。
> 因此本 Task 采取**最小侵入**：在 `PaperAuthor` 末尾新增 `institutionType: String? = null`
> （尾部可空默认参数，I5a-6），仅 OpenAlex 一处填值，其余六个源保持 null。
> 这是新增 ES 字段所必需的最小改动——没有它，works 路径采集不到的类型无法传递到入库点。

修改文件：`src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperAuthor.kt`
- 末尾新增 `val institutionType: String? = null`。

### Task 3：authors 补全路径采集（I5a-2、I5a-3、I5a-6）

> **阻塞已解除（2026-08-25 实测，见 Task 5）**：`/authors` 响应中
> `last_known_institutions` 存在且元素带 `type`，结构与 works 路径的机构对象一致
> （`{id, ror, display_name, country_code, type, lineage}`）。本 Task 正常执行。
> 取数路径确定为 **`last_known_institutions[0].type`**。
> **不要**改用 `affiliations[]` —— 那是带 `years` 的历史机构列表（实测该作者有 12 条），
> 选取规则会引入"取哪一年"的额外判断，违反 I5a-2。

修改文件：`src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt`

- `AuthorEnrichment`（`:320-328`）末尾新增 `val institutionType: String? = null`（I5a-6）。
- `parseAuthorEnrichmentFromNode`（`:276-294`）—— **单专家与批量两条路径的共用解析点**，
  在此提取 `node.path("last_known_institutions")` 的**第一项**的 `type`（I5a-2），放进返回值。
  数组为空、无 `type` 键、`type` 为空串三种情况均产出 null（I5a-3）。
- **禁止**新增任何 HTTP 请求：两条路径均不带 `select=`，字段已在现有响应中
  （来源: K-openalex-author-full-object）。若实测发现字段不在响应内，走上方的取消分支，
  **不得**为此新增一次 `/institutions/{id}` 请求。

### Task 4：入库与补全写入（I5a-3）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`

两处，均照既有可空字段的写法：

1. `:740-748` 构造 `ExpertProfile` 处传入 `institutionType = authorEmail.institutionType`；
   `toIndexMap:752-767` 的 map 中新增 `"institutionType" to profile.institutionType`。
   **注意**：`toIndexMap` 现有写法是无条件放入 map（含 null 值），与既有 `keyword`（恒 null）一致，
   保持该风格；ES 对 null 值不建索引，等价于缺失（I5a-3）。
2. `updateExpertAcademicFields:1085-1096` 的 `doc` 构造中，照 `:1096` 的
   `enrichment.disciplineCategory?.let { doc["disciplineCategory"] = it }` 写法新增一行：
   `enrichment.institutionType?.let { doc["institutionType"] = it }`。
   **必须用 `?.let`**，null 时不写入该键，避免把存量已有值覆盖成 null（I5a-3）。

### Task 5：研究检查点 —— **已于 2026-08-25 完成**

实测结论（命令与原始输出见下）：`/authors` 端点返回的 author 对象**含 `last_known_institutions`
数组，元素带 `type`**，结构为 `{id, ror, display_name, country_code, type, lineage}`，
与 works 路径的机构对象一致。**Task 3 按 `last_known_institutions[0].type` 执行。**

同时发现 `affiliations` 数组（该样本 12 条），元素形如
`{institution: {...含 type...}, years: [...]}` —— 是**历史**机构列表，
本计划**不使用**（选取规则需引入"取哪一年"的判断，违反 I5a-2）。

该实测同时暴露了 I5a-7 记录的语义差异：样本作者的
`last_known_institutions[0]` 是 `OpenAlex`（nonprofit），
而 `affiliations` 中含 `National Institutes of Health`（government, 2008）。

原检查点命令（保留供复现）：

```bash
curl -sS --max-time 20 'https://api.openalex.org/authors?filter=orcid:0000-0003-1613-5981&mailto=wuwei@qftechtalent.com' \
 | python3 -c "
import sys,json
r=json.load(sys.stdin)['results'][0]
for key in ('last_known_institutions','affiliations'):
    v=r.get(key)
    print(f'--- {key}: {type(v).__name__} 条数={len(v) if isinstance(v,list) else 0}')
    if isinstance(v,list) and v: print(json.dumps(v[0], ensure_ascii=False)[:500])
"
```

实测结果：**第一行命中** —— `last_known_institutions` 条数=2，首项
`{"id":"https://openalex.org/I4200000001","ror":"...","display_name":"OpenAlex","country_code":"CA","type":"nonprofit","lineage":[...]}`。
存量专家（`orcidId` 不以 `EMAIL-` 开头者）可经 enrichment 补齐该字段。

### Task 6：读路径（I5a-4）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt`
- 末尾（`expertClassification` 之后）新增 `val institutionType: String? = null`（I5a-6）。

修改文件：`src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt`
- `sourceFields()`（`:585-596`）在 `"institution"` 之后加入 `"institutionType"`。
- `toExpertProfile()` 在 `institution = source.nullableText("institution")`（`:488`）之后新增
  `institutionType = source.nullableText("institutionType"),`。
- **不得**改动同文件的 `expertSendableFilter`（`:55-63`）——那是 05B 的范围（I5a-1）。

### Task 7：测试

修改文件：`src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt`
- `sourceFields()` 包含 `institutionType`。
- `toExpertProfile` 能解析该字段；`_source` 中无该键时为 null；空串时为 null（I5a-3）。
- **回归断言**：`expertSendableFilter()` 的返回结构与本计划前逐字相同（保护 I5a-1）。

修改文件：`src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt`
- works 响应含 `institutions[0].type` 时，`PaperAuthor.institutionType` 被填充。
- `institutions` 为空数组、`type` 缺失、`type` 为空串三种情况均产出 null（I5a-3）。
- 多机构时取**第一个**，与 `affiliation` 取自同一个机构对象（I5a-2）。
- （若 Task 3 未取消）authors 响应解析出 `AuthorEnrichment.institutionType`。

修改文件：`src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`
- `toIndexMap` 产出含 `institutionType` 键。
- `updateExpertAcademicFields` 在 `enrichment.institutionType` 为 null 时**不写入该键**（I5a-3）。
- **回归断言**：`toIndexMap` 的键集合除新增一项外与改动前逐字相同。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/resources/es/orcid_info_raw.json` | 新增 `institutionType: keyword` |
| 2 | `src/main/resources/es/orcid_info_candidate.json` | 同上（逐字相同） |
| 3 | `src/main/resources/es/orcid_info_application.json` | 同上（逐字相同） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperAuthor.kt` | 尾部新增 `institutionType: String? = null` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | works 解析取 type；`AuthorEnrichment` 加字段；authors 共用解析点取 type（依 Task 5 结论） |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | `toIndexMap` 与 `updateExpertAcademicFields` 各新增一处写入 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt` | 尾部新增 `institutionType: String? = null` |
| 8 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | `sourceFields()` + `toExpertProfile()` 各一行 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 读路径与门禁回归断言 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 两条解析路径断言 |

> 第 11 个文件 `src/test/kotlin/.../discovery/service/ExpertDiscoveryServiceTest.kt`（Task 7 第三组）
> 为**纯新增断言、零业务代码**，不增加 interaction surface，执行方须一并交付，验证方按此说明处理。

子系统：ES 字段读写（1）+ 发现/补全链路（2）= 2，符合上限。零前端改动，故无 `## 样式契约` 节。
新增数据字段：**1 个**（`institutionType`），符合「每个共享存储每份计划最多 1 个新字段」。

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7 / Java 11 Maven 工程，**必须用 JDK 11（zulu-11）**，裸 `mvn` 会构建失败（`CLAUDE.md:7`）。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexDataSourceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest

# 一次跑完本计划全部相关类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='ExpertSearchServiceTest,OpenAlexDataSourceTest,ExpertDiscoveryServiceTest'

# 分类器回归（本计划不应改变任何分类行为）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertClassificationServiceTest

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：Maven 退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0`；`git diff --check` 无输出。

来源：`CLAUDE.md:5-27`。

## 验收标准

- I5a-1: `git diff` 证明 `ExpertClassificationService.kt` **零改动**；grep 证明 `institutionType` 在 `src/main/kotlin` 中的出现位置仅限本清单第 5、6、7、8 号文件，**不出现在** `ExpertClassificationService.kt`、`BatchExecutionModels.kt`、`ManualInitialOutreachService.kt`；单测断言 `expertSendableFilter()` 返回结构逐字未变。
- I5a-2: 单测断言多机构输入下，`institutionType` 与 `affiliation` 取自同一个（第一个）机构对象。
- I5a-3: 单测覆盖三种空值来源（数组空 / 无 type 键 / 空串）均产出 null；断言 `updateExpertAcademicFields` 在 null 时不写入该键。
- I5a-4: grep 证明四处均已同步（三份 mapping、`ExpertProfile.kt`、`sourceFields()`、`toExpertProfile()`）。
- I5a-5: `diff` 证明三份 mapping 中该行逐字相同；grep 证明类型是 `keyword` 且无 `fields` 子字段。
- I5a-6: 编译通过即证明未打断既有位置参数构造点；额外 grep 证明两个新字段均位于各自 data class 的**最后一行**。
- I5a-7: grep 证明 `ExpertProfile.institutionType` 的属性注释中同时出现「论文署名机构」与「当前已知机构」两种语义的说明；grep 证明 `updateExpertAcademicFields` 的 `doc` 构造中**没有** `institution` 键（未为消除差异而顺手写入机构名）。
- I5a-8: grep 证明 `updateExpertAcademicFields` 中 `institutionType` 的写入是无条件的 `?.let`，**不存在**任何形如「已有值则跳过」的条件判断；单测断言：文档已有 `institutionType=education` 时，enrichment 返回 `company` 会覆盖成 `company`。
- 回归：执行「验证命令」节的全量测试命令通过，其中 `ExpertClassificationServiceTest` 必须**零用例改动**即通过。

## 人工验收清单

### A5a-1: 新发现的专家带上机构类型
- 前置条件: 测试环境可执行「发现专家 → 深度发现」，且 OpenAlex 源已启用。
- 操作步骤: 1. 部署本计划；2. 跑一次小规模深度发现（限制论文数以缩短时间）；3. 用 `GET /api/experts?level=RAW&size=20` 取回若干新入库专家；4. 直接查 ES 原始文档 `GET /orcid_info/_doc/{orcidId}`。
- 预期结果: 至少部分文档的 `_source` 中出现 `institutionType`，取值属于 `education`/`company`/`healthcare`/`facility`/`nonprofit`/`government`/`other`/`archive`/`funder` 之一；同一文档的 `institution`（机构名）与该类型在语义上对得上（例如 `institution` 为某大学时类型为 `education`）。
- 覆盖: I5a-2、需求描述第 1 条

### A5a-2: 存量专家经补全获得机构类型
- 前置条件: 选定一名 CANDIDATE 专家，其 `orcidId` **不以 `EMAIL-` 开头**（否则永远不会被 enrichment 覆盖），且当前 `_source` 中无 `institutionType`。记录其 orcidId。
  **若 Task 5 判定 authors 响应无机构类型，本条整体作废**，改为记录「存量专家不通过此路径获得该字段」。
- 操作步骤: 1. 执行「发现专家 → 补充学术数据（OpenAlex）」；2. 重新读取该文档。
- 预期结果: `institutionType` 出现且非空；`hIndex` / `researchFields` 等既有 enrichment 字段与执行前相比**只增不改**（未被覆盖成 null）。
- 覆盖: I5a-3、现状审计的 interaction point「写路径 2 × 读路径」

### A5a-3: 晋升透传不丢字段
- 前置条件: 选定一名带 `institutionType` 的 RAW 专家，记录该值；确认其尚未进入 CANDIDATE。
- 操作步骤: 1. 执行「快速晋升（扫描 RAW）」使其进入 CANDIDATE；2. 读取 CANDIDATE 层同一 orcidId 的文档。
- 预期结果: `institutionType` 值与 RAW 层**逐字相同**。
- 覆盖: 现状审计的 interaction point「写路径 3/4/5 透传」

### A5a-4: 分类结果零变化（回归）
- 前置条件: 部署前，用 `GET /api/experts?level=CANDIDATE&size=50` 记录 50 名专家的 `expertClassification.type` 与两个分数（若子计划 01 尚未上线，直接查 ES `_source`）。
- 操作步骤: 1. 部署本计划；2. 等待或手动触发一次增量分类；3. 重新读取同样 50 名专家。
- 预期结果: 50 条的 `type`、`productionScore`、`researchScore`、`version`、`sourceFingerprint` **逐字全部相同**。任一条变化即为 I5a-1 违规。
- 覆盖: I5a-1、必须保持不变第 1 条

### A5a-5: 发信行为零变化（回归）
- 前置条件: 部署前记录某 INTRODUCTION 批量任务的收件人预估数字 M。
- 操作步骤: 1. 部署本计划；2. 不改任何配置；3. 读取同一任务的预估；4. 执行一轮发送。
- 预期结果: 预估等于 M；实际发送数与部署前同条件一致。
- 覆盖: I5a-1、必须保持不变第 2 条

### A5a-6: 既有机构名字段未被改动（回归）
- 前置条件: 部署前记录 10 名专家的 `institution` 与 `employment` 原值。
- 操作步骤: 1. 部署并跑一次 enrichment；2. 重新读取这 10 名专家。
- 预期结果: `institution` 与 `employment` 十条全部逐字未变。
- 覆盖: 必须保持不变第 3 条

### A5a-8: 两条路径语义差异可观察（覆盖 I5a-7 / I5a-8）
- 前置条件: 选定一名 CANDIDATE 专家，其 `orcidId` 不以 `EMAIL-` 开头，且已有 `institutionType`（来自发现时的论文署名机构）。记录该值与其 `institution` 机构名。
- 操作步骤: 1. 执行「发现专家 → 补充学术数据（OpenAlex）」；2. 重新读取该文档的 `institution` 与 `institutionType`；3. 用 `curl 'https://api.openalex.org/authors?filter=orcid:<该专家的 orcid>&mailto=wuwei@qftechtalent.com'` 查其 `last_known_institutions[0]`。
- 预期结果: `institution`（机构名）**逐字未变**，仍是发现时的论文署名机构；`institutionType` 等于 OpenAlex 返回的 `last_known_institutions[0].type`。两者**可以不一致**（例如 `institution` 是某大学而 `institutionType` 是 `company`）——**这不是缺陷**，是 I5a-7 记录的预期语义。若 `institution` 被改写，即为「必须保持不变」第 3 条违规。
- 覆盖: I5a-7、I5a-8、必须保持不变第 3 条

### A5a-7: 真实分布盘点（本计划的产出物，供 05B 定稿用）
- 前置条件: A5a-1 与 A5a-2 已通过，且已跑过至少一轮完整 enrichment。
- 操作步骤: 对 CANDIDATE 层执行 terms 聚合：
  ```
  POST /orcid_info_candidate/_search
  { "size": 0, "aggs": { "t": { "terms": { "field": "institutionType", "size": 20 } },
                          "missing": { "missing": { "field": "institutionType" } } } }
  ```
- 预期结果: 产出一张真实分布表（各 type 的条数 + 缺失数）。**本条无对错判据，它的产出是 05B 的输入** ——
  05B 的评分数值必须依据这张表来定，而不是依据 2026-08-25 那份 50 篇论文的抽样。
- 覆盖: 需求描述第 1 条；05B 的前置

人工验收开始时，从本节导出 `05a-institution-type-collection-acceptance.md`；不得提前生成。
