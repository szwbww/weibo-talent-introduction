# Apollo 辅助核验：代码、存储、样式审计

审计日期2026-10-18；HEAD `e7c3422c176be971d823e2b82b1bca63ebb04741`。以下行号属于该基线；执行前diff确认。事实与拟实现方案分开：本文件“当前”有代码/回执支持，contracts文件定义将来的接口与行为。未读线上账户配额，不猜流量或核验成功率。

## 证据索引

- [命令与hash](apollo-evidence/manifest.json)、[路径总表](apollo-evidence/path-inventory.txt)、[关键代码逐字快照](apollo-evidence/critical-paths.txt)。量化断言以保存的命令输出为准；不可只写“搜索过”。
- admission SQL及调用：[直接SQL](apollo-evidence/admission-store.txt)、[调用者](apollo-evidence/admission-callers.txt)、[repository方法](apollo-evidence/DiscoveryReviewRepository-methods.txt)。
- ES：[写读调用](apollo-evidence/es-write-read.txt)、[索引引用扩展检索](apollo-evidence/es-index-references.txt)、[schema逐字](apollo-evidence/schema-before.txt)。
- 持久队列：[SQL及引用](apollo-evidence/queue-store.txt)；task：[直接表名](apollo-evidence/task-store-full.txt)、[repository调用](apollo-evidence/task-store.txt)。
- [前端基线完整片段](apollo-evidence/frontend-before.txt)、[共享class使用点](apollo-evidence/shared-style-uses.txt)、[注册/缓存/日志](apollo-evidence/frontend-hooks.txt)。
- [业务Apollo检索](apollo-evidence/apollo-backend.txt)：`rg -n -i apollo src/main src/test`返回exit1，无匹配；这不否认scripts存在Apollo试验。

## 1. expert_discovery_admission / expert_discovery_review_item

Schema：V148，docId大小写敏感varchar128主键；identity_hash char64；decision varchar24；revision bigint；policy_version varchar32；review_item保存批次/动作/状态/旧revision/快照/执行ID，unique(batch_key,expert_doc_id)。没有Apollo来源、资格明确性或自动过程历史。

当前直接写路径（源码包含表名的写SQL全部在下面两文件；不是只在repository）：

|位置|写入/触发|新功能处理|
|---|---|---|
|DiscoveryReviewRepository.initializeAdmission:37|INSERT IGNORE初始自动结论|保留；新流程调用前有纳管gate|
|同文件applyItem:239|行锁+revision/identity CAS写人工当前决定及item|保留优先级|
|同文件revokeCurrent:287|撤销当前人工决定，恢复自动结论|自动结论必须使用新gate|
|同文件insertItem:77，markBatchReady:163，claimBatchItems:193，cancelUnappliedItems:225，markItemFailed:345，私有update/mark:369/375/386|人工审核生命周期与历史|不往这里塞Apollo自动批次，保持语义|
|DiscoveryReviewService.updateAutomaticAdmission:1435|**直接JdbcTemplate UPDATE** CAS更新自动结论|自动结果与新证据在一个事务提交；不能绕过manual保护|
|同文件markCandidateSyncFailed:1455 / clearCandidateSyncFailed:1463|人工review item同步错误|新worker错误存新账本，不伪造人工item|

触发上述自动写入的上游：ExpertDiscoveryService两个收录分支→admitDiscoveryExpert:2452→recordAutomatic:844；ExpertRevalidationService:371/395；DiscoveryReviewService初始化worker:1170、refreshworker:1230、撤销:782；身份修复/恢复通过重验接入（详见admission-callers原始输出）。

读取：repository.findAdmission(s)、findItem(s)、history、batch分页/状态；DiscoveryReviewService.resolveAdmissionBatch:979供发送选择器；DiscoveryReviewScanService.enrich:237读当前决定并重新计算automaticReasons；manual prepare/confirm/revoke读真实RAW/seq/term与历史revision。发送BatchRecipientSelectionService:163批量读持久准入；DiscoveryPersonRecipientService复用selector，已有联系保护通过其读取。仅查新账本不能替代这些真值读者。

**旧知识修正**：K-discovery-admission-read-write-parity中“Writer未读institutionEvidence/filterResult”是2026-10-04事实；现在ExpertIndexWriterService.discoveryProfile:771–797已读取，不能再安排同样修复。当前仍有独立 `hardBlockingReasons`，必须处理新学历/年龄gate。

## 2. RAW / CANDIDATE / APPLICATION

Schema：`src/main/resources/es/orcid_info_{raw,candidate,application}.json`；根层dynamic及identityVerification.dynamic以[逐字schema](apollo-evidence/schema-before.txt)为证，不拿过去线上dynamic值当当前事实。`age` integer，`degree` keyword，`employment/institution`文本，externalIds enabled=false，identityVerification具status/version/email/name/evidence，institutionEvidence keyword。本轮**不新增ES字段、不改mapping、不回填博士/年龄/机构**。

