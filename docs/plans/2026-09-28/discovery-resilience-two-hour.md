# 深度发现：有限延迟续跑与每两小时调度修复计划

- 状态：待审阅；本文件不表示已实施、已上线或网络故障已根治。
- 日期：2026-09-28；代码基线：`f98e27c7538d091bfcdecfcb6ffc10360a35ba04`。
- 工作树：`/Users/lukai/IdeaProjects/weibo-talent-introduction`，当前分支 `main`。执行前复核 HEAD、工作树及本文件哈希，保留其他任务的未提交修改。
- 范围：2 个子系统，8 个产品/测试文件；不新建数据库表，不引入队列，不启用现有 pipeline，不改前端文件。
- 需求依据：本会话已经讨论的“OpenAlex 连续短重试失败后延迟续跑”和“每两小时执行一次”；用户要求明确代码证据、不要过度设计。
- 取消语义：本稿采用已有“取消本轮，下一定时点仍可触发”。已发出选项询问，未收到改变既有行为的选择，因此采用保持现状的最小方案；这不是用户已确认永久暂停方案。若选择“取消同时永久暂停”，须先修订本稿，不得在执行时偷偷扩展。
- 本轮只生成计划和证据。提交、上线、改服务器配置与真实采集须在执行阶段按用户当时授权处理；此前暂停发布不由本文件自动撤销。

## 需求描述

### 可观察结果

O-1：OpenAlex 同一页发生可重试的连接中断/超时/既定 5xx，三次短尝试仍失败时，不马上判本来源最终失败；先保留页入口、处理其他来源，再按 30 秒、2 分钟、5 分钟的有限退避恢复。页面与进度日志能看见原因、轮次、计划重试时间。持续失败必须有上限，不能无穷重试。

O-2：旧同步发现模式每两小时触发一次，北京时间 00:00、02:00、…、22:00。上一轮未结束时跳过，不并发、不补排被跳过的触发。新一轮读取原查询检查点；一日内多轮共享原有 OpenAlex 预算，不增加免费额度或论文单轮上限。

### 必须保持

- P-1：姓名—邮箱身份核验、学科准入、RAW→CANDIDATE 晋升、异步补全与现有晋升读投影全部保持；不为提高产量放宽条件。
- P-2：只有完整消费页才能推进游标；RAW 写失败、入队失败、去重失败、半页、取消、预算结束均不得跳页；旧查询与新查询不能混用检查点。
- P-3：继续使用共享 credits 账本、补全保留额度、429 冷却、UNKNOWN 预占保护、证书验证与禁止跨站认证重定向。
- P-4：手动/关键词/定时入口共享当前进程互斥；“取消任务”打断本轮，包括重试等待。永久停定时用现有 cron `-` 配置并重启生效；不把取消伪装成永久暂停。
- P-5：pipeline-enabled=true 的既有持久化暂停、恢复、tick 行为不变；本计划不切换模式。
- P-6：失败与部分成功如实记账；不要把等待、取消、限额结束显示为“已全部穷尽”。

### 不在范围

- ARXIV `RAW_WRITE_INCOMPLETE`：另案定位写失败，本计划不重试该类消费错误。
- TLS 根因尚未定位到客户端/上游/网络设备；不预设“强制 TLS1.2”“换 HTTP 库”“关 keep-alive”“切代理”能修好，不关闭证书校验。
- 不创建新的通用调度器、持久化重试队列、数据库锁、自动化机器人或自动开通付费；不把批量发邮件配置当作深度发现配置。
- 不增加生产研发类别，不删历史专家，不重算历史任务 JSON，不修改邮件发送或其他定时任务。
- 不承诺每次处理 10000 篇或用尽每天 credits；页大小、单轮论文上限、账号周期额度是不同概念。

## 关键不变量

### Invariant I-1: 只恢复确定可重试的搜索错误
- Rule：复用当前 `isRetryableOpenAlexFailure` 与 500/502/503/504 白名单，仅同步 OpenAlex 元数据搜索可进入新流程。每组保留最多 3 次请求；每来源每轮发现额外恢复组最多 3 组，间隔依次为 30s、120s、300s。恢复组额度不因成功一页而重置。若同一页一直失败，最多 12 次实际搜索尝试，然后只记一次 `SEARCH_FAILED`。
- Applies to：`ExpertDiscoveryService.discoverFromSource` 两条异常分支、`discover` 恢复循环。
- Violation consequence：把证书/权限/解析错误变成请求风暴，或在适配器与协调者双层重试。
- 来源：原始需求；K-page-commit-must-cover-consumer-failures。

### Invariant I-2: 页入口与查询归属保持
- Rule：搜索失败、待重试、取消和时间预算结束都保留当前页入口与 ACTIVE；仅确认完整消费后写 next cursor。`sourceKey` 编码不变，绝不靠累计论文数推测游标。恢复使用本轮内存的确切入口，不依赖再查库成功；跨重启使用已有持久化检查点。
- Applies to：同步论文页、ORCID 保持回归、`persistSourceCheckpoint`；恢复组重新进入同一论文源。
- Violation consequence：跳页、重新扫第一页或串查询。
- 来源：K-search-error-must-not-clear-cursor；K-page-commit-must-cover-consumer-failures。

