# 核对基线与存储审计

本目录为开发计划代码证据，不是修复测试结果。2026-10-24；HEAD b161e87464d42b9fa2cd076ef9255b0e3b2b29ab；实际工作树文件哈希见 source-sha256.txt。实施前逐项对照差异，不能覆盖既有未提交文件。

## 已证实与未证实

- [统计超时](../../../audits/2026-10-24-paper-archive/review-count-timeout.md)：18 条分类接口 503；部署类隔离重放 21.23 秒触发 REVIEW_SCAN_TIMEOUT；11 次 ES 页请求、lastOffset=5000。15,398 是当时 RAW/discovered 总量。不是当前批次人数，不是固定验收实数。10,000 窗口是另一代码边界，不冒充此次根因。
- [全批原文对照](../../../audits/2026-10-24-paper-archive/full-batch-comparison.md)：执行 28671，4472 篇有原文、4487 个保存尝试、1296 个原始完整姓名关联；不是 4487 个独立 PDF。已验证 MIME、DOCTYPE、页底/左裁切、邮箱标签、伪邮箱及资格原句漏解析。
- 519 是批内新发现邮箱/姓名绑定；未全历史去重，不是 519 个新增专家，更不是 519 个准入。897 已有待审核专家均已有完整姓名；519 中只有 1 组给该待审核集合增加身份佐证。
- 13 人可补充正向资格事实，10 份原文现有提取器 NOT_FOUND。3 人规则内存反事实仅证明特定姓名顺序/职称字符串有影响；未证明生产修复后全部可准入。
- 没有证明“所有查不到公开信息者科研水平不足”，没有证明“422 都是 Apollo 错误”；不纳入修复收益承诺。

## 存储和读写全集（检索路径见 store-paths.txt / consumers.txt）

### expert_discovery_admission / expert_discovery_review_item

Schema：V148；前者 docId 主键、identity_hash、decision、revision、decision_item_id、policy_version；后者 batch_key+docId 唯一、snapshot/reason JSON、expected_revision、state。

直接 SQL 写者：DiscoveryReviewRepository.initializeAdmission、insertItem、markBatchReady、claimBatchItems、cancelUnappliedItems、applyItem、revokeCurrent、markItemFailed 及其 lock/updateItemState/markItemApplied/markStale 私有更新。DiscoveryReviewService 的 automatic 更新 SQL 见回执。上游：人工 prepare/confirm/retry/revoke；initialize/refreshDiscovery；发现 recordAutomatic；VerificationCommitService；ExpertRevalidationService 的发现复评。此方案不增加直接 SQL 准入写者。

读者：Repository.findAdmission/findAdmissions/findItem/findItemsByIds/findItemsByBatch/findItemsByBatchPage/findHistory/batchStateCounts/findBatchCreatedAt；ReviewService；ReviewScanService.enrich；CommitService.prepare/commit/project；VerificationQueryService 的当前结论；发送链通过 admission 查询。对照模式禁止 initializeAdmission，不能复用现有人工 prepareAllMatching 的写入副作用。

交互：解析结果→核验→原 CAS 提交→准入读取/发送；人工更新→重审提交保护；同一 identity 的已批准结论必须不被对照/进行中重审抢占。

### discovery_verification_run/item/attempt

Schema：V158；run executionId 主键；item 唯一(docId,identityHash,policyVersion)，generation/token/leaseUntil；attempt requestKey 唯一，HTTP 请求和响应、预算累计。直接写者只有 DiscoveryVerificationRepository：createRun/reserveItem/activateItem/closeInput、claimDue/renewLease/reserveAttemptWithBudget/completeAttempt、saveEvidenceAndOutcome/saveFailure/markProjection/reschedule/failReserved/saveProgress/completeAttemptAndProgress/attachAdmission/bump；旧 CLI 两个 requeue 方法也写，不能遗漏。

上游：RegistrationService.begin/reserve/freeze/activate/recoverReserved/recoverDuplicate；Worker.tick/process；CommitService；ReplayTool.apply/apply-errors。读者：Repository 的 findRun/findItem/findManagedContexts/findItemsByDocIds/listReserved/listProjectionDue/findAttempts/findSuccessfulDetails、全部 reporting*；AdmissionGate、QueryService、Worker、Registration、ReplayTool；独立 probe.py 只读脚本。

