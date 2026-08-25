# 子计划 03：快速晋升的宽档分类门禁（现算）

> **风险等级最高**：本计划是四份子计划中唯一改变既有运营指标（晋升数）的一份，因此强制带默认关闭的开关。
> 建议在子计划 04 见效之后再打开开关。

## 需求描述

可观察结果：

1. 快速晋升（RAW→CANDIDATE 扫描）在晋升每一名专家前，用当次读出的 `ExpertProfile` 现场计算分类；类型为 `SERVICE_ONLY` 或 `OUT_OF_SCOPE` 的专家不再被晋升，并在任务进度的 `filterReasons` 中以 `CLASSIFICATION:SERVICE_ONLY` / `CLASSIFICATION:OUT_OF_SCOPE` 计数。
2. 被晋升的专家，其 CANDIDATE 文档带上本次计算出的 `expertClassification` 对象。
3. 上述门禁受配置项 `talent-introduction.expert-classification.promotion-gate-enabled` 控制，**默认关闭**；关闭时晋升行为与改动前逐字相同，但第 2 条的分类写入仍然生效。

必须保持不变：

- `CandidateEligibilityService.evaluateEligibility`（`CandidateEligibilityService.kt:19-54`）的判据、顺序与 reject reason 文案零变化。
- `promoteEligibleRawExperts` 中既有的存在性检查、邮箱校验、取消检查、进度上报与统计字段语义不变。
- `promoteRawToCandidate` 对 `rawDoc` 的逐字段复制不变；`candidateValidatedAt` / `updatedAt` / `tags` 三个既有叠加键不变。
- `ExpertClassificationService` 的词表、分数、阈值、`VERSION` 不改。
- `EXPERT_REVALIDATION`（重新验证候选人）任务链路零影响。

范围外：

- RAW 层全量分类回填（被本计划的现算方案取代）。
- 修改 `CandidateEligibilityService`（它同时被发现链路的入库判定复用，改动会连带改变新发现专家的行为）。
- 修改 `ExpertClassificationScheduler` 的层级或默认值。
- 前端展示晋升任务的分类拒绝原因明细（`filterReasons` 已随既有进度接口下发，UI 侧本轮不动）。
- 对 `UNKNOWN` 专家做任何清理或降级。

## 关键不变量

### Invariant I3-1: 宽档——只排除证据充分的两类
- Rule: 门禁只在类型为 `SERVICE_ONLY` 或 `OUT_OF_SCOPE` 时拒绝。`UNKNOWN` 必须放行。禁止写成 `if (!classification.sendable) reject`。
- Applies to: `ExpertRevalidationService.promoteEligibleRawExperts`。
- Violation consequence: RAW 层可参与打分的字段只有 `employment` / `institution`（二者同为 affiliation 原串，`ExpertDiscoveryService.kt:744`）与 `lastPublicationYear`（`:745`），`keyword` 恒为 null、`researchFields`/`recentWorkTitles`/`patentTitles`/`hIndex`/`worksCount` 均不写入（`toIndexMap:752-767`）。按 `productionScore:104-129` 与 `researchScore:131-169` 计分，企业 affiliation 只能拿到 `COMPANY_TERMS` +15 与 `RESEARCH_RECENT_PUBLICATION` +35，两项均不过 50 阈值 → 恒为 `UNKNOWN`。严档会把企业研发人员整类误杀。
  **2026-08-25 实测补强（CP-2）**：`PROD_PATENTS` +45 这条退路也不存在——
  `works?filter=type:patent` 返回 `meta.count = 0`，OpenAlex 的 `/types` 列表中根本没有 `patent` 类型。
  因此「先打开 `OPENALEX_FETCH_PATENTS_ENABLED` 补齐专利数据，再考虑严档」这条论证被实测否决，
  **宽档是唯一可行方案**，不存在后续收紧的技术前提。
- 来源: original