|写入族|实际入口/字段|本计划边界|
|---|---|---|
|新收录RAW|ExpertDiscoveryService:1450/1779→ExpertIndexWriterService.indexToRaw|登记意向后原样写；不把Apollo资料混进RAW|
|自动/人工候选投影|Writer.projectDiscoveryCandidate:872，writeCandidateDocument:939|复用create/409身份核对；只新增候选，不覆盖现存运营字段|
|候选重验更新/删除|ExpertRevalidationService.revalidateDiscovery:340→Writer.reconcileDiscoveryCandidate:803|纳管档案用新gate，禁止旧资格理由误删|
|历史晋升扫描/旧补全|ExpertRevalidationService:172/221/279/298；ExpertDiscoveryService.promoteRawToCandidateWithEmail:3504、backfill:3558|新发现通过重验统一；非发现原路径不变|
|学术补全部分更新|ExpertDiscoveryService.updateExpertAcademicFields:3167/3221|只读其真实结果，proof失效要重算；不改写它的归属来源|
|身份/人物维护|DiscoveryIdentityRepairService、DiscoveryIdentityRecoveryService、DiscoveryPersonService/维护服务|identity/fact变更使旧核验不可复用；本轮不修改修复算法|
|人工资料/状态/晋升|ExpertIndexWriterService各partial更新，ExpertIndexPromotionService整_source复制，ExpertManualProfileService，ExpertSearchService写入相关入口|不写新字段；不用Apollo重写_profile、contact、operatorStatus|
|离线脚本|apply_legacy_discovery_approval、SBIR导入/职称更新、contactout导入、enterprise_batch的import/update/enrich脚本|不运行、不批量重分类；仅新ENFORCE账本纳管，存量脚本不改变适用范围|

精确文件/行号列表在es-write-read与es-index-references回执，包含tests及脚本噪声，不能把搜索命中行数当写路径数量。对应读者：ExpertSearchService的列表/scroll/toExpertProfile，Writer.discoveryProfile/读取候选，ReviewScan审核投影，DiscoveryPromotionProgressService候选存在性统计，人物查询/恢复/维护，campaign选择器。新增业务不通过ES聚合Apollo统计，不依赖dynamic映射。

## 3. 新 discovery_verification_* 三表

当前不存在。01定义完整schema，schema/字段语义见contracts C-2/C-3。

|写者|写内容|读者|
|---|---|---|
|05 RegistrationService|run快照、RESERVED、激活QUEUED、input_closed|04 worker；03gate；06报表|
|04 Worker / 01Repository|租约/预算请求/attempt/证据/终态|03gate、04提交/恢复、06报表|
|04 AdmissionCommitService|证据+自动准入revision事务；投影状态|03gate、06明细；旧发送selector仍读原admission|
|03 gate|只读；没有写入|policy/revalidation/scan读同一context|
|06 QueryService / Controller|只读；绝不GET时修复统计|07弹窗|

新reader必须区分未登记、读取失败、记录存在但无证据；无法读ledger时对已纳管目标不得当“无ledger”走旧准入。为了判断纳管范围，数据库不可用时新发现/重验该次失败返回，不做准入/候选改变。旧历史不被标新政策拒绝，但也不能在存储错误时假装拿到有效新证明。

## 4. discovery_pipeline / collection_stream / paper_job / source_cursor

Schema V133：pipeline单例、criteria_json/version/query_hash、generation/owner租约、execution_id；stream UNIQUE(query_hash,source,epoch)，cursor与入队同事务；job UNIQUE(stream_id,item_key)，token+generation CAS；V32 source_name varchar50保存旧同步游标。

写路径：DiscoveryPaperQueueRepository的ensure/launch/pause/resume/owner/bindExecution/updatePhase/counter/stream/page/claim/saveExtraction/complete/retry/cleanup（[完整表名检索](apollo-evidence/queue-store.txt)）；DiscoverySourceCursorRepository保存旧同步游标；ExpertDiscoveryService与DiscoveryPipelineService驱动；定时ExpertDiscoveryScheduler在pipelineEnabled=true仅tick、旧模式同步discover。读路径：pipeline/status/resume、scheduler、窗口consumer、checkpointcodec与来源查询。

本轮只让现有criteria_json携带一个 `apolloAssist` 布尔值快照；不用新schema列，不改paper_job payload字段，不把Apollo耗时塞进PDF消费线程。`canonicalCriteria`当前只含学术查询条件（DiscoveryCheckpointCodec:68）；Apollo开关不改变来源查询hash和已采集游标；相同查询但辅助开关不同、尚有活跃窗口/积压时launch须事务内409 `VERIFICATION_OPTIONS_CONFLICT`，不能静默沿用旧选项。

