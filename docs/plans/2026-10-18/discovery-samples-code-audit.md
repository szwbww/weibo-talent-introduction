# 失败样本计划 · 代码审计

基线 `3efc192faf043badc1ef27bef124a71d3120c4c5`，2026-10-18。以下路径均相对仓库；行号固定于该基线，执行时用方法名定位并复核差异。
证据目录 [discovery-samples-evidence](discovery-samples-evidence/manifest.json) 记录检索 argv、退出码和文件 SHA。查询范围 src/main、src/test、scripts；命中为空用 exitCode=1 明示，不推断有隐藏实现。

## 1. 真实任务证据

[summary.json](discovery-samples-evidence/summary.json)、[原始27行批次日志](discovery-samples-evidence/run-26127-logs.json) 来自本会话已授权的服务器只读调查，不是本轮新查询。任务26127处理2,200论文；1,350是作者邮箱身份未解析记录数；123是RAW收录，不是自动准入；403的379与PDF_DOWNLOAD_FAILED的417重叠。聚合失败数不能相加当人数。

按正 batch_number 取最大日志id：23批次号、22个有处理量批次；未去重会得到错误的2,300篇/124收录。已查队列表0行、任务聚合日志与应用日志，未恢复逐篇失败输入；结论仅限已查范围，不宣称服务器所有目录均无备份。历史6组原文与12文件SHA核验在 historical-sample-verification.json；捕获时间2026-09-29，与10月15日不同。

## 2. 当前输入与结果 schema

- `discovery/domain/PaperMetadata.kt`：pmcId/pmid/doi/title/pubYear/journal/authors/source/fullText/downloadUrl/candidateDownloadUrls；**没有 OpenAlex work ID**。
- `PaperAuthor.kt`：姓名、ORCID、机构、通讯作者标志、email、OpenAlex作者ID、结构化机构来源/国家。不能只保存名字列表代替完整作者输入。
- `EmailExtractionOutcome.kt`：emails/methodUsed/failureReason/httpRequests/fulltextObtained/downloadFailureCategory/identityRuleVersion。null 的 fulltextObtained 有兼容推导；HTML=true不证明论文正文。首版不改此DTO，避免影响队列JSON。
- `config/PdfExtractionProperties.kt:7–23`：10MiB、maxPages=2、tailPages=1、HTML开关、blacklist、重试；实际配置须保存，不能假定线上都是默认值。

## 3. 同步与消费边界

全部调用点见 capture-paths.txt。
- `ExpertDiscoveryService.kt:662/673` discoverFromSource拿到executionId；`:928` parallelExtractOutcomes先抽整页，`:930+`消费循环才检查预算/暂停，`:951`消费。
- `:1573` extractOutcome捕获业务抽取异常转PaperExtraction；`:1628–1647` CompletableFuture在worker内执行。只在外线程设置ThreadLocal会漏采；必须显式带不可变context进worker，再建立局部scope。
- `:1746`缺姓/名只累计IDENTITY_UNRESOLVED后continue；`:1779`才写RAW。仅从RAW无法追溯被丢弃样本。
- `:1822`私有processPaper存在，检索未找到其他调用；不为它制造新生产入口。其他内部调用保留默认无采集context。
- `:2270`身份拒绝条件是givenNames或familyNames空白；采样使用同一谓词语义，不引入姓名猜测。

## 4. 队列存储（依赖审计，不改写）

Schema：`db/migration/V133__create_discovery_paper_queue.sql:118–150`；metadata_json NOT NULL、extraction_json NULL、唯一(stream_id,item_key)、status/unit/identity_quality约束。完整schema见V133.sql.txt。
全部相关存取引用见 queue-paths.txt；权威仓库 `DiscoveryPaperQueueRepository.kt`：enqueuePage/insertFailedItem写metadata；saveExtraction写缓存；claimJob/renewJobLease/completeJob/completeJobWithExperts/scheduleRetry/returnToPending/forceFailForLeaseLoss维护状态；clearTerminalPayloads清缓存；deleteTerminalJobs删终态。nextDueJob/nextDueOrdinaryJob读取负载；统计读activeJobStats/jobStatusCountsByStream；Pipeline消费及维护调用均在检索回执中。

