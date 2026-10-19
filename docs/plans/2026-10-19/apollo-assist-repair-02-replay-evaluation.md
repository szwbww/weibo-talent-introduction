# 02：受控历史重算、留存保护与效果验证

状态：待执行。依赖01已通过机器验证；不依赖新增前端、数据库列或新外部接口。

## 需求描述

O-1：在指定运行、显式档案名单内，将历史PENDING档案用留存证据重新判定，经原worker/commit/gate落库，展示实际准入与投影结果。
O-2：保留原始留档及重算前快照，对固定输入输出旧版/解析修复/完整修复差异；另采一轮修复后真实数据，判断实际效果。

必须保留：N-1 APOLLO_UNCLEAR自动准入及原资格口径；N-2 采样功能、配额/回收、ORCID/Apollo原返回、请求流水及计费；N-3 人工/联系/APPLICATION/SBIR/身份事实变化保护和真实docId；N-4 原运行归属/批次、只读页面、幂等性。
范围外：HTTP补查旧别名、通用重跑平台、新管理UI、全量清洗、重新下载论文、覆盖已准入记录、重置预算、自动恢复人工HOLD、自动撤销准入或删除候选。本轮历史重算是decision-only；完整查询优化由新任务检验。

## 关键不变量

### Invariant I-1: 名单与原始输入冻结
- Rule：命令必须显式executionId和itemIds文件，1～100条/批；freeze采用同一数据库只读一致性事务读取run/item/attempt/当前admission，保存UTF-8 before.json与manifest SHA-256、采样时刻、代码类hash、mode/settings、原state/generation。freeze同时保存测试用Clock的UTC instant；保留原before中的providerFetchedAt而非改成当前抓取。freeze不更新任何表；目录0700、文件0600、无符号链接、原子写、fsync后再允许apply。
- Applies to：新ReplayTool freeze/evaluate/apply；文件写路径。
- Violation consequence：运行中数据漂移造成不可对照，或个人数据/凭据泄漏。
- 来源：original、K-plan-quantified-claims-need-grep-receipts。

### Invariant I-2: 只重排队未准入完成项
- Rule：apply只允许输入已关闭且task_execution.finished_at非空、该run无RESERVED/QUEUED/RUNNING/RETRY_WAIT项的运行；目标必须activated、COMPLETED/PENDING、admission_revision为空、projection_state=NOT_REQUIRED、无lease、personMatchedAt/personId为空。精确比对冻结snapshot/evidence/reasons哈希、identity/fact/policy/generation和admission身份/decision/revision（含不存在的情况）。已准入、ERROR、PROTECTED、在途项不转换。已知IDENTITY_CHANGED/FACT_CHANGED/SOURCE_MISSING/保护原因跳过，人工/legacy/已AUTO_PASSED的当前admission跳过。
- Applies to：Repository新增requeuePendingForEvidenceReplay、CLI apply。
- Violation consequence：覆盖人工结论、重复事件或处理正在变化的输入。
- 来源：K-discovery-admission-read-write-parity、K-historical-identity-orcid-fallback。

### Invariant I-3: 新写路径仅排队，提交走原链
- Rule：锁序run→admission→item；每批至多100条短事务（CLI显式TransactionTemplate，不直接调用裸@Bean绕过@Transactional）。requeue仅使state=QUEUED/outcome=NULL/qualification=NOT_EVALUATED/route=NONE、completed_at/next_attempt_at/lease=NULL，evidence_json保留旧内容但progress.stage设DECIDE，reasons_json置[]，generation原值+1，updated_at更新，run.revision+1。admission_revision保持NULL，projection_state保持NOT_REQUIRED；不写准入表、ES或contact。Worker claim后重新parse原文→evaluate→原finish→commit→project。commit必须重新读当前RAW/人工保护，不能信freeze时的ES状态。
- Applies to：新Repository方法、原Worker DECIDE、CommitService。
- Violation consequence：领域对象state/outcome约束失败、僵尸lease回写、绕开硬条件。
- 来源：original、K-admission-event-and-projection-counts。

### Invariant I-4: 计费、原文与归属不重置
- Rule：不改id/owner_execution_id/expert_doc_id/batch_number/activated_at/identity_hash/fact_fingerprint/policy_version/snapshot_json/request_count/transient_attempts及原Apollo阶段时间；不删attempt；orcidCompleted和search逐值不变，只用保存结果作决定；本轮即使恢复出新别名，也不声明这些别名已搜过，报告单列unqueriedAliasCount。DECIDE重算HTTP请求增量必须为0。已持久准入与候选投影分别统计，不从旧采集“晋升”列推断。
- Applies to：重排队、Worker DECIDE、报告、run查询。
- Violation consequence：伪造漏斗/提升，消耗重复Apollo额度。
- 来源：K-discovery-reentry-keeps-run-accounting、K-discovery-budget-backpressure、K-admission-event-and-projection-counts。

