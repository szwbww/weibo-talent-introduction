# 03 — 整个执行任务的持久化重新解析与对照

状态：待执行；2026-11-01。仅计划，不表示已实施或发布。

## 需求描述

提供选定 sourceExecutionId 全部留存论文的后台对照任务；关闭页面可恢复，逐篇记录来源、差异、失败原因。本子计划独立发布时只开放 AUDIT，ENFORCE 明确返回 APPLY_NOT_AVAILABLE，等04接通后再开放。

保持不变：①单篇解析对照、下载、删除与保护语义；②原文/首次结果/专家/准入零修改；③不重跑深度发现采集、不重置游标；④页面关闭不是取消。

不做：通用工作流引擎、独立消息队列、复制原文、新增全库索引。两个子系统：论文重处理任务、任务活动登记。

## 关键不变量

### Invariant I-1: 固定整批
- Rule: sourceExecutionId正整数且源采集任务已终态；固定该执行的 archive recordId、metadata hash 与所有当前 SAVED/AVAILABLE attempt/hash，按recordId keyset分页。忽略当前页/标题/保护筛选/勾选；保护论文允许读，保护仅限制删除。创建时记录上界maxRecordId，固定完成后不扩大范围。
- Applies to: preview/create/freeze/worker
- Violation consequence: 只处理一页或把后来的文件混入固定批次。
- 来源: original

### Invariant I-2: 状态与幂等
- Rule: 新增run.mode仅AUDIT/ENFORCE，state仅PREPARING/RUNNING/CANCELLING/COMPLETED/COMPLETED_WITH_ERRORS/FAILED/INTERRUPTED/CANCELLED；phase仅FREEZE/READ/PARSE/BIND/APPLY/REVIEW/SAVE。UNIQUE(request_key)校验相同payload；active_key单例常量保护本功能仅一个活动任务，不锁其他深度发现任务。不同请求重复启动返回当前runId，不创建第二个任务。
- Applies to: 新run表、controller、recovery
- Violation consequence: 重复解析/补录或UI丢运行任务。
- 来源: original

### Invariant I-3: 两类工作项
- Rule: item.kind=PAPER/TARGET；PAPER键=recordId，TARGET键=规范邮箱指纹，跨论文同邮箱冲突独立标记；不是按姓名合并专家。item.state仅PENDING/RUNNING/COMPLETE/ERROR/SKIPPED/CANCELLED；leaseToken/generation/until+run状态控制写入，旧token影响0行。input_json不可变，result_json.schemaVersion固定1，含逐attempt/hash结果和6步trace，成功失败都持久化。PAPER的COMPLETE表示读取/解析结束，后续补档/核验由TARGET及其引用结果决定；父任务不能因此提前COMPLETED。trace.stepNo=1..6，trace.state仅NOT_STARTED/RUNNING/SUCCESS/INSUFFICIENT/SKIPPED/ERROR/CANCELLED；任何未知schema/state拒绝且保留原始行供诊断。
- Applies to: 新item表、worker、统计
- Violation consequence: 计数重复、崩溃丢档、冲突先写后发现。
- 来源: original

### Invariant I-4: 计数单位
- Rule: 总论文数按record去重，不按attempt/文件hash；总任务显示处理论文/成功/失败/取消。档案统计按目标docId/未建档邮箱键另列；尚无personId不得称去重人物。COMPARE完成仅表示解析结束，actualNew/actualUpdated/actualAdmitted均0。
- Applies to: summary/list/steps
- Violation consequence: 文件数、人次、准入混算。
- 来源: original

### Invariant I-5: 资源与副作用
- Rule: 读取只能用服务端路径校验+lease，显式hash必须与冻结清单相同，读后立即释放。默认单个解析工作项并发；不长期持SQL/全局archive锁解析。只写新审计表及任务记录，AUDIT不调用消费/repair/Apollo。完成才释放active_key；取消保存已完成结果不回滚。
- Applies to: archive lease、scheduler、cancel
- Violation consequence: 文件被并发删、OOM、比较触发业务。
- 来源: K-paper-facts-need-owner-time-and-readonly-compute

## 现状审计

