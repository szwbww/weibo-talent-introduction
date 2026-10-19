# Apollo 辅助验证修复总计划

状态：待执行。日期：2026-10-19。基线：`e33b3cdcd228a6692c7aaeab047e5bafd0e7bd3f`；工作区已有未提交修改，执行前逐项保留、记录差异，不覆盖原 Apollo 计划或他人工作。

## 需求描述

O-1：让公开 ORCID 证据被解析，使用绑定本人 ORCID 的当前任职信息与 Apollo 候选交叉确认身份；资格不明确但身份已确认的 `APOLLO_UNCLEAR` 继续自动准入。
O-2：留存功能继续运行；修复后对同批历史档案及新一轮真实数据给出可追溯的“证据恢复→身份确认→持久准入→候选投影”对照结果。

必须保留：N-1 APOLLO_UNCLEAR 自动准入及明确/未明确区分；N-2 论文样本、ORCID/Apollo 返回与请求历史；N-3 人工审核/已联系/APPLICATION/SBIR/身份变化保护及租约 CAS；N-4 当前配额、请求预算、原 RAW 身份字段；N-5 当前页面结构、CSS、只读查询接口。

范围外：姓名模糊匹配、机构模糊词典、职称和博士年龄规则重写、补抓学历网站、Apollo付费接口扩展、论文下载403优化、全量历史清洗、扩大采样配额、自动发信或重启正在运行的任务。现有 isProfessor 对助理教授的处理和博士/年龄分支另案审计，本轮不夹带改写。

## 关键不变量

### Invariant I-1: 未明确准入保持
- Rule：APOLLO_UNCLEAR 必须仍为 route=APOLLO、qualification=UNKNOWN、matchedPersonId 非空；ENFORCE 经现有硬条件与 commit 后才计自动准入。SELF 任职不能变成资格 MET。
- Applies to：规则、Worker、Gate、Commit、报表及重处理。
- Violation consequence：暗改用户接受的政策，或把身份线索当学历证明。
- 来源：original；用户本轮明确保留。

### Invariant I-2: 留存独立且持续
- Rule：现有采样开关、限额、旁路失败语义、schemaVersion不变；不能清空旧 evidence/attempt/样本来制造成功率。冻结对照副本在留存根之外，原包仍受正常7天/容量回收。
- Applies to：部署配置、子计划02冻结/重处理/报告、现有 Capture/Store。
- Violation consequence：失去真实对照或误阻断采集。
- 来源：K-extraction-samples-are-not-consumption。

### Invariant I-3: 修复不是结果承诺
- Rule：报告分开固定输入纯规则结果与线上提交结果；必须输出负向变化、缺证据和新增请求，不能预设准入人数或声称未标注数据的准确率。
- Applies to：两个子计划的测试和验收。
- Violation consequence：用新增样本或重试量伪装提升。
- 来源：K-plan-quantified-claims-need-grep-receipts、K-admission-event-and-projection-counts。

## 现状审计

### 实证基线
- [原始脱敏重放结果](apollo-repair-evidence/baseline-replay.json)：2026-10-19 16:16:58，任务26839，固定89条已完成档案。仅把 public 大小写归一的实验：证据0→233条、覆盖0→63人，匹配/准入仍0；31人有SELF当前任职；机构来源任职2条且均UNKNOWN；6条新增NAME_CONFLICT。重复两次输出一致。
- 基线规则与库中原因差异1条 item88，库中IDENTITY_CHANGED来自提交保护，纯规则未执行该阶段；不得强行让两者同义。
- [固定档案ID](apollo-repair-evidence/cohort-item-ids.json) 和 [留存观察](apollo-repair-evidence/retention-baseline.json)。旧重放只保存了脱敏输出和输入摘要，原始输入当时在内存处理后释放；不能承诺恢复完全相同旧输入，执行前必须重新冻结同ID当前数据并披露差异。
- 核验解析类SHA `889a0b5def6a59cdc32efab3a2cdd7101f61948835c4d891161baeb3ee004960`，规则类SHA `7198dee48b904e5b4e9cc8d3d5932dd29a562381af836de36e31c19b52abc500` 已与生产一致。不是仅根据截图推断。