### Invariant I-5: 可重复与可中断
- Rule：apply接受before manifest完整SHA而非单纯itemId；按原generation及哈希CAS，重复同manifest最多排队一次/条。结果先由DB决定，再写after报告；报告写失败允许inspect补读，不回滚已准入业务。未入队条目可重试原manifest，已变化条目标记STALE_OR_ALREADY_APPLIED。一个失败不扩大到其他run。支持小批执行，不新增定时全量扫描；同批所有item均已改变时优先返回STALE_OR_ALREADY_APPLIED，不因本批上次排队而报运行不稳定。
- Applies to：CLI、Repository CAS及操作报告。
- Violation consequence：崩溃后重复准入、跨运行污染、覆盖历史。
- 来源：original。

### Invariant I-6: 留存持续与固定对照分开
- Rule：原 `/opt/talent/data/discovery-samples` 只由既有Store按7天/配额回收，不写入对照报告子目录；人工对照快照放 `/opt/talent/data/discovery-verification-replays/<executionId>/<manifestHash>/`，同名不得覆盖。论文样本如需冻结，从完整已提交包复制到该隔离目录并逐一校验；并发被回收的包报告MISSING_DURING_COPY，不能捏造原件。别修改线上samples.enabled、限额或schemaVersion。
- Applies to：冻结、部署、效果验证；Capture/Store留存链。
- Violation consequence：污染Store严格目录扫描、原样本被覆盖或误报留存失效。
- 来源：K-extraction-samples-are-not-consumption。

### Invariant I-7: 效果报告不得混淆
- Rule：报告分三层：同冻结输入的纯规则对照；该名单的实际commit/投影结果；修复后新run的完整HTTP观察。每层都输出分母、跳过/失败、原因分布、匹配、DIRECT/APOLLO_CLEAR/APOLLO_UNCLEAR、持久准入、投影状态、attempt增量。没有人工标签不得写准确率/召回率。原始89条仅是指定ID集合，当前内容若变必须记录而非冒充旧hash。
- Applies to：evaluate/inspect/report和人工验收。
- Violation consequence：把规则建议当真实入库，或不同样本之间虚算提升。
- 来源：K-plan-quantified-claims-need-grep-receipts、K-admission-event-and-projection-counts。

## 现状审计