### Invariant I-3: 额度、统计、deadline 跨恢复组累积
- Rule：恢复不创建新 `DiscoveryStats`、executionId 或 deadline，不重新分配完整来源份额。初次 `runBudget` 固定；本源已处理数从剩余额度扣除，同时满足全局剩余、作者上限和原 deadline。`papers_processed_total` 每次只加尚未持久化的本次新增量；不能因再次进入函数把前 500 篇再加一次。批次号继续 `stats.nextBatchSeq()`，elapsedMs 累加真实来源运行片段，不覆盖首段。
- Applies to：`discover`、`discoverFromSource` 的局部计数/检查点闭包、所有最终汇总。
- Violation consequence：重复计费口径、超过单轮上限、统计增长而没有新增论文。
- 来源：K-discovery-budget-backpressure；原始。

### Invariant I-4: 等待是运行中状态，不是终态错误
- Rule：只新增来源停止原因字符串 `RETRY_WAIT`，不新增 task_execution 状态。待重试时 task 为 RUNNING、source.pendingWork=true、source.exhausted=false；暂时失败不加 `sourceFailureCount`、`failureReasons.SEARCH_FAILED`。实际最终用尽尝试才加一次；恢复成功按实际停止原因覆盖来源状态。取消优先于时间、额度与重试。
- Applies to：`SourceRunOutcome`、SourceStats、`DiscoveryTerminalStatus` 的输入与进度/返回结果。
- Violation consequence：恢复成功仍被计算成来源失败，或“等重试”被当已完成。
- 来源：原始；现有 DiscoveryResult.kt 终态合同优先，不照搬其他任务“一律 FAILED”。

### Invariant I-5: 重试可见但不成为第二份恢复依据
- Rule：SourceStats 只新增一个可空对象字段 `retry`，内容 `round: Int`、`maxRounds: Int`、`nextRetryAt: String?`、`reason: String`；时间用 ISO-8601 UTC，显示文案转 Asia/Shanghai。它随 details_json/result_summary 序列化，仅作观察；不用于启动恢复、不写入专家 ES。round 为已安排的延迟恢复组序号 1..3，nextRetryAt 非空表示仍待执行；真正开始、取消、超时或结束均清空 nextRetryAt，保留轮次/原因用于审计。
- Applies to：SourceStats 声明、buildBySourceDetails、buildProgressDetails、buildSummaryText、恢复入口/出口。
- Violation consequence：跨执行误恢复、页面一直显示已过期等待、异步晋升刷新抹掉重试提示。
- 来源：K-progress-log-batchonly-two-readers；原始。

### Invariant I-6: 两小时只改变触发频率
- Rule：默认 cron 改为 `0 0 */2 * * ?`，注解明确 `zone = "Asia/Shanghai"`；删除旧路径“本日已执行”检查，保留统一 `tryStartWithToken("EXPERT_DISCOVERY", ...)`。每个 cron 回调最多一次 discover；抢不到运行槽时无新 task_execution、不发请求、不排队补跑。不得删除通用 countScheduledSince/countActiveSince API 或改变它对其他调用者的语义。
- Applies to：ExpertDiscoveryScheduler、application.yml、SchedulerTest。
- Violation consequence：只改 cron 仍一天一次，或并发耗费额度。
- 来源：原始、直接代码审计。

### Invariant I-7: 取消、停定时、pipeline 暂停分别处理
- Rule：同步“取消”仍仅取消该 execution，等待检查粒度不超过 100ms；不得因仍有重试条目发出下一请求。cron `-` 不注册定时触发，手动入口仍可用。pipeline 的持久化 PAUSED 不受新 cron 复活；不开启 pipeline。重启不读旧进度 JSON 重新执行等待动作。
- Applies to：等待循环、Scheduler 的模式分支、既有控制器回归。
- Violation consequence：用户取消后继续请求、关掉定时后仍自动跑、不同模式语义混淆。
- 来源：K-clearExecutionContext-status-leak；原始。取消语义待用户选项最终确认，见文件头。

### Invariant I-8: 预算与安全闸门不绕过
- Rule：每次实际搜索仍经 `OpenAlexDataSource.getJson→OpenAlexRequestPolicy.reserve`。429、任何 `OpenAlexBudgetDeferredException` 不进入网络延迟恢复列表；直接保持 BUDGET_DEFERRED，由未来正常触发重新检查账本。发现额度不够不阻止 Crossref/其他不使用该额度的来源继续；不得清零预算、补全预留或 UNKNOWN。不得关闭证书验证、发送明文 key 或自动跟随携凭证跨站重定向。
- Applies to：新短重试/延迟重试分支、Scheduler 多次运行。
- Violation consequence：实际超额、突破冷却、凭证泄漏。
- 来源：K-discovery-budget-backpressure（旧内存账本描述已更正）；现有 V132 与 RequestPolicy。

## 现状审计

所有路径相对于工作树根；行号锚定文件头 SHA，执行前按符号重新核对。grep 收据与 SHA：同目录 `discovery-resilience-evidence/`。

### E-1：已证实线上现象与未知边界

