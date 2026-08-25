# 研发人才门禁改造主计划

## 需求描述

可观察结果：

1. 专家列表页可按研发类型（`PRODUCTION_RND` 等六值 + 未分类）筛选，列表行显示类型标记。
2. 批量发送任务可在既有 `sendable=true` 硬门禁之内，按研发类型进一步限定收件范围；默认不限，上线当天发送行为零变化。
3. 快速晋升（RAW→CANDIDATE）在晋升时现算分类，挡掉 `SERVICE_ONLY` / `OUT_OF_SCOPE`，并把分类结果写入 CANDIDATE 文档。
4. 深度发现按学科范围取数，不再把一半配额投在生物医学库；`EUROPE_PMC_ENABLED=false` 真正生效。

必须保持不变：

- 计划 03（`docs/plans/2026-08-24/03-expert-rnd-send-gate.md`）已落地的 INTRODUCTION 硬门禁语义逐字不变：`ExpertSearchService.expertSendableFilter()`（`:55-63`）与 `RecipientScope.matchesExpert` 的分类判定（`BatchExecutionModels.kt:65-69`）不得被新增筛选替代或削弱。
- MATERIAL_REMINDER 全链路零影响。
- `ExpertClassificationService` 的词表、分数、阈值、`VERSION`（`:220 = "rnd-v2-2026"`）一律不改。
- 既有 `discipline` 筛选维度行为不变。
- 现有专家的姓名、邮箱、`employment`、`institution`、`tags`、`operatorStatus` 不被本轮任何写路径覆盖。

范围外：

- SBIR/STTR 数据源接入（见下方「已识别但本轮不做」）。
- PatentsView / Lens / CORDIS 接入、邮箱补全服务。
- RAW 层全量分类回填（被子计划 03 的现算方案取代）。
- 修改 `OpenAlexDataSource.resolveDisciplineCategory`（`:298-309`）的 STEM/HUMANITIES 归类逻辑。
- 修改 `PaperMetadata` / `PaperAuthor` 的字段集合。
- 邮件模板、发件节奏、配额、退订、抑制。

## 关键不变量

### Invariant M-1: 硬门禁不可被筛选替代
- Rule: 新增的 `expertTypes` 是**可选筛选**，永远与 `expertSendableFilter()` / `matchesExpert` 的分类硬门禁**并存且为 AND 关系**；`expertTypes` 为空集合时不得追加任何 ES filter，也不得跳过硬门禁。
- Applies to: `ManualInitialOutreachService.buildEsFiltersForLevel`（`:1296-1327`）、`RecipientScope.matchesExpert`（`BatchExecutionModels.kt:62-121`）。
- Violation consequence: 运营勾选任意类型即绕过版本校验（硬门禁额外校验 `version == VERSION`，筛选不校验），策略升版后旧分类结果继续放行。
- 来源: original

### Invariant M-2: 分类语义单一来源
- Rule: 类型枚举值、`sendable` 派生关系、评分与证据只由 `ExpertClassificationService` 与 `ExpertClassification`（`ExpertClassification.kt:19-46`）产出；本轮所有新代码只消费结果，不得复制关键词、阈值或重新推断类型。
- Applies to: 子计划 01/02/03 的全部新增代码。
- Violation consequence: 同一专家在列表、批量、晋升三处得到不同类型。
- 来源: original（同源于 K-expert-classification-one-object-three-layers）

### Invariant M-3: 空集合 = 不限
- Rule: 所有新增多值筛选参数，空集合必须返回 `null` / 不追加 filter；禁止产出 `should: [] + minimum_should_match: 1`。
- Applies to: `ExpertSearchService.expertTypesFilter`（新增）、`RecipientScope.matchesExpert`、`ExpertSearchService` 四个查询构造点。
- Violation consequence: 所有"不限"的存量任务静默命中 0 条、停发。
- 来源: K-batch-multi-value-filter-seams

