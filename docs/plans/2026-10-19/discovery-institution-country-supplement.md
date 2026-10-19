# 新发现档案：机构国家补全子计划

日期：2026-10-19。状态：已规划，未实现、未部署，真实补全率尚未测量。

入口：[机构主体修复计划](apollo-institution-subject-repair.md)。顺序：Apollo 前置修复01/02 → 机构主体修复及验证 → 本子计划及验证。禁止两个执行者同时改共享调用链。执行前按 T-0 复核实际代码；不能把本文引用的拟新增接口当作现有实现。

## 需求描述

- O-1：对开启本功能后首次登记核验的新档案，只在 `country` 为空且机构、作者有可靠绑定时补机构国家。优先复用本篇 OpenAlex 作者机构；没有可用结果时尝试绑定 ORCID 的公开当前任职。页面已有“所在地”直接读取该字段。
- O-2：每条已登记档案保存采用来源、机构、国家、依据时间和跳过原因；固定输入对照分别统计补齐、冲突、无证据、请求失败，能够区分合成测试与真实效果。

必须保留：N-1 原机构、任职、类型、国籍、身份绑定、博士/年龄/职称资格规则及 APOLLO_UNCLEAR 准入语义；N-2 旧档案、旧核验快照、人工/已联系/APPLICATION/SBIR保护、租约与准入revision；N-3 原论文样本/HTTP回执、采样配额与过期策略；N-4 页面DOM/CSS、Apollo请求计数含义和GET只读。

范围外：补城市/个人居住地/国籍；历史回填；联网ROR/机构百科/LLM；改作者绑定；用邮箱后缀推国家；从作者 last_known_institutions 任取国家；复用被旧投影丢掉的ORCID国家；改核验中的RAW；重写签名协议；新表、ES字段、定时补全任务、前端控件。

**规模：8个实现/测试文件，采集登记与国家解析两个紧邻模块。只新增已有 `snapshot_json` 顶层可选成员 `countrySupplement`；不改ES schema。** 这与机构阶段的 `evidence_json.institutionNormalization` 分步执行、分别验收，不把18个文件伪装成单个计划。

## 关键不变量

### Invariant I-1: 国家语义和匹配证据
- Rule：country表示展示机构所在国家，不表示国籍或个人所在地。原值非空一律保留，包括非标准原值；未知只指null/空白，不擅自清洗字面值“未知”。只接受来源明确的两字母国家代码，trim后大写，且CountryContinentMapping可识别。机构名缺失、作者歧义、机构歧义、多个候选国家、缺失国家均不猜。
- Applies to：国家解析器、登记入口、统计和人工报告。
- Violation consequence：国外校区、医院或历史雇主国家被填给错误的人。
- 来源：K-institution-type-must-share-source、K-openalex-institution-two-sources。

### Invariant I-2: 首次冻结前补全
- Rule：先查所有既有保护和已存在的核验item，再决定是否补国家。只对没有既存item的新登记计算一次。补完后用原机构证据来源调用既有institutionEvidence重签，再计算最终snapshot/factFingerprint；同一份最终profile供eligibility、RAW映射、登记与候选投影使用。不存在合法原机构token、不允许身份、无核验run或开关关闭时不补。不得在Worker、Commit或投影之后补country，不改签名算法，不排除签名字段。
- Applies to：Registration.reserve、ExpertDiscoveryService两个消费分支、后续只读Gate/Commit。
- Violation consequence：RAW和冻结快照不同，FACT_CHANGED或机构证据失效。
- 来源：K-country-bound-to-institution-token。

### Invariant I-3: 重试使用已冻结事实
- Rule：已有同doc/identity/policy的RESERVED item只能恢复其已存profile；先比较来源及去掉country/institutionEvidence后的输入事实，任一其他事实变化按登记失败处理。旧item无countrySupplement也不升级。并发reserveItem若返回另一调用已创建的item，返回该赢家的冻结profile，不能把本调用计算的country写入RAW。其他状态或来源不一致不得重建/重写。持久化失败按原登记失败路径返回，不伪装“补全失败继续写RAW”。
- Applies to：reserve前查、reserveItem返回处理、RAW失败后的重试、跨进程并发。
- Violation consequence：重试不同HTTP结果覆盖冻结事实，或签名通过但存储事实不同。
- 来源：original；现有checkedIdentity只检查身份键，不检查事实。