计划 05 增加 run.recheck_json、item.review_epoch，各表仅一个列；新增 trace 表。旧 CLI 保留其原入口，不把它伪装成新网页重审；它不能修改新重审项。AdmissionGate 与 findManagedContexts 只纳入普通发现项以及已真正提交准入的新重审项；AUDIT/READY/RUNNING/PENDING 重审不改变当前业务结论。commit 内 staged Context 是显式例外，仍通过纯规则和全量 hard blockers。

### discovery_paper_archive_setting/record/blob/attempt + 原文文件

Schema：V159；record 唯一(execution,source,paper_key)，first/latest outcome、analysis_json、revision/protected；blob SHA 主键；attempt→record/blob 外键；blob 状态 AVAILABLE/DELETING/MISSING；attempt 状态 STARTED/SAVED/失败/DELETED。目录按 SHA 分层，不能由客户端路径定位。

直接 SQL 写者只有 ArchiveRepository.saveSetting/beginRecord/beginAttempt/saveBody/finishAttempt/finishExtraction/saveAnalysis/protect/blobState/finishDelete/deleteUnreferencedBlob/touch；读者 setting/record/records/attempt/attempts/references/blobs/blob/list/counts。上游 Capture.capture 的下载观察钩子、ArchiveService 的设置/分析/保护/删除/恢复；AnalysisService 写 analysis。文件写/删只有 ArchiveService.saveBodyInternal/delete/recover，读有 withContent/analysisLease/hashFile/validBlob/storage。Controller 暴露这些行为；discovery-papers.js 展示 first 与 analysis。

新增只读分析器不能调用 commitAnalysis；新证据通过 trace/item JSON 留档，不重写原文或 first。删除与分析必须沿用 analysisLease/use/finally，不能手拼文件路径绕过锁。缺原文/部分扫描必须单列，不算完整零结果。

### RAW/CANDIDATE/APPLICATION ES

实读schema：src/main/resources/es/orcid_info_raw.json、orcid_info_candidate.json、orcid_info_application.json三层根dynamic=false；identityVerification对象dynamic=false，明确status/version/email/givenNames/familyNames/source/evidenceHash/orcid/openAlexAuthorId；externalIds是enabled=false对象，仅可通过_source读取，不可假设可ES term检索其doi。ExpertIndexService:65–67加载三份声明。

本计划不新增 ES 字段、不改 mapping。仍使用现有 ExpertProfile/DiscoveryIdentity；准入投影走 ExpertIndexWriterService/DiscoveryReviewService.projectApprovedCandidate。原文补录走 ExpertDiscoveryService.consumeOutcomeInternal 既有身份、邮箱、去重、RAW 入库、核验登记/候选投影/补全入队链，不新造旁路。

现有写入类别：来源同步消费 consumeOutcome→consumeOutcomeInternal；队列 consumeQueuedItem→同核心；ORCID 来源；重复 recordIdentityDuplicate 身份补证；原邮箱回填 updateRawDocumentEmail/promoteRawToCandidateWithEmail；学术 updateExpertAcademicFields→三层 partial update；ExpertIndexWriterService 的写入/身份修复；ExpertIndexPromotionService 层间晋升；ExpertRevalidationService 复评撤回；人工审核共享投影。现有读者：审核 ScanService、Writer.readDiscoveryDocument/discoveryProfile；搜索、去重；学术补全；准入 Gate；候选/发送读取。新计划不改变原写者的字段结构或普通调用结果，新增入口必须调用其共享门禁，不直接写 ES。

学术字段更新目前还会触发 revalidateDiscovery（ExpertDiscoveryService:3459–3483），所以对照不能调用 enrichProfiles。新重审只收集学术数据供本轮判断/展示；原学术补全任务的三层写入职责保留。

### task_execution / task_progress_log