**旧知识修正**：K-discovery-cron-daily-gate-and-cancel中“每日只运行一次闸门”已过时；当前ExpertDiscoveryScheduler:57明确无该闸门。保留取消与持久PAUSED的区别，不安排删除已不存在逻辑。

## 5. task_execution / task_progress_log / 学术补全队列

Schema V4/V22+V35/V73/V100/V102/V137；request_payload/result_summary/details_json都是JSON文本，progress.batch_passed不是业务准入专用列。task记录有独立保留策略。

写路径：TaskExecutionService开始/结束及owner心跳，TaskExecutionRepository恢复/中断/清理；TaskProgressStore进度update写TaskProgressLogRepository，pending token绑定正execution；TaskAuditRetentionService分别清理；DiscoveryReviewService也通过任务服务记录人工审核。直接表名及repository调用已保存。读路径：TaskProgressController进度/批次/执行列表，TaskExecutionController任务活动详情，TaskExecutionSummaryExtractor，DiscoveryPromotionProgressService；mail/campaign按execution关联邮件统计，保持不变。

学术补全队列V131 unique(expert_doc_id)，enqueue/claim/lease/complete/reopen由JobRepository/JobService及ExpertAcademicEnrichmentWorker管理；它的SUCCEEDED/UNMATCHED是OpenAlex补全状态，**不能当Apollo状态**，不能复用同一唯一键存第二种provider任务。

新日志使用独立API，不改通用progress列含义、summaryExtractor或TaskProgressStore写者。前端原“采集日志”仍读原API，但标签写“收录/未收录”；新的“准入批次”从新账本编号，不冒用论文批次号。采集结束后保留新核验的5s轮询，关闭/换execution取消。

## 6. Apollo 与公开证据核查

当前生产目录没有Apollo（回执exit1）。`scripts/expert_discovery/adapters.py:213–231`有脚本Search/enrich endpoint映射；这些不是Spring服务，也不具生产准入事务。`outputs/apollo-pending100-20261017/revalidate_variants.py:92–108`是历史试验，不可直接作为全分页/生产幂等实现。

本地历史回执只检查字段形态，不复制个人记录：`outputs/apollo-institution-audit-20261017/apollo-access-response.json`有7个person对象，`institution-search/search-results.json`有6个；两者都含first_name/last_name_obfuscated/title/organization，均未有非空last_name/email/linkedin_url/education/birth_date。这是响应样本事实，不是保证未来所有响应字段相同。无个人内容的统计与原文件SHA见[response-shapes](apollo-evidence/apollo-response-shapes.json)。

