# 学科分类数据落地(Plan A:disciplineCategory 字段)

> 系列计划 1/2。后续 Plan B(`discipline-filter-batch-send.md`)依赖本计划,反向不依赖。
> 本计划独立可交付:完成后专家文档携带学科分类,可通过 ES 查询验证,不含任何前端/筛选改动。

## 需求描述

**Observable outcome:**
1. 经过 enrichment 的专家在三层 ES 索引文档中携带 `disciplineCategory` 字段,取值 `STEM`(理工医)或 `HUMANITIES`(文社科),来源为 OpenAlex author `topics[].domain.display_name`。
2. 已完成学术字段补齐的存量专家,通过现有"补充学术信息"任务(`POST /api/expert-discovery/enrich`)一次运行完成回填。
3. `/api/experts` 列表响应中每个专家带 `disciplineCategory` 字段(可为 null),供 Plan B 前端消费。

**What must NOT change:**
- 现有 enrichment 字段(hIndex、citationCount、worksCount、researchFields、recentWorkTitles、patentTitles、enrichedAt、enrichmentSource)的写入行为。
- enrichment 的限流/退避/熔断行为(429/503 → RateLimited → backoff)。
- L3→L2、L2→L1 晋升逻辑(`promoteToCandidate` / `promoteToApplication`)——**零改动**,依赖其 `_source` 全量复制透传新字段。
- 30 天 TTL 重新 enrichment 的既有语义。
- OpenAlex API 请求次数(不新增请求,domain 在既有 author 响应中)。

**Out of scope(显式推迟):**
- 专家列表/批量发送的筛选(Plan B)。
- 前端任何改动(Plan B)。
- 存储 domains 明细数组(每 store 每计划最多 1 个新字段;主域单值已满足筛选需求)。
- 对无 OpenAlex topics 的专家做关键词/LLM 兜底分类。

## 关键不变量

### Invariant I-1: 双值或缺失
- Rule: `disciplineCategory` 在 ES 中只允许两个显式值:`"STEM"`、`"HUMANITIES"`;"未分类"由**字段缺失**表达。禁止写入 `""`、`"UNKNOWN"`、`null` 等第三态。
- Applies to: `ExpertDiscoveryService.updateExpertAcademicFields()`(唯一写入点,见 I-3)。
- Violation consequence: Plan B 的 `term` 筛选与 `must_not exists`(未分类)语义被第三态污染,筛选结果不完整。
- 来源: original

### Invariant I-2: 固定映射 + 加权主域
- Rule: OpenAlex domain → category 映射固定为:`Physical Sciences` / `Life Sciences` / `Health Sciences` → `STEM`;`Social Sciences` → `HUMANITIES`。主域 = 对 author `topics[]` **全部条目**(非 top5)按 `count` 求和分组取最大;和相等时取 `STEM`(本项目目标人群)。未识别的 domain 名称(OpenAlex 新增域)不参与求和;若全部不可识别则视为无 topics(不写字段,遵守 I-1)。
- Applies to: `OpenAlexDataSource.parseAuthorEnrichmentFromNode()`(解析)、`ExpertDiscoveryService.updateExpertAcademicFields()`(写入)。
- Violation consequence: 同一专家在不同批次得到不同分类,筛选结果不稳定。
- 来源: original

### Invariant I-3: enrichment 是唯一写入点
- Rule: `disciplineCategory` 只由 `updateExpertAcademicFields()` 写入。晋升(`promoteToCandidate` / `promoteToApplication`)通过 `_source.fields()` 全量复制自动透传,**不得**在晋升代码中显式读写该字段。
- Applies to: `ExpertIndexWriterService`(禁改)、`ExpertDiscoveryService`(唯一写入)。
- Violation consequence: 写入点分裂后,后续字段语义变更需要多点同步,产生漂移。
- 来源: original(经 grep 验证:`promoteToApplication` L268-283、`promoteToCandidate` L386-393 均为 `_source` 全量复制)

### Invariant I-4: mapping 三文件 + 增量集合同步
- Rule: 新字段必须同时:① 在 `es/orcid_info_raw.json`、`es/orcid_info_candidate.json`、`es/orcid_info_application.json` 三个 mapping 中声明为 `{"type": "keyword"}`;② 加入 `ExpertIndexService.phase5NewFields` 集合,使 `@PostConstruct bootstrapMappings()` 对已存在索引执行 `PUT _mapping` 增量更新。三层索引均为 `dynamic: false`,缺任一步字段不可查询。
- Applies to: 3 个 mapping JSON、`ExpertIndexService.kt`。
- Violation consequence: 字段写入成功但 term 查询/聚合永远空结果,且故障隐蔽(无报错)。
- 来源: K-es-dynamic-false