### Invariant I3-2: 被拒不可逆，故只拒证据充分者
- Rule: I3-1 的宽档口径不得以「以后可以再补」为由收紧。被挡回 RAW 的文档**永远不会被补数据**。
- Applies to: 同上。
- Violation consequence: `ExpertDiscoveryService.enrichExistingExperts:845-877` 只扫 `ExpertIndexLevel.CANDIDATE`（`:850`、`:877`），且 `buildEnrichmentFilters:800-826` 带 `must_not: prefix orcidId "EMAIL-"`（`:820-822`）；`ExpertClassificationScheduler:50` 的增量分类同样只跑 CANDIDATE 且默认关闭（`application.yml:33`）。三者共同导致 RAW 中被拒者永久失联。
- 来源: original

### Invariant I3-3: 现算，不读 ES 旧值
- Rule: 判定输入必须是 `expertClassificationService.classify(profile)` 的当次返回值，其中 `profile` 是本批次从 RAW 读出的对象。禁止读取 `profile.expertClassification`。
- Applies to: `ExpertRevalidationService.promoteEligibleRawExperts`、`promoteRawToCandidate`。
- Violation consequence: RAW 层几乎无 `expertClassification` 字段，读旧值等于全量拒绝或全量放行，取决于安全失败方向；两者都错。
- 来源: original

### Invariant I3-4: 序列化单一实现
- Rule: 写入 CANDIDATE 文档的 `expertClassification` 必须由 `ExpertIndexWriterService` 中既有的 `classificationNode`（`:352-363`）产出。该函数当前是 `private`，需改为可被同模块调用（`internal` 或提升到伴生对象）。禁止在 revalidation 侧另写一份 Map/JSON 构造。
- Applies to: `ExpertIndexWriterService.kt`、`ExpertRevalidationService.promoteRawToCandidate`。
- Violation consequence: 两份序列化一旦漂移，ES 中出现两种形状的同名对象，`toExpertProfile` 解析失败即触发发信端安全失败。
- 来源: original（同源于 K-expert-classification-one-object-three-layers）

### Invariant I3-5: 门禁可关，写入不可关
- Rule: 配置开关只控制「是否拒绝 `SERVICE_ONLY`/`OUT_OF_SCOPE`」；无论开关状态，被晋升的文档都必须带上分类对象。
- Applies to: `ExpertRevalidationService.promoteEligibleRawExperts`。
- Violation consequence: 若写入也随开关关闭，则开关关闭期间晋升的专家在 CANDIDATE 层无分类，发信端安全失败把它们全挡住，可发送池反而缩小——与"关闭开关 = 零行为变化"的承诺矛盾。
- 来源: original

### Invariant I3-6: 判定位置在资格之后、写入之前
- Rule: 分类判定必须插在 `evaluateEligibility` 判定通过之后、`documentExistsInIndex` 存在性检查之前。
- Applies to: `promoteEligibleRawExperts` 的循环体（`:148-191`）。
- Violation consequence: 放在资格之前会为必然被拒的文档白算分类；放在存在性检查之后会为已在 CANDIDATE 的文档重复计算并污染 `filterReasons` 计数。
- 来源: original

## 现状审计

### RAW ES —— 快速晋升的读路径
- Schema/mapping: `src/main/resources/es/orcid_info_raw.json`，`dynamic:false`。
- Read path: `ExpertSearchService.scrollExperts(ExpertIndexLevel.RAW)`（调用点 `ExpertRevalidationService.kt:140`）→ `toExpertProfile` 反序列化。
- 字段可得性（决定 I3-1，逐行证据）:
  - `ExpertDiscoveryService.kt:740-748` 构造 RAW 用的 `ExpertProfile`：`keyword = null`、`employment = authorEmail.affiliation`、`institution = authorEmail.affiliation`、`lastPublicationYear = paper.pubYear`。
  - `ExpertDiscoveryService.toIndexMap:752-767` 写入 ES 的键：`orcidId`/`email`/`givenNames`/`familyNames`/`country`/`keyword`/`employment`/`institution`/`lastPublicationYear`/`emailSource`/`emailVerifiedLevel`/`dataSource`/`externalIds`/`discoveredAt`/`updatedAt`/`filterResult`/`filterRejectReason`/`tags`。**无** `researchFields`、`recentWorkTitles`、`patentTitles`、`hIndex`、`worksCount`、`citationCount`。
  - 补齐这些字段的唯一写路径是 `ExpertDiscoveryService.kt:1093-1096`（`enrichment.topics → researchFields` 等），由 `enrichExistingExperts` 驱动，而后者只扫 CANDIDATE。
