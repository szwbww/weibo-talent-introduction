# 子计划 04：深度发现按学科范围取数 + EuropePMC 开关缺陷修复

## 需求描述

可观察结果：

1. 深度发现新增「学科范围」概念 `subjectScope`，首个取值 `RND_TARGET`（工程/材料/计算机/化工/能源/物理）。定时发现任务默认启用该范围；手动接口可覆盖。
2. 在 `RND_TARGET` 下：OpenAlex 与 arXiv 的查询按各自原生语法收窄到目标学科；EuropePMC 与 PMC OA 不参与本次运行。
3. `EUROPE_PMC_ENABLED=false` 真正生效——定时发现不再运行 Europe PMC。
4. 不设 `subjectScope` 时，各源查询串与本计划落地前**逐字相同**。

必须保持不变：

- `subjectScope` 为 null 时，六个源的查询构造、分页游标、限流、重试逻辑全部逐字不变。
- 邮箱抽取链路（`JatsXmlEmailParser` / `PdfEmailExtractor` / `PlainTextEmailExtractor` / `UnpaywallClient`）零改动。
- `ExpertDiscoveryService` 的作者去重、入库判定、`toIndexMap` 字段集合、RAW 扫描分支零改动。
- 手动发现界面的「数据源平台」勾选语义不变（勾选即 `criteria.sources` 非空，按名单过滤）。
- `PaperSearchCriteria` 既有 9 个字段的名称、类型、默认值不变。

范围外：

- 接入 SBIR 或任何新数据源（见主计划的阻塞说明）。
- 修改 `OpenAlexDataSource.resolveDisciplineCategory`（`:298-309`）的 STEM/HUMANITIES 归类。
- 覆盖制药研发学科（Pharmacology 等 Health Sciences / Life Sciences 域的 field）。本轮按主计划 F-5 的选项 1 处理，待需求方决策后另行评估。
- 修改 `PaperMetadata` / `PaperAuthor` 字段集合。
- 修改 CROSSREF 与 ORCID 两个源的查询（前者无有效学科过滤能力，后者按 ORCID 记录拉取，均不适用主题范围）。
- 前端新增「学科范围」控件（本轮 `subjectScope` 只由定时任务与接口请求体设置）。

## 关键不变量

### Invariant I4-1: 单一语义 + 各源翻译
- Rule: 学科范围的定义（包含哪些学科、每个源翻译成什么查询片段）只在一个新增的 `SubjectScopeCatalog` 中声明。各 DataSource 只调用它取回本源的片段，不得在自己文件里硬编码学科名单。
- Applies to: `SubjectScopeCatalog`（新增）、`OpenAlexDataSource`、`ArxivDataSource`、`ExpertDiscoveryService`。
- Violation consequence: 与分类器词表同类问题——半年后没人说得清「工程学科」到底怎么定义，各源口径分叉。
- 来源: original（同源于主计划 M-2 的单一语义原则）

### Invariant I4-2: null 即原样
- Rule: `criteria.subjectScope == null` 时，每个源产出的查询串必须与本计划落地前**逐字相同**（含参数顺序）。
- Applies to: `OpenAlexDataSource.buildFilter`（`:63-74`）、`ArxivDataSource.searchPapers`（`:31-44`）、`ExpertDiscoveryService.resolveEnabledSources`（`:201-216`）。
- Violation consequence: 未启用新功能的运行（手动发现、其他 scope）行为被意外改变，无法归因。
- 来源: original

### Invariant I4-3: 源退出是「本次不参与」不是「注销」
- Rule: `RND_TARGET` 下 EuropePMC 与 PMC OA 的退出，必须实现为 `resolveEnabledSources` 的运行时过滤，**不得**删除它们在该函数中的注册行，也不得改动它们的 `enabled` 配置默认值。
- Applies to: `ExpertDiscoveryService.resolveEnabledSources`。
- Violation consequence: 其他 scope 或手动指定 `sources` 时无法再使用这两个源；`/api/expert-discovery/sources` 的返回也会缺项。
- 来源: original