### 核验账本：run / item / attempt
- Schema：`src/main/resources/db/migration/V158__create_discovery_verification.sql`。run 主键 execution_id，mode/policy_version/settings_json 快照；item 唯一键 `(expert_doc_id,identity_hash,policy_version)`，snapshot_json/evidence_json 为 MEDIUMTEXT，state 默认 RESERVED，generation/request_count 默认 0，lease_token/lease_until/admission_revision 可空；attempt 唯一 request_key，response_json 保存裁剪后的响应，response_sha256 是 HTTP 响应摘要，不可拿裁剪 JSON 重新哈希冒充原 HTTP 哈希。
- 写入路径（完整 grep 回执：`apollo-repair-evidence/verification-store-paths.txt` 和 `db-all-read-write-paths.txt`）：`DiscoveryVerificationRepository.createRun/reserveItem/activateItem/closeInput/claimDue/renewLease/reserveAttemptWithBudget/completeAttempt/saveEvidenceAndOutcome/saveFailure/markProjection/reschedule/failReserved/saveProgress/completeAttemptAndProgress/attachAdmission/bump`。`DiscoveryVerificationRegistrationService.activate` 外层事务锁 run、初始化 admission、调用 activateItem；`recoverReserved/recoverDuplicate` 经同一 repository 恢复。迁移 V158 建表；隔离测试含直接 INSERT/UPDATE/DELETE，不是生产入口。
- 读取路径：Repository `reportingCollectionStatuses/reportingRuns/reportingCounts/reportingSuggestions/reportingReasons/reportingBatchTimes/reportingBatchReasons/reportingRecordTotal/reportingRecords/findRun/findItem/findManagedContexts/findItemsByDocIds/claimDue/listReserved/listProjectionDue/findAttempts`；内部 `identity/checkedIdentity/lockRun/lockItem/lockedItem/owns`。消费者：RegistrationService、Worker、CommitService、QueryService、AdmissionGate；规则间接被 AdmissionPolicy、ReviewScanService、ExpertRevalidationService 使用。
- Worker 在 `DiscoveryVerificationWorker.kt:149-175` 读取旧 payload，原来只在新 HTTP 完成 callback 中 parse；缓存 orcidCompleted 不会自动重新生成 evidence。`:177-204` 已有全名→别名→机构查询和 DECIDE 分支。本轮不是新建 Apollo 系统。
- 准入读取会重新运行规则：`DiscoveryVerificationAdmissionGate.kt:44-75` 检查持久 outcome/qualification/route/personId 与重算相等；不能只改 worker 或在查询接口里伪造结果。(来源: K-discovery-admission-read-write-parity)
- 锁序为 run→admission（需要时）→item→attempt；generation/token/lease CAS，网络请求必须在数据库锁外。预算按 attempt 持久计费，单条 request_count 同时包含 ORCID 与 Apollo；重算不退款、不清零。

### 准入表、审核历史、ES 候选投影（沿用，禁止新旁路）
- Schema：V148 的 `expert_discovery_admission` 每真实 docId 一行，decision/revision/identity_hash/policy_version；`expert_discovery_review_item` 唯一 `(batch_key,expert_doc_id)`，记录快照、状态、actor、error_code。无新增列。
- 写路径：`DiscoveryReviewRepository.initializeAdmission/insertItem/markBatchReady/claimBatchItems/cancelUnappliedItems/applyItem/revokeCurrent/markItemFailed/updateItemState/markItemApplied/markStale`；另有 `DiscoveryReviewService.updateAutomaticAdmission:1455` 直接更新 admission，`markCandidateSyncFailed/clearCandidateSyncFailure` 更新审核 error_code。自动准入经 `recordAutomaticPersisted→recordAutomaticConclusion/recordAutomaticAgainst`；人工 prepare/confirm/retry/revoke、initialize/refresh 共用这些底层写点。完整位置见 `admission-paths.txt`，不可误认为只改一个写点即可。
- 读路径：ReviewRepository `findAdmission/findAdmissions/findItem/findItemsByIds/findItemsByBatch/findHistory/batchStateCounts/findBatchCreatedAt/findItemsByBatchPage` 与锁读；ReviewService `resolveAdmission/resolveAdmissionBatch/projectApprovedCandidate`；CommitService.prepare/commit/project；QueryService.records/item；AdmissionPolicy/Gate、审核扫描、重验和发送选择器间接消费。
- `DiscoveryVerificationCommitService.kt:49-99` 校验当前身份/事实/人工保护/准入 revision，再调用 policy、recordAutomaticPersisted、attachAdmission；`:103-128` 后置投影。不能将规则建议直接当成实际准入，也不能由脚本写 admission 或 ES。(来源: K-admission-event-and-projection-counts)
- ES 仓库 mapping：`src/main/resources/es/orcid_info_raw.json`、`orcid_info_candidate.json`、`orcid_info_application.json` 均声明 dynamic:false；identityVerification 为 object/dynamic:false，externalIds enabled:false。这里是仓库定义，非再次查询生产 mapping。本轮无新 ES 字段/DDL。(来源: K-es-dynamic-false)
- 相关读写全集回执：`es-all-paths.txt`、`es-paths.txt`。ExpertIndexWriterService 的 RAW 写 `indexToRaw/reconcileDiscoveryCandidate`；CANDIDATE 写 `promoteToCandidate/projectDiscoveryCandidate/writeCandidateDocument/reconcileDiscoveryCandidate/removeFromCandidateIndex/demoteToRaw`；APPLICATION 写 `promoteToApplication/retryFailedPromotion`；旁路 partial 更新 `markApplicationClosed/syncOperatorStatus/syncOperatorStatusBatch/bulkUpdateExpertClassifications/syncApplicationStatus/addTag/removeTag/writeIdentityProofIfVersionMatches/writeDiscoveryNameAndProofIfVersionMatches`，发现补全 `updateExpertAcademicFields` 也是既有三层更新入口，均不纳入修改。读取 `readDiscoveryDocument/readRawDocument/discoveryProfile/documentExistsInIndex/findDiscoveryHitsByEmail`。
- 本轮到 ES 的写链固定为 CommitService.project→ReviewService.projectApprovedCandidate→Writer.projectDiscoveryCandidate（`:872`，创建带 op_type=create，已有同身份返回 ALREADY_PRESENT，APPLICATION_PRESENT 保护）；透传原 _source，不覆盖原姓名、邮箱、ORCID或学历。(来源: K-promotion-source-passthrough, K-historical-identity-orcid-fallback)