- Interaction points: RAW 字段稀疏 × 分类器阈值 50 → `UNKNOWN` 的语义是「尚未取数」。这是 I3-1/I3-2 的全部依据。

### CANDIDATE ES —— 快速晋升的写路径
- Write path: `ExpertRevalidationService.promoteRawToCandidate:241-261`。逐字流程：`readRawDocument(docId)` → `rawDoc.toMutableMap()` → `put("orcidId", docId)` / `put("candidateValidatedAt", now)` / `put("updatedAt", now)` / `put("tags", existingTags + "auto_promoted")` → `expertIndexWriterService.writeCandidateDocument(docId, doc)`。
- 其他写路径（本计划不碰）：`ExpertIndexWriterService.bulkUpdateExpertClassifications`（回填）、`promoteToCandidate`/`promoteToApplication`（`_source` 全量复制，新字段自动透传，来源: K-promotion-source-passthrough）、`ExpertDiscoveryService.promoteDiscoveredToCandidate:770-786`（发现时直接晋升，**本计划不覆盖此路径**）。
- Read paths: `ExpertSearchService.sourceFields/toExpertProfile`（唯一反序列化 seam）；发信端 `expertSendableFilter`；子计划 01 的列表展示。
- Interaction points:
  - 本计划写入 `expertClassification` → 子计划 01 的列表 chip 读出 → 发信端硬门禁读出。
  - `promoteDiscoveredToCandidate` 路径**不带**分类，其产出的 CANDIDATE 文档仍需靠增量分类任务补齐。这是「效果评估必须先开 `EXPERT_CLASSIFICATION_INCREMENTAL_ENABLED`」的原因，写入主计划的执行顺序。

### 分类服务
- `ExpertClassificationService.classify(profile)` 是确定性纯函数：`VERSION = "rnd-v2-2026"`（`:220`）、`RECENT_PAPER_CUTOFF_YEAR = 2021`（`:221`）、双阈值 50（`:222-223`）、`classifiedAt` 来自注入的 `Clock`。
- 判定优先级（`classify` 主体）：明确临床 → `SERVICE_ONLY`；医学域且无制药/器械白名单 → `OUT_OF_SCOPE`；两分均 ≥50 → `HYBRID_RND`；仅生产 ≥50 → `PRODUCTION_RND`；仅科研 ≥50 → `ACADEMIC_RND`；有服务岗位且两分不足 → `SERVICE_ONLY`；其余 → `UNKNOWN`。
- **注意**：末位规则「有服务岗位且两分不足 → `SERVICE_ONLY`」意味着 affiliation 含 `service`/`support`/`sales`/`consultant`/`coordinator`/`administrative`/`客服`/`服务`/`销售`/`顾问`/`专员`（`:329-333`）的 RAW 文档会被本计划的门禁拒绝。这是**有意保留**的行为（这批词确实指示非研发岗位），但须在 A3-4 中观察其占比。

### 配置
- `ExpertClassificationProperties`（`config/ExpertClassificationProperties.kt:14-27`）是 `talent-introduction.expert-classification` 前缀的唯一绑定类，现有 5 个字段，`init` 块对三个数值做范围校验。
- `application.yml:32-37` 是对应配置段。
- Interaction points: 新增开关加在此类，不新建 properties 类，避免同前缀两个绑定。