- `docs/audits/2026-09-28-discovery-20504/evidence.json`：20504 于 02:00:00–02:33:28 运行，OpenAlex 500/10000，新增 RAW 10；02:13:21.227、25.538、30.841 同页三次 `SSLHandshakeException: Remote host terminated the handshake`，最终 SEARCH_FAILED。并非 RATE_LIMIT。
- 9 月 27 日执行 20445 同类错误在 700 篇后发生，记录在 `/tmp/discovery-700-check/result.json`；本计划验收不依赖临时文件，9 月 28 日仓内审计为持久证据。
- 线上 18 个修复 class 与 437349e 补丁一致；非“新版本未部署”。
- `production-config.json`：Java 11.0.23、JVM Asia/Shanghai、主 WAR 默认每日 02:00、pipeline false；可见进程环境没有 EXPERT_DISCOVERY_CRON 覆盖，profile=simulator。并未穷举所有外部 PropertySource，因此上线仍必须核对**实际生效值**，不能把打包默认等同全部线上配置。
- 尚无证据证明具体 TLS 协商、DNS、复用连接或网络设备是哪一方的根因；本计划的确定修复对象是短暂故障恢复不足与每日一次闸门。TLS 对照诊断设研究检查点 R-0，不凭猜测改 HTTP 客户端。

### E-2：同步搜索与已有另一套恢复流程

- `ExpertDiscoveryService.kt:385–403`：论文来源 for 循环一次遍历，之后 ORCID；没有延迟回访列表。
- 同文件 `:680–742`：额度/429直接延期；可重试搜索最多 3 次，之后记终止错误并 break。
- `:2055–2088`：等待约 1s、2s加0..200ms抖动；白名单明确排除 CertificateException、SSLPeerUnverifiedException、SSLProtocolException 与 JSON/转换异常，只接受指定握手中断及相应 I/O 错误。
- `OpenAlexDataSource.kt:49–89`：异常抛给上层；每次请求单独申请 permit，异常记 UNKNOWN。不能在该层另叠一轮重试。
- `RestTemplateConfig.kt:115–134,176–181`：专用 openAlexRestTemplate，配置化连接/读取超时、NoRedirectRequestFactory、认证与流量拦截器；本次不预设替换它。
- `DiscoveryPipelineService.kt:786,853` 已有 recordStreamError + SOURCE_ERROR_BACKOFF 的延迟恢复；当前线上运行的是旧同步入口。不能因此宣称整个系统没有重试，也不能为了本需求开启大范围 pipeline 切换。
- `ExpertDiscoveryService.kt:594–635` 的 persistedPapers=0、sourcePapersProcessed=0 是**每次函数调用**初始化；同轮重复调用若不改基线会累加旧论文或重新给满配额。`SourceStats.runBudget/elapsedMs` 也会被覆盖，必须处理 I-3。

### E-3：discovery_source_cursor

- Schema：`V32__create_discovery_source_cursor.sql`；source_name VARCHAR(50) 唯一，cursor_value TEXT可空，papers_processed_total BIGINT默认0，last_run_at可空，updated_at非空；无自动过期和重试字段。
- 全部生产写路径：唯一 `ExpertDiscoveryService.persistSourceCheckpoint:158–194→cursorRepository.save`；论文闭包 `:628` 与 ORCID闭包 `:956` 在页提交、提前停止、函数退出调用。仓库 scripts/ops 未检出其他 SQL 写入；migration 创建表，不改历史数据。
- 全部生产读路径：`loadSourceCheckpoint:133` 供同步论文/ORCID；`findCompatibleLegacyCursor` 附近 `:1523–1535` 供 pipeline 旧游标兼容读取；repository.findBySourceName；Codec encode/decode 只在内存转换。
- 编码：`DiscoveryCheckpointCodec.kt:45–132`，来源+实际查询+版本键，cursor不参与hash，ACTIVE与EXHAUSTED明确区分。
- Interaction X-1：新增延迟恢复再进入论文循环 → 既有检查点落库 → 下一定时/手动任务读取同一key；需保持delta与入口正确。(来源: K-search-error-must-not-clear-cursor)
- Interaction X-2：搜索恢复不能误恢复消费错误；RAW/补全队列/去重失败都让页未完成并保持入口。既有 `:827–898` 与 ORCID `:1136–1165` 保持。(来源: K-page-commit-must-cover-consumer-failures)

### E-4：task_execution、task_progress_log、进程运行槽

