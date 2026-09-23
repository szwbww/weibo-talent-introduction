# 子计划02：持久化采集队列与全文处理

日期：2026-09-22。状态：详细计划已完成，待用户确认，未实施。
依赖：[01账号预算](01-openalex-account-budget.md)；接口使用其permit/Deferred及预算快照。交付：队列、恢复、窗口服务；本期通过测试/受控服务调用验收，生产入口尚不切换。下一计划只接入口，不再补底层状态机。

## 需求描述

- R-1：论文采集不再等待整页PDF下载；每条处理完即可持久化并生成专家。其他来源能获得处理机会。
- R-2：暂停、进程退出、ES故障、4小时窗口结束均保留已采集及已抽取工作；安全续跑，队列和磁盘有上限。
- 保持：RND_TARGET及启用来源过滤、现有专家身份/邮箱/去重/晋升门禁；自动学术补全及三层局部写入；旧单次模式及旧检查点保留；ORCID记录不冒充论文。
- 范围外：改变资格或职称判断、ES映射迁移、付费全文、邮件逻辑、新增来源、前端、接通全天调度、保证每天用完全部额度。

## 关键不变量

### I-1：查询与采集位置独立于处理完成
- Rule：每来源规范化有效条件生成query_hash；包括关键词、机构、国家排除、年份、OA、scope、页大小和来源自己的名称，不包含其他sources的排列/省略方式。stream唯一(query_hash,source,epoch)，cursor仅在整页入队同一事务提交后推进；cursor状态ACTIVE/EXHAUSTED。EXHAUSTED不因重启、次日、4小时新窗口重置。旧v2消费游标仅在完整条件能确认匹配时用于种子；多条冲突则从头保守重放，禁止挑最大游标。
- Applies to：规范化、建stream、旧游标导入、页入队、恢复。
- Violation consequence：手动定时分叉、跳页丢数据、每天反复扫相同页。
- 来源：DiscoveryCheckpointCodec审计；K-discovery-budget-backpressure。

### I-2：工作唯一性与版本
- Rule：队列唯一(stream_id,item_key)；PAPER键优先使用规范化DOI、PMCID、PMID的带类型前缀SHA256，无ID时使用规范序列化完整元数据SHA256并标identity_quality=PAYLOAD_HASH，不用标题模糊合并。ORCID键用完整ORCID。payload_version=1，unit=PAPER/RECORD显式区分；未知版本不得消费。无可靠标识/过大记录形成可观测FAILED条目，不能静默丢弃并声称完成。
- Applies to：整页入队、序列化、消费、清理、迁移。
- Violation consequence：误去重、重复下载、跨版本错误消费。
- 来源：PaperMetadata没有通用paper.id的源码事实。

### I-3：租约与状态机
- Rule：job状态PENDING→RUNNING→SUCCEEDED/RETRY_WAIT/FAILED；RETRY_WAIT到期、RUNNING租约失效可重新领取。claim/heartbeat/finish均校验lease_token及generation；saveExtraction校验job租约token和领取时generation。唯一例外：pipeline因人工暂停递增generation但job未被重新领取且租约仍有效时，允许旧job保存抽取结果并退回PENDING，不允许消费专家或complete；owner失效/已重新领取均不得覆盖结果。attempts只在实际处理失败时增加，额度等待、人工暂停、容量等待不消耗尝试数。可重试失败最多5次，30s/2m/10m/30m退避；鉴权/载荷错误直接FAILED并给原因。FAILED不会被每天自动重新入队。
- Applies to：领取、重试、进程恢复、终态写入。
- Violation consequence：重复工作、旧worker覆盖新结果、失败无限循环。
- 来源：既有V131租约/CAS模式。

### I-4：先保存抽取结果，再幂等消费
- Rule：extraction_json非空意味着已可靠抽取，后续尝试直接消费；不能再次下载。专家写入仍调用原consumeOutcome门禁及补全入队，成功后才置SUCCEEDED。跨ES/MySQL至少一次重放；不宣称跨库exactly-once。已存在专家不重复计新增，补建缺失学术任务；RAW写入异常与“无合格邮箱”必须是不同结果，后者可SUCCEEDED但新增0。
- Applies to：抽取→队列、队列→RAW/晋升、RAW→学术任务、恢复。
- Violation consequence：重复下载、写失败误成功、丢补全、重复专家。
- 来源：K-enrichment-write-three-layers；原consumeOutcome/ensureEnrichmentJob路径。

