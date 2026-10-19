# Apollo 机构主体核验修复计划

日期：2026-10-19。状态：**已规划，待前置修复完成及执行前复核；不可直接跳过复核开工**。

前置：[Apollo 辅助验证修复总计划](apollo-assist-repair-master.md)的 01、02 完成机器验证，固定对照输入与结果已保存。当前研究基线见 [baseline.txt](apollo-institution-evidence/baseline.txt)，不是对前置计划完成状态的认定。后续执行者先完成 T-0；仅行号、命名漂移可更新证据后继续，行为合同或白名单变化须先修订本计划。

### 2026-10-19 补充：所在地未知

本入口追加[机构国家补全子计划](discovery-institution-country-supplement.md)，不是只记录建议。顺序固定为：**Apollo前置修复01/02 → 本文机构主体阶段（10文件）→ 国家补全阶段（8文件）**，各自验证；不并行修改共享调用链。

- 本文以下 O/N/I/T/A 与文件白名单仅适用于“机构主体阶段”；其“原RAW不改”“无ES新增字段”合同保留。国家阶段另有独立合同，只在新档案首次冻结前补现有country并生成对应token，不覆盖历史或核验中档案。
- 国家优先复用本篇OpenAlex已绑定作者的同机构数据；不足时最多一次ORCID公开employments请求。它会增加公开请求和耗时，设独立上限，不能混入Apollo计数或宣称复用旧ORCID缓存。
- 国家阶段不改UI/CSS、国籍、APOLLO_UNCLEAR准入和论文样本留存。用snapshot_json.countrySupplement留审计依据；不增加ES字段/表/后台调度，不做全量历史回填。
- 补全效果按固定真实输入另行测量；当前没有线上补全率结论。代码、签名与登记竞争证据及黑盒验收见子计划。

## 需求描述

- O-1：新进入 Apollo 核验的档案，机构限定查询使用可明确提取的机构主体；原档案、当前 ORCID 任职、Apollo 机构使用同一提取规则比较，避免院系、地址、脚注导致完整字符串不相等。
- O-2：保留每次核验采用的原机构、主体、提取状态与版本，可对固定真实输入解释新增匹配、负向变化和未解决原因。不能把提取覆盖率称作身份准确率。

必须保留：
- N-1：姓名、绑定 ORCID、当前任职来源、唯一候选及全部资格规则；身份已确认但资格 UNKNOWN 的 APOLLO_UNCLEAR 仍可经原 Gate/Commit 自动准入。
- N-2：旧已完成结果和旧在途查询序列；原始 RAW、ORCID 派生事实、HTTP 回执、attempt 和论文样本留存。
- N-3：配额、分页完整性、租约 CAS、人工/已联系/APPLICATION/SBIR 保护、准入 revision 与候选投影链。
- N-4：页面结构/CSS、现有统计口径、GET 只读。

范围外：全量改写 41,844 条历史档案、自动重排历史核验、重新绑定作者身份、大学医院隶属库、全球机构词典、ROR 联网解析、缩写自动展开、翻译/模糊匹配、LLM、额外 Apollo API、扩大请求数或留存配额、前端展示新组件。本轮不解决所有语言和所有复杂署名。

**机构主体阶段规模：一个核验子系统，10 个实现/测试文件；不新增表、列、ES 字段。仅在已有 item.evidence_json 增加一个可选成员 institutionNormalization。** 本文与审计附件属于规划产物，不计入执行白名单。

## 关键不变量

### Invariant I-1: 原值与事实来源不变
- Rule：机构主体是派生比较值，不覆盖 profile.institution/employment、snapshot_json、evidence 中 employment.value、orcidCompleted、ApolloCandidate.institution 或 attempt.response_json。论文署名不变成当前任职；身份来源继续使用前置 01 的 identityEmployments 合同，资格证据筛选不变。
- Applies to：Normalizer、Worker 保存、Rules、Gate、Commit 透传。
- Violation consequence：机构清洗污染事实指纹，或历史论文单位被当成当前雇主。
- 来源：K-paper-affiliation-is-not-employment、K-author-identity-needs-email-evidence。

### Invariant I-2: 主体提取有边界
- Rule：同输入同版本输出相同。RESOLVED 恰有一个明确主体；AMBIGUOUS 有多个不同主体或无法拆开的层级；UNRESOLVED 包括缺失、残缺和无法可靠解析。不得按字符串长度、逗号第一段、任意 firstOrNull、子串或编辑距离直接判同机构。UC 校区、医院、联合单位不折叠成大学总名。输出的主体必须可追溯到原字符串中的连续片段（允许声明的脚注/空白清理），不得臆造名称。
- Applies to：Normalizer.resolve/compare、Worker 窄查询、Rules 两个机构比较位置。
- Violation consequence：同名异校、大学与医院被误合并。
- 来源：original；K-orcid-institution-first-is-ambiguous 的歧义原则。

### Invariant I-3: 查询提示不是身份凭据
- Rule：机构查询只取合格 CURRENT ORCID 任职提取结果；每条候选机构字段须 RESOLVED，去重后恰为一个才追加一次 NARROW。有任一未解决任职或多个主体时不任选、不逐机构扩查；姓名宽查与公开别名照旧。匹配仍须完整宽搜、唯一人选、姓名和 ORCID 约束全部成立；RAW 机构不能替代缺失的当前任职。
- Applies to：Worker 查询构造、Rules 身份匹配、ApolloSearchEvidence.merge。
- Violation consequence：查询命中被当成身份确认，或增加请求掩盖规则效果。
- 来源：original；K-author-identity-needs-email-evidence。