### Invariant I-5: 读模型四处同步
- Rule: ES 字段要在应用侧可用,必须同步:① mapping(I-4)② `ExpertProfile.kt` 属性 ③ `ExpertSearchService.sourceFields()` ④ `ExpertSearchService.toExpertProfile()`;暴露到前端 API 还需 ⑤ `ExpertIndexResponse` + 其 `from()` 工厂。
- Applies to: `ExpertProfile.kt`、`ExpertSearchService.kt`、`ExpertIndexController.kt`。
- Violation consequence: 字段存在但应用读不到,Plan B 无数据可显示/验证。
- 来源: K-expert-profile-source-sync

### Invariant I-6: 回填子句防无限重扫
- Rule: `buildEnrichmentFilters()` 的回填条件必须限定为 `enrichedAt exists AND researchFields exists AND disciplineCategory missing`(作为既有 should 列表的第 3 个子句)。无 topics 的专家(researchFields 缺失)永远无法被分类,不得纳入回填集,否则每次 enrichment 都会重扫全部无 topics 专家浪费 API 配额。
- Applies to: `ExpertDiscoveryService.buildEnrichmentFilters()`。
- Violation consequence: `/enrich/stats` pending 数虚高不收敛;每次任务重复消耗 OpenAlex 配额(限额 10 万/天 + polite pool ~1000 req/5min,见 K-enrichment-no-ratelimit)。
- 来源: original + K-enrichment-no-ratelimit

## 现状审计

### OpenAlex 数据源(`discovery/service/OpenAlexDataSource.kt`)
- author 获取有两条路径,均**不带 `select=` 参数**,返回完整 author 对象(含 `topics[]` 及其 `domain` 子结构):
  1. `enrichAuthor(openAlexAuthorId)` L108-124 — `GET /authors/{id}`,单个。
  2. `batchEnrichByOrcids(orcids)` L200+ — `GET /authors?filter=orcid:a|b|c`,批量(enrichment 任务主路径)。
- 两条路径共用 `parseAuthorEnrichmentFromNode()` L277-294:现取 `topics[]` 按 count 排序 top5 的 `display_name` → `AuthorEnrichment.topics`。**`domain.display_name` 当前被丢弃** — 本计划的解析改动点。
- `AuthorEnrichment` data class 在同文件末尾 L304-311。
- **研究检查点(执行时必做)**:OpenAlex author 对象 `topics[]` 每项含 `domain: {id, display_name}`,domain 全集为 Physical Sciences / Life Sciences / Health Sciences / Social Sciences 四值。执行 agent 动手前须用 1 次真实 API 调用(如 `GET https://api.openalex.org/authors/A5023888391`)确认响应结构,不得凭记忆。

### 三层 ES 索引(RAW / CANDIDATE / APPLICATION)
- Mapping:`src/main/resources/es/orcid_info_{raw,candidate,application}.json`,均 `dynamic: false`;`researchFields` 已是 `keyword`(raw L24 / application L36 / candidate L25)。
- 增量 mapping 机制:`ExpertIndexService.bootstrapMappings()`(`@PostConstruct`)→ `updateMappingIfNeeded()` 只推送 `phase5NewFields` 集合(L111-117)中的字段到已存在索引。
- Write paths(grep 全量核实):
  1. `ExpertDiscoveryService.updateExpertAcademicFields()` L1068+ — enrichment 结果 `_update` partial update,对三层逐层 `documentExistsInIndex` 后按需写(来源: K-enrichment-write-three-layers)。**本计划唯一新增写入点。**
  2. `ExpertIndexWriterService.promoteToCandidate()` L365 — RAW `_source` 全量复制 → CANDIDATE。透传,无需改。
  3. `ExpertIndexWriterService.promoteToApplication()` L232 — CANDIDATE `_source` 全量复制 → APPLICATION。透传,无需改。
  4. `ExpertIndexWriterService.indexToRaw()` L435 — 发现管道写 RAW,写入 map 由发现流程组装,不含 enrichment 字段。不涉及。
  5. `ExpertIndexWriterService.syncCandidateOperatorStatus/Batch、markApplicationClosed、syncApplicationStatus、addTag/removeTag` — script/doc 局部更新,不触碰本字段。不涉及。
- Read paths:
  1. `ExpertSearchService.sourceFields()` L249-263 + `toExpertProfile()` L202-242 — 所有查询共用。需加字段(I-5)。
  2. `ExpertIndexController.listExperts` → `ExpertIndexResponse.from()` L351+ — 前端列表响应。需加字段(I-5⑤)。
  3. `MailVariableService` / `MailPlaceholderService` — 读 `researchFields` 做模板变量,不读新字段。不涉及。
- Interaction points:
  - 写路径 1 × 读路径 1/2:enrichment 写入后列表接口应能返回该字段(A-1 验证)。
  - 写路径 2/3(透传)× 读路径 1:晋升后字段不丢失(A-4 验证)。