### Invariant I4-4: EuropePMC 的 enabled 必须被读取
- Rule: `resolveEnabledSources` 中 Europe PMC 的加入必须以 `europePmcProperties.enabled` 为条件，与其余六源的 `@ConditionalOnProperty` 语义等价。
- Applies to: `ExpertDiscoveryService.resolveEnabledSources:209`。
- Violation consequence: 现状缺陷——`EuropePmcDataSource` 是七源中唯一裸 `@Service`（无 `@ConditionalOnProperty`），被 `ExpertDiscoveryService:50` 直接注入，`:209` 的 `add({ europePmc }, europePmc.sourceName)` 从不读 `enabled`。定时发现传空 `sources`（`ExpertDiscoveryScheduler:40-43`），命中 `criteria.sources.isEmpty()` 恒真分支，Europe PMC 照跑满配额；而 `/api/expert-discovery/sources`（`ExpertDiscoveryController:53`）读的是 `europePmcProperties.enabled`，页面显示为灰。**页面与实际行为矛盾。**
- 来源: original（2026-08-25 审计发现）

### Invariant I4-5: 配额调整只改 yml
- Rule: 各源的 `maxPapersPerSource` 调整只改 `application.yml` 中的默认值，不改任何 `*Properties.kt` 的 Kotlin 默认值，也不在代码中硬编码数字。
- Applies to: `application.yml:154-215`。
- Violation consequence: 两处默认值分叉，运维改环境变量时行为不可预测。
- 来源: original

## 现状审计

### `PaperSearchCriteria`
- Schema: `discovery/domain/PaperSearchCriteria.kt:3-13`，9 个字段：`keywords`、`affiliationKeywords`、`excludeCountries`（默认 `listOf("CN")`）、`publicationYearFrom`（2020）、`publicationYearTo`（2026）、`openAccessOnly`（true）、`pageSize`（100）、`cursor`、`sources`（默认空）。
- 构造点（全集，grep `PaperSearchCriteria(` 实测 5 处）:
  1. `ExpertDiscoveryController.kt:83` —— `/run` 的 body 为 null 时的兜底
  2. `ExpertDiscoveryController.kt:93` —— 同上，第二处兜底（同一端点内重复构造）
  3. `ExpertDiscoveryController.kt:155` —— `/run/by-keyword`
  4. `ExpertDiscoveryScheduler.kt:40-43` —— 定时任务
  5. `ExpertDiscoveryService.kt:1160` —— 内部使用
- Interaction points: 新增字段必须是**尾部带默认值** `subjectScope: String? = null`，否则打断上述 5 处及全部测试构造。

### 各源查询构造
| 源 | 构造位置 | 无关键词时的行为 |
|---|---|---|
| EUROPE_PMC | `EuropePmcDataSource.buildQuery`（`:145-164`） | `IN_EPMC:y` + `OPEN_ACCESS:y` + `PUB_YEAR:[from TO to]`，**无主题约束**（`:153` 的 keywords 分支不进） |
| PMC_OA | `PmcOaDataSource.buildQuery`（`:86` 一带） | 同上，keywords 分支不进 |
| OPENALEX | `OpenAlexDataSource.buildFilter`（`:63-74`） | `is_oa:true` + `publication_year:from-to` + 逐个 `authorships.institutions.country_code:!CN`，**无主题约束**（`:70-72` 的 keywords 分支不进） |
| CROSSREF | `CrossrefDataSource` | 无学科过滤能力 |
| CORE | `CoreDataSource.doInitialSearch`（`:47-50`） | `q = "*" AND yearPublished>=from AND yearPublished<=to` |
| ARXIV | `ArxivDataSource.searchPapers`（`:31-44`） | `search_query=all:*`，**全库按提交时间倒序** |
| ORCID | `OrcidDataSource` | 按 ORCID 记录拉取，不适用主题范围 |