### Invariant M-4: 晋升端宽档、发信端严档
- Rule: 晋升门禁只排除 `SERVICE_ONLY` 与 `OUT_OF_SCOPE` 两个类型；`UNKNOWN` 必须放行进 CANDIDATE。发信端维持"仅 `sendable=true`"。两处口径不得互相照搬。
- Applies to: 子计划 03 的 `ExpertRevalidationService.promoteEligibleRawExperts`。
- Violation consequence: RAW 层字段稀疏（`ExpertDiscoveryService.toIndexMap:752-767` 只写 `employment`/`institution`/`lastPublicationYear` 三个可参与打分的字段），严档会把企业研发人员整类误杀，且被挡回的文档永不 enrich（`buildEnrichmentFilters:820-822` 只扫 CANDIDATE 且 `must_not prefix orcidId "EMAIL-"`）。
- 来源: original

### Invariant M-5: 晋升时现算，不读 ES 旧值
- Rule: 子计划 03 的判定输入必须是当次从 RAW 读出的 `ExpertProfile` 现场 `classify()` 的结果，禁止读取 `profile.expertClassification`。写入 CANDIDATE 时必须复用 `ExpertIndexWriterService` 的既有序列化函数 `classificationNode`（`:352-363`），禁止另写一份。
- Applies to: 子计划 03。
- Violation consequence: RAW 层几乎无 `expertClassification`（增量调度 `ExpertClassificationScheduler:50` 只跑 CANDIDATE，且 `application.yml:33 incremental-enabled` 默认 false），读旧值等于全量安全失败；两份序列化会在 ES 里产生两种形状的同名对象。
- 来源: original

### Invariant M-6: 发布与行为变更分离
- Rule: 子计划 02 的筛选默认值为空集合；子计划 03 的晋升门禁受一个默认关闭的配置开关控制；子计划 04 的 `subjectScope` 在定时任务上显式启用、在手动接口可覆盖。四份子计划任一单独发布后，若不改配置，线上行为必须逐字不变。
- Applies to: 子计划 02、03、04。
- Violation consequence: 发布即改变运营天天观察的指标（晋升数、发现数、发送数），无法归因。
- 来源: original

## 现状审计

本节只记录跨子计划共享的事实；逐存储明细见各子计划。

### 分类结果对象
- Schema/mapping: 三份 `src/main/resources/es/orcid_info_*.json` 均 `dynamic:false`；`expertClassification` 顶层对象已由 2026-08-24 计划 01 声明。
- 语义源: `ExpertClassification.kt:19-46`（六值枚举 + `sendable` 只读派生 getter + `SENDABLE_TYPES` 前三类）；`ExpertClassificationService.kt:220` `VERSION = "rnd-v2-2026"`、`:222-223` 双阈值 50。
- Read paths: `ExpertSearchService.toExpertProfile` / `sourceFields`（`:588` 已含该字段）是唯一反序列化 seam。
- Interaction points: 列表读（子计划 01）、批量读（子计划 02）、晋升写（子计划 03）三者共用同一对象。

### 已落地的发信硬门禁
- `ExpertSearchService.expertSendableFilter()`（`:55-63`）：`sendable == true` **AND** `version == VERSION` 两个 term。
- `ManualInitialOutreachService.buildEsFiltersForLevel:1323-1325`：仅 `mailType == INTRODUCTION` 时追加，MATERIAL_REMINDER 不追加。
- `RecipientScope.matchesExpert`（`BatchExecutionModels.kt:65-69`）：内存侧同口径，`classification?.sendable != true || classification.version != VERSION` 即拒绝。
- Interaction points: 本轮新增筛选必须落在这两处**之外并与之 AND**，不得改写它们。