### Invariant I-4: 一份可选派生上下文，旧数据不被隐式升级
- Rule：institutionNormalization 缺失/null＝前置修复完成时的旧机构比较语义；值为版本 INST_SUBJECT_V1 的有效对象＝新语义；未知版本、来源不一致、缺项或被篡改的对象不能退回旧语义放行。旧 COMPLETED、旧 APOLLO_SEARCH、旧 DECIDE 不补该字段。LOCAL_EVIDENCE 完成并第一次进入 Apollo 前才创建新对象，先持久化再发 Apollo 请求。旧准入在新 Gate 下保持原判定。
- Applies to：VerificationWorkerEvidence、VerificationAdmissionEvidence、Worker 全部退出/恢复路径、Rules、Gate、QueryService。
- Violation consequence：部署后历史准入被重算撤回、在途游标错位、未知协议静默通过。
- 来源：K-verification-cached-evidence-replay、K-discovery-admission-read-write-parity。

### Invariant I-5: 派生上下文可复核，不自证
- Rule：Worker 保存 institutionNormalization，Rules/Gate 从 snapshot、合格 employment、search 原值重新计算并核对，不能只相信保存的主体。版本、输入引用、原值、主体、状态和提取依据一致才使用。无网络、无写库。QueryService 必须传同一上下文；不能一个页面读旧规则、准入读新规则。新字段只存派生信息，不再复制完整 HTTP 文档。
- Applies to：Worker→Repository JSON→Rules/Gate/QueryService；测试对照输出。
- Violation consequence：主体被手工或错误程序改写后绕过原始证据；日志与判定不一致。
- 来源：K-discovery-admission-read-write-parity。

### Invariant I-6: 恢复、计费、提交和留存不变
- Rule：APOLLO_SEARCH 恢复使用已保存版本、任职输入和 queryInstitution；不得重新计算不同查询计划覆盖 queryIndex/page。只增补新返回候选的派生记录。每次 HTTP 仍先 reserve；429、分页上限、请求上限和累计预算照旧。finish、retry、fail、ITEM_LIMIT 均保留新成员；最终准入必须通过同一 Gate/Commit，原采样配置和回收策略不动。
- Applies to：Worker、Repository 既有保存入口、Gate、Commit、SampleStore。
- Violation consequence：重复请求、丢失留档、误记准入或绕过人工保护。
- 来源：K-verification-cached-evidence-replay、K-extraction-samples-are-not-consumption。

### Invariant I-7: 固定输入与效果边界
- Rule：41,844 是历史唯一文档 ID 数，不是当前人数或人工真值。比较采用相同冻结输入和 UTC 时钟；机构提取与身份判定分别统计。新规则离线对照不得联网补查或写业务库。没有真实完整核验证据时只报告提取结果，身份提升标 NOT_EVALUATED，不编造成功率。
- Applies to：固定 fixture、离线报告、人工验收。
- Violation consequence：把样本增长、候选召回或格式命中数误报为准确率提升。
- 来源：K-plan-quantified-claims-need-grep-receipts。

## 现状审计

### 实际输入与代码证据

- [41,844 档案统计](apollo-institution-evidence/institution-statistics.json)，来源 `outputs/apollo-institution-audit-20261017/pending-institution-inventory.json`，SHA-256 `d2019b7f6532236e1a07f46dde9f95cc2c28ad7ca1294429f099572556ce1150`，快照 2026-10-16T03:27:17.424942+00:00。26,134 条有机构，15,710 缺失；19,090 个不同机构字符串；9,690 条超过 100 字符，45 条超过 500，最长 970。输入 inventory 已做空白折叠，不是逐字节原 ES 备份。
- 非互斥规则命中：院系关键词11,796，开头脚注样式10,814，邮编/街道线索6,094，医院与大学词并存3,126，分号1,643，邮箱/网址434，非 ASCII 字母2,500。脚注和地址只是线索；分号不等于多机构。审计程序及规则见 [audit 回执](apollo-institution-evidence/institution-statistics-audit.py.txt)。
- `DiscoveryVerificationRules.kt:84–87` 原机构与 ORCID 比较；`:98–99` ORCID 与 Apollo 比较；`:119–122` 只比 standardId 或经 NFKC/大小写/标点归一的名称/aliases。这是两个需要一起修复的位置。
- `DiscoveryVerificationWorker.kt:181–183` 当前 firstOrNull 当前机构构造 NARROW；前置01计划将先实现 identityEmployments 和唯一机构选择，此处必须等完成后重读，不能覆盖其 SELF 身份支持。
- `ApolloPeopleSearchClient.kt:120–123` q_organization_name 直接取 query.institution.name；`:141–143` 原样构造候选机构，没有别名/标准ID补全。保持该 HTTP client 不变，由上游传主体。
- `OrcidVerificationEvidenceClient.kt:132–138` 文本投影 take(500)，`QualificationEvidenceParser.kt:68` 机构再 take(500)。**500 字符边界可能已截断**；本计划不增加原文采集，恰达该上限的 ORCID 机构保守 UNRESOLVED，不能拿前半截提取结果当完整机构。
- 结构化采集已存在：JatsXmlEmailParser:194–219 限 university/edu 且唯一；OrcidDataSource:166–176 用 singleOrNull；ExpertDiscoveryService:1592–1593、2465–2466 已不把 affiliation 写成 employment。历史长字段不能证明今天的采集仍走旧逻辑。两条旧知识的“当前仍 firstOrNull/复制 employment”描述已过期，本轮纠正；保留其语义原则。
- 调用回执 [rule-callers.txt](apollo-institution-evidence/rule-callers.txt)：当前主代码 evaluate 在 Worker:153/165/203/204/212、Gate:64、QueryService:136。后者计算 missingFields，不能遗漏。前置02还计划新增 ReplayTool 调用，T-0 必须重新 grep 确认。
- [逐行代码快照与哈希](apollo-institution-evidence/code-baseline.txt)；行号只对应当前基线，执行前按符号复核。

