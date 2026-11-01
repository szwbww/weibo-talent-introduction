# 论文明确漏绑修复与整批补录重审 — 主计划

状态：待执行；2026-11-01。仅计划，不表示已实施或发布。

## 需求描述

本次把“明确漏解析修复”和“可反复使用的整批补录/重审”合并为一个顺序主计划。上线后的两个入口：

1. **专家审核原重审按钮**：已建档专家可重新读取留存论文，补齐明确身份，再按现行规则核验。
2. **论文原文新增“重新解析并补录本批”**：按整个已结束执行任务处理所有留存原文；可仅解析对照，也可补录/补齐后自动重审，覆盖此前未建档的人。
3. **日志可对照**：区分首次解析、本次解析、补档变化、实际准入及未通过步骤，页面重开可恢复；不需要用户再手动串联两个按钮。

保持不变：M-1 原专家重审、单篇解析/下载/删除/保护入口；M-2 人工/联系人保护、现行资格与Apollo免费/付费规则；M-3 原论文文件、首次快照、旧核验历史与源采集计数；M-4 邮件发送/收信与采集游标；M-5 取消不撤销已完成补录/准入。

范围外：整批删除功能重构、403/422网络问题、OCR、新姓名猜测规则、扩大下载范围、任意提高并发、资格规则放宽、自动修复47个尚未确认分栏候选、生产发版/补跑。本计划不会把上述邻近问题混入代码任务。

已核实样本依据为#30711审计。当前文档是代码调研后的设计，**未宣称本地已验证325均可恢复**；00明确补全这项证据再实施对应规则。

## 关键不变量

### Invariant I-1: 不猜根因和数量
- Rule: 仅将原文证据+生产重放一致的漏绑作为修复真值；325不是新增/准入人数；未证明的推测不进入需求。
- Applies to: 00/01/06全部报告
- Violation consequence: 承诺无法兑现的修复效果。
- 来源: K-source-recall-needs-original-layout

### Invariant I-2: 来源与历史可追溯
- Rule: 原文SHA、metadata、first.outcome和旧审核轮次不覆写；新解析记录有实现版本、页码和原句；未来任务与本批同一机制。
- Applies to: 所有归档/重处理/重审路径
- Violation consequence: 无法复验，成为一次性脚本。
- 来源: K-extraction-samples-are-not-consumption

### Invariant I-3: 身份事实不等于准入
- Rule: 解析绑定成功不直接晋升；现有邮箱、身份、人物与资格门禁仍执行；论文历史职称不等于当前教授。
- Applies to: 新旧身份补齐/消费/资格
- Violation consequence: 把作者/老师署名误当合格专家。
- 来源: K-paper-facts-need-owner-time-and-readonly-compute

### Invariant I-4: 保护与模式隔离
- Rule: 人工/联系/APPLICATION/冲突保护不绕开；AUDIT不写业务；论文页AUDIT不外呼，专家原AUDIT按原REUSE/REFRESH选项执行；ENFORCE确认后执行；取消不回滚已提交业务。
- Applies to: 所有API、worker、commit
- Violation consequence: 误写或撤销用户处理结果。
- 来源: original

### Invariant I-5: 固定整批与可恢复
- Rule: 任务scope由sourceExecutionId的持久化快照决定，跨全部页；幂等、lease/generation、取消、恢复都由DB控制；关闭页面不取消。
- Applies to: 整批任务与前端
- Violation consequence: 漏处理、重复任务、假运行。
- 来源: K-task-activity-execution-identity

### Invariant I-6: 复用现有写路径
- Rule: 新作者唯一消费入口、已有档案窄CAS修复、准入现有Commit、投影现有project；旧CLI保持只prepare的含义。
- Applies to: 04所有业务写
- Violation consequence: 第二套规则漂移。
- 来源: K-discovery-reentry-keeps-run-accounting

