# 深度发现联合修复：代码审计附件

审计基线：`64c0394a940bd79c2ecc04e5c497650f045faa75`，2026-09-26，main。只读代码核验；未重新访问生产、未重跑历史批次。行号针对本基线，后续按方法名定位。原始 grep 命令和完整输出在 [discovery-repair-evidence](discovery-repair-evidence/)，不是凭文件名推断调用点。

## 证据分级

- 真实来源：DD-01～07、09、12、13、21、22；具体原始文件及 SHA 见 [审计台账](../../audits/2026-09-26-deep-discovery-diagnosis/README.md)。其中 DD-07 只证明输入/拆分错误，未验证恢复数量。
- 生产事件：DD-08、11、15、16；20240 是历史任务，不代表当前线上状态。
- 构造输入：DD-14、19、20；DD-18 为真实 service 的 ES500 故障注入。它们证明代码行为，不证明生产影响人数。
- DD-10 为来源异常，未证明本系统写错；DD-17 为显示语义。
- 原始 PDF/HTML 位于 evidence/original-sources.zip；XML 为 evidence/round2/PMC7759461.xml。不得将重新排版的文本冒充命名论文夹具。(来源: K-named-fixture-must-use-real-row)

## 存储和读写闭包

### C1：discovery_source_cursor

- schema：V32__create_discovery_source_cursor.sql:1，source_name VARCHAR(50) UNIQUE NOT NULL，cursor_value TEXT NULL，papers_processed_total BIGINT DEFAULT 0。DiscoverySourceCursorRepository.kt:9 按 sourceName 查；继承 CrudRepository.save。
- 写入口：ExpertDiscoveryService.persistSourceCheckpoint:146～184 → cursorRepository.save:177。论文循环局部 persistCheckpoint:617、ORCID 对应局部函数及退出落盘都进入此处。无 migration/backfill 对该表赋新业务值，检索全集见 cursor.txt。
- 读入口：同类 loadSourceCheckpoint:121～143；persistSourceCheckpoint:160 读旧行合并计数；queueLegacySeedCursor:1460～1477 供队列初始化继承位置。
- 表达：DiscoveryCheckpointCodec 的 ACTIVE/EXHAUSTED envelope 与查询 hash；计数不代表恢复位置。同步 EXHAUSTED 下次从头重开，队列 EXHAUSTED 不按日自动重开。(来源: K-search-error-must-not-clear-cursor)
- 交互 X1：同步页消费写 cursor → 下一次同查询 load；队列初始化读旧 cursor。01 只修去重失败时的推进条件，不改 codec、查询键、继承策略。
- 当前漏项证据：论文路径 :725/:763/:773/:815 只查 RAW/入队失败；dedup 在 :1322 置计数/标志。ORCID :999 同样 continue，:1057/:1067 同样漏 dedup。论文路径有故障注入回执；ORCID 只有代码证明，01 必须补 RED 用例。

### C2：discovery_pipeline / discovery_collection_stream / discovery_paper_job

- schema：V133__create_discovery_paper_queue.sql。单例 pipeline desired_state=PAUSED/RUNNING；stream UNIQUE(query_hash,source,epoch)、cursor_state ACTIVE/EXHAUSTED；job UNIQUE(stream_id,item_key)，metadata_json MEDIUMTEXT NOT NULL、extraction_json MEDIUMTEXT NULL、payload_version INT，lease_token/generation CAS。数据库不存 PDF 二进制。
- 入队/位置写：DiscoveryPipelineService.collectPage:830 → repository.enqueuePage；DiscoveryPaperQueueRepository 同事务插 job、计数量/字节并推进 stream。insertFailedItem 保存不合格元数据的轻量 FAILED 行；recordStreamError/deferStream/releaseStreamLease 只更新错误、下次时间、租约。具体实现与调用全集 queue.txt。
- 抽取写：ExpertDiscoveryService.extractQueuedItem:1606 序列化 outcome+identityRuleVersion；DiscoveryPipelineService.processJob（:1005～1031）→ repository.saveExtraction:1009（SQL :1025），将预留空间转实际字节，租约 CAS。
- 清理写：DiscoveryPipelineService:603/608 → clearTerminalPayloads:1351（metadata 清空，extraction=NULL）/deleteTerminalJobs:1391。入队不赋 extraction，默认 NULL。完成/领取/续租/returnToPending/completeJobWithExperts 不改抽取内容；会改状态、租约、计数和保留空间。
- 读：repository.jobRow:1506 供 claim/find/lock/complete；pipeline :1005 非空结果跳过下载；consumeQueuedItem:1647 要求当前 EXTRACTION_VERSION。旧版本进入 unrecoverableReason，pipeline :1076 判 FAILED，不会自动重抽。
- 错误恢复：collectPage:853 记录 SOURCE_ERROR，SOURCE_ERROR_BACKOFF=:1434 的 5 分钟；处理失败最多 5 次 (:1428)。去重失败已被 consumeQueuedItem.succeeded 排除，:1105 使用 DEDUP_LOOKUP_FAILED 重试。02 不往队列内再叠加一层即时重试。
- 交互 X2：parser→新鲜版本化结果→saveExtraction→消费；结果规则变动须明确旧缓存行为。(来源: K-identity-cache-version-and-admission)
- 交互 X3：采集失败→stream.next_attempt_at→后续领取；人工暂停/租约失效仍阻止消费及错误推进。同步与队列控制不可混同。(来源: K-discovery-budget-backpressure)