- Schema：V4 的 execution含status、trigger_type、request_payload、result_summary、开始结束时间与计数；V73加batch_config_id，V100索引，V137加owner_token/heartbeat_at/人工中断信息。V22的progress含可空executionId、batchNumber、message、details_json；V35加batch_reject_reasons_json，V102加created_at索引。JSON是TEXT，没有 schema强制；本次不加SQL列。
- execution全部写入口：TaskExecutionService.runAndRecord/runAndRecordWithResult插入；finishOwned CAS终态；updateProgressCounts更新批量计数；reconcileInterruptedExecutions经heartbeatOwned/interruptExpired；interruptManually；TaskAuditRetentionService删除旧任务。新流程仍只经上述服务，不直写SQL。
- execution全部repository读入口：TaskExecutionService的列表/批量配置/计数/详情方法，TaskExecutionController详情与类型统计，TaskProgressController运行列表，TaskActivityController活动分页，DiscoveryPromotionProgressService按id刷新观察值。完整调用收据 `store-users.txt`。
- progress全部写入口：TaskProgressStore.persistProgressLog.save；bindExecutionId负token重绑；TaskAuditRetentionService.deleteOlderThan。TaskProgressStore的update、tryStart/tryStartWithToken、requestCancel触发日志保存。新等待消息仍走update(expectedExecutionId)，不要异步直接改持久化JSON。
- progress全部读入口：TaskProgressStore.restoreFromLog、TaskProgressController进度/日志/执行列表、TaskExecutionSummaryExtractor回退、BatchSendConfigController日志、DiscoveryPromotionProgressService读最新执行归属。`batchOnly=true`按batchNumber取末条，不能拿它当完整重试事件记录。
- cache：ConcurrentHashMap按taskType存当前TaskProgress；取消标记按type:id。update/tryStart/setCurrentExecutionId/bind/clearExecutionContext/requestCancel/clear为全部状态入口；get/peek/isRunning/getCurrentExecutionId/isCancelled为消费者。clearExecutionContext只清id不改status；get空槽读日志恢复，不能证明活任务。
- 互斥：scheduler和controller常规/关键词三入口同用tryStartWithToken；只是单JVM原子槽，并非分布式锁。当前取证为一个Tomcat进程，不承诺多节点互斥；若部署拓扑改变，应另外审计。
- Interaction X-3：等待信息 → progress日志 → getProgress与已有UI message/summaryText；`DiscoveryPromotionProgressService.refresh`会修正晋升/summary，新增重试提示必须被保留。它当前保留非空summaryText并替换晋升数，本计划通过回归证明，不改其代码。
- Interaction X-4：scheduler抢槽 → task_execution创建 → service终态 → finally清上下文 → 下一两小时触发。异常也要落FAILED，不能留RUNNING空id阻塞下一轮。

### E-5：调度配置与共享预算（原写路径不变）

- `application.yml:178–211` 默认 cron `0 0 2 * * ?`，运行预算4h、总论文15000、作者20000、pipeline=false。
- `ExpertDiscoveryScheduler.kt:55–66`：@Scheduled读取属性，没有显式zone；countScheduledSince(todayStart)>0直接return。
- `TaskExecutionService.kt:124`→`TaskExecutionRepository.kt:60–68`：统计SCHEDULED且status为RUNNING/SUCCESS/PARTIAL_SUCCESS；因此凌晨PARTIAL_SUCCESS同样阻止当天再触发。代码修复必须移除此调用，不能只改配置。
- configureTasks/recoverPipelineTick仅pipeline=true注册恢复tick；切换cron不改变它。
- YAML由部署、环境覆盖写入；ExpertDiscoveryProperties只绑定读取，scheduler读cron/模式，service读timeBudget/caps、各数据源读来源配置，worker读补全开关。没有已证实的“批量发邮件任务配置控制深度发现cron”路径；不做虚假UI承诺。
- V132共享预算：account主键account_scope；day唯一(account_scope,reset_at)；reservation主键permit_id。UTC DATETIME(3)，金额BIGINT，UNKNOWN不退款。
- 全部预算持久写方法位于OpenAlexBudgetRepository：reserve（账号/slot/预占）、settle、markUnknown、applyCooldown、observeProvider、reconcile、claimSyncLease/releaseSyncLease、recordNewEnrichmentRequest、cleanupClosedCycles（包括私有ensureAccountRow/advanceRateSlot）；读方法ledger、countUnfinishedEnrichmentJobs、recentNewEnrichmentCosts及其私有SELECT。`budget-paths.txt`列出精确位置。生产Bean强制用JDBC账本；内存store仅兼容测试。
- 预算消费者：OpenAlexRequestPolicy及所有getJson/补全请求、pipeline预算观察；每次重试仍原路申请，不新增写者。reset_at由官方校准，不按本地0点或本轮开始重置。
- Interaction X-5：两小时多次discover → 同一account_scope预算与新专家补全预留；单轮结束不释放UNKNOWN，不动该周期额度。
- Interaction X-6：cron禁用与pipeline PAUSED是两类既有入口；取消本轮也不同。频率变化必须覆盖模式/取消回归。

### E-6：ES与补全队列边界

本计划不增加ES字段、不改任何专家读写路径、mapping或补全生命周期，仅重复调用现有搜索/消费。`consumeOutcome`、ORCID消费、`enrichProfiles`、`consumeQueuedItem`保持原准入和幂等合同。研究对象为调度/搜索失败恢复，不将所有ES写者纳入修改范围。对重新遇到历史专家的去重与未完成页回放设回归，不能为“统计好看”覆盖姓名邮箱。

前端无变更：`app.js:2360–2374`已显示message和summaryText，`:2681–2718`已显示stopReason与原因字典；直接复用。没有新增DOM/class/CSS，因此本计划不需要样式契约、缓存键升级或前端设计改造。

## 实现方案

### R-0：实施前研究检查点（只读，不预设网络修复）

