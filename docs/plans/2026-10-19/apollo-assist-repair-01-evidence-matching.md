# 01：ORCID 证据解析与 Apollo 身份交叉匹配

状态：待执行。前置：阅读[总计划](apollo-assist-repair-master.md)。可独立部署；不自动重处理历史完成记录。

## 需求描述

O-1：公开ORCID返回能形成可查证据；绑定同一ORCID的SELF当前任职可参与Apollo身份交叉匹配，资格UNKNOWN且唯一确认本人的结果仍为APOLLO_UNCLEAR。
O-2：新任务使用真实公开别名和可用机构信息；日志区分分页截断与单档案请求上限。

必须保留：N-1 APOLLO_UNCLEAR自动准入、DIRECT_CLEAR/APOLLO_CLEAR含义和原资格规则；N-2 身份/机构/ORCID冲突、唯一候选、人工/联系/SBIR/APPLICATION保护；N-3 请求预算、请求留档、解析样本留存；N-4 原页面DOM/CSS、GET只读。
范围外：放宽sameName/maskedCompatible/sameInstitution、把论文署名当当前任职、把Apollo title或SELF教育当机构资格证据、补抓官网、修改学历/职称词典、历史批量排队（属02）。

## 关键不变量

### Invariant I-1: 公开字段解析一致
- Rule：两个visibility判断共用私有判断函数；文本public/PUBLIC及大小写变体允许；private/limited、空、非文本、显式null、缺失均不产生公开证据。这里明确收紧旧“缺失默认PUBLIC”的推断；测试fixture必须提供真实visibility，不能删除负例绕过失败。输出保留来源URL/HTTP哈希/时间/指针和真实visibility，不改原orcidCompleted。
- Applies to：QualificationEvidenceParser.emit与affiliation遍历；Worker重建。
- Violation consequence：公开证据丢失或把不明可见性当公开事实。
- 来源：original；真实public大小写实验。

### Invariant I-2: 身份证据与资格证据分离
- Rule：资格facts仍只取ORGANIZATION，原博士、年龄、职称逻辑不变；身份用的employment必须provider=ORCID、同identityHash/factFingerprint、value.orcid=snapshot.boundOrcid、CURRENT、机构名非空、assertionSource∈{ORGANIZATION,SELF}。UNKNOWN来源和历史/时间未知任职均不能参与身份匹配。SELF必须由Parser的source-orcid等于绑定ORCID生成，不能在规则里把UNKNOWN改名为SELF。
- Applies to：Rules身份机构集合、Worker新查询机构选择。
- Violation consequence：身份证据被错当资格证明，或论文署名推导现在任职。
- 来源：K-paper-affiliation-is-not-employment、K-author-identity-needs-email-evidence。

### Invariant I-3: 唯一确认与未明确准入
- Rule：除I-2的来源选择外，原身份验证、姓名冲突、同名多候选、显式ORCID矛盾、原机构冲突及complete/broadComplete门槛不变。匹配唯一且资格UNKNOWN→APOLLO_UNCLEAR；NOT_MET/CONFLICT不准入；不因Apollo有title直接改MET。
- Applies to：Rules→Worker→AdmissionGate→Commit；现有投影读取。
- Violation consequence：为提升数值误准入或暗改用户政策。
- 来源：original、K-discovery-admission-read-write-parity。

### Invariant I-4: 缓存可重解析、在途游标不能错位
- Rule：只在LOCAL_EVIDENCE和显式DECIDE重算分支重建可恢复证据；APOLLO_SEARCH恢复继续使用已冻结evidence与原queryIndex，不在中途增加别名/机构、更换查询序列。重建必须按endpoint文档逐条parse并distinct，得到与逐条HTTP callback累积相同的事实集合，不能跨endpoint错误去重。对有原文endpoint只替换该endpoint对应的ORCID派生事实；无原文的既有事实保留。原始文档、搜索候选、历史attempt不变。
- Applies to：Worker.process LOCAL_EVIDENCE/DECIDE；Parser新增可复用重建方法。
- Violation consequence：旧缓存永久空证据、反复追加、错页查询或丢审计证据。
- 来源：K-identity-cache-version-and-admission（应用其缓存原则，不改论文identity版本）。