### 源启用与配额
- `ExpertDiscoveryService.resolveEnabledSources:201-216`：内嵌 `add(provider, name)` 闭包，条件为 `src != null && (criteria.sources.isEmpty() || criteria.sources.contains(name))`。六次调用（`:209-214`）。
- `:209` 的 Europe PMC 是唯一以**非 provider** 形式注入的源（`ExpertDiscoveryService.kt:50` 直接注入 `EuropePmcDataSource`），也是唯一无 `@ConditionalOnProperty` 的源。实测其余六源均有该注解：`CoreDataSource.kt:21`、`ArxivDataSource.kt:18`、`PmcOaDataSource.kt:18`、`OpenAlexDataSource.kt:19`、`CrossrefDataSource.kt:17`、`OrcidDataSource.kt:17`。
- `/api/expert-discovery/sources`（`ExpertDiscoveryController.kt:50-64`）返回硬编码 7 项，`enabled` 由 `provider.getIfAvailable() != null` 现算，Europe PMC 例外（读 `europePmcProperties.enabled`）。前端 `app.js:5893 fetchSources` 把 `enabled=false` 渲染为 **disabled 且未勾选的 checkbox（不隐藏）**，`getSelectedSources`（`:15911` 一带）用 `:checked:not([disabled])` 过滤。
- 当前配额（`application.yml`）：`EUROPE_PMC_MAX_PAPERS:1500`（`:160`）、`PMC_OA_MAX_PAPERS:1000`（`:206`）、`OPENALEX_MAX_PAPERS:1200`（`:168`）、`CROSSREF_MAX_PAPERS:500`（`:184`）、`ARXIV_MAX_PAPERS:200`（`:192`）、`CORE_MAX_PAPERS:500`（`:215`）。生物医学两源合计 2500，占约 4900 的一半以上。
- Interaction points:
  - `resolveEnabledSources` × `ExpertDiscoveryScheduler:40`（传空 `sources`）：这是 I4-4 缺陷的触发路径。
  - `resolveEnabledSources` × `getAvailableSources`：两者对「启用」的判定必须一致，否则页面与行为矛盾。

### 定时与手动入口
- `ExpertDiscoveryScheduler.scheduleDiscovery:25-58`，`:40-43` 构造 criteria，`:50` 调 `discoveryService.discover(criteria, "SCHEDULED", ...)`。cron 为 `application.yml:149` 的 `${EXPERT_DISCOVERY_CRON:0 0 2 * * ?}`。
- `ExpertDiscoveryController.triggerDiscovery:66-131`（`/run`，接受可空 body）与 `triggerDiscoveryByKeyword:133-`（`/run/by-keyword`，`@RequestParam`）。
- Interaction points: 定时启用 scope、手动可覆盖 —— 这是做 A/B 对照实验的手段。

## 实现方案

### Task 1：学科范围目录（I4-1）

新增文件：`src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt`

要求：

- `const val RND_TARGET = "RND_TARGET"`；`val ALLOWED: Set<String> = setOf(RND_TARGET)`。
- `fun openAlexFilterParts(scope: String?): List<String>` —— 返回追加到 `buildFilter` `parts` 的片段列表；null 或未知 scope 返回 `emptyList()`。
- `fun arxivCategories(scope: String?): List<String>` —— 返回 arXiv 分类前缀列表；null 返回 `emptyList()`。
- `fun coreKeywords(scope: String?): List<String>` —— 返回可喂给 CORE `q` 的主题词；null 返回 `emptyList()`。
- `fun excludedSources(scope: String?): Set<String>` —— `RND_TARGET` 返回 `setOf("EUROPE_PMC", "PMC_OA")`；null 返回 `emptySet()`。
- 六个学科的 OpenAlex field id **已于 2026-08-25 实测取得**（CP-3，见主计划 F-2），逐字写入本文件并在注释中记录取数日期：

| field | id | field | id |
|---|---|---|---|
| Chemical Engineering | `15` | Energy | `21` |
| Computer Science | `17` | Engineering | `22` |
| Materials Science | `25` | Physics and Astronomy | `31` |

  `openAlexFilterParts(RND_TARGET)` 返回单个片段：`primary_topic.field.id:22|31|17|25|21|15`
  （实测该多值 `|` 语法可用，count = 1,473,809）。
- **不叠加 `primary_topic.domain.id:!4`（Health Sciences）**：实测 domain 为
  Life Sciences `1` / Social Sciences `2` / Physical Sciences `3` / Health Sciences `4`，
  而上述六个 field **全部隶属 Physical Sciences**，正向锁定已隐含排除 Health Sciences。
  再加一段反向排除是冗余条件，只会让查询串更长、更难与「改动前逐字相同」的锚点断言对齐。
- arXiv 分类取 `cs.*`、`eess.*`、`cond-mat.*`、`physics.*`（arXiv 官方分类命名稳定，可直接写入并在注释中标注来源）。

### Task 2：criteria 字段（I4-2）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperSearchCriteria.kt`
- 末尾新增 `val subjectScope: String? = null`。**必须是最后一个字段且带默认值**（现状审计已列出 5 个构造点）。

### Task 3：OpenAlex 与 arXiv 翻译（I4-1、I4-2）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt`
- `buildFilter`（`:63-74`）在 `return parts.joinToString(",")` 之前追加：
  `parts += SubjectScopeCatalog.openAlexFilterParts(criteria.subjectScope)`