完整源码回执：[paper-reprocess-evidence/README.md](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/README.md)、[paper-reprocess-evidence/paths.json](/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-11-01/paper-reprocess-evidence/paths.json)。共享存储全部生产读写路径及交互编号见主计划「现状审计」；本子计划只改变下述接缝，其他匹配路径为回归边界。当前本地基线 `b758eb0`，工作区存在先前改动；不得当作已核实的生产部署版本。
- V159：record unique(execution_id,source,paper_key)，attempt指向可去重复用blob；同一文件被多记录引用，不能按物理hash当论文数（X-1/X-5）。
- `DiscoveryPaperArchiveService.kt:60–79` 的 analysisLease 会限定 first.attemptIds；不能拿它默认为“整批全部留存attempt”。新增显式选择重载，不改变单篇旧默认行为。
- `DiscoveryPaperArchiveRepository.kt:61` records()全表读取；新功能必须使用新repository中的有界execution/id查询，禁止套用此方法。
- `TaskExecutionRepository.kt:133,157` 提供定向heartbeatExecution/finishOwned；活动卡片 `TaskActivityController.kt:66–80` 只接受同executionId进度（X-6）。
- 新两表无现有读者；唯一写读所有权均归新repository，task catalog+前端/API为消费者。共享archive、task表不加列。执行前发现V172已由`V172__transfer_existing_full_access_pending.sql`占用；本功能使用首次空闲的V173，不改已应用迁移。

## 实现方案

1. **API与权限（I-1/I-2）**：新增 `/api/expert-discovery/paper-reprocess`：GET `/preview?sourceExecutionId=...` 返回只读范围数量/当前活动任务；POST `/runs` 接受 `{requestKey,sourceExecutionId,mode,expectedScopeHash}`，返回202和runId；GET `/runs?sourceExecutionId=&active=&requestKey=&page=&size=`、GET `/runs/{id}`、GET `/runs/{id}/items?kind=&page=&size=`、GET `/runs/{id}/items/{itemId}`；POST `/runs/{id}/cancel` 和 `/runs/{id}/retry`，需revision。沿用已有认证拦截器，不新增匿名或公开原文地址；item必须属于run。size1..100默认20，页码非负；缺源404、并发版本409、不可用503，前端中文原因+code。
2. **新增schema（I-2/I-3）**：V173只建本功能独占两表，不改共享表。
   - run：execution_id BIGINT PK；source_execution_id BIGINT索引；request_key CHAR(36) UNIQUE；request_hash CHAR(64)；active_key VARCHAR(32) NULL UNIQUE（活动值固定PAPER_REPROCESS）；mode VARCHAR(16)、state VARCHAR(32)、phase VARCHAR(16)；scope_json MEDIUMTEXT（upperRecordId、cursor、frozen完成标志、sourcesHash、parserVersions、codeVersion、evaluationAt、Apollo冻结设置）；revision BIGINT默认0；lease_token VARCHAR(64) NULL、lease_until DATETIME(3) NULL、generation BIGINT默认0；cancel_requested bool默认false；created/updated/finished时间。JSON不保存key/password。
   - item：id BIGINT自增PK；execution_id BIGINT；kind VARCHAR(8)；item_key VARCHAR(128)；state VARCHAR(16)；input_json MEDIUMTEXT；result_json MEDIUMTEXT NULL；lease_token/lease_until/generation/revision同上；created/updated/completed时间；UNIQUE(execution_id,kind,item_key)，索引(execution_id,kind,state,id)、(state,lease_until,id)。PAPER输入存record与body清单、metadata、原first摘要；TARGET输入存唯一邮箱候选及paper refs，结果保存before/after/receipt、verification item/run引用。无跨业务表级联删除；状态合法性应用层强制校验；DB CHECK在支持时增加，实际MySQL版本与约束是否生效须由迁移测试确认，不能依赖MySQL 5.7会忽略的CHECK。mode/state/phase/kind取值按I-2/I-3；时间按UTC DATETIME(3)；JSON每项最大4MiB，超限显式RESULT_TOO_LARGE而非截断有效证据；输入超限INPUT_TOO_LARGE。request_key不含邮箱，target item_key使用SHA-256。body字节不入SQL；trace保留有限原句与记录定位，不复制全文。