### C3：专家 RAW / CANDIDATE / APPLICATION

- mapping 实读：src/main/resources/es/orcid_info_{raw,candidate,application}.json 根 mappings.dynamic=false；identityVerification 对象 dynamic=false，status/source keyword、version integer、姓名/邮箱/作者 ID 等 index=false；externalIds enabled=false。RAW/CANDIDATE givenNames/familyNames keyword；APPLICATION text。
- 本组不修改任何 ES schema、索引写函数、资格函数、发信选择器。以下是输入变化所经过的身份写闭包；全部索引访问文件/行在 es-sites.txt，身份字段和相关 writer 调用在 identity.txt。
- 发现写：ExpertDiscoveryService.consumeOutcomeInternal:1247→buildProfile:1855→toIndexMap:1870；ORCID buildOrcidProfile:1117→同一 toIndexMap；RAW 均经 ExpertIndexWriterService.indexToRaw:602（discovered 使用 op_type=create），候选经 promoteDiscoveredToCandidate:1891（create）。它们是此次 parser 结果的落点。
- 重复路径：recordIdentityDuplicate:1830 读取现存 _id/_source，比较整体身份；冲突只记失败原因，不覆盖旧姓名/作者 ID；只在原规则允许时补建学术补全任务。
- 其他写/转移：ExpertIndexWriterService.promoteToCandidate:531、writeCandidateDocument:692、promoteToApplication:404；ExpertRevalidationService 重新资格校验后调用 writer；发现 RAW 补邮箱/晋升路径 :2899/:2918。层级操作从 ExpertIndexLevelOperationService / ExpertContactManagementService 进入。既有身份随 _source 转移，不在本组改写。
- 非身份局部写：ExpertIndexWriterService 的 classification bulk、operatorStatus 单条/bulk、applicationStatus、标签、移除/降级；ExpertDiscoveryService.updateExpertAcademicFields :2567 对 discovered 以 identity/email/name/externalIds 作 CAS 防旧画像覆盖。这些保留。
- 读：ExpertDiscoveryService.existsInRawIndexByEmail:2774、trustedOpenAlexAuthorId:2627、trustedOrcid:2642；ExpertSearchService 的画像映射及分页/搜索；CandidateEligibilityService/ExpertRevalidationService 的资格读；writer 晋升读 RAW/CANDIDATE；联络/发送通过已有画像读，不新增 DiscoveryIdentity.allowed 发送闸门。具体命中见 identity.txt/es-sites.txt。
- 交互 X4：来源证据→完整 AuthorEmail→身份过滤→邮箱验证→去重→RAW→原资格→CANDIDATE。每个解析子计划至少一条真实输入穿过实际 parser 与 consumer/writer（ES/验证服务可替身，作者归属不可替身）。
- 交互 X5：同邮箱重复→原身份不覆盖→补全入队仍使用现存 _id；同名不同邮箱不合并。(来源: K-author-identity-needs-email-evidence、K-historical-identity-orcid-fallback)

### C4：OpenAlex 预算账本（只复用）

- OpenAlexDataSource.getJson:74～88 每次 reserve，再 exchange；响应 recordResponse，IO recordTimeout 为 UNKNOWN，不退款。
- OpenAlexRequestPolicy:691～709 记录响应、Retry-After 冷却；:871～883 延期直接返回 Deferred。02 不修改其 schema/存储/计费、不给 TLS 超时退 credit；重试重新调用 searchPapers→getJson。
- 交互 X6：每次实际元数据调用→reserve/结算；Deferred→同步保留 cursor，队列 deferStream。apiRequests 不等于已计费 credits。

### C5：任务进度、历史记录（只改已有字段值/展示）