### Invariant I-7: 日志口径明确
- Rule: 论文、attempt、唯一邮箱目标、docId、personId、准入、同步分别计数；六步处理日志包含失败/跳过/未开始，不伪造成功。
- Applies to: 所有API/查询/前端
- Violation consequence: 结果不可解释。
- 来源: original

### Invariant I-8: 不扩Apollo策略
- Rule: 保留当前免费/付费保存配置；一个父任务冻结后各子核验一致；不新增内部500次限制或开启收费详情。
- Applies to: registration/recheck内部派发
- Violation consequence: 配置漂移或费用意外。
- 来源: original

### Invariant I-9: 前端实现受完整契约约束
- Rule: 05中S-1..S-6是全部新增CSS/DOM的唯一权威；已有全局样式不改；每个新状态有文字及可访问操作。
- Applies to: 05全部前端文件
- Violation consequence: agent实现偏离真实页面。
- 来源: K-global-p-is-muted-in-dialogs

## 现状审计

完整源码回执：[paper-reprocess-evidence/README.md](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/README.md)、[paper-reprocess-evidence/paths.json](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/paths.json)。共享存储全部生产读写路径及交互编号见主计划「现状审计」；本子计划只改变下述接缝，其他匹配路径为回归边界。当前本地基线 `b758eb0`，工作区存在先前改动；不得当作已核实的生产部署版本。

### 已证实与尚未证实的边界

| 结论 | 证据 | 对应修复 |
|---|---|---|
| 本批存在明确原文漏绑 | report.md：244论文、337组合、333保守去重分组；325本批无完整绑定 | 00固定真值，01仅修真实重现类型 |
| 不能承诺325全修/全新增/全准入 | report.md:18、51：未逐条生产根因重放，也未盘点全库是否已建档 | 00先列真实缺口，06报实际增量 |
| 现有文本/版面只覆盖部分明确联系形式 | SourceAuthorEmailResolver.kt:117–183；PdfAuthorContactLayout.kt:37–42、145–218 | 01加入原文重现正负例；未扫描页单列 |
| parser修好并不会自动修旧专家 | PaperVerificationEvidenceService.kt:138–145；Worker.kt:448–454仍用旧snapshot.identityValid | 02正式执行后受控修复、有效身份重绑定 |
| 空姓名重复档案会先被冲突挡住 | ExpertDiscoveryService.kt:2545–2550 mismatch 后return | 04旧档案走02；新档案走consumeArchivedPaper |
| CLI并非网页“补录后自动正式重审” | DiscoveryHistoricalReplayTool.kt:330–386：apolloAssist=false；prepare AUDIT/REUSE且不start | 04复用叶子能力，补齐产品编排；旧CLI语义不改 |
| 单篇analysisLease不是全部留存attempt集合 | DiscoveryPaperArchiveService.kt:60–79 按first.attemptIds选文件 | 03显式冻结所有有效attempt并用受控lease |
| 归档已有只读分析与物理去重，不需要复制原文 | V159、PaperBodyAnalyzer.kt、ArchiveService blobPath/analysisReaders | 新增两张本功能任务表；原文读取现有blob |

上表限制条件是代码事实，不表示其中每一条都是325中每个样本的原因。00是生产解析根因归类的必经检查点。