Schema：V4，V137 owner_token/heartbeat/interruption；task_type/status 是 VARCHAR，不需要新增 enum 列。TaskExecutionService.runAndRecord[WithResult] 创建与终结、updateProgressCounts 更新；TaskExecutionRepository.finishOwned/touch/recovery/manual interrupt 及现有 prepare/refresh 任务为已有写路径。读者 TaskExecutionController/Service、TaskSummaryExtractor、DiscoveryReviewService、VerificationRepository 报表。完整 SQL grep 见 store-paths.txt。

计数统计用独立 task_type DISCOVERY_REVIEW_COUNTS；重审名单固定用 DISCOVERY_RECHECK_PREPARE，成功只代表名单固定完成；真正重审阶段以 verification_run.recheck_json.control 为准。无需修改普通任务枚举/邮件调度。

task_progress_log 只作概括进度；通用 batchOnly/批量发送读取会丢逐人事件，不用它替代新 trace 表（K-progress-log-batchonly-two-readers）。

### Apollo 保存配置 / 学术补全

V160 discovery_apollo_setting 已有 search_enabled/detail_enabled/revision；Repository+DiscoveryApolloSettingsService.save 唯一保存路径，Controller 保存；Registration.begin 通过 freeze 读并写入 run.settings_json.apolloAccess。新重审复用冻结配置，免费不请求 detail、不揭示邮箱；不恢复已取消的自设 500 次额度。

ExpertAcademicEnrichmentJobService/Repository、Worker及 ExpertDiscoveryService 读写既有 enrichment job；本计划不改其表、状态或自动复评。OpenAlexDataSource.batchEnrichByAuthorIds/:Orcids 是可读采集接口；已有可信身份规则见 ExpertDiscoveryService:3357–3382。无可信 ID 记 NO_TRUSTED_ACADEMIC_ID，不按姓名猜配。

## 共享交互验收索引

| 写入→读取 | 必须覆盖 |
|---|---|
| 准入/身份变化→一次统计 | 01：初始化状态、手工状态、身份变化、旧结果时间 |
| first outcome→原文列表 | 02：3 邮箱/2 完整姓名显示2，而非0 |
| 原文→身份 proof→既有消费 | 03、10：共享邮箱歧义为0绑定、来源证据不能伪造 |
| paper facts→Gate/commit | 04、06、07：旧职称不当现职、博士在读不当已毕业 |
| 独立重审项→当前 Gate | 05、07：未提交的新项不抢占已有准入 |
| 冻结名单→执行/取消/重启 | 08：固定人数、CAS、已提交不回滚 |
| 统计/重审 API→网页 | 09：旧回包隔离、8步、真实失败原因、不显示假0 |
| 补录→RAW/核验/候选 | 10：重复执行不重复建人，519不等于准入 |

## 样式与运行基准

当前 index.html 17 个带版本资源，key=20261023-discovery-paper-apollo-settings；精确查 src/test 为0命中。但两个资源测试锁定17，增加 recheck.js 必须改到18并核对顺序。相关断言在 meetingConfirmationAssets.test.js:38–55、trustReplyWorkbenchSharedMount.test.js:180–190。

审核真实 DOM：index.html:1274–1319；styles.css:12717 起审核容器/12752 起四卡片；app.js:2995–3037 是错误统计路径。弹窗1180px，窄屏640px宽calc(100vw - 20px)；dr-table min-width1000px。完整新样式只在含前端的子计划中给出。

验证环境 JDK11；`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=<子计划测试>`；JS `node --test src/test/js/<文件>.test.js`。Flyway/并发租约测试需真实 MySQL 测试库；不能把禁用的 IT 当通过。每份测试结果记录实际命令、退出码、跳过项；本轮未执行任何修复测试。

## 计划自检

plan-self-check.json只检查文档章节、每份文件数、不变量验收映射、链接和新增DOM/CSS类、已审计业务源码SHA未改变；不是实现测试。十个子计划仍为待执行。发布前还必须执行各子计划的真实测试/IT和浏览器验收。

阶段0匹配知识已更新hit_count/last_used；没有归档此次使用项。无新条目跨过10次推广阈值，已有高频规则已在CLAUDE引用；本次新增统计口径、论文证据只读/时间归属知识，并补充重审未提交项不抢占Gate的边界。