### 留存文件与展示
- `DiscoverySampleModels.kt:31-37` 定义 sample.json/schemaVersion=1/stage=EXTRACTION，含 paper/outcome/attempts/bodyBytesRetained；`DiscoverySampleStore.kt:80-115` 校验路径、大小和 SHA-256，`:136-146` 做过期/配额回收，`:148-197` 原子提交目录。写路径 Capture.capture/save→Store.commit，启动及 scheduled cleanup 可能删除过期包；读路径 Store.scan/retained、测试工具 DiscoverySampleReplay 和 scripts/expert_discovery/replay_samples.sh。完整回执 `sample-read-write-paths.txt`。
- 同步 `ExpertDiscoveryService.parallelExtractOutcomes` 和队列 `DiscoveryPipelineService.processJob` 采样；HTTP/PDF/XML hooks 记录尝试。文件只是抽取留档，不等于消费成功、收录或准入。(来源: K-extraction-samples-are-not-consumption)
- 本轮保留 `samples.enabled=true` 的现网外部配置，以及 7 天、512 MiB、1000 包、失败每类20/运行、成功对照10/运行、10 MiB/正文、2正文/包的当前限制。配置来源 `DiscoverySampleProperties.kt:8-21` 与先前线上核对；执行时再次读有效配置，禁止把源码默认 false 覆盖线上 true。
- 2026-10-19 16:12 留档为 110 包/51 正文，129037672 字节，正文大小与 SHA-256 全通过；数字是当时快照，不作为未来固定数量承诺。正常回收不算缺陷，效果验证需要先在留存根目录之外冻结对照副本。
- `DiscoveryVerificationQueryService.kt:150-171` 输出 reason label；`src/main/resources/static/app.js:27068` 使用服务端 reason.label/code 原样转义渲染。可补服务端文案而无需前端修改。所有 GET 保持只读；不在读日志时重算写库。

### 交互点
- X-1：ORCID缓存/新响应→Parser→Worker持久evidence→Rule/Gate重复判定。
- X-2：SELF当前任职→Apollo身份唯一匹配；资格仍只消费机构来源事实。
- X-3：Worker请求/游标→attempt预算与报表；新别名不能错位使用旧queryIndex。
- X-4：重排队→lease CAS→commit人工保护→admission revision→ES投影→报表。
- X-5：样本Capture/Store→对照冻结→解析重放；旁路失败不得改变采集结果。
- X-6：原因码持久化→QueryService label→现有日志页面。

## 实现方案

1. 顺序执行 [01：解析与匹配](apollo-assist-repair-01-evidence-matching.md)，9个变更文件。独立测试、独立可部署；只影响新核验/合法恢复，不自动改历史已完成记录。遵守I-1/I-2。
2. 再执行 [02：历史重算与效果验证](apollo-assist-repair-02-replay-evaluation.md)，6个变更文件。使用01修复后的共享规则；固定名单、先冻结、只重算、不重发HTTP。历史别名补查不混入本次对照，新任务自然使用已有别名查询逻辑。遵守I-1～I-3。
3. 总计14个不同代码/测试/脚本文件（FlowIT两个子计划共用），每子计划≤10文件，两个子系统：核验链路、受限运维重处理。无新增DB列、无新ES字段、无前端改动。计划/审计/知识文档是本次规划产物，不计入实现白名单。
4. 每子计划单独验证，再做组合验证。部署安排在采集输入关闭、核验队列排空后；不为执行计划强行取消用户运行中的任务。发布/重处理按后续实际执行授权进行，本轮只交付计划。