### 归档四表、原文件及缓存（X-1 / X-5）
- Schema：V159 `record`唯一(execution_id,source_name,paper_key)，revision保护analysis/protected/outcome；`attempt`独立下载记录；`blob`按SHA去重；setting独立版本。原文是blobs/SHA分片的.bin，实际mediaType可为PDF/XML/HTML/TEXT，不按任务目录复制。
- 全部存储写出口：`DiscoveryPaperArchiveRepository.saveSetting/beginRecord/beginAttempt/saveBody/finishAttempt/finishExtraction/saveAnalysis/protect/blobState/finishDelete/deleteUnreferencedBlob/touch`。调用方为Capture采集scope、ArchiveService直接API/删除/恢复、AnalysisService保存对照；所有命中行见archive.txt/archive-file.txt。HistoricalReplayTool现有SQL/文件读取也在回执中，不应新增隐藏write。
- 全部读取出口：repository的setting/record/records/lookupOriginal/lookupHistorical/lookupHistoricalBatch/lookupSourceAttempts/attempt/attempts/references/blobs/blob/list/counts；调用方为ArchiveService stats/list/detail/content/delete-preview/recovery、AnalysisService、PaperVerificationEvidenceService、HistoricalReplayTool/BackfillService、Capture结束采集；前端通过ArchiveController读取。
- 本次：不写上述业务字段；增加显式读取lease且PaperVerificationEvidenceService按固定refs使用该lease，new repository只按源execution分页读，保留旧默认analysis语义。新结果写本功能item.result_json，新UI并列展示first/current，不替换archive.analysis_json/first.outcome。
- 缓存：PaperVerificationEvidenceService按body/kind/authors/year/versions键复用解析；新增parserVersion要穿透已有versions。读取成功且新身份变化后重新绑定证据，不能继承旧名字下的owner判定。

### 三层ES身份（X-2 / X-3 / X-7）
- 实读mapping：`src/main/resources/es/orcid_info_{raw,candidate,application}.json`均dynamic=false；RAW/CANDIDATE姓名keyword，APPLICATION姓名text；identityVerification对象dynamic=false，内含status/version/email/givenNames/familyNames/source/evidenceHash/orcid/openAlexAuthorId；externalIds enabled=false。**本次不加ES字段、不改mapping、不跑全量reindex**。(来源: K-es-dynamic-false / K-es-mapping-single-declaration-source)
- 窄身份写出口：ExpertIndexWriterService.writeIdentityProofIfVersionMatches、writeDiscoveryNameAndProofIfVersionMatches；调用方IdentityRepairService.repairDuplicateIdentity/repairEmptyNameProof。整文档写出口：indexToRaw、candidate/application晋升和discovered projection（create保护）、import/bulk update与企业脚本；相关完整命中含路径见identity.txt；本次只新增窄补丁调用与复用原consumeArchivedPaper，不能拓宽bulk/普通patch允许字段。
- 业务调用写路径：ExpertDiscoveryService正常采集/队列/归档消费→RAW；duplicate→identityRepair；IdentityRecoveryService.apply→repair并人物/准入重验；VerificationCommitService.project→候选；Existing academic/country update与promotion保留已有字段，禁止新代码整文档回写旧快照。
- 全部相关读者：Writer.readDiscoveryDocument/readRawDocument/findDiscoveryHitsByEmail/discoveryProfile，ExpertSearchService/profile DTO，DiscoveryIdentity身份门，AdmissionPolicy，ReviewService/ScanService，VerificationRegistration/Worker/AdmissionGate/Query，PersonService/Query/Maintenance，RecheckAcademicEvidence，CountrySupplement，Campaign RecipientSelection/PersonRecipient，MailVariableService；完整字段命中与测试在identity.txt。原快照与有效快照由02显式区分，不能只改其中一个读者。
- 新写→读接缝：补名改变identity hash/factFingerprint，原VerificationCommit会拒绝旧snapshot。02限定本轮有证据rebase；保留before以给审计/名单hash读者使用。旧普通发现缓存、凭证版本保持原值。(来源: K-identity-cache-version-and-admission)

