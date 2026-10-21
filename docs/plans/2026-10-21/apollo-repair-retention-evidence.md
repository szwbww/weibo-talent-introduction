# Apollo 修复与留档：证据、schema、读写链

日期：2026-10-21；代码HEAD：`962fcfe924ad7562a4ea917d174180df82ac3b5c`。
配套：[总计划](apollo-repair-retention-master.md)、[代码行号/文件摘要回执](apollo-repair-retention-code-receipts.md)。

本文是本会话此前只读审计的脱敏汇总，未把生产原始人物数据复制进仓库。生产状态可能变化；执行前通过服务器原台账重新冻结。不能将此文冒称完整原始查询输出。生产审计与当前HEAD代码结论分别标明。

## E-1：生产快照与限制

任务27254：北京时间2026-10-21 00:38:28开始、03:38:49结束，PARTIAL_SUCCESS。1046项：DIRECT_CLEAR120、APOLLO_CLEAR83、PENDING579、ERROR264；203准入revision均已PROJECTED。1046项均有snapshot/evidence；候选记录累计43665条，非唯一人数。

PENDING分布：APOLLO_NO_MATCH280、IDENTITY_NOT_MATCHED194、NAME_CONFLICT25、IDENTITY_AMBIGUOUS21、SEARCH_INCOMPLETE/SEARCH_PAGE_LIMIT55、RND_OUT_OF_SCOPE2、IDENTITY_CHANGED2。合计579。

Apollo83已明确：教授62、当前机构11、历史机构10；其中1项带TUM别名标签（附加原因，不是第84人）。任务读取过13000篇论文及1000条ORCID输入；不把两者相加当专家人数。

原始汇总stats.promoted=0；与203已PROJECTED不一致。代码已有读侧补统计，原始汇总0不证明所有当前API仍0；最终需同时核对运行中和结束后接口。

## E-2：422证据等级

历史attempt：BROAD200成功1107、NARROW成功401、VARIANT成功517；DETAIL成功2115、422失败264；ORCID成功2163。历史264条无错误正文，全部personId为24位十六进制；未见同ID此前成功记录。其中1项在失败前已经完成过其他候选详情。

最后成功DETAIL：2026-10-20 19:35:24.566 UTC；首422：19:35:24.867 UTC；末422：19:54:30.637 UTC。相邻时间本身不证明全部相同根因。

从其中一条既有失败personId做过一次只读GET复现：返回422，脱敏短语提取命中明确 `insufficient credits`，另含upgrade/credits/limit/plan。该次响应摘要：`65ad87fa83d832c361209d222e3739f93e054022d77a507996b0d13c12cccb2f`。**只有该次可确认额度不足；其余263条不能倒推。** 不把单独limit/plan等词作为分类依据。

代码证据：ApolloPeopleSearchClient.kt:74–100对非2xx不读body，400/422统一REQUEST_INVALID；Worker.kt:141–157失败不传response，无失败正文及hash。01修诊断，02修等待恢复；不承诺代码能修复账户额度。

## E-3：分页与重复请求

55条页上限：queryIndex0=35、1=16、2=2、3=2；14条后续尚有机构查询，但Worker.kt:276–280直接finish/return阻止执行。修续查有明确代码依据；未实时补查14人，不能宣称有14个准入收益。

全部DETAIL2379次/2314个不同ID；其中成功2115次/2084个ID，重复成功31次分布在30组ID，已存响应在每组内一致。Worker当前只有item内detailsCompleted。跨item同run成功复用可消除顺序重复；并发在途请求仍可能重复。不能把65次总重复含错误全部当可安全缓存。

## E-4：Nicolas与规则对照

任务27254/item411，Apollo ID `5f5cf6567ddaa000016f89e4`。