## 变更文件清单

本总计划不授权直接修改运行代码；执行白名单分别以01/02清单为准。本次规划新增：本文件、两个子计划、`apollo-repair-evidence/`审计回执与脱敏基线；知识计数及可复用结论更新单独记录，不覆盖既有修改。不得生成提前勾选的 acceptance 文件。

## 验收标准

- I-1：正例经过真实Worker→MySQL→Gate→Commit→候选投影，APOLLO_UNCLEAR计入自动准入且仍计入未明确；同名多候选/ORCID矛盾/人工HOLD均不得通过。
- I-2：原留存未被修复脚本删除；新的独立run仍产出sample.json，SAVED正文hash/size全通过；达到配额允许QUOTA，不能误报DISABLED。
- I-3：同一冻结输入完成旧版、仅解析兼容、完整修复三个输出；每档案能追到before/after及原因，新增准入逐条核对证据链。新任务数据另表；无人工真值时不输出准确率。
- X-1～X-6分别由子计划01 A-1～A-6、子计划02 A-1～A-6覆盖。Docker/MySQL集成用例不能以SKIP作为PASS。

## 人工验收清单

### A-1: 新核验与未明确准入
- 前置条件：隔离环境运行01的SELF/UNKNOWN资格、唯一Apollo候选用例，并保留该runId/itemId；无人工保护。
- 操作步骤：1. 开执行记录→逐条核验。2. 打开该item证据。3. 查看候选同步状态。
- 预期结果：outcome=APOLLO_UNCLEAR、qualification=UNKNOWN、自动准入1、其中Apollo1、未明确1；证据来源显示SELF而非机构证明；候选同步完成。
- 覆盖：O-1、N-1、X-1/X-2/X-4、I-1。

### A-2: 留存与旧数据对照
- 前置条件：按02冻结任务26839固定名单和样本副本；完成重算；另有一个独立新run。
- 操作步骤：1. 打开对照报告。2. 核对固定名单SHA及每阶段分母。3. 查看新run留档校验报告。
- 预期结果：每条有before/after/原因，原attempt保留；新run存在SAVED包，正文hash/size失败数0；没有将旧版89条与新run人数直接相减。
- 覆盖：O-2、N-2、N-4、X-3/X-5、I-2/I-3。

### A-3: 保护与界面回归
- 前置条件：02隔离用例含人工HOLD、已联系、APPLICATION、SBIR、身份变化；01包含页数上限用例。
- 操作步骤：1. 查看重处理结果及受保护候选列表。2. 展开日志原因。3. 对照修改前页面。
- 预期结果：保护样本新增准入0、原手工结论不变；显示“查询未完成：达到单次查询页数上限”；原页签、列、间距、颜色保持；刷新GET不新增attempt或改revision。
- 覆盖：N-3/N-5、X-4/X-6、I-1/I-2。

## 计划自查与知识使用

- 单计划范围：01为9文件，02为6文件，交集FlowIT为1，总14；每个子计划≤10，顺序依赖。
- 数据表示：不新增数据库字段、枚举state、ES字段或policyVersion；只增加两种既有reasons_json字符串码、隔离文件manifest格式，各有不变量。
- 样式：变更清单无HTML/CSS/前端JS，禁止新增样式；标签仅由QueryService提供，现有app.js消费者已定位。
- 13条相关知识已读取并刷新last_used/hit_count：证据/身份、审核读写对齐、预算/归属、采样非消费、ES映射/透传、量化回执。均在90天内，无衰减归档。预算知识仅用于不重置账本约束，不扩改OpenAlex；历史机构取first风险用于禁止新窄查询任意选机构，不修全局ORCID数据源。
- Apollo两条知识仍含“尚未实现”的旧阶段描述，本轮附2026-10-19复核纠正；不把旧阶段叙述当现存代码状态。
- 本次主题没有5条同题知识可合并；不为满足数量强行合并身份、预算、采样等不同问题。达到10次的身份归属知识追加CLAUDE.md索引；保留已有未提交内容。
- 实现/部署未开始；人工验收A-n完整列于各计划，验收开始才导出acceptance，不提前生成或勾选。