### 核验、准入、人物、学术入队（X-4 / X-7 / X-8）
- V158核验run/item/attempt；V161新增review_epoch参与唯一键，step_no只能1..8；V164准备spool，V165统计query snapshot。V148 admission每docId一行revision，review_item保留人工操作历史；V156 person/member/author_key的唯一键是真实docId/可信作者ID，邮箱和同名不是全局人物主键。schema原文见schemas.txt。
- 核验全写路径：DiscoveryVerificationRepository create/reserve/activate/claim/lease/step/evidence/complete/restore/replay/projection/cancel；TraceRepository逐步记录；RecheckService prepare/start/cancel；RegistrationService新档案和duplicate恢复；Worker外部attempt/结果；CommitService准入和投影；HistoricalReplayTool和VerificationReplayTool运维接缝。完整直接SQL与snapshotJson读写命中在verification.txt，旧CLI不得被新入口默认模式改变。
- 核验读路径：Repository find/history/claim/report/recheck/query/count，VerificationAdmissionGate/AdmissionPolicy，Worker缓存/REFRESH，RecheckService名单来源定位，QueryService详情/名单/指标，CommitService当前保护，ReplayTool/历史对照，Controller以及原discovery-recheck.js/discovery-verification.js。普通Gate只接受已提交的新epoch准入，AUDIT/排队不抢占旧准入。
- 准入全部写出口：ReviewRepository initializeAdmission/insertItem/markBatchReady/claimBatchItems/applyItem/revokeCurrent/markItemFailed及内部CAS；ReviewService人工/历史/自动记录；VerificationCommit通过recordAutomaticPersisted使用原路径；IdentityRecoveryService已有正式恢复也会重算。读者：Policy/ReviewScan/VerificationCommit/Registration/PersonOutreachGuard，见admission-person.txt。
- 人物全部写出口：PersonRepository createPerson/attachToPerson/attachToPersonVerified→insertMembers/insertAuthorKeys，调用PersonService/Registration/repair/normal discovery/maintenance；读者PersonQuery、Campaign人物联系人选择/OutreachGuard及各登记保护查询。继续调用既有登记，不直接给同名插person relation。(来源: K-discovery-reentry-keeps-run-accounting)
- 学术补全入队继续由ExpertDiscoveryService.enqueueEnrichmentJob/ensureEnrichmentJob负责；本功能不再单独写一次job，不改变现有唯一docId和身份保护。V131已读取；该表全部写出口为ExpertAcademicEnrichmentJobRepository.insertIfAbsent/reopenUnmatchedOrStaleSuccess/claimById/renewLeaseById/completeWithToken/reopenFailedById，读出口findDiscoveryDocuments/findByExpertDocId/findDueCandidates及OpenAlexBudgetRepository计数；由JobService/ExpertDiscoveryService/PromotionProgressService消费，回执enrichment-executor.txt。论文历史职称仅证据，不因发现老师称谓就直接覆盖当前职称。(来源: K-paper-facts-need-owner-time-and-readonly-compute)

### 任务与新增owned store（X-6）
- task_execution V4及后续owner/heartbeat迁移、TaskExecutionRepository/Service有owner条件终态与中断恢复；TaskProgressStore只有同executionId的进度能用于卡片。已有通用任务读取服务、TaskActivityController、TaskTypeCatalog与app任务列表均为读者。
- 本次唯一新增taskType=DISCOVERY_PAPER_REPROCESS，不复用原sourceExecutionId，不污染采集计数；新增run/item两表为该功能独占，不向共享task/archive/admission表追加字段。
- 新表写读唯一归03repository；Service/API/05UI消费。取消/恢复/统计从DB事实得出，不使用页面内存当任务权威。(来源: K-task-activity-execution-identity)
- 完整任务命中task.txt；源采集、重审、外联、收信的其他owner不变，不暂停/清理其他运行任务。

### 前端样式盘点（X-9）
- 当前HTML、完整dp CSS、button/label/p全局影响逐字记录于frontend-before.md；不是设计猜测。完整最终CSS/DOM位于05 S-1..S-6，执行时只能使用该契约。
- 论文页现有 `DiscoveryPapers.mount` 与app `ensureDiscoveryPapers/syncDiscoveryPapers`；所有页面交互由api包装认证。新组件嵌入现有挂载/暂停/销毁，不另写一份后台页面。
- 预览依据：`docs/mockups/paper-reprocess-preview/`原样式快照及entry/confirm/result/steps图片；这些是假数据预览，不是生产任务结果。