### MySQL 核验账本：run / item / attempt

Schema 已直接读取 [V158 回执](apollo-institution-evidence/schema.sql.txt)：item 唯一键 `(expert_doc_id, identity_hash, policy_version)`，evidence_json/snapshot_json 为 MEDIUMTEXT，state 默认 RESERVED，request_count/generation 默认0；attempt.request_json 为 TEXT，response_json 为 MEDIUMTEXT；run 保存设置、模式、版本、revision。本轮只改变 item.evidence_json 内的一个可选成员，attempt.request_json 仍使用已有 ApolloSearchQuery 结构，机构值改为主体。不新增索引或版本 policyVersion。

读写全集 grep 命令及输出：[store-paths.txt](apollo-institution-evidence/store-paths.txt)、[repository-methods.txt](apollo-institution-evidence/repository-methods.txt)。按实际命中归纳：

- run 写：Repository.createRun、closeInput、bump；读：reportingRuns、findRun、lockRun，以及各 reporting JOIN；RegistrationService.activate:112/117 锁读 run/item。无本计划新写入口。
- item 写：reserveItem、activateItem、claimDue、renewLease、reserveAttemptWithBudget（request_count）、saveEvidenceAndOutcome、saveFailure（委托并更新重试数）、markProjection、reschedule、failReserved、saveProgress、completeAttemptAndProgress（委托）、attachAdmission。schema 迁移建表；测试 SQL fixture 和 IT 直接 INSERT/UPDATE/DELETE 仅用于隔离构造。前置02将新增 requeuePendingForEvidenceReplay，执行时加入实测回执。
- **实际写 evidence_json 的方法**：saveEvidenceAndOutcome:341、reschedule:385、saveProgress:423；saveFailure/completeAttemptAndProgress 间接调用。Worker.json→finish/retry/fail/save/completed 提供 payload；Commit:84–95 透传 proposed.evidenceJson、再保存。新对象必须走这些既有路径，不另建 SQL writer。
- item 读：Repository.reportingCounts/Suggestions/Reasons/BatchTimes/BatchReasons/RecordTotal/Records、findItem/findItemsByDocIds/findManagedContexts、claimDue/listReserved/listProjectionDue、identity/checkedIdentity/lockItem/lockedItem/owns。消费者为 RegistrationService、Worker、Commit、QueryService、Gate；前置02 ReplayTool 为后续复核项。
- attempt 写：reserveAttemptWithBudget、completeAttempt、completeAttemptAndProgress；读：findAttempts、预算检查、reportingCounts 子查询。request_json 可审计实际机构查询；前端 attempt DTO 当前不返回 request_json，不能承诺页面已经能显示主体。
- 序列化读者不同：WorkerEvidence 与 Gate 的 AdmissionEvidence 是两个 DTO，二者都须添加同名可选属性。QueryService 读取 WorkerEvidence。不能只给 Worker 加字段后让 Gate 忽略它。

### 准入、ES、采样：沿用链，不扩写