### I-5：容量与所有权有界
- Rule：活跃job=PENDING/RUNNING/RETRY_WAIT，数量高水位20000、低水位10000；按实际UTF-8负载字节及预留结果空间共同限制1GiB。metadata上限64KiB，extraction上限32KiB；入队时为每条未抽取job预留32KiB，保存后转换为实际字节，不能队列满后连在途结果也无法写入。PDF二进制不入库。整页若超过剩余容量整页不提交、不推进；单条超限写轻量FAILED诊断，不截断姓名/邮箱后继续造专家。
- Applies to：事务入队、结果保存、容量计数、清理。
- Violation consequence：磁盘失控、抽取结果死锁、内容截断导致身份错配。
- 来源：总体设计背压要求。

### I-6：公平与外部请求边界
- Rule：单账号流水线同时只有一个有效窗口owner；owner协调全局最多8个提取任务、同域名最多2个HTTP请求，包含重定向实际目的域名；单论文90秒。生产者各来源每轮一页100，ORCID一页100记录；消费按来源轮转，每源内部先公开可下载任务，同时每10个高优先任务至少取1个最老普通任务。有待处理的慢来源不能阻止其他源。OpenAlex延期只影响依赖该API的操作，其他来源/公开全文继续。
- Applies to：采集、下载执行器、URL选择、轮转、限流。
- Violation consequence：前源独占窗口、线程无限增长、越过站点并发限制。
- 来源：线上18921其他来源0的观测及整页join审计。

### I-7：持久化控制与窗口交接
- Rule：singleton pipeline(id=1)保存desired_state=PAUSED/RUNNING、phase=QUEUED/RUNNING/WAITING/DRAINED/FAULTED、criteria_json/version、generation、owner_token/until、execution_id、window_until、next_wake_at、wait_reason、raw_scan_done。初始PAUSED。pause先提交desired_state=PAUSED并递增generation停止新领取；已领取工作允许保存可靠抽取结果，停止新增专家消费，待恢复再消费。restart/reset不得改PAUSED。4小时仅窗口截止，停止领取后最多90秒收尾并释放owner；RUNNING允许后续tick开启新窗口。所有来源EXHAUSTED且队列无活跃条目置DRAINED；无操作需求不循环创建空task_execution。
- Applies to：launch/pause/resume/tick/window、重启、旧任务迁移。
- Violation consequence：暂停被撤销、无限空记录、终态不真实。
- 来源：总体设计；K-circuit-breaker-terminal-status。

### I-8：计数和清理不改变事实
- Rule：pipeline/stream累计queuedPapers、queuedRecords、processedPapers、processedRecords、indexedExperts、duplicateExperts、failedItems分别计数；一次状态CAS最多累加一次。重放遇已有专家可计重复，不能追认新增；崩溃边界新增计数允许保守少计，明示非全库总数。queueDepth只计活跃job。已终态负载7天后可清空，去重键/状态90天保留；非终态及未消费抽取结果不清理。清理不重置stream游标、不从负载重算已累计指标。
- Applies to：结果落库、状态快照、task_execution摘要、清理。
- Violation consequence：采集冒充专家产量、清理后指标倒退、重复扫描。
- 来源：原任务指标口径及总体设计。

## 现状审计

### 旧检查点
- V32 `discovery_source_cursor`：source_name VARCHAR(50)唯一、cursor_value TEXT、papers_processed_total、时间字段。完整写路径：ExpertDiscoveryService.persistSourceCheckpoint的论文页/ORCID页调用；完整读路径：loadSourceCheckpoint→repository.findBySourceName。DiscoveryCheckpointCodec生成含sources列表的v2键。
- 新队列只读旧表做首次种子，不修改V32结构或消费语义。X-1：旧已处理位置→新已采集位置，无法验证条件则重放。

### 新增流水线存储
- 尚无新表。所有新写路径收口于DiscoveryPaperQueueRepository：create/launch/pause/resume、owner/stream/job租约、事务入页+cursor、saveExtraction、retry/complete/fail、wait、cleanup。所有读路径：DiscoveryPipelineService调度/恢复/快照、下一阶段controller状态接口。
- X-2：页事务→采集检查点→worker领取；X-3：抽取结果→ES消费→MySQL终态；X-4：持久化控制→定时/手动入口→任务历史。