### Invariant I-4: 回执不是资格授权
- Rule：countrySupplement缺失＝旧数据/本功能未执行，不等于失败。存在时仅接受COUNTRY_V1审计结构；补齐值、补齐前空值、原token来源、最终profile一致。该成员不传给Rules作为学历/年龄/职称证据，不新建Apollo/ORCID verification attempt，也不修改其request_count。ORCID解析中临时生成的事实不存入最终evidence_json。新receipt不覆盖旧snapshot或HTTP回执。
- Applies to：新DTO、Registration序列化、Worker/Gate/Query兼容、审计报告。
- Violation consequence：国家补全被当作身份/资格确认，或计数虚增。
- 来源：K-discovery-admission-read-write-parity。

### Invariant I-5: 请求有界、失败不阻塞收录
- Rule：OpenAlex路径新增HTTP=0。ORCID最多一次GET /{boundOrcid}/employments，无person/education/detail/姓名搜索、重试或重定向。每进程国家补全服务最多1并发、滚动60秒10次/滚动3600秒100次；拿不到许可立即跳过，不等待额度。每次实际发出（含失败）占额度，重启重置该内存上限，不能称持久日配额。连接<=2秒、读取<=3秒、读取响应体<=2MiB；不宣称这些配置等于严格端到端5秒deadline。429按Retry-After暂停这条补全通道，未给合法值暂停60秒。限额/HTTP/解析失败只留下原因，继续原收录流程。取消或线程中断必须传播原取消语义，不吞掉继续收录。
- Applies to：新公开HTTP读取、许可计数、调用出口和测试。
- Violation consequence：新增抓取拖垮发现、错误重试造成请求风暴或吞取消。
- 来源：original。

### Invariant I-6: 效果可复算且不夸大
- Rule：分母是固定新登记档案集合；同一输入、UTC时钟、上游回执对照。记录新增非空、保持非空、冲突/无证据/限流/失败及机构一致性人工判断。请求成功、出现国家均不等于正确补齐。历史41844机构清单不含country，不能据此计算国家缺失或补全率。
- Applies to：fixture、真实冻结样本、发布说明。
- Violation consequence：把测试覆盖率或来源命中率冒充线上效果。
- 来源：K-plan-quantified-claims-need-grep-receipts。

## 现状审计

### 可核对的原因与实现接缝

代码逐行/哈希：[country-code-baseline.txt](apollo-institution-evidence/country-code-baseline.txt)、[country-code-hashes.json](apollo-institution-evidence/country-code-hashes.json)。全局读写检索：[country-paths.txt](apollo-institution-evidence/country-paths.txt)、[country-store-writers.txt](apollo-institution-evidence/country-store-writers.txt)、[country-registration-callers.txt](apollo-institution-evidence/country-registration-callers.txt)。行号以此基线为准，执行前按方法名复核。

| 已核实位置 | 事实及含义 |
|---|---|
| OpenAlexDataSource:103–136、287–308 | JATS已有机构名时不调用boundOpenAlexInstitution，即使country空；OpenAlex authorship同机构country_code已在PaperAuthor里，无需新查API。 |
| JatsXmlEmailParser:194–228 | 只解析同一结构化aff的country；自由文本地址不保证有结构country。 |
| OrcidDataSource:166–177；ExpertDiscoveryService:1585–1595 | expanded-search只有唯一institution-name；country显式为null，不能假称已有当前任职地址。 |
| OrcidVerificationEvidenceClient:125–140 | selectedFields丢弃organization.address.country。旧缓存无法凭空恢复；本子计划不把它当数据源。 |
| QualificationEvidenceParser:43–88 | 能解析来源、jsonPointer和CURRENT/HISTORICAL；前置修复完成后复用其公开性/时间语义，不另写宽松版CURRENT。 |
| ExpertDiscoveryService:1460–1475、1819–1838 | 两个实际reserve入口都先算eligibility/profileMap再reserve；本次必须调整为reserve返回最终profile后再构造映射。只改一个会漏同步ORCID消费。 |
| RegistrationService:77–103 | reserve已读所有ES层、admission、contact保护，然后一次写冻结snapshot，正好在RAW创建之前。当前返回值没有最终profile，需要显式扩展。 |
| DiscoveryIdentity:135–160；Gate.snapshot:83–100 | country参与机构token；token进入factFingerprint。不能直接在已登记RAW上补country。 |
| Repository.reserveItem:169–185、checkedIdentity:468–470 | 唯一键复用既存行，checkedIdentity不校验factFingerprint。必须处理并发赢家及复用原profile，不能只查一次后假设自己插入成功。 |
| app.js:13693/14301；ExpertSearchService:159–180/483/597 | 页面读country为空则“未知”；查询与地区筛选也读ES country，不能仅前端临时展示地址来冒充补齐。 |