- 位置在 keywords 分支之后，保证 null 时 `parts` 内容与改动前逐字相同（I4-2）。

修改文件：`src/main/kotlin/com/weibo/talentintroduction/discovery/service/ArxivDataSource.kt`
- `searchPapers`（`:32-36`）的 `keywordQuery` 计算改为三分支：keywords 非空 → 现有写法逐字不变；keywords 为空且 `arxivCategories(subjectScope)` 非空 → `cats.joinToString("+OR+") { "cat:$it*" }`；否则 → `"all:*"`（I4-2 的兜底）。
- 其余 URL 拼接、排序参数、限流、解析逻辑逐字不动。

**CORE 本轮不改**：其 `q` 参数是 `AND` 拼接（`CoreDataSource.kt:48-49`），把多个主题词以 OR 语义塞进去需要改写查询构造，风险与收益不匹配；`coreKeywords` 先在 catalog 中声明但不接线，留待后续。此为有意识取舍，须在代码注释中写明。

### Task 4：源启用（I4-3、I4-4）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`

`resolveEnabledSources`（`:201-216`）两处改动：

1. **修复 I4-4**：`:209` 改为条件加入——
   ```kotlin
   // I4-4: EuropePmcDataSource 是七源中唯一无 @ConditionalOnProperty 的（裸 @Service），
   // 故必须在此显式读 enabled，否则 EUROPE_PMC_ENABLED=false 对定时发现（sources 为空）无效。
   if (europePmcProperties.enabled) add({ europePmc }, europePmc.sourceName)
   ```
   需在构造函数中注入 `EuropePmcProperties`（追加在参数列表末尾）。
2. **实现 I4-3**：在 `add` 闭包的条件中追加 `&& name !in SubjectScopeCatalog.excludedSources(criteria.subjectScope)`；六行注册**全部保留**。

### Task 5：入口启用（I4-2、I4-5）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt`
- `:40-43` 的 criteria 构造追加 `subjectScope = SubjectScopeCatalog.RND_TARGET`。

修改文件：`src/main/resources/application.yml`
- 调整六个配额默认值：`EUROPE_PMC_MAX_PAPERS` 1500 → 1500（不动，由 scope 排除而非降配额，符合 I4-3）；`PMC_OA_MAX_PAPERS` 1000 → 1000（同）；`OPENALEX_MAX_PAPERS` 1200 → **2500**；`ARXIV_MAX_PAPERS` 200 → **800**；`CROSSREF_MAX_PAPERS` 500 → **300**；`CORE_MAX_PAPERS` 500 → 500（不动）。
- 只改 `${...:默认值}` 中的默认值，不改任何 `*Properties.kt`（I4-5）。

`ExpertDiscoveryController` 的两个 `/run` 端点**无需改动**：`/run` 已接受完整 `PaperSearchCriteria` body，新字段自动可覆盖；`/run/by-keyword` 走 `@RequestParam`，本轮不为其加参数（范围外：前端不加控件）。

### Task 6：研究检查点 —— **已于 2026-08-25 完成（CP-3）**

原检查点的四项全部有实测结论，Task 1 可直接开工，无需再次请求 OpenAlex：

1. 六个 field 的 id 与所属 domain：见 Task 1 的表格；六者全在 Physical Sciences（domain `3`）。
2. Health Sciences 的 domain id = `4`（本计划最终**不使用**，理由见 Task 1）。
3. 语法验证通过：`primary_topic.field.id:22|31|17|25|21|15` count = 1,473,809，抽查结果落在目标 field 内；
   与既有三段条件叠加后 count = 8,972,684，供给充足。
4. 初判未被推翻——多值 `|` 语法可用，无需退化到反向排除。

执行 agent 只需把上述数值逐字写进 `SubjectScopeCatalog.kt` 的文件注释，
注明「取数日期 2026-08-25，来源 docs/plans/2026-08-25/00-research-checkpoints.md 的 CP-3」。

**遗留待决（不阻塞本计划编码，但影响需求覆盖面）**：六个 field 全在 Physical Sciences 域，
制药研发（Pharmacology 等，位于 Health Sciences / Life Sciences 域）会被整体排除，
与原始需求「医学专业只要制药、器材研发这类」冲突。三个选项与补充实测命令见
主计划的 F-5 与 [00-research-checkpoints.md](./00-research-checkpoints.md) 的 CP-5。
需求方选定前，本计划按**选项 1（维持六个 field，制药列入范围外）**编码。