### 交互点清单（每项有人工跨路径验收）
| 编号 | 写→读 / 协作 | 验收位置 |
|---|---|---|
| X-0 | 原文真值→生产旧新parser结果 | 00 A-1、06 A-1 |
| X-1 | 首次归档→新解析/基线差异 | 01 A-1、03 A-1 |
| X-2 | 新绑定→本轮有效身份→验证 | 02 A-1 |
| X-3 | 补齐ES→人物登记/保护/来源 | 02 A-1/A-4、04 A-2 |
| X-4 | 身份变化→提交guard→准入→候选同步 | 02 A-1/A-3、04 A-3 |
| X-5 | 原文lease→删除/保护→新UI读回 | 03 A-3、05 A-4 |
| X-6 | 持久化任务→活动卡片/重入恢复 | 03 A-2、05 A-2 |
| X-7 | 新作者→旧采集门禁→新档案/人物/核验 | 04 A-1 |
| X-8 | 父任务→自动启动child→准入汇总 | 04 A-1/A-3、05 A-2 |
| X-9 | 原页面挂载→新DOM/CSS/API→旧详情回跳 | 05 A-1..A-4 |

### 本次载入知识的取舍
13条载入知识已更新last_used/hit_count：身份证据、缓存版本、论文事实时效、缓存重放、重新发现计数、抽样不是消费、真实版面；ES dynamic/mapping；全局p样式；Flyway；重审epoch；task execution identity。均有对应不变量或审计引用。旧“V158唯一键无epoch”的知识已由V161证据纠正，不能作为当前schema；没有使用过期条目推导实现。未加载无关邮件/业务计划。


## 实现方案

### 顺序与门禁

| 顺序 | 子计划 | 文件数上限（含测试） | 可独立验证/交付的切片 |
|---|---|---:|---|
| 00 | [固定真实对照](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-00-replay.md) | 6 | 可复跑只读清单、根因证据；无应用变更 |
| 01 | [解析修复](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-01-parser.md) | 7 | 新采集和单篇对照获得相同解析改进 |
| 02 | [原重审补绑](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-02-identity.md) | 11 | 原专家重审可补齐既有档案；含既有 persisted-admission 的受控身份修复窄接缝 |
| 03 | [整批任务](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-03-batch.md) | 13 | API可整批只读对照，含解析链按页取消/预算checkpoint窄接缝，原页面不受影响 |
| 04 | [补录+自动重审](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-04-apply.md) | 10 | API可完整正式处理；前端尚未开放也可隔离验证 |
| 05 | [前端](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-05-frontend.md) | 7 | 完整真实页面与日志，不改变后台算法 |
| 06 | [总复验](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-06-verification.md) | 4 | 固定清单的逐人效果报告及发布依据 |

严格00→01→02→03→04→05→06。共享文件（Worker/Repository/IntegrationTest/Service）顺序修改，不并行写。每个子计划在前一计划核验通过后才实施；除用户批准的02≤11和03≤13文件外，每个≤10文件，均≤2子系统。新增共享store字段只有02的snapshot_json.identitySupplement一个结构化字段；03两张owned新表不属于共享store字段扩张。主计划不作为无界代码白名单。

### 最小实现决策
1. **I-1/I-2**：生产解析器一个、现有资格引擎一个；不把审计Python算法直接搬成第二套长期判断。
2. **I-3/I-4**：原专家重审修已建档；论文入口覆盖原先漏建档；二者共享RetainedPaperIdentityService，原资格统一Worker/Commit/Projection。
3. **I-5/I-6**：整批解析先跨论文归并再写档案；两张任务表足够，不上事件总线/通用DAG/新消息队列；默认并发1，以实测决定后续提高，不跟本次Apollo请求策略混在一起。
4. **I-2/I-7**：原文只读复用、first.outcome不动；新日志保留每个来源、页码、旧/新身份、应用收据和核验引用。绑定、补录、准入分层计数。
5. **I-4/I-8**：正式模式才自动重审并写业务；纯对照只是解析差异与拟补档，本次不在“仅解析对照”按钮里偷偷联网评估资格；原专家AUDIT继续现有资格对照语义，新增补名仅虚拟投影。
6. **I-9**：样式完整契约绑定05，不允许执行agent自由发挥。示例数据不进入生产，服务端状态恢复与失败文案属于验收，不是后续打磨。