3. **持久化范围（I-1/I-2）**：preview只对sourceExecutionId、maxRecordId、maxAttemptId、记录revision合计/数量及源任务终态生成范围token（expectedScopeHash），不在HTTP请求里读所有原文/metadata；create短事务重核这些水位。后台固定完成后另存完整manifestHash；二者名称和用途不得混用；大清单后台keyset每100条固定并落PAPER项，记录cursor与进度“已固定N篇”。PREPARING崩溃只接续cursor；冻结结束再核对同一范围token，期间被删除/修改导致token变化则FAILED/SCOPE_CHANGED，禁止带部分变动清单进入补录。冻结完成前不解析、不补录。发生原文删除/变化标SOURCE_CHANGED/SOURCE_MISSING，不悄悄换同DOI另一批原文。
4. **读原文（I-3/I-5）**：archive新增显式attemptId/hash列表的lease，只开放服务层调用。校验路径与所有hash后打开，保持analysisReaders计数；不向客户端暴露文件路径。冻结后新增attempt忽略；所有指定body逐个解析，必须包含HTML/XML/PDF/TEXT现有格式，不重复下载。first锚点仅作旧结果baseline，不限制新重处理范围。同步修改 `PaperVerificationEvidenceService.collect`：按当前固定source refs给archive请求显式attempt lease，而非默认first-only lease；否则新任务选中的后续attempt在自动重审时仍可能被排除。原单篇AnalysisService继续调用旧默认lease。
5. **解析/跨文档归并（I-3/I-4）**：复用01，每篇完整存新绑定、首次绑定、gain/loss、覆盖范围、冲突。解析阶段全部结束后才能建立TARGET的唯一候选集；跨论文同邮箱不同姓名/作者ID标CONFLICT，不选择某个覆盖另一个。在ServiceTest中用真实PaperVerificationEvidenceService验证非first的明确attempt可用于后续核验；不同hash/record仍拒绝。没有基线为BASELINE_UNAVAILABLE，只显示当前解析，不虚构“新增”。AUDIT只给wouldCreate/wouldRepair候选与理由，不运行资格外呼，实际业务计数全0。
6. **任务生命周期（I-2/I-5）**：使用已有task repository的owner+heartbeat；Service内部专用ThreadPoolExecutor(1线程、ArrayBlockingQueue容量1)执行解析，scheduler只做快速claim/投递；拒绝投递必须释放或延期当前lease。不得占用共享定时线程或discoveryRecheckPrepareExecutor来跑PDF；@PreDestroy停止claim并关闭线程池，未完成由租约恢复。5秒轮询、heartbeat至多15秒一次，lease60秒且每次提交校验generation。解析page回调检查取消/预算；不要catch Throwable继续。超时或进程退出置INTERRUPTED并由显式retry恢复同源/同版本；版本变更不可接续旧job，返回VERSION_CHANGED让新建一轮。心跳不依赖页面。task_activity实时数据不匹配时可为空，但详情必须从新表显示进度。
   - 按页取消/预算检查仅经可选checkpoint从PaperBodyAnalyzer传递至资格提取与PDF联系提取；回调默认无操作，保留旧调用形态、解析规则、缓存/身份/解析版本与普通异常处理。只对取消/预算的明确异常窄传播，禁止通用catch吞掉它们。原授权ServiceTest覆盖取消/budget实际生效及旧默认调用不变，不新增测试文件。
7. **取消/重试（I-2/I-3）**：cancel先CAS CANCELLING，禁止再claim；正在读取的项安全退出，未开始项CANCELLED；已有COMPLETE保留。retry仅恢复ERROR/因中断未完成项，成功项不重跑；用户取消后的续跑须显式retry同run并重获active_key。纯对照阶段同样不创建任何新专家。
8. **统计（I-4）**：用item落库状态聚合，不靠前端累加。阶段前没有total显示“统计中”，不显示虚假0%；错误行有有限reasonCode和中文解释。任务表状态映射：PREPARING/RUNNING→RUNNING，CANCELLING→CANCELLING，COMPLETED→SUCCESS，COMPLETED_WITH_ERRORS/FAILED→FAILED，INTERRUPTED→INTERRUPTED，CANCELLED→CANCELLED；result_summary保留更细状态与成功数。与任务活动catalog使用 `DISCOVERY_PAPER_REPROCESS / 论文重新解析与补录 / 已处理论文/失败`。
9. **测试（I-1–I-5）**：范围121篇、每页20；同hash跨3记录仍3篇；2个attempt同hash仅解码一次但两条引用均保留；多任务双击/两tab、断网后重送requestKey、lease过期、取消与删除竞争均使用真SQL或接口fixture验证。

## 变更文件清单