- [ ] 复核当前HEAD、线上class指纹、任务停止日志、实际启用来源、cron所有有效覆盖、pipeline模式、时区和实例数量。只打印白名单配置，不输出环境全量、API key或认证请求URL。
- [ ] 在服务器同JDK用HTTPS握手探针，对同一解析IP/SNI与curl做顺序对照；每种最多3次，连接/读超时均≤15s，保留UTC时间、异常链、协议、远端IP，不携带key、不做批量采集。比较默认协商与显式TLS1.2仅是探针变量，不能据一次成功直接改生产协议。
- [ ] 若未复现，记录“当前未复现，历史日志确认”，继续实施已确认的恢复缺口；禁止声称TLS底层根因已查明。若稳定复现证书/协议配置错误，保存证据并另修订客户端修复范围，不能以增加重试掩盖不可重试错误。
- [ ] 执行时按本稿保留本轮取消语义；无需为保持既有行为再次请求权限。若用户明确选择持久暂停，先修订计划并重新核对文件/存储范围。

### T-1：重试策略与可测试时间（I-1、I-4、I-5、I-8）

文件：ExpertDiscoveryProperties.kt、SourceStats.kt、ExpertDiscoveryService.kt、application.yml、ExpertDiscoveryPropertiesTest.kt（新增）、ExpertDiscoveryServiceTest.kt。

- [ ] 先写失败测试：前3次同游标握手失败不立刻终止来源；第四次在恢复时间后成功。原代码应在第3次就停，断言因此失败。
- [ ] properties新增一个配置列表 `openAlexSearchRecoveryDelays: List<Duration> = listOf(30s,120s,300s)`。YAML绑定 `open-alex-search-recovery-delays: ${OPENALEX_SEARCH_RECOVERY_DELAYS:30s,120s,300s}`；配置列表是进程配置，非数据库字段。空列表表示关闭新延迟恢复、回退既有3次短尝试；最多3项，每项正数、非递减、≤5分钟，非法值启动失败。不得复用“补全退避”参数控制搜索。
- [ ] SourceStats添加唯一对象字段retry（I-5），同文件定义小data class。仅来源RETRY_WAIT新增字符串，task层不加状态枚举。将retry对象加入buildBySourceDetails，并由buildProgressDetails追加已有summaryText，不覆盖现有论文/收录/晋升信息。
- [ ] 新重试协调使用可注入时间/等待测试接缝，可复用现有 `PolicyTimeSource` 类型作为service末尾有默认SYSTEM的参数（或同文件internal辅助对象）；不引入新通用框架。deadline比较、安排时刻和等待用同一clock；已有短等待保留粒度≤100ms与中断标志恢复。生产普通论文处理的既有时限检查不得被绕过。
- [ ] 给SourceRunOutcome追加可空的待恢复信息（默认null，兼容CORE/ORCID调用），只在短尝试用尽且白名单允许、还有恢复组预算时返回。reason只允许脱敏码REMOTE_TLS_HANDSHAKE/TIMEOUT/NETWORK_IO/HTTP_500/502/503/504，禁止携URL或密钥。
- [ ] 失败分组与调度轮次写在本次discover局部状态，不用Service全局map/后台线程。短重试与延迟恢复均回到searchPapers原许可路径，不能直接调用RestTemplate。

### T-2：来源回访、正确计数与可见进度（I-1～I-5、I-7～I-8）

文件：ExpertDiscoveryService.kt、SourceStats.kt、ExpertDiscoveryServiceTest.kt。

- [ ] 原来源顺序首轮执行保持。OpenAlex待恢复时先安全保存进入页，停止本片段；继续剩余论文来源，再走既有ORCID。只有这些来源完成/停止后才消费本轮局部待恢复项，避免用长等待饿死其他来源。
- [ ] 恢复时间从该组最后失败时刻算起；其他来源已耗过等待时间，回访时不再多睡一遍。若没有其他工作且未到期，100ms片段等待并检查取消/deadline；不得一次Thread.sleep(300000)。达到全局/作者上限或deadline，保留游标、清等待时间、给真实停止原因，不再发请求。
- [ ] 给discoverFromSource显式传入同轮恢复上下文：确切resumeCursor、固定初始runBudget、已处理累计基线。入口persistedPapers初始化为该来源已累计并已在上一片段提交的数量；sourcePapersProcessed同理从本轮来源已处理量开始，避免与原runQuota比较时重新获得完整额度。pending游标null也是合法“第一页”，不能拿旧值替代。
- [ ] 恢复组不再allocateSourceQuota。请求前检查 `本源已处理 < 初始runBudget`、全局剩余、作者cap、deadline。不提高预算，不重跑已经EXHAUSTED的来源。使用stats.nextBatchSeq保证跨来源再回访的进度批次号唯一递增。
- [ ] 只有本来源当轮最终停止才决定sourceFailureCount；全部恢复组用尽记SEARCH_FAILED一次；如果被429/预算拦住则BUDGET_DEFERRED且不算搜索终止错误；取消/时间耗尽不因先前短暂失败变成SEARCH_FAILED。中间RETRY_WAIT不污染DiscoveryResult最终失败数。
- [ ] 安排时写一次带expectedExecutionId的进度；等待时不按100ms落日志。示例消息：`[OPENALEX] 搜索连接中断，等待恢复 1/3，计划重试时间 2026-09-28 02:14:01（北京时间）；先处理其他来源`。其他来源运行时其正常批次message保持，footer summaryText附加该待恢复信息。回访开始与最终停止各写一次事件。完整事件取logs?batchOnly=false；batchOnly=true仍保留现有按批聚合规则。
- [ ] UI晋升读投影会改summaryText中的数字；回归断言RETRY_WAIT轮次/时间文本仍在，不修改DiscoveryPromotionProgressService。持久化原始进度晋升计数保持其现有语义，不冒充实时投影。
- [ ] 通过失败后恢复、永久失败、取消、deadline、未完成页、累积计数全部测试后再进入调度修改。