- schema 实读：V4 task_execution status VARCHAR(32)、result_summary TEXT；V22 task_progress_log details_json/errors_json TEXT、task_execution_id nullable，无外键；V35 batch_reject_reasons，V137 owner_token/heartbeat 与中断字段。没有新增列。
- 内存写：TaskProgressStore.update/tryStart/tryStartWithToken/bindExecutionId/requestCancel/clear/clearExecutionContext/setCurrentExecutionId；persistProgressLog:192 写 DB（save:209），bindExecutionId:184 rebind 负 token。恢复读 restoreFromLog:215。详情含 bySource，不能跨 executionId 复用旧进度。
- DB 写：TaskExecutionService 的开始 save、updateProgressCounts、finishOwned；心跳 heartbeatOwned、失联 interruptExpired、人工 interruptManually；Retention 清理调用两 repository 的 deleteOlderThan。TaskProgressLogRepository 写点为 save/rebind/deleteOlderThan。按表字段检索全集 progress.txt。
- 读：TaskExecutionService 列表/详情，TaskExecutionController 任务记录、TaskProgressController:109/113/159读当前/历史批次日志；TaskExecutionSummaryExtractor:158读findTopByTaskExecutionIdOrderByIdDesc，从details_json抽取执行摘要；BatchSendConfigController:264/371读配置任务日志（本组不改变该任务类型的数据）；TaskProgressStore.get/peek/restoreFromLog；TaskExecutionRepository 的筛选/统计及 BatchTaskConfig 读最近执行。详情 result_summary 与 progress.details.bySource 共用 app.js renderBySourceTable :2681。
- 统计生产者：ExpertDiscoveryService SourceStats→buildBySourceDetails:239，snapshotFailureReasons/snapshotFilterReasons 各最多20项；日志 :850/:1106 把 filtered 统称资格淘汰；DiscoveryResult/DiscoveryTerminalStatus 决策终态。
- 交互 X7：SourceStats/bySource→TaskProgressStore/TaskExecutionService→实时表和历史详情。08 显示完整**已存**原因，不声称恢复后端早已截掉的键；下载总类与 HTTP 子类不能相加当人数。

### C6：解析共享入口及文件

- resolveText：PdfEmailExtractor.associateEmailsWithAuthors:279、CoreDataSource.extractAuthorEmails:159。
- resolveHtml：PdfEmailExtractor.extractFromHtml:148。
- JATS：EuropePmcDataSource/PmcOaDataSource；OpenAlex XML 回退接收 EuropePMC 结果。入口全集 parser.txt。
- PDF：OpenAlexDataSource:160、CrossrefDataSource、ArxivDataSource、CoreDataSource 的下载回退。同一 URL 只试一次、至多3地址、整篇共享时限见 OpenAlexDataSource :91～168；当前 PDF/EuropePMC 已调用 FetchRetry，02 不重复创建下载重试。
- PlainTextEmailExtractor:23 对邮箱清洗，但 resolver.textClaims:58～87 用原文本；brace regex :39 仅字母/逗号。03 对齐两者时必须保留段落边界。
- 测试资源是执行时写入的固定 ZIP/JSON：原文来自诊断归档，不含凭据；classloader 读、核对内部 manifest SHA；禁止测试启动后现抓网络。ZIP 解包路径不得逸出。真实/合成目录明确分开。
- 交互 X8：共享 resolver 规则同时影响 PDF/HTML/CORE；JATS 规则同时影响 PMC/EuropePMC/OpenAlex。需要旧正例与共享/冲突负例回归。

## 前端样式盘点（08 的事实输入）

- table-wrap：styles.css:987～991，overflow-x:auto、-webkit-overflow-scrolling:touch、width:100%。
- data-table：:3421～3456，width:100%、border-collapse:collapse、font-size:11px；th/td padding:6px 8px、text-align:left、border-bottom:1px solid var(--line)。th font-weight:600、color:var(--text-muted)、background:rgba(15,23,42,0.02)、sticky top:0、font-size:11px、letter-spacing:.3px；details 11px，summary cursor:pointer、color:var(--primary)、font-weight:600。
- 全局继承：:993 table min-width:720px；:1022～1032 td font-size:13px，hover rgba(var(--primary-rgb),.07)，transition .12s。:1～75 tokens primary=#1e40af、primary-rgb=30,64,175、text-muted=#94a3b8、line=rgba(15,23,42,.055)、panel-bg=rgba(255,255,255,.55)。08 不改这些规则；现有 table-wrap/data-table 使用点全集 ui.txt。
- 被改区域基线：app.js :2681～2724；逐字复制保存在 discovery-repair-evidence/renderer-before.js。原来8列表用 inline padding:3px 8px，失败 cell max-width:120px/ellipsis/nowrap，失败仅前3项。改用已有 data-table 6px纵向间距，原因允许展开，明确不是逐像素保留旧表。
- 缓存键：index.html:11/2286/2290 三引用均20260925-mail-open-tracking。`rg -l '20260925-mail-open-tracking' src/test` exit=1，无硬编码此值的现存测试；08 增加三引用一致断言，不改其他静态资源内容。

## 知识加载结论

9 条匹配知识已刷新 hit_count/last_used：discovery 5条、task预算1条、audit3条。上文已逐项使用；历史 ORCID 键经验只用于禁止本组改历史关联键。没有本轮使用后仍超过90天未使用的条目，无需归档。审计计数规则已在 CLAUDE.md:146 推广，不重复追加；本轮没有5条同题知识可合并。agents/ 与 templates/ 不存在，不凭空建角色目录。