2026-10-18核对官方资料：
- [People API Search](https://docs.apollo.io/reference/people-api-search)：POST `/api/v1/mixed_people/api_search`，不返回邮箱/电话；分页公开上限100条/页、500页；q_person_name支持姓名搜索。Search标示0 credits，实际账户余额未实测。
- [查找人员](https://docs.apollo.io/docs/find-people-using-filters)：结果是有限人员/机构信息，has_email不能等同邮箱可用性。
- [ORCID公开记录读取](https://info.orcid.org/documentation/api-tutorials/api-tutorial-read-data-on-a-record/)：可读person/education/employment/researcher URLs，字段有可见性及来源，缺失不证明不存在。项目当前OrcidDataSource:30只保留orcidId/name/email/institution/country，因此公开履历reader是新增工作。

不以第三方博客作为接口事实；首版不声称具备未实现的全网学历核验。来源口径限定见contracts C-4。

## 7. 前端样式盘点

逐字完整规则与修改前HTML已保存[frontend-before.txt](apollo-evidence/frontend-before.txt)，不是根据截图臆造。07只派生scoped新class，不原地改全局复用class。共享class各使用点检索见[回执](apollo-evidence/shared-style-uses.txt)。

|复用class / token|源码行号|实值/用途|
|---|---|---|
|:root|styles.css:1–91|primary #1e40af；hover #1e3a8a；active #172554；text #1e293b；secondary #475569；muted #94a3b8；bg-subtle #f8fafc；panel-bg rgba(255,255,255,.55)|
|radius/shadow|同上|sm7px/md10px/lg18px；shadow-xl `0 20px 48px -12px rgba(15,23,42,.2),0 4px 12px rgba(15,23,42,.06)`|
|.button/.primary/.secondary/.small|802–879|现有蓝渐变按钮/轻按钮，复用，不自造主操作按钮|
|label / input,select,textarea|1117–1160|label默认column；input width100%、height/min-height34px；新小checkbox必须完整局部覆盖|
|.data-table|3421–3456|表格100%，collapse，6px8px；th默认sticky且透明；只在新日志scope取消sticky并用#f8fafc|
|.modal-overlay/.modal-content/.modal-header/.modal-body|4483–4533|overlay24px；圆角18px；max-height80vh；纵向auto；header16px20px、body20px|
|.task-modal-log-table-wrapper|4553–4562|原max-height300px+overflow双轴；新日志局部改纵向visible、表格单独横向scroll|
|.task-modal-config-section/.task-modal-input-*|4662–4692|config gap16；label12px600；input8px12px、圆角7|
|.task-modal-divider/.task-modal-logs-title|4735–4768|分隔线、13px标题、4×12px蓝色竖条|
|.batch-send-tabs/.batch-send-tab|9150–9187|gap28、48px高、0 28px；原采集/审核/记录3页签保留|
|discovery modal scope|12675–12708|max-width1180px；width calc(100vw - 48px)，≤640时减20px|

DOM基线：index.html:1129开始taskProgressModal→modal-content.task-modal→header→discoveryReviewTabs→discoveryPipelinePane→modal-body→config/progress/divider/task-modal-logs→table→taskModalRunBody。另有discoveryReviewPane及historyPane。新summary必须在**展开执行行内部**；不在顶部插卡片矩阵。

现有app.js:4349把失败理由截top3+ellipsis；:4429内层max-height200又产生第二纵向滚动；二者与sticky透明表头组合对应用户截图重叠。07只对深度发现分支提供新完整行结构，其他任务保留旧函数分支。

缓存实际16个带?v资源（并非旧测试注释中的11）：`frontend-hooks.txt`可核；多份JS契约断言16且runtime无版本，故本计划**不新增JS资源文件/不改runtime引用**，功能代码在app.js有边界标记的区块中实现；16个key统一改`20261018-apollo-verification`，保持顺序。不会为模块化随意扩展10个测试文件。

## 8. 跨模块交互点

|IP|写→读|风险|验收|
|---|---|---|---|
|IP-1|发现RAW/登记意向→核验worker→报表|漏登记、串execution、重放多计|总A-1/A-4；05 A-1/A-2|
|IP-2|证据+准入事务→候选投影→旧发送selector|SQL/ES分裂、sync.ok误解|总A-2/A-4；04 A-2|
|IP-3|新证据→policy/revalidation/refresh/scan|重验删掉已准入、审核显示另一套理由|总A-2/A-3；03 A-1/A-2|
|IP-4|人工HOLD/撤销/身份变更→worker CAS/gate|晚到回包覆盖人|总A-3；04 A-3|
|IP-5|账本聚合→summary/batches/records→modal|混单位、null变0、异步响应串页|总A-1/A-5；06/07验收|
|IP-6|启动快照/暂停→连续窗口/已有核验队列|选项丢失或取消语义变化|总A-4；05 A-3|

## 9. Phase 0知识采用/排除说明

已读取条目列表见[knowledge-loaded.txt](apollo-evidence/knowledge-loaded.txt)，本轮只递增一次hit_count并更新last_used。身份/门禁/重验、存储投影、前端缓存/DOM/表头、任务ID/日志读者已映射总I-2～I-14及上述审计。未作为实施任务的条目：ORCID首机构歧义、多OpenAlex作者、历史ORCID回退、原版论文布局、来源召回、OpenAlex延迟恢复/预算等只作为“不得拿这些推测事实”的边界；它们对应P2或已存在逻辑，本轮不改解析器、下载器、搜索游标策略。精确行号guard仍列入必跑回归，但本轮不修改它钉住的operatorStatus赋值位置，禁止顺手放宽白名单。

已证伪的旧知识本轮回写：Writer投影字段已补齐；cron每日闸门已移除；旧“发送门禁固定四处”不能当当前数量事实；任务启动登记说明须补连续模式202路径，不再要求所有任务同步完成通知。不同主题条目不因同domain就机械合并；同主题≥5才按技能蒸馏。


## 研究期间的工作区增量（已只读核对）

在本计划编写期间，三个静态文件出现非本任务修改，详见[差异冻结](apollo-evidence/concurrent-frontend-delta.diff)与[增量hash](apollo-evidence/concurrent-frontend-manifest.json)：收件人数预估改为按钮手动触发、新增batch-recipient-preview-controls、16个资源key变为`20261018-recipient-preview-manual`。本任务没有修改这些实现文件。与深度发现目标DOM无结构冲突；styles.css第9310行后插入42行，因此原基线12675规则在增量中移到12717，按选择器和逐字块定位，不盲用行号。实施07须保留该功能全部差异，再统一资源key为本计划目标。不得重置三个文件到HEAD或覆盖为线上旧快照。