### T-3：每两小时触发（I-2、I-3、I-6～I-8）

文件：ExpertDiscoveryScheduler.kt、application.yml、ExpertDiscoverySchedulerTest.kt。

- [ ] 先改测试：“今日已有PARTIAL_SUCCESS、当前无运行槽”仍应启动第二轮；当前代码在countScheduledSince处返回，应先红。
- [ ] 只移除同步分支todayStart/countScheduledSince闸门与无用import、修正“每日一次”注释；保留pipeline优先分支、tryStartWithToken、onStarted绑定、finally清理、异常FAILED。
- [ ] 默认cron改成 `0 0 */2 * * ?`，@Scheduled指定Asia/Shanghai；显式部署覆盖仍优先，`-`仍禁用定时。ExpertDiscoveryProperties.cron默认`-`保留（直接实例化/测试安全），运行部署值由application.yml提供。
- [ ] 不增加第二个@Scheduled、Cron任务、自动补跑队列或线程池。4小时单轮deadline保留：一轮超过2小时可跨一个触发点，后者跳过；每两小时是触发机会，不是强制每次启动。
- [ ] 默认全来源每两小时继续，不只OpenAlex；保留每源与全局cap。原已EXHAUSTED来源下一扫描周期从头开始是Codec现有语义，重复专家由原去重过滤；记录这一成本，不擅自引入每天额外查询/轮换策略。
- [ ] 初始化进度文案显示本次为定时发现及实际cron/时区，可同时给恢复间隔配置文本；去除误导性的“初始化 EuropePMC 搜索”。直接沿用已有message显示，不改前端。
- [ ] 取消只止本轮；要停止未来触发，设置已有EXPERT_DISCOVERY_CRON=`-`并重启。不要把EXPERT_DISCOVERY_ENABLED=false当作只关cron的无害替代，因其影响Bean装配，需另查。

### T-4：验证及上线检查，不扩大修复范围

- [ ] TDD：每项记录原代码失败断言、改后通过命令；不靠改旧期望“让测试绿”。既有“三次timeout即终止”测试改为显式空恢复列表，另加默认恢复组用尽的测试。
- [ ] 运行本计划验收命令，记录测试数、失败原因、exit code；端口绑定测试在许可环境跑，不跳过后宣称全通过。
- [ ] 上线前核对有效配置来源（包括profile/external/env），将实际cron设两小时，不只修改仓内默认。等待无运行中发现/补全/邮件任务的安全部署窗口，保存WAR与配置备份；不通过杀进程制造“已停止”。
- [ ] 发布授权单独遵从用户；本次计划不自动上线。发布后校验class、启动健康、cron实际时区与下一时间，不自动触发额外手工采集。至少观察一次恢复和两个相邻触发点后，才报告生产效果验证完成。
- [ ] 失败回滚代码包及原cron；不回滚正常收录专家、游标或credits账本，不清除UNKNOWN。回滚后两小时cron若仍被旧代码每日闸门挡住，必须恢复原02:00配置并说明状态。

## 变更文件清单

路径均相对仓库根。只允许以下8个产品/测试文件；文档/证据归本目录，不改变产品范围。