### 分类打分的输入字段可得性（决定 M-4）
- `ExpertDiscoveryService.toIndexMap:752-767` 写入 RAW 的字段中，能参与打分的只有 `employment`、`institution`（二者同为 affiliation 原串，见 `:744`）与 `lastPublicationYear`；`keyword` 恒为 null（`:744`），`researchFields`/`recentWorkTitles`/`patentTitles`/`hIndex`/`worksCount` 均不写入。
- 补齐这些字段的唯一路径是 `ExpertDiscoveryService.enrichExistingExperts:845-877`，它只扫 CANDIDATE（`:850`、`:877`），且 `buildEnrichmentFilters:820-822` 带 `must_not prefix orcidId "EMAIL-"`。
- `patentTitles` 在批量 enrich 路径受 `OpenAlexDataSource.kt:240` 的 `needsWorksOrPatents` 与 `:253` 的 `properties.fetchPatentsEnabled` 双重门控，后者 `application.yml:176`、`OpenAlexProperties.kt:21` 默认 `false`。（来源: K-openalex-fetch-works-gated）
- **2026-08-25 实测（CP-2）：即使打开该开关也永远拿不到值。** `works?filter=type:patent` 返回 `meta.count = 0`，且 `/types` 列表中**根本没有 `patent` 这个 work type`。`productionScore` 中权重最高的 `PROD_PATENTS` +45（`ExpertClassificationService.kt:108-111`）在本系统中**恒不可得**。详见「实测结论」F-1。
- Interaction points: 上述三条共同证明「RAW 层的 `UNKNOWN` 含义是尚未取数」，是 M-4 的证据基础。

### 晋升写路径
- `ExpertIndexWriterService.promoteToCandidate` / `promoteToApplication` 通过 `_source` 全量逐字段复制，新增 ES 字段自动透传 RAW→CANDIDATE→APPLICATION，晋升代码零改动。（来源: K-promotion-source-passthrough，本轮 grep 复核仍成立）
- `ExpertRevalidationService.promoteRawToCandidate:241-261` 是快速晋升专用写路径，`rawDoc.toMutableMap()` 后叠加 `candidateValidatedAt`/`updatedAt`/`tags` 三个键。
- Interaction points: 子计划 03 在此叠加第四个键 `expertClassification`。

## 实测结论（2026-08-25，原始输出见 [00-research-checkpoints.md](./00-research-checkpoints.md)）

### F-1：OpenAlex 没有专利数据 —— P0 动作反转

`works?filter=type:patent` → `meta.count = 0`；`/types` 端点的类型列表中**不存在 `patent`**。

**原 P0「打开 `OPENALEX_FETCH_PATENTS_ENABLED`」作废，改为「明确不要打开」**：
开了不会带来任何 `patentTitles`，只让每位专家多一次 OpenAlex 请求加一次
`enrichmentDelayMs` 等待（`OpenAlexDataSource.kt:240-253`），纯成本无收益。

三条连带后果：

1. **`PROD_PATENTS` +45 恒不可得** → `productionScore` 实际可达上限从 100 降到 55
   （`PROD_ROLE` +35 + `PROD_THEME` +20 + `PROD_COMPANY` +15 + `PROD_WHITELIST` +15 中，
   前两项是过阈值的必要条件）。**`PRODUCTION_RND` 是罕见类型，不是常见类型。**
   → 修正子计划 01 的验收前置条件（见该文件 A-1）。
2. **子计划 03 的宽档成为唯一可行方案。** 原本还能论证「先开专利开关、数据补齐后再考虑严档」，
   这条退路现在不存在。→ 写入子计划 03 的 I3-1 证据。
3. **「有专利」筛选恒为 0 命中，且是一颗地雷。** `index.html:570` 的 chip 是死控件；
   更危险的是 `patentTitles` 位于 `ExpertSearchService.ALLOWED_HAS_FIELDS`（`:35`）之内，
   一旦某邮件模板使用 `${patentTitle}` 占位符，`requiredEsFields` 会把它算进 `gateEsFields`
   （`ManualInitialOutreachService.resolveScope:432-442`），经 `fieldPresenceFilters` 变成
   ES 的 `exists patentTitles`，使该任务**收件人恒为 0 且无任何报错**。
   现存迁移与模板中暂无 `${patentTitle}` 使用（grep 零命中）。**本轮不修**（超范围），
   已记入知识库 `K-openalex-has-no-patent-data`。

### F-2：OpenAlex 学科 ID 实测值（2026-08-25）

domain：Life Sciences `1`、Social Sciences `2`、Physical Sciences `3`、**Health Sciences `4`**

| field | id | field | id |
|---|---|---|---|
| Chemical Engineering | `15` | Energy | `21` |
| Computer Science | `17` | Engineering | `22` |
| Materials Science | `25` | Physics and Astronomy | `31` |

语法验证：正向多值锁定 `primary_topic.field.id:22|31|17|25|21|15` **可用**
（3-3 count = 1,473,809，抽查结果落在目标 field 内）；反向排除
`primary_topic.domain.id:!4` 亦可用（3-4 count = 5,263,473）；
与既有三段条件叠加后 3-5 count = 8,972,684，样本量充足，
子计划 04 把 `OPENALEX_MAX_PAPERS` 上调到 2500 无供给风险。

**推论**：上述六个 field 全部隶属 Physical Sciences（domain `3`），
因此正向锁定**已隐含排除** Health Sciences，无需再叠加 `domain.id:!4`。
子计划 04 只保留正向锁定一段，反向排除删去。

### F-3：线上资格开关无阻塞

`candidate.requireOrcid` / `candidate.requireDoctoralDegree` /
`academic.enableHIndexFilter` / `academic.enableActivityFilter` 实测**均为 `false`**。
→ 无 ORCID、无学位、无 hIndex、无发表年份的数据源（含 SBIR）不会在
`CandidateEligibilityService.evaluateEligibility:22-51` 被拒。该层阻塞解除。

### F-4：SBIR 可用性仍未知，但失败已定性为出网问题

CP-0 对照：`api.crossref.org` 200 / `api.openalex.org` 200 / `www.ebi.ac.uk` 200 /
**`api.www.sbir.gov` TLS 握手被掐断（`HTTP_STATUS=000`）**。
**这是出网白名单问题，不是 SBIR 服务端问题。**
在把该域名加进允许清单并重跑 CP-1 之前，**不得**据此选择路径 C。

### F-5：待需求方决策 —— 六个 field 把制药/器械研发整体排除了

原始需求是「如果是医学专业，只需要制药、器材研发这类」，
`ExpertClassificationService` 也为此保留了 `PHARMA_WHITELIST_TERMS`（`:287-294`）与
`DEVICE_WHITELIST_TERMS`（`:296-299`）两个正向白名单。

但 F-2 的六个 field 全在 Physical Sciences 域，
**Pharmacology / Biochemistry / Immunology 等制药研发的主阵地位于 Health Sciences 与 Life Sciences 域，会被整体排除**。
后果：分类器的制药/器械白名单永远不会被触发——因为这类论文根本不会被抓取。
医疗器械稍好（`biomedical engineering` 类主题多归在 Engineering `22` 之下），制药则基本归零。

三个选项，需要需求方选一个后子计划 04 才能定稿：

- **选项 1（最小改动）**：维持六个 field。承认本轮不覆盖制药研发，写进「范围外」。
- **选项 2**：加入 Pharmacology, Toxicology and Pharmaceutics 一个 field。
  代价是会带进大量临床药理，但它们会被分类器的 `OUT_OF_SCOPE` 规则挡在发信之外
  （命中医学域且无制药白名单），属于「抓进来但不发信」，浪费抓取配额而非污染发信池。
- **选项 3**：按 `primary_topic.subfield.id` 做更细的锁定，只取制药研发相关 subfield。
  最精确但需要再跑一次实测拿 subfield 清单，且 subfield 数量多、维护成本高。

选项 2 需要补一次实测：取 Pharmacology 那个 field 的 id 与叠加后的 count。
命令见 [00-research-checkpoints.md](./00-research-checkpoints.md) 新增的 CP-5。

---

## 实现方案

按顺序执行，每份独立验证、独立发布：

1. [01-expert-list-type-filter.md](./01-expert-list-type-filter.md) — 列表筛选与展示。纯读路径，无迁移。遵守 M-2、M-3。**必须最先**：后续三份的效果观察全部依赖它。
2. [02-batch-send-type-filter.md](./02-batch-send-type-filter.md) — 批量发送类型筛选。含 V100 迁移。遵守 M-1、M-2、M-3、M-6。
3. [03-promotion-classification-gate.md](./03-promotion-classification-gate.md) — 晋升端宽档现算门禁。遵守 M-2、M-4、M-5、M-6。
4. [04-discovery-subject-scope.md](./04-discovery-subject-scope.md) — 发现源学科范围 + EuropePMC 开关缺陷修复。遵守 M-6。

共享文件跨子计划出现，执行 agent **禁止并行修改**：

| 文件 | 出现于 | 顺序约束 |
|---|---|---|
| `ExpertSearchService.kt` | 01（新增 `expertTypesFilter` + 四个查询构造点）、02（仅调用） | 01 必须先落地并合并，02 才能开始 |
| `app.js` / `index.html` | 01（专家列表筛选区）、02（批量配置两处面板） | 区域不重叠，但仍需 01 先合并以避免冲突 |

发布顺序建议：01 → 04 → 02 → 03。理由：01 提供观察手段；04 先改变进池的人（见效需两周）；02 在有东西可筛之后上线；03 风险最高、放最后并带开关。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| 无 | 主计划不直接授权业务文件；以四份子计划各自的穷举清单为准 |

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7 / Java 11 Maven 工程，**必须用 JDK 11（zulu-11）**，裸 `mvn` 会构建失败（`CLAUDE.md:7`）。前端 JS 测试由 `exec-maven-plugin` 在 `test` 阶段执行（`pom.xml:186-232`），也可脱离 Maven 单跑。

```bash
# 全量测试（回归门禁；含 node-test / node-check 三个 execution）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建 WAR
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 单个测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest

# 单个测试方法
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest#methodName

# 前端 JS 测试（无需 JAVA_HOME，node v22 实测通过）
node --test src/test/js/*.test.js

# 前端语法检查
node --check src/main/resources/static/app.js

# 空白/换行卫生
git diff --check
```

通过判据：Maven 退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0`；node 退出码 0 且输出含 `# fail 0`；`git diff --check` 无输出。

来源：`CLAUDE.md:5-27`（Maven 命令与 JDK 要求）；K-js-tests-run-via-exec-plugin（node 命令，2026-08-19 实测）。

## 验收标准

- M-1: grep 证明 `buildEsFiltersForLevel` 中 `expertSendableFilter()` 调用行逐字保留；自动测试证明 `expertTypes` 非空时两个 filter 同时出现在 filter 数组中。
- M-2: grep 证明 `ExpertType` 枚举名与两个阈值常量只出现在 `ExpertClassification.kt` / `ExpertClassificationService.kt` 及其测试中，本轮新增文件零命中。
- M-3: 自动测试证明 `expertTypesFilter(emptyList())` 返回 `null`，且四个查询构造点在空集合时 filter 数组长度不变。
- M-4: 自动测试证明 `UNKNOWN` 在晋升判定中放行、`SERVICE_ONLY`/`OUT_OF_SCOPE` 被拒。
- M-5: grep 证明子计划 03 的新代码零命中 `profile.expertClassification`，且 `classificationNode` 在仓库中仍只有一处定义。
- M-6: 自动测试证明默认配置下 `buildEsFiltersForLevel` 输出与改动前逐字相同、晋升开关默认 false、`subjectScope` 为 null 时各源查询串与改动前相同。
- 回归：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 四份子计划任一发布后行为零变化
- 前置条件: 测试环境已有存量 CANDIDATE 与批量任务配置；不改任何环境变量与后台配置。
- 操作步骤: 1. 依次部署 01→04→02→03；2. 每次部署后请求一次 INTRODUCTION 收件人预估；3. 各跑一次快速晋升与深度发现。
- 预期结果: 四次预估数字两两相同；晋升数与部署前同量级；深度发现启用平台列表与部署前一致。
- 覆盖: M-6、必须保持不变第 1、2 条

### A-2: 硬门禁未被削弱
- 前置条件: 存在一名 `expertClassification.version` 为旧值（非 `rnd-v2-2026`）但 `type=PRODUCTION_RND` 的 CANDIDATE，且有有效邮箱。
- 操作步骤: 1. 在批量发送配置里勾选「生产研发」；2. 请求收件人预估；3. 执行一次大小为 1 的批量首发。
- 预期结果: 预估不含该专家；该专家无新增 OUTBOUND/INTRODUCTION/SENT 记录。
- 覆盖: M-1

### A-3: 材料提醒回归
- 前置条件: 存在一名 APPLICATION 联系人，标签「承诺回复材料」，`expertClassification` 字段缺失。
- 操作步骤: 1. 请求 MATERIAL_REMINDER 预估；2. 执行一次材料提醒。
- 预期结果: 该联系人仍计入预估并成功发送。
- 覆盖: 必须保持不变第 2 条

人工验收开始时，从本节导出 `00-rnd-gate-master-acceptance.md`；不得提前生成。

## 已识别但本轮不做：SBIR 接入的阻塞问题

方案评审时列为改动点 5 的 SBIR/STTR 数据源接入，在本轮研究中被**代码证据否决了原定映射**，需求方决策前不写计划。

三条证据：

1. `ExpertDiscoveryService.buildEnrichmentFilters:820-822` 带 `must_not: prefix orcidId "EMAIL-"`。SBIR 的 PI 无 ORCID，文档 id 走 `ExpertIdGenerator:16` 的 `EMAIL-<hash>`，因此**永远不会被 OpenAlex enrichment 补字段**。其分类只能靠 SBIR 自身写入的字段决定，且永不改变。
2. `MailPlaceholderService.kt:135` 把 `recentWorkTitle` 的标签定义为**"近期论文标题"**，`:158` 映射到 ES 字段 `recentWorkTitles`，`MailVariableService.kt:144` 将其注入对外邮件。把 SBIR 的 award title 写进该字段，会让介绍邮件对真实收件人声称一篇不存在的论文。同理 `lastPublicationYear` 标签为"最近发表年份"（`:133`）、`employment` 为"职位"（`:130`）。**这三个字段不能承载 award 语义。**
3. 在只允许写 `employment`/`institution`（公司名）与 `keyword`（research keywords）的前提下，按 `ExpertClassificationService.productionScore:104-129` 逐项计分：`COMPANY_TERMS` +15（公司名含 `inc`/`ltd` 等，`:316-320`）+ `PRODUCTION_THEME_TERMS` +20（keyword 含 `manufacturing` 等，`:310-313`）= **35 < 50**；`PRODUCTION_ROLE_TERMS` +35 需 `employment` 含 `engineer`/`design` 等岗位词（`:303-307`），公司名通常不含；`patentTitles` +45 不可得。结论：**SBIR 来源的专家在现有评分下恒为 `UNKNOWN`，永远进不了发信池。**

### 2026-08-25 补充：官方字段清单已确认，路径 A 出局

`https://www.sbir.gov/api` 文档给出的 awards 端点返回字段全集（逐字）：

```
firm, award_title, agency, branch, phase, program, agency_tracking_number, contract,
proposal_award_date, contract_end_date, solicitation_number, solicitation_year, topic_code,
award_year, award_amount, duns, uei, hubzone_owned, socially_economically_disadvantaged,
women_owned, number_employees, company_url, address1, address2, city, state, zip,
poc_name, poc_title, poc_phone, poc_email,
pi_name, pi_phone, pi_email,
ri_name, ri_poc_name, ri_poc_phone,
research_area_keywords, abstract, award_link
```

三条关键读数：

1. **没有 `pi_title`。** PI 只有 `pi_name` / `pi_phone` / `pi_email` 三个字段。**路径 A 不成立** ——
   不存在可写入 `employment` 的 PI 职位数据，`PRODUCTION_ROLE_TERMS` 的 +35 拿不到。
2. **有 `poc_title`，但那是另一个人。** POC 是公司业务联系人，`poc_email` 与 `pi_email` 是两个不同地址。
   把 POC 的头衔当作 PI 的职位写入 `employment`，属于张冠李戴，与
   [[K-mail-placeholder-labels-are-semantic-contracts]] 记录的是同一类错误——
   `employment` 的对外标签是「职位」，会直接进邮件正文。**禁止**。
3. **有 `research_area_keywords` 与 `abstract`。** 前者可正当写入 `keyword` 与 `researchFields`。

按可正当写入的字段重算（`productionScore:104-129`）：
`COMPANY_TERMS` +15（firm 含 inc/ltd/corp/company）+ `PRODUCTION_THEME_TERMS` +20
（research_area_keywords 含 product/engineering/manufacturing/production）= **35 < 50**。
仅当关键词恰好命中制药/器械白名单时再 +15 = 50 险过，即只有 medtech/pharma 类 SBIR 公司能过阈值，
而那恰恰是本轮最不想要的那类。**结论不变：SBIR 来源的专家在现有评分下基本恒为 `UNKNOWN`。**

另注：`ri_name`（STTR 的研究机构合作方）虽能命中 `RESEARCH_INSTITUTION_TERMS` +20，
但它是合作机构而非 PI 的雇主，写入 `institution` 同样是张冠李戴，不采纳。

**收件人字段取 `pi_email` 而非 `poc_email`** —— PI 是技术负责人，POC 多为行政/商务。

### 剩余两条路径

- **路径 B**：为 `ExpertClassificationService` 增加一条生产分证据——「获得政府 SBIR/STTR R&D 资助的企业 PI」。
  论据是这条信号的强度不低于专利（政府评审过的商业化研发资助），建议权重取 +45（与 `PROD_PATENTS` 同级），
  依据 `dataSource == "SBIR"` 判定。代价：突破本主计划「不改分类器」的范围外声明，需单开一份计划、
  重跑 `ExpertClassificationServiceTest` 全部用例，并确认 `sourceFingerprint`（`:180-186`）的输入集合是否需要纳入 `dataSource`
  （目前指纹只覆盖六个文本字段 + 三个数值字段，不含 `dataSource`，加规则后同一输入的指纹语义会变，须一并评估是否升 `VERSION`）。
- **路径 C**：本轮不接 SBIR，先执行 01-04，用子计划 04 的两周效果数据再评估。

**API 可用性仍未实测**（2026-08-25 更新）：两次尝试均未触达服务端——抓取工具侧 403，
执行 agent 侧 DNS 失败后转 TLS 失败（`HTTP_STATUS=000`）。CP-0 对照实验已证明这是
**出网白名单问题**（见 F-4），不是 SBIR 服务端问题。
前置动作：把 `api.www.sbir.gov` 加进出网允许清单，然后重跑 CP-1。

**CP-4 已解除一层阻塞**（见 F-3）：四个资格开关实测均为 `false`，
SBIR 数据不会在晋升处被全量拒绝。

**路径 B 的相对优先级因 F-1 上升**：OpenAlex 专利数据为空，意味着系统失去了唯一的
「企业研发」强信号（`PROD_PATENTS` +45），`PRODUCTION_RND` 沦为罕见类型。
若 CP-1 证明 SBIR 可用，它将是唯一能带来企业属性的数据源。
但这仍是需求方的决策，本计划不代选。

无论选哪条，`GET /api/experts/eligibility-filters` 返回的线上 `requireOrcid` / `requireDoctoralDegree` 真实值必须先核对（代码默认均为 `false`，见 `CandidateFilterProperties.kt:9-10`，但真实值存于 `eligibility_filter_setting` 表）；任一为 `true` 则 SBIR 数据在 `CandidateEligibilityService.evaluateEligibility:22-24` 即被全量拒绝。