| 文件（仓库根目录相对路径） | 改动 |
|---|---|
| `src/main/resources/db/migration/V173__create_discovery_paper_reprocess.sql` | 新增本功能独占run/item两表；V172已被既有迁移占用 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPaperReprocess.kt` | 请求/返回DTO、状态、JSON证据契约 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepository.kt` | 任务幂等/固定范围/分页/租约/CAS/统计 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessService.kt` | 后台固定范围、纯解析、归并与恢复 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPaperReprocessController.kt` | 认证API；短请求不执行整批解析 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt` | 显式attempt集合的只读lease，保留原analysisLease默认行为 |
| `src/main/kotlin/com/weibo/talentintroduction/task/domain/TaskTypeCatalog.kt` | 新增任务类型中文标签与进度能力 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperReprocessServiceTest.kt` | 只读、租约、取消、范围、冲突及MockMvc权限/参数测试 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PaperVerificationEvidenceService.kt` | collect按固定refs读取显式attempt；保持默认单篇分析行为 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperReprocessRepositoryIntegrationTest.kt` | 真实SQL约束/重启/分批范围固定 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperBodyAnalyzer.kt` | 可选checkpoint透传与取消/预算异常窄传播，默认行为不变 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt` | PDF逐页调用checkpoint，不吞取消/预算异常 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfEmailExtractor.kt` | 联系解析beforePage透传可选checkpoint，保留旧默认和规则 |
| `src/test/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionSummaryExtractorTest.kt` | 同步受新catalog类型直接影响的测试；删除过时固定条数/拷贝白名单断言，不重钉新条数，保留真实摘要与进度行为覆盖 |

共 14 个文件。新增解析链接缝仅为I-5按页取消/预算执行，task测试仅同步直接受影响契约，不扩大业务目标；未列文件禁止扩改，新增问题先记录证据并修订范围。

## 验收标准

- I-1：121记录跨7页全部固定；所有额外筛选不改变scope；非终态source拒绝SOURCE_RUNNING。
- I-2/I-3：同requestKey相同payload只1任务，异payload409；不同key并发只1active_key；新SQL索引/约束在真实MySQL验证。原文列表翻页不提交任务。
- I-4：同hash3论文不算1；COMPARE实际档案/准入计数全0；paper/target两类单独统计。
- I-5：取消、重启、删除竞争没有越权写；旧token不能覆盖新结果；所有AUDIT外部客户端调用0。
- X-1/X-5/X-6：归档读→新审计存储→API/任务活动可读；同页旧解析对照和删除仍走旧路径。03的repository集成测试必须实际跑新增迁移与状态约束；06再覆盖项目Flyway空库/升级门，不能仅凭编译通过。
- 必跑命令补充：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperReprocessServiceTest,DiscoveryPaperReprocessRepositoryIntegrationTest,TaskExecutionSummaryExtractorTest -DmysqlIt=true`；不替代真实API/归档/SQL smoke。

## 人工验收清单

### A-1: 跨页与多份原文
- 前置条件：测试任务含121论文；3篇共享一blob，一篇2个不同有效attempt；页面每页20。
- 操作步骤：1. 选择该任务；2. 输入只命中1篇的标题；3. 创建AUDIT。
- 预期结果：范围仍121篇；最终每篇有记录，双attempt均可追溯；实际新建/补齐/准入均0。
- 覆盖：I-1/I-3/I-4，X-1/X-5，保持不变②③

### A-2: 关闭恢复与重复点击
- 前置条件：上述任务运行中，另开一tab。
- 操作步骤：1. 重复点击启动；2. 关闭两tab；3. 重新进入同批。
- 预期结果：只1个runId；恢复该run的已落库进度；页面关闭不取消；活动显示“论文重新解析与补录”。
- 覆盖：I-2/I-3/I-5，X-6，保持不变④

### A-3: 取消/重试/删除竞争
- 前置条件：测试任务2篇已完成、3篇未开始；其中1篇保护。
- 操作步骤：1. 取消；2. 查看2篇结果；3. 显式重试；4. 单篇执行旧下载/解析/删除预览。
- 预期结果：已完成2篇结果保留；仅余项续跑；受保护原文可读取但删除预览跳过；原文first快照不变。
- 覆盖：I-2/I-3/I-5，X-5，保持不变①②


人工验收开始时才从此处导出同前缀 `-acceptance.md`；本次不生成衍生勾选文件。

## 修正记录
- 2026-11-02：按用户“后续授权都继续”的在目标内授权，将迁移文件从V172改为V173；PaperBatchImplementer03的完整`glob src/main/resources/db/migration/V*.sql`回执证明最高V172，且现有`V172__transfer_existing_full_access_pending.sql`不可修改。仅新迁移编号变化，表结构、文件数及验收不变；决定A3，见`docs/plans/fast/paper-reprocess-master/ledger.md`。
- 2026-11-02：按用户后续必要授权，追加三个已证实缺失的checkpoint传递接缝并将文件上限改为13；PaperBatchImplementer03E2的专用grep回执与签名/catch证据见fast-p执行日志epoch2，原白名单无法实现第6步按页取消。只允许默认兼容、窄异常传播；解析规则/版本、保护与验收不变。决定A4/A5见ledger。
- 2026-11-02：按用户后续必要授权，新增TaskExecutionSummaryExtractorTest作为直接受catalog变更影响的第14文件，并加入必跑命令；执行者grep/read回执证明固定集合/数量断言必红。仅退役不应重钉的incidental断言、保留真实摘要/进度行为；见ledger A6/A7和epoch3执行日志。