### 需要执行时确认的证据检查点
- 00原始PDF/metadata获取情况及每条生产根因；缺文件则该项不能认定修复，不根据独立审计推断具体Kotlin根因。
- 首次/尾页/未扫描中间页区别；01不擅自调整线上maxPages，未覆盖计入未修清单。
- 新运行的基线代码/迁移号：本地HEAD与文件SHA在baseline.json，执行前若漂移重查受影响行与写读路径，禁止覆盖其他未提交改动。
- 保护和跨存储CAS语义：本次沿用既有机制，不承诺MySQL+ES原子提交；任何错误或来源变化都有明确结果，不能放宽来凑数量。

### 交付与发布
开发完成只交代码、测试与实际报告。该请求仅创建计划；不在本轮实施、不发版、不操作生产任务/删除原文。后续如授权执行，先全量机器门禁，再导出各计划人工验收清单；发布前保留旧WAR、schema升级结果与回滚步骤。回滚应用不删除新增审计表，不自动撤销已经确认的补名/准入；需要撤回业务结果另列有依据的人工处理，不泛化SQL回滚。


## 变更文件清单

| 文件（仓库根目录相对路径） | 改动 |
|---|---|
| `docs/plans/2026-11-01/paper-reprocess-00-replay.md` | 顺序子计划；本总计划不直接扩大其文件白名单 |
| `docs/plans/2026-11-01/paper-reprocess-01-parser.md` | 顺序子计划；本总计划不直接扩大其文件白名单 |
| `docs/plans/2026-11-01/paper-reprocess-02-identity.md` | 顺序子计划；本总计划不直接扩大其文件白名单 |
| `docs/plans/2026-11-01/paper-reprocess-03-batch.md` | 顺序子计划；本总计划不直接扩大其文件白名单 |
| `docs/plans/2026-11-01/paper-reprocess-04-apply.md` | 顺序子计划；本总计划不直接扩大其文件白名单 |
| `docs/plans/2026-11-01/paper-reprocess-05-frontend.md` | 顺序子计划；本总计划不直接扩大其文件白名单 |
| `docs/plans/2026-11-01/paper-reprocess-06-verification.md` | 顺序子计划；本总计划不直接扩大其文件白名单 |

共 7 个文件。未列文件禁止扩改；新增问题先记录证据并修订范围。

## 验收标准

- I-1/I-2：00/01/06逐条固定manifest；原始文件与first快照不变；原文不足不报成功。
- I-3：相同输入经首次/对照/重放结果一致，后续资格仍调用现有引擎；任何错绑为阻断。
- I-4：所有新写入口均有AUDIT/保护/取消/并发反例；无后台邮件写入。
- I-5：跨121篇、多tab、网络超时、关闭/重启、旧lease、取消/重试验证通过。
- I-6：grep确认新业务写只有批准的叶子入口；旧CLI测试仍AUDIT/REUSE准备状态。
- I-7：逐项结果可与统计复算，六步骤与八步骤对应可追溯；部分错误不伪装全成功。
- I-8：配置冻结前后与实际外部queryKind吻合；detail=false时详情请求为0。
- I-9/S-1..S-6：05最终CSS块逐字比较、DOM/可访问性/截图通过。
- 每子计划文件数≤10，02经用户批准例外≤11，03为按页取消/预算传递例外≤13，子系统均≤2；共享文件串行。迁移MySQL验证、JDK11业务测试、Node测试、WAR打包及人工验收分别记录，任一NOT_RUN不算PASS。
- 效果报告必须回答：可读取多少、正确新增绑定多少、已有补齐多少、实际新建多少、实际准入多少、剩余每种明确原因多少。没有执行前不填成功率或时长。