### Invariant I-5: 查询与限额
- Rule：新任务保持全名→规范化去重的公开别名→可选机构查询顺序。机构仅在I-2可用机构去重后恰为一个时追加；多机构不firstOrNull猜选。不得降低页数/请求预算计数，不清零attempt/request_count。窄查询不能抹去宽查询候选或把不完整搜索改complete。
- Applies to：Worker查询构造、ApolloSearchEvidence.merge、Repository预算调用。
- Violation consequence：浪费请求、漏掉候选、伪造完整性。
- 来源：K-orcid-institution-first-is-ambiguous、K-discovery-budget-backpressure、K-discovery-reentry-keeps-run-accounting。

### Invariant I-6: 日志状态有实义
- Rule：分页上限保留SEARCH_INCOMPLETE并追加SEARCH_PAGE_LIMIT；单条请求上限在现有EVIDENCE_INCOMPLETE或SEARCH_INCOMPLETE之外追加ITEM_REQUEST_LIMIT。两者是reasons_json里的字符串码，不是新state/列。状态仍COMPLETED/PENDING，不伪称ERROR、无匹配或仍在排队。
- Applies to：Worker完成/ITEM_LIMIT分支、QueryService标签。
- Violation consequence：用户误判故障或认为会自动继续搜索。
- 来源：original。

### Invariant I-7: 存储与保护不变
- Rule：不新增DDL/ES字段/版本号；不修改采样配置和Capture/Store；commit/gate/reporting仍使用同一纯规则。新规则不得自动覆写已完成历史结论；人工批准/HOLD/已联系/APPLICATION保护保留。
- Applies to：部署、新核验、既有重验与展示。
- Violation consequence：污染历史、重复准入、停止留存。
- 来源：K-extraction-samples-are-not-consumption、K-admission-event-and-projection-counts。

## 现状审计