### Task 7：测试

修改文件：`src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt`
- `subjectScope = null` 时 `buildFilter` 输出与硬编码的改动前期望串逐字相等（I4-2 的锚点断言）。
- `subjectScope = RND_TARGET` 时输出包含 catalog 声明的全部片段，且原有三段（`is_oa`、`publication_year`、`country_code:!CN`）位置与内容不变。

修改文件：`src/test/kotlin/com/weibo/talentintroduction/discovery/service/ArxivDataSourceTest.kt`
- 三分支各一条断言；`subjectScope = null` 且无 keywords 时 URL 中含 `search_query=all:*`（I4-2）。
- keywords 非空时，`subjectScope` 不影响查询串（keywords 优先）。

修改文件：`src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`
- `europePmcProperties.enabled = false` 且 `criteria.sources` 为空 → `resolveEnabledSources` 结果**不含** `EUROPE_PMC`（I4-4 的核心断言，即当前缺陷的回归测试）。
- `europePmcProperties.enabled = true` 且 `subjectScope = null` → 结果与改动前一致（六源全在，I4-2）。
- `subjectScope = RND_TARGET` → 结果不含 `EUROPE_PMC` 与 `PMC_OA`，但含其余四源（I4-3）。
- 手动指定 `sources = listOf("EUROPE_PMC")` 且 `enabled = true` 且 `subjectScope = RND_TARGET` → 结果**不含** `EUROPE_PMC`（scope 排除优先于手动指定；此语义须由本测试固定下来）。

修改文件：`src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt`
- 断言定时任务构造的 criteria 中 `subjectScope == "RND_TARGET"`。

新增文件：`src/test/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalogTest.kt`
- 未知 scope 与 null 均返回空集合/空列表（I4-2）。
- `excludedSources(RND_TARGET)` 恰为两项。
- `ALLOWED` 与各函数的分支覆盖一致（防止新增 scope 时漏改某个函数）。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SubjectScopeCatalog.kt` | 新增；学科范围的唯一声明处 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperSearchCriteria.kt` | 尾部新增 `subjectScope` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt` | `buildFilter` 追加一行 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ArxivDataSource.kt` | `keywordQuery` 改三分支 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 注入 `EuropePmcProperties`；`resolveEnabledSources` 两处改动 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt` | criteria 追加 `subjectScope` |
| 7 | `src/main/resources/application.yml` | 三个配额默认值 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSourceTest.kt` | 两条断言 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ArxivDataSourceTest.kt` | 四条断言 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 四条断言 |

> **超出 10 个文件的两份测试**：`ExpertDiscoverySchedulerTest.kt` 与新增的 `SubjectScopeCatalogTest.kt`。
> 二者均为**纯新增断言/纯新增文件、零业务代码**，不增加 interaction surface。执行 agent 须一并交付，
> 验证方按本清单 + 这两份测试为准。若严格执行 10 文件上限导致这两份测试被省略，
> I4-1 与「定时任务确实启用了 scope」将失去自动验证手段。

子系统：1 个（发现链路），零前端改动，故无 `## 样式契约` 节。

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7 / Java 11 Maven 工程，**必须用 JDK 11（zulu-11）**，裸 `mvn` 会构建失败（`CLAUDE.md:7`）。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=SubjectScopeCatalogTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OpenAlexDataSourceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ArxivDataSourceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoverySchedulerTest

# 一次跑完本计划全部相关类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='SubjectScopeCatalogTest,OpenAlexDataSourceTest,ArxivDataSourceTest,ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest'

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：Maven 退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0`；`git diff --check` 无输出。

来源：`CLAUDE.md:5-27`（含 `-Dtest=Class` 与 `#method` 语法）。

## 验收标准