官方字段语义参考：[OpenAlex authorships](https://help.openalex.org/data/authorships/)、[ORCID employment metadata](https://support.orcid.org/hc/en-us/articles/360006894694-Metadata-in-the-Employment-section)。这些只证明字段含义，不证明本批样本有值或匹配正确。

### ES RAW / CANDIDATE / APPLICATION

- Schema：已读取三个 `src/main/resources/es/orcid_info_{raw,candidate,application}.json`；dynamic:false，country与institutionEvidence均keyword，原字段无需迁移。新审计对象只在SQL快照，不塞externalIds，不增加ES字段。（来源：K-expert-profile-source-sync、K-es-mapping-single-declaration-source）
- 本次写入口：ExpertDiscoveryService两个消费分支→toIndexMap→Writer.indexToRaw（discovery使用op_type=create）；随后原Commit/project→Writer.reconcileDiscoveryCandidate透传。
- 其他既有写者：Writer的promoteToCandidate/promoteToApplication/demoteToRaw、人工保存/导入及原profile全量透传；全局命中见附件。它们不调用本补全服务、不补历史country，不改写。Academic updateExpertAcademicFields:3254已禁止跨源覆盖institutionType；旧知识中的“现会覆盖”已过期。
- 读者：Writer.discoveryProfile、ExpertSearchService.toExpertProfile/sourceFields/地区筛选、DiscoveryAdmissionPolicy.countryReasons、Identity验签、Gate指纹、ContactCountryBackfillService及UI。contact国家同步保持原服务，不由国家补全新开SQL更新路径。

### MySQL 核验item及其他账本

- V158：snapshot_json MEDIUMTEXT；唯一键(doc,identity,policy)；state默认RESERVED。只追加顶层countrySupplement；Repository.reserveItem仍是唯一生产snapshot写入口。不会UPDATE旧snapshot_json，不新增schema。
- snapshot读者：Worker、Gate、QueryService均用忽略未知字段的VerificationSnapshot反序列化；Repository reporting用JSON_EXTRACT读取profile姓名/机构。新根成员不改变既有解析。登记恢复将新读取冻结profile，必须验证身份、来源及基线事实。
- 原evidence_json/attempt/run/admission读写全集沿用入口计划现状审计及附件；本子计划不向这些列写国家补全结果。HTTP只在事务锁之外执行。
- 论文采样capture/store/scan/replay/cleanup全部不改；国家审计来自snapshot，不能称论文EXTRACTION样本已经包括本次ORCID HTTP原文。

### 交互点

- X-1：PaperMetadata作者结构 → 国家选择 → 原profile country；作者和机构要同时绑定。
- X-2：ORCID原响应 → 既有Parser当前任职/jsonPointer → 同organization地址；不能跨summary拼机构和国家。
- X-3：补全后profile → token → 冻结snapshot → RAW → Gate/Commit → candidate → 搜索/页面；整链一致。
- X-4：reserve重试/并发 → 旧snapshot/赢家snapshot → RAW；必须相同profile。
- X-5：额外公开请求 → receipt及独立服务日志；不得混入Apollo漏斗、attempt数和论文抽取失败统计。

## 实现方案

### T-0 执行前复核和基线（I-1～I-6）

只读核对：前置两阶段已完成；机构Normalizer实际API；Parser大小写公开性及SELF来源合同；全部reserve调用及snapshot消费者；profile字段在原分支是否还有隐藏中间写入。更新附件哈希。不在前置未完成时猜接口。行为/白名单变化先修订计划，不扩改文件。

建立fixture包括本节所有正反例及冻结UTC时钟。真实样本另行从获授权只读的任务item、对应RAW、论文PaperMetadata/保留回执获取；记录executionId、读取时间、文件SHA、完整/缺失原因。若不能得到完整输入，标NOT_EVALUATED，不能拿41844机构清单代替。此计划编辑阶段没有查询最新线上country数量。

### T-1 纯选择和回执（I-1/I-4/I-6）

文件：新增 `DiscoveryCountrySupplementService.kt`（DTO、纯选择与有界读取同文件，避免零散框架）。依赖已有Normalizer、QualificationEvidenceParser和CountryContinentMapping，均只读复用。

国家判定比Apollo主体判同更保守：必须双方完整机构名经NFKC、空白折叠、大小写归一后相等，且Normalizer双方RESOLVED、医院/校区/多机构边界一致。**主体相同但完整机构不同，先不补国家**；不因清洗掉地址而把境外校区等同本部。这不改变Apollo主体匹配规则，接受较低覆盖率。

OpenAlex：仅paper.source=OPENALEX且作者的institutionSource=OPENALEX；通过profile已验证的OpenAlexAuthorId唯一绑定到paper.authors，同时所有出现的ORCID不得冲突。不以姓名/邮箱域绑定。一个author ID多条不一致信息或多机构均拒绝；同机构同国家完全相同的重复可去重。国家取该唯一PaperAuthor.institutionCountry。不能取论文其他作者、作者enrichment或检索国家。原机构已有非空国家时不检查/覆盖；无可用信息可走ORCID；已知绑定/机构/国家冲突则终止补全，不能用另一源洗掉冲突。

ORCID：只有合法boundOrcid才尝试；结果在内存构造OrcidEvidenceDocument并调用既有Parser，使用其 `field=employment`、CURRENT、ORGANIZATION或SELF、无冲突事实。UNKNOWN来源不采信。用每条fact.jsonPointer在同一响应取 `organization.address.country`；先确认pointer对应原机构和同put-code，禁止找“响应里任意country”。完全匹配机构的当前条目必须都有合法国家且去重后一个国家；多国、缺值、历史/未来/不明任职均不补。summary不足时不追加detail请求，记NO_EVIDENCE。这里SELF仅作机构地点线索，不升级学历/职称可信级别。

回执结构（示例键为合同；状态枚举仅用于该审计对象，不新增业务状态）：

```json
{
  "version": "COUNTRY_V1",
  "status": "FILLED",
  "reason": "MATCHED_BOUND_INSTITUTION",
  "beforeCountry": null,
  "afterCountry": "US",
  "originalInstitutionEvidence": "JATS:<原64位摘要>",
  "institution": "Example University",
  "selectedSource": "OPENALEX",
  "constructedAt": "2026-10-19T00:00:00Z",
  "observations": [{
    "source": "OPENALEX", "sourceRef": "doi:10.example/test#author=A123",
    "institution": "Example University", "country": "US",
    "inputSha256": "<结构化输入摘要>", "fetchedAt": null,
    "publicationYear": 2026
  }]
}
```

- status仅FILLED/UNCHANGED/UNRESOLVED/CONFLICT/ERROR/SKIPPED；reason固定枚举：MATCHED_BOUND_INSTITUTION、EXISTING_VALUE、NO_EVIDENCE、IDENTITY_UNRESOLVED、INSTITUTION_MISMATCH、MULTIPLE_COUNTRIES、INVALID_COUNTRY、HTTP_ERROR、INVALID_RESPONSE、RATE_LIMITED、BUDGET_EXHAUSTED、EVIDENCE_TOO_LARGE。
- observations最多20条，每条机构原文上限2000字符；超过上限不截断后继续判定，记EVIDENCE_TOO_LARGE并不填。只存验证所需机构、代码、来源绑定、日期/来源类别和pointer，不存个人通讯地址/邮箱/Token/整个响应。ORCID observation存endpoint、put-code、jsonPointer、来源类别、起止日期、responseSha256和真实fetchedAt；OpenAlex无抓取时间就保留null，不能用登记时间冒充上游抓取时间。
- originalInstitutionEvidence、beforeCountry、afterCountry与最终profile用于离线重算；inputSha256是明确列出的结构化输入摘要，不冒充HTTP原文摘要。失败允许没有body/hash。旧snapshot无该对象按旧语义读取。

### T-2 有界公开读取（I-4/I-5）

文件：同一新增服务；`DiscoveryVerificationProperties.kt`。

新增单一部署开关 `countrySupplementEnabled:Boolean=false`，绑定 `talent-introduction.discovery-verification.country-supplement-enabled`；无需改application.yml。国家服务开关关闭或无核验run＝旧行为。开关覆盖两种来源，不影响旧item恢复，不添加前端开关。

复用现有配置orcidPublicBaseUrl/orcidAccessToken，独立专用RestTemplate/请求工厂；HTTPS固定base + 校验过的ORCID + `/employments`；Accept application/json；不拼候选返回URL，不日志输出凭据。按I-5配置超时、禁止跳转、流式限量读取和请求许可；不伪造VerificationHttp的RequestPermit来绕过其账本合同。仅存成功响应的有限字段用于审计，错误只保留有限错误码。解析器处理不足时继续保持未知。

成功、失败和跳过输出一条结构日志，键固定 `event=DISCOVERY_COUNTRY_SUPPLEMENT`、executionId、docId、source、status、reason、httpRequests(0或1)、elapsedMs；不输出人员姓名/邮箱或原文。此日志是公开补全请求记录，UI既有“Apollo请求”不包含它。补全在业务核验之前，后续Worker可能再次请求ORCID employments；**本轮接受这一次额外请求，不承诺复用现有Worker缓存或零额外耗时**。暂不建第二套持久缓存/调度/配额库。

### T-3 冻结登记与消费一致性（I-2/I-3/I-4）

文件：`DiscoveryVerificationRegistrationService.kt`、`ExpertDiscoveryService.kt`。

1. reserve尾部加可选paper参数，旧调用默认null。DiscoveryRegistration尾部加可选preparedProfile（默认null），不更改既有位置参数。将原profile、paper传给登记层；新补全只发生在reserve内查完保护之后、repository.reserveItem之前，网络不在SQL事务中。
2. 原层级/contact/manual/SBIR保护保持原判定。检查是否已有相同identity/policy item；来源不同失败。RESERVED同源先验证冻结输入的其余事实与本次一致，然后直接返回冻结profile（无HTTP）；不覆盖旧国家或新增receipt。其他状态不允许重建RAW。原recoverDuplicate仍处理已有RAW的恢复。
3. 全新记录原机构token必须有效，country补成功后用原token前缀调用DiscoveryIdentity.institutionEvidence生成最终token。失败或无法签发则保留原profile、记录未补原因；绝不清空token。country和token之外，所有profile字段保持一致。SQL冻结前把countrySupplement放在snapshot根对象。
4. reserveItem返回后，再按上述相同校验读取真正持久化的profile并返回给消费端，即使是并发赢家，也不能用局部补全结果。最终profile需能够按相同config重算得到item.factFingerprint。不同source/不相容输入报登记失败，不修改赢家。
5. ExpertDiscoveryService同步ORCID分支与consumeOutcomeInternal统一按顺序：原身份/邮箱/去重门禁 → 构造初始profile → reserve → protected直接退出 → preparedProfile或原profile → eligibility → filterResult/rejectReasons → toIndexMap → 原indexToRaw/activate/准入/入队/人物关系。RAW写入的profile必须就是冻结值；没有run时仍原行为。队列纸面/ORCID工厂保持相同主键和论文上下文。
6. 不修改Repository、Gate、Commit、QueryService、ES writer；用集成测试证明它们从新冻结profile继续读到同一country/token。不得写新CAS更新去补旧文档。

### T-4 验证、上线与回滚（I-1～I-6）

文件：白名单中的3个测试类和fixture。单测验证纯选择、有界HTTP；登记测试覆盖保护前置与恢复；FlowIT用真实MySQL与隔离ES验证RAW→候选→查询/地区过滤及签名，不以全mock代替这条集成路径。

首次上线开关关闭；通过验收后对新任务开启。不要中断运行中的用户任务。本计划不授权立即部署。回滚优先关闭开关；已冻结新国家和token不回写、不删除receipt，现有Gate按相同签名算法应继续验证。旧WAR是否理解机构主体新协议仍服从入口计划的回滚边界，不能据此宣称整个项目可以直接降级。

输出真实对照报告必须有输入SHA、UTC时钟、eligibleMissing/filledByOpenAlex/filledByOrcid/unchanged/conflict/noEvidence/error/skipped、原/新country与source证据表；各数按doc去重且互斥总和一致。新增FILLED逐条核对机构和国家，出现错人/错校区则不启用；没有完整真实输入时明确报告NOT_EVALUATED，不给成功率。请求日志数和已登记receipt数可能因登记失败/并发不同，分别报告，不能强行凑等。

## 变更文件清单

| # | 路径 | 修改 |
|---|---|---|
|1|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementService.kt`|新增纯选择、有限公开读取、限流和审计DTO|
|2|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt`|保护后补齐、冻结回执、恢复赢家profile|
|3|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`|两个消费分支使用登记返回的最终profile|
|4|`src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryVerificationProperties.kt`|一个默认关闭开关|
|5|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementServiceTest.kt`|新增纯选择与HTTP、时间/额度测试|
|6|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountryRegistrationTest.kt`|新增保护、并发、冻结和兼容测试|
|7|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementFlowIT.kt`|新增真实存储链路、查询和对照报告|
|8|`src/test/resources/discovery/institution-country-cases.json`|新增脱敏与合成标注fixture|

无前端文件，无CSS变更；不创建新class或改现有DOM。现有页面国家值改变是数据变化。没有把country混入externalIds规避mapping审计。（来源：K-expert-profile-source-sync）

## 验收标准

- I-1：已有US始终US，已有“未知”不擅改；同人同机构US可补；同名异人、医院/大学、不同校区、机构名缺失、多个国家、完整机构名不等均不填；国籍不变。
- I-2：成功后validInstitutionEvidence=true，snapshot.profile.country=RAW.country=candidate.country；Gate无新增FACT_CHANGED/机构签名失败。仅country及对应token变化，证据/身份/资格字段相同。
- I-3：RAW创建失败重试、并发一US一GB、旧RESERVED无receipt、已RUNNING/COMPLETED、输入机构变化均覆盖；赢家profile唯一，重试无新HTTP；不覆盖任何旧item。
- I-4：新receipt能解析且固定输入可复算；旧snapshot三读者不报错；attempt/request_count/Apollo总请求不因国家补全+1；APOLLO_UNCLEAR仍经Gate/Commit准入；采样源码配置零diff且原回归通过。
- I-5：Mock服务计数：OpenAlex成功0请求，ORCID最多1；单进程10次/分钟、100次/小时、并发1；额满立即跳过；429暂停、404/500/超时/非法JSON/超大响应/取消/中断分别验证；无无限重试、无跨域跳转。测试用注入clock推进，不实际等一小时。
- I-6：固定fixture前后对照可重放；真实报告的数值由真实冻结输入计算，非完整数据标NOT_EVALUATED；新增填充逐条人工审计，不承诺预设成功率。

建议在隔离工作目录、JDK11执行：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryCountrySupplementServiceTest,DiscoveryCountryRegistrationTest,DiscoveryIdentityTest,CountryContinentMappingTest,QualificationEvidenceParserTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryCountrySupplementFlowIT,DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT test
```

新增FlowIT缺Docker/真实MySQL或隔离ES须明确SKIP，不能当PASS。实际前置测试类/环境变化在T-0核对并更新命令，不能宣称本计划阶段已运行业务测试。

## 人工验收清单

开始人工验收时才导出同前缀acceptance.md。执行者先用fixture+隔离环境提供case ID、查询入口、SQL只读回执和测试报告；验收者无需阅读代码。

### A-1: OpenAlex零请求补国家
- 前置条件：fixture OA_MATCH：身份绑定唯一A123；RAW初始机构Example University/country空；同篇唯一同名机构country=US；开关开。通过FlowIT在隔离环境创建该case。
- 操作步骤：1. 打开测试报告与专家详情。2. 按North America筛选。3. 查看该item的snapshot审计与HTTP计数。
- 预期结果：所在地US，区域筛选能找到；receipt FILLED/OPENALEX/US；额外HTTP=0；机构和国籍与初始相同；最终签名有效；若原资格满足则候选同为US。
- 覆盖：O-1/O-2、N-1/N-4、I-1/I-2/I-4、X-1/X-3/X-5。

### A-2: ORCID同机构当前任职与负例
- 前置条件：fixture ORCID_MATCH无OpenAlex国家，绑定合法ORCID；公开当前SELF任职Example University，organization.address.country=GB；另准备HISTORICAL、OTHER_INSTITUTION、TWO_COUNTRIES、MISSING_COUNTRY、CAMPUS_MISMATCH、NO_BOUND_ORCID六例，HTTP mock响应固定。
- 操作步骤：1. 在隔离环境分别执行。2. 查看country、receipt、HTTP计数、学历/职称结果。
- 预期结果：MATCH填GB且请求1；六个负例均维持空值、页面未知；NO_BOUND_ORCID请求0；没有新增博士/职称事实。MATCH可属于APOLLO_UNCLEAR，原准入合同保留。
- 覆盖：O-1/O-2、N-1、I-1/I-4、X-2/X-3。

### A-3: 非空、旧记录和保护
- 前置条件：隔离库准备country=US、country字面“未知”、旧RESERVED、旧COMPLETED、人工准入、已联系、APPLICATION、SBIR各一例；提供运行前导出SHA。
- 操作步骤：1. 开启开关触发重复发现。2. 对比字段/旧快照/请求数。3. 关闭开关运行一条全新空国家案例。
- 预期结果：已有country逐字不变；受保护及旧item无国家补全HTTP/无新增receipt；旧快照字节不变；关闭开关的新案例维持空值。资格和已准入结论不被清空。
- 覆盖：N-1/N-2、I-1/I-2/I-3、X-4。

### A-4: 故障、并发及重启
- 前置条件：FlowIT构造首次SQL登记成功但RAW写失败；另构造相同身份两个并发调用分别得到US/GB，由SQL先持久US；另提供429、500、超时与预算耗尽案例。
- 操作步骤：1. 重启服务后重放失败RAW案例。2. 查看并发赢家快照及RAW。3. 查看故障案是否继续入库及日志。
- 预期结果：重启复用已存profile，不重新请求ORCID；并发最终snapshot/RAW都是US，不能一边GB；故障均country空但原收录继续，receipt为ERROR或SKIPPED及精确原因；限额不等待。登记SQL失败则没有RAW创建，不吞成普通HTTP失败。
- 覆盖：N-2、I-2/I-3/I-5、X-3/X-4/X-5。

### A-5: 留存、统计和真实效果
- 前置条件：执行者提供原EXTRACTION样本包清单、相同固定输入前后报告、国家receipt、独立国家请求日志、原Apollo统计，并列明哪些是合成案例、哪些是实际冻结样本。
- 操作步骤：1. 核对每条新增country的作者/机构/国家依据。2. 汇总分桶。3. 对照Apollo计数和论文样本。4. 查看页面布局与开关关闭前截图。
- 预期结果：分桶总和等于固定分母；新增country逐条有依据，缺完整证据标NOT_EVALUATED；国家请求没有混入Apollo请求或论文失败数；原样本仍可读取/回放；DOM/CSS和原日志栏目不变。已知误填数须为0才能开启。
- 覆盖：O-2、N-3/N-4、I-4/I-5/I-6、X-5。