### 专家与学术任务（结构保持）
- PaperMetadata无通用稳定work ID；含DOI/PMCID/PMID、作者、下载URL和可选fullText。OrcidDataSource.searchPage返回OrcidRecord，不能直接当PaperMetadata。
- `config/RestTemplateConfig.kt`中的BoundedFulltextHttp是PDF、EuropePMC XML及Unpaywall有界请求公共执行器，当前请求工厂允许底层重定向；同域并发必须在逐跳请求处实施，仅在论文入口按downloadUrl分组不够。
- ExpertDiscoveryService.discover按源串行；parallelExtractOutcomes整页提交并join；consumeOutcome负责邮箱/身份、重复检查、RAW写入及晋升，enqueueEnrichmentJob/ensureEnrichmentJob负责学术任务。
- RAW/CANDIDATE/APPLICATION mapping均dynamic:false，enrichedAt keyword。直接写入、RAW到候选晋升、候选到APPLICATION业务写入、学术补全partial update均继续走原服务，不为队列新增ES字段。现有详情/搜索/外联读取同一结构。
- V131 expert_academic_enrichment_job的expert_doc_id唯一；入队服务写，repository领取/续租/终态CAS，自动worker和人工补全读取。X-5：新队列consumeOutcome重放→ensureEnrichmentJob不能缺失或重置已成功任务。（来源：K-enrichment-write-three-layers）

### 执行器与任务记录
- DiscoveryExecutorConfig现有discoveryFetchExecutor默认4、队列按maxPapers配置；不能无限提交整个数据库队列。已有补全执行器独立。
- task_execution由TaskExecutionService.runAndRecord创建/结束，TaskExecutionRepository和任务历史接口读；task_progress_log由TaskProgressStore写、进度/日志接口读。新服务继续通过这两个服务记录窗口，不直接插入另一套任务历史。
- X-6：任务启动token/executionId→窗口结束清理，必须按token清理，不能旧窗口清掉新窗口；指标按I-8，taskSuccessCount仍为新增专家。
- 工作区已有其他任务WIP；本计划只改列明代码段，不整理无关差异。

## 实现方案

### T-1：三表、队列事务与恢复（I-1至I-5/I-7/I-8）
修改清单1、2；实施前复核V133未占用，已占用则先更新本计划文件名，不覆盖迁移。V133新增下列InnoDB表，时间统一UTC DATETIME(3)，UUID租约token，JSON以有界UTF-8 TEXT/MEDIUMTEXT保存并验证版本。

- `discovery_pipeline`：I-7所有控制字段；增加queued/processed/indexed/duplicate/failed累计BIGINT、active_count、payload_bytes、reserved_result_bytes、capacity_paused、created_at/updated_at；容量与状态修改锁id=1。
- `discovery_collection_stream`：id、pipeline_id、query_hash CHAR(64)、source VARCHAR(50)、epoch BIGINT、criteria_json、cursor_value TEXT、cursor_state、next_attempt_at、lease_token/until、source_error、I-8分单位计数、created_at/updated_at；唯一(query_hash,source,epoch)。phase/desired_state只属于pipeline，stream只保存其来源状态。
- `discovery_paper_job`：id、stream_id、item_key VARCHAR(160)、identity_quality、unit、payload_version、metadata_json、extraction_json nullable、payload_bytes、reserved_result_bytes、status、attempts、next_attempt_at、lease_token/until、generation、last_error VARCHAR(1000)、created_at/updated_at/completed_at；唯一(stream_id,item_key)，索引(status,next_attempt_at,source所在stream_id,id)，终态清理索引(completed_at,id)。
- owner租约30秒、心跳10秒；job租约120秒、心跳30秒；来源请求同样使用stream租约避免取同一页。锁序pipeline→stream→job；网络和ES调用不持锁。租约失效的旧worker不能开始下一外部调用，已发请求可能完成，CAS禁止旧结果覆盖；恢复依赖专家幂等，不承诺撤销在途外部写入。
- 页容量预检可减少浪费，最终以入队事务为准；重复键不占新增容量。容量不足维持原cursor，保存next_attempt_at和QUEUE_FULL原因。低水位且字节足够容纳一页后恢复；总字节计入终态尚未清理负载，清理每批1000。
- fullText不允许无声丢弃：可以从公开地址重取时仅保存引用；无可重取地址时纳入64KiB负载限制，超限形成FAILED/PAYLOAD_TOO_LARGE，页其他条目正常入队。过大抽取形成FAILED/EXTRACTION_TOO_LARGE并保存诊断，不能写截断专家。