### Enrichment 任务选取(`ExpertDiscoveryService`)
- `buildEnrichmentFilters(cutoff)` L797-812:`should [enrichedAt missing, enrichedAt < now-30d], minimum_should_match=1, must_not [orcidId prefix EMAIL-]`。回填子句加在 should 列表(I-6)。
- `enrichExistingExperts()` L831+:CANDIDATE 层 `searchAfterExpertsFiltered` 遍历,批量 50/请求,退避+熔断已有(K-enrichment-no-ratelimit — 本计划不改此逻辑)。
- `getEnrichmentStats()` L789-795 与任务共用 `buildEnrichmentFilters` → 回填量自动计入 pending,无需另做统计。
- 触发入口:`POST /api/expert-discovery/enrich`(K-openalex-enrichment-existing)。前端已有任务按钮,零前端改动。

## 实现方案

### Task 1: OpenAlex 解析 domain(遵守 I-2)
文件:`discovery/service/OpenAlexDataSource.kt`
- `AuthorEnrichment` 增加 `val disciplineCategory: String? = null`。
- `parseAuthorEnrichmentFromNode()`:遍历 `topics[]` 全部条目,按 `domain.display_name` 分组累加 `count`;映射表 `{"Physical Sciences","Life Sciences","Health Sciences"} → STEM`,`{"Social Sciences"} → HUMANITIES`;取和最大者,并列取 STEM;无可识别 domain → null。
- 消费方确认:`AuthorEnrichment` 仅被 `updateExpertAcademicFields()` 消费(grep 核实),新属性带默认值,`baseEnrichment` 等既有构造点不需改。

### Task 2: enrichment 写入 + 回填子句(遵守 I-1、I-3、I-6)
文件:`discovery/service/ExpertDiscoveryService.kt`
- `updateExpertAcademicFields()` L1071 doc map:`enrichment.disciplineCategory?.let { doc["disciplineCategory"] = it }` — null 不写(I-1)。
- `buildEnrichmentFilters()` should 列表追加第 3 子句:
  `bool { must [exists enrichedAt, exists researchFields], must_not [exists disciplineCategory] }`(I-6)。

### Task 3: mapping 三文件 + 增量集合(遵守 I-4)
文件:`es/orcid_info_raw.json`、`es/orcid_info_candidate.json`、`es/orcid_info_application.json`、`expert/service/ExpertIndexService.kt`
- 三个 JSON 的 `properties` 中紧邻 `researchFields` 添加 `"disciplineCategory": { "type": "keyword" }`。
- `phase5NewFields` 集合(L111-117)加入 `"disciplineCategory"`。

### Task 4: 读模型同步(遵守 I-5)
文件:`expert/domain/ExpertProfile.kt`、`expert/service/ExpertSearchService.kt`、`expert/controller/ExpertIndexController.kt`
- `ExpertProfile` 增加 `val disciplineCategory: String? = null`(紧邻 `researchFields`)。
- `ExpertSearchService.sourceFields()` 列表与 `toExpertProfile()`(`source.nullableText("disciplineCategory")`)各加一处。
- `ExpertIndexResponse` 增加 `val disciplineCategory: String? = null`,`from()` 透传 `expert.disciplineCategory`。

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `src/main/kotlin/.../discovery/service/OpenAlexDataSource.kt` | domain 解析 + AuthorEnrichment 新属性 |
| 2 | `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt` | doc map 写入 + 回填子句 |
| 3 | `src/main/resources/es/orcid_info_raw.json` | mapping 加字段 |
| 4 | `src/main/resources/es/orcid_info_candidate.json` | mapping 加字段 |
| 5 | `src/main/resources/es/orcid_info_application.json` | mapping 加字段 |
| 6 | `src/main/kotlin/.../expert/service/ExpertIndexService.kt` | phase5NewFields 加字段 |
| 7 | `src/main/kotlin/.../expert/domain/ExpertProfile.kt` | 新属性 |
| 8 | `src/main/kotlin/.../expert/service/ExpertSearchService.kt` | sourceFields + toExpertProfile |
| 9 | `src/main/kotlin/.../expert/controller/ExpertIndexController.kt` | ExpertIndexResponse + from() |

共 9 文件,2 子系统(enrichment 写入侧 / 专家读模型)。新增共享存储字段:1(disciplineCategory)。测试文件按需新增/修改不计入(QaMatchServiceTest 同级的 OpenAlexDataSource 解析单测)。

## 验收标准