- 沿用[总计划现状审计](apollo-assist-repair-master.md#现状审计)中的V158/V148/ES/采样schema及读写全集；本子计划新增写点只落run/item和隔离报告文件。原admission/候选链不新增旁路写点。
- Repository已有findRun/findItem/findAttempts/reportingCollectionStatuses/claimDue/lockRun/lockedItem/bump；目前没有已完成PENDING重新排队接口（检索回执 `verification-store-paths.txt`）。唯一身份键使“另建相同档案”不是合法恢复办法。新增一个受限repository方法，不改唯一键/policyVersion。
- `DiscoveryVerificationWorker.kt:204`已有DECIDE；01已为该分支补原文重建，因此02无需新增进度stage、HTTP接口或worker构造参数。
- `DiscoveryVerificationItem`在`domain/DiscoveryVerification.kt:74-84`约束非COMPLETED必须outcome=NULL，非终态admission_revision=NULL/projection=NOT_REQUIRED。只UPDATE state而保留PENDING会导致反序列化失败，计划I-3明确整组字段。
- 新的读取路径：ReplayTool.freeze读取上述表并写对照文件；evaluate只读冻结文件和指定版本类，不连接ES/HTTP、不启动Spring定时器；apply读取before并调用repository受限排队；inspect读取run/item/attempt/admission及既有详情输出，写脱敏报告。tool不提供API、不成为@Service。
- 新文件schemaVersion=1，仅用于隔离运维文件，不是数据库新字段：manifest包含executionId、sortedItemIds、capturedAt、baselineClassHashes、beforeSha256、固定UTC evaluatedAt；before包含原run/items/attempts/admissions；报告只含itemId、状态/原因/计数/哈希，姓名邮箱等原文留在服务器受限before。
- file写：freeze创建不可覆盖manifest/before；evaluate创建old/parser-only/fixed结果；apply创建逐条操作回执；inspect追加带时间戳的报告。读：后续上述命令及人工核验。失败不得删除before；不新增自动清理服务，存储受本轮100条和一次运行限制，保留清理路径于操作报告。
- 交互X-1：冻结文件→CAS输入；X-2：队列重算→原commit/Gate/保护→候选投影；X-3：stage/计数→只读报表；X-4：采样Store→冻结正文校验→新run留存验收；X-5：新规则对旧持久证据复算→原准入结果一致性。

## 实现方案

### T-1 单用途运维工具（I-1/I-5/I-6/I-7）
新增 `DiscoveryVerificationReplayTool.kt`（普通object/main，非Spring组件）及 `scripts/expert_discovery/replay_verification.sh`。只支持freeze、evaluate、apply、inspect四个命令，不做调度器/通用任务框架。
- wrapper明确JDK11和webapp/classes+lib或构建classpath；不使用服务器默认Java8，不启动完整Application。DB_URL/DB_USERNAME/DB_PASSWORD只从受限进程环境读取；不得出现在参数、日志、报告；异常需脱敏。
- freeze：要求 `--execution-id 26839 --item-ids <json> --output <受限目录>`；运行中允许只读freeze/evaluate，apply才要求稳定运行。一个REPEATABLE_READ/read-only事务读取范围，不用多条独立快照拼成“固定输入”。先按稳定格式编码before、计算SHA，再创建hash目录，fsync，写manifest；不把“HTTP response SHA”与“留存JSON SHA”混为一谈。
- evaluate：同before、同UTC evaluatedAt，分别以核验通过hash的旧类、新类运行独立JVM（参数数组传递，禁shell拼接）。旧组=原文+旧Parser/Rules；解析兼容组=原文副本中public归一+旧Parser/Rules；完整修复组=新Parser/Rules。每组都先检查local判定流程，并与Gate的持久判定方式区分；旧版baseline按现有RetentionReplay源码行为复现。不要在新代码中长期复制一套旧规则；旧类仅通过独立受控classpath加载。
- 各组两次结果必须相同；输出每item事实来源/时间分类数量、候选匹配和原因变化；6条原NAME_CONFLICT核对记录保留，但不能未经核对就给“错误人”标签。
- 原89个ID取计划附件；freeze时实际多少条、缺哪些、内容变化多少均报告。若无法获得旧版类或旧原始输入，明确UNAVAILABLE，禁止把旧脱敏输出硬拼作完全相同输入对照；可先做新冻结基线，不阻断其余已授权只读工作。

### T-2 有限重排队（I-2/I-3/I-4/I-5）
修改 `DiscoveryVerificationRepository.kt`，新增 `requeuePendingForEvidenceReplay(executionId, expectedItems, now)` 事务方法；expectedItems每项带itemId、原generation、原payload/snapshot/reasons哈希、预期admission状态及新evidenceJson，仅允许JSON变动progress.stage=DECIDE。实现需自行校验新旧JSON差异，不能信调用者可任意传值。
- 锁run后重新检查input_closed和task finished_at；目标run有在途/未激活预留项则整个apply返回RUN_NOT_SETTLED。每批在同一事务内先统一检查run是否稳定，再统一锁run→按docId排序锁admission→按itemId排序锁item，所有检查完成后更新合格项；不在每次UPDATE后再次以本批刚排入的QUEUED阻止自身。事务中禁止文件/HTTP/ES操作。至多100条；整个事务失败时该批写入0，报告按条SKIPPED并不意味着数据库错误可部分提交。
- 已保护、不符合条件、冻结后变更条目逐条跳过并返回明确码；其余按I-3变更。对每个成功项generation+1，并对该批成功排队触发run revision递增（至少一次；不得降低或重置）；重复apply成功数0。
- CLI在所有文件持久化完成后，使用TransactionTemplate包裹该repository批次调用，事务结束写操作回执；只通过此方法更新表，禁止脚本直接UPDATE admission/候选。
- 重排队用原itemId、runId和batchNumber；不新建run或新item，不复用“登记恢复”去假装未处理过。执行结束由正在运行的原worker领取，不在CLI里new Worker启动并发任务。

### T-3 实际结果与持续留存（I-3/I-4/I-6/I-7）
CLI inspect查询重算名单的完成/保护/错误/准入revision/投影状态和attempt差额；给出“规则可通过但commit拒绝”的独立列表。报告不能因为结果仍0就自动放松规则。
先在隔离环境完成测试；上线后在原任务稳定且获得实际执行授权时，先小批不超过10条，再处理同manifest剩余条目（如使用单事务批次，其manifest拆成互不相交子批，保留共同cohortHash）。每批准入结果逐条核对证据与人工保护；发现误匹配立即停止后续批次。
采集一轮修复后新run，沿用现有样本限额。对新run记录资料数、完整查询数、有候选数、确认本人、直接/Apollo明确/未明确、提交准入、候选投影、请求数及失败类型。旧run的110包达到分类配额，不应通过删包清额度；新run才用于继续采样观察。

### T-4 可重放与真实数据库回归（I-1～I-7）
新增 `DiscoveryVerificationReplayToolTest.kt`，修改 `DiscoveryVerificationRepositoryIT.kt`、`DiscoveryVerificationFlowIT.kt`。
- tool测试freeze权限/哈希/无凭据输出、evaluate时钟固定/无网络、before不可覆盖、损坏JSON拒绝；本轮独立main不启动Scheduler。
- RepositoryIT使用实际MySQL测试100条限制、事务锁序、原generation CAS、重复apply、目标run在途拒绝、本次同批排队不自我阻塞、人工decision/revision改变、错误hash/不同run拒绝、domain重新读取合法。
- FlowIT构造COMPLETED/PENDING+空evidence+原文缓存+Apollo成功历史，重排队后通过真实worker/gate/commit；对UNKNOWN正例断言APOLLO_UNCLEAR/admission_revision非空且新增HTTP0；人工变更、RAW变化、已联系/APPLICATION/SBIR、不完整search仍保护；候选投影失败重试不重新核验、不双记准入。
- 为避免改Gate/Commit扩大范围，出现旧已准入记录在新规则下被Gate拒绝时先报告原因并停止发布，不修改结果字段去迎合新规则；补兼容修复须修订计划。

## 变更文件清单

6个文件，两个相依子系统：账本受限排队、运维冻结/验证工具。无前端/DDL/ES字段。

| # | 路径 | 目的 |
|---|---|---|
|1|`src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt`|受限CAS重排队|
|2|`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayTool.kt`（新增）|独立main冻结/对照/排队/结果读取|
|3|`scripts/expert_discovery/replay_verification.sh`（新增）|JDK11/受控classpath启动|
|4|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayToolTest.kt`（新增）|冻结、文件、离线对照回归|
|5|`src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt`|真实数据库CAS/幂等/锁测试|
|6|`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt`|历史重算到提交投影集成|

测试报告、before/after等运行产物放指定证据目录，不作为额外实现文件。生产配置只核对/沿用，不授权修改配额或关闭留存。

## 验收标准

- I-1：freeze前后run revision、item字段、attempt行数逐值相等；before可重读并复核SHA，权限0700/0600；secret扫描无DB密码/API key；100+名单拒绝。
- I-2：运行中apply返回RUN_NOT_SETTLED且写入0；不符合条件各类明确SKIPPED原因；固定名单之外所有item逐值不变。
- I-3：正确排队后domain可反序列化：QUEUED/outcome NULL；领取后generation再递增且旧token不能提交；结束只有原commit能写admission，投影沿用真实docId。保护与身份变化返回预期原因，不写候选。
- I-4：前后attempt数量、request_count、原response_json/SHA、orcidCompleted、search、owner/batch/activatedAt不变；新增HTTP=0；准入数依据admission_revision，不按sourceStats.promoted。
- I-5：同manifest重复apply排队数0；部分失败后仅未改变条目可续；after写失败不丢before，inspect可恢复结论；batch不因为自己新排队而拒绝自身剩余项。
- I-6：采样根目录无新非schema目录；新run有SAVED样本，正文hash/size失败0，7天/512MiB/分类配额未改；原文快照在独立目录，损坏副本不能继续apply。
- I-7：固定名单三组同输入哈希/时钟；差异逐item可解释；线上commit/投影与规则建议分列；新run不混算；真实正例通过必须人工核对，负例回归误准入0。若实际仍0，必须报告剩余原因，不算“优化成功”。
- 命令：JDK11独立worktree `mvn -Dtest=DiscoveryVerificationReplayToolTest,DiscoveryVerificationRepositoryIT,DiscoveryVerificationFlowIT,DiscoveryVerificationReportingIT,DiscoveryVerificationAdmissionGateTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test`，Docker MySQL必需；`bash -n scripts/expert_discovery/replay_verification.sh`。结合01再执行完整项目规定测试，不以测试跳过充当通过。
- 技术检查点：先用两版本真实编译类做evaluate最小试验，确认Kotlin/Jackson/JDK11及入口兼容；主程序必须用稳定的Parser.parse/Rules.evaluate接口按文档调用，不能在旧classpath执行新版helper或requeue入口。该试验失败先明确修订工具启动方式，不复制旧规则或扩大业务代码白名单。
- 发布门槛：先冻结/校验基线与原部署类，01+02测试通过，再按实际授权部署；新代码运行但旧库原状态尚未重算时UI仍展示原结果，不强制刷新篡改历史。回退二进制前评估新规则产生的APOLLO_UNCLEAR会被旧Gate拒绝的兼容影响；禁止直接还原before覆盖已提交审核/准入，必须走现有人工审核/撤销流程。

## 人工验收清单

### A-1: 冻结可重放且不写业务
- 前置条件：服务器有任务26839；使用附件ID名单；准备JDK11、受限服务器环境变量、旧/新类classpath及各自SHA。
- 操作步骤：1. 运行 `replay_verification.sh freeze --execution-id 26839 --item-ids <名单.json> --output /opt/talent/data/discovery-verification-replays`。2. 查看manifest与DB前后校验输出。3. 用同before运行evaluate两次。
- 预期结果：freeze业务写入0；manifest可复核；两次evaluate输出除单独执行时间外一致，decision时钟相同；89个ID的缺失/变化分别列出，不冒充旧输入摘要。
- 覆盖：O-2、I-1/I-7、X-1。

### A-2: 保留未明确准入及独立统计
- 前置条件：隔离历史PENDING正例，绑定ORCID、SELF当前任职、完整Apollo唯一匹配、资格UNKNOWN，无硬阻断；before已冻结。
- 操作步骤：1. apply传manifest及其SHA。2. 等原worker完成。3. inspect并打开执行记录。
- 预期结果：APOLLO_UNCLEAR；自动准入1/其中Apollo1/未明确1；新增HTTP0；候选投影PROJECTED或ALREADY_PRESENT；原run档案数和batchNumber不变。
- 覆盖：O-1、N-1/N-4、I-2/I-3/I-4、X-2/X-3。

### A-3: 重复与并发保护
- 前置条件：同A-2；另有freeze后人工HOLD或revision改变样本；另有正在RUNNING的任务样本。
- 操作步骤：1. 重复apply同manifest。2. apply人工变化样本。3. apply运行中样本。4. 查看准入/请求数。
- 预期结果：重复新增排队0；变更样本STALE_OR_ALREADY_APPLIED或明确PROTECTED/SKIPPED；运行中RUN_NOT_SETTLED；三类新增准入和HTTP均0，人工HOLD保持。
- 覆盖：N-3/N-4、I-2/I-3/I-5、X-1/X-2。

### A-4: 保留两类留档
- 前置条件：原样本目录已有有效包，02报告目录与它不同；新run尚未到配额。
- 操作步骤：1. 核查原sample.json和SAVED正文hash。2. 重算后比对原ORCID/Apollo文档与attempt。3. 启动新run并查看留档。
- 预期结果：原证据逐值未被改写；原样本只受既有到期/配额回收；新run产生SAVED包且hash/size失败0；`samples.enabled=true`，7天/512MiB/失败每类20/成功10设置不变。
- 覆盖：O-2、N-2、I-4/I-6、X-4。

### A-5: 实际效果与冲突可见
- 前置条件：固定名单完成三组离线对照；目标运行已稳定，按授权完成小批重算；6个历史NAME_CONFLICT纳入报告。
- 操作步骤：1. 打开逐item差异。2. 对新增匹配逐条核对ORCID归属、当前任职、Apollo姓名/机构、唯一性。3. 对照实际commit/投影。4. 单独查看新run完整查询报告。
- 预期结果：每个新增准入有可核对证据；规则建议与实际入库差额有明确保护/同步原因；6条冲突不被静默消除；已人工核对样本的误匹配数明确，未标注的不写准确率；实际0提升也如实报告。
- 覆盖：O-1/O-2、N-3、I-3/I-7、X-2/X-5。

### A-6: 只读页面与投影重试
- 前置条件：隔离候选投影失败样本已持久准入；run其他写入停止。
- 操作步骤：1. 刷新日志及详情。2. 恢复ES后等待原投影重试。3. inspect同item。
- 预期结果：刷新不改revision/attempt；投影失败不再次计准入；恢复后投影完成且HTTP次数不增，原准入revision不变；页面无新增按钮/布局变更。
- 覆盖：N-2/N-4、I-3/I-4/I-5、X-2/X-3。