### T-2：来源适配与复用门禁（I-1/I-2/I-4/I-6）
修改清单3、4、10。

- ExpertDiscoveryService提供窄的内部接口：`collectQueuePage(source,criteria,cursor)`返回原始单页及nextCursor/exhausted；`extractQueuedItem(envelope)`返回版本化抽取结果；`consumeQueuedItem(envelope,result,executionId)`返回已处理/新增/重复/可重试失败；ORCID同入口按unit分支。抽取/消费逻辑从现有方法提取并由旧流程共同调用，不复制门禁。
- 新来源查询每次只取一页，不把maxPapersPerSource/maxPapersPerRun当日上限；旧流程仍保留10000/15000限制。新查询规范化固定scope=RND_TARGET并应用相同禁用来源规则。API方法不接受未校验任意source字符串。
- 原consumeOutcome内部吞掉的可重试写失败必须通过返回结果表达，旧模式保持统计语义；队列仅在专家/补全必要写入已成功或明确无匹配结果时complete。
- RestTemplateConfig.kt的BoundedFulltextHttp增加仅队列提取作用域启用的request gate；ExpertDiscoveryService.extractQueuedItem在同一worker线程进入/退出该作用域（finally清理）。gate按每个实际URI host领取共享许可，响应流关闭后释放；关闭底层自动重定向，显式最多跟随5跳，每跳重新限流/检查90秒剩余时限，并剥离跨origin凭证。保留现有转换器、错误处理和有界流。没有队列作用域的旧调用保持原行为；拒绝OpenAlex Content目标，不将其当公开PDF。域名许可暂不可得则记录作用域内的HOST_BUSY标志并终止本次提取；即使来源适配器将异常转为空outcome，外层也依据该标志把job延期，不把它计永久失败或消耗attempts。测试同时覆盖PDF/XML/Unpaywall、重试与重定向的真实本地HTTP请求，避免只mock外层。
- 保留已启用自动学术补全；队列不直接写补全表、不改学术字段或LayerUpdateResult。

### T-3：窗口服务、公平调度与配置（I-5至I-8）
修改清单3、5、6、7。DiscoveryPipelineService负责协调状态与DTO，数据库SQL只在repository；不新增任务类型。

固定下游接口：

| 接口 | 持久化/返回约定 |
|---|---|
| launch(criteria,triggeredBy,includeRawScan) | 原子保存RUNNING和规范查询；同查询幂等返回pipelineId=1、phase、executionId可空；活跃/有积压的不同查询409；PAUSED同查询视为显式恢复 |
| pause() | 幂等持久化PAUSED；无需有RUNNING任务历史；返回phase及在途数 |
| resume() | 只恢复已有查询；无配置返回409；不新建epoch |
| tick() | 短事务判断PAUSED/nextWake/owner；派发到专用执行器后立即返回；线程池拒绝则保留QUEUED，下次重试 |
| status() | state（desired_state=PAUSED时派生PAUSED，否则取phase）、phase、desiredState、currentExecutionId、queryHash、各来源状态、I-8计数、queueDepth/bytes/oldestAge、waitReasons、nextWakeAt和01预算快照 |