- `DiscoveryPipelineService.kt:984–1075` processJob；仅extractionJson空才调用extractQueuedItem，缓存保存之后仍可因暂停/租约等不消费。
- `ExpertDiscoveryService.kt:2008–2073` extractQueuedItem：payload检查→来源解析→FulltextRequestGate→抽取→stamp identityRuleVersion→限长→返回；HostBusy特殊捕获。新增context仅给fresh PAPER/OPENALEX分支，不改HostBusy、重试、序列化限长、缓存版本或CAS。
- 不在queue metadata_json/extraction_json/tasks details_json加样本字段。缓存命中无完整正文，首版不伪造新样本，也不重新下载。(来源: K-identity-cache-version-and-admission)

## 5. 下载与解析路径

- `OpenAlexDataSource.kt:105–190`：先PMC XML，再候选URL，必要时Unpaywall；成功全文即返回。`NO_PMC_ID`兜底并不单指缺PMC ID。该文件本轮不改。
- `PdfEmailExtractor.kt:58–120`：单地址extract，FetchRetry内部多次尝试；`:224–282`实际限流读取，完整bytes在`:267`可取得；`:293+`PDF布局、首尾页及mailto线索共同参与解析。禁止在回放器复制一个仅PDFTextStripper的“简化算法”。
- `EuropePmcDataSource.kt:69/91`公共/私有fetchFullTextXml，内部FetchRetry；`:110`getForObject返回bytes，成功接口没有暴露headers/status，记录null，不能猜200；`:193`JatsXmlEmailParser.parse。
- BoundedHttpExecutor与BoundedFulltextHttp实际在 `config/RestTemplateConfig.kt:224/390`。本计划不改全局HTTP传输层。Pdf可以注入本地假BoundedHttpExecutor用于回放；XML直接调用JatsXmlEmailParser.parse(bytes)，没有网络和Spring上下文。
- 回放XML是解析器层，不含OpenAlex唯一ORCID补作者ID/机构逻辑；PDF/HTML是单内容解析，不复现网络回退策略。生产最终outcome只保留供查阅，不与parser级报告直接作等价比较。
- 首版不捕获Unpaywall/API元数据响应、重定向底层请求、错误响应正文或超限半截正文。没有这些证据时明确写未采集。

## 6. 新文件仓库全部路径（计划）

当前不存在样本仓库实现（sample-store-absence.txt，exitCode=1）。新仓库唯一writer为DiscoverySampleStore.commit；同类cleanup为唯一删除入口（开启时启动、每小时、提交前）；Scope仅聚合有界数据，不自行写文件。reader为Store的限额/清理扫描及03只读list/replay；报告写独立输出目录，不回写原样本。无其他新增store读写者。文件路径、schema、配额、锁、原子提交见contracts。

IP-1～IP-5定义见总计划；子计划人工验收逐一覆盖。

## 7. Apollo 重叠与执行门槛

Apollo05清单与本计划02恰好重叠4文件：ExpertDiscoveryService、DiscoveryPipelineService及两者Test。不是“零冲突可以直接一起发布”。01与03可独立开发，02在Apollo05合并后再核对服务参数、冻结context和异常边界；独立工作树，不在同一目录同时改共享文件；最终单一合并产物构建部署。

现有Apollo计划基线e7c3422，本计划3efc192；执行时以最新已合并代码为准，保留两个计划的语义；若需新增清单外文件，先修订计划，不暗扩范围。

## 8. 知识核验与排除

已读取并使用 K-source-recall-needs-original-layout、K-author-identity-needs-email-evidence、K-identity-cache-version-and-admission、K-discovery-reentry-keeps-run-accounting、K-page-commit-must-cover-consumer-failures、K-task-activity-execution-identity、K-progress-log-batchonly-two-readers、K-plan-quantified-claims-need-grep-receipts；命中计数/日期已维护。均为近期条目，不触发90天衰减。

发现 K-task-activity-execution-identity 的“无心跳/peek未实现”已过期：TaskExecutionService:21/26存在ownerToken和heartbeatOwned，TaskProgressStore:54已有peek。保留“按正执行ID关联”规则并修正文档；executionDetail的errorMessage也已在TaskExecutionController:135/430实现，一并修正旧条目。不能沿用旧实现断言。其它关于专家字段/资格/前端样式知识不扩散到本计划，因为这些存储和页面均不修改。
