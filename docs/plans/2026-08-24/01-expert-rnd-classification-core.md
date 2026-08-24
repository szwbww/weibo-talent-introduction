# 子计划 01：专家研发类型分类核心

## 需求描述

可观察结果：给定同一份 `ExpertProfile`，系统可稳定、可解释地输出 `rnd-v1-2026` 分类对象；三层 ES 均能保存并读取该对象，但本子计划不回填线上数据、不改变发信行为。

必须保持不变：现有 ExpertProfile 构造调用保持源码兼容；现有搜索字段和排序不变；mapping 只做可向后兼容的新增；应用启动不执行分类写入。

范围外：批量回填、管理 API、发送过滤、定时增量、外部数据补全、前端展示。

## 关键不变量

### Invariant I1-1: 类型与可发信派生关系唯一
- Rule: `ExpertType` 只能是 `PRODUCTION_RND / ACADEMIC_RND / HYBRID_RND / SERVICE_ONLY / OUT_OF_SCOPE / UNKNOWN`；`sendable=true` 当且仅当前三种类型，调用方不得独立传入 sendable。
- Applies to: `ExpertClassification` 构造与 JSON 反序列化测试、`ExpertClassificationService.classify`。
- Violation consequence: 类型和发信布尔值互相矛盾。
- 来源: original

### Invariant I1-2: 临床职业最高优先级
- Rule: 明确临床职业词命中后必须返回 `SERVICE_ONLY/sendable=false`，优先于论文、专利和研发分数；`doctor`、`MD`、`PhD`、`doctorate` 单独出现不得视为临床职业。
- Applies to: `ExpertClassificationService.classify`。
- Violation consequence: 看病型医生被科研产出重新放行，或博士被误杀。
- 来源: original

### Invariant I1-3: 医学范围正向白名单
- Rule: 文本命中医学域、未命中明确临床职业时，仍必须命中制药研发或医疗器械研发白名单；否则返回 `OUT_OF_SCOPE/sendable=false`，即使学术分达到阈值。
- Applies to: `ExpertClassificationService.classify`。
- Violation consequence: 临床医学、流行病学等非目标医学专家进入发信池。
- 来源: original

### Invariant I1-4: 确定性与可解释性
- Rule: policy 固定为 `rnd-v1-2026`、近期论文截止年固定为 2021、生产分阈值 50、科研分阈值 50；同一输入的 type、分数、evidence、sourceFingerprint 必须逐字一致，只有 `classifiedAt` 来自注入的 Clock。
- Applies to: 归一化、计分、证据排序、SHA-256 指纹、测试 fixture。
- Violation consequence: 重跑结果漂移，无法审计或幂等。
- 来源: original

### Invariant I1-5: 一个顶层 ES 对象
- Rule: RAW/CANDIDATE/APPLICATION 各只新增 `expertClassification` 对象；对象子字段为 `type(keyword)`、`sendable(boolean)`、`productionScore(integer)`、`researchScore(integer)`、`positiveEvidence(keyword[])`、`negativeEvidence(keyword[])`、`version(keyword)`、`sourceFingerprint(keyword)`、`classifiedAt(date)`。
- Applies to: 三份 mapping、`ExpertProfile.expertClassification`、`ExpertSearchService.sourceFields/toExpertProfile`。
- Violation consequence: dynamic:false 丢字段或出现并列事实源。
- 来源: original

## 现状审计

### 三层 ES mapping
- Schema/mapping: `orcid_info_raw.json:7`、`orcid_info_candidate.json:7`、`orcid_info_application.json:7` 均 `dynamic:false`；分类所需源字段已声明，分类结果字段未声明。`ExpertIndexService.bootstrapMappings:66-69` 会在启动时把三份 JSON 的 properties 推送到已存在索引。
- Write paths:
  1. `ExpertDiscoveryService.consumeOutcome → ExpertIndexWriterService.indexToRaw`、`promoteDiscoveredToCandidate`、`updateRawDocumentEmail`、`promoteRawToCandidateWithEmail`、`updateExpertAcademicFields` — RAW/CANDIDATE 整文档写、邮件局部更新、三层学术字段局部更新。
  2. `ExpertIndexWriterService.markApplicationClosed/syncOperatorStatus/syncOperatorStatusBatch/syncApplicationStatus/addTag/removeTag` — 三层或指定层的现有局部更新。
  3. `ExpertIndexWriterService.indexToRaw/promoteToCandidate/writeCandidateDocument/promoteToApplication/demoteToRaw` — RAW 写入与 RAW→CANDIDATE→APPLICATION 整文档复制；`removeFromCandidateIndex/demoteToRaw` 包含删除。
  4. `ExpertRevalidationService.promoteRawToCandidate` — 经 readRawDocument/writeCandidateDocument 完整复制。
  5. 子计划 02 的分类 bulk update（本子计划尚不新增）。