- 加pipelineCoordinatorExecutor单线程、queueCapacity=0。其窗口循环独立协调采集future及最多8个提取future，**不等待整页全部join**；采集任务使用单独pipelineCollectionExecutor有界池（4线程、队列8），同来源最多1个未完成页，其余已就绪来源可继续。新队列使用独立pipelineFetchExecutor（同配置文件声明），pipeline-fetch-concurrency默认8，旧discoveryFetchExecutor配置保持；新队列最多提交8个；同域许可不占满全部工作槽，无法取得时延期条目。
- 全局仅一个有效owner使8并发跨上下文成立；owner失效时不立刻重叠新窗口外呼，先等待已派发job租约/90秒外呼期限结束，状态WAITING/OWNER_RECOVERY。窗口状态/计数仅协调者更新，worker通过线程安全结果通道汇报。
- 每窗口通过runAndRecord建EXPERT_DISCOVERY记录；onStarted绑定token与executionId并CAS写pipeline。task result实现既有TaskExecutionSummaryProvider并保留stats/indexed口径，附pipeline快照；终止原因统一`WINDOW_END/DAILY_BUDGET/QUEUE_FULL/MANUAL_PAUSE/SOURCE_EXHAUSTED/SOURCE_ERROR`。有待处理的窗口交接记PARTIAL_SUCCESS、人工暂停CANCELLED、不可恢复全失败FAILED；只在已排空且无失败时SUCCESS。progress采用现有状态值并用message明确“窗口结束，等待续跑”，不新增共享枚举。
- RAW扫描只在本查询启动显式includeRawScan=true且raw_scan_done=false时运行；沿用原扫描门禁，完成后持久化标记，后续窗口不重扫。默认false。扫描中崩溃允许安全重放，不据半途进度假定全部完成。
- 新配置：pipeline-enabled=false；queue-high/low=20000/10000；queue-max-bytes=1073741824；metadata-max-bytes=65536；extraction-max-bytes=32768；per-host-concurrency=2；pipeline-tick=30s；window沿用4h。均环境变量可调且启动校验0<low<high、容量可容一页、并发为正；不是逐档手工把论文cap从2500改到10000。
- WAITING无就绪工作时释放窗口，不长时间睡到reset；当多个等待原因并存，nextWake取最早可进展时间，其他来源不必跟OpenAlex等待。暂停时phase可保留在途收尾态，对外state优先显示PAUSED；旧owner仅可按原token释放自身租约/写收尾，不可清除新owner或恢复RUNNING。人工暂停不暂停已经独立入队的学术补全，此处只停深度发现生产/消费，UI下阶段明确这一点。

### T-4：故障注入与灰度前置（I-1至I-8）
修改清单8、9。隔离MySQL+可控HTTP/ES mock，对事务提交前后、抽取保存后、RAW成功补全入队前、终态前分别注入异常；同一组fixture比较旧/新模式专家集合。pipeline-enabled默认false保持现有生产流程；本阶段验收直接调用service并显式tick，不提前改controller/scheduler。

## 变更文件清单

恰好10个文件，2个子系统：发现队列存储、发现处理/协调。旧共享表/ES新增字段0；三张新表字段由I-1至I-8完整约束。

| # | 文件 | 改动 |
|---|---|---|
| 1 | src/main/resources/db/migration/V133__create_discovery_paper_queue.sql | 新控制/stream/job表 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt | DTO、事务、租约、清理 |
| 3 | src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt | 协调、生命周期、快照 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt | 复用单页/抽取/消费接口 |
| 5 | src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt | 独立协调/采集执行器及有界提取 |
| 6 | src/main/kotlin/com/weibo/talentintroduction/config/ExpertDiscoveryProperties.kt | 队列参数 |
| 7 | src/main/resources/application.yml | 环境配置及说明 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt | 窗口、暂停、公平、故障重放 |
| 9 | src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepositoryIT.kt | MySQL事务/容量/CAS/恢复 |
| 10 | src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt | 队列全文作用域、逐跳域名许可；测试放在清单8 |

## 验收标准