## 人工验收清单

### A-1: 原按钮补绑
- 前置条件：测试环境已有一条明确漏绑且已建空名档案，来源原文可读。
- 操作步骤：1. 用原专家重审按钮固定此人；2. 确认ENFORCE；3. 查看第3步。
- 预期结果：确认前业务不变；确认后姓名等于原文真值，显示证据页码/原句；随后按现行资格返回准入或待核验。
- 覆盖：I-1/I-2/I-3/I-4，需求1，M-1/M-2，X-2/X-3/X-4

### A-2: 漏建档与自动衔接
- 前置条件：测试源121篇，含一个符合现行资格的新作者；页面只选1篇且筛选命中1篇。
- 操作步骤：1. 使用新按钮正式执行；2. 等完成；3. 打开作者逐项详情。
- 预期结果：范围121；新建该作者1档案、自动核验并准入1；不需二次点击；原源任务计数不变。
- 覆盖：I-3/I-5/I-6/I-7/I-8，需求2，M-2/M-3，X-1/X-7/X-8

### A-3: 只读对照与历史
- 前置条件：固定已存原文SHA、first快照、专家/准入版本、邮件条数、游标。
- 操作步骤：1. 执行仅解析对照；2. 查看新旧绑定差异；3. 核对快照。
- 预期结果：实际新增/补齐/准入均0；外部请求0；仅新审计结果新增，其他快照及邮件/游标不变。
- 覆盖：I-1/I-2/I-4/I-7，需求3，M-3/M-4，X-0/X-1

### A-4: 保护、冲突与取消
- 前置条件：准备人工通过/已联系/不同非空姓名3条；另有本任务已准入1条。
- 操作步骤：1. 执行后查看保护；2. 取消任务；3. 刷新专家和旧记录。
- 预期结果：3条不覆盖；已经准入1条保留；剩余取消单列；旧核验历史不改。
- 覆盖：I-4/I-6，M-2/M-3/M-5，X-3/X-4/X-8

### A-5: 恢复与样式
- 前置条件：1440px及390px浏览器，存在运行中的整批任务。
- 操作步骤：1. 关闭并重新进入；2. 看相同runId；3. 查看原文/8步详情；4. 对照05 CSS视觉值。
- 预期结果：同任务继续；关闭不取消；入口14px标题/8px圆角、统计23px、手机3列，确认按钮始终可达；原单篇下载/保护/删除仍可用。
- 覆盖：I-5/I-7/I-9，需求3，M-1，X-5/X-6/X-9

### A-6: 对照结论不夸大
- 前置条件：06生成固定真值报告，包含仍缺源/歧义的项。
- 操作步骤：1. 逐条核对一条成功、一条缺源、一条冲突；2. 查看总体计数。
- 预期结果：每条有bodyHash/recordId/目标标识与原因；报告分别写绑定、补齐、新建、准入；不将325直接填为修复/准入人数。
- 覆盖：I-1/I-2/I-7，X-0..X-9


人工验收开始时才从此处导出同前缀 `-acceptance.md`；本次不生成衍生勾选文件。

## 修正记录
- 2026-11-01：用户批准继续与后续必要授权，解除02暂停：X-4/I-4/I-6要求复用既有准入写 seam，因此02加入 DiscoveryReviewService.kt 受控身份变化接缝，文件上限11且不减少测试；见 fast-p ledger Amendments A1/A2。业务目标、保护、顺序与不发版不变；完成后停在 review-fast-p 可执行阶段，不执行该审查。
- 2026-11-02：按用户后续必要授权，03加入PaperBodyAnalyzer、PaperQualificationEvidenceExtractor、PdfEmailExtractor的可选checkpoint传递及取消/预算异常窄传播，文件上限13；原默认调用、解析规则与版本不变。执行者证据证明现有签名无调用者checkpoint且catch会吞取消，原I-5按页安全退出要求无法在原白名单内实现；见fast-p ledger A4/A5及03执行日志epoch2。不减少验收。