- Read paths: `ExpertSearchService.toExpertProfile:398-438` 与 `sourceFields:446-460` 是所有 ExpertProfile 查询的共享读 seam。
- Interaction points: mapping 新字段必须同时进入 `_source` 列表与 Jackson/Kotlin 模型，否则 ES 写入成功但发信/回填读不到。

### ExpertProfile
- Schema/mapping: 当前尾部字段是 `enrichmentSource: String? = null`，现有测试和生产代码大量使用位置参数/命名参数构造。
- Write paths: Kotlin 对象本身不可变；各数据源和搜索服务构造。
- Read paths: 邮件模板、发送、资格验证、发现补全和本计划分类器。
- Interaction points: 新字段必须作为尾部可空默认参数，禁止插入必填参数破坏既有构造。

### 分类输入事实
- Schema/mapping: 可用文本为 employment、keyword、researchFields、institution、recentWorkTitles、patentTitles；数值为 lastPublicationYear、hIndex、worksCount。degree 不作为临床判据。
- Write paths: 外部发现和 OpenAlex enrichment 写入上述字段。
- Read paths: 新 `ExpertClassificationService` 只读这些字段。
- Interaction points: classifier 不得读取 email、国籍或性别等与生产/科研能力无关的字段。

## 实现方案

### Task 1：建立领域对象（I1-1、I1-4、I1-5）

修改文件：

- 新增 `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertClassification.kt`
- 修改 `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt`

要求：

- 定义六值 `ExpertType`。
- `ExpertClassification` 构造函数不接收任意 sendable；用只读 getter `type in SENDABLE_TYPES`，序列化仍输出 `sendable`。
- 保存 productionScore/researchScore、正负证据、version、sourceFingerprint、classifiedAt。
- `ExpertProfile` 尾部增加 `expertClassification: ExpertClassification? = null`。

### Task 2：实现固定 v1 分类策略（I1-1～I1-4）

修改文件：

- 新增 `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationService.kt`
- 新增 `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationServiceTest.kt`

要求：

1. 归一化：输入字段 Unicode NFKC、Locale.ROOT lowercase、标点转空格、连续空白折叠；数组先逐项归一再按原顺序连接。sourceFingerprint 对“归一化后的六个文本字段 + 三个数值字段”做 SHA-256。
2. 明确临床词使用单词/短语边界，至少覆盖：`physician, clinician, surgeon, general practitioner, family practitioner, attending physician, resident physician, consultant physician, hospitalist, cardiologist, oncologist, neurologist, psychiatrist, pediatrician, paediatrician, dermatologist, anesthesiologist, anaesthesiologist, radiologist, urologist, gynecologist, gynaecologist, obstetrician, ophthalmologist, otolaryngologist, gastroenterologist, nephrologist, pulmonologist, rheumatologist, endocrinologist, hematologist, haematologist, pathologist, dentist, nurse practitioner, 医师, 医生, 主任医师, 副主任医师, 主治医师, 住院医师, 临床医生, 外科医生, 内科医生, 牙医`。
3. 临床排除词明确禁止加入：裸 `doctor`、`MD`、`PhD`、`doctorate`。
4. 医学域词至少覆盖：`medicine, medical, clinical, patient, hospital, healthcare, therapy, disease, oncology, cardiology, surgery, diagnosis, 医学, 临床, 患者, 医院, 疾病, 诊疗`。
5. 制药白名单至少覆盖：`drug discovery, drug development, drug design, drug delivery, medicinal chemistry, pharmaceutical, pharmacology, pharmacokinetics, pharmacodynamics, toxicology, formulation, preclinical, biologics, biopharma, vaccine development, therapeutic development, target validation, small molecule, antibody development, 药物研发, 药物发现, 药物设计, 药剂, 制剂, 药代动力学, 毒理, 生物制药, 疫苗研发`。
6. 器械白名单至少覆盖：`medical device, biomedical engineering, biomaterial, biosensor, in vitro diagnostic, IVD, medical imaging, implant, prosthetic, surgical robot, medical instrumentation, diagnostic device, wearable medical, 医疗器械, 生物医学工程, 生物材料, 生物传感器, 体外诊断, 医学影像, 植入物, 假体, 手术机器人`。
7. 生产分：patentTitles 非空 +45；明确 R&D/产品/工艺/设计/制造岗位 +35；产品/工程/制造主题 +20；公司形态词（Inc/Ltd/GmbH/Corp/Company/Pharma/Biotech/MedTech 及中文有限公司/制药/生物科技/医疗科技）+15；制药/器械白名单 +15。每类证据最多计一次，封顶 100。
8. 科研分：lastPublicationYear≥2021 +35；recentWorkTitles 非空 +25；researchFields 非空 +15；hIndex≥20 +20、否则 hIndex≥5 +10；worksCount≥20 +15、否则 worksCount≥5 +10；大学/研究所/实验室/教授/研究员/科学家语义 +20。每类最多一次，封顶 100。
9. 判定优先级逐字固定：明确临床→SERVICE_ONLY；医学域但无制药/器械→OUT_OF_SCOPE；两分均≥50→HYBRID_RND；仅生产≥50→PRODUCTION_RND；仅科研≥50→ACADEMIC_RND；存在服务岗位且两分均不足→SERVICE_ONLY；其余→UNKNOWN。
10. evidence 使用稳定枚举 code、去重并按声明顺序输出；禁止输出自由文本个人信息。