- 数据存储schema、读写全集见[总计划现状审计](apollo-assist-repair-master.md#现状审计)，本子计划涉及同一run/item/attempt持久链，以及沿用的admission/候选投影。完整读写回执位于 `apollo-repair-evidence/`，执行前重跑，不依赖旧行号。
- 缺陷：`QualificationEvidenceParser.kt:21,55` 两处大小写敏感过滤；`:23-26` 已区分ORGANIZATION/SELF/UNKNOWN；`:55-78` 已有日期和机构提取。
- 阻塞：`DiscoveryVerificationRules.kt:48-49` 资格与身份共用了机构来源jobs；`:81-109` 本人确认要求绑定ORCID、当前机构、候选唯一。变更只拆分身份机构证据集合。
- 缓存：`DiscoveryVerificationWorker.kt:149-175` callback才parse；`OrcidVerificationEvidenceClient.kt:70-85` 缓存命中不会触发同一callback；Worker`:204`已有DECIDE，不必新增状态。
- 新查询：Worker`:177-185`已有公开别名与窄查询；新证据恢复后会自然触发。API client字段、URI、鉴权不改。
- Gate：`DiscoveryVerificationAdmissionGate.kt:58-74`复算且比对outcome等；必须用FlowIT覆盖，不只验证纯规则。
- 日志：Worker`:201-214`截断及ITEM_LIMIT目前原因泛化；QueryService标签集中在`:158-171`，app.js`:27068`显示服务端label，无需前端改文件。无样式契约章节，因为变更白名单没有前端文件，禁止执行者自行美化。
- 旧FlowIT`:114`任职fixture缺visibility，会被I-1新合同拒绝；在本白名单内改成lowercase public，并加入非公开/缺失负例。这是已查到的交互，不允许临时恢复缺省PUBLIC来让测试绿。
- Interaction：X-1缓存→Parser→Worker→Rules/Gate；X-2 SELF匹配但资格UNKNOWN；X-3查询游标/预算→attempt报表；X-4commit→持久准入/投影；X-5留存不变；X-6原因码→UI。分别对应人工A-1～A-6。

## 实现方案

### T-1 解析与缓存重建（I-1/I-4/I-7）
修改 `QualificationEvidenceParser.kt`、`QualificationEvidenceParserTest.kt`：统一公开判断；新增 `rebuild(snapshot, documents, existingEvidence)` 纯方法，按document.endpoint逐份parse、按已有事实源URL精准替换同endpoint的派生字段（name/otherName/employment/education），保留其他provider和无对应原文事实；随后distinct。不改responseSha256或原JSON。输入文档与快照身份不符应失败封闭，不重新绑定身份。两次重建结果相等。缺原文时不发起补抓；如果旧已有证据的不可公开原文导致事实减少，报告变化而不是沿用被拒绝的对应旧事实。
修改 `DiscoveryVerificationWorker.kt`：LOCAL_EVIDENCE在local判定前从已保存documents重建；完成ORCID读取后再把read.documents纳入同一重建，保存重建结果才进入Apollo；DECIDE从保存documents重建并调用与Gate一致的 `rules.evaluate(snapshot,evidence,search)`，通过原finish/commit落库。APOLLO_SEARCH恢复不重建、不重排查询。

### T-2 拆分身份集合（I-2/I-3/I-5）
修改 `DiscoveryVerificationRules.kt`、`DiscoveryVerificationRulesTest.kt`：提取 `identityEmployments(snapshot,evidence)` 纯helper供Rules和Worker共享，精确实施I-2。现有qualification facts/jobs变量继续只消费ORGANIZATION；只替换身份路径的employments构造。身份机构集合保留全部不同机构，不按返回顺序选一个；原机构冲突规则不取消。不得修改sameName/maskedCompatible/sameInstitution/isProfessor/completedDoctorate。
修改Worker查询构造：别名仍从解析后的公开事实取；机构helper结果按sameInstitution去重，唯一时追加NARROW，多机构只做原宽搜/别名，不生成任意窄搜。新查询仍用现有计费、重试、持久completion机制。

### T-3 解释具体限额（I-5/I-6/I-7）
修改Worker、`DiscoveryVerificationQueryService.kt`：本页hasNext且页数达上限时保存SEARCH_PAGE_LIMIT；ITEM_LIMIT保存ITEM_REQUEST_LIMIT。不得因补原因码改变qualification/conflict、matchedPersonId或outcome。QUERY已完整时不附加截断码。标签逐字：SEARCH_PAGE_LIMIT＝“查询未完成：达到单次查询页数上限”；ITEM_REQUEST_LIMIT＝“核验未完成：达到单档案请求上限”。旧SEARCH_INCOMPLETE继续“查询尚未完成”，不得替旧数据猜测原因。
读路径QueryService.records/batches/item消费既有reasons_json，不新增DTO字段、分页或CSS；新增reason计数允许一人多原因，不能相加当人数。

### T-4 真实链路验证（I-1～I-7）
修改 `DiscoveryVerificationWorkerTest.kt`、`DiscoveryVerificationFlowIT.kt`、`DiscoveryVerificationQueryServiceTest.kt`，配合T-1/T-2测试；不用mock Parser返回全套正确facts代替真实lowercase输入。新增正例从HTTP JSON→Parser→Worker→MySQL→Gate→Commit→投影穿透；新增负例同名多候选/非当前/未知来源/绑定ORCID缺失/人工保护。执行已有AdmissionGate与Reporting/Repository集成、SampleStore/Capture/Replay测试，白名单外测试只运行不编辑。

## 变更文件清单

以下前缀展开为仓库根下绝对确定路径；除此之外禁止修改实现文件。9个文件，核验链路1个子系统。

| # | 路径 | 目的 |
|---|---|---|
|1|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/QualificationEvidenceParser.kt`|公开判断、纯重建helper|
|2|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt`|身份与资格集合拆分|
|3|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt`|缓存重解析、机构查询、限额原因|
|4|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt`|原因文案|
|5|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/QualificationEvidenceParserTest.kt`|真实可见性/重建回归|
|6|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt`|身份与资格隔离回归|
|7|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt`|恢复/游标/上限回归|
|8|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt`|端到端/保护回归|
|9|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt`|日志文案/只读回归|

## 验收标准

- I-1：同一人名/任职分别用public、PUBLIC产出同事实语义（excerpt保留原大小写）；private/limited/缺失/null/数值均0；真实来源字段不丢失。
- I-2：SELF当前任职＋唯一同名同机构Apollo→qualification UNKNOWN；SELF的Professor或PhD不能变MET；UNKNOWN/HISTORICAL/错ORCID不通过。
- I-3：UNKNOWN唯一匹配→APOLLO_UNCLEAR→admission_revision非空；候选2个→IDENTITY_AMBIGUOUS；姓名矛盾→NAME_CONFLICT；原机构矛盾→INSTITUTION_CONFLICT；NOT_MET/CONFLICT新增准入0。
- I-4：orcidCompleted有3份文档/evidence空的LOCAL和DECIDE恢复出facts且无额外ORCID请求；两次重建equal；APOLLO_SEARCH在queryIndex>0恢复查询顺序、page不变；不同endpoint同内容不错误合并。无原文不清空原证据。
- I-5：全名与同规范化别名只请求一次；已知其他公开别名请求一次；单机构加NARROW，多机构不任意选择；宽搜非空+窄搜空仍保留宽搜候选；请求上限/分钟/日预算保留。
- I-6：maxPagesPerQuery=1且hasNext→PENDING+SEARCH_INCOMPLETE+SEARCH_PAGE_LIMIT；maxRequestsPerItem耗尽→对应INCOMPLETE+ITEM_REQUEST_LIMIT；UI中文精确相等；不完整搜索不准入。
- I-7：FlowIT验证所有保护、Gate重新读取与持久结果一致；samples配置与实现零diff；GET前后revision/attempt条数不变。
- 命令：使用JDK11，独立执行worktree内 `mvn -Dtest=QualificationEvidenceParserTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationAdmissionGateTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test`；随后 `mvn -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT,DiscoveryVerificationReportingIT test`。真实Docker/MySQL必须可用，跳过=未验证。按仓库原Maven配置保留JS测试阶段。
- 研究检查点：若实现需要改Gate、Commit、schema、policyVersion或前端，停止扩改并修订本计划；不能靠新增第二套规则或静默强制准入解决失败。

## 人工验收清单

### A-1: 公开证据从缓存恢复
- 前置条件：隔离MySQL中构造3份公开ORCID缓存、evidence空的未完成核验档案；另构造private/缺visibility文档。测试提供runId/itemId。
- 操作步骤：1. 启动一次Worker。2. GET `/api/expert-discovery/verification/items/{itemId}`。3. 查看ORCID请求次数。
- 预期结果：public缓存对应证据非空，来源仍SELF/ORGANIZATION原值；private/缺visibility证据0；缓存完整时新增ORCID请求0。
- 覆盖：O-1、I-1/I-4、X-1。

### A-2: 未明确准入保留
- 前置条件：隔离样本已有绑定ORCID、SELF当前Example University任职；Apollo完整结果只有同名同机构1人；学历/年龄缺失；无保护/硬阻断。
- 操作步骤：1. 完成核验。2. 查看执行记录与逐条详情。3. 查看候选同步结果。
- 预期结果：自动准入1，其中Apollo1，已明确0，未明确1，qualification UNKNOWN，outcome APOLLO_UNCLEAR；候选新增1或已有同身份返回ALREADY_PRESENT。
- 覆盖：O-1、N-1、I-2/I-3/I-7、X-2/X-4。

### A-3: 不能误认本人
- 前置条件：隔离fixture分别提供同名同机构2候选、ORCID矛盾、NAME_CONFLICT、历史任职、人工HOLD、已联系、APPLICATION、SBIR。
- 操作步骤：1. 核验每条。2. 查看原因、原审核决定和候选记录。
- 预期结果：各条新增准入0；歧义为IDENTITY_AMBIGUOUS、姓名冲突为NAME_CONFLICT，其他显示对应保护/缺失原因；人工HOLD不被AUTO_PASSED替换。
- 覆盖：N-2、I-2/I-3/I-7、X-2/X-4。

### A-4: 查询和限额可解释
- 前置条件：隔离Apollo响应含hasNext；页数上限设1；另有单条请求数已到限额的档案；公开别名样本由mock记录请求名。
- 操作步骤：1. 执行。2. 打开日志原因详情。3. 查看attempt列表。
- 预期结果：分页样本显示“查询未完成：达到单次查询页数上限”；请求上限样本显示“核验未完成：达到单档案请求上限”；两条PENDING、不准入；别名按规范化去重，attempt计数不减少。
- 覆盖：O-2、N-3、I-5/I-6、X-3/X-6。

### A-5: 留存继续
- 前置条件：隔离采样开启且未到配额；启动新的独立深度发现run，含有正文的输入。
- 操作步骤：1. 完成一个采集批次。2. 查看样本目录校验输出。3. 确认线上部署配置的samples.enabled。
- 预期结果：出现该run的sample.json；SAVED正文SHA/size失败0；线上enabled仍true，7天/512MiB限额不变。旧run达到QUOTA允许不新增包。
- 覆盖：N-3、I-7、X-5。

### A-6: 页面与只读回归
- 前置条件：同一隔离run停止写入，记录revision和attempt数；保存修改前执行记录截图。
- 操作步骤：1. 连续刷新summary/batches/records/items。2. 打开日志页签与原因弹层。3. 对照截图。
- 预期结果：revision/attempt数不变；原DOM列、页签、字体、间距和颜色不变；只新增约定原因文案，不出现新按钮。
- 覆盖：N-4、I-6/I-7、X-6。