- I-1: 单测断言 `parseAuthorEnrichmentFromNode` 对无 topics / 全部未知 domain 的节点返回 `disciplineCategory == null`;grep `disciplineCategory` 写入点仅 `updateExpertAcademicFields` 一处,且写入被 `?.let` 包裹(无空串/UNKNOWN 分支)。
- I-2: 单测:构造 topics(Physical Sciences count=10, Social Sciences count=15)→ HUMANITIES;(Physical 10 + Health 6, Social 15)→ STEM(16>15);并列 → STEM;未知 domain 混入不计入。
- I-3: diff 断言 `ExpertIndexWriterService.kt` 零改动。
- I-4: 三个 mapping JSON 均含 `"disciplineCategory": { "type": "keyword" }`;`phase5NewFields` 含该字段名;启动日志出现 `Updated N mapping fields`。
- I-5: `sourceFields()`、`toExpertProfile()`、`ExpertProfile`、`ExpertIndexResponse`、`from()` 五处 grep 均命中 `disciplineCategory`。
- I-6: 单测/grep 断言回填子句为 `must [exists enrichedAt, exists researchFields] + must_not [exists disciplineCategory]`;`getEnrichmentStats` 与 `enrichExistingExperts` 共用 `buildEnrichmentFilters`(无第二份副本)。
- 集成:`mvn test` 全绿;既有 enrichment 相关测试不回归。

## 人工验收清单

### A-1: 新 enrichment 写入学科分类
- 前置条件: CANDIDATE 层存在一个 `enrichedAt` 缺失、ORCID 在 OpenAlex 有理工科 topics 的专家(如自然科学领域学者;可用 ES `_update` 清掉某专家的 `enrichedAt`/`researchFields`/`disciplineCategory` 构造)。
- 操作步骤: ① 打开管理后台 → 触发"补充学术信息"任务(或 `POST /api/expert-discovery/enrich`);② 任务完成后 `GET {es}/{candidate_index}/_doc/{orcid}`。
- 预期结果: `_source.disciplineCategory` 值为 `"STEM"`;`researchFields`、`hIndex`、`enrichedAt` 同时更新。
- 覆盖: 需求 1、I-1、I-2、交互点(写1×读1)

### A-2: 存量回填经既有任务完成
- 前置条件: 存在 ≥1 个 `enrichedAt` 与 `researchFields` 均存在但 `disciplineCategory` 缺失的 CANDIDATE 专家(存量数据天然满足)。
- 操作步骤: ① `GET /api/expert-discovery/enrich/stats` 记录 pending 数;② 触发 enrichment 任务至完成;③ 再查 stats;④ 抽查 3 个此前缺字段的专家 ES 文档。
- 预期结果: ①的 pending ≥ 存量缺字段数;③的 pending 明显下降且不再包含"有 researchFields 无 disciplineCategory"的文档(用 ES `_count` + bool 查询核对为 0 或仅剩 OpenAlex 查无此人者);④ 抽查文档均有 `"STEM"` 或 `"HUMANITIES"`。
- 覆盖: 需求 2、I-6

### A-3: 无 topics 专家不写字段、不重扫
- 前置条件: 存在 `enrichedAt` 存在但 `researchFields` 缺失的专家(OpenAlex 无 topics)。
- 操作步骤: ① 记录该专家 ES 文档;② 触发 enrichment 任务;③ 复查该文档与 `/enrich/stats` pending。
- 预期结果: 文档无 `disciplineCategory` 字段(非空串);该专家不计入 pending(30 天 TTL 到期前不被重扫)。
- 覆盖: I-1、I-6

### A-4: 晋升透传(回归)
- 前置条件: 一个已有 `disciplineCategory: "STEM"` 的 CANDIDATE 专家,且有其联系人记录。
- 操作步骤: ① 模拟该专家回信触发 L2→L1 晋升(或后台手动晋升入口);② `GET {es}/{application_index}/_doc/{orcid}`。
- 预期结果: APPLICATION 文档 `disciplineCategory` 仍为 `"STEM"`;CANDIDATE 文档已删除。
- 覆盖: must-NOT-change(晋升零改动)、I-3、交互点(写3×读1)

### A-5: 既有 enrichment 字段回归
- 前置条件: 同 A-1。
- 操作步骤: enrichment 完成后对比该专家文档前后差异。
- 预期结果: `hIndex`、`citationCount`、`worksCount`、`researchFields`、`recentWorkTitles`、`patentTitles`、`enrichedAt`、`enrichmentSource` 全部正常写入,值非空(OpenAlex 有数据时);无字段丢失。
- 覆盖: must-NOT-change(既有字段写入)

### A-6: 列表接口暴露字段
- 前置条件: 完成 A-1 或 A-2。
- 操作步骤: `GET /api/experts?level=CANDIDATE&size=10` 查看响应。
- 预期结果: 已分类专家的条目含 `"disciplineCategory": "STEM"`(或 `"HUMANITIES"`),未分类为 `null`。
- 覆盖: 需求 3、I-5