### 任务与进度
- `PromotionScanStats`（`domain/ExpertRevalidationDomain.kt:30-40`）：`filtered` 与 `filterReasons: MutableMap<String, Int>` 已存在，**无需新增统计字段**。
- `PromotionScanResult.taskFailureCount`（`:47`）= `filtered + emailRejected + promotionFailed + existenceCheckFailed`。被分类门禁拒绝的专家计入 `filtered`，会使任务终态从 `SUCCESS` 变为 `PARTIAL_SUCCESS`。**这是既有语义**（现有的资格过滤同样如此），不修改。
- `TaskTypeCatalog:124-125` 已登记 `RAW_PROMOTION_SCAN`，`hasProgressUi = true`，无需改动（无新任务类型）。

## 实现方案

### Task 1：配置开关（I3-5、M-6）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/config/ExpertClassificationProperties.kt`
- 新增字段 `val promotionGateEnabled: Boolean = false`（放在 `incrementalEnabled` 之后），并补类注释说明其只控制拒绝行为、不控制写入。

修改文件：`src/main/resources/application.yml`
- 在 `:32-37` 的 `expert-classification` 段内新增：
  `promotion-gate-enabled: ${EXPERT_CLASSIFICATION_PROMOTION_GATE_ENABLED:false}`

### Task 2：开放序列化函数（I3-4）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt`
- `classificationNode`（`:352-363`）从 `private fun` 改为 `fun`（保持在类内，供同 Spring 上下文的 `ExpertRevalidationService` 通过已注入的 `expertIndexWriterService` 调用）。
- 函数体逐字不动。

### Task 3：晋升判定（I3-1、I3-3、I3-5、I3-6）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt`

- 构造函数（`:14-21`）追加两个依赖：`private val expertClassificationService: ExpertClassificationService`、`private val expertClassificationProperties: ExpertClassificationProperties`。**追加在参数列表末尾**，避免打断既有位置参数构造的测试。
- `promoteEligibleRawExperts` 循环体（`:148-191`）：在 `:151-158` 的 `evaluateEligibility` 块之后、`:160-174` 的存在性检查之前插入（I3-6）：

```kotlin
// I3-3: 现算，不读 profile.expertClassification（RAW 层几乎恒为 null）。
val classification = expertClassificationService.classify(profile)
// I3-1/I3-5: 宽档——只拒证据充分的两类，UNKNOWN 放行；开关关闭时不拒绝，但下方仍写入。
if (expertClassificationProperties.promotionGateEnabled &&
    (classification.type == ExpertType.SERVICE_ONLY || classification.type == ExpertType.OUT_OF_SCOPE)
) {
    stats.filtered++
    stats.filterReasons.merge("CLASSIFICATION:${classification.type.name}", 1) { a, b -> a + b }
    continue
}
```

- `promoteRawToCandidate`（`:241-261`）签名改为 `(profile: ExpertProfile, classification: ExpertClassification)`；在 `:252-257` 的 `apply` 块内追加第四个键（I3-4、I3-5）：

```kotlin
put("expertClassification", expertIndexWriterService.classificationNode(classification))
```

- `:185` 的调用点改为 `promoteRawToCandidate(profile, classification)`。
- **不改**：`revalidateCandidates`（`:24` 起）与 `promoteRawToCandidate` 的其余逐字段复制逻辑。

### Task 4：测试

修改文件：`src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt`

必须覆盖：