测试必须覆盖每个优先级、阈值 49/50、裸 doctor/MD/PhD 不误杀、中英文词边界、医学白名单、分数封顶、固定 Clock、指纹稳定、输入变化导致指纹变化。

### Task 3：声明并读取 ES 对象（I1-5）

修改文件：

- `src/main/resources/es/orcid_info_raw.json`
- `src/main/resources/es/orcid_info_candidate.json`
- `src/main/resources/es/orcid_info_application.json`
- `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt`

要求：

- 三份 mapping 使用完全相同的 `expertClassification.properties`，不得使用 `enabled:false`。
- `classifiedAt` format 与现有日期字段一致：`yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis`。
- `sourceFields()` 增加 `expertClassification`；`toExpertProfile()` 显式解析所有子字段，字段缺失/null 返回 null，未知 type 不得悄悄映射为 UNKNOWN，必须记录 warn 并将整个 classification 视为 null，以触发安全失败。
- 读写 round-trip 测试断言 `sendable` 由 type 派生，ES 中不可信的 sendable 值不能覆盖领域 getter。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertClassification.kt` | 新分类领域对象 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt` | 尾部新增可空分类对象 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationService.kt` | v1 规则、计分、指纹 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | ES 分类对象读取 |
| 5 | `src/main/resources/es/orcid_info_raw.json` | RAW mapping |
| 6 | `src/main/resources/es/orcid_info_candidate.json` | CANDIDATE mapping |
| 7 | `src/main/resources/es/orcid_info_application.json` | APPLICATION mapping |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationServiceTest.kt` | 分类规则测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | ES 解析/round-trip 测试 |

共 9 个文件、1 个子系统、每个共享索引 1 个新顶层字段。

## 验收标准

- I1-1: 参数化测试遍历六类，断言只有前三类 sendable=true，且无独立可写 sendable 构造参数。
- I1-2: 临床词+高科研/生产分 fixture 仍为 SERVICE_ONLY；仅含 doctor/MD/PhD 的科研 fixture 不因这些词被排除。
- I1-3: 医学+近期论文但无白名单为 OUT_OF_SCOPE；加入 drug development 或 medical device 后按分数进入研发类型。
- I1-4: 同输入同 Clock 两次结果逐字段相同；阈值 49/50、封顶 100、SHA-256 长度 64 均有断言。
- I1-5: 三份 JSON mapping 结构相同；ExpertSearchService 请求 `_source` 含对象并完整解析；缺失/未知 type 安全返回 null。
- 回归：运行 `mvn test`；现有 ExpertProfile 构造测试编译通过；现有 ExpertSearchService 查询 sort/filter JSON 不因新增 `_source` 字段之外发生变化。

## 人工验收清单

### A1-1: 分类样本可解释
- 前置条件: 在测试环境调用分类 service 的诊断测试，准备五个固定 fixture：① employment=`surgeon` 且有近期论文/专利；② researchFields=`drug development`、lastPublicationYear=2025、recentWorkTitles 非空、hIndex=10、worksCount=20、institution=`University Laboratory`；③ employment=`R&D Engineer, MedTech Ltd`、researchFields=`medical device`、patentTitles 非空；④ researchFields=`distributed systems`、lastPublicationYear=2025、recentWorkTitles 非空、institution=`University Laboratory`；⑤除 ORCID 外所有分类输入为空。
- 操作步骤: 1. 运行指定测试；2. 查看测试输出的 type、两分、证据 code。
- 预期结果: 依次为 SERVICE_ONLY、ACADEMIC_RND、PRODUCTION_RND、ACADEMIC_RND、UNKNOWN；第一和第五 sendable=false，其余 true。
- 覆盖: I1-1～I1-4、需求描述

### A1-2: mapping 向后兼容
- 前置条件: 空 ES 测试索引按三份资源分别创建，并各写入一条不含分类字段的旧文档。
- 操作步骤: 1. 启动应用触发 mapping bootstrap；2. 查询 `_mapping/field/expertClassification.*`；3. 读取旧文档。
- 预期结果: 九个对象子字段类型与 I1-5 一致；旧文档仍可读取且 `expertClassification=null`；原 `_source` 未改变。
- 覆盖: I1-5、必须保持不变第 3 条

### A1-3: 本子计划零写入
- 前置条件: 测试 ES 有 10 条无分类文档。
- 操作步骤: 1. 仅部署子计划 01；2. 重启应用；3. 查询分类字段存在数量。
- 预期结果: 数量仍为 0；没有分类任务执行记录。
- 覆盖: 必须保持不变第 4 条、范围外“批量回填”

人工验收开始时导出 `01-expert-rnd-classification-core-acceptance.md`。