- I4-1: grep 证明 `SubjectScopeCatalog.kt` 之外的文件中，零命中 OpenAlex field id 数字与 arXiv 分类字符串（`cs.`、`eess.` 等）。
- I4-2: `OpenAlexDataSourceTest` 与 `ArxivDataSourceTest` 中的「改动前期望串」断言通过（这两条断言必须用硬编码字面量，不得用被测代码反算）。
- I4-3: grep 证明 `resolveEnabledSources` 中六行 `add(...)` 注册全部保留；单测断言手动指定被排除源时仍被排除。
- I4-4: 单测断言 `enabled = false` + 空 `sources` 时结果不含 `EUROPE_PMC`。
- I4-5: `git diff` 证明 `ArxivProperties.kt` / `OpenAlexProperties.kt` / `CrossrefProperties.kt` 中的 `maxPapersPerSource` 默认值未变，只有 `application.yml` 变化。
- 回归：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A4-1: EuropePMC 开关真正生效
- 前置条件: 测试环境当前 `EUROPE_PMC_ENABLED` 未设置（即默认 true）。
- 操作步骤: 1. 设 `EUROPE_PMC_ENABLED=false` 并重启；2. 打开「发现专家 → 深度发现」弹窗，确认 EUROPE_PMC 复选框为灰；3. 不勾选任何数据源直接启动一次发现（等价于定时任务的空 `sources`）；4. 查看任务执行日志开头的「启用平台=[...]」一行。
- 预期结果: 启用平台列表中**不含** `EUROPE_PMC`。修复前此处会含它——这是本条的核心。
- 覆盖: I4-4、需求描述第 3 条

### A4-2: 学科范围在定时任务上生效
- 前置条件: 应用已部署；可查看任务执行日志。
- 操作步骤: 1. 手动触发一次定时发现（或等 cron 到点）；2. 查看日志中的「启用平台=[...]」一行。
- 预期结果: 列表不含 `EUROPE_PMC` 与 `PMC_OA`，含 `OPENALEX`、`CROSSREF`、`CORE`、`ARXIV`（在各自 enabled 为 true 的前提下）。
- 覆盖: I4-3、需求描述第 1、2 条

### A4-3: 手动发现不受影响（回归）
- 前置条件: 部署前记录一次「深度发现」手动运行、勾选全部可用数据源时的启用平台列表与论文总数量级。
- 操作步骤: 1. 部署后打开弹窗，勾选全部可用数据源；2. 启动一次发现；3. 对比日志。
- 预期结果: 启用平台列表与部署前相同（含 EUROPE_PMC / PMC_OA，前提是它们 enabled）；说明手动路径未被 scope 影响。
- 覆盖: I4-2、必须保持不变第 5 条

### A4-4: 捞回来的人确实换了学科
- 前置条件: 子计划 01 已上线（否则无法按类型查看）；记录改动前一周新增 CANDIDATE 中「生产研发 + 学术科研 + 混合研发」的**绝对条数** B0。
- 操作步骤: 1. 本计划上线后运行两周；2. 在专家列表按 `candidateValidatedAt` 时间范围 + 三个可发信类型统计新增绝对条数 B1；3. 同时记录同期发现总数。
- 预期结果: **B1 > B0**。发现总数下降属预期（砍掉了 2500 篇生物医学配额且工程论文邮箱命中率更低），**不作为失败判据**。若 B1 ≤ B0，说明学科过滤没有换来目标人群，需回到 Task 6 检查 OpenAlex filter 是否真正生效。
- 覆盖: 需求描述第 1、2 条
- 注意: 统计前必须确认 `EXPERT_CLASSIFICATION_INCREMENTAL_ENABLED=true` 已开启，否则经 `promoteDiscoveredToCandidate`（`ExpertDiscoveryService.kt:770-786`）入库的文档无 `expertClassification`，会被类型筛选整批漏掉，导致 B1 被严重低估。

### A4-5: 未设 scope 时查询串未变（回归）
- 前置条件: 能抓取或从日志读到 OpenAlex 与 arXiv 的实际请求 URL。
- 操作步骤: 1. 通过 `/api/expert-discovery/run` 传一个不含 `subjectScope` 的 body 启动发现；2. 记录两个源的请求 URL。
- 预期结果: URL 与部署前逐字相同（参数顺序也相同）。
- 覆盖: I4-2、必须保持不变第 1 条

### A4-6: 邮箱抽取链路未受影响（回归）
- 前置条件: 同 A4-3。
- 操作步骤: 1. 完成一次手动发现；2. 对比任务结果中的 `totalPapers` / `totalAuthors` / `indexed` / `promoted` 四个数字的相互比例。
- 预期结果: 邮箱抽取成功率（`indexed / totalAuthors`）与部署前同数据源组合下处于同一量级。
- 覆盖: 必须保持不变第 2 条

人工验收开始时，从本节导出 `04-discovery-subject-scope-acceptance.md`；不得提前生成。