| # | 文件 | 变更 | 归属 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 同轮有限恢复、上下文与计数、等待进度、时间测试接缝 | T-1/T-2 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SourceStats.kt` | 唯一retry观察对象及同文件类型 | T-1/T-2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/config/ExpertDiscoveryProperties.kt` | 搜索恢复间隔及校验 | T-1 |
| 4 | `src/main/resources/application.yml` | 搜索恢复列表、两小时默认cron | T-1/T-3 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt` | 取消每日闸门、固定时区、准确初始化文案 | T-3 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 新恢复测试及现有边界回归 | T-1/T-2 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/config/ExpertDiscoveryPropertiesTest.kt`（新增） | 列表默认/绑定/非法值/空值关闭 | T-1 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt` | 两小时/互斥/模式/异常测试 | T-3 |

不修改：OpenAlexRequestPolicy、OpenAlexDataSource、RestTemplateConfig、数据库migration、TaskProgressStore、通用任务repository、任何前端文件。需要变更它们必须先给新增证据并修订范围，不能执行时随意“顺手修”。

## 验收标准

- I-1：默认列表30s/120s/300s、同页前三次失败后允许恢复；一直失败共12次；跨多页间歇失败也只共用3个恢复组。空列表维持3次。HTTP403、证书/协议/反序列化错误只走原非重试路径；每个500/502/503/504用例可恢复。改用fake clock，不真实等待450秒。
- I-2：固定C7页连败后成功，全部失败请求cursor=C7，成功完整消费后才C8；无页成功时累计论文0、检查点ACTIVE/C7。部分消费/RAW/入队/去重失败分别保持入口，旧查询key不变。新service实例从真实保存的entity继续，不依赖旧内存map。
- I-3：先处理500、失败恢复再处理100，papersSearched=600、累计checkpoint只增600；不得1100。source runBudget仍10000；定制总cap=600时第601篇不能请求。其他来源消耗全局余额时恢复只用余量。elapsedMs不丢首段，批次号不重复，只有一个executionId和原deadline。
- I-4：等待时sourceFailureCount=0且task RUNNING；恢复后穷尽为EXHAUSTED、无SEARCH_FAILED；永久失败计1次SEARCH_FAILED/1个failedSource。仅OpenAlex且一直失败最终FAILED；另一来源成功则PARTIAL_SUCCESS；取消优先CANCELLED。
- I-5：retry仅对象字段；消息/summary、details与返回stats一致。等待期nextRetryAt非空，执行/取消/截止后为空；完整日志包含安排/回访，batchOnly旧行为不变；晋升投影更新数字不删除等待提示。无每100ms日志。
- I-6：从北京时间2026-09-28 00:01计算下3个触发为02:00、04:00、06:00；默认时区改成UTC的测试中注解zone仍Asia/Shanghai。YAML实际默认cron与注解一起断言，不只测试一份手写字符串。今日已有SUCCESS/PARTIAL_SUCCESS不阻止新轮；模拟运行槽失败则discover调用0次/新增execution0条。
- I-7：等待期间取消，在下一100ms切片内观察到取消且之后搜索0次；InterruptedException恢复线程中断位；时间不足不等完退避、不发下一请求。cron`-`没有定时触发，手动仍可启动；pipeline=true仍只tick、PAUSED不复活。异常后槽不留RUNNING空id。
- I-8：预算延期/429即便此前已安排恢复也撤销网络回访，不突破retryAt；每个实际重试经reserve；两个定时轮次共享同一store/account_scope，不能恢复到免费上限；补全保留额度与UNKNOWN测试保持全绿。不通过TLS证书错误进入恢复。
- X-1～X-6：分别由下方A-1/A-2、A-6、A-3、A-4/A-5、A-7、A-5/A-8覆盖；机器测试必须覆盖对应跨路径，而非仅测私有辅助函数。

执行命令（JDK11），按顺序，最终代码后重新运行：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest,ExpertDiscoveryPropertiesTest,DiscoveryCheckpointCodecTest,OpenAlexRequestPolicyTest,DiscoveryPromotionProgressServiceTest,TaskProgressStoreTest,TaskProgressStoreRebindTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest,DiscoveryPipelineServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

`mvn test`已绑定JS测试；不另造前端实现。无数据库结构变更，不要求新增Docker IT。原测试存在需要本地socket的真实XML管道fixture，权限失败需在允许本机端口的环境重跑，不能记成业务通过。验收报告区分已跑与未跑，本计划创建阶段没有运行实现测试。

## 人工验收清单

仅在隔离验收环境构造故障；上线不得为验收关闭真实证书校验。实施者须用上述测试中的可控源/时钟fixture生成脱敏的请求顺序与API响应记录，验收人按以下步骤操作，不需读实现。生产自然发生的相同故障可替代fixture，但必须提供同等证据。

### A-1: 三次失败后恢复，先处理其他来源
- 前置条件：验收环境配置OpenAlex和Crossref，每页100；OpenAlex第一页成功，第二页C7连续3次返回指定握手中断，之后返回100篇及无下一页；Crossref返回100篇后穷尽。恢复列表30s/120s/300s，固定输入保存为可复跑fixture。
- 操作步骤：1. 手动启动深度发现；2. 打开任务弹窗；3. 查看完整进度日志；4. 查看fixture的按时间排列请求记录。
- 预期结果：OpenAlex第二页前三次均C7，随后Crossref至少开始一次，再回访OpenAlex C7；回访不早于第三次失败后30秒。OpenAlex最终200篇、sourceFailureCount=0，无终态SEARCH_FAILED，游标仅成功后标EXHAUSTED。
- 覆盖：O-1；I-1/I-2/I-3/I-4；X-1。

### A-2: 持续失败与重新启动续跑
- 前置条件：验收环境仅启用OpenAlex，预置Codec生成的ACTIVE/C7；全部搜索请求返回相同握手中断，单轮时间足够。
- 操作步骤：1. 启动；2. 等到所有有限恢复结束；3. 查看请求总数与任务状态；4. 重启验收应用并把故障撤除；5. 再运行相同条件。
- 预期结果：第一轮12次请求、论文0、SEARCH_FAILED一次、任务FAILED；检查点仍ACTIVE/C7。第二轮第一请求为C7，不从第一页重扫，不从进度日志自动复活旧等待。
- 覆盖：O-1；P-2/P-6；I-1/I-2/I-4/I-7；X-1。

### A-3: 等待提示及异步晋升共同显示
- 前置条件：A-1场景，已收录的1位专家在等待期间由已有补全流程晋升。
- 操作步骤：1. 保持弹窗轮询；2. 查看表格下summary与停止原因；3. 请求`GET /api/task-progress/EXPERT_DISCOVERY/logs?executionId=<本次id>&batchOnly=false`；4. 故障恢复后刷新。
- 预期结果：等待时显示`RETRY_WAIT`、`1/3`和明确北京时间；晋升显示1仍保留等待提示；恢复开始后无过期nextRetryAt，最终文案不再称“等待”。完整日志有安排和回访事件，无每100ms一条的刷屏。
- 覆盖：O-1；P-1/P-6；I-4/I-5；X-3。

### A-4: 同一天两个触发与跨触发互斥
- 前置条件：验收环境用真实两小时cron或可控时钟推进；北京时间02:00有一次正常完成或PARTIAL_SUCCESS记录，04:00前空闲。
- 操作步骤：1. 到04:00查看是否有新的SCHEDULED记录；2. 让该轮持续到06:00；3. 到06:00查看记录数；4. 当前轮结束后到08:00再次查看。
- 预期结果：04:00新增且继续原查询游标；06:00不新增、不并发、不补排；08:00再新增。02:00历史记录不会封锁04:00。初始化文案显示当前cron和Asia/Shanghai。
- 覆盖：O-2；P-4；I-2/I-3/I-6；X-4。

### A-5: 取消本轮与关闭定时分别验证
- 前置条件：同步模式、某轮处于等待恢复；下个定时点可推进。此项以本稿取消语义为前提。
- 操作步骤：1. 点“取消任务”；2. 检查随后请求记录；3. 到下一定时点；4. 再取消该轮，设置EXPERT_DISCOVERY_CRON=`-`并重启；5. 跨过两个定时点，再手动启动。
- 预期结果：取消后不发下一次恢复搜索，当前轮CANCELLED；未关cron时下一定时点仍可启动。cron关闭后两个点均没有SCHEDULED新记录；手动可启动，且从原入口继续。操作提示不承诺“永久暂停”。
- 覆盖：O-2；P-2/P-4；I-6/I-7；X-4/X-6。

### A-6: 消费错误、准入与上限不被恢复逻辑绕过
- 前置条件：隔离环境准备3组fixture：姓名邮箱不匹配、方向不合格、RAW写入失败；另有总论文上限600且先成功500的分页输入。不可写真实生产ES故障数据。
- 操作步骤：1. 分别运行三组；2. 查任务原因与筛选层；3. 运行500+恢复后100+额外下一页场景；4. 查请求计数和checkpoint累计量。
- 预期结果：身份/方向不合格者仍不入筛选层；RAW失败停为RAW_WRITE_INCOMPLETE且不进入TLS恢复组，游标不前进；cap600时只处理600，累计增量600，不请求第601篇。没有为提高产量改姓名/邮箱。
- 覆盖：P-1/P-2/P-6；I-2/I-3；X-2。

### A-7: 429、额度耗尽及跨轮账本
- 前置条件：隔离预算store准备一个官方周期，首次响应429带Retry-After，另一组余额低于发现保留门槛；使用验收stub，不能清空生产账本。
- 操作步骤：1. 运行429组；2. 查看恢复请求数；3. 运行低余额组并在下个两小时点再次触发；4. 查看账本、其他来源与补全请求。
- 预期结果：429/余额不足均BUDGET_DEFERRED，不进入30/120/300秒网络恢复；第二轮不把余额重置10000；补全保留额度与UNKNOWN仍在；其他来源按原规则继续。
- 覆盖：P-3；I-8；X-5。

### A-8: pipeline与运行时限回归
- 前置条件：隔离环境现有pipeline已PAUSED；另建同步模式、deadline短于下一恢复时刻的运行；禁止在生产切pipeline。
- 操作步骤：1. pipeline环境经过两小时cron和恢复tick；2. 同步环境触发短deadline故障；3. 查看状态和后续请求。
- 预期结果：pipeline保持PAUSED、没有新窗口；同步轮停止为TIME_BUDGET且无超时后的搜索，保留ACTIVE入口与pendingWork，不标穷尽。不会修改TLS信任配置。
- 覆盖：P-3/P-5/P-6；I-3/I-7/I-8；X-6。

人工验收开始时才从本节导出`discovery-resilience-two-hour-acceptance.md`，带复选框、验收人、日期和备注；本次不生成两份清单。

## 自检与执行交接

- [x] 两个子系统、8个文件、共享JSON仅新增一个retry对象，无SQL/ES新字段。
- [x] 全部新增状态/观察字段有I-4/I-5，写者与消费者有审计收据。
- [x] X-1～X-6逐一对应测试/人工场景；需求O-1/O-2与P-1～P-6均有A项。
- [x] 不修改前端，明确沿用message/summary/停止原因既有显示；无新增样式。
- [x] 不把TLS网络根因猜测写成既定实施动作，不混入ARXIV修复。
- [x] 明确同轮恢复计数、全局份额、deadline、跨执行预算、取消与模式隔离。
- [x] 已使用知识重新核对代码；过时的预算内存实现与旧缺陷状态在知识回写中纠正。K-task-activity-execution-identity中尚无peek/heartbeat的历史描述未作为当前事实；本轮依据实际代码。K-circuit-breaker-terminal-status仅借鉴“终态不可假成功”，不采用其他任务一律FAILED规则。命中的5条知识均更新使用计数，无命中条目被归档；未发现需处理的超过90天低频相关条目，无5条同主题合并或10次推广触发。
- [ ] 执行前核对R-0有效部署配置；当前仅待审计划，不能宣称实施/验收通过。取消沿用既有语义，用户若另行选择持久暂停再修订。