- I-1：sources省略/排序不同但有效同源条件相同得到相同stream；事务回滚cursor不动；EXHAUSTED跨日不重置；旧冲突游标保守重放。
- I-2：DOI规范化重复只一job；无ID不同元数据不按同标题合并；ORCID计record；未知版本/过大条目明确失败。
- I-3：双连接claim唯一；旧token终态更新0行；第5次失败终止；预算等待attempts不变；租约过期可恢复。
- I-4：已存抽取但ES故障恢复下载次数不增加；RAW写失败job不成功；重放补建缺失学术任务，APPLICATION字段不覆盖。
- I-5：count与bytes任一满停止整页；多个入队者不越界；在途结果预留生效；低水位才恢复；终态清理释放字节。
- I-6：故意挂起首源请求，其他来源仍采集/消费；全局≤8、每目标域≤2；慢站点/重定向验证；优先任务不断加入时最老普通任务仍被领取。
- I-7：暂停持久化、在途仅保存不新消费、重启/日切不解除；4h测试缩成1分钟后可续同队列；executor拒绝和owner崩溃后无重复活跃窗口。
- I-8：重复complete不累加；计数论文/ORCID/专家分离；清理不改计数/游标；历史task result、progress、status三者终止原因一致。
- Java11：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPipelineServiceTest,DiscoveryPaperQueueRepositoryIT,ExpertDiscoveryServiceTest,RestTemplateConfigTest -DmysqlIt=true test`。真实MySQL IT必须实际执行。发布前package；未实现逐跳域名限制/真实MySQL故障测试不得标通过。

## 人工验收清单

### A-1：入队与处理解耦（X-2）
- 前置条件：隔离环境启用队列、测试服务驱动launch/tick；Mock OpenAlex/Crossref各100篇、ORCID100记录，OpenAlex全文延迟30秒，关闭发信。
- 操作步骤：1. 启动；2. 10秒时查看status和数据库；3. 释放全文请求后再次查看。
- 预期结果：OpenAlex全文未完成时其他来源已有采集/消费；queuedPapers=200、queuedRecords=100；处理数单独增长；入队数不计新增专家。
- 覆盖：R-1、I-1/I-2/I-6/I-8。

### A-2：进度迁移、暂停、窗口交接（X-1/X-4/X-6）
- 前置条件：隔离库预置可确认同条件的旧v2已处理检查点；窗口1分钟，驱动每30秒tick；另准备条件冲突旧检查点。
- 操作步骤：1. 启动匹配查询并等窗口结束；2. 观察下一窗口；3. pause后重启并跨模拟reset；4. resume；5. 独立隔离用例启动冲突旧检查点。
- 预期结果：匹配旧位置用于首次种子且旧行不改；下一窗口处理未完成job；暂停跨重启/reset保持PAUSED；恢复不重扫已保存抽取；冲突旧检查点从头重放并去重。
- 覆盖：R-2、I-1/I-3/I-7，旧模式/检查点保持项。

### A-3：跨库失败重放（X-3/X-5）
- 前置条件：100篇唯一fixture；已保存50条抽取；Mock让ES失败，再让RAW成功但学术入队一次失败。
- 操作步骤：1. 运行并重启；2. 恢复ES/数据库；3. 再tick直至排空；4. 查下载日志、专家数和补全表。
- 预期结果：已有50条抽取不再下载；100条最终成功处理；专家与无故障基线一致；每个合格专家唯一补全任务，成功补全任务不重置；错误期间job不提前SUCCEEDED。
- 覆盖：R-2、I-3/I-4/I-8，学术补全保持项。

### A-4：数量/磁盘背压及限流
- 前置条件：隔离测试高水位200、低100，容量改为能容100条fixture+每条32KiB结果预留；Mock提供超大metadata及跨域重定向下载。
- 操作步骤：1. 暂停消费让生产填满；2. 恢复消费至99条；3. 观察容量和HTTP并发；4. 推进测试时钟7天/90天执行清理。
- 预期结果：QUEUE_FULL时游标不推进，活跃≤200且总占用≤配置；降至99且字节够一页才恢复；超大条目FAILED/PAYLOAD_TOO_LARGE；HTTP全局≤8、实际域名≤2；未完成负载不删，终态负载7天后可清、键90天后可清。
- 覆盖：R-2、I-5/I-6/I-8。

### A-5：专家规则与旧模式回归
- 前置条件：隔离环境包含合法作者、无效邮箱、身份歧义、重复专家、已有APPLICATION五类，原自动补全启用；分别使用全新测试索引跑旧/新模式。
- 操作步骤：1. 用相同RND_TARGET及有效来源执行；2. 比较专家集合、运营字段、补全任务；3. 关闭pipeline-enabled再次使用原手动单次入口。
- 预期结果：两模式有效专家集合相同；无效/歧义不晋升；既有运营字段不变；ORCID计记录；旧入口继续受旧cap保护；三层学术局部更新保持；邮件发送0。
- 覆盖：所有保持项、I-2/I-4/I-8。