- Commit:49–99 校验 lease/generation、最新 RAW 指纹及人工保护；将 proposed 行交给 policy/Gate，随后 saveEvidenceAndOutcome→recordAutomaticPersisted→attachAdmission；project:103 起沿用候选投影。Gate:64 复算并核对 outcome/qualification/route/personId。这解释了为什么本次需要修改 Gate 传参，而不能直接替换全局 sameInstitution。
- admission/review schema、存量读写路径沿用已核实的[前置总计划现状审计](apollo-assist-repair-master.md#现状审计)及 `apollo-repair-evidence/admission-paths.txt`；无新 admission writer。
- RAW/CANDIDATE/APPLICATION 仓库 mapping 均 dynamic:false，institution/employment 已存在。本轮不写新的 ES 主体字段，不改变原指纹；候选仅由既有 Commit.project 链透传。T-0 必须核对前置计划未改变此合同。
- DiscoverySampleModels:31 定义 EXTRACTION/schemaVersion=1；Capture.capture/save→Store.commit 保存，scan/retained/Replay 读取，启动/定时 cleanup 回收。路径见 [preserved-paths.txt](apollo-institution-evidence/preserved-paths.txt)及前置 `sample-read-write-paths.txt`。本轮零修改采样实现/配置；现存包正常过期，效果基线另行冻结。机构解析审计在 item JSON，不能冒充论文抽取样本。

### 交互点

- X-1：当前 ORCID 事实→主体提取→NARROW request_json/API 查询。
- X-2：RAW/ORCID/Apollo 原值→同一解析→Rules→Gate/Commit→持久准入；SELF 仍不能产生资格 MET。
- X-3：Worker JSON 三类写入口→重启恢复/Gate/QueryService；缺省字段和未知版本必须有明确语义。
- X-4：新旧规则结果→固定输入报告；提取覆盖与身份/准入统计不能混算。
- X-5：原 Capture/Store/attempt→继续留存；原保护/投影/GET 读取不发生旁路变更。

无前端文件，故不设样式契约；禁止执行者增加 UI、CSS 或新按钮。

## 实现方案

### T-0 执行前复核与冻结（I-1/I-4/I-6/I-7）

不修改业务文件。先核对前置01、02机器验证结果、人工验收状态和实际授权范围，记录最终 commit、dirty diff、构建产物哈希。重跑附件中的 grep，重新读取 Worker/Rules/Gate/QueryService/ReplayTool/Repository。

必须确认：identityEmployments 已实现；SELF 仅参与身份；public 兼容和缓存重建已落地；旧 APOLLO_SEARCH 不重排；受限 replay 只改 progress.stage；Gate 与持久结果一致。若前置工作未完成，计划保持 WAITING_FOR_PREDECESSOR；不能在此计划中顺手执行前置工作。

前置 replay 工具在当前代码尚不存在，**不预写假接口**。若完成后的工具仍三参 evaluate，新参数默认 null 要保持它的旧组行为；本轮新组在指定测试 harness 内显式传上下文。复核其冻结格式后，把实际文件/键名和调用命令补入执行回执；若必须修改该工具才能完成本计划，则先修订白名单，不把第11个文件夹带进来。

冻结本轮真实输入：优先使用前置02同一份冻结档案，不直接读“最新数据库”做前后差；记录 cohort/hash/UTC时间。仅有历史41,844机构字符串时也可完成提取审计，不能据此宣称 Apollo 人员匹配改善。

### T-1 单个纯解析器，不建机构平台（I-1/I-2/I-7）

新增 `DiscoveryInstitutionNormalizer.kt`（普通类/object，无 Spring/HTTP/DB 依赖），同文件定义派生 DTO 和版本常量。修改文件限清单1；测试与 fixture 限清单6、10。Normalizer不自行筛选任职：Rules/Worker将前置identityEmployments筛好的输入传入builder，避免复制第二套来源/时效规则；不改变现有Spring构造器注入方式。

固定处理顺序与第一版边界：

1. 原文留存不变；工作副本做 NFKC、空白折叠。脚注仅清理开头数字/†/‡/*或紧邻 Department/Institute/School/Faculty/Laboratory/College/Center/Centre 的单字母标记，不能删机构名内数字。没有明确机构词的 `3M` 不删3。
2. 按分号分块；邮件/URL 仅在能确定是独立联系片段时剔除。邮箱连着机构名或缺分隔的粘连文本标 UNRESOLVED，不猜分界。括号缩写不自动生成 aliases；保留附加信息，不能删除可能代表分校/院所的括号内容。
3. 逗号用于寻找完整机构片段，**不是取首段**。第一版可识别完整 University/Hospital/Institute/College/Clinic 机构片段及明确院系前缀；院系/School/Laboratory 名称不靠词长认定上级机构。只在剩余部分可归入院系或明确地址尾部时输出 RESOLVED；不明机构片段保留为 UNRESOLVED/AMBIGUOUS。
4. 为避免任意地名词典，本版地址去除限：明确街道/邮编/国别尾部组合；无法证明属于地址的残余名称不丢弃。地名不得单凭短词、全字母或逗号位置判为地址。执行第一步应先跑下表真实样本，并在 fixture 内记录被删片段与依据；规则不足就保留未解决，不扩大启发式去追求覆盖率。
5. `University of California, Los Angeles`/`…, Berkeley` 必须保留校区：完整独立名直接保留；若复杂文本不能唯一界定其完整校区名，UNRESOLVED，禁止退到 `University of California`。`Rutgers, The State University of New Jersey` 保留完整名称。其他未覆盖的逗号嵌入正式名不随意拆短。
6. 同一字段明确出现多个独立机构（含 Hospital 与 University）输出 AMBIGUOUS，保留各主体候选，不建立父子等价关系。同一主体的重复院系块可规范化去重。ORCID 多条各自明确的当前任职可分别保留；身份规则仍可与其中一家匹配，但窄查询须满足 I-3。
7. 单一结构化、无署名噪声的名称（例如 ETH Zurich、Université Paris-Sud）按完整名称保留；不翻译、不去掉重音、不展开简称。非结构化且无可识别主体的 `ACHISINA` 保留原文、UNRESOLVED，不能从字符串存在推导有效机构。
8. ORCID institution 长度达到500视为潜在裁剪：UNRESOLVED/INPUT_MAY_BE_TRUNCATED；RAW超过4096或分段过多（超过32段）输出 UNRESOLVED/INPUT_LIMIT，原值仍在 snapshot，审计记录引用+SHA而不重复大字符串。不得截前N字后继续匹配。无法判读的粘连、乱码、未知语言复杂层级保守退出。

解析状态仅用于派生 JSON，不增加 VerificationState。compare 返回 MATCH/DIFFERENT/UNKNOWN：两个完整且兼容的明确主体才能得到 MATCH；明确不同为 DIFFERENT；提取不可靠为 UNKNOWN。保留已有标准ID和显式aliases的使用，但不得从提取内容创造ID/别名；结构化输入的来源由调用方source类型与原字段决定，不能让原文自带某个词就冒充结构化来源；同命名空间ID冲突不能被名称匹配覆盖，不同命名空间不可直接判同。已存在且证明同一机构的标准ID优先，必须与其来源绑定。

固定样本合同（真实串来自本轮名单；人工构造的反例明确标注，不把这些案例当全量准确率）：

| 输入/对照 | 必须结果 |
|---|---|
| `2Department of BioNano Technology, Gachon University, 1342 Seongnam-daero, Sujeong-gu, Seongnam-si 13120, Gyeonggi-do, Republic of Korea` | 主体 Gachon University；脚注/院系/地址去除有依据 |
| `Department of Chemical EngineeringMassachusetts Institute of TechnologyCambridge, MA 02139 USA` | UNRESOLVED；不靠插空格猜主体 |
| `University of California, Los Angeles` 与 `University of California, Berkeley` | 两个完整主体，DIFFERENT |
| `Rutgers, The State University of New Jersey` | 完整保留，不能输出 Rutgers 或 The State University of New Jersey |
| `Leidos Research Support Team, Albany, OR, 97321, USA; National Energy Technology Laboratory, Albany, OR, 97321, USA` | 多机构歧义；无单一窄查询，不能只取 Laboratory |
| `bDepartment of Pain Medicine, Renji Hospital, School of Medicine, Shanghai Jiao Tong University, Shanghai, China` | AMBIGUOUS；Renji Hospital 不等于 Shanghai Jiao Tong University |
| `Department of Education, Seoul National University, Seoul, South Korea; Department of Education, Seoul National University, 1 Gwanak-ro, Gwanak-gu, Seoul, South Korea` | 两块同一主体去重；主体 Seoul National University |
| `CNRS UMI 3288- NTU - THALES` | 联合机构歧义；不能输出 CNRS 并匹配其法语总机构 |
| `Université Paris-Sud` | 原名称保留；不自动改成现代名称或英文名 |
| 构造：`Department of Biology, Example University, London, UK; a@example.invalid (A.B.)` | Example University；邮箱片段不成为第二机构 |
| 构造：Example University / Example Technical University | DIFFERENT |
| 构造：相同名称，ROR:1 / ROR:2 | DIFFERENT；名称不能掩盖同源ID冲突 |
| 构造：ETH Zurich / ETH Zurich，均为结构化机构输入 | MATCH，不需要硬编码ETH别名 |
| 缺机构、机构字段仅邮箱、ORCID满500字符、超4096 RAW | UNRESOLVED，单一窄查询0 |

这里是首版最低支持集合；复杂用例保留未解决属于预期。新增启发式必须补正例和相邻反例，不建立维护平台或先加入几千条未经验证的映射。

### T-2 上下文与新旧规则分流（I-1～I-5）

修改 `DiscoveryVerificationRules.kt`、`DiscoveryVerificationAdmissionGate.kt`，配合 Normalizer 同文件 DTO。

在现有 evaluate 参数末尾增加可选 `institutionNormalization`，默认 null；不得挪动 protected 参数位置。保留现有 sameInstitution 方法的旧语义，旧上下文继续调用它。新上下文走 normalizer.compare，两个机构比较点一并替换：RAW↔当前任职、当前任职↔Apollo；姓名、候选唯一性、ORCID冲突、qualification分支不改。

唯一新持久成员合同：

```text
institutionNormalization: null | {
  version: "INST_SUBJECT_V1",
  queryInstitution: null | "Gachon University",
  inputs: [
    { source: "PROFILE" | "ORCID_EMPLOYMENT" | "APOLLO_CANDIDATE",
      sourceRef: <profile字段路径 / evidence位置与其来源指针 / candidate.id>,
      raw: <当前已保存输入值；超限时null并留sha256>,
      rawSha256: <UTF-8原值SHA256>,
      status: "RESOLVED" | "AMBIGUOUS" | "UNRESOLVED",
      subjects: [<逐字主体片段>],
      reason: <固定解析原因码>,
      removedParts: [<被移除片段及规则标识>]
    }
  ]
}
```

- sourceRef 必须指向当前 payload/snapshot 中真实来源，不能写候选的机构到ORCID输入。ORCID证据重建后引用重新绑定；保存顺序固定 PROFILE、employment按输入稳定顺序、Apollo按id排序，去重不得丢不同来源。
- reason 固定取 `UNCHANGED_STRUCTURED`、`SUBJECT_EXTRACTED`、`MULTIPLE_SUBJECTS`、`MISSING`、`UNSUPPORTED_LAYOUT`、`INPUT_MAY_BE_TRUNCATED`、`INPUT_LIMIT`；这些是审计对象字段，不是数据库状态或前端新原因码。
- queryInstitution 由合格当前任职解析并满足 I-3 生成。每个主体最多保留原输入长度；超限/片段上限完整拒绝，不静默丢机构。rawSha256是字段哈希，不冒称HTTP哈希。
- Rules/Gate 从原输入重建期望对象后比较，未知版本/错误sourceRef/删掉一条候选机构记录等抛出受控 IllegalArgumentException，Gate沿用catch返回VERIFICATION_EVIDENCE_INVALID；Worker记录VERIFICATION_FAILED，绝不当作合法无匹配。Worker可以在保存前用同一builder生成对象，Gate不能信任其结论。
- 正常缺失RAW机构沿用原规则不新增阻断；非空RAW无法解析或多机构时 PENDING + IDENTITY_EVIDENCE_MISSING，missingFields增加 `institutionSubject`，而不是伪造明确机构冲突。明确主体与全部合格当前任职均不同才 INSTITUTION_CONFLICT。
- ORCID无可用当前机构继续 CURRENT_INSTITUTION_EVIDENCE_MISSING；Apollo机构解析失败不能匹配，仍受唯一候选与全名/ORCID约束。明确不同和不确定分开留档。
- Gate的 VerificationAdmissionEvidence 增加同名默认null属性并显式传入规则；原结果一致性校验、policyVersion与数据库唯一键都不改。旧记录和旧回放默认null保持前置计划的旧机构语义。

### T-3 Worker 查询、恢复与只读消费（I-3～I-6）

修改 `DiscoveryVerificationWorker.kt`、`DiscoveryVerificationQueryService.kt`。

- WorkerEvidence 增加同名默认null属性。LOCAL_EVIDENCE按前置计划完成原文重建、身份/资格预判后，仅在决定第一次进入Apollo、enteredAt为空且没有旧search时创建对象；DIRECT_CLEAR不创建也不需要机构解析。
- 原全名→公开别名顺序不变；NARROW仅使用已保存queryInstitution。进入APOLLO_SEARCH时同事务路径保存对象、stage、空search，再请求。旧在途无对象就继续前置版本查询构造，不能注入新机构重排queryIndex。
- 新页返回合并search后，只更新对应APOLLO_CANDIDATE解析记录；PROFILE/ORCID/queryInstitution保持冻结。DECIDE有新对象时按当前合法重建事实复核：PROFILE/ORCID/queryInstitution必须仍与冻结对象一致；若前置Parser重建改变这些输入，返回PENDING + EVIDENCE_CONFLICT，不刷新成另一份上下文继续准入、不补HTTP。候选派生项只能根据已保存search补齐并复核。无对象的旧DECIDE保留旧模式。
- 将新上下文传给每个 evaluate 调用，包括 ITEM_LIMIT、正常finish和缓存恢复。finish/retry/fail/reschedule保存整份payload，不重建成缺新成员的DTO。DTO中的version/诊断状态先按字符串接收再受控校验，未知值不得在解析阶段丢证据；如果整份JSON反序列化失败，失败保存必须沿用lease.evidenceJson原串，不能用初始空payload覆盖。
- QueryService.record:136 将payload上下文传给规则以计算missingFields；不增加接口字段、写库或新的文案/CSS。未知版本/损坏上下文不能导致整个 records GET 500：对该条missingFields返回明确 `institutionSubject`，记录仍按已保存state/outcome展示；不据读请求改写结果或假准入。该容错不适用于Gate，Gate必须失败封闭。
- 新审计对象通过已有数据库只读导出/测试报告查看；页面暂不新增“主体”字段。Apollo实际请求的request_json和原response仍在原attempt表中。

### T-4 验证与对照（I-1～I-7）

新增 `DiscoveryInstitutionNormalizerTest.kt`、`institution-subject-cases.json`；修改 `DiscoveryVerificationRulesTest.kt`、`DiscoveryVerificationWorkerTest.kt`、`DiscoveryVerificationFlowIT.kt`。不另建重处理工具。

- 纯解析测试加载固定案例，输出原值/主体/状态/移除依据/预期对照；随机打乱重复块的去重顺序不改变主体集合。上下文篡改、版本未知、原值hash错、500边界、Unicode、校区和医院负例必须覆盖。
- Worker测试覆盖旧在途、新在途429后重启、断点在第二页、ITEM_LIMIT、completion保存失败、证据/上下文持久恢复；验证URI解码后q_organization_name与attempt.request_json的主体一致，宽搜候选不被窄搜空结果覆盖。
- FlowIT用真实MySQL+现有模拟HTTP/ES，从真实public ORCID JSON→Parser→Worker→Gate→Commit→投影→QueryService穿透；在同文件覆盖Gate旧记录兼容、未知/篡改字段拒绝和GET容错，不扩改GateTest/QueryServiceTest文件。运行现有两者测试作回归。FlowIT将各案输入、实际URI/请求JSON、持久结果、Gate与QueryService结果、投影和保护断言输出到 `target/institution-subject-flow/<case>.json`（只含隔离fixture），供A-2～A-5人工查看，不要求验收人读测试源码。
- NormalizerTest提供可选离线数据审计：`-DinstitutionAuditInput=<inventory绝对路径> -DinstitutionAuditOutput=<新建输出目录>`。无参数时只跑确定fixture；有参数但路径/格式错误必须失败，不能静默SKIP。输入只读，输出不可覆盖既有目录。固定fixture每次另输出 `target/institution-subject-fixtures/summary.json`、`cases.jsonl` 供人工查看（只允许覆盖本次构建target产物）；数据审计生成summary.json、cases.jsonl，带输入SHA、条数、明确/歧义/未解决、超限、丢弃片段；全量输出姓名邮箱不重复复制，机构原文里的邮箱脱敏。
- RulesTest提供可选固定证据对照：`-DinstitutionReplayInput=<同一冻结核验输入> -DinstitutionReplayOutput=<新目录>`，按T-0确认的前置工具实际格式读取。使用同一UTC时钟，旧组 institutionNormalization=null、新组显式build，禁用网络/库写。输出每item原/新outcome、personId、原因、机构解析链、负向变化；重复两次语义一致。这里新组上下文只在内存生成，不升级生产旧item。
- 保留三阶段材料：前置修复前、前置修复后、本次机构优化后。只有输入SHA与时钟相同才给三列直接比较；旧材料不可复现时标 UNAVAILABLE，不拼凑。历史41,844仅作格式分布，不能作为核验真值。
- 提取全部变化不能自动视作正确。按RESOLVED新增、AMBIGUOUS、UNRESOLVED及医院/校区/联合机构分层复核，每层固定seed选20条或不足时全量；用于描述抽样结果，不宣称总体准确率。固定真实核验队列的新增身份匹配和负向变化必须逐条复核，有一条已确认误合并就不发布。

### T-5 发布与运行边界（I-4/I-6/I-7）

本轮只交付计划，不部署/重启/批量重跑。后续获实际执行授权时：先机器验证和人工验收，再在现有任务自然结束后部署；不要为改机构中断用户任务。新版只对新进入Apollo的合法项启用主体模式，历史已完成项不自动采用新规则。

回滚必须先验证旧Gate读取新上下文后的结果兼容。旧程序会忽略未知字段，可能把新主体准入重算成不匹配，故**不能承诺直接覆盖旧WAR可无损回滚**。若已有新准入，优先停新增核验并保留当前可识别版本的Gate，再制定受控回滚；禁止批量清空新成员或准入记录。该风险在上线前验收，不新增常驻调度/开关体系。

## 变更文件清单

恰10个文件，一个核验子系统。清单以外的实现文件均不在执行范围；文档回执可更新，不代表许可扩改业务。无前端文件。

| # | 路径 | 修改 |
|---|---|---|
|1|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizer.kt`|新增纯解析、比较、上下文DTO/builder/校验|
|2|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt`|新旧机构语义分流，两个比较点统一|
|3|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`|新对象留存、主体查询、恢复与全部判定传参|
|4|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGate.kt`|DTO兼容、上下文传递及失败封闭|
|5|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt`|同语义只读计算与单条损坏容错|
|6|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizerTest.kt`|解析矩阵、可选历史机构审计|
|7|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt`|对照、反例、可选冻结证据回放|
|8|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt`|实际请求、留存与恢复断点|
|9|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt`|真实SQL链路、Gate/QueryService兼容、保护与回归|
|10|`src/test/resources/discovery/institution-subject-cases.json`|脱敏真实机构样例与明确标注的构造反例|

## 验收标准

- I-1：原RAW/profile/事实/缓存/候选原值逐字段不变，institution主体不进入factFingerprint；SELF任职仍qualification UNKNOWN；不得增加provider来源。
- I-2：T-1矩阵全部通过；UC校区/医院大学/Example Technical/联合机构反例不合并；移除片段可解释；满500 ORCID不靠截断成功。
- I-3：唯一明确当前主体产生一次NARROW；多/未知当前主体NARROW=0；只具RAW机构、候选同名或仅邮箱域都不能确认本人；不完整search不准入。
- I-4：缺字段旧completed通过旧Gate矩阵；旧search恢复URI/page/queryIndex完全一致；旧DECIDE重放不凭部署自动加字段；未知版本拒绝且不降级；DIRECT_CLEAR流程不受新成员影响。
- I-5：Worker写入后重建服务，从SQL读JSON经Gate得到相同结论；改subject/raw/sourceRef/version任一项均不能通过Gate；QueryService与相同上下文规则missingFields一致，损坏单条不使列表500。
- I-6：429后恢复、预算、CAS、HTTP历史数与保护不回归；APOLLO_UNCLEAR经commit后admission_revision非空；候选投影失败重试不再请求Apollo；原留存测试通过，采样文件/配置零diff。
- I-7：全量机构报告records=41844、有值26134、缺失15710、输入SHA与附件相同；解析分类不与旧正则统计混为一谈。固定证据报告分母/输入SHA/时钟一致，新增/失去匹配逐条解释；未获取真实证据时不得把合成IT计作线上效果。

执行命令（JDK11；在独立构建目录运行，禁止与另一个执行任务共用target）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryInstitutionNormalizerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest,QualificationEvidenceParserTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT,DiscoveryVerificationReportingIT test
```

FlowIT与Repository/Reporting需真实Docker MySQL；SKIP不是PASS。数据审计命令由上述测试类配合T-4参数运行，输入/输出绝对路径及实际SHA写执行回执。真实样本对照是独立验收项，不以单元测试数量替代。

自查：I-1～I-7各有验证；每个新JSON含义/状态与兼容分支有不变量；共享store新增成员1；实现文件10；无前端；无新SQL writer；实际grep及schema已归档。前置未完成与执行接口复核是明确门槛，不包装为“已可直接执行”。

## 人工验收清单

本节为权威版。人工验收开始时才导出同目录 `apollo-institution-subject-repair-acceptance.md`，含A-n、勾选框、验收人、日期、结果/备注。现在不生成勾选副本。

### A-1: 真实长机构提取
- 前置条件：执行者在隔离环境运行 NormalizerTest 固定fixture，交付 `cases.jsonl` 和测试摘要；fixture含T-1全部输入，非用户凭据。
- 操作步骤：1. 打开Gachon案例。2. 对照原机构、主体和移除片段。3. 查看UC两个校区、Renji医院、Leidos多机构和粘连MIT案例。
- 预期结果：Gachon主体精确为Gachon University；UC保留Los Angeles/Berkeley且不相同；Renji及Leidos为AMBIGUOUS；粘连MIT为UNRESOLVED，未自动造机构名。
- 覆盖：O-1/O-2、I-1/I-2/I-7、X-1/X-4。

### A-2: 主体查询与未明确准入
- 前置条件：FlowIT提供隔离case及报告，姓名Ada Lee、绑定同一ORCID，SELF当前任职为T-1 Gachon长串，RAW同一长串；Apollo完整结果仅Ada Lee/Gachon University，资格未知，无其他硬阻断。报告包含实际URI、attempt.request_json、持久结果、QueryService输出与候选结果。
- 操作步骤：1. 查看实际请求中的q_organization_name。2. 查看该item持久结果及准入记录。3. 查看原始机构和派生机构。
- 预期结果：NARROW机构仅Gachon University；原长串不变；APOLLO_UNCLEAR、qualification UNKNOWN；自动准入1、其中Apollo1、未明确1、已明确0；admission_revision非空，候选投影PROJECTED或同身份ALREADY_PRESENT。
- 覆盖：O-1/O-2、N-1、I-1/I-3/I-5/I-6、X-1/X-2/X-3。

### A-3: 不能误并机构或凭名称确认本人
- 前置条件：FlowIT报告提供UC不同校区、医院对大学、同名同机构2个Apollo候选、仅RAW无当前任职、历史任职、ORCID矛盾、分页未完整案例；每案从同一隔离初始库独立运行。
- 操作步骤：1. 查看各案主体与原因。2. 查看新增准入数和NARROW请求数。
- 预期结果：每案新增准入0；同名2候选IDENTITY_AMBIGUOUS；无当前任职CURRENT_INSTITUTION_EVIDENCE_MISSING；多主体/未知主体不任意窄查；不完整查询SEARCH_INCOMPLETE。没有医院自动等于大学或UC校区合并。
- 覆盖：N-1/N-3、I-2/I-3、X-1/X-2。

### A-4: 旧记录、重启和损坏字段
- 前置条件：隔离FlowIT构造缺institutionNormalization的旧completed/旧在途，另有带新对象的429重试；提供重启前后请求、页码、结果；还构造未知版本和改subject两种损坏对象。
- 操作步骤：1. 对比旧记录升级前后Gate结论。2. 查看新旧在途重启后的下一次请求。3. 查看损坏对象的Gate和只读列表输出。
- 预期结果：旧completed结果不变；旧在途不换机构/页码；新版429恢复仍用相同queryInstitution、page，attempt累计不清零；损坏对象Gate不准入，列表能返回该条并显示institutionSubject缺项；新派生对象和原回执均保留。
- 覆盖：N-2/N-3/N-4、I-4/I-5/I-6、X-3/X-5。

### A-5: 人工保护、留存及页面回归
- 前置条件：FlowIT提供人工HOLD、已联系、APPLICATION、SBIR、指纹改变、投影失败再重试案例；隔离采样开启且未到配额，现有Sample测试产出目录校验；暂停写入的run记录revision/attempt数及修改前截图。
- 操作步骤：1. 查看保护案例准入变化。2. 查看投影重试请求数。3. 校验sample.json及正文hash/size。4. 连续刷新现有执行记录页与详情接口。
- 预期结果：保护样本新增准入0；投影重试新增Apollo请求0；SAVED正文hash/size失败0；原样本未人为删除清额度；GET前后revision/attempt不变，原列/页签/CSS无新增。未知资格唯一匹配仍按A-2准入。
- 覆盖：N-1～N-4、I-1/I-5/I-6、X-2/X-3/X-5。

### A-6: 同输入效果报告
- 前置条件：前置计划已冻结真实核验证据及UTC时钟；按T-4生成新旧组报告，另对41,844机构清单生成独立格式报告。执行者提供输入SHA与未解决列表。
- 操作步骤：1. 核对两组核验输入SHA/人数/时钟。2. 逐条查看新增匹配和负向变化的主体、姓名、ORCID证据。3. 对照格式报告与身份报告的分母。4. 查看抽样人工复核结果。
- 预期结果：同输入两次运行语义一致；格式报告41844条、机构非空26134、缺失15710；身份报告单列新匹配/失去匹配/仍未解决，不以RESOLVED数冒充准入人数；未知真值不报准确率。任一确认误合并阻止发布；缺少真实核验证据时标NOT_EVALUATED，不能将该项勾为完成。
- 覆盖：O-2、I-7、X-4。

## 知识使用与计划阶段结论

本轮加载7条知识：论文署名不等于任职、缓存重放、审核读写对齐、ORCID首条歧义、作者身份证据、采样非消费、量化回执，均已更新hit_count/last_used并在不变量/审计使用。均为近90天记录，无衰减归档。两条采集实现旧事实按本轮源码纠正；保留历史发现时间。既有CLAUDE已收录达到阈值的作者身份和量化回执规则，不重复追加。此主题未达到5条同题知识，不强行合并不同领域。仓库没有agents/或templates/角色上下文目录，域级交互规则在CLAUDE团队知识区追加索引，不新建角色体系。

计划新增可复用结论：机构比较并非仅供Worker，Gate会复算、QueryService会计算missingFields；新算法须携带可识别的持久模式并保持旧缺省语义。写回独立知识条目，说明这是已核实的交互和拟定边界，不声称实现已完成。