1. 开关开启 + `SERVICE_ONLY` → 不晋升，`filtered` +1，`filterReasons["CLASSIFICATION:SERVICE_ONLY"] == 1`。
2. 开关开启 + `OUT_OF_SCOPE` → 同上。
3. 开关开启 + `UNKNOWN` → **晋升**（I3-1 的核心断言）。
4. 开关开启 + `PRODUCTION_RND` / `ACADEMIC_RND` / `HYBRID_RND` → 晋升。
5. 开关关闭 + `SERVICE_ONLY` → **晋升**，且 `filterReasons` 中无 `CLASSIFICATION:` 前缀键（I3-5）。
6. 开关关闭 → 晋升后的文档仍含 `expertClassification`（I3-5）。
7. 一个只有 `employment = "Robert Bosch GmbH"` + `lastPublicationYear = 2024`、其余字段为 null 的 RAW profile，断言 `classify` 结果为 `UNKNOWN` 且本计划的门禁放行它（I3-1 的回归锚点，防止后人收紧）。
8. 晋升后的 doc 中 `email` / `employment` / `institution` / `tags` 与 `rawDoc` 逐字相同，仅新增 `expertClassification` 与既有三个叠加键（必须保持不变第 3 条）。
9. 资格不通过者不进入分类计算（用 mock 断言 `classify` 未被调用，I3-6）。

修改文件：`src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt`
- 断言 `classificationNode` 的输出结构与既有回填路径逐字一致（9 个键，`classifiedAt` 用既有 `dateFormatter` 格式）。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/config/ExpertClassificationProperties.kt` | 新增 `promotionGateEnabled` 字段 |
| 2 | `src/main/resources/application.yml` | `expert-classification` 段新增一行 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | `classificationNode` 由 `private fun` 改为 `fun` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt` | 构造加 2 依赖；循环体插入判定；`promoteRawToCandidate` 加参数与一个写入键 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt` | 9 组断言 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt` | 序列化结构断言 |

合计 6 个文件，1 个子系统（专家晋升），零前端改动，故无 `## 样式契约` 节。

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7 / Java 11 Maven 工程，**必须用 JDK 11（zulu-11）**，裸 `mvn` 会构建失败（`CLAUDE.md:7`）。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertRevalidationServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertIndexWriterServiceTest

# 关联回归（分类器与晋升行为）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertClassificationServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertRevalidationServiceBehaviorTest

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：Maven 退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0`；`git diff --check` 无输出。

来源：`CLAUDE.md:5-27`。

## 验收标准

- I3-1: 单测 3 与 7 通过——`UNKNOWN` 及"仅公司名 + 发表年份"的企业 profile 均被放行。grep 证明判定条件是两个类型的显式枚举比较，仓库中**不存在** `!classification.sendable` 之类的写法。
- I3-2: 本不变量为设计约束，由 I3-1 的断言间接保证；另在 `ExpertRevalidationService` 判定处的注释中逐字保留 `enrichExistingExperts` 只扫 CANDIDATE 的行号引用，供后人查证。
- I3-3: grep 证明 `ExpertRevalidationService.kt` 中零命中 `profile.expertClassification` / `.expertClassification?.type`（读旧值）。
- I3-4: grep 证明 `classificationNode` 在 `src/main/kotlin` 中仍只有一处定义；单测断言其输出键集合与回填路径一致。
- I3-5: 单测 5 与 6 通过——开关关闭时不拒绝，但写入照做。
- I3-6: 单测 9 通过——资格不通过者不调用 `classify`。
- 回归：执行「验证命令」节的全量测试命令通过；`git diff src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityService.kt` 为空。

## 人工验收清单

### A3-1: 开关默认关闭，发布零行为变化
- 前置条件: 部署前记录一次快速晋升的完整结果（`total` / `promoted` / `filtered` / `emailRejected` 四个数字）。测试环境 RAW 层数据在两次运行之间不变。
- 操作步骤: 1. 部署本计划；2. 不设置任何环境变量；3. 在「发现专家 → 快速晋升（扫描 RAW）」再跑一次；4. 对比四个数字。
- 预期结果: `filtered` 与部署前相同；`filterReasons` 中**不出现**任何 `CLASSIFICATION:` 前缀的键。（`promoted` 可能因 `alreadyPromoted` 增长而变小，属正常。）
- 覆盖: I3-5、需求描述第 3 条

### A3-2: 开关关闭时分类照样写入
- 前置条件: 选定一名 `expertClassification` 缺失、资格可通过、尚未在 CANDIDATE 的 RAW 专家，记录其 orcidId。
- 操作步骤: 1. 保持开关关闭；2. 跑一次快速晋升；3. 在专家列表页（子计划 01）搜索该专家。
- 预期结果: 该专家出现在 CANDIDATE 层，且列表行**有**类型 chip。
- 覆盖: I3-5、需求描述第 2 条

### A3-3: 开关打开后只挡医生
- 前置条件: RAW 层准备四条可通过资格的数据：(a) `employment = "Department of Cardiology, X Hospital"`；(b) `employment = "Department of Mechanical Engineering, Y University"`、`lastPublicationYear = 2024`；(c) `employment = "Robert Bosch GmbH"`、`lastPublicationYear = 2024`；(d) `employment` 为空串、其余字段全空。四条均不在 CANDIDATE 中。
- 操作步骤: 1. 设 `EXPERT_CLASSIFICATION_PROMOTION_GATE_ENABLED=true` 并重启；2. 跑一次快速晋升；3. 在专家列表按 orcidId 逐条查找；4. 查看任务进度详情中的 `filterReasons`。
- 预期结果: (a) **未**晋升，`filterReasons` 含 `CLASSIFICATION:OUT_OF_SCOPE` 计数 ≥1；(b)(c)(d) **均已**晋升；(b) 类型 chip 为「学术科研」，(c)(d) 为「未知」。
  若 (c) 未被晋升，即为 I3-1 违规——这正是本计划要防的企业研发误杀。
- 覆盖: I3-1、I3-3、需求描述第 1 条

### A3-4: 拒绝占比在可接受范围
- 前置条件: 开关已打开，RAW 层为真实存量数据。
- 操作步骤: 1. 跑一次完整快速晋升；2. 记录 `total`、`filtered`，以及 `filterReasons` 中 `CLASSIFICATION:SERVICE_ONLY` 与 `CLASSIFICATION:OUT_OF_SCOPE` 两项的数值；3. 计算两项之和占 `total` 的比例。
- 预期结果: 比例应在 30%~70% 之间（现状是发信专家"大部分是医生"，故过低说明门禁没生效，过高说明词表过宽）。若 `CLASSIFICATION:SERVICE_ONLY` 显著高于 `OUT_OF_SCOPE`，需检查是否被 `SERVICE_ROLE_TERMS`（`ExpertClassificationService.kt:329-333`，含 `service`/`consultant` 等常见词）过度命中——此时应调词表而非回退本计划。
- 覆盖: 现状审计的「注意」条

### A3-5: 原字段未被覆盖（回归）
- 前置条件: 选定一名待晋升 RAW 专家，先用 `GET /api/experts?level=RAW` 记录其 `email`、`employment`、`institution`、`tags`、`country` 五个原值。
- 操作步骤: 1. 跑快速晋升使其进入 CANDIDATE；2. 用 `GET /api/experts?level=CANDIDATE` 读取同一 orcidId 的完整信息。
- 预期结果: 五个原值逐字相同；`tags` 在原值基础上多出 `auto_promoted`；新增 `expertClassification`。
- 覆盖: 必须保持不变第 3 条

### A3-6: 重新验证候选人链路未受影响（回归）
- 前置条件: 记录部署前一次「重新验证候选人」的结果数字。
- 操作步骤: 1. 部署并打开开关；2. 执行一次「重新验证候选人」。
- 预期结果: 结果数字与部署前同条件下一致；任务终态未从 SUCCESS 变化。
- 覆盖: 必须保持不变第 5 条

人工验收开始时，从本节导出 `03-promotion-classification-gate-acceptance.md`；不得提前生成。