- 源机构University of Geneva；Apollo历史任职Université de Genève，2011-01-01至2024-12-01，current=false。
- Apollo当前title Assistenzprofessor；headline开头Assistant Professor，所在University of Southern Denmark。
- 同一ORCID `0000-0002-1966-4435`；ORCID任职缺日期仍UNKNOWN，不能直接当CURRENT。
- 参照来源： [SDU个人页](https://portal.findresearcher.sdu.dk/da/persons/ubrig/)、[SDU入职新闻](https://www.sdu.dk/en/forskning/polima/news/new-experimental-assistant-professor)、[Geneva官网](https://www.unige.ch/en/)、[Geneva联系页](https://www.unige.ch/en/contact/)。这些页面在本会话前序审计中已读取；实施时只使用有出处的固定别名，不从任意翻译推同一机构。

服务器实际部署类离线对照：782个COMPLETED项（1046减264错误）。纯规则重放与持久结论原有3项差异：2项RND_OUT_OF_SCOPE，1项IDENTITY_CHANGED；这不是新修复收益。仅改Geneva法英对应，1项PENDING→APOLLO_CLEAR；仅对该明确title/headline组合兼容，1项变化；两者均为item411，合并收益仍1。未写线上准入。

这里不是把Assistant Professor等同Associate Professor；用户现行策略允许匹配教授路线，该政策在本轮保持，原title和等级不可篡改。

## E-5：现有留档覆盖

服务器目录 `/opt/talent/data/discovery-samples/27254/`，每包sample.json。110包：BODY_TOO_LARGE、NO_FULLTEXT_ADDRESS、NO_EMAIL、DOWNLOAD_FAILED、IDENTITY_UNRESOLVED各20，SUCCESS_CONTROL10。

50个SAVED正文，57个NOT_AVAILABLE尝试；正文hash不匹配0，尝试截断0。单位是包/正文/尝试，不能把不同单位强行加总。当前采集是OpenAlex抽样，非全部平台/全部失败。

DiscoverySampleProperties.kt：目录默认上述路径，保留7天，最多1000包/512MiB，每失败桶每run20、成功对照10，单正文10MiB、每包最多2正文、最多16尝试、metadata1MiB。实际配置优先于默认。DiscoverySampleStore.kt:80/117/136/148负责扫描校验、.writer.lock、清理与提交；本计划不改这些文件或保留策略。

新增留档分两层：attempt.response_json保存有界脱敏错误；item.evidence_json.diagnostics保存查询/复用/判定轨迹。复用原SQL权限和备份，不新增无界全文附件。原样本7天到期仍会清理；需要长期对照的run在过期前用现有受限freeze保留必要证据，不承诺sample永久存在。

## E-6：verification三个表——实际schema与全写路径

schema：`src/main/resources/db/migration/V158__create_discovery_verification.sql`。

| 表 | 关键字段/约束 | 本次变化 |
|---|---|---|
| run | execution_id主键；mode/policy_version；settings_json TEXT；input_closed/revision | 沿现有settings冻结ruleVersion；无新增列 |
| item | 唯一expert_doc_id+identity_hash+policy_version；snapshot_json/evidence_json MEDIUMTEXT；state/outcome/route/qualification；generation/lease；admission_revision/projection_state；run_batch/claim/projection索引 | evidence_json新增一个diagnostics根；沿既有状态恢复；无新列 |
| attempt | 唯一request_key；request_json TEXT、response_json MEDIUMTEXT、response_sha256；provider/query_kind/result_code；item与provider/requested_at索引 | 非成功response_json增加带kind的诊断对象；成功格式不变；无新列 |

生产源码中三表SQL集中DiscoveryVerificationRepository；RegistrationService另只读task_execution存在性。仓库grep结果见代码回执；不将测试SQL当线上写路径。

全部Repository写入口（行号以回执为准）：

| 写路径 | 内容/调用者 |
|---|---|
| createRun、reserveItem、activateItem、closeInput | RegistrationService.begin/reserve/activate/closeInput；Pipeline/ExpertDiscovery结束关闭输入 |
| claimDue、renewLease | Worker领取、心跳，维护generation/token |
| reserveAttemptWithBudget | Worker.reserve；预约attempt、request_count；Apollo锁保留，内部配额已旁路 |
| completeAttempt | 完成attempt响应；由原子组合方法调用 |
| saveProgress、completeAttemptAndProgress | Worker.save/completed；响应与证据游标事务 |
| saveEvidenceAndOutcome、saveFailure | CommitService.commit及Worker.fail；最终结论与错误 |
| reschedule | Worker.rateLimit/transient/defer；RETRY_WAIT及next_attempt_at |
| failReserved | Worker恢复未激活项 |
| attachAdmission | CommitService.commit；绑定真实admission revision，projection=PENDING |
| markProjection | CommitService.project；投影状态CAS |
| requeuePendingForEvidenceReplay | ReplayTool.apply；只允许settled COMPLETED/PENDING且只改stage=DECIDE |
| bump | 上述写路径更新run revision，非独立外部调用 |

计划新增写入口只有02独立错误恢复CAS（同Repository、同保护），不另写SQL脚本绕过。

全部读入口及消费者：

- findRun/findItem/identity/checkedIdentity/lockRun/lockItem/lockedItem/owns：Registration、Worker、Commit、Replay的身份/租约检查。
- findManagedContexts/findItemsByDocIds：AdmissionGate批量上下文、规则/发送读取；不能因为新增诊断改变归属。
- reportingCollectionStatuses/reportingRuns/reportingCounts/reportingSuggestions/reportingReasons/reportingBatchTimes/reportingBatchReasons/reportingRecordTotal/reportingRecords/reportingWhere：QueryService summary/batches/records，默认只读。
- listReserved/listProjectionDue：Worker恢复和投影；额度故障不应阻断它们。
- findAttempts：Worker诊断/QueryService详情/Replay冻结；成功与错误JSON带明确类型边界。
- replayAdmission：Replay只读或锁读当前admission；工具恢复不写admission。
- ReplayTool.freeze只读事务直接获取运行、item、attempt和admission，输出受限文件；evaluate对子JVM固定输入，但当前evaluateInput还会重建ORCID事实，且名为gate的变量仅Rules复算；不是实际AdmissionGate/Commit调用。05将增加直接读冻结事实的显式模式并区分这些结果。apply调用Repository CAS。当前只支持PENDING，不支持ERROR复跑。

## E-7：其他store与边界

1. **admission与ES：不新增写路径。** CommitService.prepare/commit用当前RAW、身份指纹和人工保护重新检查；ReviewService.recordAutomaticPersisted写admission；Commit.attachAdmission绑定revision；projectApprovedCandidate写候选，再markProjection。ReviewService的人工审核、撤销、重验、批量操作，以及ExpertRevalidation和BatchRecipientSelection/发送读取均继续通过已有Gate语义。本轮不直接SQL改admission或ES，诊断不是事实来源。（K-discovery-admission-read-write-parity）
2. **ES mapping实际文件**：`src/main/resources/es/orcid_info_raw.json`、`orcid_info_candidate.json`、`orcid_info_application.json`均dynamic=false；tags keyword，discoveredAt date（yyyy-MM-dd HH:mm:ss/日期/epoch）。本轮只有06读既有_source tags/discoveredAt，无新字段、无mapping或reindex。ExpertIndexWriterService晋升透传原source；不改晋升写入代码。（K-promotion-source-passthrough）
3. **enrichment_job schema**：V131，UNIQUE expert_doc_id，discovery_execution_id可空，状态/到期索引。写路径insertIfAbsent、reopenUnmatchedOrStaleSuccess、claimById、renewLeaseById、completeWithToken、reopenFailedById；读findDiscoveryDocuments/findByExpertDocId/findDueCandidates与CrudRepository标准读。06只读findDiscoveryDocuments，不改任何生命周期写入。
4. **task进度**：TaskProgressController.get/执行列表、TaskExecutionController.detail分别消费PromotionProgressService两refresh重载；TaskExecution.resultSummary及TaskProgressLog.details由原任务服务写，本轮禁止回写。通用batchOnly与发送日志不改。（K-progress-log-batchonly-two-readers、K-execution-detail-running-needs-progress-log）
5. **文件**：sample写入/清理由DiscoverySampleStore统一管理；ReplayTool.freeze/report输出在服务器独立受限目录。新报告不覆盖before.json/manifest或原sample；生产人物数据不进Git。

## E-8：必须验证的交互

| 交互 | 风险 | 验收位置 |
|---|---|---|
| HTTP失败→attempt→详情API | 空正文/泄密/误分类 | 01 A-1/A-3 |
| attempt额度状态→预约→重启恢复 | 内存状态丢失/并发探测 | 02 A-1 |
| Error恢复→身份/人工保护→正常Worker | 跳过游标/误准入 | 02 A-3 |
| 页响应→候选并集+游标→重启 | 截断被洗掉/后续查询遗漏 | 03 A-1 |
| 成功详情→同run另item→请求计数 | 串人/虚增HTTP | 03 A-2 |
| 冻结版本→Rules→Gate→Query/重验 | 新旧规则漂移 | 04 A-1/A-2 |
| 真实判定→trace→离线报告 | 重算冒充原记录 | 05 A-1/A-2/A-3 |
| markProjection→运行中/完成详情 | 自动准入与新晋升混算 | 06 A-1/A-2 |
| 论文抽样→原hash复核 | 为新诊断破坏旧留存 | 03 A-3、05 A-2 |

## 不能据此承诺的效果

不承诺264人补跑通过、不承诺14人新增准入、不承诺解决全部IDENTITY_UNRESOLVED/403下载/机构未知、不承诺一个有限别名表覆盖所有大学。可确认的是具体错误分类、确定跳过路径、重复请求、固定样本规则与读侧统计的可测试修复。

## E-9：r2补充代码核对（2026-10-21）

- Worker:69/131–138/360–366的BudgetDeferred目前只有reason；增加retryAt必须覆盖reservation→异常→catch整条链，不能只加返回字段。
- VerificationProgress（DiscoveryVerification.kt:102–108）已有finishedQueryKeys；分页自然完成事实应留在业务游标，diagnostics仅镜像显示。无须为分页另加一套控制状态。
- ReplayTool:177–184的expected调用replayEvidence强制DECIDE；ERROR恢复必须构造独立输入，不能复用该函数。
- ExpertIndexWriterService:903–908候选投影复制source，新增candidateValidatedAt/updatedAt，**未改discoveredAt**。删除“异步投影晚就导致时间窗遗漏”的推断；统计计划仍针对已有job归属和直接投影账读取缺口。
- ApolloPeopleSearchClientTest已有MockRestServiceServer；WorkerTest使用Mockito；两个Repository IT启动独立MySQL8.0.36且Docker不可用直接失败。复用这些设施，不建新测试平台。
- Rules.matchReason调用点：Rules:101/111/115、Worker:264/291；Rules:319的emailNameMatches只接受机构命中，保持范围，不把新增教授兼容扩散成新邮箱匹配政策。

以上都是当前代码事实；r2仅修订计划，未执行实现测试或新生产请求。
